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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.Job
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import tv.own.owntv.BuildConfig
import tv.own.owntv.R
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVIcon as DrawOwnTVIcon
import tv.own.owntv.features.profiles.ProfilesViewModel
import tv.own.owntv.core.database.entity.ChannelEntity
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.flowOn
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Semaphore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
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
internal data class XpResource(val name: String, val types: List<String>, val idPrefixes: List<String>) {
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
    val tmdbId: String? = null, val ratingSource: String? = null, val imdbId: String? = null,
    val activityAt: Long = 0L,
    val liveNow: Boolean = false,
    val eventTime: String? = null,
)
data class XpPlaybackSource(
    val url: String,
    val label: String,
    val provider: String,
    val identity: String = "",
    // No more confusing auto-play/automatic failover with an explicit selection by the viewer.
    val userSelected: Boolean = false,
    val legacyIdentity: String = "",
    val stableIdentity: String = "",
    val addonIdentity: String = "",
    val quality: String? = null,
    val releaseName: String? = null,
    val infoHash: String? = null,
    val fileIndex: Int? = null,
    val tmdbId: String? = null,
    val imdbId: String? = null,
)
private fun String.isLiveCatalogueType(): Boolean =
    lowercase(java.util.Locale.ROOT).filter(Char::isLetterOrDigit) in setOf(
        "tv", "channel", "channels", "livetv", "live", "iptv", "sport", "sports", "livechannel", "livechannels",
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
        addon.catalogs.filter { it.type == "movie" && !XpCatalogueVisibility.hidden(addon.id, it.id, it.name) }
            .map { XpCatalogRow(addon, it) }
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

    if (categoryId == "xp_cinemeta_featured") return candidates.firstOrNull {
        it.addon.builtIn && it.catalog.id == "imdbRating"
    }?.let { XpDiscoverSource(categoryId, it) }

    val customGenre = XpHomeConfig.genreName(categoryId)
    if (customGenre != null) {
        val aliases = if (XpCentralMetadata.category(categoryId) == "science-fiction") {
            listOf(customGenre, "Science Fiction", "Sci-Fi")
        } else listOf(customGenre)
        return genre(aliases)
    }
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
        "xp_cinema" -> candidates.firstOrNull {
            it.catalog.name.equals("In Cinemas", true) || it.catalog.name.equals("Now Playing", true)
        }?.let { XpDiscoverSource(categoryId, it) }
        "xp_upcoming" -> candidates.firstOrNull {
            it.catalog.name.equals("Upcoming", true)
        }?.let { XpDiscoverSource(categoryId, it) }
        else -> null
    }
}

private data class XpSearchHit(val addon: XpAddon, val meta: XpMeta)
private data class XpStream(
    val addon: XpAddon,
    val label: String,
    val detail: String?,
    val url: String?,
    val providerId: String? = null,
) {
    fun playback(): XpPlaybackSource? = url?.let {
        val release = detail?.lineSequence()?.map(String::trim)?.firstOrNull { line ->
            line.isNotBlank() && !line.contains("://") &&
                !line.contains("seeds", true) && !line.contains("size", true)
        }
        XpPlaybackSource(it, label, addon.name,
            identity = XpSourcePreference.identity(addon.url, label),
            legacyIdentity = XpSourcePreference.legacyIdentity(addon.url, label, detail),
            stableIdentity = XpSourcePreference.stableIdentity(addon.url, label, release, providerId),
            addonIdentity = XpSourcePreference.identity(addon.url, addon.id),
            quality = Regex("(?i)2160p|1080p|720p|480p|4k").find(label)?.value,
            releaseName = release ?: label,
            infoHash = providerId?.substringBeforeLast(':'),
            fileIndex = providerId?.substringAfterLast(':')?.toIntOrNull()?.takeIf { index -> index >= 0 },
        )
    }
}

private object XpAddonProtocol {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS).build()

    fun manifestAddress(raw: String): String? = runCatching { manifestUrl(raw) }.getOrNull()

    fun observeManifest(repository: XpCatalogueRepository, profile: String, url: String) =
        repository.observe(XpCatalogueRepository.Key(profile, url, url)) {
            get(url).also { parseManifest(url, it) }.toString()
        }.map { body -> body?.let { parseManifest(url, JSONObject(it)) } }.flowOn(Dispatchers.IO)

    private fun parseManifest(url: String, json: JSONObject): XpAddon {
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
        return XpAddon(
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
        parseCatalog(row, get(catalogUrl(row, overrides)))
    }

    fun observeCatalog(
        repository: XpCatalogueRepository,
        profile: String,
        row: XpCatalogRow,
        overrides: Map<String, String>,
        refreshAfter: Long,
    ) = catalogUrl(row, overrides).let { url ->
        repository.observe(XpCatalogueRepository.Key(profile, row.addon.url, row.addon.version + row.catalog.toString() + url), refreshAfter) {
            get(url).also { require(it.optJSONArray("metas") != null) }.toString()
        }.map { body -> body?.let { parseCatalog(row, JSONObject(it)) } }.flowOn(Dispatchers.IO)
    }

    private fun catalogUrl(row: XpCatalogRow, overrides: Map<String, String>): String {
        val route = "catalog/${Uri.encode(row.catalog.type)}/${Uri.encode(row.catalog.id)}"
        val parameters = (row.catalog.defaults.toMap() + overrides).filterKeys { it in row.catalog.extraFields }
        val extra = parameters.entries.joinToString("&") { (key, value) ->
            "${Uri.encode(key)}=${Uri.encode(value)}"
        }
        val path = if (extra.isBlank()) "$route.json" else "$route/$extra.json"
        return resourceUrl(row.addon.url, path)
    }

    private fun parseCatalog(row: XpCatalogRow, json: JSONObject): List<XpMeta> {
        val source = json.optJSONArray("metas") ?: JSONArray()
        return buildList {
            for (index in 0 until source.length()) {
                val item = source.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                val name = item.optString("name").trim()
                if (id.isBlank() || name.isBlank()) continue
                add(
                    XpMeta(
                        id = id,
                        type = item.optString("type", row.catalog.type).ifBlank { row.catalog.type },
                        name = name,
                        poster = item.optString("poster").takeIf { it.isNotBlank() }
                            ?: if (row.catalog.type.isLiveCatalogueType()) {
                                listOf("logo", "thumbnail", "background").firstNotNullOfOrNull { key ->
                                    item.optString(key).takeIf(String::isNotBlank)
                                }
                            } else null,
                        liveNow = row.catalog.type.isLiveCatalogueType() && item.optBoolean("isLive", false),
                        eventTime = if (row.catalog.type.isLiveCatalogueType()) {
                            item.optString("startTime").takeIf(String::isNotBlank)
                        } else null,
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

    suspend fun cachedCinemeta(
        repository: XpCatalogueRepository,
        profile: String,
        type: String,
        id: String,
    ): JSONObject? {
        if (!XpPlaybackIdentity.isImdbId(id) || type !in setOf("movie", "series")) return null
        val host = "https://v3-cinemeta.strem.io"
        val path = "/meta/$type/$id.json"
        val body = repository.observe(XpCatalogueRepository.Key(profile, host, path)) {
            get(host + path).also { require(it.optJSONObject("meta") != null) }.toString()
        }.last() ?: return null
        return JSONObject(body)
    }

    suspend fun loadStreams(addon: XpAddon, item: XpMeta, streamId: String): List<XpStream> = withContext(Dispatchers.IO) {
        val source = get(resourceUrl(addon.url, "stream/${Uri.encode(item.type)}/${Uri.encode(streamId)}.json"))
            .optJSONArray("streams") ?: JSONArray()
        buildList {
            for (index in 0 until source.length()) {
                val stream = source.optJSONObject(index) ?: continue
                val url = stream.optString("url").trim().takeIf(::isHttpUrl)
                val label = stream.optString("title").ifBlank {
                    stream.optString("name").ifBlank { addon.name }
                }
                val detail = stream.optString("description").takeIf { it.isNotBlank() }
                val hash = stream.optString("infoHash").takeIf { it.matches(Regex("[0-9a-fA-F]{40}")) }
                val providerId = hash?.let { "${it.lowercase()}:${stream.optInt("fileIdx", -1)}" }
                add(XpStream(addon, label, detail, url, providerId))
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

    fun read(context: Context, profileId: Long? = null, suffix: String = ""): List<String> = runCatching {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val profileKey = profileId?.let { "encrypted_urls_profile_$it" }
        // Never feed the unscoped legacy cache into a different account's profile.
        val encoded = if (profileKey == null) preferences.getString(LEGACY_VALUE, null)
            else preferences.getString(profileKey + suffix, null)
        if (encoded == null) return emptyList()
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        require(payload.size > 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, payload.copyOfRange(0, 12)))
        val values = JSONArray(String(cipher.doFinal(payload.copyOfRange(12, payload.size)), Charsets.UTF_8))
        buildList { for (i in 0 until values.length()) values.optString(i).takeIf { it.isNotBlank() }?.let(::add) }
    }.getOrDefault(emptyList())

    fun write(context: Context, urls: List<String>, profileId: Long? = null, suffix: String = "") {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val plain = JSONArray(urls.distinct()).toString().toByteArray(Charsets.UTF_8)
        val payload = cipher.iv + cipher.doFinal(plain)
        val key = profileId?.let { "encrypted_urls_profile_$it" } ?: LEGACY_VALUE
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(key + suffix, Base64.encodeToString(payload, Base64.NO_WRAP)).apply()
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
                        tmdbId = item.optString("tmdb_id").takeIf(String::isNotBlank),
                        ratingSource = item.optString("rating_source").takeIf(String::isNotBlank),
                        imdbId = item.optString("imdb_id").takeIf(String::isNotBlank),
                        activityAt = item.optLong("activityAt"),
                    )
                )
            }
        }
    }.getOrDefault(emptyList()).sortedByDescending(XpMeta::activityAt)

    fun write(context: Context, profileId: Long, titles: List<XpMeta>) {
        val json = JSONArray().apply {
            XpCollectionOrder.unique(titles).forEach { item ->
                put(JSONObject().put("id", item.id).put("type", item.type).put("name", item.name)
                    .put("poster", item.poster ?: "").put("description", item.description ?: "")
                    .put("year", item.releaseInfo ?: "").put("rating", item.rating ?: "").put("tmdb_id", item.tmdbId.orEmpty())
                    .put("rating_source", item.ratingSource.orEmpty()).put("imdb_id", item.imdbId.orEmpty())
                    .put("activityAt", item.activityAt))
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(PREFIX + profileId, json.toString()).apply()
    }

    fun isFavorite(context: Context, profileId: Long, type: String, id: String): Boolean =
        read(context, profileId).any { it.type == type && it.id == id }

    fun toggle(context: Context, profileId: Long, item: XpMeta): Boolean {
        val current = read(context, profileId)
        val matches = current.filter { XpPlaybackIdentity.sameTitle(it, item) }
        val added = item.copy(positionMs = 0L, durationMs = 0L, activityAt = System.currentTimeMillis())
        val updated = if (matches.isNotEmpty()) current - matches.toSet() else listOf(added) + current
        write(context, profileId, updated)
        if (matches.isEmpty()) XpCentralDataSync.markFavorite(context, profileId, added, deleted = false)
        else matches.forEach { XpCentralDataSync.markFavorite(context, profileId, it.copy(activityAt = added.activityAt), deleted = true) }
        return matches.isEmpty()
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
                        item.optString("year").takeIf(String::isNotBlank), item.optString("rating").takeIf(String::isNotBlank),
                        item.optLong("positionMs", 0L), item.optLong("durationMs", 0L),
                        item.optString("tmdb_id").takeIf(String::isNotBlank),
                        item.optString("rating_source").takeIf(String::isNotBlank),
                        item.optString("imdb_id").takeIf(String::isNotBlank), item.optLong("activityAt"))
                )
            }
        }
    }.getOrDefault(emptyList()).sortedByDescending(XpMeta::activityAt)

    @Synchronized
    fun add(context: Context, profileId: Long, item: XpMeta) {
        val existing = read(context, profileId).firstOrNull { XpPlaybackIdentity.sameTitle(it, item) }
        // Opening a title must not erase the saved playhead before the player can use it.
        save(context, profileId, item.copy(
            positionMs = existing?.positionMs ?: 0L,
            durationMs = existing?.durationMs ?: 0L,
            tmdbId = item.tmdbId ?: existing?.tmdbId,
            imdbId = item.imdbId ?: existing?.imdbId,
        ))
    }

    @Synchronized
    fun updateProgress(
        context: Context, profileId: Long, id: String, type: String, name: String, poster: String?, year: String?,
        positionMs: Long, durationMs: Long,
    ) {
        val previous = read(context, profileId).firstOrNull { it.id == id && it.type == type }
        val knownDuration = durationMs.takeIf { it > 0L } ?: previous?.durationMs ?: 0L
        val safePosition = positionMs.coerceAtLeast(0L)
        val now = System.currentTimeMillis()
        if (knownDuration > 0L && safePosition >= knownDuration * .95f) {
            val meta = XpMeta(id, type, name, poster, null, previous?.releaseInfo ?: year, previous?.rating, safePosition, knownDuration, previous?.tmdbId, previous?.ratingSource, previous?.imdbId, now)
            val remaining = read(context, profileId).filterNot { it.id == id && it.type == type }
            write(context, profileId, remaining)
            XpCentralDataSync.markResume(context, profileId, meta, deleted = true)
        } else if (safePosition > 0L) {
            val meta = XpMeta(id, type, name, poster, null, previous?.releaseInfo ?: year, previous?.rating, safePosition, knownDuration, previous?.tmdbId, previous?.ratingSource, previous?.imdbId, now)
            save(context, profileId, meta)
            XpCentralDataSync.markResume(context, profileId, meta, deleted = false)
        }
    }

    private fun save(context: Context, profileId: Long, item: XpMeta) {
        val updated = XpCollectionOrder.merge(read(context, profileId), item, deleted = false).take(120)
        write(context, profileId, updated)
    }

    internal fun writeCloud(context: Context, profileId: Long, updated: List<XpMeta>) = write(context, profileId, updated)

    private fun write(context: Context, profileId: Long, updated: List<XpMeta>) {
        val json = JSONArray().apply {
            updated.forEach { meta ->
                put(JSONObject().put("id", meta.id).put("type", meta.type).put("name", meta.name)
                    .put("poster", meta.poster ?: "").put("year", meta.releaseInfo ?: "").put("rating", meta.rating ?: "")
                    .put("tmdb_id", meta.tmdbId.orEmpty()).put("rating_source", meta.ratingSource.orEmpty())
                    .put("imdb_id", meta.imdbId.orEmpty())
                    .put("positionMs", meta.positionMs).put("durationMs", meta.durationMs).put("activityAt", meta.activityAt))
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(PREFIX + profileId, json.toString()).apply()
    }
}

internal val LocalXpRestorePoster = staticCompositionLocalOf { false }
internal val LocalXpPlaybackActive = staticCompositionLocalOf { false }
internal val LocalXpPosterDisplay = staticCompositionLocalOf { true to true }

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
    restoreHomePosterFocus: Boolean = false,
) {
    val context = LocalContext.current
    val playbackActive = LocalXpPlaybackActive.current
    val television = (androidx.compose.ui.platform.LocalConfiguration.current.uiMode and
        android.content.res.Configuration.UI_MODE_TYPE_MASK) == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    val focusManager = LocalFocusManager.current
    var headerEditing by remember { mutableStateOf(false) }
    var gridEditing by remember { mutableStateOf(false) }
    val remoteHold = remember { XpRemoteHold() }
    var remoteTimer by remember { mutableStateOf<Job?>(null) }
    val restorePosterFocus = LocalXpRestorePoster.current || restoreHomePosterFocus
    // Keep all five XP tab labels legible on narrow touch devices.
    val compactPhone = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600
    val scope = rememberCoroutineScope()
    val xpUpdater = remember(context) { XpSoftwareUpdater(context.applicationContext) }
    var xpDownloadJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(xpUpdater) { xpUpdater.checkIfDue() }
    val profiles: ProfilesViewModel = koinViewModel()
    val activeProfileId by profiles.activeProfileId.collectAsStateWithLifecycle()
    val profileList by profiles.profiles.collectAsStateWithLifecycle()
    val activeProfile = profileList.firstOrNull { it.id == activeProfileId }
    val activeProfileName = activeProfile?.name.orEmpty()
    val favoritesProfileId = activeProfileId ?: -1L
    var centralConnection by remember { mutableStateOf(XpCentralConnectionStore.read(context)) }
    var centralReachable by remember(centralConnection) { mutableStateOf(false) }
    var centralConnecting by remember(centralConnection) { mutableStateOf(centralConnection != null) }
    val catalogueProfile = catalogueProfileKey(centralConnection, favoritesProfileId)
    val addons = remember(catalogueProfile) { mutableStateListOf<XpAddon>() }
    val builtInUrl = stringResource(R.string.moviotv_xp_default_catalogue_manifest)
    var providerUrls by remember(catalogueProfile) {
        mutableStateOf(XpAddonStore.read(context, favoritesProfileId).filterNot { it == builtInUrl })
    }
    var disabledAddonUrls by remember(catalogueProfile) {
        mutableStateOf(XpAddonStore.read(context, favoritesProfileId, "_disabled").toSet())
    }
    var centralDisabledAddonUrls by remember(catalogueProfile) {
        mutableStateOf(XpAddonStore.read(context, favoritesProfileId, "_central_disabled").toSet())
    }
    var localAddonOrder by remember(catalogueProfile) {
        mutableStateOf(XpAddonStore.read(context, favoritesProfileId, "_order"))
    }
    val currentCatalogueProfile by rememberUpdatedState(catalogueProfile)
    val addonSyncLock = remember { Mutex() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun updateAddon(addon: XpAddon) {
        val index = addons.indexOfFirst { it.url == addon.url }
        if (index >= 0) addons[index] = addon else addons.add(addon)
    }
    val homeListState = key(catalogueProfile) { rememberLazyListState() }
    var lastHomePoster by key(catalogueProfile) { rememberSaveable { mutableStateOf<String?>(null) } }
    var returnHomePoster by remember(catalogueProfile) { mutableStateOf<String?>(null) }
    LaunchedEffect(restorePosterFocus, playbackActive) {
        if (restorePosterFocus && !playbackActive) {
            xpDebug("Restoring catalogue poster focus")
            returnHomePoster = lastHomePoster
        }
    }
    fun trackHomePoster(poster: String) {
        lastHomePoster = poster
        if (returnHomePoster == poster) returnHomePoster = null
    }
    var savedHomeAnchor by key(catalogueProfile) { rememberSaveable { mutableStateOf<String?>(null) } }
    var savedHomeOffset by key(catalogueProfile) { rememberSaveable { mutableIntStateOf(0) } }
    var returnHomeAnchor by remember(catalogueProfile) { mutableStateOf(savedHomeAnchor) }
    val returnHomeOffset = remember(catalogueProfile) { savedHomeOffset }
    var homeSettings by remember(favoritesProfileId) {
        mutableStateOf(XpHomeConfig.read(context, favoritesProfileId))
    }
    var showAddHomeCategory by remember { mutableStateOf(false) }
    val directoryAddons = remember { mutableStateListOf<XpAddonListing>() }
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
    var selectedImdbId by remember { mutableStateOf<String?>(null) }
    var selectedImdbRating by remember { mutableStateOf<String?>(null) }
    var episodes by remember { mutableStateOf<List<XpEpisode>>(emptyList()) }
    var selectedEpisode by remember { mutableStateOf<XpEpisode?>(null) }
    var selectedSeason by remember { mutableIntStateOf(1) }
    var seasonMenu by remember { mutableStateOf(false) }
    var episodeMenu by remember { mutableStateOf(false) }
    val streamRequests = remember(catalogueProfile) { mutableMapOf<List<String>, Deferred<List<XpStream>>>() }
    var selectedStreams by remember { mutableStateOf<List<XpStream>>(emptyList()) }
    var streamsLoading by remember { mutableStateOf(false) }
    var directoryLoading by remember { mutableStateOf(false) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var addonCategory by remember { mutableStateOf("community") }
    var addonFilter by remember { mutableStateOf("") }
    val browsePages = setOf("movies", "series", "live", "favorites")
    var page by key(catalogueProfile, initialPage) { rememberSaveable { mutableStateOf(initialPage) } }
    // Independent, small accordion state for the redesigned MovioXP Settings.
    var expandedSettingsGroup by remember { mutableStateOf<Int?>(null) }
    var showPreferredAddons by remember { mutableStateOf(false) }
    var showXpAbout by remember { mutableStateOf(false) }
    var returnPage by key(catalogueProfile, initialPage) { rememberSaveable { mutableStateOf(initialPage) } }
    var searchText by key(catalogueProfile) { rememberSaveable { mutableStateOf("") } }
    var discoveryType by key(catalogueProfile) { rememberSaveable { mutableStateOf("all") } }
    var discoveryCatalog by key(catalogueProfile) { rememberSaveable { mutableStateOf("all") } }
    var discoveryGenre by key(catalogueProfile) { rememberSaveable { mutableStateOf("all") } }
    var genrePickerOpen by remember { mutableStateOf(false) }
    val sortPrefs = remember(context) { context.getSharedPreferences(XpCentralDataSync.SORT_PREFS, Context.MODE_PRIVATE) }
    val sortKey = favoritesProfileId.toString()
    fun readSort() = sortPrefs.getInt(sortKey, -1).takeIf { it in 0..6 } ?: -1
    var sortMode by remember(sortKey) { mutableStateOf(readSort()) }
    DisposableEffect(sortPrefs, sortKey) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == sortKey) sortMode = readSort()
        }
        sortPrefs.registerOnSharedPreferenceChangeListener(listener)
        sortMode = readSort()
        onDispose { sortPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val sortLabels = listOf(stringResource(R.string.xp_catalog_sort_default), stringResource(R.string.xp_catalog_sort_newest), stringResource(R.string.xp_catalog_sort_oldest), stringResource(R.string.xp_catalog_sort_name_asc), stringResource(R.string.xp_catalog_sort_name_desc), stringResource(R.string.xp_catalog_sort_rating_desc), stringResource(R.string.xp_catalog_sort_rating_asc))
    fun changeSort(mode: Int) {
        XpCentralDataSync.saveCatalogSort(context, favoritesProfileId, mode)
        sortMode = mode
    }
    fun sortedTitles(titles: List<XpMeta>, category: String? = null): List<XpMeta> =
        sortXpTitles(titles, xpCategorySort(category, sortMode.takeIf { it >= 0 }))
    val effectiveSort = xpCategorySort(null, sortMode.takeIf { it >= 0 })
    var expandedFilter by remember { mutableStateOf<String?>(null) }
    var searchHits by remember { mutableStateOf<List<XpSearchHit>>(emptyList()) }
    var searchLoading by remember { mutableStateOf(false) }
    var searchableCatalogCount by remember { mutableStateOf(0) }
    var searchFailureCount by remember { mutableStateOf(0) }
    var continueTitles by remember(favoritesProfileId) { mutableStateOf(XpContinueStore.read(context, favoritesProfileId)) }
    var favoriteTitles by remember(favoritesProfileId) { mutableStateOf(XpFavoritesStore.read(context, favoritesProfileId)) }
    var historyTitles by remember(catalogueProfile) {
        mutableStateOf(XpPersonalCollections.read(context, favoritesProfileId, XpCollection.HISTORY))
    }
    var watchlistTitles by remember(catalogueProfile) {
        mutableStateOf(XpPersonalCollections.read(context, favoritesProfileId, XpCollection.WATCHLIST))
    }
    DisposableEffect(catalogueProfile) {
        val prefs = context.getSharedPreferences(XpPersonalCollections.PREFS, Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
            when (changed) {
                XpPersonalCollections.key(favoritesProfileId, XpCollection.HISTORY) ->
                    historyTitles = XpPersonalCollections.read(context, favoritesProfileId, XpCollection.HISTORY)
                XpPersonalCollections.key(favoritesProfileId, XpCollection.WATCHLIST) ->
                    watchlistTitles = XpPersonalCollections.read(context, favoritesProfileId, XpCollection.WATCHLIST)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val pagePrefs = remember(context) { context.getSharedPreferences("xp_browse_categories", Context.MODE_PRIVATE) }
    var discoverCategory by key(catalogueProfile) {
        rememberSaveable { mutableStateOf(pagePrefs.getString(catalogueProfile, "movies") ?: "movies") }
    }
    var libraryCategory by key(catalogueProfile) { rememberSaveable { mutableStateOf("favorites") } }
    val personalGridState = key(catalogueProfile) { rememberSaveableStateHolder() }
    val ratingKey = "xp_show_rating_$favoritesProfileId"
    val yearKey = "xp_show_year_$favoritesProfileId"
    val displayPrefs = remember(context) { context.getSharedPreferences("movioxp_playback", Context.MODE_PRIVATE) }
    var showRating by remember(ratingKey) { mutableStateOf(displayPrefs.getBoolean(ratingKey, true)) }
    val legacyShowYear = MovioMoviePrefs.get(context, favoritesProfileId).showYear
    var showYear by remember(yearKey, legacyShowYear) { mutableStateOf(displayPrefs.getBoolean(yearKey, legacyShowYear)) }
    DisposableEffect(ratingKey, yearKey, legacyShowYear) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
            if (changed == ratingKey) showRating = displayPrefs.getBoolean(ratingKey, true)
            if (changed == yearKey) showYear = displayPrefs.getBoolean(yearKey, legacyShowYear)
        }
        displayPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { displayPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val libraryPrefs = remember(context) { context.getSharedPreferences("moviotv_poster_grid_v75", Context.MODE_PRIVATE) }
    val libraryZoomKey = "xp_library_zoom_$favoritesProfileId"
    val homeZoomKey = "xp_home_zoom_$favoritesProfileId"
    val discoverZoomKey = "xp_discover_zoom_$favoritesProfileId"
    var discoverScale by remember(discoverZoomKey) {
        mutableStateOf(libraryPrefs.getFloat(discoverZoomKey, 1f).coerceIn(0.65f, 1.85f))
    }
    var libraryScale by remember(libraryZoomKey) {
        mutableStateOf(libraryPrefs.getFloat(libraryZoomKey, 1f).coerceIn(0.65f, 1.85f))
    }
    var homeScale by remember(homeZoomKey) {
        mutableStateOf(libraryPrefs.getFloat(homeZoomKey, 1f).coerceIn(0.65f, 1.85f))
    }
    val homeScaleNow by rememberUpdatedState(homeScale)
    val homePosterWidth = 142.dp * homeScale
    DisposableEffect(libraryPrefs, libraryZoomKey, homeZoomKey, discoverZoomKey) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            when (key) {
                libraryZoomKey -> libraryScale = prefs.getFloat(libraryZoomKey, 1f).coerceIn(0.65f, 1.85f)
                discoverZoomKey -> discoverScale = prefs.getFloat(discoverZoomKey, 1f).coerceIn(0.65f, 1.85f)
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
    var centralApiBase by remember { mutableStateOf(centralConnection?.apiBase?.removeSuffix("/") ?: "https://moviotv.opticalzoo.com/movio/api/index.php") }
    var centralUsername by remember { mutableStateOf(centralConnection?.username.orEmpty()) }
    var centralPassword by remember { mutableStateOf("") }
    var showAccountMenu by remember { mutableStateOf(false) }
    var centralSignOutAction by remember { mutableStateOf<Int?>(null) }
    var centralMobileLogin by remember { mutableStateOf(false) }
    var showLogoMenu by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var addProfileOpen by remember { mutableStateOf(false) }
    var centralProfileName by remember { mutableStateOf("") }
    var profileWorking by remember { mutableStateOf(false) }
    var profileError by remember { mutableStateOf(false) }
    var manageCentralProfiles by remember { mutableStateOf(false) }
    var showCredits by remember { mutableStateOf(false) }
    var detailsMetadata by remember { mutableStateOf<XpDetailMetadata?>(null) }
    val searchListState = key(catalogueProfile) { rememberLazyListState() }
    var previousSearchQuery by key(catalogueProfile) { rememberSaveable { mutableStateOf(searchText.trim()) } }
    LaunchedEffect(searchText.trim(), catalogueProfile) {
        if (previousSearchQuery != searchText.trim()) {
            previousSearchQuery = searchText.trim()
            searchListState.scrollToItem(0)
        }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val activity = androidx.activity.compose.LocalActivity.current
    var centralSyncBusy by remember { mutableStateOf(false) }
    // Installing an add-on during an existing Central refresh must never drop its upload.
    var centralSyncAgain by remember { mutableStateOf(false) }
    var centralSyncMessage by remember { mutableStateOf<String?>(null) }
    val firstSourceFocus = remember { FocusRequester() }
    val detailFocus = remember { FocusRequester() }
    val firstGridFocus = remember { FocusRequester() }
    val categoryRailFocus = remember { FocusRequester() }
    val bottomNavigationFocus = remember { FocusRequester() }
    val topNavigationFocus = remember { FocusRequester() }
    val shortcutsEnabled = television && showBottomNavigation && !playbackActive &&
        page in setOf("home", "discover", "library") && selected == null && !showAccountMenu && centralSignOutAction == null && !showLogoMenu && !confirmExit && !addProfileOpen && !manageCentralProfiles &&
        !headerEditing && !gridEditing
    val canJump by rememberUpdatedState(shortcutsEnabled)
    LaunchedEffect(page) {
        if (page != "home") headerEditing = false
        if (page != "discover" && page != "library") gridEditing = false
    }
    LaunchedEffect(shortcutsEnabled, page) {
        remoteTimer?.cancel()
        remoteHold.cancel()
    }

    fun jump(direction: XpRemoteHold.Direction) {
        val target = if (direction == XpRemoteHold.Direction.TOP) topNavigationFocus else bottomNavigationFocus
        runCatching { target.requestFocus() }
    }

    fun signOutCentral() {
        val previous = centralConnection ?: return
        XpCentralEventSync.clear()
        XpCentralConnectionStore.clear(context)
        centralConnection = null
        centralUsername = ""
        centralPassword = ""
        centralSyncMessage = null
        showAccountMenu = false
        addons.clear()
        continueTitles = emptyList()
        favoriteTitles = emptyList()
        page = "central"
        XpCentralAddonSync.signOut(previous)
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
    val repository = remember(context) { XpCatalogueRepository.get(context) }
    var refreshCatalogues by remember(catalogueProfile, page, discoveryType, discoveryCatalog, discoveryGenre) { mutableLongStateOf(0L) }
    val discoverMetas = remember(catalogueProfile) { mutableStateMapOf<Pair<String, XpDiscoverSource?>, List<XpMeta>>() }
    val discoverLoading = remember(catalogueProfile) { mutableStateMapOf<String, Boolean>() }
    val metas = remember(catalogueProfile) { mutableStateMapOf<Pair<String, XpCatalogRow>, List<XpMeta>>() }
    val loadingRows = remember(catalogueProfile) { mutableStateMapOf<String, Boolean>() }
    val addonsByUrl = addons.associateBy { it.url }
    val manifestAddresses = (listOf(builtInUrl) + providerUrls).mapNotNull(XpAddonProtocol::manifestAddress).distinct()
    val managedAddresses = manifestAddresses.filterNot { it == builtInUrl }.let { addresses ->
        localAddonOrder.filter { it in addresses } + addresses.filterNot { it in localAddonOrder }
    }
    val managedAddons = managedAddresses.mapNotNull(addonsByUrl::get)
    val installedAddons = listOfNotNull(addonsByUrl[builtInUrl]) +
        managedAddons.filterNot { it.url in disabledAddonUrls || it.url in centralDisabledAddonUrls }
    val moveableAddons = managedAddons.map { it.url }
    val homeSources = visibleHomeIds.associateWith { id -> xpDiscoverSource(id, installedAddons) }

    fun discoveryDataKey(id: String, source: XpDiscoverSource?) = id to source
    LaunchedEffect(catalogueProfile, centralConnection, homeSources, refreshCatalogues) {
        val connection = centralConnection
        discoverMetas.keys.retainAll(homeSources.map { (id, source) -> discoveryDataKey(id, source) }.toSet())
        for ((id, source) in homeSources) {
            val category = XpCentralMetadata.category(id)
            if ((connection == null || category == null) && source == null) continue
            launch {
                val dataKey = discoveryDataKey(id, source)
                discoverLoading[id] = true
                try {
                    suspend fun fallback() {
                        if (source == null) {
                            discoverMetas.remove(dataKey)
                            return
                        }
                        XpAddonProtocol.observeCatalog(repository, catalogueProfile, source.row, source.parameters, refreshCatalogues)
                            .collect { titles ->
                                if (titles != null) discoverMetas[dataKey] = titles.distinctBy { it.type to it.id }
                            }
                    }
                    if (connection == null || category == null) fallback()
                    else XpCentralMetadata.observe(repository, catalogueProfile, connection, category, refreshCatalogues)
                        .collectLatest { titles ->
                            if (titles == null) {
                                xpDebug("Using catalogue provider fallback")
                                fallback()
                            } else discoverMetas[dataKey] = titles
                        }
                } finally {
                    discoverLoading.remove(id)
                }
            }
        }
    }

    fun catalogKey(row: XpCatalogRow) = "${row.addon.url}|${row.catalog.type}|${row.catalog.id}"
    fun rowKey(row: XpCatalogRow) = "${catalogKey(row)}|${if (page == "discover" || page in browsePages) discoveryGenre else "all"}"
    val configuredRows = remember(installedAddons) {
        installedAddons.flatMap { addon -> addon.catalogs.map { XpCatalogRow(addon, it) } }
    }
    val externalIds = remember(configuredRows) {
        configuredRows.associateWith { XpHomeConfig.externalRowId(it.addon.url, it.catalog.type, it.catalog.id) }
    }
    fun externalRowId(row: XpCatalogRow) = externalIds.getValue(row)
    val externalRows = configuredRows.filterNot {
        (it.addon.builtIn && it.catalog.type == "movie" && it.catalog.id == "imdbRating") ||
            XpCatalogueVisibility.hidden(it.addon.id, it.catalog.id, it.catalog.name)
    }
    val rows = (if (page == "home") externalRows.filter {
        XpHomeConfig.externalVisible(homeSettings, externalRowId(it), it.addon.builtIn)
    } else configuredRows).filter { row ->
            val typeMatches = when (page) {
                "movies" -> row.catalog.type.equals("movie", true)
                "series" -> row.catalog.type.equals("series", true)
                "live" -> row.catalog.type.isLiveCatalogueType()
                "favorites" -> false
                "discover" -> false
                else -> true
            }
            val genreSupported = discoveryGenre == "all" || page !in setOf("movies", "series") ||
                row.catalog.extraFields.keys.any { it.equals("genre", true) }
            val catalogMatches = page != "discover" || discoveryCatalog == "all" || catalogKey(row) == discoveryCatalog
            typeMatches && catalogMatches && genreSupported
        }
    LaunchedEffect(catalogueProfile, page, rows, discoveryGenre, refreshCatalogues) {
        if (page != "home" && page != "discover" && page !in browsePages) return@LaunchedEffect
        val visibleKeys = rows.map { rowKey(it) to it }.toSet()
        metas.keys.retainAll(visibleKeys)
        rows.forEach { row ->
            val key = rowKey(row)
            val genreField = row.catalog.extraFields.keys.firstOrNull { it.equals("genre", true) }
            val overrides = if ((page == "discover" || page == "movies" || page == "series") && discoveryGenre != "all" && genreField != null) {
                mapOf(genreField to discoveryGenre)
            } else emptyMap()
            launch {
                loadingRows[key] = true
                try {
                    XpAddonProtocol.observeCatalog(repository, catalogueProfile, row, overrides, refreshCatalogues).collect { titles ->
                        if (titles != null) metas[key to row] = titles.distinctBy { it.type to it.id }
                    }
                } finally {
                    loadingRows.remove(key)
                }
            }
        }
    }

    val visibleRows = if (page == "home") rows.filter { metas[rowKey(it) to it].orEmpty().isNotEmpty() } else rows
    LaunchedEffect(page, visibleHomeIds, visibleRows, returnHomeAnchor) {
        val anchor = returnHomeAnchor ?: return@LaunchedEffect
        if (page != "home") return@LaunchedEffect
        val keys = listOf("xp-continue-watching") + visibleHomeIds.map { "discover:$it" } + visibleRows.map(::rowKey)
        val index = keys.indexOf(anchor)
        if (index >= 0) {
            homeListState.scrollToItem(index, returnHomeOffset)
            returnHomeAnchor = null
        }
    }

    LaunchedEffect(page, catalogueProfile) {
        if (page != "home") return@LaunchedEffect
        snapshotFlow {
            val first = homeListState.firstVisibleItemIndex
            val key = homeListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == first }?.key as? String
            key to homeListState.firstVisibleItemScrollOffset
        }.collect { (key, offset) ->
            if (returnHomeAnchor == null && key != null) {
                savedHomeAnchor = key
                savedHomeOffset = offset
            }
        }
    }

    val availableCatalogRows = configuredRows.filterNot {
        XpCatalogueVisibility.hidden(it.addon.id, it.catalog.id, it.catalog.name)
    }
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
    val liveRows = availableCatalogRows.filter { it.catalog.type.isLiveCatalogueType() }
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
    val sectionTitles = (if (page == "favorites") favoriteTitles else localLiveTitles + rows.flatMap { row -> metas[rowKey(row) to row].orEmpty() })
        .distinctBy { "${it.type}:${it.id}" }
        .let { sortedTitles(it) }
    val gridState = rememberLazyGridState()


    fun toggleFavorite(item: XpMeta) {
        XpFavoritesStore.toggle(context, favoritesProfileId, item.copy(imdbId = selectedImdbId ?: item.imdbId))
        favoriteTitles = XpFavoritesStore.read(context, favoritesProfileId)
        XpCentralEventSync.request(context, favoritesProfileId)
    }

    fun playbackMetadata(item: XpMeta): XpMeta {
        val episode = selectedEpisode
        if (episode != null) return item.copy(id = episode.id,
            name = episode.title?.let { context.getString(R.string.movioxp_episode_title, item.name, it) } ?: item.name,
            tmdbId = null, imdbId = null, positionMs = 0L, durationMs = 0L)
        return item.copy(imdbId = selectedImdbId ?: item.imdbId,
            rating = selectedImdbRating ?: item.rating,
            ratingSource = if (selectedImdbRating == null) item.ratingSource else "IMDb")
    }

    fun playbackWithResume(item: XpMeta): XpMeta {
        val enriched = playbackMetadata(item)
        val previous = continueTitles.firstOrNull { XpPlaybackIdentity.sameTitle(it, enriched) }
        return if (previous == null) enriched else enriched.copy(
            id = previous.id,
            positionMs = previous.positionMs,
            durationMs = previous.durationMs,
            tmdbId = XpPlaybackIdentity.tmdbId(enriched) ?: XpPlaybackIdentity.tmdbId(previous),
            imdbId = XpPlaybackIdentity.ids(enriched).firstOrNull(XpPlaybackIdentity::isImdbId),
        )
    }

    fun playbackCandidates(media: XpMeta, sources: List<XpPlaybackSource>): List<XpPlaybackSource> =
        sources.map { it.copy(tmdbId = media.tmdbId, imdbId = media.imdbId) }

    fun savedSource(media: XpMeta): String? =
        (listOf(media.id) + XpPlaybackIdentity.ids(media)).distinct().firstNotNullOfOrNull { id ->
            XpSourcePreference.read(context, favoritesProfileId, "stremio:${media.type}:$id")
        }


    fun withSavedResume(item: XpMeta): XpMeta {
        val saved = continueTitles.firstOrNull { XpPlaybackIdentity.sameTitle(it, item) }
        return if (saved != null) item.copy(positionMs = saved.positionMs, durationMs = saved.durationMs) else item
    }

    fun closeDetails() {
        showCredits = false
        selected = null
        manualSourceSelection = false
        showManualSources = false
        returnHomePoster = lastHomePoster
    }

    fun resetEpisodeSelection() {
        showCredits = false
        detailsMetadata = null
        streamRequests.values.forEach { it.cancel() }
        streamRequests.clear()
        episodes = emptyList()
        selectedEpisode = null
        seasonMenu = false
        episodeMenu = false
        selectedImdbId = null
        selectedImdbRating = null
        selectedStreams = emptyList()
        streamsLoading = true
    }

    var resumeFromShelf by remember { mutableStateOf(false) }

    fun openItem(item: XpMeta, resume: Boolean = false) {
        resumeFromShelf = resume
        resetEpisodeSelection()
        manualSourceSelection = false
        showManualSources = false
        selected = withSavedResume(item)
    }

    fun openDetails(item: XpMeta, showSources: Boolean) {
        resumeFromShelf = false
        resetEpisodeSelection()
        manualSourceSelection = true
        showManualSources = showSources
        selected = withSavedResume(item)
    }

    fun signInCentral() {
        if (centralSyncBusy) return
        if (centralUsername.isBlank() || centralPassword.isBlank() || centralApiBase.isBlank()) {
            centralSyncMessage = context.getString(R.string.moviotv_xp_central_details_required)
            return
        }
        centralSyncBusy = true
        centralSyncMessage = null
        scope.launch {
            try {
                val connection = XpCentralAddonSync.signIn(centralApiBase, centralUsername, centralPassword)
                XpCentralConnectionStore.write(context, connection)
                centralConnection = connection
                centralApiBase = connection.apiBase.removeSuffix("/")
                centralPassword = ""
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (_: Exception) {
                centralSyncMessage = context.getString(R.string.moviotv_xp_central_sync_failed)
            } finally {
                centralSyncBusy = false
            }
        }
    }

    val pendingAddonRefresh = remember { kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    fun syncAddonsWithCentral() {
        if (centralSyncBusy) {
            centralSyncAgain = true
            return
        }
        if (favoritesProfileId <= 0L || activeProfileName.isBlank()) {
            centralSyncMessage = context.getString(R.string.moviotv_xp_central_profile_required)
            return
        }
        val localProfileId = favoritesProfileId
        val localProfileName = activeProfileName
        val connection = centralConnection ?: return
        centralSyncBusy = true
        centralSyncMessage = null
        scope.launch {
            try {
                val requestedProfile = catalogueProfile
                do {
                    centralSyncAgain = false
                    addonSyncLock.withLock {
                        val assignments = XpCentralAddonSync.syncProfile(context, connection, localProfileId)
                        val urls = assignments.mapNotNull { XpAddonProtocol.manifestAddress(it.url) }
                            .distinct().filterNot { it == builtInUrl }
                        val disabled = assignments.filterNot { it.enabled }
                            .mapNotNull { XpAddonProtocol.manifestAddress(it.url) }.toSet()
                        if (currentCatalogueProfile == requestedProfile) {
                            if (centralDisabledAddonUrls != disabled) {
                                XpAddonStore.write(context, disabled.toList(), localProfileId, "_central_disabled")
                                centralDisabledAddonUrls = disabled
                            }
                            if (providerUrls != urls) {
                                XpAddonStore.write(context, urls, localProfileId)
                                providerUrls = urls
                                addons.removeAll { !it.builtIn && it.url !in urls }
                            }
                            centralSyncMessage = context.getString(R.string.moviotv_xp_central_profile_saved, localProfileName)
                        }
                    }
                } while (centralSyncAgain && currentCatalogueProfile == requestedProfile)
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (_: Exception) {
                if (currentCatalogueProfile == catalogueProfile) {
                    centralSyncMessage = context.getString(R.string.moviotv_xp_central_sync_failed)
                }
            } finally {
                centralSyncBusy = false
                if (centralSyncAgain && currentCatalogueProfile != catalogueProfile) {
                    centralSyncAgain = false
                    pendingAddonRefresh.tryEmit(Unit)
                }
            }
        }
    }

    val latestAddonSync by rememberUpdatedState { syncAddonsWithCentral() }
    LaunchedEffect(pendingAddonRefresh) { pendingAddonRefresh.collect { latestAddonSync() } }
    LaunchedEffect(lifecycle, centralConnection, catalogueProfile, activeProfileName) {
        if (centralConnection == null || favoritesProfileId <= 0L || activeProfileName.isBlank()) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                latestAddonSync()
                delay(90_000L)
            }
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
            centralConnecting = true
            runCatching {
                XpCentralDataSync.sync(context, connection, favoritesProfileId, activeProfileName, activeProfile?.isKids == true)
            }.onSuccess {
                centralReachable = true
                centralConnecting = false
                favoriteTitles = XpFavoritesStore.read(context, favoritesProfileId)
                continueTitles = XpContinueStore.read(context, favoritesProfileId)
            }.onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                centralReachable = false
                centralConnecting = false
                xpDebug("Central profile synchronization failed")
            }
            delay(90_000L)
        }
    }

    fun installAddon(url: String) {
        val manifestAddress = XpAddonProtocol.manifestAddress(url)
        if (manifestAddress == null) {
            notice = context.getString(R.string.moviotv_xp_addon_install_failed)
            return
        }
        if (manifestAddress in manifestAddresses) {
            notice = context.getString(R.string.moviotv_xp_addon_already_installed)
            return
        }
        busy = true
        notice = null
        scope.launch {
            runCatching {
                requireNotNull(XpAddonProtocol.observeManifest(repository, catalogueProfile, manifestAddress).last())
            }
                .onSuccess { addon ->
                    if (addons.none { it.url == addon.url }) {
                        providerUrls = (providerUrls + addon.url).distinct()
                        XpAddonStore.write(context, providerUrls, favoritesProfileId)
                        updateAddon(addon)
                        centralConnection?.let { conn ->
                            centralSyncMessage = context.getString(R.string.moviotv_xp_central_saving_profile)
                            runCatching {
                                addonSyncLock.withLock { XpCentralAddonSync.installForProfile(
                                    context, conn, favoritesProfileId,
                                    XpCentralAddon(addon.url, addon.name,
                                        JSONObject().put("id", addon.id).put("name", addon.name)
                                            .put("version", addon.version).put("description", addon.description)
                                            .put("types", JSONArray(addon.types))),
                                ) }
                            }.onSuccess { pendingAddonRefresh.tryEmit(Unit) }
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
        if (showAddonRemote) addonRemoteServer.start() else addonRemoteServer.close()
    }

    fun moveAddon(addon: XpAddon, offset: Int) {
        val index = moveableAddons.indexOf(addon.url)
        val target = index + offset
        if (index < 0 || target !in moveableAddons.indices) return
        val order = moveableAddons.toMutableList()
        java.util.Collections.swap(order, index, target)
        localAddonOrder = order
        XpAddonStore.write(context, order, favoritesProfileId, "_order")
        if (centralConnection != null) syncAddonsWithCentral()
    }

    fun toggleAddon(addon: XpAddon) {
        disabledAddonUrls = if (addon.url in disabledAddonUrls) disabledAddonUrls - addon.url else disabledAddonUrls + addon.url
        XpAddonStore.write(context, disabledAddonUrls.toList(), favoritesProfileId, "_disabled")
        if (centralConnection != null) syncAddonsWithCentral()
    }

    fun removeAddon(addon: XpAddon) {
        if (addon.builtIn) return
        addons.remove(addon)
        disabledAddonUrls = disabledAddonUrls - addon.url
        localAddonOrder = localAddonOrder - addon.url
        XpAddonStore.write(context, disabledAddonUrls.toList(), favoritesProfileId, "_disabled")
        XpAddonStore.write(context, localAddonOrder, favoritesProfileId, "_order")
        val matchingUrls = providerUrls.filter { XpAddonProtocol.manifestAddress(it) == addon.url }
        providerUrls = providerUrls - matchingUrls.toSet()
        XpAddonStore.write(context, providerUrls, favoritesProfileId)
        scope.launch { repository.invalidate(catalogueProfile, addon.url) }
        centralConnection?.let { connection ->
            scope.launch {
                runCatching {
                    addonSyncLock.withLock {
                        for (url in matchingUrls) XpCentralAddonSync.unlinkForProfile(context, connection, favoritesProfileId, url)
                    }
                }.onSuccess { pendingAddonRefresh.tryEmit(Unit) }
                    .onFailure { centralSyncMessage = context.getString(R.string.moviotv_xp_central_sync_failed) }
            }
        }
    }

    LaunchedEffect(catalogueProfile, manifestAddresses, builtInUrl) {
        for (url in manifestAddresses) {
            val builtIn = url == builtInUrl
            launch {
                XpAddonProtocol.observeManifest(repository, catalogueProfile, url).collect { addon ->
                    if (addon != null) updateAddon(addon.copy(builtIn = builtIn))
                }
            }
        }
    }
    val canUpdateSearch by rememberUpdatedState(!playbackActive && selected == null)
    LaunchedEffect(page, searchText, installedAddons, catalogueProfile) {
        val query = searchText.trim()
        if (page != "search" || query.length < 2) {
            searchLoading = false
            searchHits = emptyList()
            searchFailureCount = 0
            searchableCatalogCount = installedAddons.sumOf { addon -> addon.catalogs.count { it.searchParameter != null } }
            return@LaunchedEffect
        }
        delay(400)
        searchLoading = true
        val searchable = installedAddons
        searchableCatalogCount = searchable.sumOf { addon -> addon.catalogs.count { it.searchParameter != null } }
        val (hits, failures) = XpAddonProtocol.searchAll(searchable, query)
        val resolvedHits = hits.map { hit ->
            val imdb = XpPlaybackIdentity.cachedImdb(repository, catalogueProfile, centralConnection, hit.meta)
            if (imdb == null) hit else hit.copy(meta = hit.meta.copy(imdbId = imdb))
        }
        searchHits = XpCataloguePaging.unique(resolvedHits) { it.meta }
        searchFailureCount = failures
        searchLoading = false
        // Keep results interactive while verifying a bounded set of possible cross-provider duplicates.
        val imdbHits = resolvedHits.map { it.meta }.filter { XpPlaybackIdentity.isImdbId(it.id) }
        val candidates = resolvedHits.filter { hit ->
            hit.meta.imdbId == null && hit.meta.type == "movie" && XpPlaybackIdentity.tmdbId(hit.meta) != null &&
                XpPlaybackIdentity.matchingTitleCandidates(hit.meta, imdbHits).isNotEmpty()
        }.take(4)
        if (candidates.isNotEmpty()) {
            val connection = centralConnection
            val mapped = candidates.map { hit -> async(Dispatchers.IO) {
                val imdb = XpPlaybackIdentity.resolve(repository, catalogueProfile, connection, hit.meta) {
                    XpPlaybackIdentity.exactImdbMatch(hit.meta, imdbHits)
                }
                hit.meta.id to imdb
            } }.awaitAll().toMap()
            if (canUpdateSearch) searchHits = XpCataloguePaging.unique(resolvedHits.map { hit ->
                val imdb = mapped[hit.meta.id]
                if (imdb == null) hit else hit.copy(meta = hit.meta.copy(imdbId = imdb))
            }) { it.meta }
        }
    }
    fun episodePreference(seriesId: String) = "$catalogueProfile|$seriesId"

    LaunchedEffect(selected?.id, selected?.type) {
        val item = selected ?: return@LaunchedEffect
        if (item.type != "series" || !XpPlaybackIdentity.isImdbId(item.id)) return@LaunchedEffect
        val result = withContext(Dispatchers.IO) {
            XpAddonProtocol.cachedCinemeta(repository, catalogueProfile, "series", item.id)
                ?.let { XpEpisode.parse(it, item.id) }.orEmpty()
        }
        episodes = result
        val remembered = pagePrefs.getString(episodePreference(item.id), null)
        val previousEpisode = result.firstOrNull { it.id == remembered }
        selectedSeason = previousEpisode?.season ?: result.firstOrNull { it.season > 0 }?.season ?: 1
        // Reopening details remembers the episode without initiating playback.
        if (previousEpisode != null) {
            manualSourceSelection = true
            selectedEpisode = previousEpisode
        }
    }

    LaunchedEffect(selected?.id, selectedImdbId, catalogueProfile) {
        val item = selected ?: return@LaunchedEffect
        val imdb = listOfNotNull(selectedImdbId, item.imdbId, item.id).firstOrNull(XpPlaybackIdentity::isImdbId)
            ?: return@LaunchedEffect
        detailsMetadata = withContext(Dispatchers.IO) {
            XpAddonProtocol.cachedCinemeta(repository, catalogueProfile, item.type, imdb)
                ?.optJSONObject("meta")?.let(XpDetailMetadata::parse)
        }
    }
    LaunchedEffect(selected?.id, selected?.type, selectedEpisode?.id, playbackActive,
        installedAddons.map { it.url }.takeUnless { playbackActive }) {
        if (playbackActive) return@LaunchedEffect
        val item = selected ?: return@LaunchedEffect
        val episode = selectedEpisode
        autoStartedItem = null
        selectedStreams = emptyList()
        if (item.id.startsWith("own-live:") ||
            (item.type == "series" && XpPlaybackIdentity.isImdbId(item.id) && episode == null)) {
            streamsLoading = false
            return@LaunchedEffect
        }
        streamsLoading = true
        val resolved = if (episode == null) withContext(Dispatchers.IO) {
            XpPlaybackIdentity.resolve(repository, catalogueProfile, centralConnection, item) {
                val builtIn = installedAddons.filter { it.builtIn }
                if (builtIn.isEmpty() || xpFullReleaseDate(item) == null) return@resolve null
                val hits = XpAddonProtocol.searchAll(builtIn, item.name).first.map { it.meta }
                val matches = XpPlaybackIdentity.matchingTitleCandidates(item, hits)
                if (matches.size > 4) return@resolve null
                val detailed = matches.map { candidate ->
                    if (xpFullReleaseDate(candidate) != null) candidate else {
                        val meta = XpAddonProtocol.cachedCinemeta(repository, catalogueProfile, item.type, candidate.id)?.optJSONObject("meta")
                        candidate.copy(releaseInfo = meta?.optString("released")?.takeIf(String::isNotBlank) ?: candidate.releaseInfo)
                    }
                }
                XpPlaybackIdentity.exactImdbMatch(item, detailed)
            }
        } else null
        selectedImdbId = resolved
        if (resolved != null && item.type == "movie" && item.ratingSource != "IMDb") launch(Dispatchers.IO) {
            val meta = XpAddonProtocol.cachedCinemeta(repository, catalogueProfile, item.type, resolved)?.optJSONObject("meta")
            val value = meta?.optString("imdbRating")?.takeIf {
                it.toDoubleOrNull()?.let { number -> number > 0 && number <= 10 } == true
            }
            withContext(Dispatchers.Main) { selectedImdbRating = value }
        }
        val streamIds = if (episode == null) XpPlaybackIdentity.ids(item, resolved) else
            listOfNotNull(episode.id, XpPlaybackIdentity.tmdbId(item)?.let(episode::forAlias))
        val providers = installedAddons.sortedBy { it.builtIn }.distinctBy { it.url }.mapNotNull { addon ->
            val ids = streamIds.filter { id -> addon.resources.any { it.supports(item.type, id) } }
            if (ids.isEmpty()) null else addon to ids
        }
        selectedStreams = providers.map { (addon, ids) -> async {
            ids.firstNotNullOfOrNull { id ->
                val requestKey = listOf(addon.url, item.type, id)
                val result = streamRequests.getOrPut(requestKey) {
                    scope.async {
                        xpDebug("Requesting streams from a compatible add-on")
                        try {
                            XpAddonProtocol.loadStreams(addon, item, id).filter { it.url != null }
                        } catch (error: kotlinx.coroutines.CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            xpDebug("Add-on stream request failed")
                            emptyList()
                        }
                    }
                }.await()
                result.takeIf { it.isNotEmpty() }
            }.orEmpty()
        } }.awaitAll().flatten().distinctBy { it.url }
        streamsLoading = false
    }
    LaunchedEffect(selected?.id, selected?.type, selectedStreams, streamsLoading, autoSelectSource, preferredAddonUrl, manualSourceSelection) {
        val item = selected ?: return@LaunchedEffect
        if (manualSourceSelection || streamsLoading) return@LaunchedEffect
        val media = playbackWithResume(item)
        val itemKey = "${media.type}:${media.id}"
        if (autoStartedItem == itemKey) return@LaunchedEffect
        val saved = savedSource(media)
        if (saved != null && !resumeFromShelf) {
            manualSourceSelection = true
            showManualSources = true
            return@LaunchedEffect
        }
        if (resumeFromShelf && saved == null) {
            manualSourceSelection = true
            showManualSources = true
            return@LaunchedEffect
        }
        if (!autoSelectSource && !resumeFromShelf) return@LaunchedEffect
        val candidates = selectedStreams.mapNotNull(XpStream::playback)
        val match = XpSourcePreference.playedMatch(saved, candidates)
        if (saved != null && match == null) {
            manualSourceSelection = true
            showManualSources = true
            return@LaunchedEffect
        }
        val preferred = match ?: candidates.firstOrNull { source ->
            selectedStreams.any { it.url == source.url && it.addon.url == preferredAddonUrl }
        } ?: candidates.firstOrNull() ?: return@LaunchedEffect
        val sources = listOf(preferred) + candidates.filterNot { it == preferred }
        autoStartedItem = itemKey
        val playback = playbackWithResume(item)
        selected = null
        manualSourceSelection = false
        showManualSources = false
        if (onPlayStreamCandidates != null) {
            onPlayStreamCandidates(playbackCandidates(playback, sources), playback.name, playback.type, playback.releaseInfo, playback.id, playback.poster, playback.positionMs)
        } else {
            onPlayStream(sources.first().url, playback.name, playback.type, playback.releaseInfo, playback.id, playback.poster, playback.positionMs)
        }
    }
    LaunchedEffect(selected?.id, selected?.type, streamsLoading, selectedStreams.size, autoSelectSource, manualSourceSelection, showManualSources) {
        if (selected != null && !streamsLoading) runCatching {
            if (manualSourceSelection && showManualSources && selectedStreams.isNotEmpty()) firstSourceFocus.requestFocus()
            else if (selectedStreams.isEmpty() || !showManualSources) detailFocus.requestFocus()
        }
    }
    BackHandler(enabled = !playbackActive && (selected != null || adding || page != initialPage)) {
        when {
            selected != null -> closeDetails()
            adding -> adding = false
            else -> page = if (page == "search" && returnPage.isNotBlank()) returnPage else initialPage
        }
    }
    BackHandler(enabled = !playbackActive && selected == null && !adding && page == initialPage, onBack = onBack)

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
            val key = event.nativeKeyEvent
            if (showBottomNavigation && !playbackActive && key.action == android.view.KeyEvent.ACTION_DOWN &&
                key.keyCode == android.view.KeyEvent.KEYCODE_MENU) {
                jump(XpRemoteHold.Direction.BOTTOM)
                return@onPreviewKeyEvent true
            }
            if (!shortcutsEnabled) return@onPreviewKeyEvent false
            val direction = when (key.keyCode) {
                android.view.KeyEvent.KEYCODE_DPAD_UP -> XpRemoteHold.Direction.TOP
                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> XpRemoteHold.Direction.BOTTOM
                else -> return@onPreviewKeyEvent false
            }
            when (key.action) {
                android.view.KeyEvent.ACTION_DOWN -> {
                    if (key.repeatCount == 0 && remoteHold.start(direction, key.downTime)) {
                        remoteTimer?.cancel()
                        remoteTimer = scope.launch {
                            delay(XpRemoteHold.DELAY)
                            if (canJump && remoteHold.trigger(direction, android.os.SystemClock.uptimeMillis())) jump(direction)
                        }
                    }
                    true
                }
                android.view.KeyEvent.ACTION_UP -> {
                    if (!remoteHold.isActive(direction)) return@onPreviewKeyEvent false
                    remoteTimer?.cancel()
                    if (!key.isCanceled && remoteHold.trigger(direction, key.eventTime)) jump(direction)
                    val released = remoteHold.release(direction)
                    if (!key.isCanceled && released == XpRemoteHold.Release.STEP) {
                        focusManager.moveFocus(if (direction == XpRemoteHold.Direction.TOP) FocusDirection.Up else FocusDirection.Down)
                    }
                    released != XpRemoteHold.Release.IGNORE
                }
                else -> false
            }
        },
    ) {
        CompositionLocalProvider(LocalXpPosterDisplay provides (showRating to showYear), LocalXpTvControls provides television) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = if (compactPhone) 12.dp else 22.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (compactPhone) 8.dp else 14.dp),
            ) {
                XpHeaderControl(compactPhone, stringResource(R.string.movioxp_account_menu), topNavigationFocus,
                    onClick = { showAccountMenu = true }, large = true) { color ->
                    Box(Modifier.size(39.dp)) {
                        DrawOwnTVIcon(OwnTVIcon.PERSON, color, Modifier.size(34.dp).align(Alignment.Center))
                        val ledColor = when {
                            centralConnection == null -> Color(0xFF9A9AA3)
                            centralConnecting -> Color(0xFFFFB83D)
                            centralReachable -> Color(0xFF36D68A)
                            else -> Color(0xFFF54C55)
                        }
                        val ledShape = androidx.compose.foundation.shape.CircleShape
                        Box(Modifier.align(Alignment.BottomEnd).size(13.dp)
                            .background(ledColor, ledShape).border(2.dp, XpBackground, ledShape))
                    }
                }
                if (page == "discover" || page == "library") {
                    Text(
                        stringResource(if (page == "discover") R.string.moviotv_xp_nav_discover else R.string.moviotv_xp_nav_library),
                        color = XpText, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                    )
                    MovioBrowseButton(stringResource(R.string.moviotv_xp_nav_search), {
                        returnPage = page
                        page = "search"
                    }, background = XpPanel, foreground = XpText)
                } else Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(XpPanel)
                        .border(if (television && headerEditing) 2.dp else 0.dp,
                            if (television && headerEditing) XpRemotePink else Color.Transparent, RoundedCornerShape(18.dp))
                        .padding(horizontal = if (compactPhone) 12.dp else 18.dp, vertical = if (compactPhone) 11.dp else 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        if (searchText.isBlank()) Text(
                            stringResource(if (compactPhone) R.string.moviotv_xp_nav_search else R.string.moviotv_xp_search_hint),
                            color = if (television && headerEditing) XpRemotePink else XpMuted, fontSize = if (compactPhone) 15.sp else 20.sp,
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
                            textStyle = TextStyle(color = if (television && headerEditing) XpRemotePink else XpText, fontSize = if (compactPhone) 16.sp else 20.sp),
                            modifier = Modifier.fillMaxWidth().onFocusChanged { headerEditing = it.hasFocus },
                        )
                    }
                    DrawOwnTVIcon(OwnTVIcon.SEARCH, if (television && headerEditing) XpRemotePink else XpText, Modifier.size(if (compactPhone) 19.dp else 24.dp))
                }
                XpHeaderControl(compactPhone, stringResource(R.string.moviotv_xp_title),
                    onClick = { showLogoMenu = true }) {
                    Image(painter = androidx.compose.ui.res.painterResource(R.drawable.moviotv_launcher),
                        contentDescription = null, modifier = Modifier.size(if (compactPhone) 32.dp else 38.dp))
                }
            }
            if (!showBottomNavigation) Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
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
                        } else {
                        page = "addons"
                        adding = false
                    }
                }
                if (page != "addons") XpAction(stringResource(R.string.moviotv_xp_refresh), busy) { refreshCatalogues = System.currentTimeMillis() }
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
            if (page in browsePages) {
                val currentSort = effectiveSort
                XpFilterDropdown(
                    title = stringResource(R.string.movioxp_sort),
                    selectedLabel = sortLabels[currentSort],
                    options = sortLabels.mapIndexed { index, label -> index.toString() to label },
                    expanded = expandedFilter == sortLabels.first(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    onToggle = { expandedFilter = if (expandedFilter == sortLabels.first()) null else sortLabels.first() },
                    onSelect = { changeSort(it.toInt()); expandedFilter = null },
                )
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
                    onSort = { changeSort((effectiveSort + 1) % sortLabels.size) },
                    itemCount = sectionTitles.size,
                    gridState = gridState,
                    categoryFocusRequester = categoryRailFocus,
                    emptyText = stringResource(if (busy || rows.any { loadingRows[rowKey(it)] == true }) R.string.moviotv_xp_loading_catalogue else R.string.moviotv_xp_no_titles),
                    poster = { index, width ->
                        val item = sectionTitles[index]
                        HomeCataloguePoster(
                            item = item, row = page, width = width, returnPoster = returnHomePoster,
                            onFocused = ::trackHomePoster,
                            initialRequester = if (index == 0) firstGridFocus else null,
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
            if (page == "addons" && adding && !showAddonRemote) {
                XpQa5Dialog(stringResource(R.string.moviotv_xp_addon_manifest_title),
                    stringResource(R.string.movioxp_close), { adding = false; notice = null }) {
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
                    XpDialogActions {
                        XpDialogButton(stringResource(R.string.moviotv_xp_install), OwnTVIcon.ADD, primary = true) {
                            val candidate = address.trim()
                            if (candidate.isBlank()) {
                                notice = context.getString(R.string.moviotv_xp_enter_addon_url)
                            } else installAddon(candidate)
                        }
                        if (!compactPhone) {
                            XpDialogButton(stringResource(R.string.moviotv_xp_remote_enter_on_phone), OwnTVIcon.PHONE) {
                                showAddonRemote = true
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
                        (listOfNotNull(addonsByUrl[builtInUrl]) + managedAddons).filter { it.name.contains(addonFilter, true) || it.description.contains(addonFilter, true) }
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
                                items((listOfNotNull(addonsByUrl[builtInUrl]) + managedAddons).filter { it.name.contains(addonFilter, true) || it.description.contains(addonFilter, true) }, key = { "installed:${it.url}" }) { addon ->
                                    XpAddonInfoCard(
                                        name = addon.name,
                                        version = addon.version,
                                        description = addon.description,
                                        types = addon.types,
                                        logo = addon.logo,
                                        action = stringResource(if (addon.builtIn) R.string.moviotv_xp_addon_builtin else R.string.moviotv_xp_remove),
                                        disabled = addon.builtIn || busy,
                                        controls = if (addon.builtIn) null else {
                                            {
                                                XpAddonPriorityControls(
                                                    moveableAddons.indexOf(addon.url), moveableAddons.lastIndex,
                                                    addon.url !in disabledAddonUrls && addon.url !in centralDisabledAddonUrls,
                                                    busy, { moveAddon(addon, -1) }, { moveAddon(addon, 1) },
                                                    { toggleAddon(addon) }.takeUnless { addon.url in centralDisabledAddonUrls },
                                                )
                                            }
                                        },
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
                    Text(stringResource(R.string.movioxp_account_login_title), color = XpText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.moviotv_xp_central_account_help),
                        color = XpMuted, fontSize = 16.sp,
                    )
                    if (centralConnection == null) {
                        XpCentralField(value = centralUsername, label = stringResource(R.string.moviotv_xp_central_username_label), onValueChange = { centralUsername = it })
                        XpCentralField(value = centralPassword, label = stringResource(R.string.moviotv_xp_central_password_label), onValueChange = { centralPassword = it }, password = true)
                        XpAction(stringResource(R.string.moviotv_xp_central_login), centralSyncBusy, onClick = ::signInCentral)
                        Text(stringResource(R.string.movioxp_login_or), color = XpMuted)
                        XpDialogButton(stringResource(R.string.movioxp_mobile_login), OwnTVIcon.PHONE,
                            enabled = !centralSyncBusy) { centralMobileLogin = true }
                        if (!BuildConfig.MOVIOXP_RELEASE) {
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
                        }
                    } else {
                        Text(stringResource(R.string.movioxp_account_name, centralConnection?.username.orEmpty()), color = XpAccent, fontSize = 17.sp)
                        XpAction(stringResource(R.string.moviotv_xp_central_sync_now), centralSyncBusy) { syncAddonsWithCentral() }
                        XpAction(stringResource(R.string.movioxp_logout_central), centralSyncBusy) {
                            centralSignOutAction = R.string.movioxp_logout_central
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
                                ?: XpCentralMetadata.category(id)?.let { XpCentralMetadata.customGenres[it] }?.let { stringResource(it) }
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
                        if (homeSettings.showDefaultCatalogs) externalRows.forEach { row ->
                            val id = externalRowId(row)
                            val enabled = XpHomeConfig.externalVisible(homeSettings, id, row.addon.builtIn)
                            XpSettingsOption(
                                stringResource(R.string.movioxp_home_row_toggle,
                                    stringResource(R.string.moviotv_xp_filter_catalog_label, row.catalog.name, row.addon.name),
                                    stringResource(if (enabled) R.string.movioxp_home_on else R.string.movioxp_home_off)),
                                selected = enabled,
                            ) {
                                homeSettings = XpHomeConfig.toggleExternal(homeSettings, id, enabled)
                                XpHomeConfig.save(context, favoritesProfileId, homeSettings)
                            }
                        }
                        XpSettingsOption(
                            title = stringResource(R.string.movioxp_home_add_category),
                            selected = showAddHomeCategory,
                        ) { showAddHomeCategory = !showAddHomeCategory }
                        if (showAddHomeCategory) {
                            val available = (availableHomeGenres +
                                if (centralConnection != null) XpCentralMetadata.customGenres.keys.map { it.replace('-', ' ') } else emptyList()
                            ).distinctBy { it.lowercase(java.util.Locale.ROOT).replace('-', ' ') }.sorted().filter { genre ->
                                val id = XpHomeConfig.idForGenre(genre)
                                id != null && id !in homeSettings.order
                            }
                            if (available.isEmpty()) {
                                Text(stringResource(R.string.movioxp_home_no_categories),
                                    color = XpMuted, fontSize = 13.sp)
                            }
                            available.forEach { genre ->
                                val category = XpHomeConfig.idForGenre(genre)?.let(XpCentralMetadata::category)
                                val label = category?.let { XpCentralMetadata.customGenres[it] }?.let { stringResource(it) } ?: genre
                                XpSettingsOption(label) {
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
                        XpSettingsFieldLabel(stringResource(R.string.movioxp_home_card_size))
                        listOf(
                            0.8f to R.string.movioxp_home_card_small,
                            1f to R.string.movioxp_home_card_medium,
                            1.25f to R.string.movioxp_home_card_large,
                        ).forEach { (size, label) ->
                            XpSettingsOption(stringResource(label), selected = homeScale == size) {
                                homeScale = size
                                libraryPrefs.edit().putFloat(homeZoomKey, size).apply()
                                XpCentralDataSync.markSetting(context, favoritesProfileId, homeZoomKey, size)
                            }
                        }
                        XpSettingsOption(stringResource(R.string.movioxp_show_rating), selected = showRating) {
                            showRating = !showRating
                            displayPrefs.edit().putBoolean(ratingKey, showRating).apply()
                            XpCentralDataSync.markSetting(context, favoritesProfileId, ratingKey, showRating)
                        }
                        XpSettingsOption(stringResource(R.string.movioxp_show_year), selected = showYear) {
                            showYear = !showYear
                            displayPrefs.edit().putBoolean(yearKey, showYear).apply()
                            XpCentralDataSync.markSetting(context, favoritesProfileId, yearKey, showYear)
                        }
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
                            Text(stringResource(R.string.movioxp_account_name,
                                centralConnection?.username.orEmpty()), color = XpAccent, fontSize = 15.sp)
                        }
                        XpSettingsOption(
                            title = stringResource(if (centralConnection == null)
                                R.string.moviotv_xp_central_connect_sync else R.string.moviotv_xp_central_sync_now),
                            enabled = !centralSyncBusy,
                        ) { if (centralConnection == null) signInCentral() else syncAddonsWithCentral() }
                        if (centralConnection == null) {
                            if (BuildConfig.MOVIOXP_RELEASE) {
                                XpSettingsOption(stringResource(R.string.movioxp_mobile_login), enabled = !centralSyncBusy) {
                                    centralMobileLogin = true
                                }
                            } else XpSettingsOption(stringResource(R.string.moviotv_xp_central_register),
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
                                centralSignOutAction = R.string.movioxp_logout_central
                            }
                        }
                        if (centralSyncBusy) Text(stringResource(R.string.moviotv_xp_central_syncing),
                            color = XpMuted, fontSize = 13.sp)
                        centralSyncMessage?.let { Text(it, color = XpAccent, fontSize = 13.sp) }
                    }
                    XpSettingsAccordion(
                        title = stringResource(R.string.movioxp_update_title),
                        summary = stringResource(R.string.movioxp_update_summary),
                        expanded = expandedSettingsGroup == 5,
                        onToggle = { expandedSettingsGroup = if (expandedSettingsGroup == 5) null else 5 },
                    ) {
                        XpSoftwareUpdatePanel(xpUpdater,
                            onCheck = { scope.launch { xpUpdater.check() } },
                            onInstall = { xpDownloadJob = scope.launch { xpUpdater.downloadAndInstall() } },
                            onCancel = { xpDownloadJob?.cancel(); xpDownloadJob = null },
                        )
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
            } else if (page == "library" || page == "discover") {
                val library = page == "library"
                val liveLabel = stringResource(R.string.moviotv_nav_live_tv)
                val liveSources = buildMap<String, Pair<XpDiscoverSource, String>> {
                    for (row in liveRows) {
                        val id = "live:" + externalRowId(row)
                        val label = context.getString(R.string.movioxp_live_catalogue_label, row.catalog.name, row.addon.name)
                        put(id, XpDiscoverSource(id, row) to label)
                        for ((field, values) in row.catalog.extraFields) {
                            if (field.equals("skip", true) || field == row.catalog.searchParameter) continue
                            for ((index, value) in values.distinct().withIndex()) {
                                val filterId = "$id:$field:$index"
                                put(filterId, XpDiscoverSource(filterId, row, mapOf(field to value)) to context.getString(R.string.movioxp_live_catalogue_label, value, label))
                            }
                        }
                    }
                }
                val category = if (library) libraryCategory else discoverCategory.let {
                    if (it.startsWith("live:") && it !in liveSources) "live" else it
                }
                val liveCategory = !library && (category == "live" || category in liveSources)
                val selectedLiveSources = when {
                    !liveCategory -> emptyList()
                    category == "live" -> liveSources.values.map { it.first }.filter { it.parameters.isEmpty() }
                    else -> listOfNotNull(liveSources[category]?.first)
                }
                val personalOptions = listOf(
                    XpBrowseCategory("favorites", stringResource(R.string.content_category_favorites)),
                    XpBrowseCategory("history", stringResource(R.string.movioxp_history)),
                )
                val options = if (library) personalOptions + listOf(
                    XpBrowseCategory("continue", stringResource(R.string.moviotv_xp_continue_watching)),
                    XpBrowseCategory("watchlist", stringResource(R.string.movioxp_watchlist)),
                ) else personalOptions + listOf(
                    XpBrowseCategory("movies", stringResource(R.string.moviotv_xp_all_movies)),
                    XpBrowseCategory("series", stringResource(R.string.moviotv_xp_all_series)),
                    XpBrowseCategory("live", liveLabel),
                ) + liveSources.map { (id, source) -> XpBrowseCategory(id, source.second, liveLabel) } + listOf("xp_trending", "xp_latest", "xp_cinema", "xp_upcoming", "xp_bollywood").mapNotNull { id ->
                    XpHomeConfig.categories.firstOrNull { it.id == id }?.let { XpBrowseCategory(id, stringResource(it.labelRes)) }
                } + listOf("xp_horror", "xp_action", "xp_thriller").mapNotNull { id ->
                    XpHomeConfig.categories.firstOrNull { it.id == id }?.let {
                        XpBrowseCategory(id, stringResource(it.labelRes), stringResource(R.string.movioxp_genres))
                    }
                } + (availableHomeGenres + XpCentralMetadata.customGenres.keys.map { it.replace('-', ' ') })
                    .mapNotNull(XpHomeConfig::idForGenre).distinctBy { it.lowercase(java.util.Locale.ROOT) }
                    .filter { XpCentralMetadata.category(it) !in setOf("horror", "action", "thriller") }
                    .mapNotNull { id ->
                        val name = XpCentralMetadata.category(id)?.let { XpCentralMetadata.customGenres[it] }?.let { stringResource(it) }
                            ?: XpHomeConfig.genreName(id)
                        name?.let { XpBrowseCategory(id, it, stringResource(R.string.movioxp_custom_categories)) }
                    }
                val firstPage = when (category) {
                    "favorites" -> favoriteTitles
                    "history" -> historyTitles
                    "continue" -> XpCollectionOrder.resumeShelf(continueTitles)
                    "watchlist" -> watchlistTitles
                    else -> null
                }
                val providerRow = if (category == "movies" || category == "series") {
                    val type = if (category == "movies") "movie" else "series"
                    availableCatalogRows.firstOrNull {
                        it.catalog.type == type && it.addon.builtIn && it.catalog.id == "top"
                    } ?: availableCatalogRows.firstOrNull { it.catalog.type == type }
                } else null
                val source = providerRow?.let { XpDiscoverSource(category, it) } ?: xpDiscoverSource(category, installedAddons)
                val centralCategory = if (category == "movies") "all-movies" else XpCentralMetadata.category(category)
                val snapshotKey = "$page|$category"
                var railQuery by key(page, catalogueProfile) { rememberSaveable { mutableStateOf("") } }
                val railState = key(page, catalogueProfile) { rememberLazyListState() }
                var restoreRailFocus by remember(page, catalogueProfile) { mutableStateOf(false) }
                personalGridState.SaveableStateProvider(snapshotKey) {
                    val pages = remember(catalogueProfile, category) { mutableStateMapOf<Int, XpCatalogueSlice>() }
                    var loadPage by rememberSaveable { mutableIntStateOf(1) }
                    var pageLoading by remember { mutableStateOf(false) }
                    var pageFailed by remember { mutableStateOf(false) }
                    var reload by remember { mutableLongStateOf(0L) }
                    var pageOneRefresh by remember { mutableLongStateOf(0L) }
                    val liveTitles = remember(catalogueProfile, selectedLiveSources) {
                        mutableStateMapOf<XpDiscoverSource, List<XpMeta>>()
                    }
                    LaunchedEffect(catalogueProfile, selectedLiveSources, refreshCatalogues, reload) {
                        if (selectedLiveSources.isEmpty()) return@LaunchedEffect
                        pageLoading = true
                        pageFailed = false
                        try {
                            val completed = selectedLiveSources.map { liveSource ->
                                async {
                                    XpAddonProtocol.observeCatalog(repository, catalogueProfile, liveSource.row,
                                        liveSource.parameters, maxOf(refreshCatalogues, reload)).collect { titles ->
                                        if (titles != null) liveTitles[liveSource] = titles
                                    }
                                    liveSource in liveTitles
                                }
                            }.awaitAll()
                            pageFailed = completed.none { it }
                        } finally {
                            pageLoading = false
                        }
                    }
                    LaunchedEffect(source, centralConnection, centralCategory, loadPage, refreshCatalogues, reload, liveCategory) {
                        if (firstPage != null || liveCategory) return@LaunchedEffect
                        val refresh = maxOf(refreshCatalogues, reload)
                        pageLoading = true
                        pageFailed = false
                        try {
                            // Rehydrate the already-viewed window from disk after a process recreation.
                            for (index in 1..loadPage.coerceIn(1, XpCataloguePaging.MAX_PAGES)) {
                                if (index > 1 && pages[index - 1]?.next != index) break
                                if (index in pages && (index != 1 || refresh <= pageOneRefresh)) continue
                                val requestRefresh = if (index == 1 || index == loadPage) refresh else 0L
                                suspend fun fallback() {
                                    if (source == null || (index > 1 && "skip" !in source.row.catalog.extraFields)) return
                                    val skipped = pages.filterKeys { it < index }.values.sumOf { it.titles.size }
                                    val overrides = if (index == 1) source.parameters else source.parameters + ("skip" to skipped.toString())
                                    XpAddonProtocol.observeCatalog(repository, catalogueProfile, source.row, overrides, requestRefresh).collect { titles ->
                                        if (titles != null) {
                                            val slice = titles.take(XpCataloguePaging.PAGE_SIZE)
                                            val repeated = index > 1 && slice.all { title ->
                                                pages[index - 1]?.titles?.any { XpPlaybackIdentity.sameTitle(it, title) } == true
                                            }
                                            val next = XpCataloguePaging.next(index, index + 1)?.takeIf {
                                                slice.isNotEmpty() && !repeated && "skip" in source.row.catalog.extraFields
                                            }
                                            pages[index] = XpCatalogueSlice(slice, next, providerFallback = true)
                                        }
                                    }
                                }
                                val connection = centralConnection
                                if (connection == null || centralCategory == null || pages[index - 1]?.providerFallback == true) fallback()
                                else XpCentralMetadata.observePage(repository, catalogueProfile, connection, centralCategory, index, requestRefresh)
                                    .collectLatest { data ->
                                        when {
                                            data?.failed == true -> pageFailed = true
                                            data != null -> {
                                                val previous = pages[index]
                                                if (previous != null && previous.providerFallback != data.providerFallback) {
                                                    pages.keys.removeAll { it > index }
                                                }
                                                pages[index] = data
                                            }
                                            index == 1 -> fallback()
                                        }
                                    }
                                if (index == 1) pageOneRefresh = refresh
                                if (index !in pages) {
                                    pageFailed = true
                                    break
                                }
                            }
                        } finally {
                            pageLoading = false
                        }
                    }
                    val loadedPages = pages.toMap()
                    val gridTitles = if (liveCategory) {
                        sortedTitles(selectedLiveSources.flatMap { liveTitles[it].orEmpty() }
                            .distinctBy { it.type to it.id }, category)
                    } else remember(firstPage, loadedPages, sortMode, category) {
                        val titles = firstPage ?: XpCataloguePaging.unique(
                            loadedPages.toSortedMap().values.flatMap { it.titles },
                        ) { it }.take(XpCataloguePaging.MAX_TITLES)
                        if (firstPage != null) titles else sortedTitles(titles, category)
                    }
                    XpBrowseGrid(
                        options = options,
                        category = category,
                        focusScope = snapshotKey,
                        categoryQuery = railQuery,
                        onCategoryQuery = { railQuery = it },
                        railState = railState,
                        restoreRailFocus = restoreRailFocus,
                        onRailFocusHandled = { restoreRailFocus = false },
                        onEditing = { gridEditing = it },
                        sortLabels = sortLabels,
                        selectedSort = xpCategorySort(category.takeUnless { library }, sortMode.takeIf { it >= 0 }),
                        onSort = ::changeSort,
                        titles = gridTitles,
                        scale = if (library) libraryScale else discoverScale,
                        loading = pageLoading,
                        failed = pageFailed,
                        landscape = liveCategory,
                        emptyLabel = if (liveCategory && liveRows.isEmpty()) R.string.movioxp_live_no_addons else null,
                        canLoadMore = firstPage == null && pages[loadPage]?.next != null,
                        onLoadMore = {
                            val next = pages[loadPage]?.next
                            if (!pageLoading && next != null) {
                                pageLoading = true
                                loadPage = next
                            }
                        },
                        onRetry = { reload = System.currentTimeMillis() },
                        onCategory = { id, fromRail ->
                            restoreRailFocus = fromRail && id != category
                            if (library) libraryCategory = id else {
                                discoverCategory = id
                                pagePrefs.edit().putString(catalogueProfile, id).apply()
                            }
                        },
                        onZoom = { next ->
                            if (library) libraryScale = next else discoverScale = next
                            val zoomKey = if (library) libraryZoomKey else discoverZoomKey
                            libraryPrefs.edit().putFloat(zoomKey, next).apply()
                            XpCentralDataSync.markSetting(context, favoritesProfileId, zoomKey, next)
                        },
                        restorePoster = returnHomePoster,
                        onFocused = ::trackHomePoster,
                        onOpen = { openItem(it, resume = library && category == "continue") },
                        onDetails = ::openDetails,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
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
                            val ordered = remember(searchHits, sortMode) {
                                val hits = searchHits.associateBy { it.meta }
                                sortXpTitles(searchHits.map { it.meta }, sortMode).mapNotNull(hits::get)
                            }
                            LaunchedEffect(returnHomePoster, ordered) {
                                val target = returnHomePoster ?: return@LaunchedEffect
                                val index = xpRestoredPosterIndex("search", ordered.map { it.meta }, target)
                                if (index >= 0 && searchListState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
                                    searchListState.scrollToItem(index)
                                }
                            }
                            LazyColumn(state = searchListState, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 14.dp)) {
                                items(ordered, key = { "${it.addon.url}|${it.meta.type}:${it.meta.id}" }) { hit ->
                                    XpSearchResultCard(hit, returnHomePoster, ::trackHomePoster) {
                                        trackHomePoster(homePosterKey("search", hit.meta))
                                        keyboard?.hide()
                                        openItem(hit.meta)
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (rows.isEmpty() && page != "home" && visibleHomeIds.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.moviotv_xp_empty_title), color = XpText, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.moviotv_xp_empty_description), color = XpMuted, fontSize = 16.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth()
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
                    state = if (page == "home") homeListState else rememberLazyListState(),
                    contentPadding = PaddingValues(bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (page == "home") {
                        item(key = "xp-continue-watching") {
                            val titles = XpCollectionOrder.resumeShelf(continueTitles)
                            if (titles.isNotEmpty()) Column {
                                Text(stringResource(R.string.moviotv_xp_continue_watching), color = XpText, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 24.dp, top = 2.dp))
                                LazyRow(state = rememberHomeCatalogueRowState("xp-continue-watching", titles, returnHomePoster),
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(titles, key = { "continue:${it.type}:${it.id}" }) { title ->
                                        HomeCataloguePoster(title, "xp-continue-watching", homePosterWidth, returnHomePoster,
                                            onFocused = ::trackHomePoster,
                                            onLongClick = { openDetails(title, false) },
                                            onVeryLongClick = { openDetails(title, true) }) { openItem(title, resume = true) }
                                    }
                                }
                            }
                        }
                    }

                    if (visibleHomeIds.isNotEmpty()) {
                        items(visibleHomeIds, key = { "discover:$it" }) { id ->
                            val category = XpHomeConfig.categories.firstOrNull { it.id == id }
                            val label = category?.let { stringResource(it.labelRes) }
                                ?: XpCentralMetadata.category(id)?.let { XpCentralMetadata.customGenres[it] }?.let { stringResource(it) }
                                ?: XpHomeConfig.genreName(id)
                            val source = homeSources[id]
                            if (label != null) {
                                Column {
                                    Text(
                                        label, color = XpText,
                                        fontSize = 20.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(start = 24.dp, top = 6.dp),
                                    )
                                    if (source == null && (centralConnection == null || XpCentralMetadata.category(id) == null)) {
                                        Text(
                                            stringResource(R.string.movioxp_discover_no_source),
                                            color = XpMuted, fontSize = 13.sp,
                                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 9.dp),
                                        )
                                    } else {
                                        val titles = sortedTitles(discoverMetas[discoveryDataKey(id, source)].orEmpty(), id)
                                        if (titles.isEmpty()) {
                                            Text(
                                                stringResource(if (discoverLoading[id] == true)
                                                    R.string.moviotv_xp_loading_catalogue else R.string.moviotv_xp_no_titles),
                                                color = XpMuted, fontSize = 13.sp,
                                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 9.dp),
                                            )
                                        } else {
                                            LazyRow(
                                                state = rememberHomeCatalogueRowState(id, titles, returnHomePoster.takeIf { page == "home" }),
                                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            ) {
                                                items(titles, key = { "${it.type}:${it.id}" }) { item ->
                                                    HomeCataloguePoster(item, id, homePosterWidth,
                                                        returnHomePoster.takeIf { page == "home" },
                                                        onFocused = { if (page == "home") trackHomePoster(it) },
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
                    if (page == "home" && visibleRows.isEmpty() && visibleHomeIds.isEmpty() && continueTitles.isEmpty()) {
                        item(key = "xp-home-empty") {
                            Text(stringResource(R.string.moviotv_xp_empty_description), color = XpMuted, fontSize = 16.sp,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp))
                        }
                    }
                    items(visibleRows, key = { rowKey(it) }) { row ->
                        val key = rowKey(row)
                        val titles = sortedTitles(metas[key to row].orEmpty())
                        Column {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
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
                                    state = rememberHomeCatalogueRowState(key, titles, returnHomePoster.takeIf { page == "home" }),
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(titles, key = { "${it.type}:${it.id}" }) { item ->
                                        HomeCataloguePoster(item, key, homePosterWidth,
                                            returnHomePoster.takeIf { page == "home" },
                                            onFocused = { if (page == "home") trackHomePoster(it) },
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
                            compact = compactPhone, focusRequester = bottomNavigationFocus.takeIf {
                                key == if (page in setOf("home", "discover", "library", "addons")) page else "home"
                            }) {
                            page = if (key == "app_settings") "settings" else key
                            if (key == "addons") adding = false
                    }
                }
            }
        }
        val signOutAction = centralSignOutAction
        if (signOutAction != null) {
            XpCentralSignOutDialog(
                action = signOutAction,
                enabled = !centralSyncBusy,
                onCancel = { centralSignOutAction = null },
                onConfirm = {
                    centralSignOutAction = null
                    signOutCentral()
                },
            )
        }
        if (centralMobileLogin) {
            XpCentralPairingDialog(centralApiBase, onDismiss = { centralMobileLogin = false }) { connection ->
                XpCentralConnectionStore.write(context, connection)
                centralConnection = connection
                centralApiBase = connection.apiBase.removeSuffix("/")
                centralPassword = ""
                centralSyncMessage = null
                centralMobileLogin = false
            }
        }
        if (showAccountMenu && centralSignOutAction == null) {
            val connection = centralConnection
            val statusColor = when {
                connection == null -> Color(0xFF9A9AA3)
                centralConnecting -> Color(0xFFFFB83D)
                centralReachable -> Color(0xFF36D68A)
                else -> Color(0xFFF54C55)
            }
            val statusLabel = when {
                connection == null -> R.string.movioxp_central_local
                centralConnecting -> R.string.movioxp_central_connecting
                centralReachable -> R.string.movioxp_central_online
                else -> R.string.movioxp_central_offline
            }
            XpQa5Dialog(activeProfileName.ifBlank { stringResource(R.string.movioxp_guest_label) },
                stringResource(R.string.movioxp_close), { showAccountMenu = false }, titleIcon = OwnTVIcon.PERSON) {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (connection != null) {
                        Text(stringResource(R.string.movioxp_account_name, connection.username),
                            color = XpText, fontSize = 16.sp, maxLines = 3)
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Box(Modifier.size(13.dp).background(statusColor, androidx.compose.foundation.shape.CircleShape))
                        Text(stringResource(statusLabel), color = statusColor,
                            fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    }
                    XpDialogActions(stacked = true) {
                        XpDialogButton(stringResource(R.string.movioxp_switch_profile), OwnTVIcon.PERSON,
                            enabled = onSwitchProfile != null) {
                            showAccountMenu = false
                            onSwitchProfile?.invoke()
                        }
                        XpDialogButton(stringResource(R.string.movioxp_add_profile), OwnTVIcon.ADD) {
                            showAccountMenu = false
                            centralProfileName = ""
                            profileError = false
                            addProfileOpen = true
                        }
                        XpDialogButton(stringResource(R.string.movioxp_manage_profile), OwnTVIcon.GEAR) {
                            showAccountMenu = false
                            profileError = false
                            if (connection != null) manageCentralProfiles = true
                            else if (onOpenSettingsGroup != null) onOpenSettingsGroup.invoke(
                                tv.own.owntv.features.shell.components.SettingsGroup.PROFILE.ordinal)
                            else page = "settings"
                        }
                        if (connection != null) {
                            XpDialogButton(stringResource(R.string.movioxp_switch_account), OwnTVIcon.SWAP,
                                enabled = !centralSyncBusy) { centralSignOutAction = R.string.movioxp_switch_account }
                        } else {
                            XpDialogButton(stringResource(R.string.moviotv_xp_central_login), OwnTVIcon.PERSON) {
                                showAccountMenu = false
                                page = "central"
                            }
                        }
                        XpDialogButton(stringResource(R.string.movioxp_logout_central), OwnTVIcon.POWER,
                            enabled = connection != null && !centralSyncBusy) { centralSignOutAction = R.string.movioxp_logout_central }
                        XpDialogButton(stringResource(R.string.movioxp_exit), OwnTVIcon.POWER) {
                            showAccountMenu = false
                            confirmExit = true
                        }
                    }
                }
            }
        }
        if (addProfileOpen) {
            val connection = centralConnection
            if (connection == null) {
                tv.own.owntv.features.profiles.ProfileEditorDialog(
                    initial = null, onDismiss = { addProfileOpen = false }, movioXpDialog = true,
                    takenNames = profileList.map { it.name.trim().lowercase() }.toSet(),
                    onConfirm = { name, avatar, child, pin, picture ->
                        addProfileOpen = false
                        profiles.create(name, avatar, child, pin, name, picture)
                    },
                )
            } else {
                XpQa5Dialog(stringResource(R.string.movioxp_add_profile), stringResource(R.string.movioxp_close),
                    { if (!profileWorking) addProfileOpen = false },
                    primaryAction = {
                        XpDialogButton(stringResource(R.string.profiles_create), OwnTVIcon.ADD, enabled = !profileWorking &&
                            centralProfileName.isNotBlank() && centralProfileName.trim().length <= 80, primary = true) {
                            val name = centralProfileName.trim()
                            profileWorking = true
                            profileError = false
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        val existing = XpCentralAddonSync.request(connection.apiBase, "profiles", connection.token).getJSONArray("profiles")
                                        require((0 until existing.length()).none {
                                            existing.optJSONObject(it)?.optString("name").equals(name, ignoreCase = true)
                                        })
                                        XpCentralAddonSync.request(connection.apiBase, "profiles", connection.token, "POST",
                                            JSONObject().put("name", name).put("is_child", false))
                                    }
                                    XpCentralProfileScope.refresh(context, connection, profiles)
                                }.onSuccess { addProfileOpen = false }.onFailure { profileError = true }
                                profileWorking = false
                            }
                        }
                    }) {
                    XpCentralField(centralProfileName, stringResource(R.string.movioxp_profile_name), { centralProfileName = it })
                    if (profileError) Text(stringResource(R.string.movioxp_profile_create_failed), color = XpMuted)
                }
            }
        }
        if (manageCentralProfiles) {
            XpQa5Dialog(stringResource(R.string.movioxp_manage_profile), stringResource(R.string.movioxp_close),
                { manageCentralProfiles = false }) {
                Text(stringResource(R.string.movioxp_central_profile_management), color = XpMuted)
                XpDialogButton(stringResource(R.string.moviotv_xp_refresh), OwnTVIcon.REFRESH, enabled = !profileWorking) {
                    val connection = centralConnection ?: return@XpDialogButton
                    profileWorking = true
                    scope.launch {
                        runCatching { XpCentralProfileScope.refresh(context, connection, profiles) }
                            .onSuccess { manageCentralProfiles = false }.onFailure { profileError = true }
                        profileWorking = false
                    }
                }
                if (profileError) Text(stringResource(R.string.moviotv_xp_central_sync_failed), color = XpMuted)
            }
        }
        if (showLogoMenu) {
            XpQa5Dialog(stringResource(R.string.moviotv_xp_title), stringResource(R.string.common_cancel),
                { showLogoMenu = false }, titleContent = {
                    Text(androidx.compose.ui.text.buildAnnotatedString {
                        append(stringResource(R.string.movioxp_logo_menu_movio))
                        pushStyle(androidx.compose.ui.text.SpanStyle(color = XpRemotePink))
                        append(stringResource(R.string.movioxp_logo_menu_xp))
                        pop()
                    }, color = XpText, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                }) {
                XpDialogActions {
                    XpDialogButton(stringResource(R.string.moviotv_xp_about_heading), OwnTVIcon.INFO) {
                        showLogoMenu = false
                        showXpAbout = true
                    }
                    XpDialogButton(stringResource(R.string.movioxp_exit), OwnTVIcon.POWER) {
                        showLogoMenu = false
                        confirmExit = true
                    }
                }
            }
        }
        if (showXpAbout) XpAboutDialog(onDismiss = { showXpAbout = false })
        if (confirmExit) {
            XpQa5Dialog(stringResource(R.string.movioxp_exit), stringResource(R.string.common_cancel),
                { confirmExit = false }) {
                Text(stringResource(R.string.movioxp_exit_confirm), color = XpMuted)
                XpDialogButton(stringResource(R.string.movioxp_exit), OwnTVIcon.POWER) {
                    confirmExit = false
                    XpCentralEventSync.request(context, favoritesProfileId)
                    val connection = centralConnection
                    if (connection != null && favoritesProfileId > 0L) {
                        scope.launch {
                            runCatching {
                                kotlinx.coroutines.withTimeout(1500L) {
                                    XpCentralDataSync.sync(context, connection, favoritesProfileId,
                                        activeProfileName, activeProfile?.isKids == true)
                                }
                            }
                            activity?.finish()
                        }
                    } else activity?.finish()
                }
            }
        }
        if (genrePickerOpen) {
            XpQa5Dialog(stringResource(R.string.moviotv_categories), stringResource(R.string.movioxp_close),
                { genrePickerOpen = false }) {
                XpGenreRail(
                    modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
                    genres = genreOptions, selected = discoveryGenre,
                    allLabel = context.getString(if (page == "movies") R.string.moviotv_xp_all_movies else R.string.moviotv_xp_all_series),
                    focusRequester = categoryRailFocus, rightFocusRequester = firstGridFocus,
                    onSelect = { discoveryGenre = it; genrePickerOpen = false },
                )
            }
        }
        if (showAddonRemote) {
            XpQa5Dialog(stringResource(R.string.moviotv_xp_remote_tv_title), stringResource(R.string.movioxp_close),
                { showAddonRemote = false; addonRemoteServer.close() }, width = 560.dp) {
                Column(
                    Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
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
                }
            }
        }
        selected?.let { item ->
            val metadata = detailsMetadata
            if (showCredits) {
                XpQa5Dialog(stringResource(R.string.movioxp_credits), stringResource(R.string.movioxp_close),
                    { showCredits = false }, width = 620.dp) {
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val credits = listOf(
                            R.string.movioxp_cast to metadata?.cast.orEmpty(),
                            R.string.movioxp_directors to metadata?.directors.orEmpty(),
                            R.string.movioxp_writers to metadata?.writers.orEmpty(),
                        )
                        if (credits.all { it.second.isEmpty() }) {
                            Text(stringResource(R.string.movioxp_credits_unavailable), color = XpMuted)
                        }
                        credits.forEach { (heading, people) ->
                            if (people.isNotEmpty()) {
                                Text(stringResource(heading), color = XpText, fontWeight = FontWeight.Bold)
                                Text(people.joinToString(), color = XpMuted)
                            }
                        }
                    }
                }
            } else if (showManualSources) {
                XpQa5Dialog(stringResource(R.string.moviotv_xp_stream_sources), stringResource(R.string.common_back),
                    { showManualSources = false; manualSourceSelection = true }, width = 660.dp) {
                    Text(item.name, color = XpMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val saved = savedSource(playbackWithResume(item))
                    val candidates = selectedStreams.mapNotNull(XpStream::playback)
                    val lastPlayed = XpSourcePreference.playedMatch(saved, candidates)
                    val ordered = if (lastPlayed == null) selectedStreams else
                        selectedStreams.sortedByDescending { it.playback() == lastPlayed }
                    if (saved != null && lastPlayed == null && !streamsLoading) {
                        Text(stringResource(R.string.movioxp_last_source_missing), color = XpMuted)
                    }
                    when {
                        streamsLoading -> Text(stringResource(R.string.moviotv_xp_loading_streams), color = XpMuted)
                        selectedStreams.isEmpty() -> Text(stringResource(R.string.moviotv_xp_no_streams), color = XpMuted)
                        else -> LazyColumn(Modifier.weight(1f, fill = false).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            itemsIndexed(ordered) { index, stream ->
                                val resultLabel = stringResource(R.string.moviotv_xp_stream_result, stream.label, stream.addon.name)
                                val label = stream.detail?.let {
                                    stringResource(R.string.moviotv_xp_stream_result_detail, resultLabel, it)
                                } ?: resultLabel
                                val actionLabel = if (stream.playback() == lastPlayed && lastPlayed != null)
                                    stringResource(R.string.movioxp_last_source, label) else label
                                XpDialogButton(actionLabel, OwnTVIcon.PLAY, enabled = stream.url != null,
                                    primary = lastPlayed != null && stream.playback() == lastPlayed,
                                    requester = if (index == 0) firstSourceFocus else null) {
                                    stream.url?.let { playable ->
                                    val playback = playbackWithResume(item)
                                    selected = null
                                    manualSourceSelection = false
                                    showManualSources = false
                                    if (onPlayStreamCandidates != null) {
                                        onPlayStreamCandidates(
                                            playbackCandidates(playback, listOfNotNull(stream.playback()?.copy(userSelected = true)) +
                                                candidates.filterNot { it.url == playable }),
                                            playback.name, playback.type, playback.releaseInfo, playback.id, playback.poster, playback.positionMs,
                                        )
                                    } else {
                                        onPlayStream(playable, playback.name, playback.type, playback.releaseInfo, playback.id, playback.poster, playback.positionMs)
                                    }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                val chooseEpisode = item.type == "series" && XpPlaybackIdentity.isImdbId(item.id) && selectedEpisode == null
                XpQa5Dialog(item.name, stringResource(R.string.movioxp_close), ::closeDetails, width = 880.dp,
                    primaryAction = {
                        XpDialogButton(stringResource(R.string.movioxp_find_sources), OwnTVIcon.PLAY,
                            enabled = !chooseEpisode, requester = detailFocus, primary = true) {
                            manualSourceSelection = true
                            showManualSources = true
                        }
                    }) { wide ->
                    val body: @Composable () -> Unit = {
                        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            val rating = xpRating(playbackMetadata(item))?.let {
                                val value = String.format(java.util.Locale.US, "%.1f", it)
                                val sourceLabel = when (playbackMetadata(item).ratingSource) {
                                    "TMDB" -> context.getString(R.string.movioxp_tmdb_rating, value)
                                    "IMDb" -> context.getString(R.string.movioxp_imdb_rating, value)
                                    else -> value
                                }
                                context.getString(R.string.moviotv_xp_rating, sourceLabel)
                            }
                            val runtime = metadata?.runtime?.let {
                                if (it.toIntOrNull() != null) stringResource(R.string.movioxp_runtime_minutes, it.toInt()) else it
                            }
                            val line = listOfNotNull(xpReleaseYear(item), rating, runtime)
                                .plus(metadata?.genres.orEmpty()).joinToString(stringResource(R.string.moviotv_xp_meta_separator))
                            if (line.isNotBlank()) Text(line, color = XpAccent, fontSize = 14.sp)
                            item.description?.takeIf(String::isNotBlank)?.let { description ->
                                var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
                                Text(description, color = XpMuted, fontSize = 15.sp,
                                    maxLines = if (expanded) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis)
                                if (description.length > 140) {
                                    XpDialogButton(stringResource(if (expanded) R.string.movioxp_read_less else R.string.movioxp_read_more),
                                        if (expanded) OwnTVIcon.CHEVRON_UP else OwnTVIcon.CHEVRON_DOWN) { expanded = !expanded }
                                }
                            }
                            val collectionItem = item.copy(imdbId = selectedImdbId ?: item.imdbId)
                            val savedWatch = watchlistTitles.firstOrNull { XpPlaybackIdentity.sameTitle(it, collectionItem) }
                            XpDialogActions {
                                XpDialogButton(stringResource(if (favoriteTitles.any { XpPlaybackIdentity.sameTitle(it, collectionItem) })
                                    R.string.moviotv_xp_remove_favorite else R.string.moviotv_xp_add_favorite), OwnTVIcon.FAVORITE) {
                                    toggleFavorite(item)
                                }
                                XpDialogButton(stringResource(if (savedWatch == null) R.string.movioxp_watchlist_add else R.string.movioxp_watchlist_remove),
                                    OwnTVIcon.PLAYLIST) {
                                    XpPersonalCollections.change(context, favoritesProfileId, XpCollection.WATCHLIST,
                                        savedWatch ?: collectionItem, deleted = savedWatch != null)
                                }
                                XpDialogButton(stringResource(R.string.movioxp_credits), OwnTVIcon.PERSON) { showCredits = true }
                                val history = historyTitles.filter { XpPlaybackIdentity.sameTitle(it, collectionItem) }
                                if (history.isNotEmpty()) XpDialogButton(stringResource(R.string.movioxp_history_remove), OwnTVIcon.HISTORY) {
                                    history.forEach { XpPersonalCollections.change(context, favoritesProfileId,
                                        XpCollection.HISTORY, it, deleted = true) }
                                }
                            }
                if (item.type == "series" && XpPlaybackIdentity.isImdbId(item.id)) {
                    val seasons = episodes.map { it.season }.distinct()
                    val seasonTitle = stringResource(R.string.movioxp_season, selectedSeason)
                    if (seasons.isNotEmpty()) XpFilterDropdown(
                        title = seasonTitle, selectedLabel = seasonTitle,
                        options = seasons.map { it.toString() to stringResource(R.string.movioxp_season, it) },
                        expanded = seasonMenu, onToggle = { seasonMenu = !seasonMenu; episodeMenu = false },
                        onSelect = { next ->
                            selectedSeason = next.toInt()
                            selectedEpisode = null
                            seasonMenu = false
                        },
                    )
                    val label = selectedEpisode?.let { stringResource(R.string.movioxp_episode, it.number) }
                        ?: stringResource(R.string.movioxp_choose_episode)
                    if (seasons.isNotEmpty()) XpFilterDropdown(
                        title = label, selectedLabel = label,
                        options = episodes.filter { it.season == selectedSeason }.map {
                            it.id to (it.title ?: stringResource(R.string.movioxp_episode, it.number))
                        },
                        expanded = episodeMenu, onToggle = { episodeMenu = !episodeMenu; seasonMenu = false },
                        onSelect = { id ->
                            selectedEpisode = episodes.firstOrNull { it.id == id }
                            episodeMenu = false
                            pagePrefs.edit().putString(episodePreference(item.id), id).apply()
                            manualSourceSelection = true
                            showManualSources = true
                        },
                    )
                }

                        }
                    }
                    if (wide) {
                        Row(Modifier.weight(1f, fill = false).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            item.poster?.let {
                                AsyncImage(it, null, Modifier.width(156.dp).aspectRatio(.68f).clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop)
                            }
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { body() }
                        }
                    } else {
                        Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            item.poster?.let {
                                AsyncImage(it, null, Modifier.width(96.dp).aspectRatio(.68f).clip(RoundedCornerShape(10.dp)),
                                    contentScale = ContentScale.Crop)
                            }
                            body()
                        }
                    }
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
    val tv = LocalXpTvControls.current
    val focusColor = if (tv) XpRemotePink else XpFocus
    Column(
        Modifier.weight(1f).padding(horizontal = if (compact) 0.dp else 2.dp).clip(RoundedCornerShape(12.dp))
            .background(if (selected && tv) XpAccent.copy(alpha = .20f)
                else if (focused && !tv) XpFocus.copy(alpha = .32f)
                else if (selected) XpAccent.copy(alpha = .20f) else Color.Transparent)
            .border(if (focused) 3.dp else 1.dp, if (focused) focusColor else Color.Transparent, RoundedCornerShape(12.dp))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick)
            .padding(horizontal = if (compact) 1.dp else 5.dp, vertical = if (compact) 7.dp else 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        DrawOwnTVIcon(icon, if (focused) focusColor else if (selected) XpAccent else XpMuted, Modifier.size(if (compact) 21.dp else 23.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = if (focused) focusColor else if (selected) XpText else XpMuted,
            fontSize = if (compact) 10.sp else 12.sp, fontWeight = FontWeight.SemiBold,
            maxLines = if (compact) 2 else 1, overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center, lineHeight = if (compact) 12.sp else 15.sp)
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
    var focused by remember { mutableStateOf(false) }
    val pink = focused && LocalXpTvControls.current
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                .background(XpPanel)
                .border(if (pink) 2.dp else 0.dp, if (pink) XpRemotePink else Color.Transparent, RoundedCornerShape(12.dp))
                .onFocusChanged { focused = it.isFocused }.clickable(onClick = onToggle)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(selectedLabel, color = if (pink) XpRemotePink else XpText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(if (expanded) R.string.moviotv_xp_filter_expanded else R.string.moviotv_xp_filter_collapsed), color = if (pink) XpRemotePink else XpMuted, fontSize = 13.sp)
        }
        if (expanded) {
            Column(
                Modifier.fillMaxWidth().padding(top = 4.dp).heightIn(max = 260.dp)
                    .clip(RoundedCornerShape(12.dp)).background(XpPanel).verticalScroll(rememberScrollState()),
            ) {
                Text(title, color = XpMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp))
                options.distinctBy { it.first }.forEach { (key, label) ->
                    XpGenreRow(label, label == selectedLabel, null, null) { onSelect(key) }
                }
            }
        }
    }
}

private data class XpBrowseCategory(val id: String, val label: String, val group: String? = null)

@Composable
private fun XpBrowseGrid(
    options: List<XpBrowseCategory>,
    category: String,
    focusScope: String,
    categoryQuery: String,
    onCategoryQuery: (String) -> Unit,
    railState: androidx.compose.foundation.lazy.LazyListState,
    restoreRailFocus: Boolean,
    onRailFocusHandled: () -> Unit,
    onEditing: (Boolean) -> Unit,
    sortLabels: List<String>,
    selectedSort: Int,
    onSort: (Int) -> Unit,
    titles: List<XpMeta>,
    scale: Float,
    loading: Boolean,
    failed: Boolean,
    landscape: Boolean = false,
    emptyLabel: Int? = null,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onCategory: (String, Boolean) -> Unit,
    onZoom: (Float) -> Unit,
    restorePoster: String?,
    onFocused: (String) -> Unit,
    onOpen: (XpMeta) -> Unit,
    onDetails: (XpMeta, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sidebar by rememberSaveable { mutableStateOf(false) }
    var filterOpen by rememberSaveable { mutableStateOf(false) }
    val categoryFocus = remember { FocusRequester() }
    val gridFocus = remember { FocusRequester() }
    val filterFocus = remember { FocusRequester() }
    val editingNow by rememberUpdatedState(onEditing)
    DisposableEffect(Unit) { onDispose { editingNow(false) } }
    LaunchedEffect(restoreRailFocus) {
        if (restoreRailFocus) {
            withFrameNanos { }
            runCatching { categoryFocus.requestFocus() }
            onRailFocusHandled()
        }
    }
    var titleQuery by rememberSaveable { mutableStateOf("") }
    val state = rememberLazyGridState()
    val shown = remember(titles, titleQuery) { titles.filter { it.name.contains(titleQuery.trim(), true) } }
    val scaleNow by rememberUpdatedState(scale)
    val zoomNow by rememberUpdatedState(onZoom)
    val nearEnd = shown.isNotEmpty() && state.layoutInfo.visibleItemsInfo.lastOrNull()?.let {
        it.index >= shown.lastIndex - 3
    } == true
    LaunchedEffect(nearEnd, shown.size, loading, failed, canLoadMore, titleQuery) {
        if (nearEnd && !loading && !failed && canLoadMore && titleQuery.isBlank()) onLoadMore()
    }
    LaunchedEffect(restorePoster, shown) {
        if (restorePoster != null) {
            val index = xpRestoredPosterIndex(focusScope, shown, restorePoster)
            if (index >= 0 && state.layoutInfo.visibleItemsInfo.none { it.index == index }) state.scrollToItem(index)
        }
    }

    BoxWithConstraints(modifier.padding(horizontal = 12.dp)) {
        val wide = maxWidth >= 560.dp
        val railWidth = (maxWidth * 0.30f).coerceIn(166.dp, 260.dp)
        val categoryLabel = options.firstOrNull { it.id == category }?.label ?: stringResource(R.string.moviotv_categories)
        BackHandler(enabled = !LocalXpPlaybackActive.current && (filterOpen || (!wide && sidebar))) {
            if (filterOpen) {
                filterOpen = false
                runCatching { filterFocus.requestFocus() }
            } else sidebar = false
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (wide || sidebar) {
                Column(
                    (if (wide) Modifier.width(railWidth) else Modifier.fillMaxWidth()).fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp)).background(XpPanel).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (!wide) MovioBrowseButton(stringResource(R.string.moviotv_categories), { sidebar = false },
                        background = XpBackground, foreground = XpText)
                    MovioTouchSearch(
                        categoryQuery, onCategoryQuery, Modifier.fillMaxWidth().onFocusChanged { onEditing(it.hasFocus) },
                        hint = stringResource(R.string.movioxp_filter_categories),
                        background = XpBackground, outline = XpAccent.copy(alpha = .45f), textColor = XpText, hintColor = XpMuted,
                    )
                    val matching = options.filter { it.label.contains(categoryQuery.trim(), true) }
                    LazyColumn(state = railState, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        matching.forEachIndexed { index, entry ->
                            if (entry.group != null && matching.getOrNull(index - 1)?.group != entry.group) {
                                item {
                                    Text(entry.group, color = XpMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 4.dp))
                                }
                            }
                            item(key = entry.id) {
                                XpGenreRow(entry.label, category == entry.id,
                                    categoryFocus.takeIf { category == entry.id || (index == 0 && matching.none { it.id == category }) },
                                    gridFocus.takeIf { wide },
                                ) {
                                    onCategory(entry.id, wide)
                                    sidebar = false
                                }
                            }
                        }
                    }
                }
            }
            if (wide || !sidebar) Column(
                Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!wide) MovioBrowseButton(stringResource(R.string.moviotv_categories), { sidebar = true; filterOpen = false },
                    background = XpPanel, foreground = XpText)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    MovioTouchSearch(
                        titleQuery, { titleQuery = it }, Modifier.weight(1f).onFocusChanged { onEditing(it.hasFocus) },
                        hint = stringResource(R.string.movioxp_filter_titles),
                        background = XpPanel, outline = XpAccent.copy(alpha = .55f), textColor = XpText, hintColor = XpMuted,
                    )
                    MovioBrowseButton(
                        stringResource(R.string.movioxp_grid_filter, stringResource(
                            if (filterOpen) R.string.moviotv_xp_filter_expanded else R.string.moviotv_xp_filter_collapsed)),
                        { filterOpen = !filterOpen }, background = XpAccent.copy(alpha = .42f), foreground = XpText,
                        modifier = Modifier.focusRequester(filterFocus),
                    )
                }
                if (filterOpen) {
                    Column(Modifier.fillMaxWidth().heightIn(max = 210.dp).clip(RoundedCornerShape(12.dp))
                        .background(XpPanel).verticalScroll(rememberScrollState()).padding(6.dp)) {
                        Text(stringResource(R.string.movioxp_sort), color = XpMuted, fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                        sortLabels.forEachIndexed { index, label ->
                            XpGenreRow(label, index == selectedSort, null, null) {
                                onSort(index)
                                filterOpen = false
                                runCatching { filterFocus.requestFocus() }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(categoryLabel, color = XpText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (failed) XpAction(stringResource(R.string.moviotv_xp_refresh), loading, onClick = onRetry)
                }
                BoxWithConstraints(
                    Modifier.fillMaxWidth().weight(1f).pointerInput(Unit) {
                        awaitEachGesture {
                            do {
                                val event = awaitPointerEvent()
                                if (event.changes.count { it.pressed } >= 2) {
                                    val next = (scaleNow * event.calculateZoom()).coerceIn(0.65f, 1.85f)
                                    if (kotlin.math.abs(next - scaleNow) > 0.003f) {
                                        zoomNow(next)
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    },
                ) {
                    if (shown.isEmpty()) Text(
                        stringResource(when {
                            loading -> R.string.moviotv_xp_loading_catalogue
                            failed -> R.string.moviotv_xp_catalogue_load_failed
                            else -> emptyLabel ?: R.string.moviotv_xp_no_titles
                        }), color = XpMuted, modifier = Modifier.align(Alignment.Center),
                    )
                    val columns = (maxWidth.value / ((if (landscape) 158f else 132f) * scale + 8f)).toInt()
                        .coerceIn(if (maxWidth >= 250.dp) 2 else 1, 12)
                    val width = (maxWidth - 8.dp * (columns - 1)) / columns
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns), state = state, modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(shown.size, key = { index -> homePosterKey(focusScope, shown[index]) }) { index ->
                            val item = shown[index]
                            Box(Modifier.onPreviewKeyEvent { event ->
                                wide && index % columns == 0 &&
                                    event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                    event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT &&
                                    runCatching { categoryFocus.requestFocus() }.getOrDefault(false)
                            }) {
                                HomeCataloguePoster(item, focusScope, width, restorePoster, onFocused,
                                    onLongClick = { onDetails(item, false) },
                                    onVeryLongClick = { onDetails(item, true) },
                                    initialRequester = gridFocus.takeIf { index == state.firstVisibleItemIndex },
                                    landscape = landscape,
                                ) { onOpen(item) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun XpSearchResultCard(hit: XpSearchHit, restorePoster: String?, onFocused: (String) -> Unit, onClick: () -> Unit) {
    val itemKey = homePosterKey("search", hit.meta)
    val requester = remember(itemKey) { FocusRequester() }
    LaunchedEffect(restorePoster) {
        if (restorePoster == itemKey) {
            withFrameNanos { }
            runCatching { requester.requestFocus() }
        }
    }
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(XpPanel)
            .border(if (focused) 3.dp else 0.dp, if (focused) XpRemotePink else Color.Transparent, RoundedCornerShape(14.dp))
            .focusRequester(requester).onFocusChanged {
                if (it.isFocused && !focused) onFocused(itemKey)
                focused = it.isFocused
            }.clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(58.dp, 82.dp).clip(RoundedCornerShape(8.dp)).background(XpBackground)) {
            hit.meta.poster?.let {
                AsyncImage(model = it, contentDescription = hit.meta.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            XpPosterBadges(hit.meta)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(hit.meta.name, color = if (focused) XpRemotePink else XpText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
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
private fun XpGenreRow(label: String, selected: Boolean, focusRequester: FocusRequester?, rightFocusRequester: FocusRequester?, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val tv = LocalXpTvControls.current
    val focusColor = if (tv) XpRemotePink else XpFocus
    Text(
        label,
        color = if (focused) focusColor else if (selected) XpText else XpMuted,
        fontSize = 15.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (selected || (focused && !tv)) (if (focused && !tv) XpFocus else XpAccent).copy(alpha = .22f) else Color.Transparent)
            .border(if (focused) 2.dp else 1.dp, if (focused) focusColor else Color.Transparent, RoundedCornerShape(10.dp))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                    event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                    rightFocusRequester?.let { runCatching { it.requestFocus() }.getOrDefault(false) } ?: false
                } else false
            }
            .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 10.dp, vertical = 10.dp),
    )
}

@Composable
private fun XpHeaderControl(
    compact: Boolean,
    label: String,
    requester: FocusRequester? = null,
    onClick: () -> Unit,
    large: Boolean = false,
    icon: @Composable (Color) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val pink = focused && LocalXpTvControls.current
    val shape = RoundedCornerShape(14.dp)
    Box(Modifier.size(if (large) { if (compact) 55.dp else 62.dp } else { if (compact) 44.dp else 50.dp }).clip(shape)
        .border(if (pink) 2.dp else 0.dp, if (pink) XpRemotePink else Color.Transparent, shape)
        .then(if (requester == null) Modifier else Modifier.focusRequester(requester))
        .semantics { contentDescription = label }
        .onFocusChanged { focused = it.isFocused }
        .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        icon(if (pink) XpRemotePink else XpText)
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.XpPosterBadges(item: XpMeta) {
    if (item.type != "movie" && item.type != "series") return
    val (showRating, showYear) = LocalXpPosterDisplay.current
    val rating = xpRating(item)
    if (showRating && rating != null) {
        Row(Modifier.align(Alignment.TopStart).padding(4.dp)
            .clip(RoundedCornerShape(5.dp)).background(Color.Black.copy(alpha = .78f)).padding(horizontal = 5.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("★ ", color = Color(0xFFFFD54F), fontSize = 11.sp)
            val value = String.format(java.util.Locale.US, "%.1f", rating)
            Text(value, color = Color.White, fontSize = 11.sp)
        }
    }
    val year = xpReleaseYear(item).orEmpty()
    if (showYear && year.isNotEmpty()) {
        Text(year, modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
            .clip(RoundedCornerShape(5.dp)).background(Color.Black.copy(alpha = .78f)).padding(horizontal = 5.dp, vertical = 2.dp),
            color = Color.White, fontSize = 11.sp)
    }
}

internal fun homePosterKey(row: String, item: XpMeta) = "$row|${item.type}:${item.id}"

internal fun xpRestoredPosterIndex(scope: String, titles: List<XpMeta>, target: String): Int =
    titles.indexOfFirst { homePosterKey(scope, it) == target }

@Composable
private fun rememberHomeCatalogueRowState(row: String, titles: List<XpMeta>, returnPoster: String?): androidx.compose.foundation.lazy.LazyListState {
    val state = rememberLazyListState()
    LaunchedEffect(row, titles, returnPoster) {
        if (returnPoster == null) return@LaunchedEffect
        val index = xpRestoredPosterIndex(row, titles, returnPoster)
        if (index < 0) return@LaunchedEffect
        withFrameNanos { }
        if (state.layoutInfo.visibleItemsInfo.none { it.index == index }) state.scrollToItem(index)
    }
    return state
}

@Composable
private fun HomeCataloguePoster(
    item: XpMeta,
    row: String,
    width: Dp,
    returnPoster: String?,
    onFocused: (String) -> Unit,
    onLongClick: () -> Unit,
    onVeryLongClick: () -> Unit,
    initialRequester: FocusRequester? = null,
    landscape: Boolean = false,
    onClick: () -> Unit,
) {
    val posterKey = homePosterKey(row, item)
    val requester = initialRequester ?: remember(posterKey) { FocusRequester() }
    var restored by remember(posterKey, returnPoster) { mutableStateOf(false) }
    LaunchedEffect(returnPoster) {
        if (!restored && returnPoster == posterKey) {
            withFrameNanos { }
            restored = runCatching { requester.requestFocus() }.getOrDefault(false)
        }
    }
    XpPoster(item, width, requester, onLongClick, onVeryLongClick,
        onFocused = { onFocused(posterKey) }, landscape = landscape, onClick = onClick)
}

@Composable
private fun XpPoster(
    item: XpMeta,
    width: Dp = 142.dp,
    focusRequester: FocusRequester? = null,
    onLongClick: () -> Unit = {},
    onVeryLongClick: () -> Unit = {},
    onFocused: (() -> Unit)? = null,
    landscape: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var centerDownAt by remember { mutableStateOf<Long?>(null) }
    Column(
        Modifier.width(width).clip(RoundedCornerShape(12.dp))
            .border(if (focused) 3.dp else 1.dp, if (focused) XpFocus else Color.Transparent, RoundedCornerShape(12.dp))
            .background(if (focused) XpFocus.copy(alpha = .22f) else Color.Transparent)
            .padding(3.dp).then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                if (it.isFocused && !focused) onFocused?.invoke()
                focused = it.isFocused
            }
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
            Modifier.fillMaxWidth().height(width * (if (landscape) 0.68f else 1.46f)).clip(RoundedCornerShape(12.dp))
                .background(if (focused) XpAccent.copy(alpha = .24f) else XpPanel),
        ) {
            item.poster?.let {
                val context = LocalContext.current
                val request = remember(context, it) {
                    coil3.request.ImageRequest.Builder(context).data(it)
                        .diskCachePolicy(coil3.request.CachePolicy.ENABLED).build()
                }
                AsyncImage(model = request, contentDescription = item.name, modifier = Modifier.fillMaxSize(), contentScale = if (landscape || item.type.isLiveCatalogueType()) ContentScale.Fit else ContentScale.Crop)
            }
            if (!landscape) XpPosterBadges(item)
            else if (item.liveNow) {
                Text(stringResource(R.string.movioxp_live_now), color = Color.White, fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.BottomStart).background(Color.Black).padding(4.dp))
            }
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
            minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (landscape && item.eventTime != null) {
            Text(item.eventTime, color = XpMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun XpAddonPriorityControls(
    index: Int,
    lastIndex: Int,
    enabled: Boolean,
    busy: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onToggle: (() -> Unit)?,
) {
    Text(
        stringResource(R.string.movioxp_addon_priority, index + 1,
            stringResource(if (enabled) R.string.movioxp_addon_enabled else R.string.movioxp_addon_disabled)),
        color = XpMuted,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        XpAction(stringResource(R.string.movioxp_addon_up), busy || index <= 0, compact = true, onClick = onUp)
        XpAction(stringResource(R.string.movioxp_addon_down), busy || index >= lastIndex, compact = true, onClick = onDown)
    }
    if (onToggle != null) {
        XpAction(stringResource(if (enabled) R.string.movioxp_addon_disable else R.string.movioxp_addon_enable),
            busy, compact = true, onClick = onToggle)
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
    controls: (@Composable () -> Unit)? = null,
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
        controls?.invoke()
        XpAction(action, disabled, onClick = onAction)
    }
}

@Composable
internal fun XpAction(label: String, disabled: Boolean, focusRequester: FocusRequester? = null,
    compact: Boolean = false, primary: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val tv = LocalXpTvControls.current
    val focusColor = if (tv) XpRemotePink else XpFocus
    Row(
        Modifier.clip(RoundedCornerShape(22.dp))
            .background(when {
                focused && !tv -> XpFocus.copy(alpha = .28f)
                primary && !disabled -> XpAccent.copy(alpha = .27f)
                else -> XpPanel
            })
            .border(if (focused) 3.dp else 1.dp, if (focused) focusColor else Color(0xFF3A3144), RoundedCornerShape(22.dp))
            .heightIn(min = if (compact) 44.dp else 56.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }.clickable(enabled = !disabled, onClick = onClick)
            .padding(horizontal = if (compact) 12.dp else 22.dp, vertical = if (compact) 8.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (disabled) XpMuted else if (focused) focusColor else XpText, fontSize = if (compact) 14.sp else 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun XpCentralField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    password: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val pink = focused && LocalXpTvControls.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = if (pink) XpRemotePink else XpText, fontSize = 16.sp),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(XpPanel).border(if (pink) 2.dp else 1.dp,
                        if (pink) XpRemotePink else XpAccent.copy(alpha = .55f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 13.dp),
            ) {
                if (value.isBlank()) Text(label, color = if (pink) XpRemotePink else XpMuted, fontSize = 16.sp)
                inner()
            }
        },
        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
    )
}
