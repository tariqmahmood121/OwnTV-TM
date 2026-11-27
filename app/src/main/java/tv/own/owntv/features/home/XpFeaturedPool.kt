package tv.own.owntv.features.home

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
                ukCertificate = optional("certificate"), titleLogo = optional("logo"), genre = optional("genre"), runtime = optional("runtime"))
        })
    }.getOrDefault(emptyList())

    fun write(source: String, items: List<XpMeta>) = synchronized(prefs) {
        val fresh = xpFeatureCandidates(items)
        if (fresh.isEmpty()) return@synchronized
        val candidates = xpFeatureCandidates(fresh + read(source))
        val encoded = JSONArray(candidates.map {
            JSONObject().put("id", it.id).put("name", it.name).put("poster", it.poster)
                .put("description", it.description).put("year", it.releaseInfo)
                .put("rating", it.rating).put("ratingSource", it.ratingSource).put("imdb", it.imdbId).put("tmdb", it.tmdbId)
                .put("backdrop", it.backdrop).put("certificate", it.ukCertificate).put("logo", it.titleLogo)
                .put("genre", it.genre).put("runtime", it.runtime)
        }).toString()
        prefs.edit().putString(key(source), encoded).apply()
    }
}

internal val xpFeaturedCategories = listOf("xp_trending", "xp_action", "xp_latest", "xp_thriller", "xp_bollywood", "xp_horror")

internal data class XpFeatureSettings(val enabled: Boolean = true, val seconds: Int = 20, val trendingOnly: Boolean = false)
internal val xpFeatureIntervals = listOf(20, 30, 60, 120)

internal fun xpFeatureReleased(item: XpMeta, today: java.time.LocalDate): Boolean {
    val exact = xpFullReleaseDate(item)
    if (exact != null) return !exact.isAfter(today)
    // A provider's past release year is sufficient for eligibility, never for full-date sorting.
    val year = xpReleaseYear(item)?.toIntOrNull() ?: return false
    return year in 1888 until today.year
}

internal fun xpFeatureRotationBuckets(
    sources: Map<String, List<XpMeta>>, trendingOnly: Boolean, cursor: Int, current: String,
    recent: List<String>, today: java.time.LocalDate,
): List<Pair<Int, List<XpMeta>>> {
    val order = if (trendingOnly) xpFeaturedCategories.take(1) else xpFeaturedCategories
    val buckets = (1..order.size).mapNotNull { step ->
        val index = (cursor + step).mod(order.size)
        val options = xpFeatureCandidates(sources[order[index]].orEmpty().filter {
            it.id != current && xpFeatureReleased(it, today)
        })
        options.takeIf { it.isNotEmpty() }?.let { index to it }
    }
    val freshBuckets = buckets.mapNotNull { (index, options) ->
        options.filterNot { it.id in recent }.takeIf { it.isNotEmpty() }?.let { index to it }
    }
    return freshBuckets.ifEmpty { buckets }
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
    settings: XpFeatureSettings,
    categories: Map<String, List<XpMeta>>,
    prepareMetadata: suspend (XpMeta) -> XpMeta,
    readCatalogueCache: suspend () -> List<XpMeta>,
): XpFeaturedState {
    val context = LocalContext.current
    val started = remember(profile, retry) { SystemClock.elapsedRealtime() }
    val pool = remember(context, profile) { XpFeaturedPool(context, profile) }
    val session = remember(context) { context.getSharedPreferences("movioxp_feature_sessions", Context.MODE_PRIVATE) }
    val profileKey = profile
    val rotationSources by rememberUpdatedState(categories)
    val remainingKey = "$profileKey|remaining|${settings.seconds}"
    val lastTitleKey = "$profileKey|title"
    val cursorKey = "$profileKey|cursor"
    val lastFeatureSource = authorizedSources.joinToString("|", prefix = "current:")
    var cached by remember(profile, authorizedSources) { mutableStateOf(emptyList<XpMeta>()) }
    var hero by remember(profile, authorizedSources) { mutableStateOf<XpMeta?>(null) }
    var waiting by remember(profile, enabled, active, retry) { mutableStateOf(true) }
    val initialCandidates = xpFeatureCandidates(cached + candidates)
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
        val stored = withContext(Dispatchers.IO) { pool.read(lastFeatureSource) + authorizedSources.flatMap(pool::read) }
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
        val last = session.getString(lastTitleKey, null)
        val priority = if (last == null) initialCandidates.shuffled()
            else initialCandidates.sortedByDescending { it.id == last }
        for (item in priority) {
            if (ready(item)) {
                hero = item
                session.edit().putString(lastTitleKey, item.id).apply()
                withContext(Dispatchers.IO) { pool.write(lastFeatureSource, listOf(item)) }
                xpDebug("Featured first ready at ${SystemClock.elapsedRealtime() - started} ms")
                break
            }
        }
    }
    var rotationCycle by remember(profile) { mutableStateOf(0) }
    LaunchedEffect(profile, authorizedSources, enabled, rotationAllowed, hero?.id, settings, rotationCycle) {
        val current = hero ?: return@LaunchedEffect
        if (!enabled || !rotationAllowed || !settings.enabled) return@LaunchedEffect
        val interval = settings.seconds * 1_000L
        val remaining = session.getLong(remainingKey, interval).coerceIn(0, interval)
        val deadline = SystemClock.elapsedRealtime() + remaining
        var completed = false
        try {
            kotlinx.coroutines.delay((remaining - 4_000).coerceAtLeast(0))
            val cursor = session.getInt(cursorKey, -1)
            val prior = session.getString("$profileKey|recent", "").orEmpty().split('|').filter(String::isNotBlank)
            val recent = (listOf(current.id) + prior).distinct().take(5)
            val options = xpFeatureRotationBuckets(rotationSources, settings.trendingOnly, cursor, current.id,
                recent, java.time.LocalDate.now())
            xpDebug("Featured rotation: ${options.size} eligible categories after cursor $cursor")
            var next: XpMeta? = null
            var nextCursor = cursor
            var attemptedCursor = cursor
            for ((index, candidates) in options) {
                val remainingPreparation = deadline - SystemClock.elapsedRealtime()
                if (remainingPreparation <= 0) break
                attemptedCursor = index
                val candidate = candidates.shuffled().take(2).firstOrNull {
                    val allowance = (deadline - SystemClock.elapsedRealtime()).coerceAtMost(1_400)
                    allowance > 0 && withTimeoutOrNull(allowance) { ready(it) } == true
                }
                if (candidate != null) {
                    val allowance = (deadline - SystemClock.elapsedRealtime()).coerceAtMost(1_000)
                    next = (if (allowance > 0) withTimeoutOrNull(allowance) { prepareMetadata(candidate) } else null)
                        ?: candidate
                    nextCursor = index
                    break
                }
            }
            delay((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0))
            completed = true
            session.edit().putLong(remainingKey, interval).apply()
            if (next != null) {
                session.edit().putInt(cursorKey, nextCursor).putString(lastTitleKey, next.id)
                    .putString("$profileKey|recent", recent.joinToString("|")).apply()
                hero = next
                withContext(Dispatchers.IO) { pool.write(lastFeatureSource, listOf(next)) }
            } else {
                session.edit().putInt(cursorKey, attemptedCursor).apply()
                rotationCycle++
            }
        } finally {
            if (!completed) session.edit().putLong(remainingKey,
                (deadline - SystemClock.elapsedRealtime()).coerceIn(0, interval)).apply()
        }
    }
    return XpFeaturedState(hero.takeIf { enabled && active }, waiting)
}
