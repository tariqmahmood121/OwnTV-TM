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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.focusGroup
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import tv.own.owntv.R
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
internal data class XpPlaybackSource(val url: String, val label: String, val provider: String)
private data class XpCatalogRow(val addon: XpAddon, val catalog: XpCatalog)
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
                        releaseInfo = item.optString("releaseInfo").takeIf { it.isNotBlank() },
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
    private const val VALUE = "encrypted_urls"
    private const val ALIAS = "movioxp_addons_aes_v1"

    fun read(context: Context): List<String> = runCatching {
        val encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(VALUE, null)
            ?: return emptyList()
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        require(payload.size > 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, payload.copyOfRange(0, 12)))
        val values = JSONArray(String(cipher.doFinal(payload.copyOfRange(12, payload.size)), Charsets.UTF_8))
        buildList { for (i in 0 until values.length()) values.optString(i).takeIf { it.isNotBlank() }?.let(::add) }
    }.getOrDefault(emptyList())

    fun write(context: Context, urls: List<String>) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val plain = JSONArray(urls.distinct()).toString().toByteArray(Charsets.UTF_8)
        val payload = cipher.iv + cipher.doFinal(plain)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(VALUE, Base64.encodeToString(payload, Base64.NO_WRAP)).apply()
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
        write(context, profileId, if (exists) current.filterNot { it.type == item.type && it.id == item.id }
            else listOf(item.copy(positionMs = 0L, durationMs = 0L)) + current)
        return !exists
    }
}

/** Lightweight recent-title shelf for Stremio catalogue items opened from MovioXP. */
internal object XpContinueStore {
    private const val PREFS = "movioxp_catalogues"
    private const val VALUE = "continue_titles"

    fun read(context: Context): List<XpMeta> = runCatching {
        val source = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(VALUE, "[]"))
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

    fun add(context: Context, item: XpMeta) {
        save(context, item.copy(positionMs = 0L, durationMs = 0L))
    }

    fun updateProgress(
        context: Context, id: String, type: String, name: String, poster: String?, year: String?,
        positionMs: Long, durationMs: Long,
    ) {
        if (durationMs <= 0L) return
        if (positionMs >= durationMs * .95f) {
            val remaining = read(context).filterNot { it.id == id && it.type == type }
            write(context, remaining)
        } else if (positionMs > 0L) {
            save(context, XpMeta(id, type, name, poster, null, year, null, positionMs, durationMs))
        }
    }

    private fun save(context: Context, item: XpMeta) {
        val updated = (listOf(item) + read(context).filterNot { it.id == item.id && it.type == item.type }).take(20)
        write(context, updated)
    }

    private fun write(context: Context, updated: List<XpMeta>) {
        val json = JSONArray().apply {
            updated.forEach { meta ->
                put(JSONObject().put("id", meta.id).put("type", meta.type).put("name", meta.name)
                    .put("poster", meta.poster ?: "").put("year", meta.releaseInfo ?: "")
                    .put("positionMs", meta.positionMs).put("durationMs", meta.durationMs))
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(VALUE, json.toString()).apply()
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
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profiles: ProfilesViewModel = koinViewModel()
    val activeProfileId by profiles.activeProfileId.collectAsStateWithLifecycle()
    val profileList by profiles.profiles.collectAsStateWithLifecycle()
    val activeProfileName = profileList.firstOrNull { it.id == activeProfileId }?.name.orEmpty()
    val favoritesProfileId = activeProfileId ?: -1L
    val addons = remember { mutableStateListOf<XpAddon>() }
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
    var returnPage by remember(initialPage) { mutableStateOf(initialPage) }
    var searchText by remember { mutableStateOf("") }
    var discoveryType by remember { mutableStateOf("all") }
    var discoveryCatalog by remember { mutableStateOf("all") }
    var discoveryGenre by remember { mutableStateOf("all") }
    var genrePickerOpen by remember { mutableStateOf(false) }
    var reverseTitleSort by remember { mutableStateOf(false) }
    var expandedFilter by remember { mutableStateOf<String?>(null) }
    var searchHits by remember { mutableStateOf<List<XpSearchHit>>(emptyList()) }
    var searchLoading by remember { mutableStateOf(false) }
    var searchableCatalogCount by remember { mutableStateOf(0) }
    var searchFailureCount by remember { mutableStateOf(0) }
    var continueTitles by remember { mutableStateOf(XpContinueStore.read(context)) }
    var favoriteTitles by remember(favoritesProfileId) { mutableStateOf(XpFavoritesStore.read(context, favoritesProfileId)) }
    val playbackPrefs = remember(context) { context.getSharedPreferences("movioxp_playback", Context.MODE_PRIVATE) }
    var autoSelectSource by remember { mutableStateOf(playbackPrefs.getBoolean("auto_select_source", true)) }
    var preferredAddonUrl by remember { mutableStateOf(playbackPrefs.getString("preferred_addon", "").orEmpty()) }
    var autoStartedItem by remember { mutableStateOf<String?>(null) }
    val firstSourceFocus = remember { FocusRequester() }
    val detailFocus = remember { FocusRequester() }
    val firstGridFocus = remember { FocusRequester() }

    DisposableEffect(context, favoritesProfileId) {
        val preferences = context.getSharedPreferences("movioxp_catalogues", Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "favorites_profile_$favoritesProfileId") {
                favoriteTitles = XpFavoritesStore.read(context, favoritesProfileId)
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun catalogKey(row: XpCatalogRow) = "${row.addon.url}|${row.catalog.type}|${row.catalog.id}"
    fun rowKey(row: XpCatalogRow) = "${catalogKey(row)}|${if (page == "discover" || page in browsePages) discoveryGenre else "all"}"
    fun fetchRows() {
        rows.clear()
        val catalogRows = addons.flatMap { addon -> addon.catalogs.map { XpCatalogRow(addon, it) } }
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
        .let { if (reverseTitleSort) it.sortedByDescending { meta -> meta.name.lowercase() } else it.sortedBy { meta -> meta.name.lowercase() } }
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

    fun openItem(item: XpMeta) {
        manualSourceSelection = false
        showManualSources = true
        selected = item
    }

    fun openDetails(item: XpMeta, showSources: Boolean) {
        manualSourceSelection = true
        showManualSources = showSources
        selected = item
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
                        XpAddonStore.write(context, addons.filterNot { it.builtIn }.map { it.url })
                    }
                    notice = null
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
        XpAddonStore.write(context, addons.filterNot { it.builtIn }.map { it.url })
    }

    LaunchedEffect(Unit) {
        busy = true
        runCatching {
            XpAddonProtocol.loadManifest(context.getString(R.string.moviotv_xp_default_catalogue_manifest))
                .copy(builtIn = true)
        }
            .onSuccess { builtIn -> addons.add(builtIn) }
            .onFailure { notice = context.getString(R.string.moviotv_xp_default_catalogue_load_failed) }
        XpAddonStore.read(context).forEach { saved ->
            runCatching { XpAddonProtocol.loadManifest(saved) }
                .onSuccess { addon -> if (addons.none { it.url == addon.url }) addons.add(addon) }
                .onFailure { notice = context.getString(R.string.moviotv_xp_addon_refresh_failed) }
        }
        busy = false
    }
    LaunchedEffect(page, discoveryType, discoveryCatalog, discoveryGenre, addons.size) {
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
        val preferenceKey = "last_source_${favoritesProfileId}_${("stremio:${item.type}:${item.id}").hashCode()}"
        val lastProvider = playbackPrefs.getString(preferenceKey, null)
        val candidates = selectedStreams.filter { it.url != null }
            .sortedWith(
                compareBy<XpStream> { if (it.addon.name == lastProvider) 0 else 1 }
                    .thenBy { if (it.addon.url == preferredAddonUrl) 0 else 1 },
            )
        if (candidates.isEmpty()) return@LaunchedEffect
        autoStartedItem = itemKey
        XpContinueStore.add(context, item)
        continueTitles = XpContinueStore.read(context)
        val sources = candidates.mapNotNull { stream -> stream.url?.let { XpPlaybackSource(it, stream.label, stream.addon.name) } }
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

    Box(modifier.fillMaxSize().background(XpBackground)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 34.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.moviotv_xp_title), color = XpText, fontSize = 28.sp, fontWeight = FontWeight.Black)
                    if (activeProfileName.isNotBlank()) Text(activeProfileName, color = XpMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(stringResource(when (page) { "movies" -> R.string.moviotv_nav_movies; "series" -> R.string.moviotv_nav_series; "live" -> R.string.moviotv_nav_live_tv; "search" -> R.string.moviotv_xp_nav_search; "library" -> R.string.moviotv_xp_nav_library; "addons" -> R.string.moviotv_xp_nav_addons; "settings" -> R.string.moviotv_xp_nav_settings; "home" -> R.string.moviotv_nav_home; else -> R.string.moviotv_xp_nav_discover }), color = XpAccent, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
                if (page != "settings") XpAction(stringResource(R.string.moviotv_xp_nav_settings), false) { page = "settings"; adding = false }
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
                if (page == "addons") XpAction(if (adding) stringResource(R.string.moviotv_xp_cancel) else stringResource(R.string.moviotv_xp_add_catalogue), false) { adding = !adding; notice = null }
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
            if (page == "home" || page == "discover" || page == "search") {
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
                            onSelect = { discoveryGenre = it; genrePickerOpen = false },
                        )
                    },
                    onSort = { reverseTitleSort = !reverseTitleSort },
                    itemCount = sectionTitles.size,
                    gridState = gridState,
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
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 34.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(12.dp))
                            .background(XpPanel).padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (address.isBlank()) Text(stringResource(R.string.moviotv_xp_addon_url_hint), color = XpMuted)
                        BasicTextField(
                            value = address,
                            onValueChange = { address = it },
                            singleLine = true,
                            textStyle = TextStyle(color = XpText, fontSize = 16.sp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    XpAction(stringResource(R.string.moviotv_xp_install), busy) {
                        val candidate = address.trim()
                        if (candidate.isBlank()) notice = context.getString(R.string.moviotv_xp_enter_addon_url)
                        else {
                            installAddon(candidate)
                            address = ""
                            adding = false
                        }
                    }
                    XpAction(stringResource(R.string.moviotv_xp_remote_enter_on_phone), busy) {
                        showAddonRemote = true
                        addonRemoteServer.start()
                    }
                }
            }
            notice?.let {
                Text(it, color = Color(0xFFFFB4AB), fontSize = 14.sp, modifier = Modifier.padding(horizontal = 34.dp, vertical = 4.dp))
            }
            if (page == "home" && continueTitles.isNotEmpty()) {
                Text(stringResource(R.string.moviotv_xp_continue_watching), color = XpText, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 24.dp, top = 2.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(continueTitles, key = { "continue:${it.type}:${it.id}" }) { title ->
                        XpPoster(title, onLongClick = { openDetails(title, false) }, onVeryLongClick = { openDetails(title, true) }) { openItem(title) }
                    }
                }
            }
            if (page in browsePages) {
                // The add-on catalogue rows are rendered as the MovioXP poster grid above.
            } else if (page == "addons") {
                Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.moviotv_xp_addon_category), color = XpMuted, fontSize = 13.sp)
                    Column {
                        val categoryLabel = when (addonCategory) {
                            "official" -> stringResource(R.string.moviotv_xp_addon_official)
                            "installed" -> stringResource(R.string.moviotv_xp_addon_installed)
                            else -> stringResource(R.string.moviotv_xp_addon_community)
                        }
                        XpAction("$categoryLabel  ▾", false) { categoryMenuExpanded = true }
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
            } else if (page == "settings") {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = 34.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.moviotv_xp_auto_select_source), color = XpText, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    XpAction(
                        label = stringResource(if (autoSelectSource) R.string.moviotv_xp_source_auto_enabled else R.string.moviotv_xp_source_auto_disabled),
                        disabled = false,
                    ) {
                        autoSelectSource = !autoSelectSource
                        playbackPrefs.edit().putBoolean("auto_select_source", autoSelectSource).apply()
                    }
                    Text(stringResource(R.string.moviotv_xp_preferred_addon), color = XpText, fontSize = 21.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp))
                    XpAction(
                        label = stringResource(R.string.moviotv_xp_any_addon),
                        disabled = false,
                    ) {
                        preferredAddonUrl = ""
                        playbackPrefs.edit().putString("preferred_addon", "").apply()
                    }
                    addons.forEach { addon ->
                        XpAction(
                            label = if (preferredAddonUrl == addon.url) stringResource(R.string.moviotv_xp_preferred_addon_option, addon.name) else addon.name,
                            disabled = false,
                        ) {
                            preferredAddonUrl = addon.url
                            playbackPrefs.edit().putString("preferred_addon", addon.url).apply()
                        }
                    }
                }
            } else if (page == "library") {
                if (continueTitles.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.moviotv_xp_library_empty), color = XpMuted, fontSize = 16.sp)
                    }
                } else {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)) {
                        item { Text(stringResource(R.string.moviotv_xp_continue_watching), color = XpText, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                        items(continueTitles, key = { "library:${it.type}:${it.id}" }) { title ->
                            XpPoster(title, onLongClick = { openDetails(title, false) }, onVeryLongClick = { openDetails(title, true) }) { openItem(title) }
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
                                    stringResource(R.string.moviotv_xp_search_partial, searchFailureCount),
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
            } else if (rows.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.moviotv_xp_empty_title), color = XpText, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.moviotv_xp_empty_description), color = XpMuted, fontSize = 16.sp)
                    }
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(rows, key = { rowKey(it) }) { row ->
                        val key = rowKey(row)
                        val titles = metas[key].orEmpty()
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
                                        XpPoster(item, onLongClick = { openDetails(item, false) }, onVeryLongClick = { openDetails(item, true) }) { openItem(item) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (showBottomNavigation) Row(
                Modifier.fillMaxWidth().background(XpBackground).padding(horizontal = 18.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                    listOf("home" to R.string.moviotv_nav_home, "discover" to R.string.moviotv_xp_nav_discover, "movies" to R.string.moviotv_nav_movies, "series" to R.string.moviotv_nav_series, "favorites" to R.string.moviotv_xp_more_favorites, "search" to R.string.moviotv_xp_nav_search, "library" to R.string.moviotv_xp_nav_library, "addons" to R.string.moviotv_xp_nav_addons).forEach { (key, labelRes) ->
                    XpNavigationItem(stringResource(labelRes), selected = page == key) {
                        page = key
                        if (key == "addons") adding = false
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
                                    XpContinueStore.add(context, item)
                                    continueTitles = XpContinueStore.read(context)
                                    selected = null
                                    manualSourceSelection = false
                                    showManualSources = false
                                    onPlayStream(playable, item.name, item.type, item.releaseInfo, item.id, item.poster, item.positionMs)
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
private fun RowScope.XpNavigationItem(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier.weight(1f).padding(horizontal = 2.dp).clip(RoundedCornerShape(12.dp))
            .background(if (focused || selected) XpAccent.copy(alpha = if (focused) .32f else .20f) else Color.Transparent)
            .border(if (focused) 2.dp else 1.dp, if (focused) XpAccent else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).onFocusChanged { focused = it.isFocused }.focusable()
            .padding(horizontal = 5.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, color = if (selected || focused) XpText else XpMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
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
    onSelect: (String) -> Unit,
) {
    Column(modifier.background(XpPanel).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.moviotv_categories), color = XpMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item(key = "all") { XpGenreRow(allLabel, selected == "all") { onSelect("all") } }
            items(genres.distinct().sorted(), key = { "genre:$it" }) { genre ->
                XpGenreRow(genre, selected.equals(genre, true)) { onSelect(genre) }
            }
        }
    }
}

@Composable
private fun XpGenreRow(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        label,
        color = if (selected || focused) XpText else XpMuted,
        fontSize = 15.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (selected || focused) XpAccent.copy(alpha = .22f) else Color.Transparent)
            .clickable(onClick = onClick).onFocusChanged { focused = it.isFocused }.focusable()
            .padding(horizontal = 10.dp, vertical = 10.dp),
    )
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
            .border(if (focused) 3.dp else 1.dp, if (focused) XpAccent else Color.Transparent, RoundedCornerShape(12.dp))
            .background(if (focused) XpAccent.copy(alpha = .18f) else Color.Transparent)
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
        Text(item.name, color = XpText, fontSize = 14.sp, fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
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
        XpAction(action, disabled, onAction)
    }
}

@Composable
private fun XpAction(label: String, disabled: Boolean, onClick: () -> Unit, focusRequester: FocusRequester? = null) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.clip(RoundedCornerShape(22.dp))
            .background(if (focused) XpAccent.copy(alpha = .26f) else XpPanel)
            .border(if (focused) 2.dp else 1.dp, if (focused) XpAccent else Color(0xFF3A3144), RoundedCornerShape(22.dp))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable(enabled = !disabled, onClick = onClick)
            .onFocusChanged { focused = it.isFocused }.focusable(enabled = !disabled)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (disabled) XpMuted else XpText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
