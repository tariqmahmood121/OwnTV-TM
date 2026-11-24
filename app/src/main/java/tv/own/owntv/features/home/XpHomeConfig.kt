package tv.own.owntv.features.home

import android.content.Context
import org.json.JSONArray
import tv.own.owntv.R

/**
 * Profile-scoped Home layout; the UI and Central sync use the same small preference records.
 * Installing/removing an add-on is separate from enabling its catalogue rows.
 */
internal object XpHomeConfig {
    // Built-in home organiser. Uses existing catalogue providers, no separate server.
    private const val PREFS = "movioxp_playback"

    internal data class Category(val id: String, val labelRes: Int)

    val categories = listOf(
        Category("xp_trending", R.string.movioxp_home_trending),
        Category("xp_latest", R.string.movioxp_home_latest),
        Category("xp_horror", R.string.movioxp_home_horror),
        Category("xp_action", R.string.movioxp_home_action),
        Category("xp_thriller", R.string.movioxp_home_thriller),
        Category("xp_bollywood", R.string.movioxp_home_bollywood),
    )
    private val ids = categories.map { it.id }
    private val defaults = ids.toSet()
    // Extra categories can come from any installed catalogue's advertised movie genres.
    // Store the actual genre, never a made-up category or an unbounded user-supplied URL.
    private const val GENRE_PREFIX = "genre:"

    fun isDiscoverCategory(id: String): Boolean {
        if (id in defaults) return true
        if (!id.startsWith(GENRE_PREFIX)) return false
        val name = id.removePrefix(GENRE_PREFIX)
        return name.length in 1..70 && name.isNotBlank() &&
            name.all { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '&' || it == '\'' }
    }

    fun idForGenre(name: String): String? {
        val id = GENRE_PREFIX + name.trim()
        return id.takeIf(::isDiscoverCategory)
    }

    fun genreName(id: String): String? =
        if (isDiscoverCategory(id) && id.startsWith(GENRE_PREFIX))
            id.removePrefix(GENRE_PREFIX) else null

    data class Settings(
        val order: List<String>,
        val hidden: Set<String>,
        val showDefaultCatalogs: Boolean,
    ) {
        fun visibleDiscoverIds(): List<String> = order.filterNot { it in hidden }
    }

    fun orderKey(id: Long) = "xp_home_categories_$id"
    fun hiddenKey(id: Long) = "xp_home_hidden_$id"
    fun defaultsKey(id: Long) = "xp_home_defaults_$id"

    private fun parseIds(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val json = JSONArray(raw)
            buildList {
                for (i in 0 until json.length()) {
                    val id = json.optString(i)
                    if (isDiscoverCategory(id) && id !in this) add(id)
                }
            }
        }.getOrDefault(emptyList())
    }

    fun read(context: Context, profileId: Long): Settings {
        val pref = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val specified = parseIds(pref.getString(orderKey(profileId), null))
        val order = specified + ids.filterNot { it in specified }
        val hidden = parseIds(pref.getString(hiddenKey(profileId), null)).toSet()
        return Settings(order, hidden, pref.getBoolean(defaultsKey(profileId), true))
    }

    fun save(context: Context, profileId: Long, settings: Settings) {
        if (profileId <= 0) return
        val order = settings.order.filter(::isDiscoverCategory).distinct() +
            ids.filterNot { it in settings.order }
        val hidden = settings.hidden.filter(::isDiscoverCategory)
        val orderJson = JSONArray(order).toString()
        val hiddenJson = JSONArray(hidden).toString()
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit()
            .putString(orderKey(profileId), orderJson)
            .putString(hiddenKey(profileId), hiddenJson)
            .putBoolean(defaultsKey(profileId), settings.showDefaultCatalogs)
            .apply()
        XpCentralDataSync.markSetting(context, profileId, orderKey(profileId), orderJson)
        XpCentralDataSync.markSetting(context, profileId, hiddenKey(profileId), hiddenJson)
        XpCentralDataSync.markSetting(context, profileId, defaultsKey(profileId), settings.showDefaultCatalogs)
    }

    fun move(settings: Settings, index: Int, direction: Int): Settings {
        val nextIndex = index + direction
        if (index !in settings.order.indices || nextIndex !in settings.order.indices) return settings
        val next = settings.order.toMutableList()
        val old = next[index]
        next[index] = next[nextIndex]
        next[nextIndex] = old
        return settings.copy(order = next)
    }

    fun add(settings: Settings, id: String): Settings =
        if (!isDiscoverCategory(id) || id in settings.order) settings
        else settings.copy(order = settings.order + id)

    fun remove(settings: Settings, id: String): Settings =
        if (id in defaults) settings
        else settings.copy(order = settings.order - id, hidden = settings.hidden - id)
}
