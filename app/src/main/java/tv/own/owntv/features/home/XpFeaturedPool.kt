package tv.own.owntv.features.home

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.request.SuccessResult
import coil3.request.ImageRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

internal fun xpFeatureCandidates(items: List<XpMeta>): List<XpMeta> = items.asSequence()
    .filter { it.type == "movie" && it.name.isNotBlank() && xpCinemaLandscape(it.backdrop, it.poster) != null }
    .distinctBy { it.id }
    .take(8).toList()

internal fun xpHeroRequest(context: Context, backdrop: String): ImageRequest =
    ImageRequest.Builder(context).data(backdrop).size(1280, 720).build()

internal class XpFeaturedPool(context: Context, profile: String) {
    private val prefs = context.getSharedPreferences("movioxp_featured", Context.MODE_PRIVATE)
    private val scope = profile

    private fun key(source: String) = MessageDigest.getInstance("SHA-256")
        .digest((scope + "\u0000" + source).toByteArray()).joinToString("") { "%02x".format(it) }

    fun read(source: String): List<XpMeta> = runCatching {
        val entries = JSONArray(prefs.getString(key(source), null) ?: "[]")
        xpFeatureCandidates((0 until entries.length()).map { index ->
            val item = entries.getJSONObject(index)
            fun optional(key: String) = item.optString(key).takeIf { it.isNotBlank() && !item.isNull(key) }
            XpMeta(item.getString("id"), "movie", item.getString("name"), optional("poster"),
                optional("description"), optional("year"), optional("rating"), tmdbId = optional("tmdb"),
                imdbId = optional("imdb"), ratingSource = optional("ratingSource"), backdrop = optional("backdrop"),
                ukCertificate = optional("certificate"), titleLogo = optional("logo"), genre = optional("genre"))
        })
    }.getOrDefault(emptyList())

    fun write(source: String, items: List<XpMeta>) = synchronized(prefs) {
        val fresh = xpFeatureCandidates(items)
        if (fresh.isEmpty()) return@synchronized
        val candidates = xpFeatureCandidates(fresh + read(source))
        val encoded = JSONArray(candidates.map {
            JSONObject().put("id", it.id).put("name", it.name).put("poster", it.poster)
                .put("description", it.description).put("year", xpReleaseYear(it))
                .put("rating", it.rating).put("ratingSource", it.ratingSource).put("imdb", it.imdbId).put("tmdb", it.tmdbId)
                .put("backdrop", it.backdrop).put("certificate", it.ukCertificate).put("logo", it.titleLogo)
                .put("genre", it.genre)
        }).toString()
        prefs.edit().putString(key(source), encoded).apply()
    }
}

internal data class XpFeaturedState(val hero: XpMeta?, val waiting: Boolean)

@Composable
internal fun rememberXpFeaturedMovie(
    enabled: Boolean,
    profile: String,
    authorizedSources: List<String>,
    candidates: List<XpMeta>,
    rotationAllowed: Boolean,
    active: Boolean,
    retry: Long,
    catalogueReady: Boolean,
    readCatalogueCache: suspend () -> List<XpMeta>,
): XpFeaturedState {
    val context = LocalContext.current
    val started = remember(profile, retry) { SystemClock.elapsedRealtime() }
    val pool = remember(context, profile) { XpFeaturedPool(context, profile) }
    var cached by remember(profile, authorizedSources) { mutableStateOf(emptyList<XpMeta>()) }
    var hero by remember(profile, authorizedSources) { mutableStateOf<XpMeta?>(null) }
    var waiting by remember(profile, enabled, active, retry) { mutableStateOf(true) }
    val initialCandidates = xpFeatureCandidates(cached + candidates)
    val rotationCandidates = xpFeatureCandidates(candidates + cached)
    suspend fun ready(item: XpMeta): Boolean {
        val backdrop = item.backdrop ?: return false
        val since = SystemClock.elapsedRealtime()
        val result = try {
            withTimeoutOrNull(3_000) {
                SingletonImageLoader.get(context).execute(xpHeroRequest(context, backdrop))
            } as? SuccessResult
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        val landscape = result != null && result.image.width > result.image.height
        xpDebug("Featured artwork ready=$landscape in ${SystemClock.elapsedRealtime() - since} ms")
        return landscape
    }
    LaunchedEffect(enabled, active, authorizedSources, catalogueReady, retry) {
        if (!enabled || !active) return@LaunchedEffect
        val stored = withContext(Dispatchers.IO) { authorizedSources.flatMap(pool::read) }
        cached = xpFeatureCandidates(stored)
        xpDebug("Featured pool read ${cached.size} entries at ${SystemClock.elapsedRealtime() - started} ms")
        if (cached.isEmpty()) cached = xpFeatureCandidates(readCatalogueCache())
    }
    LaunchedEffect(enabled, active, retry) {
        if (enabled && active) {
            delay(6_000)
            waiting = false
        }
    }
    LaunchedEffect(enabled, active, initialCandidates, retry) {
        if (!enabled || !active || hero != null) return@LaunchedEffect
        for (item in initialCandidates) {
            if (ready(item)) {
                hero = item
                xpDebug("Featured first ready at ${SystemClock.elapsedRealtime() - started} ms")
                break
            }
        }
    }
    LaunchedEffect(enabled, rotationAllowed, hero?.id, rotationCandidates) {
        val current = hero ?: return@LaunchedEffect
        if (!enabled || !rotationAllowed) return@LaunchedEffect
        val options = rotationCandidates.filterNot { it.id == current.id }.shuffled()
        if (options.isEmpty()) return@LaunchedEffect
        while (true) {
            val cycle = SystemClock.elapsedRealtime()
            delay(52_000)
            val next = options.take(2).firstOrNull { ready(it) }
            delay((60_000 - (SystemClock.elapsedRealtime() - cycle)).coerceAtLeast(0))
            if (next != null) {
                hero = next
                break
            }
        }
    }
    return XpFeaturedState(hero.takeIf { enabled && active }, waiting)
}
