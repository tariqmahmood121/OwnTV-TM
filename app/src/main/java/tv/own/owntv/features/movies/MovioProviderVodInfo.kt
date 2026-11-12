package tv.own.owntv.features.movies

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType

internal data class MovioProviderVodInfo(
    val synopsis: String?,
    val genres: List<String>,
    val cast: List<String>,
    val director: String?,
    val releaseDate: String?,
    val durationSeconds: Int?,
    val rating: Double?,
)

internal object MovioProviderVodLookup {
    private data class Cached(val at: Long, val value: MovioProviderVodInfo?)
    private val cache = ConcurrentHashMap<String, Cached>()
    suspend fun load(source: SourceEntity, vodId: String): MovioProviderVodInfo? {
        if (source.type != SourceType.XTREAM) return null
        val user = source.username.orEmpty()
        val pass = source.password.orEmpty()
        val server = source.url.trim().trimEnd('/').removeSuffix("/player_api.php")
        if (!(server.startsWith("https://", true) || server.startsWith("http://", true)) ||
            user.isBlank() || pass.isBlank() || vodId.isBlank()) return null
        val key = source.id.toString() + ":" + vodId
        val now = System.currentTimeMillis()
        cache[key]?.let {
            if (now - it.at < (if (it.value == null) 60_000L else 600_000L)) return it.value
        }
        val result = withContext(Dispatchers.IO) {
            runCatching {
                fun q(s: String) = URLEncoder.encode(s, "UTF-8")
                val endpoint = "$server/player_api.php?username=${q(user)}&password=${q(pass)}&action=get_vod_info&vod_id=${q(vodId)}"
                val connection = URL(endpoint).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 5500
                    connection.readTimeout = 5500
                    connection.instanceFollowRedirects = false
                    connection.setRequestProperty("Accept", "application/json")
                    if (connection.responseCode !in 200..299) null
                    else {
                        val payload = connection.inputStream.bufferedReader().use { reader ->
                            val buffer = CharArray(4096)
                            val text = StringBuilder()
                            while (text.length < 128_000) {
                                val n = reader.read(buffer, 0, minOf(buffer.size, 128_000-text.length))
                                if (n < 0) break
                                text.append(buffer, 0, n)
                            }
                            text.toString()
                        }
                        parse(payload)
                    }
                } finally { connection.disconnect() }
            }.getOrNull()
        }
        if (cache.size > 600) cache.clear()
        cache[key] = Cached(now, result)
        return result
    }
    private fun parse(json: String): MovioProviderVodInfo? {
        val root=JSONObject(json)
        val info=root.optJSONObject("info")
        val data=root.optJSONObject("movie_data")
        fun field(vararg names: String): String? {
            for (obj in listOfNotNull(info,data,root)) for (name in names) {
                val v=obj.opt(name) ?: continue
                val text=when(v) {
                    is JSONArray -> (0 until v.length()).joinToString(", ") { i -> v.optString(i) }
                    is String, is Number -> v.toString()
                    else -> ""
                }.trim()
                if (text.isNotEmpty() && !text.equals("null",true) && text!="0") return text
            }
            return null
        }
        fun names(raw: String?): List<String> = raw.orEmpty().split(',', ';', '|')
            .map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(10)
        val rawTime=field("duration_secs","duration_seconds","duration")
        val seconds=rawTime?.toIntOrNull()?.takeIf { it > 0 }
            ?: rawTime?.split(':')?.takeIf { it.size in 2..3 && it.all { s -> s.toIntOrNull()!=null } }
                ?.map { it.toInt() }?.fold(0) { a,v -> a*60+v }?.takeIf { it>0 }
        return MovioProviderVodInfo(
            synopsis=field("plot","description","overview","storyline"),
            genres=names(field("genre","genres")),
            cast=names(field("cast","actors","starring")),
            director=field("director"),
            releaseDate=field("releasedate","release_date","releaseDate"),
            durationSeconds=seconds,
            rating=field("rating","rating_5based")?.toDoubleOrNull()?.takeIf { it in 0.1..10.0 },
        )
    }
}
