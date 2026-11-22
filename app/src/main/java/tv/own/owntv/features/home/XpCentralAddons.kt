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
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(VALUE).apply()
        context.getSharedPreferences("movioxp_central_profiles", Context.MODE_PRIVATE).edit().clear().apply()
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

    /** Merge this local profile's add-ons with its Central profile, then return the merged URL list. */
    suspend fun syncProfile(
        context: Context,
        connection: XpCentralConnection,
        localProfileId: Long,
        localProfileName: String,
        isChild: Boolean,
        localAddons: List<XpCentralAddon>,
    ): List<String> = withContext(Dispatchers.IO) {
        require(localProfileId > 0)
        val centralProfileId = ensureProfile(context, connection, localProfileId, localProfileName, isChild)

        val accountAddons = request(connection.apiBase, "addons", connection.token).optJSONArray("addons") ?: JSONArray()
        val knownIds = linkedMapOf<String, Long>()
        for (i in 0 until accountAddons.length()) {
            val item = accountAddons.optJSONObject(i) ?: continue
            item.optString("url").takeIf(String::isNotBlank)?.let { knownIds[it] = item.optLong("id") }
        }
        localAddons.distinctBy { it.url }.forEach { addon ->
            if (addon.url !in knownIds) {
                val result = request(connection.apiBase, "addons", connection.token, "POST",
                    JSONObject().put("url", addon.url).put("label", addon.label).put("manifest", addon.manifest))
                knownIds[addon.url] = result.getLong("id")
            }
        }

        val profilePath = "profiles/$centralProfileId/addons"
        val assigned = request(connection.apiBase, profilePath, connection.token).optJSONArray("addons") ?: JSONArray()
        val mergedUrls = linkedSetOf<String>()
        for (i in 0 until assigned.length()) assigned.optJSONObject(i)?.optString("url")
            ?.takeIf(String::isNotBlank)?.let(mergedUrls::add)
        localAddons.forEach { mergedUrls.add(it.url) }

        // Every assigned add-on must also exist in the account library. Remote-only profile add-ons
        // will normally already be present there; refresh the account map once if a legacy response omits one.
        if (mergedUrls.any { it !in knownIds }) {
            val refreshed = request(connection.apiBase, "addons", connection.token).optJSONArray("addons") ?: JSONArray()
            for (i in 0 until refreshed.length()) {
                val item = refreshed.optJSONObject(i) ?: continue
                item.optString("url").takeIf(String::isNotBlank)?.let { knownIds[it] = item.optLong("id") }
            }
        }
        val ids = mergedUrls.mapNotNull(knownIds::get)
        if (ids.size != mergedUrls.size) throw IllegalStateException()
        val addonIds = JSONArray().apply { ids.forEach(::put) }
        request(connection.apiBase, profilePath, connection.token, "PUT", JSONObject().put("addon_ids", addonIds))
        mergedUrls.toList()
    }

    private fun ensureProfile(
        context: Context,
        connection: XpCentralConnection,
        localProfileId: Long,
        name: String,
        isChild: Boolean,
    ): Long {
        val prefs = context.getSharedPreferences("movioxp_central_profiles", Context.MODE_PRIVATE)
        val mapKey = "${connection.accountId}:$localProfileId"
        val mapped = prefs.getLong(mapKey, 0L)
        val response = request(connection.apiBase, "profiles", connection.token)
        val profiles = response.optJSONArray("profiles") ?: JSONArray()
        for (i in 0 until profiles.length()) {
            val profile = profiles.optJSONObject(i) ?: continue
            if (profile.optLong("id") == mapped && mapped > 0L) return mapped
        }
        var match = 0L
        for (i in 0 until profiles.length()) {
            val profile = profiles.optJSONObject(i) ?: continue
            if (profile.optString("name").equals(name, true) && profile.optBoolean("is_child") == isChild) {
                match = profile.optLong("id")
                break
            }
        }
        if (match == 0L) {
            match = request(connection.apiBase, "profiles", connection.token, "POST",
                JSONObject().put("name", name.ifBlank { context.getString(tv.own.owntv.R.string.moviotv_profile_fallback) }).put("is_child", isChild)).getLong("id")
        }
        prefs.edit().putLong(mapKey, match).apply()
        return match
    }

    private fun request(
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
