package tv.own.owntv.features.home

import android.content.SharedPreferences
import org.json.JSONArray

internal enum class XpNavigationPosition { TOP, BELOW_HEADER, BOTTOM }

internal object XpRecentSearches {
    fun add(previous: List<String>, query: String): List<String> {
        val text = query.trim()
        if (text.isEmpty()) return previous
        return (listOf(text) + previous.filterNot { it.equals(text, ignoreCase = true) }).take(5)
    }

    fun read(prefs: SharedPreferences, scope: String): List<String> = runCatching {
        val values = JSONArray(prefs.getString(scope, "[]"))
        List(values.length().coerceAtMost(5)) { values.getString(it) }
    }.getOrDefault(emptyList())

    fun write(prefs: SharedPreferences, scope: String, values: List<String>) {
        prefs.edit().putString(scope, JSONArray(values.take(5)).toString()).apply()
    }

    fun rank(title: String, query: String): Int = when {
        title.equals(query, ignoreCase = true) -> 0
        title.startsWith(query, ignoreCase = true) -> 1
        title.contains(query, ignoreCase = true) -> 2
        else -> 3
    }
}

internal data class XpDisplaySnapshot(
    val navigation: XpNavigationPosition,
    val cards: Float,
    val home: Float,
    val discover: Float,
    val library: Float,
    val rating: Boolean,
    val year: Boolean,
    val zoom: Int,
    val font: Int,
)

internal class XpV4DisplayPreferences(context: android.content.Context, private val scope: String) {
    private val prefs = context.getSharedPreferences("movioxp_display_v4", android.content.Context.MODE_PRIVATE)

    fun cinema(): Boolean = prefs.getBoolean("$scope:cinema", true)
    fun saveCinema(value: Boolean) { prefs.edit().putBoolean("$scope:cinema", value).apply() }

    fun navigation(): XpNavigationPosition = XpNavigationPosition.entries.getOrElse(prefs.getInt("$scope:navigation", 1)) {
        XpNavigationPosition.BELOW_HEADER
    }

    fun cards(fallback: Float): Float = prefs.getFloat("$scope:cards", fallback).takeIf { it in setOf(0.65f, 0.8f, 1f) } ?: 0.8f

    fun saveLayout(navigation: XpNavigationPosition, cards: Float) {
        prefs.edit().putInt("$scope:navigation", navigation.ordinal).putFloat("$scope:cards", cards).apply()
    }

    fun previous(): XpDisplaySnapshot? = read("$scope:previous")
    fun savePrevious(snapshot: XpDisplaySnapshot) = write("$scope:previous", snapshot, false)

    fun pending(): XpDisplaySnapshot? = read("$scope:pending")
    fun savePending(snapshot: XpDisplaySnapshot) = write("$scope:pending", snapshot, true)
    fun clearPending() { prefs.edit().remove("$scope:pending").commit() }

    private fun write(key: String, s: XpDisplaySnapshot, durable: Boolean): Boolean {
        val edit = prefs.edit().putString(key, JSONArray(listOf(s.navigation.ordinal, s.cards, s.home,
            s.discover, s.library, s.rating, s.year, s.zoom, s.font)).toString())
        return if (durable) edit.commit() else { edit.apply(); true }
    }

    private fun read(key: String): XpDisplaySnapshot? = runCatching {
        val values = JSONArray(prefs.getString(key, null) ?: return null)
        XpDisplaySnapshot(XpNavigationPosition.entries[values.getInt(0)], values.getDouble(1).toFloat(),
            values.getDouble(2).toFloat(), values.getDouble(3).toFloat(), values.getDouble(4).toFloat(),
            values.getBoolean(5), values.getBoolean(6), values.getInt(7), values.getInt(8))
    }.getOrNull()
}
