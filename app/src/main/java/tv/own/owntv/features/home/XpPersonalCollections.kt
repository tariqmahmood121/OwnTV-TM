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
        decode(saved) {
            if (collection == XpCollection.HISTORY && profile > 0) XpContinueStore.read(context, profile)
            else emptyList()
        }
    }.getOrDefault(emptyList())

    internal fun decode(saved: String?, legacy: () -> List<XpMeta>): List<XpMeta> {
        if (saved == null) return legacy()
        val entries = JSONArray(saved)
        return List(entries.length()) { index -> entries.optJSONObject(index)?.let(XpCentralDataSync::parseMeta) }.filterNotNull().let(XpCollectionOrder::unique)
    }

    @Synchronized
    fun update(context: Context, profile: Long, collection: XpCollection, item: XpMeta, deleted: Boolean) {
        if (profile <= 0) return
        val updated = XpCollectionOrder.merge(read(context, profile, collection), item, deleted)
        val json = JSONArray(updated.take(500).map(XpCentralDataSync::itemJson))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key(profile, collection), json.toString()).apply()
    }

    fun change(context: Context, profile: Long, collection: XpCollection, item: XpMeta, deleted: Boolean = false) {
        val dated = item.copy(activityAt = if (!deleted && collection == XpCollection.HISTORY && item.activityAt > 0L) item.activityAt else System.currentTimeMillis())
        update(context, profile, collection, dated, deleted)
        XpCentralDataSync.markCollection(context, profile, collection, dated, deleted)
    }
}

internal object XpCollectionOrder {
    fun unique(items: List<XpMeta>): List<XpMeta> = buildList {
        for (item in items.sortedByDescending(XpMeta::activityAt)) {
            if (none { XpPlaybackIdentity.sameTitle(it, item) }) add(item)
        }
    }

    fun merge(items: List<XpMeta>, incoming: XpMeta, deleted: Boolean): List<XpMeta> {
        val existing = items.firstOrNull { XpPlaybackIdentity.sameTitle(it, incoming) }
        if (existing != null && existing.activityAt > incoming.activityAt && (!deleted || incoming.activityAt > 0)) return items
        if (existing != null && !deleted && incoming.activityAt == 0L) return items
        val remaining = items.filterNot { XpPlaybackIdentity.sameTitle(it, incoming) }
        if (deleted) return remaining
        val merged = incoming.copy(
            imdbId = incoming.imdbId ?: existing?.imdbId,
            tmdbId = incoming.tmdbId ?: existing?.tmdbId,
        )
        return (listOf(merged) + remaining).sortedByDescending(XpMeta::activityAt)
    }

    fun resumeShelf(items: List<XpMeta>): List<XpMeta> = unique(items)
        .filter { it.positionMs > 0L }
        .distinctBy { item ->
            item.type to if (item.type == "series") episodeParent(item.id) else item.id
        }

    private fun episodeParent(id: String): String {
        val parts = id.split(':')
        return if (parts.size >= 3 && parts.takeLast(2).all { it.toIntOrNull() != null }) {
            parts.dropLast(2).joinToString(":")
        } else id
    }
}
