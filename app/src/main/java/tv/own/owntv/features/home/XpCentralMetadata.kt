package tv.own.owntv.features.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import org.json.JSONObject
import java.util.Locale

internal object XpCentralMetadata {
    val customGenres = mapOf(
        "comedy" to tv.own.owntv.R.string.movioxp_genre_comedy,
        "drama" to tv.own.owntv.R.string.movioxp_genre_drama,
        "romance" to tv.own.owntv.R.string.movioxp_genre_romance,
        "animation" to tv.own.owntv.R.string.movioxp_genre_animation,
        "crime" to tv.own.owntv.R.string.movioxp_genre_crime,
        "documentary" to tv.own.owntv.R.string.movioxp_genre_documentary,
        "fantasy" to tv.own.owntv.R.string.movioxp_genre_fantasy,
        "mystery" to tv.own.owntv.R.string.movioxp_genre_mystery,
        "adventure" to tv.own.owntv.R.string.movioxp_genre_adventure,
        "family" to tv.own.owntv.R.string.movioxp_genre_family,
        "history" to tv.own.owntv.R.string.movioxp_genre_history,
        "war" to tv.own.owntv.R.string.movioxp_genre_war,
        "western" to tv.own.owntv.R.string.movioxp_genre_western,
        "music" to tv.own.owntv.R.string.movioxp_genre_music,
        "science-fiction" to tv.own.owntv.R.string.movioxp_genre_science_fiction,
    )
    private val imdbId = Regex("tt[0-9]+")
    private val tmdbId = Regex("tmdb:[0-9]+")

    fun category(id: String): String? = when (id) {
        "xp_trending" -> "trending"
        "xp_latest" -> "latest"
        "xp_cinema" -> "cinema"
        "xp_bollywood" -> "bollywood"
        "xp_horror" -> "horror"
        "xp_action" -> "action"
        "xp_thriller" -> "thriller"
        "xp_upcoming" -> "upcoming"
        else -> XpHomeConfig.genreName(id)?.let { genre ->
            val slug = genre.lowercase(Locale.ROOT).replace(' ', '-')
            when (slug) {
                "sci-fi" -> "science-fiction"
                "horror", "action", "thriller" -> slug
                else -> slug.takeIf(customGenres::containsKey)
            }
        }
    }

    fun observe(
        repository: XpCatalogueRepository,
        profile: String,
        connection: XpCentralConnection,
        category: String,
        refreshAfter: Long,
    ) = repository.observe(
        XpCatalogueRepository.Key(profile, connection.apiBase, category),
        refreshAfter,
        cacheable = { body -> JSONObject(body).let { it.has("metas") && !it.optBoolean("fallback") && !it.optBoolean("stale") } },
    ) {
        XpCentralAddonSync.request(connection.apiBase, "metadata/catalog/$category", connection.token).toString()
    }.map { body -> body?.let { parseCatalog(JSONObject(it)) } }.flowOn(Dispatchers.IO)

    // Null means Central allows a provider fallback. An empty list is an authoritative empty row.
    internal fun parseCatalog(response: JSONObject): List<XpMeta>? {
        val metas = response.optJSONArray("metas")
        if (response.optBoolean("fallback") && (metas == null || metas.length() == 0)) return null
        if (metas == null) return null
        return buildList {
            for (index in 0 until metas.length()) {
                val item = metas.optJSONObject(index) ?: continue
                fun field(key: String) = if (item.isNull(key)) null else item.optString(key).takeIf(String::isNotBlank)
                val providerId = field("id") ?: continue
                val imdb = field("imdb_id")?.takeIf(imdbId::matches)
                val tmdb = providerId.takeIf(tmdbId::matches)
                val title = field("name") ?: continue
                add(XpMeta(
                    id = imdb ?: providerId,
                    type = field("type") ?: "movie",
                    name = title,
                    poster = field("poster"),
                    description = field("description"),
                    releaseInfo = field("released") ?: field("releaseInfo"),
                    rating = field("rating"),
                    tmdbId = tmdb,
                    ratingSource = field("rating_source") ?: if (tmdb != null) "TMDB" else null,
                ))
            }
        }.distinctBy { it.type to it.id }
    }
}

internal fun catalogueProfileKey(connection: XpCentralConnection?, localProfileId: Long): String =
    "${connection?.apiBase}|${connection?.accountId}|$localProfileId"
