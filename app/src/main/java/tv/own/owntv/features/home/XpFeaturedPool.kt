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

private val currentFeatureByProfile = object : LinkedHashMap<String, String>(16, .75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 16
}

private const val EXPOSURE_WINDOW = 30L * 24 * 60 * 60 * 1000

internal fun xpFeatureAliases(item: XpMeta): Set<String> = XpPlaybackIdentity.ids(item).toSet()

internal class XpFeatureExposure(context: Context, scope: String) {
    private val prefs = context.getSharedPreferences("movioxp_feature_exposure", Context.MODE_PRIVATE)
    private val key = MessageDigest.getInstance("SHA-256").digest(scope.toByteArray())
        .joinToString("") { "%02x".format(it) }

    fun recent(now: Long): Map<String, Long> {
        val json = runCatching { JSONObject(prefs.getString(key, null) ?: "{}") }.getOrDefault(JSONObject())
        return json.keys().asSequence().map { it to json.optLong(it) }
            .filter { (_, timestamp) -> timestamp > 0 && now - timestamp < EXPOSURE_WINDOW }.toMap()
    }

    fun displayed(item: XpMeta, now: Long) {
        val retained = recent(now).toMutableMap()
        xpFeatureAliases(item).forEach { retained[it] = now }
        prefs.edit().putString(key, JSONObject(retained).toString()).apply()
    }
}

internal fun xpRankFeatureQueue(
    categories: Map<String, List<XpMeta>>,
    trendingOnly: Boolean,
    watched: List<XpMeta>,
    signals: List<XpMeta>,
    exposed: Set<String>,
    today: java.time.LocalDate,
): List<XpMeta> {
    val seen = exposed.toMutableSet()
    val finished = watched.filter { it.type == "movie" && (it.durationMs <= 0 ||
        it.positionMs == 0L || it.positionMs.toDouble() / it.durationMs >= .95) }
        .flatMapTo(mutableSetOf<String>(), ::xpFeatureAliases)
    val affinities = signals.take(15).mapIndexedNotNull { index, item -> item.genre?.lowercase(java.util.Locale.ROOT)?.let { it to (15 - index) } }
        .groupingBy { it.first }.fold(0) { total, match -> total + match.second }
    val options = categories.asSequence().filter { (id, _) ->
        id !in setOf("xp_cinema", "xp_upcoming") && (!trendingOnly || id == "xp_trending")
    }.flatMap { (id, titles) ->
        xpFeatureCandidates(titles.filter { xpFeatureReleased(it, today) }).asSequence().map { movie ->
            val relevance = affinities[movie.genre?.lowercase(java.util.Locale.ROOT)] ?: 0
            val recent = xpFullReleaseDate(movie)?.isAfter(today.minusMonths(4)) == true
            val weight = 15 + (if (id == "xp_trending") 35 else 0) +
                (if (affinities.isNotEmpty()) 30 * relevance / (affinities.values.maxOrNull() ?: 1) else 0) +
                (if (recent) 20 else 0)
            movie to (-kotlin.math.ln(kotlin.random.Random.nextDouble().coerceAtLeast(1e-9)) / weight)
        }
    }.sortedBy { it.second }
    return options.map { it.first }.filter { item ->
        val aliases = xpFeatureAliases(item)
        if (aliases.any { it in seen || it in finished }) false
        else { seen.addAll(aliases); true }
    }.take(10).toList()
}

internal val LocalXpFeaturedSourceCheck = androidx.compose.runtime.staticCompositionLocalOf<(suspend (XpMeta) -> Boolean)?> { null }

internal data class XpFeaturedState(val hero: XpMeta?, val waiting: Boolean)

@Composable
internal fun rememberXpFeaturedMovie(
    enabled: Boolean,
    profile: String,
    authorizedSources: List<String>,
    candidates: List<XpMeta>,
    rotationAllowed: Boolean,
    active: Boolean,
    visible: Boolean,
    retry: Long,
    catalogueReady: Boolean,
    settings: XpFeatureSettings,
    categories: Map<String, List<XpMeta>>,
    watched: List<XpMeta>,
    signals: List<XpMeta>,
    hasSource: suspend (XpMeta) -> Boolean,
    prepareMetadata: suspend (XpMeta) -> XpMeta,
    readCatalogueCache: suspend () -> List<XpMeta>,
): XpFeaturedState {
    val context = LocalContext.current
    val started = remember(profile, retry) { SystemClock.elapsedRealtime() }
    val pool = remember(context, profile) { XpFeaturedPool(context, profile) }
    val session = remember(context) { context.getSharedPreferences("movioxp_feature_sessions", Context.MODE_PRIVATE) }
    val profileKey = profile
    val rotationSources by rememberUpdatedState(categories)
    val latestWatched by rememberUpdatedState(watched)
    val latestSignals by rememberUpdatedState(signals)
    val sourceCheck by rememberUpdatedState(LocalXpFeaturedSourceCheck.current ?: hasSource)
    val exposure = remember(context, profile) { XpFeatureExposure(context, profile) }
    val remainingKey = "$profileKey|remaining|${settings.seconds}"
    val lastTitleKey = "$profileKey|title"
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
    val initialSourceFingerprint = remember(authorizedSources) {
        MessageDigest.getInstance("SHA-256").digest(authorizedSources.joinToString("|").toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
    LaunchedEffect(visible, active, hero?.id, profile) {
        val shown = hero ?: return@LaunchedEffect
        if (visible && active && currentFeatureByProfile[profile] != shown.id) {
            exposure.displayed(shown, System.currentTimeMillis())
            currentFeatureByProfile[profile] = shown.id
        }
    }
    LaunchedEffect(enabled, active, initialCandidates, categories, retry) {
        if (!enabled || !active || hero != null) return@LaunchedEffect
        val last = session.getString(lastTitleKey, null)
        val now = System.currentTimeMillis()
        val confirmed = session.getString("$profileKey|verified-source-set", null) == initialSourceFingerprint &&
            now - session.getLong("$profileKey|source-check-time", 0) in 0..86_400_000L
        val previous = initialCandidates.firstOrNull { it.id == last && confirmed && currentFeatureByProfile[profile] == it.id }
        val watches = latestWatched
        val eligiblePrevious = previous?.takeUnless { item ->
            watches.any { it.type == "movie" && xpFeatureAliases(it).any(xpFeatureAliases(item)::contains) &&
                (it.durationMs <= 0 || it.positionMs == 0L || it.positionMs.toDouble() / it.durationMs >= .95) }
        }
        val queue = xpRankFeatureQueue(
            rotationSources + ("xp_trending" to (rotationSources["xp_trending"].orEmpty() + candidates)),
            settings.trendingOnly, watches, latestSignals, exposure.recent(now).keys, java.time.LocalDate.now(),
        )
        val selected = if (eligiblePrevious != null && ready(eligiblePrevious)) eligiblePrevious else {
            queue.take(3).firstOrNull { item -> withTimeoutOrNull(1_500) { sourceCheck(item) } == true && ready(item) }
        }
        if (selected != null) {
            hero = selected
            if (selected != eligiblePrevious) session.edit().putString(lastTitleKey, selected.id)
                .putString("$profileKey|verified-source-set", initialSourceFingerprint)
                .putLong("$profileKey|source-check-time", System.currentTimeMillis()).apply()
            withContext(Dispatchers.IO) { pool.write(lastFeatureSource, listOf(selected)) }
            xpDebug("Featured first ready at ${SystemClock.elapsedRealtime() - started} ms")
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
            delay((remaining - 6_000).coerceAtLeast(0))
            val options = xpRankFeatureQueue(rotationSources, settings.trendingOnly, latestWatched, latestSignals,
                exposure.recent(System.currentTimeMillis()).keys + xpFeatureAliases(current), java.time.LocalDate.now())
            var next: XpMeta? = null
            for (candidate in options.take(3)) {
                val allowance = (deadline - SystemClock.elapsedRealtime()).coerceAtMost(1_500)
                if (allowance <= 0) break
                if (withTimeoutOrNull(allowance) { sourceCheck(candidate) } != true) continue
                if (!ready(candidate)) continue
                next = withTimeoutOrNull(1_000) { prepareMetadata(candidate) } ?: candidate
                break
            }
            delay((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0))
            completed = true
            session.edit().putLong(remainingKey, interval).apply()
            if (next != null) {
                session.edit().putString(lastTitleKey, next.id).putString("$profileKey|verified-source-set", initialSourceFingerprint)
                .putLong("$profileKey|source-check-time", System.currentTimeMillis()).apply()
                hero = next
                withContext(Dispatchers.IO) { pool.write(lastFeatureSource, listOf(next)) }
            } else if (options.isEmpty()) {
                hero = null
                session.edit().remove(lastTitleKey).apply()
                waiting = false
            } else rotationCycle++
        } finally {
            if (!completed) session.edit().putLong(remainingKey,
                (deadline - SystemClock.elapsedRealtime()).coerceIn(0, interval)).apply()
        }
    }
    return XpFeaturedState(hero.takeIf { enabled && active }, waiting)
}
