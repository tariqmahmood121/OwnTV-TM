package tv.own.owntv.features.search

import android.content.Context
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import tv.own.owntv.core.database.entity.MovieEntity

/** Only credit text already downloaded while the user was viewing film details.
 * Never fetch remote credits in a search callback or index the entire provider catalogue.
 * Movie name guards against stale IDs after catalogue sync.
 */
internal object MovioCreditIndex {
    private const val PREF = "moviotv_credit_index_v79"
    private const val PREFIX = "m_"
    private const val MAX_STORED = 1200
    private data class Entry(val title: String, val terms: String)
    private val cache = ConcurrentHashMap<Long, Entry>()
    @Volatile private var loaded = false

    private fun load(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            prefs.all.forEach { (key, raw) ->
                val id = key.removePrefix(PREFIX).toLongOrNull() ?: return@forEach
                val value = raw as? String ?: return@forEach
                val parts = value.split('\n', limit = 2)
                if (parts.size == 2) cache[id] = Entry(parts[0], parts[1])
            }
            loaded = true
        }
    }

    fun remember(context: Context, movie: MovieEntity, actors: List<String>, genres: List<String>, director: String?) {
        val terms = (actors + genres + listOfNotNull(director)).map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.length >= 3 }.distinct().joinToString("\n")
        if (terms.isBlank()) return
        load(context)
        cache[movie.id] = Entry(movie.name, terms)
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (cache.size > MAX_STORED) {
            // Keep the store bounded and responsive on low-memory devices; no bulk cataloguing.
            val old = cache.keys.firstOrNull { it != movie.id }
            if (old != null) { cache.remove(old); prefs.edit().remove(PREFIX + old).apply() }
        }
        prefs.edit().putString(PREFIX + movie.id, movie.name + "\n" + terms).apply()
    }

    fun matchingIds(context: Context, query: String): List<Long> {
        val needle = query.trim().lowercase(Locale.ROOT)
        if (needle.length < 3) return emptyList()
        load(context)
        return cache.entries.asSequence().filter { (_, entry) ->
            entry.terms.lineSequence().any { it.contains(needle) }
        }.map { it.key }.take(80).toList()
    }

    fun titleStillMatches(context: Context, movie: MovieEntity): Boolean {
        load(context)
        return cache[movie.id]?.title == movie.name
    }
}
