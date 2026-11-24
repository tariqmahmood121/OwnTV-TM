/*
 * MovioXP catalogue browsing using the Stremio Add-on Protocol.
 * This phase loads add-on manifests and catalogue metadata; stream resolution comes later.
 */
package tv.own.owntv.features.home

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.Image
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVIcon as DrawOwnTVIcon
import tv.own.owntv.features.profiles.ProfilesViewModel
import tv.own.owntv.core.database.entity.ChannelEntity
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// MovioXP follows the selected Movio theme instead of pinning the interface to purple.
private val XpBackground: Color @Composable get() = tv.own.owntv.ui.theme.OwnTVTheme.colors.background
private val XpPanel: Color @Composable get() = tv.own.owntv.ui.theme.OwnTVTheme.colors.surfaceContainerLow
private val XpAccent: Color @Composable get() = tv.own.owntv.ui.theme.OwnTVTheme.colors.primary
private val XpFocus = Color(0xFFFF78C8)
private val XpText: Color @Composable get() = tv.own.owntv.ui.theme.OwnTVTheme.colors.onSurface
private val XpMuted: Color @Composable get() = tv.own.owntv.ui.theme.OwnTVTheme.colors.onSurfaceVariant

private data class XpAddon(
    val url: String,
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val types: List<String>,
    val logo: String?,
    val resources: List<XpResource>,
    val catalogs: List<XpCatalog>,
    val builtIn: Boolean = false,
)
private data class XpAddonListing(
    val url: String,
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val types: List<String>,
    val logo: String?,
)
private data class XpResource(val name: String, val types: List<String>, val idPrefixes: List<String>) {
    fun supports(type: String, id: String): Boolean =
        name == "stream" && (types.isEmpty() || type in types) &&
            (idPrefixes.isEmpty() || idPrefixes.any(id::startsWith))
}
private data class XpCatalog(
    val type: String,
    val id: String,
    val name: String,
    val defaults: List<Pair<String, String>>,
    val extraFields: Map<String, List<String>>,
) {
    val searchParameter: String? get() = listOf("search", "query").firstOrNull(extraFields::containsKey)
}
internal data class XpMeta(
    val id: String, val type: String, val name: String, val poster: String?,
    val description: String?, val releaseInfo: String?, val rating: String?,
    val positionMs: Long = 0L, val durationMs: Long = 0L,
)
data class XpPlaybackSource(
    val url: String,
    val label: String,
    val provider: String,
    val identity: String = "",
    // No more confusing auto-play/automatic failover with an explicit selection by the viewer.
    val userSelected: Boolean = false,
    val legacyIdentity: String = "",
)
private data class XpCatalogRow(val addon: XpAddon, val catalog: XpCatalog)

/**
 * Built-in discovery rows reuse the catalogue metadata already available to MovioXP.
 * Never invent a movie genre or stream endpoint when the provider does not support it.
 */
private data class XpDiscoverSource(
    val categoryId: String,
    val row: XpCatalogRow,
    val parameters: Map<String, String> = emptyMap(),
)

private fun xpDiscoverSource(categoryId: String, addons: List<XpAddon>): XpDiscoverSource? {
    val candidates = addons.sortedByDescending { it.builtIn }.flatMap { addon ->
        addon.catalogs.filter { it.type == "movie" }.map { XpCatalogRow(addon, it) }
    }
    fun genre(terms: List<String>): XpDiscoverSource? {
        for (row in candidates) {
            val field = row.catalog.extraFields.keys.firstOrNull { it.equals("genre", true) } ?: continue
            val options = row.catalog.extraFields[field].orEmpty()
            val match = terms.firstNotNullOfOrNull { expected ->
                options.firstOrNull { it.equals(expected, true) }
            }
            if (match != null && (row.catalog.id == "top" || categoryId.startsWith("genre:"))) {
                return XpDiscoverSource(categoryId, row, mapOf(field to match))
            }
        }
        // Other installed, permitted metadata catalogues may expose Bollywood or Hindi.
        if (categoryId == "xp_bollywood") {
            for (row in candidates) {
                if (row.catalog.name.contains("Bollywood", ignoreCase = true) ||
                    row.catalog.name.contains("Hindi Movies", ignoreCase = true)) {
                    return XpDiscoverSource(categoryId, row)
                }
                val field = row.catalog.extraFields.keys.firstOrNull { it.equals("genre", true) } ?: continue
                val options = row.catalog.extraFields[field].orEmpty()
                val match = terms.firstNotNullOfOrNull { expected ->
                    options.firstOrNull { it.equals(expected, true) }
                }
                if (match != null) return XpDiscoverSource(categoryId, row, mapOf(field to match))
            }
        }
        return null
    }

    val customGenre = XpHomeConfig.genreName(categoryId)
    if (customGenre != null) return genre(listOf(customGenre))
    return when (categoryId) {
        "xp_trending" -> {
            val row = candidates.firstOrNull { it.catalog.name.contains("Trending", true) } ?:
                candidates.firstOrNull { it.catalog.id == "top" }
            row?.let { XpDiscoverSource(categoryId, it) }
        }
        "xp_latest" -> {
            val row = candidates.firstOrNull { it.catalog.id == "year" }
            if (row != null) {
                val field = row.catalog.extraFields.keys.firstOrNull { it.equals("genre", true) }
                val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR).toString()
                val supported = field != null && year in row.catalog.extraFields[field].orEmpty()
                if (supported) XpDiscoverSource(categoryId, row, mapOf(field!! to year))
                else null
            } else candidates.firstOrNull {
                it.catalog.name.contains("Latest", true) || it.catalog.name.equals("New", true)
            }?.let { XpDiscoverSource(categoryId, it) }
        }
        "xp_horror" -> genre(listOf("Horror"))
        "xp_action" -> genre(listOf("Action"))
        "xp_thriller" -> genre(listOf("Thriller"))
        "xp_bollywood" -> genre(listOf("Bollywood", "Hindi", "Indian"))
        else -> null
    }
}

private data class XpSearchHit(val addon: XpAddon, val meta: XpMeta)
private data class XpStream(val addon: XpAddon, val label: String, val detail: String?, val url: String?)

private object XpAddonProtocol {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS).build()

    suspend fun loadManifest(raw: String): XpAddon = withContext(Dispatchers.IO) {
        val url = manifestUrl(raw)
        val json = get(url)
        val resourceSource = json.optJSONArray("resources") ?: JSONArray()
        val resources = buildList {
            for (index in 0 until resourceSource.length()) {
                val item = resourceSource.opt(index)
                when (item) {
                    is String -> item.trim().takeIf(String::isNotBlank)?.let {
                        add(XpResource(it, emptyList(), emptyList()))
                    }
                    is JSONObject -> {
                        val name = item.optString("name").trim()
                        if (name.isNotBlank()) add(
                            XpResource(name, item.stringList("types"), item.stringList("idPrefixes"))
                        )
                    }
                }
            }
        }
        val source = json.optJSONArray("catalogs") ?: JSONArray()
        val catalogs = buildList {
            for (index in 0 until source.length()) {
                val item = source.optJSONObject(index) ?: continue
                val type = item.optString("type").trim()
                val id = item.optString("id").trim()
                if (type.isBlank() || id.isBlank()) continue
                val extra = item.optJSONArray("extra") ?: JSONArray()
                val extraFields = linkedMapOf<String, List<String>>()
                val defaults = buildList {
                    for (n in 0 until extra.length()) {
                        val field = extra.optJSONObject(n) ?: continue
                        val key = field.optString("name").trim()
                        val options = field.optJSONArray("options")
                        if (key.isNotBlank()) {
                            extraFields[key] = if (options == null) emptyList() else buildList {
                                for (optionIndex in 0 until options.length()) {
                                    options.optString(optionIndex).trim().takeIf(String::isNotBlank)?.let(::add)
                                }
                            }
                        }
                        if (!field.optBoolean("isRequired", false)) continue
                        if (key.isNotBlank() && options != null && options.length() > 0) {
                            val value = options.optString(0).trim()
                            if (value.isNotBlank()) add(key to value)
                        }
                    }
                }
                add(XpCatalog(type, id, item.optString("name").ifBlank { id }, defaults, extraFields))
            }
        }
        require(resources.isNotEmpty()) { "This add-on manifest has no supported resources." }
        XpAddon(
            url,
            json.optString("id").ifBlank { url },
            json.optString("name").ifBlank { URI(url).host ?: url },
            json.optString("version").ifBlank { "—" },
            json.optString("description"),
            json.stringList("types"),
            json.optString("logo").takeIf { it.isNotBlank() },
            resources,
            catalogs,
        )
    }

    suspend fun loadDirectory(category: String): List<XpAddonListing> = withContext(Dispatchers.IO) {
        require(category == "official" || category == "community")
        val source = get("https://v3-cinemeta.strem.io/addon_catalog/all/$category.json")
            .optJSONArray("addons") ?: JSONArray()
        buildList {
            for (index in 0 until source.length()) {
                val entry = source.optJSONObject(index) ?: continue
                val transportUrl = entry.optString("transportUrl").trim()
                val manifest = entry.optJSONObject("manifest") ?: continue
                val url = runCatching { manifestUrl(transportUrl) }.getOrNull() ?: continue
                val resources = manifest.optJSONArray("resources")
                if (resources == null || resources.length() == 0) continue
                add(
                    XpAddonListing(
                        url = url,
                        id = manifest.optString("id").ifBlank { url },
                        name = manifest.optString("name").ifBlank { url },
                        version = manifest.optString("version").ifBlank { "—" },
                        description = manifest.optString("description"),
                        types = manifest.stringList("types"),
                        logo = manifest.optString("logo").takeIf { it.isNotBlank() },
                    )
                )
            }
        }
    }

    suspend fun loadCatalog(row: XpCatalogRow, overrides: Map<String, String> = emptyMap()): List<XpMeta> = withContext(Dispatchers.IO) {
        val route = "catalog/${Uri.encode(row.catalog.type)}/${Uri.encode(row.catalog.id)}"
        val parameters = (row.catalog.defaults.toMap() + overrides).filterKeys { it in row.catalog.extraFields }
        val extra = parameters.entries.joinToString("&") { (key, value) ->
            "${Uri.encode(key)}=${Uri.encode(value)}"
        }
        val path = if (extra.isBlank()) "$route.json" else "$route/$extra.json"
        val source = get(resourceUrl(row.addon.url, path)).optJSONArray("metas") ?: JSONArray()
        buildList {
            for (index in 0 until source.length()) {
                val item = source.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                val name = item.optString("name").trim()
                if (id.isBlank() || name.isBlank()) continue
                add(
                    XpMeta(
                        id = id,
                        type = item.optString("type", row.catalog.type),
                        name = name,
                        poster = item.optString("poster").takeIf { it.isNotBlank() },
                        description = item.optString("description").takeIf { it.isNotBlank() },
                        releaseInfo = item.optString("released").take(10).takeIf { it.matches(Regex("""\d{4}-\d{2}-\d{2}""")) }
                            ?: item.optString("releaseInfo").takeIf { it.isNotBlank() },
                        rating = item.optString("imdbRating").takeIf { it.isNotBlank() },
                    )
                )
            }
        }
    }

    suspend fun searchAll(addons: List<XpAddon>, query: String): Pair<List<XpSearchHit>, Int> = coroutineScope {
        val candidates = addons.flatMap { addon ->
            addon.catalogs.filter { it.searchParameter != null }.map { XpCatalogRow(addon, it) }
        }
        val semaphore = Semaphore(4)
        val failures = AtomicInteger(0)
        val results = candidates.map { row ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    runCatching {
                        val parameter = requireNotNull(row.catalog.searchParameter)
                        XpAddonProtocol.loadCatalog(row, mapOf(parameter to query))
                            .map { XpSearchHit(row.addon, it) }
                    }.onFailure { failures.incrementAndGet() }.getOrDefault(emptyList())
                }
            }
        }.awaitAll().flatten()
        val unique = linkedMapOf<String, XpSearchHit>()
        results.forEach { hit -> unique.putIfAbsent("${hit.meta.type}:${hit.meta.id}", hit) }
        unique.values.toList() to failures.get()
    }

    suspend fun loadStreams(addon: XpAddon, item: XpMeta): List<XpStream> = withContext(Dispatchers.IO) {
        val source = get(resourceUrl(addon.url, "stream/${Uri.encode(item.type)}/${Uri.encode(item.id)}.json"))
            .optJSONArray("streams") ?: JSONArray()
        buildList {
            for (index in 0 until source.length()) {
                val stream = source.optJSONObject(index) ?: continue
                val url = stream.optString("url").trim().takeIf(::isHttpUrl)
                val label = stream.optString("title").ifBlank {
                    stream.optString("name").ifBlank { addon.name }
                }
                val detail = stream.optString("description").takeIf { it.isNotBlank() }
                add(XpStream(addon, label, detail, url))
            }
        }
    }

    private fun manifestUrl(raw: String): String {
        val input = raw.trim()
        require(input.startsWith("https://", ignoreCase = true)) { "Use a secure HTTPS add-on URL." }
        val uri = URI(input)
        require(uri.host != null && uri.userInfo == null) { "Enter a valid HTTPS add-on URL." }
        require(!uri.rawPath.orEmpty().trimEnd('/').endsWith("/stremio/v1")) {
            "Legacy /stremio/v1 add-ons are not supported in this first catalogue build."
        }
        val path = uri.rawPath.orEmpty().trimEnd('/')
        val endpoint = if (path.endsWith("/manifest.json")) path else "$path/manifest.json"
        return "${uri.scheme}://${uri.rawAuthority}$endpoint" + (uri.rawQuery?.let { "?$it" } ?: "")
    }

    private fun resourceUrl(manifest: String, resource: String): String {
        val uri = URI(manifest)
        val base = uri.rawPath.orEmpty().removeSuffix("manifest.json").trimEnd('/')
        return "${uri.scheme}://${uri.rawAuthority}$base/$resource" +
            (uri.rawQuery?.takeIf { it.isNotBlank() }?.let { "?$it" } ?: "")
    }

    private fun get(url: String): JSONObject {
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Add-on replied with HTTP ${response.code}.")
            val body = response.body?.string() ?: throw IOException("Add-on returned an empty response.")
            if (body.length > 2_000_000) throw IOException("Add-on response is too large.")
            return JSONObject(body)
        }
    }

    private fun JSONObject.stringList(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return buildList { for (index in 0 until array.length()) array.optString(index).trim().takeIf(String::isNotBlank)?.let(::add) }
    }

    private fun isHttpUrl(raw: String): Boolean = runCatching {
        URI(raw).let { (it.scheme == "https" || it.scheme == "http") && it.host != null }
    }.getOrDefault(false)
}

/** Add-on URLs can contain tokens, so persist the URL list encrypted with an Android Keystore key. */
private object XpAddonStore {
    private const val PREFS = "movioxp_catalogues"
    private const val LEGACY_VALUE = "encrypted_urls"
    private const val ALIAS = "movioxp_addons_aes_v1"

    fun read(context: Context, profileId: Long? = null): List<String> = runCatching {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val profileKey = profileId?.let { "encrypted_urls_profile_$it" }
        // Never feed the unscoped legacy cache into a different account's profile.
        val encoded = if (profileKey == null) preferences.getString(LEGACY_VALUE, null)
            else preferences.getString(profileKey, null)
        if (encoded == null) return emptyList()
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        require(payload.size > 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, payload.copyOfRange(0, 12)))
        val values = JSONArray(String(cipher.doFinal(payload.copyOfRange(12, payload.size)), Charsets.UTF_8))
        buildList { for (i in 0 until values.length()) values.optString(i).takeIf { it.isNotBlank() }?.let(::add) }
    }.getOrDefault(emptyList())

    fun write(context: Context, urls: List<String>, profileId: Long? = null) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val plain = JSONArray(urls.distinct()).toString().toByteArray(Charsets.UTF_8)
        val payload = cipher.iv + cipher.doFinal(plain)
        val key = profileId?.let { "encrypted_urls_profile_$it" } ?: LEGACY_VALUE
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(key, Base64.encodeToString(payload, Base64.NO_WRAP)).apply()
    }

    private fun key(): SecretKey {
        val store = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build()
        )
        return generator.generateKey()
    }
}

/** MovioXP favourites are local and isolated by the active local profile. */
internal object XpFavoritesStore {
    private const val PREFS = "movioxp_catalogues"
    private const val PREFIX = "favorites_profile_"

    fun read(context: Context, profileId: Long): List<XpMeta> = runCatching {
        val source = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(PREFIX + profileId, "[]"))
        buildList {
            for (index in 0 until source.length()) {
                val item = source.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                val name = item.optString("name").trim()
                if (id.isNotBlank() && name.isNotBlank()) add(
                    XpMeta(
                        id = id,
                        type = item.optString("type", "movie"),
                        name = name,
                        poster = item.optString("poster").takeIf(String::isNotBlank),
                        description = item.optString("description").takeIf(String::isNotBlank),
                        releaseInfo = item.optString("year").takeIf(String::isNotBlank),
                        rating = item.optString("rating").takeIf(String::isNotBlank),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    fun write(context: Context, profileId: Long, titles: List<XpMeta>) {
        val json = JSONArray().apply {
            titles.distinctBy { "${it.type}:${it.id}" }.forEach { item ->
                put(JSONObject().put("id", item.id).put("type", item.type).put("name", item.name)
                    .put("poster", item.poster ?: "").put("description", item.description ?: "")
                    .put("year", item.releaseInfo ?: "").put("rating", item.rating ?: ""))
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(PREFIX + profileId, json.toString()).apply()
    }

    fun isFavorite(context: Context, profileId: Long, type: String, id: String): Boolean =
        read(context, profileId).any { it.type == type && it.id == id }

    fun toggle(context: Context, profileId: Long, item: XpMeta): Boolean {
        val current = read(context, profileId)
        val exists = current.any { it.type == item.type && it.id == item.id }
        val updated = if (exists) current.filterNot { it.type == item.type && it.id == item.id }
            else listOf(item.copy(positionMs = 0L, durationMs = 0L)) + current
        write(context, profileId, updated)
        XpCentralDataSync.markFavorite(context, profileId, item, deleted = exists)
        return !exists
    }
}

/** Lightweight recent-title shelf for Stremio catalogue items opened from MovioXP. */
internal object XpContinueStore {
    private const val PREFS = "movioxp_catalogues"
    private const val LEGACY_VALUE = "continue_titles"
    private const val PREFIX = "continue_titles_profile_"

    fun read(context: Context, profileId: Long): List<XpMeta> = runCatching {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = PREFIX + profileId
        var encoded = preferences.getString(key, null)
        if (encoded == null && profileId > 0L) {
            // One-time migration: legacy XP history had no profile marker, so attach it only to the
            // first profile that opens it and remove the shared copy to prevent cross-profile leaks.
            encoded = preferences.getString(LEGACY_VALUE, null)
            if (encoded != null) preferences.edit().putString(key, encoded).remove(LEGACY_VALUE).apply()
        }
        val source = JSONArray(encoded ?: "[]")
        buildList {
            for (index in 0 until source.length()) {
                val item = source.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val name = item.optString("name")
                if (id.isNotBlank() && name.isNotBlank()) add(
                    XpMeta(id, item.optString("type", "movie"), name,
                        item.optString("poster").takeIf(String::isNotBlank), null,
                        item.optString("year").takeIf(String::isNotBlank), null,
                        item.optLong("positionMs", 0L), item.optLong("durationMs", 0L))
                )
            }
        }
    }.getOrDefault(emptyList())

    fun add(context: Context, profileId: Long, item: XpMeta) {
        val existing = read(context, profileId).firstOrNull { it.id == item.id && it.type == item.type }
        // Opening a title must not erase the saved playhead before the player can use it.
        save(context, profileId, if (existing != null) existing else item.copy(positionMs = 0L, durationMs = 0L))
    }

    fun updateProgress(
        context: Context, profileId: Long, id: String, type: String, name: String, poster: String?, year: String?,
        positionMs: Long, durationMs: Long,
    ) {
        val previous = read(context, profileId).firstOrNull { it.id == id && it.type == type }
        val knownDuration = durationMs.takeIf { it > 0L } ?: previous?.durationMs ?: 0L
        val safePosition = positionMs.coerceAtLeast(0L)
        if (knownDuration > 0L && safePosition >= knownDuration * .95f) {
            val meta = XpMeta(id, type, name, poster, null, year, null, safePosition, knownDuration)
            val remaining = read(context, profileId).filterNot { it.id == id && it.type == type }
            write(context, profileId, remaining)
            XpCentralDataSync.markResume(context, profileId, meta, deleted = true)
        } else if (safePosition > 0L) {
            val meta = XpMeta(id, type, name, poster, null, year, null, safePosition, knownDuration)
            save(context, profileId, meta)
            XpCentralDataSync.markResume(context, profileId, meta, deleted = false)
        }
    }

    private fun save(context: Context, profileId: Long, item: XpMeta) {
        val updated = (listOf(item) + read(context, profileId).filterNot { it.id == item.id && it.type == item.type }).take(20)
        write(context, profileId, updated)
    }

    internal fun writeCloud(context: Context, profileId: Long, updated: List<XpMeta>) = write(context, profileId, updated)

    private fun write(context: Context, profileId: Long, updated: List<XpMeta>) {
        val json = JSONArray().apply {
            updated.forEach { meta ->
                put(JSONObject().put("id", meta.id).put("type", meta.type).put("name", meta.name)
                    .put("poster", meta.poster ?: "").put("year", meta.releaseInfo ?: "")
                    .put("positionMs", meta.positionMs).put("durationMs", meta.durationMs))
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(PREFIX + profileId, json.toString()).apply()
    }
}

@Composable
fun StremioCatalogScreen(
    onBack: () -> Unit,
    onPlayStream: (url: String, title: String, type: String, year: String?, contentId: String, poster: String?, resumePositionMs: Long) -> Unit,
    localLiveChannels: List<ChannelEntity> = emptyList(),
    onPlayLocalLiveChannel: (Long) -> Unit = {},
    onPlayStreamCandidates: ((List<XpPlaybackSource>, String, String, String?, String, String?, Long) -> Unit)? = null,
    initialPage: String = "home",
    showBottomNavigation: Boolean = true,
    onSwitchProfile: (() -> Unit)? = null,
    onOpenAppSettings: (() -> Unit)? = null,
    onOpenSettingsGroup: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Keep all five XP tab labels legible on narrow touch devices.
    val compactPhone = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600
    val scope = rememberCoroutineScope()
    val profiles: ProfilesViewModel = koinViewModel()
    val activeProfileId by profiles.activeProfileId.collectAsStateWithLifecycle()
    val profileList by profiles.profiles.collectAsStateWithLifecycle()
    val activeProfile = profileList.firstOrNull { it.id == activeProfileId }
    val activeProfileName = activeProfile?.name.orEmpty()
    val favoritesProfileId = activeProfileId ?: -1L
    val addons = remember { mutableStateListOf<XpAddon>() }
    var homeSettings by remember(favoritesProfileId) {
        mutableStateOf(XpHomeConfig.read(context, favoritesProfileId))
    }
    var showAddHomeCategory by remember { mutableStateOf(false) }
    val discoverMetas = remember { mutableStateMapOf<String, List<XpMeta>>() }
    val discoverLoading = remember { mutableStateMapOf<String, Boolean>() }
    val discoverLastFetch = remember { mutableStateMapOf<String, Long>() }
    val directoryAddons = remember { mutableStateListOf<XpAddonListing>() }
    val rows = remember { mutableStateListOf<XpCatalogRow>() }
    val metas = remember { mutableStateMapOf<String, List<XpMeta>>() }
    val loadingRows = remember { mutableStateMapOf<String, Boolean>() }
    var address by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var showAddonRemote by remember { mutableStateOf(false) }
    val addonRemoteServer = remember(context) { XpAddonRemoteServer(context) }
    val addonRemoteState by addonRemoteServer.state.collectAsStateWithLifecycle()
    var notice by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<XpMeta?>(null) }
    var manualSourceSelection by remember { mutableStateOf(false) }
    var showManualSources by remember { mutableStateOf(false) }
    var selectedStreams by remember { mutableStateOf<List<XpStream>>(emptyList()) }
    var streamsLoading by remember { mutableStateOf(false) }
    var directoryLoading by remember { mutableStateOf(false) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var addonCategory by remember { mutableStateOf("community") }
    var addonFilter by remember { mutableStateOf("") }
    val browsePages = setOf("movies", "series", "live", "favorites")
    var page by remember(initialPage) { mutableStateOf(initialPage) }
    // Independent, small accordion state for the redesigned MovioXP Settings.
    var expandedSettingsGroup by remember { mutableStateOf<Int?>(null) }
    var showPreferredAddons by remember { mutableStateOf(false) }
    var showXpAbout by remember { mutableStateOf(false) }
    var returnPage by remember(initialPage) { mutableStateOf(initialPage) }
    var searchText by remember { mutableStateOf("") }
    var discoveryType by remember { mutableStateOf("all") }
    var discoveryCatalog by remember { mutableStateOf("all") }
    var discoveryGenre by remember { mutableStateOf("all") }
    var genrePickerOpen by remember { mutableStateOf(false) }
    val sortPrefs = remember(context) { context.getSharedPreferences("xp_catalog_sort", Context.MODE_PRIVATE) }
    var sortMode by remember(favoritesProfileId) { mutableStateOf(sortPrefs.getInt(favoritesProfileId.toString(), 1)) }
    val sortLabels = listOf("Default", "Newest first", "Oldest first", "Name A–Z", "Name Z–A", "Highest rating", "Lowest rating")
    fun changeSort(mode: Int) { sortMode = mode; sortPrefs.edit().putInt(favoritesProfileId.toString(), mode).apply() }
    fun sortedTitles(titles: List<XpMeta>, mode: Int = sortMode): List<XpMeta> = when (mode) {
        1 -> titles.sortedByDescending { xpReleaseKey(it) }
        2 -> titles.sortedBy { xpReleaseKey(it).ifBlank { "9999" } }
        3 -> titles.sortedBy { it.name.lowercase() }
        4 -> titles.sortedByDescending { it.name.lowercase() }
        5 -> titles.sortedByDescending { it.rating?.toDoubleOrNull() ?: -1.0 }
        6 -> titles.sortedBy { it.rating?.toDoubleOrNull() ?: Double.MAX_VALUE }
        else -> titles
    }
    var expandedFilter by remember { mutableStateOf<String?>(null) }
    var searchHits by remember { mutableStateOf<List<XpSearchHit>>(emptyList()) }
    var searchLoading by remember { mutableStateOf(false) }
    var searchableCatalogCount by remember { mutableStateOf(0) }
    var searchFailureCount by remember { mutableStateOf(0) }
    var continueTitles by remember(favoritesProfileId) { mutableStateOf(XpContinueStore.read(context, favoritesProfileId)) }
    var favoriteTitles by remember(favoritesProfileId) { mutableStateOf(XpFavoritesStore.read(context, favoritesProfileId)) }
    val libraryTitles = sortedTitles((continueTitles + favoriteTitles).distinctBy { it.type to it.id })
    val libraryPrefs = remember(context) { context.getSharedPreferences("moviotv_poster_grid_v75", Context.MODE_PRIVATE) }
    val libraryZoomKey = "xp_library_zoom_$favoritesProfileId"
    val homeZoomKey = "xp_home_zoom_$favoritesProfileId"
    var libraryScale by remember(libraryZoomKey) {
        mutableStateOf(libraryPrefs.getFloat(libraryZoomKey, 1f).coerceIn(0.65f, 1.85f))
    }
    var homeScale by remember(homeZoomKey) {
        mutableStateOf(libraryPrefs.getFloat(homeZoomKey, 1f).coerceIn(0.65f, 1.85f))
    }
    val libraryScaleNow by rememberUpdatedState(libraryScale)
    val homeScaleNow by rememberUpdatedState(homeScale)
    DisposableEffect(libraryPrefs, libraryZoomKey, homeZoomKey) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            when (key) {
                libraryZoomKey -> libraryScale = prefs.getFloat(libraryZoomKey, 1f).coerceIn(0.65f, 1.85f)
                homeZoomKey -> homeScale = prefs.getFloat(homeZoomKey, 1f).coerceIn(0.65f, 1.85f)
            }
        }
        libraryPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { libraryPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val playbackPrefs = remember(context) { context.getSharedPreferences("movioxp_playback", Context.MODE_PRIVATE) }
    DisposableEffect(playbackPrefs, favoritesProfileId) {
        val refresh = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == XpHomeConfig.orderKey(favoritesProfileId) ||
                key == XpHomeConfig.hiddenKey(favoritesProfileId) ||
                key == XpHomeConfig.defaultsKey(favoritesProfileId)) {
                homeSettings = XpHomeConfig.read(context, favoritesProfileId)
            }
        }
        playbackPrefs.registerOnSharedPreferenceChangeListener(refresh)
        onDispose { playbackPrefs.unregisterOnSharedPreferenceChangeListener(refresh) }
    }
    var autoSelectSource by remember { mutableStateOf(playbackPrefs.getBoolean("auto_select_source", true)) }
    var preferredAddonUrl by remember { mutableStateOf(playbackPrefs.getString("preferred_addon", "").orEmpty()) }
    var autoStartedItem by remember { mutableStateOf<String?>(null) }
    var centralConnection by remember { mutableStateOf(XpCentralConnectionStore.read(context)) }
    var centralApiBase by remember { mutableStateOf(centralConnection?.apiBase?.removeSuffix("/") ?: "https://moviotv.opticalzoo.com/movio/api/index.php") }
    var centralUsername by remember { mutableStateOf(centralConnection?.username.orEmpty()) }
    var centralPassword by remember { mutableStateOf("") }
    var showAccountMenu by remember { mutableStateOf(false) }
    var centralSyncBusy by remember { mutableStateOf(false) }
    // Installing an add-on during an existing Central refresh must never drop its upload.
    var centralSyncAgain by remember { mutableStateOf(false) }
    var centralSyncMessage by remember { mutableStateOf<String?>(null) }
    val firstSourceFocus = remember { FocusRequester() }
    val detailFocus = remember { FocusRequester() }
    val firstGridFocus = remember { FocusRequester() }
    val categoryRailFocus = remember { FocusRequester() }
    val bottomNavigationFocus = remember { FocusRequester() }

    // Retain account-scoped Room data on logout; old bearer token is invalidated locally first.
    fun signOutCentral() {
        val previous = centralConnection
        XpCentralEventSync.clear()
        XpCentralConnectionStore.clear(context)
        centralConnection = null
        centralPassword = ""
        centralSyncMessage = null
        showAccountMenu = false
        addons.clear()
        rows.clear()
        metas.clear()
        continueTitles = emptyList()
        favoriteTitles = emptyList()
        page = "central"
        scope.launch { runCatching { previous?.let { XpCentralAddonSync.signOut(it) } } }
    }

    DisposableEffect(context, favoritesProfileId) {
        val preferences = context.getSharedPreferences("movioxp_catalogues", Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "favorites_profile_$favoritesProfileId" -> favoriteTitles = XpFavoritesStore.read(context, favoritesProfileId)
                "continue_titles_profile_$favoritesProfileId" -> continueTitles = XpContinueStore.read(context, favoritesProfileId)
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val visibleHomeIds = if (page == "home") homeSettings.visibleDiscoverIds() else emptyList()
    val homeSources = visibleHomeIds.associateWith { id -> xpDiscoverSource(id, addons.toList()) }

    LaunchedEffect(page, favoritesProfileId, homeSettings, addons.toList()) {
        if (page != "home") return@LaunchedEffect
        val now = System.currentTimeMillis()
        for (id in homeSettings.visibleDiscoverIds()) {
            val source = xpDiscoverSource(id, addons.toList()) ?: continue
            val cacheKey = "$id|${source.row.addon.url}|${source.row.catalog.id}|${source.parameters}"
            val valid = discoverMetas.containsKey(cacheKey) &&
                now - (discoverLastFetch[cacheKey] ?: 0L) < 1800000L
            if (valid || discoverLoading[cacheKey] == true) continue
            discoverLoading[cacheKey] = true
            launch {
                runCatching { XpAddonProtocol.loadCatalog(source.row, source.parameters).let { titles -> if (id == "xp_trending") titles else titles.sortedByDescending { xpReleaseKey(it) } } }
                    .onSuccess {
                        discoverMetas[cacheKey] = it
                        discoverLastFetch[cacheKey] = System.currentTimeMillis()
                    }
                discoverLoading[cacheKey] = false
            }
        }
    }

    fun catalogKey(row: XpCatalogRow) = "${row.addon.url}|${row.catalog.type}|${row.catalog.id}"
    fun rowKey(row: XpCatalogRow) = "${catalogKey(row)}|${if (page == "discover" || page in browsePages) discoveryGenre else "all"}"
    fun fetchRows() {
        rows.clear()
        val catalogRows = if (page == "home" && !homeSettings.showDefaultCatalogs) emptyList()
            else addons.flatMap { addon -> addon.catalogs.map { XpCatalogRow(addon, it) } }
        rows.addAll(catalogRows.filter { row ->
            val typeMatches = when (page) {
                "movies" -> row.catalog.type.equals("movie", true)
                "series" -> row.catalog.type.equals("series", true)
                "live" -> row.catalog.type.equals("channel", true)
                "favorites" -> false
                "discover" -> discoveryType == "all" || row.catalog.type == discoveryType
                else -> true
            }
            val genreSupported = discoveryGenre == "all" || page !in setOf("movies", "series") ||
                row.catalog.extraFields.keys.any { it.equals("genre", true) }
            val catalogMatches = page != "discover" || discoveryCatalog == "all" || catalogKey(row) == discoveryCatalog
            typeMatches && catalogMatches && genreSupported
        })
        rows.forEach { row ->
            val key = rowKey(row)
            val genreField = row.catalog.extraFields.keys.firstOrNull { it.equals("genre", true) }
            val overrides = if ((page == "discover" || page == "movies" || page == "series") && discoveryGenre != "all" && genreField != null) {
                mapOf(genreField to discoveryGenre)
            } else emptyMap()
            loadingRows[key] = true
            scope.launch {
                runCatching { XpAddonProtocol.loadCatalog(row, overrides) }
                    .onSuccess { metas[key] = it }
                    .onFailure { notice = context.getString(R.string.moviotv_xp_catalogue_load_failed) }
                loadingRows[key] = false
            }
        }
    }

    val availableCatalogRows = addons.flatMap { addon -> addon.catalogs.map { XpCatalogRow(addon, it) } }
    val availableHomeGenres = availableCatalogRows
        .filter { it.catalog.type == "movie" }
        .filter { it.catalog.id == "top" || it.catalog.name.contains("genre", true) }
        .flatMap { it.catalog.extraFields.entries }
        .filter { it.key.equals("genre", true) }
        .flatMap { it.value }
        .distinct()
        .filterNot { it.trim().matches(Regex("\\d{4}")) }
        .filter { XpHomeConfig.idForGenre(it) != null }
        .sorted()
    val activeType = when (page) {
        "movies" -> "movie"
        "series" -> "series"
        "live" -> "channel"
        else -> discoveryType
    }
    val genreOptions = availableCatalogRows
        .filter { activeType == "all" || it.catalog.type.equals(activeType, true) }
        .filter { page != "discover" || discoveryCatalog == "all" || catalogKey(it) == discoveryCatalog }
        .flatMap { row -> row.catalog.extraFields.filterKeys { it.equals("genre", true) }.values.flatten() }
        // Some catalogues incorrectly advertise release years as genre choices; keep the rail
        // focused on actual genres instead of filling it with entries such as 1920, 1921, ... .
        .filterNot { it.trim().matches(Regex("\\d{4}")) }
        .distinct()

    val localLiveTitles = if (page == "live") localLiveChannels.map { channel ->
        XpMeta(
            id = "own-live:${channel.id}",
            type = "channel",
            name = channel.name,
            poster = channel.logoUrl,
            description = null,
            releaseInfo = null,
            rating = null,
        )
    } else emptyList()
    val sectionTitles = (if (page == "favorites") favoriteTitles else localLiveTitles + rows.flatMap { row -> metas[rowKey(row)].orEmpty() })
        .distinctBy { "${it.type}:${it.id}" }
        .let { sortedTitles(it) }
    val gridState = rememberLazyGridState()
    LaunchedEffect(page, sectionTitles.size) {
        if (page in browsePages && sectionTitles.isNotEmpty()) {
            gridState.scrollToItem(0)
            delay(120)
            runCatching { firstGridFocus.requestFocus() }
        }
    }

    fun toggleFavorite(item: XpMeta) {
        val existing = favoriteTitles.any { it.type == item.type && it.id == item.id }
        favoriteTitles = if (existing) favoriteTitles.filterNot { it.type == item.type && it.id == item.id }
        else listOf(item.copy(positionMs = 0L, durationMs = 0L)) + favoriteTitles
        XpFavoritesStore.write(context, favoritesProfileId, favoriteTitles)
    }

    fun withSavedResume(item: XpMeta): XpMeta {
        val saved = continueTitles.firstOrNull { it.id == item.id && it.type == item.type }
        return if (saved != null) item.copy(positionMs = saved.positionMs, durationMs = saved.durationMs) else item
    }

    fun openItem(item: XpMeta) {
        manualSourceSelection = false
        showManualSources = true
        selected = withSavedResume(item)
    }

    fun openDetails(item: XpMeta, showSources: Boolean) {
        manualSourceSelection = true
        showManualSources = showSources
        selected = withSavedResume(item)
    }

    fun syncAddonsWithCentral() {
        if (centralSyncBusy) {
            centralSyncAgain = true
            return
        }
        if (favoritesProfileId <= 0L || activeProfileName.isBlank()) {
            centralSyncMessage = context.getString(R.string.moviotv_xp_central_profile_required)
            return
        }
        if (centralConnection == null && (centralUsername.isBlank() || centralPassword.isBlank() || centralApiBase.isBlank())) {
            centralSyncMessage = context.getString(R.string.moviotv_xp_central_details_required)
            return
        }
        val localProfileId = favoritesProfileId
        val localProfileName = activeProfileName
        val childProfile = activeProfile?.isKids == true
        val localAddons = addons.filterNot { it.builtIn }.map { addon ->
            XpCentralAddon(
                addon.url,
                addon.name,
                JSONObject().put("id", addon.id).put("name", addon.name).put("version", addon.version)
                    .put("description", addon.description).put("types", JSONArray(addon.types)),
            )
        }
        centralSyncBusy = true
        centralSyncMessage = null
        scope.launch {
            runCatching {
                val connection = centralConnection ?: XpCentralAddonSync.signIn(
                    centralApiBase, centralUsername, centralPassword,
                ).also {
                    XpCentralConnectionStore.write(context, it)
                    centralConnection = it
                    centralApiBase = it.apiBase.removeSuffix("/")
                    centralPassword = ""
                }
                XpCentralAddonSync.syncProfile(
                    context, connection, localProfileId, localProfileName, childProfile, localAddons,
                )
            }.onSuccess { mergedUrls ->
                // Central is authoritative on refresh: do not restore a disabled/removed
                // add-on from the previous device cache (e.g. Comet).
                val allUrls = mergedUrls.distinct()
                XpAddonStore.write(context, allUrls, localProfileId)
                val builtIn = addons.firstOrNull { it.builtIn }
                addons.clear()
                if (builtIn != null) addons.add(builtIn)
                allUrls.forEach { url ->
                    runCatching { XpAddonProtocol.loadManifest(url) }
                        .onSuccess { loaded -> if (addons.none { it.url == loaded.url }) addons.add(loaded) }
                }
                centralSyncMessage = context.getString(R.string.moviotv_xp_central_profile_saved, localProfileName)
            }.onFailure {
                // Local encrypted URL list stays intact and will be retried at next sync.
                centralSyncMessage = context.getString(R.string.moviotv_xp_central_sync_failed)
            }
            centralSyncBusy = false
            if (centralSyncAgain) {
                centralSyncAgain = false
                syncAddonsWithCentral()
            }
        }
    }

    // A Central login/profile can be restored before the user opens Add-ons. Pull its account/profile
    // add-ons as soon as this catalogue screen is ready, then keep the regular data sync separate.
    LaunchedEffect(centralConnection, favoritesProfileId, activeProfileName, activeProfile?.isKids) {
        if (centralConnection != null && favoritesProfileId > 0L && activeProfileName.isNotBlank()) {
            syncAddonsWithCentral()
        }
    }

    LaunchedEffect(centralConnection, favoritesProfileId, activeProfileName, activeProfile?.isKids) {
        val connection = centralConnection
        if (connection == null) {
            XpCentralEventSync.clear()
            return@LaunchedEffect
        }
        if (favoritesProfileId <= 0L || activeProfileName.isBlank()) return@LaunchedEffect
        XpCentralEventSync.bind(connection, favoritesProfileId, activeProfileName, activeProfile?.isKids == true)
        while (true) {
            runCatching {
                XpCentralDataSync.sync(context, connection, favoritesProfileId, activeProfileName, activeProfile?.isKids == true)
            }.onSuccess {
                favoriteTitles = XpFavoritesStore.read(context, favoritesProfileId)
                continueTitles = XpContinueStore.read(context, favoritesProfileId)
            }
            delay(90_000L)
        }
    }

    fun installAddon(url: String) {
        if (addons.any { it.url == url }) {
            notice = context.getString(R.string.moviotv_xp_addon_already_installed)
            return
        }
        busy = true
        notice = null
        scope.launch {
            runCatching { XpAddonProtocol.loadManifest(url) }
                .onSuccess { addon ->
                    if (addons.none { it.url == addon.url }) {
                        addons.add(addon)
                        XpAddonStore.write(context, addons.filterNot { it.builtIn }.map { it.url }, favoritesProfileId)
                        centralConnection?.let { conn ->
                            centralSyncMessage = context.getString(R.string.moviotv_xp_central_saving_profile)
                            runCatching {
                                XpCentralAddonSync.installForProfile(
                                    context, conn, favoritesProfileId,
                                    XpCentralAddon(addon.url, addon.name,
                                        JSONObject().put("id", addon.id).put("name", addon.name)
                                            .put("version", addon.version).put("description", addon.description)
                                            .put("types", JSONArray(addon.types))),
                                )
                            }.onSuccess { syncAddonsWithCentral() }
                                .onFailure { centralSyncMessage = context.getString(R.string.moviotv_xp_central_sync_failed) }
                        }
                    }
                    notice = null
                    adding = false
                    address = ""
                }
                .onFailure { notice = context.getString(R.string.moviotv_xp_addon_install_failed) }
            busy = false
        }
    }

    DisposableEffect(addonRemoteServer) {
        onDispose { addonRemoteServer.close() }
    }
    LaunchedEffect(addonRemoteServer) {
        addonRemoteServer.submissions.collect { url ->
            showAddonRemote = false
            adding = false
            installAddon(url)
        }
    }
    LaunchedEffect(showAddonRemote) {
        if (!showAddonRemote) addonRemoteServer.close()
    }

    fun removeAddon(addon: XpAddon) {
        if (addon.builtIn) return
        addons.remove(addon)
        XpAddonStore.write(context, addons.filterNot { it.builtIn }.map { it.url }, favoritesProfileId)
        centralConnection?.let { connection ->
            scope.launch {
                runCatching {
                    XpCentralAddonSync.unlinkForProfile(context, connection, favoritesProfileId, addon.url)
                }.onSuccess { syncAddonsWithCentral() }
                    .onFailure { centralSyncMessage = context.getString(R.string.moviotv_xp_central_sync_failed) }
            }
        }
    }

    LaunchedEffect(favoritesProfileId) {
        addons.clear()
        busy = true
        runCatching {
            XpAddonProtocol.loadManifest(context.getString(R.string.moviotv_xp_default_catalogue_manifest))
                .copy(builtIn = true)
        }
            .onSuccess { builtIn -> addons.add(builtIn) }
            .onFailure { notice = context.getString(R.string.moviotv_xp_default_catalogue_load_failed) }
        XpAddonStore.read(context, favoritesProfileId).forEach { saved ->
            runCatching { XpAddonProtocol.loadManifest(saved) }
                .onSuccess { addon -> if (addons.none { it.url == addon.url }) addons.add(addon) }
                .onFailure { notice = context.getString(R.string.moviotv_xp_addon_refresh_failed) }
        }
        busy = false
        if (centralConnection != null) syncAddonsWithCentral()
    }
    LaunchedEffect(page, discoveryType, discoveryCatalog, discoveryGenre, addons.size, homeSettings.showDefaultCatalogs) {
        if (page == "home" || page == "discover" || page in browsePages) fetchRows()
    }
    LaunchedEffect(page, searchText, addons.size) {
        val query = searchText.trim()
        if (page != "search" || query.length < 2) {
            searchLoading = false
            searchHits = emptyList()
            searchFailureCount = 0
            searchableCatalogCount = addons.sumOf { addon -> addon.catalogs.count { it.searchParameter != null } }
            return@LaunchedEffect
        }
        delay(400)
        searchLoading = true
        val searchable = addons.toList()
        searchableCatalogCount = searchable.sumOf { addon -> addon.catalogs.count { it.searchParameter != null } }
        val (hits, failures) = XpAddonProtocol.searchAll(searchable, query)
        searchHits = hits
        searchFailureCount = failures
        searchLoading = false
    }
    LaunchedEffect(selected?.id, selected?.type, addons.size) {
        val item = selected ?: return@LaunchedEffect
        autoStartedItem = null
        selectedStreams = emptyList()
        if (item.id.startsWith("own-live:")) {
            streamsLoading = false
            return@LaunchedEffect
        }
        val providers = addons.filter { addon -> addon.resources.any { it.supports(item.type, item.id) } }
        if (providers.isEmpty()) {
            streamsLoading = false
        } else {
            streamsLoading = true
            selectedStreams = providers.flatMap { addon ->
                runCatching { XpAddonProtocol.loadStreams(addon, item) }.getOrDefault(emptyList())
            }
            streamsLoading = false
        }
    }
    LaunchedEffect(selected?.id, selected?.type, selectedStreams, streamsLoading, autoSelectSource, preferredAddonUrl, manualSourceSelection) {
        val item = selected ?: return@LaunchedEffect
        if (manualSourceSelection || !autoSelectSource || streamsLoading || selectedStreams.isEmpty()) return@LaunchedEffect
        val itemKey = "${item.type}:${item.id}"
        if (autoStartedItem == itemKey) return@LaunchedEffect
        val contentKey = "stremio:${item.type}:${item.id}"
        val saved = XpSourcePreference.read(context, favoritesProfileId, contentKey)
        val candidates = selectedStreams.mapNotNull { stream ->
            stream.url?.let { url ->
                XpPlaybackSource(
                    url, stream.label, stream.addon.name,
                    XpSourcePreference.identity(stream.addon.url, stream.label),
                    legacyIdentity = XpSourcePreference.legacyIdentity(stream.addon.url, stream.label, stream.detail),
                )
            }
        }.sortedWith(
            compareBy<XpPlaybackSource> { XpSourcePreference.score(saved, it) }
                .thenBy { if (addons.firstOrNull { addon -> addon.name == it.provider }?.url == preferredAddonUrl) 0 else 1 },
        )
        if (candidates.isEmpty()) return@LaunchedEffect
        autoStartedItem = itemKey
        XpContinueStore.add(context, favoritesProfileId, item)
        continueTitles = XpContinueStore.read(context, favoritesProfileId)
        val sources = candidates
        selected = null
        manualSourceSelection = false
        showManualSources = false
        if (onPlayStreamCandidates != null) {
            onPlayStreamCandidates(sources, item.name, item.type, item.releaseInfo, item.id, item.poster, item.positionMs)
        } else {
            onPlayStream(sources.first().url, item.name, item.type, item.releaseInfo, item.id, item.poster, item.positionMs)
        }
    }
    LaunchedEffect(selected?.id, selected?.type, streamsLoading, selectedStreams.size, autoSelectSource, manualSourceSelection, showManualSources) {
        if (selected != null && !streamsLoading) runCatching {
            if (manualSourceSelection && showManualSources && selectedStreams.isNotEmpty()) firstSourceFocus.requestFocus()
            else if (selectedStreams.isEmpty() || !showManualSources) detailFocus.requestFocus()
        }
    }
    BackHandler(enabled = selected != null || adding || page != initialPage) {
        when {
            selected != null -> { selected = null; manualSourceSelection = false; showManualSources = false }
            adding -> adding = false
            else -> page = if (page == "search" && returnPage.isNotBlank()) returnPage else initialPage
        }
    }
    BackHandler(enabled = selected == null && !adding && page == initialPage, onBack = onBack)

    LaunchedEffect(page, addonCategory) {
        if (page != "addons" || addonCategory == "installed") return@LaunchedEffect
        directoryLoading = true
        directoryAddons.clear()
        runCatching { XpAddonProtocol.loadDirectory(addonCategory) }
            .onSuccess { directoryAddons.addAll(it) }
            .onFailure { notice = context.getString(R.string.moviotv_xp_addon_directory_failed) }
        directoryLoading = false
    }

    Box(
        modifier.fillMaxSize().background(XpBackground).onPreviewKeyEvent { event ->
            if (showBottomNavigation && event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_MENU) {
                runCatching { bottomNavigationFocus.requestFocus() }
                true
            } else false
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            if (showBottomNavigation) Row(
                Modifier.fillMaxWidth().padding(horizontal = if (compactPhone) 12.dp else 22.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (compactPhone) 8.dp else 14.dp),
            ) {
                Image(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.moviotv_launcher),
                    contentDescription = stringResource(R.string.moviotv_xp_title),
                    modifier = Modifier.size(if (compactPhone) 34.dp else 42.dp).clickable { page = "home" },
                )
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(XpPanel)
                        .padding(horizontal = if (compactPhone) 12.dp else 18.dp, vertical = if (compactPhone) 11.dp else 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        if (searchText.isBlank()) Text(
                            stringResource(if (compactPhone) R.string.moviotv_xp_nav_search else R.string.moviotv_xp_search_hint),
                            color = XpMuted, fontSize = if (compactPhone) 15.sp else 20.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        BasicTextField(
                            value = searchText,
                            onValueChange = {
                                searchText = it
                                if (it.isNotBlank()) {
                                    if (page != "search") returnPage = page
                                    page = "search"
                                } else if (page == "search") page = returnPage
                            },
                            singleLine = true,
                            textStyle = TextStyle(color = XpText, fontSize = if (compactPhone) 16.sp else 20.sp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    DrawOwnTVIcon(OwnTVIcon.SEARCH, XpText, Modifier.size(if (compactPhone) 19.dp else 24.dp))
                }
                XpAccountProfileBadge(
                    displayName = activeProfileName,
                    signedIn = centralConnection != null,
                    compact = compactPhone,
                    onClick = { showAccountMenu = true },
                )
            } else Row(
                Modifier.fillMaxWidth().padding(horizontal = 34.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.moviotv_xp_title), color = XpText, fontSize = 28.sp, fontWeight = FontWeight.Black)
                    if (activeProfileName.isNotBlank()) Text(activeProfileName, color = XpMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(stringResource(when (page) { "movies" -> R.string.moviotv_nav_movies; "series" -> R.string.moviotv_nav_series; "live" -> R.string.moviotv_nav_live_tv; "search" -> R.string.moviotv_xp_nav_search; "library" -> R.string.moviotv_xp_nav_library; "addons" -> R.string.moviotv_xp_nav_addons; "settings" -> R.string.moviotv_xp_nav_settings; "home" -> R.string.moviotv_nav_home; else -> R.string.moviotv_xp_nav_discover }), color = XpAccent, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
                XpAccountProfileBadge(
                    displayName = activeProfileName,
                    signedIn = centralConnection != null,
                    compact = false,
                    onClick = { showAccountMenu = true },
                )
                if (page != "settings") XpAction(stringResource(R.string.moviotv_xp_nav_settings), false) { page = "settings"; adding = false }
                if (showBottomNavigation) XpAction(stringResource(R.string.moviotv_nav_more), false) {
                    runCatching { bottomNavigationFocus.requestFocus() }
                }
                if (page != "addons") XpAction(
                    stringResource(if (page in browsePages) R.string.moviotv_xp_remote_enter_on_phone else R.string.moviotv_xp_nav_addons), false,
                ) {
                    if (page in browsePages) {
                        page = "addons"
                        adding = true
                        showAddonRemote = true
                        addonRemoteServer.start()
                    } else {
                        page = "addons"
                        adding = false
                    }
                }
                if (page != "addons") XpAction(stringResource(R.string.moviotv_xp_refresh), busy) { fetchRows() }
                if (page == "addons") XpAction(stringResource(R.string.moviotv_xp_addon_url_button), false) {
                    adding = true
                    notice = null
                }
                XpAction(stringResource(R.string.moviotv_xp_back), false) {
                    if (page != initialPage) page = initialPage else onBack()
                }
            }
            if (!showBottomNavigation) Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf(
                    "home" to R.string.moviotv_nav_home,
                    "discover" to R.string.moviotv_xp_nav_discover,
                    "movies" to R.string.moviotv_nav_movies,
                    "series" to R.string.moviotv_nav_series,
                    "favorites" to R.string.moviotv_xp_more_favorites,
                    "addons" to R.string.moviotv_xp_nav_addons,
                ).forEach { (key, labelRes) ->
                    XpNavigationItem(stringResource(labelRes), selected = page == key) {
                        page = key
                        if (key == "addons") adding = false
                    }
                }
            }
            if (!showBottomNavigation && (page == "home" || page == "discover" || page == "search")) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 34.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp)).background(XpPanel).padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (searchText.isBlank()) Text(stringResource(R.string.moviotv_xp_search_hint), color = XpMuted, fontSize = 17.sp)
                    BasicTextField(
                        value = searchText,
                        onValueChange = {
                            searchText = it
                            if (it.isNotBlank()) {
                                if (page != "search") returnPage = page
                                page = "search"
                            } else if (page == "search") page = returnPage
                        },
                        singleLine = true,
                        textStyle = TextStyle(color = XpText, fontSize = 17.sp),
                        modifier = Modifier.weight(1f),
                    )
                    Text(stringResource(R.string.moviotv_xp_search_symbol), color = XpText, fontSize = 24.sp,
                        modifier = Modifier.clickable { page = "search" }.padding(start = 8.dp))
                }
            }
            if (page == "discover") {
                val typeLabelResources = mapOf(
                    "movie" to R.string.moviotv_xp_filter_movies,
                    "series" to R.string.moviotv_xp_filter_series,
                    "channel" to R.string.moviotv_xp_filter_channels,
                    "anime" to R.string.moviotv_xp_filter_anime,
                )
                val typeOptions: List<Pair<String, String>> =
                    listOf("all" to context.getString(R.string.moviotv_xp_filter_all)) +
                        availableCatalogRows.map { it.catalog.type }.distinct().map { type ->
                            type to (
                                typeLabelResources[type.lowercase()]?.let(context::getString)
                                    ?: type.replaceFirstChar(Char::uppercase)
                            )
                        }
                val catalogOptions = listOf("all" to context.getString(R.string.moviotv_xp_filter_all)) +
                    availableCatalogRows.distinctBy(::catalogKey).map { row ->
                        catalogKey(row) to context.getString(R.string.moviotv_xp_filter_catalog_label, row.catalog.name, row.addon.name)
                    }
                val genreMenuOptions = listOf("all" to context.getString(R.string.moviotv_xp_filter_any_genre)) +
                    genreOptions.map { it to it }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    XpFilterDropdown(
                        title = stringResource(R.string.moviotv_xp_filter_type),
                        selectedLabel = typeOptions.firstOrNull { it.first == discoveryType }?.second ?: typeOptions.first().second,
                        options = typeOptions,
                        expanded = expandedFilter == "type",
                        modifier = Modifier.weight(1f),
                        onToggle = { expandedFilter = if (expandedFilter == "type") null else "type" },
                        onSelect = { discoveryType = it; discoveryCatalog = "all"; discoveryGenre = "all"; expandedFilter = null },
                    )
                    XpFilterDropdown(
                        title = stringResource(R.string.moviotv_xp_filter_catalog),
                        selectedLabel = catalogOptions.firstOrNull { it.first == discoveryCatalog }?.second ?: catalogOptions.first().second,
                        options = catalogOptions,
                        expanded = expandedFilter == "catalog",
                        modifier = Modifier.weight(1f),
                        onToggle = { expandedFilter = if (expandedFilter == "catalog") null else "catalog" },
                        onSelect = { discoveryCatalog = it; discoveryGenre = "all"; expandedFilter = null },
                    )
                    XpFilterDropdown(
                        title = stringResource(R.string.moviotv_xp_filter_genre),
                        selectedLabel = genreMenuOptions.firstOrNull { it.first == discoveryGenre }?.second ?: genreMenuOptions.first().second,
                        options = genreMenuOptions,
                        expanded = expandedFilter == "genre",
                        modifier = Modifier.weight(1f),
                        onToggle = { expandedFilter = if (expandedFilter == "genre") null else "genre" },
                        onSelect = { discoveryGenre = it; expandedFilter = null },
                    )
                }
            }
            if (page in browsePages) {
                MovioTouchPosterBrowser(
                    title = stringResource(when (page) { "movies" -> R.string.moviotv_nav_movies; "series" -> R.string.moviotv_nav_series; "favorites" -> R.string.content_category_favorites; else -> R.string.moviotv_nav_live_tv }),
                    category = if (page == "favorites") stringResource(R.string.content_category_favorites) else if (page == "movies") {
                        if (discoveryGenre == "all") context.getString(R.string.moviotv_xp_all_movies) else discoveryGenre
                    } else if (page == "series") {
                        if (discoveryGenre == "all") context.getString(R.string.moviotv_xp_all_series) else discoveryGenre
                    } else context.getString(R.string.moviotv_xp_all_live_tv),
                    query = searchText,
                    onSearch = {
                        searchText = it
                        if (it.isNotBlank()) { returnPage = page; page = "search" }
                    },
                    categoryEnabled = page != "favorites" && page != "live" && genreOptions.isNotEmpty(),
                    onCategories = { genrePickerOpen = true },
                    categories = { railModifier ->
                        XpGenreRail(
                            modifier = railModifier,
                            genres = if (page == "favorites") emptyList() else genreOptions,
                            selected = discoveryGenre,
                            allLabel = context.getString(if (page == "movies") R.string.moviotv_xp_all_movies else R.string.moviotv_xp_all_series),
                            focusRequester = categoryRailFocus,
                            rightFocusRequester = firstGridFocus,
                            onSelect = { discoveryGenre = it; genrePickerOpen = false },
                        )
                    },
                    onSort = { changeSort((sortMode + 1) % sortLabels.size) },
                    itemCount = sectionTitles.size,
                    gridState = gridState,
                    categoryFocusRequester = categoryRailFocus,
                    emptyText = stringResource(if (busy || rows.any { loadingRows[rowKey(it)] == true }) R.string.moviotv_xp_loading_catalogue else R.string.moviotv_xp_no_titles),
                    poster = { index, width ->
                        val item = sectionTitles[index]
                        XpPoster(
                            item = item,
                            width = width,
                            focusRequester = if (index == 0) firstGridFocus else null,
                            onLongClick = { openDetails(item, showSources = false) },
                            onVeryLongClick = { openDetails(item, showSources = true) },
                        ) {
                            val liveChannelId = item.id.removePrefix("own-live:").toLongOrNull()
                            if (liveChannelId != null) onPlayLocalLiveChannel(liveChannelId) else openItem(item)
                        }
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
            if (page == "addons" && adding) {
                Dialog(onDismissRequest = { adding = false; notice = null }) {
                    Column(
                        Modifier.fillMaxWidth().widthIn(max = 440.dp)
                            .clip(RoundedCornerShape(22.dp)).background(XpBackground)
                            .border(1.dp, XpAccent.copy(alpha = .5f), RoundedCornerShape(22.dp))
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(stringResource(R.string.moviotv_xp_addon_manifest_title),
                            color = XpText, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.moviotv_xp_addon_paste_help),
                            color = XpMuted, fontSize = 14.sp)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.moviotv_xp_addon_url_button),
                                color = XpText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Box(
                                Modifier.fillMaxWidth().heightIn(min = 54.dp)
                                    .clip(RoundedCornerShape(12.dp)).background(XpPanel)
                                    .border(1.dp, XpAccent.copy(alpha = .65f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 14.dp, vertical = 15.dp),
                            ) {
                                if (address.isBlank()) Text(
                                    stringResource(R.string.moviotv_xp_addon_url_hint), color = XpMuted,
                                    fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                BasicTextField(
                                    value = address,
                                    onValueChange = { address = it; notice = null },
                                    singleLine = true,
                                    textStyle = TextStyle(color = XpText, fontSize = 16.sp),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        notice?.let { Text(it, color = Color(0xFFFFB4AB), fontSize = 14.sp) }
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            XpAction(stringResource(R.string.moviotv_xp_cancel), false) {
                                adding = false; notice = null
                            }
                            XpAction(stringResource(R.string.moviotv_xp_install), busy) {
                                val candidate = address.trim()
                                if (candidate.isBlank()) {
                                    notice = context.getString(R.string.moviotv_xp_enter_addon_url)
                                } else installAddon(candidate)
                            }
                        }
                        if (!compactPhone) {
                            XpAction(stringResource(R.string.moviotv_xp_remote_enter_on_phone), busy) {
                                showAddonRemote = true
                                addonRemoteServer.start()
                            }
                        }
                    }
                }
            }
            notice?.let {
                Text(it, color = Color(0xFFFFB4AB), fontSize = 14.sp, modifier = Modifier.padding(horizontal = 34.dp, vertical = 4.dp))
            }
            if (page in browsePages) {
                // The add-on catalogue rows are rendered as the MovioXP poster grid above.
            } else if (page == "addons") {
                Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = if (compactPhone) 14.dp else 24.dp, vertical = 8.dp)) {
                    // On phone the header is the compact search bar, not the TV toolbar,
                    // so the install action must live at the top of the Add-ons page itself.
                    if (showBottomNavigation) {
                        XpAction(stringResource(R.string.moviotv_xp_addon_url_button), busy) {
                            adding = true
                            notice = null
                        }
                        Spacer(Modifier.height(8.dp))
                        if (centralConnection != null && centralSyncMessage != null) {
                            Text(centralSyncMessage.orEmpty(), color = XpAccent, fontSize = 13.sp)
                            Spacer(Modifier.height(8.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(stringResource(R.string.moviotv_xp_addon_category), color = XpMuted, fontSize = 13.sp)
                    Column {
                        val categoryLabel = when (addonCategory) {
                            "official" -> stringResource(R.string.moviotv_xp_addon_official)
                            "installed" -> stringResource(R.string.moviotv_xp_addon_installed)
                            else -> stringResource(R.string.moviotv_xp_addon_community)
                        }
                        XpAction(stringResource(R.string.moviotv_xp_category_selector, categoryLabel), false) { categoryMenuExpanded = true }
                        if (categoryMenuExpanded) {
                            Column(
                                Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(14.dp))
                                    .background(XpPanel),
                            ) {
                            listOf(
                                "official" to R.string.moviotv_xp_addon_official,
                                "community" to R.string.moviotv_xp_addon_community,
                                "installed" to R.string.moviotv_xp_addon_installed,
                            ).forEach { (key, label) ->
                                Text(
                                    text = stringResource(label),
                                    color = if (addonCategory == key) XpAccent else XpText,
                                    fontSize = 16.sp,
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        addonCategory = key
                                        categoryMenuExpanded = false
                                        notice = null
                                    }.padding(horizontal = 14.dp, vertical = 12.dp),
                                )
                            }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(XpPanel)
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (addonFilter.isBlank()) Text(stringResource(R.string.moviotv_xp_addon_search_hint), color = XpMuted)
                        BasicTextField(
                            value = addonFilter,
                            onValueChange = { addonFilter = it },
                            singleLine = true,
                            textStyle = TextStyle(color = XpText, fontSize = 16.sp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    val installed = addonCategory == "installed"
                    val addonEntries = if (installed) {
                        addons.filter { it.name.contains(addonFilter, true) || it.description.contains(addonFilter, true) }
                    } else {
                        directoryAddons.filter { it.name.contains(addonFilter, true) || it.description.contains(addonFilter, true) }
                    }
                    if (!installed && directoryLoading) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.moviotv_xp_addon_directory_loading), color = XpMuted, fontSize = 16.sp)
                        }
                    } else {
                        LazyColumn(
                            Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 12.dp),
                        ) {
                            if (addonEntries.isEmpty()) {
                                item {
                                    Text(
                                        stringResource(if (installed) R.string.moviotv_xp_addon_none_installed else R.string.moviotv_xp_addon_directory_empty),
                                        color = XpMuted,
                                        fontSize = 15.sp,
                                        modifier = Modifier.padding(vertical = 12.dp),
                                    )
                                }
                            } else if (installed) {
                                items(addons.filter { it.name.contains(addonFilter, true) || it.description.contains(addonFilter, true) }, key = { "installed:${it.url}" }) { addon ->
                                    XpAddonInfoCard(
                                        name = addon.name,
                                        version = addon.version,
                                        description = addon.description,
                                        types = addon.types,
                                        logo = addon.logo,
                                        action = stringResource(if (addon.builtIn) R.string.moviotv_xp_addon_builtin else R.string.moviotv_xp_remove),
                                        disabled = addon.builtIn || busy,
                                    ) { removeAddon(addon) }
                                }
                            } else {
                                items(directoryAddons.filter { it.name.contains(addonFilter, true) || it.description.contains(addonFilter, true) }, key = { "directory:${it.url}" }) { addon ->
                                    val alreadyInstalled = addons.any { it.url == addon.url }
                                    XpAddonInfoCard(
                                        name = addon.name,
                                        version = addon.version,
                                        description = addon.description,
                                        types = addon.types,
                                        logo = addon.logo,
                                        action = stringResource(if (alreadyInstalled) R.string.moviotv_xp_addon_installed_badge else R.string.moviotv_xp_install),
                                        disabled = alreadyInstalled || busy,
                                    ) { installAddon(addon.url) }
                                }
                            }
                        }
                    }
                    Text(stringResource(R.string.moviotv_xp_addon_paste_help), color = XpMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
                }
            } else if (page == "central") {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = 34.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(stringResource(R.string.moviotv_xp_central_account_title), color = XpText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.moviotv_xp_central_account_help),
                        color = XpMuted, fontSize = 16.sp,
                    )
                    if (centralConnection == null) {
                        XpCentralField(value = centralApiBase, label = stringResource(R.string.moviotv_xp_central_api_label), onValueChange = { centralApiBase = it })
                        XpCentralField(value = centralUsername, label = stringResource(R.string.moviotv_xp_central_username_label), onValueChange = { centralUsername = it })
                        XpCentralField(value = centralPassword, label = stringResource(R.string.moviotv_xp_central_password_label), onValueChange = { centralPassword = it }, password = true)
                        XpAction(stringResource(R.string.moviotv_xp_central_login), centralSyncBusy) {
                            if (centralApiBase.isBlank() || centralUsername.isBlank() || centralPassword.isBlank()) {
                                centralSyncMessage = context.getString(R.string.moviotv_xp_central_details_required)
                            } else {
                                centralSyncBusy = true
                                centralSyncMessage = null
                                scope.launch {
                                    runCatching { XpCentralAddonSync.signIn(centralApiBase, centralUsername, centralPassword) }
                                        .onSuccess {
                                            XpCentralConnectionStore.write(context, it)
                                            centralConnection = it
                                            centralApiBase = it.apiBase.removeSuffix("/")
                                            centralPassword = ""
                                            centralSyncMessage = context.getString(R.string.moviotv_xp_central_connected_as, it.username)
                                            centralSyncBusy = false
                                            syncAddonsWithCentral()
                                        }
                                        .onFailure {
                                            centralSyncMessage = it.message ?: context.getString(R.string.moviotv_xp_central_sync_failed)
                                            centralSyncBusy = false
                                        }
                                }
                            }
                        }
                        XpAction(stringResource(R.string.moviotv_xp_central_register), centralSyncBusy) {
                            if (centralApiBase.isBlank() || centralUsername.isBlank() || centralPassword.isBlank()) {
                                centralSyncMessage = context.getString(R.string.moviotv_xp_central_details_required)
                            } else if (centralUsername.trim().length < 3 || centralPassword.length < 12) {
                                centralSyncMessage = context.getString(R.string.moviotv_xp_central_register_requirements)
                            } else {
                                centralSyncBusy = true
                                centralSyncMessage = null
                                scope.launch {
                                    runCatching { XpCentralAddonSync.register(centralApiBase, centralUsername, centralPassword) }
                                        .onSuccess { centralSyncMessage = it.ifBlank { context.getString(R.string.moviotv_xp_central_registration_complete) } }
                                        .onFailure { centralSyncMessage = context.getString(R.string.moviotv_xp_central_sync_failed) }
                                    centralSyncBusy = false
                                }
                            }
                        }
                        Text(
                            stringResource(R.string.moviotv_xp_central_registration_help),
                            color = XpMuted, fontSize = 14.sp,
                        )
                    } else {
                        Text(stringResource(R.string.moviotv_xp_central_connected_as, centralConnection?.username.orEmpty()), color = XpAccent, fontSize = 17.sp)
                        XpAction(stringResource(R.string.moviotv_xp_central_sync_now), centralSyncBusy) { syncAddonsWithCentral() }
                        XpAction(stringResource(R.string.movioxp_logout_central), centralSyncBusy) {
                            signOutCentral()
                        }
                    }
                    centralSyncMessage?.let { Text(it, color = XpAccent, fontSize = 14.sp) }
                }
            } else if (page == "settings") {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = if (compactPhone) 14.dp else 28.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(stringResource(R.string.moviotv_xp_nav_settings), color = XpText,
                        fontSize = if (compactPhone) 24.sp else 27.sp, fontWeight = FontWeight.Bold)
                    XpSettingsAccordion(
                        title = stringResource(R.string.movioxp_home_settings_title),
                        summary = stringResource(R.string.movioxp_home_settings_summary),
                        expanded = expandedSettingsGroup == 4,
                        onToggle = { expandedSettingsGroup = if (expandedSettingsGroup == 4) null else 4 },
                    ) {
                        Text(stringResource(R.string.movioxp_home_settings_hint), color = XpMuted, fontSize = 13.sp)
                        sortLabels.forEachIndexed { index, label -> XpSettingsOption(title = label, selected = sortMode == index) { changeSort(index) } }
                        XpSettingsOption(
                            title = stringResource(R.string.movioxp_home_default_rows),
                            selected = homeSettings.showDefaultCatalogs,
                        ) {
                            homeSettings = homeSettings.copy(showDefaultCatalogs = !homeSettings.showDefaultCatalogs)
                            XpHomeConfig.save(context, favoritesProfileId, homeSettings)
                        }
                        homeSettings.order.forEachIndexed { index, id ->
                            val category = XpHomeConfig.categories.firstOrNull { it.id == id }
                            val title = category?.let { stringResource(it.labelRes) }
                                ?: XpHomeConfig.genreName(id)
                            if (title != null) {
                                XpHomeCategoryControls(
                                    title = title,
                                    enabled = id !in homeSettings.hidden,
                                    canMoveUp = index > 0,
                                    canMoveDown = index < homeSettings.order.lastIndex,
                                    onToggle = {
                                        val hidden = homeSettings.hidden.toMutableSet()
                                        if (!hidden.add(id)) hidden.remove(id)
                                        homeSettings = homeSettings.copy(hidden = hidden)
                                        XpHomeConfig.save(context, favoritesProfileId, homeSettings)
                                    },
                                    onMoveUp = {
                                        homeSettings = XpHomeConfig.move(homeSettings, index, -1)
                                        XpHomeConfig.save(context, favoritesProfileId, homeSettings)
                                    },
                                    onMoveDown = {
                                        homeSettings = XpHomeConfig.move(homeSettings, index, 1)
                                        XpHomeConfig.save(context, favoritesProfileId, homeSettings)
                                    },
                                )
                                if (XpHomeConfig.genreName(id) != null) {
                                    XpSettingsOption(
                                        title = stringResource(R.string.movioxp_home_remove_category, title),
                                    ) {
                                        homeSettings = XpHomeConfig.remove(homeSettings, id)
                                        XpHomeConfig.save(context, favoritesProfileId, homeSettings)
                                    }
                                }
                            }
                        }
                        XpSettingsOption(
                            title = stringResource(R.string.movioxp_home_add_category),
                            selected = showAddHomeCategory,
                        ) { showAddHomeCategory = !showAddHomeCategory }
                        if (showAddHomeCategory) {
                            val available = availableHomeGenres.filter { genre ->
                                val id = XpHomeConfig.idForGenre(genre)
                                id != null && id !in homeSettings.order
                            }
                            if (available.isEmpty()) {
                                Text(stringResource(R.string.movioxp_home_no_categories),
                                    color = XpMuted, fontSize = 13.sp)
                            }
                            available.forEach { genre ->
                                XpSettingsOption(genre) {
                                    val id = XpHomeConfig.idForGenre(genre)
                                    if (id != null) {
                                        homeSettings = XpHomeConfig.add(homeSettings, id)
                                        XpHomeConfig.save(context, favoritesProfileId, homeSettings)
                                        showAddHomeCategory = false
                                    }
                                }
                            }
                        }
                    }
                    XpSettingsAccordion(
                        title = stringResource(R.string.moviotv_xp_settings_display_heading),
                        summary = stringResource(R.string.moviotv_xp_settings_display_summary),
                        expanded = expandedSettingsGroup == 0,
                        onToggle = { expandedSettingsGroup = if (expandedSettingsGroup == 0) null else 0 },
                    ) {
                        XpAppearanceOptions(onMore = {
                            onOpenSettingsGroup?.invoke(tv.own.owntv.features.shell.components.SettingsGroup.APPEARANCE.ordinal)
                                ?: onOpenAppSettings?.invoke()
                        })
                        XpSettingsOption(stringResource(R.string.moviotv_xp_settings_display_layout_more)) {
                            onOpenSettingsGroup?.invoke(tv.own.owntv.features.shell.components.SettingsGroup.LAYOUT.ordinal)
                                ?: onOpenAppSettings?.invoke()
                        }
                    }
                    XpSettingsAccordion(
                        title = stringResource(R.string.moviotv_xp_settings_playback_heading),
                        summary = stringResource(R.string.moviotv_xp_settings_playback_summary),
                        expanded = expandedSettingsGroup == 1,
                        onToggle = { expandedSettingsGroup = if (expandedSettingsGroup == 1) null else 1 },
                    ) {
                        XpSettingsFieldLabel(stringResource(R.string.moviotv_xp_auto_select_source))
                        XpSettingsOption(
                            title = stringResource(if (autoSelectSource)
                                R.string.moviotv_xp_source_auto_enabled else R.string.moviotv_xp_source_auto_disabled),
                            selected = autoSelectSource,
                        ) {
                            autoSelectSource = !autoSelectSource
                            playbackPrefs.edit().putBoolean("auto_select_source", autoSelectSource).apply()
                            XpCentralDataSync.markSetting(context, favoritesProfileId, "auto_select_source", autoSelectSource)
                        }
                        XpSettingsFieldLabel(stringResource(R.string.moviotv_xp_preferred_addon))
                        val chosenAddon = addons.firstOrNull { it.url == preferredAddonUrl }?.name
                        XpSettingsOption(
                            stringResource(R.string.moviotv_xp_settings_chosen_addon,
                                chosenAddon ?: stringResource(R.string.moviotv_xp_any_addon)),
                            selected = showPreferredAddons,
                        ) { showPreferredAddons = !showPreferredAddons }
                        if (showPreferredAddons) {
                            XpSettingsOption(stringResource(R.string.moviotv_xp_any_addon),
                                selected = preferredAddonUrl.isBlank()) {
                                preferredAddonUrl = ""
                                playbackPrefs.edit().putString("preferred_addon", "").apply()
                                XpCentralDataSync.markSetting(context, favoritesProfileId, "preferred_addon", "")
                                showPreferredAddons = false
                            }
                            addons.forEach { addon ->
                                XpSettingsOption(addon.name, selected = preferredAddonUrl == addon.url) {
                                    preferredAddonUrl = addon.url
                                    playbackPrefs.edit().putString("preferred_addon", addon.url).apply()
                                    XpCentralDataSync.markSetting(context, favoritesProfileId, "preferred_addon", addon.url)
                                    showPreferredAddons = false
                                }
                            }
                        }
                        Text(stringResource(R.string.moviotv_xp_settings_resume_hint),
                            color = XpMuted, fontSize = 13.sp)
                    }
                    XpSettingsAccordion(
                        title = stringResource(R.string.moviotv_xp_settings_account_heading),
                        summary = stringResource(R.string.moviotv_xp_settings_account_summary),
                        expanded = expandedSettingsGroup == 2,
                        onToggle = { expandedSettingsGroup = if (expandedSettingsGroup == 2) null else 2 },
                    ) {
                        if (centralConnection == null) {
                            Text(stringResource(R.string.moviotv_xp_central_addons_help),
                                color = XpMuted, fontSize = 13.sp)
                            XpCentralField(
                                value = centralApiBase,
                                label = stringResource(R.string.moviotv_xp_central_api_label),
                                onValueChange = { centralApiBase = it },
                            )
                            XpCentralField(
                                value = centralUsername,
                                label = stringResource(R.string.moviotv_xp_central_username_label),
                                onValueChange = { centralUsername = it },
                            )
                            XpCentralField(
                                value = centralPassword,
                                label = stringResource(R.string.moviotv_xp_central_password_label),
                                onValueChange = { centralPassword = it },
                                password = true,
                            )
                        } else {
                            Text(stringResource(R.string.moviotv_xp_central_connected_as,
                                centralConnection?.username.orEmpty()), color = XpAccent, fontSize = 15.sp)
                        }
                        XpSettingsOption(
                            title = stringResource(if (centralConnection == null)
                                R.string.moviotv_xp_central_connect_sync else R.string.moviotv_xp_central_sync_now),
                            enabled = !centralSyncBusy,
                        ) { syncAddonsWithCentral() }
                        if (centralConnection == null) {
                            XpSettingsOption(stringResource(R.string.moviotv_xp_central_register),
                                enabled = !centralSyncBusy) {
                                if (centralApiBase.isBlank() || centralUsername.isBlank() || centralPassword.isBlank()) {
                                    centralSyncMessage = context.getString(R.string.moviotv_xp_central_details_required)
                                } else if (centralUsername.trim().length < 3 || centralPassword.length < 12) {
                                    centralSyncMessage = context.getString(R.string.moviotv_xp_central_register_requirements)
                                } else {
                                    centralSyncBusy = true
                                    scope.launch {
                                        runCatching { XpCentralAddonSync.register(centralApiBase, centralUsername, centralPassword) }
                                            .onSuccess { centralSyncMessage = it.ifBlank {
                                                context.getString(R.string.moviotv_xp_central_registration_complete)
                                            } }
                                            .onFailure { centralSyncMessage = context.getString(R.string.moviotv_xp_central_sync_failed) }
                                        centralSyncBusy = false
                                    }
                                }
                            }
                        }
                        if (centralConnection != null) {
                            XpSettingsOption(stringResource(R.string.moviotv_xp_central_disconnect),
                                enabled = !centralSyncBusy) {
                                signOutCentral()
                            }
                        }
                        if (centralSyncBusy) Text(stringResource(R.string.moviotv_xp_central_syncing),
                            color = XpMuted, fontSize = 13.sp)
                        centralSyncMessage?.let { Text(it, color = XpAccent, fontSize = 13.sp) }
                    }
                    XpSettingsAccordion(
                        title = stringResource(R.string.moviotv_xp_settings_advanced_heading),
                        summary = stringResource(R.string.moviotv_xp_settings_advanced_summary),
                        expanded = expandedSettingsGroup == 3,
                        onToggle = { expandedSettingsGroup = if (expandedSettingsGroup == 3) null else 3 },
                    ) {
                        XpSettingsOption(stringResource(R.string.moviotv_xp_settings_all)) {
                            onOpenAppSettings?.invoke()
                        }
                        Text(stringResource(R.string.moviotv_xp_settings_advanced_hint),
                            color = XpMuted, fontSize = 13.sp)
                    }
                    XpSettingsOption(stringResource(R.string.moviotv_xp_about_heading)) {
                        showXpAbout = true
                    }
                    Spacer(Modifier.height(10.dp))
                }
                if (showXpAbout) XpAboutDialog(onDismiss = { showXpAbout = false })
            } else if (page == "library") {
                if (libraryTitles.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.moviotv_xp_library_empty), color = XpMuted, fontSize = 16.sp)
                    }
                } else {
                    Column(Modifier.weight(1f).fillMaxWidth()) {
                        Text(
                            stringResource(R.string.moviotv_xp_nav_library),
                            color = XpText, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 22.dp, top = 10.dp, bottom = 8.dp),
                        )
                        BoxWithConstraints(
                            Modifier.weight(1f).fillMaxWidth()
                                .pointerInput(libraryZoomKey, showBottomNavigation) {
                                    // Preserve one-finger scrolling and remote navigation.
                                    if (showBottomNavigation) awaitEachGesture {
                                        do {
                                            val event = awaitPointerEvent()
                                            if (event.changes.count { it.pressed } >= 2) {
                                                val next = (libraryScaleNow * event.calculateZoom()).coerceIn(0.65f, 1.85f)
                                                if (kotlin.math.abs(next - libraryScaleNow) > 0.003f) {
                                                    libraryScale = next
                                                    libraryPrefs.edit().putFloat(libraryZoomKey, next).apply()
                                                    XpCentralDataSync.markSetting(context, favoritesProfileId, libraryZoomKey, next)
                                                    event.changes.forEach { it.consume() }
                                                }
                                            }
                                        } while (event.changes.any { it.pressed })
                                    }
                                },
                        ) {
                            val targetWidth = (if (compactPhone) 142.dp else 186.dp) * libraryScale
                            val columns = ((maxWidth - 36.dp).value / (targetWidth.value + 12f))
                                .toInt().coerceIn(2, 12)
                            val width = ((maxWidth - 36.dp - 12.dp * (columns - 1)) / columns)
                                .coerceAtLeast(72.dp)
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(columns),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                items(libraryTitles.size, key = { index ->
                                    val title = libraryTitles[index]
                                    "library:${title.type}:${title.id}"
                                }) { index ->
                                    val title = libraryTitles[index]
                                    XpPoster(title, width = width,
                                        onLongClick = { openDetails(title, false) },
                                        onVeryLongClick = { openDetails(title, true) }) { openItem(title) }
                                }
                            }
                        }
                    }
                }
            } else if (page == "search") {
                Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp)) {
                    when {
                        searchText.trim().length < 2 -> Text(stringResource(R.string.moviotv_xp_search_minimum), color = XpMuted, fontSize = 16.sp)
                        searchLoading -> Text(stringResource(R.string.moviotv_xp_searching_addons), color = XpMuted, fontSize = 16.sp)
                        searchableCatalogCount == 0 -> Text(stringResource(R.string.moviotv_xp_search_unsupported), color = XpMuted, fontSize = 16.sp)
                        searchHits.isEmpty() -> Text(stringResource(R.string.moviotv_xp_no_search_matches), color = XpMuted, fontSize = 16.sp)
                        else -> {
                            if (searchFailureCount > 0) {
                                Text(
                                    pluralStringResource(R.plurals.moviotv_xp_search_partial, searchFailureCount, searchFailureCount),
                                    color = XpMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp),
                                )
                            }
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 14.dp)) {
                                items(searchHits, key = { "${it.addon.url}|${it.meta.type}:${it.meta.id}" }) { hit ->
                                    XpSearchResultCard(hit) { selected = hit.meta }
                                }
                            }
                        }
                    }
                }
            } else if (rows.isEmpty() && page != "home") {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.moviotv_xp_empty_title), color = XpText, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.moviotv_xp_empty_description), color = XpMuted, fontSize = 16.sp)
                    }
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth()
                        .pointerInput(homeZoomKey, showBottomNavigation) {
                            if (showBottomNavigation) awaitEachGesture {
                                do {
                                    val event = awaitPointerEvent()
                                    if (event.changes.count { it.pressed } >= 2) {
                                        val next = (homeScaleNow * event.calculateZoom()).coerceIn(0.65f, 1.85f)
                                        if (kotlin.math.abs(next - homeScaleNow) > 0.003f) {
                                            homeScale = next
                                            libraryPrefs.edit().putFloat(homeZoomKey, next).apply()
                                            XpCentralDataSync.markSetting(context, favoritesProfileId, homeZoomKey, next)
                                            event.changes.forEach { it.consume() }
                                        }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        },
                    contentPadding = PaddingValues(bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (page == "home" && continueTitles.isNotEmpty()) {
                        item(key = "xp-continue-watching") {
                            Column {
                                Text(stringResource(R.string.moviotv_xp_continue_watching), color = XpText, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 24.dp, top = 2.dp))
                                LazyRow(contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(continueTitles, key = { "continue:${it.type}:${it.id}" }) { title ->
                                        XpPoster(title, width = 142.dp * homeScale,
                                            onLongClick = { openDetails(title, false) },
                                            onVeryLongClick = { openDetails(title, true) }) { openItem(title) }
                                    }
                                }
                            }
                        }
                    }

                    if (page == "home") {
                        items(visibleHomeIds, key = { "discover:$it" }) { id ->
                            val category = XpHomeConfig.categories.firstOrNull { it.id == id }
                            val label = category?.let { stringResource(it.labelRes) }
                                ?: XpHomeConfig.genreName(id)
                            val source = homeSources[id]
                            if (label != null) {
                                Column {
                                    Text(
                                        label, color = XpText,
                                        fontSize = 20.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(start = 24.dp, top = 6.dp),
                                    )
                                    if (source == null) {
                                        Text(
                                            stringResource(R.string.movioxp_discover_no_source),
                                            color = XpMuted, fontSize = 13.sp,
                                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 9.dp),
                                        )
                                    } else {
                                        val cacheKey = "$id|${source.row.addon.url}|${source.row.catalog.id}|${source.parameters}"
                                        val titles = sortedTitles(discoverMetas[cacheKey].orEmpty())
                                        if (titles.isEmpty()) {
                                            Text(
                                                stringResource(if (discoverLoading[cacheKey] == true)
                                                    R.string.moviotv_xp_loading_catalogue else R.string.moviotv_xp_no_titles),
                                                color = XpMuted, fontSize = 13.sp,
                                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 9.dp),
                                            )
                                        } else {
                                            LazyRow(
                                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            ) {
                                                items(titles, key = { "${it.type}:${it.id}" }) { item ->
                                                    XpPoster(item, width = 142.dp * homeScale,
                                                        onLongClick = { openDetails(item, false) },
                                                        onVeryLongClick = { openDetails(item, true) }) { openItem(item) }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (page == "home" && rows.isEmpty() && visibleHomeIds.isEmpty() && continueTitles.isEmpty()) {
                        item(key = "xp-home-empty") {
                            Text(stringResource(R.string.moviotv_xp_empty_description), color = XpMuted, fontSize = 16.sp,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp))
                        }
                    }
                    items(rows, key = { rowKey(it) }) { row ->
                        val key = rowKey(row)
                        val titles = sortedTitles(metas[key].orEmpty())
                        Column {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 34.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(row.catalog.name, color = XpText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(10.dp))
                                Text(row.addon.name, color = XpMuted, fontSize = 13.sp)
                            }
                            if (titles.isEmpty()) {
                                Text(
                                    if (loadingRows[key] == true) stringResource(R.string.moviotv_xp_loading_catalogue) else stringResource(R.string.moviotv_xp_no_titles),
                                    color = XpMuted, fontSize = 14.sp,
                                    modifier = Modifier.padding(horizontal = 34.dp, vertical = 8.dp),
                                )
                            } else {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 34.dp, vertical = 7.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(titles, key = { "${it.type}:${it.id}" }) { item ->
                                        XpPoster(item, width = 142.dp * homeScale,
                                            onLongClick = { openDetails(item, false) },
                                            onVeryLongClick = { openDetails(item, true) }) { openItem(item) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (showBottomNavigation) Row(
                Modifier.fillMaxWidth().background(XpBackground.copy(alpha = .94f))
                    .padding(horizontal = if (compactPhone) 4.dp else 18.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                    listOf(
                        Triple("home", R.string.moviotv_nav_home, OwnTVIcon.HOME),
                        Triple("discover", R.string.moviotv_xp_nav_discover, OwnTVIcon.TREND),
                        Triple("library", R.string.moviotv_xp_nav_library, OwnTVIcon.PLAYLIST),
                        Triple("addons", R.string.moviotv_xp_nav_addons, OwnTVIcon.ADD),
                        Triple("app_settings", R.string.moviotv_xp_nav_settings, OwnTVIcon.GEAR),
                    ).forEach { (key, labelRes, icon) ->
                        XpNavigationItem(stringResource(labelRes), icon, selected = page == (if (key == "app_settings") "settings" else key),
                            compact = compactPhone, focusRequester = bottomNavigationFocus.takeIf { key == "home" }) {
                            page = if (key == "app_settings") "settings" else key
                            if (key == "addons") adding = false
                    }
                }
            }
        }
        if (showAccountMenu) {
            Dialog(onDismissRequest = { showAccountMenu = false }) {
                Column(
                    Modifier.widthIn(min = 270.dp, max = 420.dp).clip(RoundedCornerShape(18.dp))
                        .background(XpBackground).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.movioxp_account_menu),
                        color = XpText, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    if (centralConnection != null) {
                        Text(
                            stringResource(R.string.moviotv_xp_central_connected_as,
                                centralConnection?.username.orEmpty()),
                            color = XpAccent, fontSize = 15.sp,
                        )
                        XpAction(stringResource(R.string.movioxp_switch_profile), onSwitchProfile == null) {
                            showAccountMenu = false
                            onSwitchProfile?.invoke()
                        }
                        XpAction(stringResource(R.string.moviotv_xp_central_account_title), false) {
                            showAccountMenu = false
                            page = "central"
                        }
                        XpAction(stringResource(R.string.movioxp_logout_central), centralSyncBusy) {
                            signOutCentral()
                        }
                    } else {
                        XpAction(stringResource(R.string.moviotv_xp_central_login), false) {
                            showAccountMenu = false
                            page = "central"
                        }
                        XpAction(stringResource(R.string.movioxp_switch_profile), onSwitchProfile == null) {
                            showAccountMenu = false
                            onSwitchProfile?.invoke()
                        }
                    }
                    XpAction(stringResource(R.string.moviotv_xp_back), false) {
                        showAccountMenu = false
                    }
                }
            }
        }
        if (genrePickerOpen) {
            Dialog(onDismissRequest = { genrePickerOpen = false }) {
                Column(
                    Modifier.width(340.dp).heightIn(max = 560.dp)
                        .clip(RoundedCornerShape(18.dp)).background(XpBackground).padding(16.dp),
                ) {
                    Text(stringResource(R.string.moviotv_categories), color = XpText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    XpGenreRail(
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 10.dp),
                        genres = genreOptions,
                        selected = discoveryGenre,
                        allLabel = context.getString(if (page == "movies") R.string.moviotv_xp_all_movies else R.string.moviotv_xp_all_series),
                        focusRequester = categoryRailFocus,
                        rightFocusRequester = firstGridFocus,
                        onSelect = { discoveryGenre = it; genrePickerOpen = false },
                    )
                }
            }
        }
        if (showAddonRemote) {
            Dialog(onDismissRequest = { showAddonRemote = false; addonRemoteServer.close() }) {
                Column(
                    Modifier.width(560.dp).clip(RoundedCornerShape(20.dp)).background(XpBackground).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.moviotv_xp_remote_tv_title), color = XpText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.moviotv_xp_remote_tv_instructions),
                        color = XpMuted, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                    )
                    when {
                        addonRemoteState == null -> Text(stringResource(R.string.moviotv_xp_remote_opening), color = XpMuted, fontSize = 16.sp)
                        addonRemoteState?.failure != null -> Text(addonRemoteState?.failure.orEmpty(), color = Color(0xFFFFB4AB), fontSize = 16.sp)
                        else -> {
                            val link = addonRemoteState!!
                            link.qr?.let { qr ->
                                Image(
                                    bitmap = qr.asImageBitmap(),
                                    contentDescription = context.getString(R.string.moviotv_xp_remote_qr_description),
                                    modifier = Modifier.size(220.dp).clip(RoundedCornerShape(12.dp)),
                                )
                            }
                            Text(link.url, color = XpText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
                            Text(stringResource(R.string.moviotv_xp_remote_tv_code, link.pin), color = XpAccent, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                    XpAction(stringResource(R.string.moviotv_xp_cancel), false) { showAddonRemote = false; addonRemoteServer.close() }
                }
            }
        }
        selected?.let { item ->
            Dialog(onDismissRequest = { selected = null; manualSourceSelection = false; showManualSources = false }) {
            Column(
                Modifier.fillMaxWidth(0.94f).widthIn(max = 620.dp).heightIn(max = 700.dp).clip(RoundedCornerShape(22.dp))
                    .background(XpBackground).verticalScroll(rememberScrollState()).focusGroup().padding(28.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(item.name, color = XpText, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                XpAction(
                    stringResource(if (favoriteTitles.any { it.type == item.type && it.id == item.id }) R.string.moviotv_xp_remove_favorite else R.string.moviotv_xp_add_favorite),
                    disabled = false,
                    focusRequester = detailFocus,
                ) { toggleFavorite(item) }
                Text(
                    listOfNotNull(item.type.replaceFirstChar { it.uppercase() }, item.releaseInfo, item.rating?.let { context.getString(R.string.moviotv_xp_rating, it) })
                        .joinToString(stringResource(R.string.moviotv_xp_meta_separator)),
                    color = XpAccent, fontSize = 17.sp, modifier = Modifier.padding(top = 8.dp),
                )
                item.description?.let { Text(it, color = XpMuted, fontSize = 17.sp, modifier = Modifier.padding(top = 16.dp)) }
                if (showManualSources) Spacer(Modifier.height(22.dp))
                if (showManualSources) Text(stringResource(R.string.moviotv_xp_stream_sources), color = XpText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (showManualSources) {
                when {
                    streamsLoading -> Text(stringResource(R.string.moviotv_xp_loading_streams), color = XpMuted, fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp))
                    selectedStreams.isEmpty() -> Text(stringResource(R.string.moviotv_xp_no_streams), color = XpMuted, fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp))
                    else -> androidx.compose.foundation.lazy.LazyColumn(
                        Modifier.fillMaxWidth().heightIn(max = 250.dp).padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        itemsIndexed(selectedStreams) { index, stream ->
                            val resultLabel = stringResource(R.string.moviotv_xp_stream_result, stream.label, stream.addon.name)
                            val action = when {
                                stream.url == null -> stringResource(R.string.moviotv_xp_stream_torrent_unsupported, resultLabel)
                                stream.detail != null -> stringResource(R.string.moviotv_xp_stream_result_detail, resultLabel, stream.detail)
                                else -> resultLabel
                            }
                            XpAction(action, disabled = stream.url == null, focusRequester = if (index == 0) firstSourceFocus else null) {
                                stream.url?.let { playable ->
                                    XpContinueStore.add(context, favoritesProfileId, item)
                                    continueTitles = XpContinueStore.read(context, favoritesProfileId)
                                    selected = null
                                    manualSourceSelection = false
                                    showManualSources = false
                                    if (onPlayStreamCandidates != null) {
                                        onPlayStreamCandidates(
                                            listOf(XpPlaybackSource(playable, stream.label, stream.addon.name,
                                                XpSourcePreference.identity(stream.addon.url, stream.label),
                                                userSelected = true,
                                                legacyIdentity = XpSourcePreference.legacyIdentity(stream.addon.url, stream.label, stream.detail))),
                                            item.name, item.type, item.releaseInfo, item.id, item.poster, item.positionMs,
                                        )
                                    } else {
                                        onPlayStream(playable, item.name, item.type, item.releaseInfo, item.id, item.poster, item.positionMs)
                                    }
                                }
                            }
                        }
                    }
                }
                }
                XpAction(stringResource(R.string.moviotv_xp_back_to_catalogues), false) {
                    selected = null; manualSourceSelection = false; showManualSources = false
                }
            }
            }
        }
    }
}

@Composable
private fun RowScope.XpNavigationItem(
    label: String,
    icon: OwnTVIcon = OwnTVIcon.TILES,
    selected: Boolean,
    focusRequester: FocusRequester? = null,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier.weight(1f).padding(horizontal = if (compact) 0.dp else 2.dp).clip(RoundedCornerShape(12.dp))
            .background(if (focused || selected) (if (focused) XpFocus else XpAccent).copy(alpha = if (focused) .32f else .20f) else Color.Transparent)
            .border(if (focused) 3.dp else 1.dp, if (focused) XpFocus else Color.Transparent, RoundedCornerShape(12.dp))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable(onClick = onClick).onFocusChanged { focused = it.isFocused }.focusable()
            .padding(horizontal = if (compact) 1.dp else 5.dp, vertical = if (compact) 7.dp else 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        DrawOwnTVIcon(icon, if (focused) XpFocus else if (selected) XpAccent else XpMuted, Modifier.size(if (compact) 21.dp else 23.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = if (focused) XpFocus else if (selected) XpText else XpMuted, fontSize = if (compact) 10.sp else 12.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun XpFilterDropdown(
    title: String,
    selectedLabel: String,
    options: List<Pair<String, String>>,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
    onSelect: (String) -> Unit,
) {
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                .background(XpPanel).clickable(onClick = onToggle).focusable()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(selectedLabel, color = XpText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(if (expanded) R.string.moviotv_xp_filter_expanded else R.string.moviotv_xp_filter_collapsed), color = XpMuted, fontSize = 13.sp)
        }
        if (expanded) {
            Column(
                Modifier.fillMaxWidth().padding(top = 4.dp).heightIn(max = 260.dp)
                    .clip(RoundedCornerShape(12.dp)).background(XpPanel).verticalScroll(rememberScrollState()),
            ) {
                Text(title, color = XpMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp))
                options.distinctBy { it.first }.forEach { (key, label) ->
                    Text(label, color = if (label == selectedLabel) XpAccent else XpText, fontSize = 14.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(key) }
                            .padding(horizontal = 12.dp, vertical = 9.dp))
                }
            }
        }
    }
}

@Composable
private fun XpSearchResultCard(hit: XpSearchHit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (focused) XpAccent.copy(alpha = .18f) else XpPanel)
            .clickable(onClick = onClick).onFocusChanged { focused = it.isFocused }.focusable()
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(58.dp, 82.dp).clip(RoundedCornerShape(8.dp)).background(XpBackground)) {
            hit.meta.poster?.let {
                AsyncImage(model = it, contentDescription = hit.meta.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(hit.meta.name, color = XpText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(hit.meta.type.replaceFirstChar(Char::uppercase), hit.meta.releaseInfo, hit.addon.name)
                    .joinToString(stringResource(R.string.moviotv_xp_meta_separator)),
                color = XpMuted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun XpGenreRail(
    modifier: Modifier,
    genres: List<String>,
    selected: String,
    allLabel: String,
    focusRequester: FocusRequester,
    rightFocusRequester: FocusRequester,
    onSelect: (String) -> Unit,
) {
    Column(modifier.background(XpPanel).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.moviotv_categories), color = XpMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item(key = "all") { XpGenreRow(allLabel, selected == "all", focusRequester, rightFocusRequester) { onSelect("all") } }
            items(genres.distinct().sorted(), key = { "genre:$it" }) { genre ->
                XpGenreRow(genre, selected.equals(genre, true), null, rightFocusRequester) { onSelect(genre) }
            }
        }
    }
}

@Composable
private fun XpGenreRow(label: String, selected: Boolean, focusRequester: FocusRequester?, rightFocusRequester: FocusRequester, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        label,
        color = if (focused) XpFocus else if (selected) XpText else XpMuted,
        fontSize = 15.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (selected || focused) (if (focused) XpFocus else XpAccent).copy(alpha = .22f) else Color.Transparent)
            .border(if (focused) 2.dp else 1.dp, if (focused) XpFocus else Color.Transparent, RoundedCornerShape(10.dp))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                    event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                    runCatching { rightFocusRequester.requestFocus() }
                    true
                } else false
            }
            .clickable(onClick = onClick).onFocusChanged { focused = it.isFocused }.focusable()
            .padding(horizontal = 10.dp, vertical = 10.dp),
    )
}

@Composable
private fun XpAccountProfileBadge(
    displayName: String,
    signedIn: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val label = stringResource(R.string.movioxp_account_menu)
    val letter = displayName.ifBlank { stringResource(R.string.movioxp_guest_label) }
        .take(1).uppercase()
    val shape = RoundedCornerShape(if (compact) 15.dp else 20.dp)
    Box(
        Modifier.size(if (compact) 42.dp else 56.dp)
            .clip(shape).background(if (focused) Color(0xFFFFD078) else Color(0xFFFFAB32))
            .border(if (focused) 3.dp else 1.dp, if (focused) Color.White else Color(0xFFB5791A), shape)
            .semantics { contentDescription = label }
            .clickable(onClick = onClick)
            .onFocusChanged { focused = it.isFocused }
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, color = Color(0xFF231609), fontWeight = FontWeight.Black,
            fontSize = if (compact) 21.sp else 26.sp)
    }
}

private fun xpReleaseKey(item: XpMeta): String {
    val raw = item.releaseInfo.orEmpty()
    val date = Regex("""\d{4}-\d{2}-\d{2}""").find(raw)?.value
    return date ?: Regex("""\d{4}""").find(raw)?.value?.plus("-00-00").orEmpty()
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.XpPosterBadges(item: XpMeta) {
    if (item.type != "movie" && item.type != "series") return
    val rating = item.rating?.toDoubleOrNull()?.takeIf { it > 0 && it <= 10 }
    if (rating != null) {
        Row(Modifier.align(Alignment.TopStart).padding(4.dp)
            .clip(RoundedCornerShape(5.dp)).background(Color.Black.copy(alpha = .78f)).padding(horizontal = 5.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("★ ", color = Color(0xFFFFD54F), fontSize = 11.sp)
            Text(String.format(java.util.Locale.US, "%.1f", rating), color = Color.White, fontSize = 11.sp)
        }
    }
    val year = xpReleaseKey(item).take(4)
    if (year.isNotEmpty()) {
        Text(year, modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
            .clip(RoundedCornerShape(5.dp)).background(Color.Black.copy(alpha = .78f)).padding(horizontal = 5.dp, vertical = 2.dp),
            color = Color.White, fontSize = 11.sp)
    }
}

@Composable
private fun XpPoster(
    item: XpMeta,
    width: Dp = 142.dp,
    focusRequester: FocusRequester? = null,
    onLongClick: () -> Unit = {},
    onVeryLongClick: () -> Unit = {},
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var centerDownAt by remember { mutableStateOf<Long?>(null) }
    Column(
        Modifier.width(width).clip(RoundedCornerShape(12.dp))
            .border(if (focused) 3.dp else 1.dp, if (focused) XpFocus else Color.Transparent, RoundedCornerShape(12.dp))
            .background(if (focused) XpFocus.copy(alpha = .22f) else Color.Transparent)
            .padding(3.dp).then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                val keyCode = event.nativeKeyEvent.keyCode
                val isOk = keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                    keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                    keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
                if (!isOk) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.action) {
                    android.view.KeyEvent.ACTION_DOWN -> {
                        if (centerDownAt == null) centerDownAt = android.os.SystemClock.uptimeMillis()
                        true
                    }
                    android.view.KeyEvent.ACTION_UP -> {
                        val heldMs = centerDownAt?.let { android.os.SystemClock.uptimeMillis() - it } ?: 0L
                        centerDownAt = null
                        when {
                            heldMs >= 3_000L -> onVeryLongClick()
                            heldMs >= 2_000L -> onLongClick()
                            else -> onClick()
                        }
                        true
                    }
                    else -> false
                }
            }
            .clickable(onClick = onClick).focusable(),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            Modifier.size(width, width * 1.46f).clip(RoundedCornerShape(12.dp))
                .background(if (focused) XpAccent.copy(alpha = .24f) else XpPanel),
        ) {
            item.poster?.let {
                AsyncImage(model = it, contentDescription = item.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            XpPosterBadges(item)
            if (item.durationMs > 0L && item.positionMs > 0L) {
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp)
                        .background(XpText.copy(alpha = .35f)),
                ) {
                    Box(
                        Modifier.fillMaxWidth((item.positionMs.toFloat() / item.durationMs).coerceIn(0f, 1f))
                            .height(4.dp).background(XpAccent),
                    )
                }
            }
        }
        Text(item.name, color = if (focused) XpFocus else XpText, fontSize = 14.sp, fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun XpAddonInfoCard(
    name: String,
    version: String,
    description: String,
    types: List<String>,
    logo: String?,
    action: String,
    disabled: Boolean,
    onAction: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(XpPanel).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!logo.isNullOrBlank()) {
                AsyncImage(model = logo, contentDescription = null, modifier = Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(name, color = XpText, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(types.distinct().joinToString(" · ").ifBlank { stringResource(R.string.moviotv_xp_addon_types_unknown) }, color = XpMuted, fontSize = 13.sp)
            }
            Text("v$version", color = XpMuted, fontSize = 14.sp)
        }
        if (description.isNotBlank()) Text(description, color = XpMuted, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        XpAction(action, disabled, onClick = onAction)
    }
}

@Composable
private fun XpAction(label: String, disabled: Boolean, focusRequester: FocusRequester? = null, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.clip(RoundedCornerShape(22.dp))
            .background(if (focused) XpFocus.copy(alpha = .28f) else XpPanel)
            .border(if (focused) 3.dp else 1.dp, if (focused) XpFocus else Color(0xFF3A3144), RoundedCornerShape(22.dp))
            .heightIn(min = 56.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable(enabled = !disabled, onClick = onClick)
            .onFocusChanged { focused = it.isFocused }.focusable(enabled = !disabled)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (disabled) XpMuted else if (focused) XpFocus else XpText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun XpCentralField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    password: Boolean = false,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = XpText, fontSize = 16.sp),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(XpPanel).border(1.dp, XpAccent.copy(alpha = .55f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 13.dp),
            ) {
                if (value.isBlank()) Text(label, color = XpMuted, fontSize = 16.sp)
                inner()
            }
        },
        modifier = Modifier.fillMaxWidth().focusable(),
    )
}
