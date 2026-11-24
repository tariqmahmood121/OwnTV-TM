package tv.own.owntv.features.home

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal enum class XpCollection { HISTORY, WATCHLIST }

internal object XpPersonalCollections {
    const val PREFS = "xp_personal_collections"

    fun key(profile: Long, collection: XpCollection) = "${collection.name}:$profile"

    fun read(context: Context, profile: Long, collection: XpCollection): List<XpMeta> = runCatching {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key(profile, collection), null)
        val entries = saved?.let(::JSONArray) ?: JSONArray()
        List(entries.length()) { index -> entries.optJSONObject(index)?.let(XpCentralDataSync::parseMeta) }.filterNotNull()
    }.getOrDefault(emptyList())

    @Synchronized
    fun update(context: Context, profile: Long, collection: XpCollection, item: XpMeta, deleted: Boolean) {
        if (profile <= 0) return
        val existing = read(context, profile, collection).filterNot { XpPlaybackIdentity.sameTitle(it, item) }
        val updated = if (deleted) existing else listOf(item) + existing
        val json = JSONArray(updated.take(500).map(XpCentralDataSync::itemJson))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key(profile, collection), json.toString()).apply()
    }

    fun change(context: Context, profile: Long, collection: XpCollection, item: XpMeta, deleted: Boolean = false) {
        update(context, profile, collection, item, deleted)
        XpCentralDataSync.markCollection(context, profile, collection, item, deleted)
    }
}
