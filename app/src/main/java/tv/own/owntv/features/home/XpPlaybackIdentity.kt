package tv.own.owntv.features.home

import android.util.Log
import kotlinx.coroutines.flow.last
import java.util.Locale
import tv.own.owntv.BuildConfig

internal fun xpDebug(event: String) {
    if (BuildConfig.DEBUG || BuildConfig.DIAGNOSTIC_BUILD) runCatching { Log.d("MovioXP", event) }
}

internal object XpPlaybackIdentity {
    private val imdb = Regex("tt[0-9]+")
    private val tmdb = Regex("tmdb:[1-9][0-9]*")

    fun isImdbId(id: String): Boolean = imdb.matches(id)

    fun tmdbId(item: XpMeta): String? = listOfNotNull(item.tmdbId, item.id).firstOrNull(tmdb::matches)

    fun ids(item: XpMeta, resolved: String? = null): List<String> =
        (listOfNotNull(resolved?.takeIf(imdb::matches), item.imdbId?.takeIf(imdb::matches), item.id, tmdbId(item))).distinct()

    fun sameTitle(left: XpMeta, right: XpMeta): Boolean =
        left.type == right.type && ids(left).any(ids(right)::contains)

    private fun titleKey(title: String) = title.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

    fun matchingTitleCandidates(item: XpMeta, candidates: List<XpMeta>): List<XpMeta> =
        candidates.filter { imdb.matches(it.id) && it.type == item.type && titleKey(it.name) == titleKey(item.name) }
            .distinctBy(XpMeta::id)

    fun exactImdbMatch(item: XpMeta, candidates: List<XpMeta>): String? {
        val date = xpFullReleaseDate(item) ?: return null
        val possible = matchingTitleCandidates(item, candidates).filter { candidate ->
            val year = xpReleaseYear(candidate)?.toIntOrNull()
            year == null || year == date.year || xpFullReleaseDate(candidate) != null
        }
        if (possible.any { xpFullReleaseDate(it) == null }) return null
        return possible.filter { xpFullReleaseDate(it) == date }.singleOrNull()?.id
    }

    suspend fun resolve(
        repository: XpCatalogueRepository,
        profile: String,
        connection: XpCentralConnection?,
        item: XpMeta,
        fallback: suspend () -> String?,
    ): String? {
        listOfNotNull(item.imdbId, item.id).firstOrNull(imdb::matches)?.let { return it }
        val tmdbId = tmdbId(item) ?: return null
        if (item.type != "movie") return null
        val provider = connection?.apiBase ?: "cinemeta"
        val key = XpCatalogueRepository.Key(profile, provider, "metadata/resolve/$tmdbId")
        val value = repository.observe(key, cacheable = { imdb.matches(it) }) {
            var allowFallback = true
            val central = connection?.let {
                try {
                    val path = "metadata/resolve/movie/${tmdbId.removePrefix("tmdb:")}"
                    val response = XpCentralAddonSync.request(it.apiBase, path, it.token)
                    allowFallback = response.optBoolean("fallback", true)
                    response.optString("imdb_id").takeIf(imdb::matches)
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (_: Exception) {
                    xpDebug("Central identifier lookup unavailable")
                    null
                }
            }
            val id = central ?: if (allowFallback) fallback() else null
            xpDebug(if (id == null) "IMDb identifier unresolved" else "IMDb identifier resolved")
            id.orEmpty()
        }.last()
        return value?.takeIf(imdb::matches)
    }
}
