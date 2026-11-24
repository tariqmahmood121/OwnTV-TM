package tv.own.owntv.features.home

import android.content.Context
import org.json.JSONArray
import java.security.MessageDigest
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
        Category("xp_horror", R.string.movioxp_home_horror),
        Category("xp_action", R.string.movioxp_home_action),
        Category("xp_thriller", R.string.movioxp_home_thriller),
        Category("xp_bollywood", R.string.movioxp_home_bollywood),
        Category("xp_cinema", R.string.movioxp_home_cinema),
        Category("xp_upcoming", R.string.movioxp_home_upcoming),
        Category("xp_latest", R.string.movioxp_home_latest),
        Category("xp_cinemeta_featured", R.string.movioxp_home_cinemeta_featured),
    )
    private val ids = categories.map { it.id }
    private val defaults = ids.toSet()
    private val initiallyHidden = setOf("xp_latest", "xp_cinemeta_featured")
    private val externalId = Regex("addon:[0-9a-f]{64}")

    fun externalRowId(url: String, type: String, catalogue: String): String {
        val identity = JSONArray(listOf(url, type, catalogue)).toString()
        val hash = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
        return "addon:" + hash.joinToString("") { "%02x".format(it) }
    }

    fun externalVisible(settings: Settings, id: String, cinemeta: Boolean): Boolean =
        settings.showDefaultCatalogs && id !in settings.hidden && (!cinemeta || id in settings.order)

    fun toggleExternal(settings: Settings, id: String, currentlyEnabled: Boolean): Settings =
        settings.copy(
            order = (settings.order + id).distinct(),
            hidden = if (currentlyEnabled) settings.hidden + id else settings.hidden - id,
        )
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
        fun visibleDiscoverIds(): List<String> = order.filter { it !in hidden && isDiscoverCategory(it) }
    }

    fun orderKey(id: Long) = "xp_home_categories_$id"
    fun hiddenKey(id: Long) = "xp_home_hidden_$id"
    fun defaultsKey(id: Long) = "xp_home_defaults_$id"

    private fun isRowId(id: String) = isDiscoverCategory(id) || externalId.matches(id)

    private fun parseIds(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val json = JSONArray(raw)
            buildList {
                for (i in 0 until json.length()) {
                    val id = json.optString(i)
                    if (isRowId(id) && id !in this) add(id)
                }
            }
        }.getOrDefault(emptyList())
    }

    internal fun decode(orderJson: String?, hiddenJson: String?, showDefaultCatalogs: Boolean): Settings {
        val specified = parseIds(orderJson)
        val order = specified + ids.filterNot { it in specified }
        val hidden = parseIds(hiddenJson).toSet() + initiallyHidden.filterNot { it in specified }
        return Settings(order, hidden, showDefaultCatalogs)
    }

    fun read(context: Context, profileId: Long): Settings {
        val pref = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return decode(pref.getString(orderKey(profileId), null), pref.getString(hiddenKey(profileId), null),
            pref.getBoolean(defaultsKey(profileId), true))
    }

    fun save(context: Context, profileId: Long, settings: Settings) {
        if (profileId <= 0) return
        val order = settings.order.filter(::isRowId).distinct() +
            ids.filterNot { it in settings.order }
        val hidden = settings.hidden.filter(::isRowId)
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
