/*
 * MovioXP catalogue browsing using the Stremio Add-on Protocol.
 * This phase loads add-on manifests and catalogue metadata; stream resolution comes later.
 */
package tv.own.owntv.features.home

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import tv.own.owntv.R
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val XpBackground = Color(0xFF0D0B14)
private val XpPanel = Color(0xFF191421)
private val XpAccent = Color(0xFFB994E8)
private val XpText = Color(0xFFF8F4FC)
private val XpMuted = Color(0xFFB8ACBF)

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
private data class XpCatalog(val type: String, val id: String, val name: String, val defaults: List<Pair<String, String>>)
internal data class XpMeta(
    val id: String, val type: String, val name: String, val poster: String?,
    val description: String?, val releaseInfo: String?, val rating: String?,
    val positionMs: Long = 0L, val durationMs: Long = 0L,
)
private data class XpCatalogRow(val addon: XpAddon, val catalog: XpCatalog)
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
                val defaults = buildList {
                    for (n in 0 until extra.length()) {
                        val field = extra.optJSONObject(n) ?: continue
                        if (!field.optBoolean("isRequired", false)) continue
                        val key = field.optString("name").trim()
                        val options = field.optJSONArray("options")
                        if (key.isNotBlank() && options != null && options.length() > 0) {
                            val value = options.optString(0).trim()
                            if (value.isNotBlank()) add(key to value)
                        }
                    }
                }
                add(XpCatalog(type, id, item.optString("name").ifBlank { id }, defaults))
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

    suspend fun loadCatalog(row: XpCatalogRow): List<XpMeta> = withContext(Dispatchers.IO) {
        val route = "catalog/${Uri.encode(row.catalog.type)}/${Uri.encode(row.catalog.id)}"
        val extra = row.catalog.defaults.joinToString("&") { (key, value) ->
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
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val addons = remember { mutableStateListOf<XpAddon>() }
    val directoryAddons = remember { mutableStateListOf<XpAddonListing>() }
    val rows = remember { mutableStateListOf<XpCatalogRow>() }
    val metas = remember { mutableStateMapOf<String, List<XpMeta>>() }
    val loadingRows = remember { mutableStateMapOf<String, Boolean>() }
    var address by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<XpMeta?>(null) }
    var selectedStreams by remember { mutableStateOf<List<XpStream>>(emptyList()) }
    var streamsLoading by remember { mutableStateOf(false) }
    var directoryLoading by remember { mutableStateOf(false) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var addonCategory by remember { mutableStateOf("community") }
    var addonFilter by remember { mutableStateOf("") }
    var page by remember { mutableStateOf("home") }
    var searchText by remember { mutableStateOf("") }
    var continueTitles by remember { mutableStateOf(XpContinueStore.read(context)) }

    fun rowKey(row: XpCatalogRow) = "${row.addon.url}|${row.catalog.type}|${row.catalog.id}"
    fun fetchRows() {
        rows.clear()
        rows.addAll(addons.flatMap { addon -> addon.catalogs.map { XpCatalogRow(addon, it) } })
        rows.forEach { row ->
            val key = rowKey(row)
            loadingRows[key] = true
            scope.launch {
                runCatching { XpAddonProtocol.loadCatalog(row) }
                    .onSuccess { metas[key] = it }
                    .onFailure { notice = context.getString(R.string.moviotv_xp_catalogue_load_failed) }
                loadingRows[key] = false
            }
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
                        XpAddonStore.write(context, addons.filterNot { it.builtIn }.map { it.url })
                        fetchRows()
                    }
                    notice = null
                }
                .onFailure { notice = context.getString(R.string.moviotv_xp_addon_install_failed) }
            busy = false
        }
    }

    fun removeAddon(addon: XpAddon) {
        if (addon.builtIn) return
        addons.remove(addon)
        XpAddonStore.write(context, addons.filterNot { it.builtIn }.map { it.url })
        fetchRows()
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
        fetchRows()
        busy = false
    }
    LaunchedEffect(selected?.id, selected?.type, addons.size) {
        val item = selected ?: return@LaunchedEffect
        selectedStreams = emptyList()
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
    BackHandler(enabled = selected != null || adding || page != "home") {
        when {
            selected != null -> selected = null
            adding -> adding = false
            else -> page = "home"
        }
    }
    BackHandler(enabled = selected == null && !adding && page == "home", onBack = onBack)

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
                    Text(stringResource(when (page) { "search" -> R.string.moviotv_xp_nav_search; "library" -> R.string.moviotv_xp_nav_library; "addons" -> R.string.moviotv_xp_nav_addons; else -> R.string.moviotv_xp_nav_discover }), color = XpAccent, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
                if (page != "addons") XpAction(stringResource(R.string.moviotv_xp_refresh), busy) { fetchRows() }
                if (page == "addons") XpAction(if (adding) stringResource(R.string.moviotv_xp_cancel) else stringResource(R.string.moviotv_xp_add_catalogue), false) { adding = !adding; notice = null }
                XpAction(stringResource(R.string.moviotv_xp_back), false, onBack)
            }
            if (page == "home" || page == "search") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 34.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp)).background(XpPanel).padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (searchText.isBlank()) Text(stringResource(R.string.moviotv_xp_search_hint), color = XpMuted, fontSize = 17.sp)
                    BasicTextField(
                        value = searchText,
                        onValueChange = { searchText = it; if (it.isNotBlank()) page = "search" },
                        singleLine = true,
                        textStyle = TextStyle(color = XpText, fontSize = 17.sp),
                        modifier = Modifier.weight(1f),
                    )
                    Text(stringResource(R.string.moviotv_xp_search_symbol), color = XpText, fontSize = 24.sp, modifier = Modifier.clickable { page = "search" })
                }
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
                }
            }
            notice?.let {
                Text(it, color = Color(0xFFFFB4AB), fontSize = 14.sp, modifier = Modifier.padding(horizontal = 34.dp, vertical = 4.dp))
            }
            if (page == "home" && continueTitles.isNotEmpty()) {
                Text(stringResource(R.string.moviotv_xp_continue_watching), color = XpText, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 24.dp, top = 2.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(continueTitles, key = { "continue:${it.type}:${it.id}" }) { title -> XpPoster(title) { selected = title } }
                }
            }
            if (page == "addons") {
                Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.moviotv_xp_addon_category), color = XpMuted, fontSize = 13.sp)
                    Box {
                        val categoryLabel = when (addonCategory) {
                            "official" -> stringResource(R.string.moviotv_xp_addon_official)
                            "installed" -> stringResource(R.string.moviotv_xp_addon_installed)
                            else -> stringResource(R.string.moviotv_xp_addon_community)
                        }
                        XpAction("$categoryLabel  ▾", false) { categoryMenuExpanded = true }
                        DropdownMenu(
                            expanded = categoryMenuExpanded,
                            onDismissRequest = { categoryMenuExpanded = false },
                        ) {
                            listOf(
                                "official" to R.string.moviotv_xp_addon_official,
                                "community" to R.string.moviotv_xp_addon_community,
                                "installed" to R.string.moviotv_xp_addon_installed,
                            ).forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(label)) },
                                    onClick = {
                                        addonCategory = key
                                        categoryMenuExpanded = false
                                        notice = null
                                    },
                                )
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
            } else if (page == "library") {
                if (continueTitles.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.moviotv_xp_library_empty), color = XpMuted, fontSize = 16.sp)
                    }
                } else {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)) {
                        item { Text(stringResource(R.string.moviotv_xp_continue_watching), color = XpText, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                        items(continueTitles, key = { "library:${it.type}:${it.id}" }) { title ->
                            XpPoster(title) { selected = title }
                        }
                    }
                }
            } else if (page == "search" && searchText.isNotBlank() && rows.none { row -> metas[rowKey(row)].orEmpty().any { it.name.contains(searchText, true) } }) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.moviotv_xp_no_search_matches), color = XpMuted, fontSize = 16.sp)
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
                    items(if (page == "search") rows.filter { row -> metas[rowKey(row)].orEmpty().any { it.name.contains(searchText, true) } } else rows, key = { rowKey(it) }) { row ->
                        val key = rowKey(row)
                        val titles = metas[key].orEmpty().let { list -> if (page == "search") list.filter { it.name.contains(searchText, true) } else list }
                        if (page != "search" || titles.isNotEmpty())
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
                                    items(titles, key = { "${it.type}:${it.id}" }) { item -> XpPoster(item) { selected = item } }
                                }
                            }
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().background(Color(0xFF100D18)).padding(horizontal = 18.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf("home" to R.string.moviotv_nav_home, "search" to R.string.moviotv_xp_nav_search, "library" to R.string.moviotv_xp_nav_library, "addons" to R.string.moviotv_xp_nav_addons).forEach { (key, labelRes) ->
                    Text(stringResource(labelRes), color = if (page == key) XpAccent else XpMuted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable {
                            page = key
                            if (key == "addons") adding = false
                        }.padding(horizontal = 18.dp, vertical = 10.dp))
                }
            }
        }
        selected?.let { item ->
            Column(
                Modifier.fillMaxSize().background(XpBackground.copy(alpha = .98f)).padding(38.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(item.name, color = XpText, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text(
                    listOfNotNull(item.type.replaceFirstChar { it.uppercase() }, item.releaseInfo, item.rating?.let { context.getString(R.string.moviotv_xp_rating, it) })
                        .joinToString(stringResource(R.string.moviotv_xp_meta_separator)),
                    color = XpAccent, fontSize = 17.sp, modifier = Modifier.padding(top = 8.dp),
                )
                item.description?.let { Text(it, color = XpMuted, fontSize = 17.sp, modifier = Modifier.padding(top = 16.dp)) }
                Spacer(Modifier.height(22.dp))
                Text(stringResource(R.string.moviotv_xp_stream_sources), color = XpText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                when {
                    streamsLoading -> Text(stringResource(R.string.moviotv_xp_loading_streams), color = XpMuted, fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp))
                    selectedStreams.isEmpty() -> Text(stringResource(R.string.moviotv_xp_no_streams), color = XpMuted, fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp))
                    else -> androidx.compose.foundation.lazy.LazyColumn(
                        Modifier.fillMaxWidth().heightIn(max = 250.dp).padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(selectedStreams) { stream ->
                            val resultLabel = stringResource(R.string.moviotv_xp_stream_result, stream.label, stream.addon.name)
                            val action = when {
                                stream.url == null -> stringResource(R.string.moviotv_xp_stream_torrent_unsupported, resultLabel)
                                stream.detail != null -> stringResource(R.string.moviotv_xp_stream_result_detail, resultLabel, stream.detail)
                                else -> resultLabel
                            }
                            XpAction(action, disabled = stream.url == null) {
                                stream.url?.let { playable ->
                                    XpContinueStore.add(context, item)
                                    continueTitles = XpContinueStore.read(context)
                                    onPlayStream(playable, item.name, item.type, item.releaseInfo, item.id, item.poster, item.positionMs)
                                }
                            }
                        }
                    }
                }
                XpAction(stringResource(R.string.moviotv_xp_back_to_catalogues), false) { selected = null }
            }
        }
    }
}

@Composable
private fun XpPoster(item: XpMeta, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier.width(142.dp).onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick).focusable(),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            Modifier.size(142.dp, 208.dp).clip(RoundedCornerShape(12.dp))
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
private fun XpAction(label: String, disabled: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.clip(RoundedCornerShape(22.dp))
            .background(if (focused) XpAccent.copy(alpha = .26f) else XpPanel)
            .clickable(enabled = !disabled, onClick = onClick)
            .onFocusChanged { focused = it.isFocused }.focusable(enabled = !disabled)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (disabled) XpMuted else XpText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
