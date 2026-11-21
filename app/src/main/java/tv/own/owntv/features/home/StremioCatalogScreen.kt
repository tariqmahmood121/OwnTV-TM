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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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

private data class XpAddon(val url: String, val id: String, val name: String, val catalogs: List<XpCatalog>)
private data class XpCatalog(val type: String, val id: String, val name: String, val defaults: List<Pair<String, String>>)
private data class XpMeta(
    val id: String, val type: String, val name: String, val poster: String?,
    val description: String?, val releaseInfo: String?, val rating: String?,
)
private data class XpCatalogRow(val addon: XpAddon, val catalog: XpCatalog)

private object XpAddonProtocol {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS).build()

    suspend fun loadManifest(raw: String): XpAddon = withContext(Dispatchers.IO) {
        val url = manifestUrl(raw)
        val json = get(url)
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
        require(catalogs.isNotEmpty()) { "This add-on manifest has no catalogues." }
        XpAddon(
            url,
            json.optString("id").ifBlank { url },
            json.optString("name").ifBlank { URI(url).host ?: url },
            catalogs,
        )
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

@Composable
fun StremioCatalogScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val addons = remember { mutableStateListOf<XpAddon>() }
    val rows = remember { mutableStateListOf<XpCatalogRow>() }
    val metas = remember { mutableStateMapOf<String, List<XpMeta>>() }
    val loadingRows = remember { mutableStateMapOf<String, Boolean>() }
    var address by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<XpMeta?>(null) }

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

    LaunchedEffect(Unit) {
        busy = true
        XpAddonStore.read(context).forEach { saved ->
            runCatching { XpAddonProtocol.loadManifest(saved) }
                .onSuccess { addons.add(it) }
                .onFailure { notice = context.getString(R.string.moviotv_xp_addon_refresh_failed) }
        }
        fetchRows()
        busy = false
    }
    BackHandler(enabled = selected != null || adding) {
        if (selected != null) selected = null else adding = false
    }
    BackHandler(enabled = selected == null && !adding, onBack = onBack)

    Box(modifier.fillMaxSize().background(XpBackground)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 34.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.moviotv_xp_title), color = XpText, fontSize = 28.sp, fontWeight = FontWeight.Black)
                    Text(stringResource(R.string.moviotv_xp_catalogues), color = XpAccent, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
                XpAction(stringResource(R.string.moviotv_xp_refresh), busy) { fetchRows() }
                XpAction(if (adding) stringResource(R.string.moviotv_xp_cancel) else stringResource(R.string.moviotv_xp_add_catalogue), false) { adding = !adding; notice = null }
                XpAction(stringResource(R.string.moviotv_xp_back), false, onBack)
            }
            if (adding) {
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
                            busy = true
                            scope.launch {
                                runCatching { XpAddonProtocol.loadManifest(candidate) }
                                    .onSuccess { addon ->
                                        if (addons.any { it.url == addon.url }) notice = context.getString(R.string.moviotv_xp_addon_already_installed)
                                        else {
                                            addons.add(addon)
                                            XpAddonStore.write(context, addons.map { it.url })
                                            address = ""
                                            adding = false
                                            notice = null
                                            fetchRows()
                                        }
                                    }
                                    .onFailure { notice = context.getString(R.string.moviotv_xp_addon_install_failed) }
                                busy = false
                            }
                        }
                    }
                }
            }
            notice?.let {
                Text(it, color = Color(0xFFFFB4AB), fontSize = 14.sp, modifier = Modifier.padding(horizontal = 34.dp, vertical = 4.dp))
            }
            if (addons.isNotEmpty()) {
                Text(stringResource(R.string.moviotv_xp_installed_addons), color = XpMuted, fontSize = 13.sp,
                    modifier = Modifier.padding(start = 34.dp, top = 4.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 34.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(addons, key = { it.url }) { addon ->
                        Row(
                            Modifier.clip(RoundedCornerShape(18.dp))
                                .background(XpPanel)
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(addon.name, color = XpText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            var removeFocused by remember { mutableStateOf(false) }
                            Text(
                                "  ${stringResource(R.string.moviotv_xp_remove_symbol)}",
                                color = if (removeFocused) XpText else XpAccent,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .onFocusChanged { removeFocused = it.isFocused }
                                    .clickable {
                                        addons.remove(addon)
                                        XpAddonStore.write(context, addons.map { it.url })
                                        fetchRows()
                                    }
                                    .focusable(),
                            )
                        }
                    }
                }
            }
            if (rows.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.moviotv_xp_empty_title), color = XpText, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.moviotv_xp_empty_description), color = XpMuted, fontSize = 16.sp)
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 36.dp),
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
                                    items(titles, key = { it.id }) { item -> XpPoster(item) { selected = item } }
                                }
                            }
                        }
                    }
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
                XpAction(stringResource(R.string.moviotv_xp_back_to_catalogues), false) { selected = null }
                Text(stringResource(R.string.moviotv_xp_streams_next_phase), color = XpMuted, fontSize = 14.sp,
                    modifier = Modifier.padding(top = 12.dp))
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
        }
        Text(item.name, color = XpText, fontSize = 14.sp, fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
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
