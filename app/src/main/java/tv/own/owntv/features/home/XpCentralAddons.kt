package tv.own.owntv.features.home

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class XpCentralConnection(
    val apiBase: String,
    val token: String,
    val accountId: Long,
    val username: String,
)

internal data class XpCentralAddon(
    val url: String,
    val label: String,
    val manifest: JSONObject,
)

/** Stores the Central bearer token encrypted with an Android Keystore AES key. */
internal object XpCentralConnectionStore {
    private const val PREFS = "movioxp_central_secure"
    private const val VALUE = "connection_v1"
    private const val ALIAS = "movioxp_central_aes_v1"

    fun read(context: Context): XpCentralConnection? = runCatching {
        val encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(VALUE, null)
            ?: return null
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        require(payload.size > 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, payload.copyOfRange(0, 12)))
        val json = JSONObject(String(cipher.doFinal(payload.copyOfRange(12, payload.size)), Charsets.UTF_8))
        XpCentralConnection(
            apiBase = json.getString("api_base"), token = json.getString("token"),
            accountId = json.getLong("account_id"), username = json.optString("username"),
        )
    }.getOrNull()

    fun write(context: Context, connection: XpCentralConnection) {
        val plain = JSONObject().put("api_base", connection.apiBase).put("token", connection.token)
            .put("account_id", connection.accountId).put("username", connection.username)
            .toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encoded = Base64.encodeToString(cipher.iv + cipher.doFinal(plain), Base64.NO_WRAP)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(VALUE, encoded).apply()
        XpCentralProfileScope.bind(connection.accountId)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(VALUE).apply()
        // Keep old account mappings and all local profile data; logout only ends access.
        XpCentralProfileScope.unbind()
    }

    private fun key(): SecretKey {
        val store = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(
            ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }
}

/** HTTPS client for Central login and the dedicated account/profile add-on routes. */
internal object XpCentralAddonSync {
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS).build()

    suspend fun signIn(rawApiBase: String, username: String, password: String): XpCentralConnection = withContext(Dispatchers.IO) {
        val base = normalizeApiBase(rawApiBase)
        val response = request(base, "login", null, "POST", JSONObject().put("username", username.trim()).put("password", password))
        val token = response.getString("token")
        val accountId = response.getLong("account_id")
        XpCentralConnection(base, token, accountId, username.trim())
    }

    suspend fun register(rawApiBase: String, username: String, password: String): String = withContext(Dispatchers.IO) {
        val base = normalizeApiBase(rawApiBase)
        val response = request(base, "register", null, "POST", JSONObject().put("username", username.trim()).put("password", password))
        response.optString("message")
    }

    suspend fun signOut(connection: XpCentralConnection) = withContext(Dispatchers.IO) {
        request(connection.apiBase, "logout", connection.token, "POST", JSONObject())
    }

    /**
     * Central is authoritative on login and refresh. Never upload cached add-ons or rewrite an
     * entire assignment list during a background sync: that resurrected disabled Comet entries.
     */
    suspend fun syncProfile(
        context: Context,
        connection: XpCentralConnection,
        localProfileId: Long,
        localProfileName: String,
        isChild: Boolean,
        localAddons: List<XpCentralAddon>,
    ): List<String> = withContext(Dispatchers.IO) {
        val centralId = ensureProfile(context, connection, localProfileId, localProfileName, isChild)
        val remote = request(connection.apiBase, "profiles/$centralId/addons", connection.token)
            .getJSONArray("addons")
        buildList {
            for (index in 0 until remote.length()) {
                remote.optJSONObject(index)?.optString("url")
                    ?.takeIf(String::isNotBlank)?.let(::add)
            }
        }.distinct()
    }

    /** An explicit user install is the only path that may POST a configured manifest to Central. */
    suspend fun installForProfile(
        context: Context,
        connection: XpCentralConnection,
        localProfileId: Long,
        addon: XpCentralAddon,
    ) = withContext(Dispatchers.IO) {
        val cloudId = ensureProfile(context, connection, localProfileId, "", false)
        val created = request(connection.apiBase, "addons", connection.token, "POST",
            JSONObject().put("url", addon.url).put("label", addon.label).put("manifest", addon.manifest))
        val newId = created.getLong("id")
        val path = "profiles/$cloudId/addons"
        val old = request(connection.apiBase, path, connection.token).getJSONArray("addons")
        val ids = linkedSetOf<Long>()
        for (index in 0 until old.length()) {
            old.optJSONObject(index)?.optLong("id")?.takeIf { it > 0L }?.let(ids::add)
        }
        ids += newId
        request(connection.apiBase, path, connection.token, "PUT",
            JSONObject().put("addon_ids", JSONArray().apply { ids.forEach(::put) }))
    }

    /** Remove only this profile's link, not the account library or other profiles' settings. */
    suspend fun unlinkForProfile(
        context: Context,
        connection: XpCentralConnection,
        localProfileId: Long,
        url: String,
    ) = withContext(Dispatchers.IO) {
        val cloudId = ensureProfile(context, connection, localProfileId, "", false)
        val path = "profiles/$cloudId/addons"
        val old = request(connection.apiBase, path, connection.token).getJSONArray("addons")
        val ids = JSONArray()
        for (index in 0 until old.length()) {
            val entry = old.optJSONObject(index) ?: continue
            if (entry.optString("url") != url && entry.optLong("id") > 0) {
                ids.put(entry.getLong("id"))
            }
        }
        request(connection.apiBase, path, connection.token, "PUT", JSONObject().put("addon_ids", ids))
    }

    /** Fail closed. Background synchronization must NEVER create a Central profile or match by name. */
    internal fun ensureProfile(
        context: Context,
        connection: XpCentralConnection,
        localProfileId: Long,
        name: String,
        isChild: Boolean,
    ): Long {
        require(localProfileId > 0)
        val remoteId = XpCentralProfileScope.remoteIdFor(context, connection.accountId, localProfileId)
            ?: throw IllegalStateException("Central profile is not linked to this account")
        val remote = request(connection.apiBase, "profiles", connection.token)
            .getJSONArray("profiles")
        for (index in 0 until remote.length()) {
            if (remote.optJSONObject(index)?.optLong("id") == remoteId) return remoteId
        }
        throw IllegalStateException("Central profile is no longer available for this account")
    }

    internal fun request(
        base: String,
        path: String,
        token: String?,
        method: String = "GET",
        payload: JSONObject? = null,
    ): JSONObject {
        val builder = Request.Builder().url(base + path.trimStart('/')).header("Accept", "application/json")
        if (!token.isNullOrBlank()) builder.header("Authorization", "Bearer $token")
        if (method == "POST" || method == "PUT") {
            builder.method(method, (payload ?: JSONObject()).toString().toRequestBody(jsonType))
        } else if (method == "DELETE") builder.delete()
        else builder.get()
        client.newCall(builder.build()).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
            if (!response.isSuccessful) throw IllegalStateException(json.optString("error"))
            return json
        }
    }

    private fun normalizeApiBase(raw: String): String {
        val entered = raw.trim().trimEnd('/')
        val uri = URI(entered)
        require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank())
        val path = uri.path.orEmpty().trimEnd('/')
        val origin = "${uri.scheme}://${uri.rawAuthority}"
        return when {
            path.endsWith("/index.php", true) -> "$origin$path/"
            path.endsWith("/movio/api", true) -> "$origin$path/index.php/"
            path.isBlank() -> "$origin/movio/api/index.php/"
            else -> "$origin$path/movio/api/index.php/"
        }
    }
}
