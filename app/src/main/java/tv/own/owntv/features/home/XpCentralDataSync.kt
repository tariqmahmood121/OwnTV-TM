package tv.own.owntv.features.home

import android.content.Context
import tv.own.owntv.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/** Profile-scoped MovioXP library and playback sync through Central's versioned sync records. */
internal object XpCentralDataSync {
    private const val DIRTY_PREFS = "movioxp_central_dirty"
    private const val STATE_PREFS = "movioxp_central_state"
    private const val POSTER_PREFS = "moviotv_poster_grid_v75"
    private val syncMutex = Mutex()

    fun markFavorite(context: Context, localProfileId: Long, item: XpMeta, deleted: Boolean) =
        mark(context, localProfileId, "favorite", titleKey(item.type, item.id), itemJson(item).put("deleted", deleted))

    fun markResume(context: Context, localProfileId: Long, item: XpMeta, deleted: Boolean) =
        mark(context, localProfileId, "resume", titleKey(item.type, item.id), itemJson(item).put("deleted", deleted))

    fun markSetting(context: Context, localProfileId: Long, key: String, value: Any) {
        if (localProfileId <= 0L) return
        val cloudKey = canonicalPreferenceKey(key, localProfileId) ?: return
        mark(context, localProfileId, "setting", "xp:$cloudKey", JSONObject().put("key", cloudKey).put("value", value))
        XpCentralEventSync.request(context, localProfileId)
    }

    private fun mark(context: Context, profileId: Long, type: String, key: String, payload: JSONObject) {
        if (profileId <= 0L) return
        context.getSharedPreferences(DIRTY_PREFS, Context.MODE_PRIVATE).edit()
            .putString(dirtyKey(profileId, type, key), payload.toString()).apply()
    }

    suspend fun sync(context: Context, connection: XpCentralConnection, localProfileId: Long, profileName: String, isChild: Boolean) =
        withContext(Dispatchers.IO) {
            syncMutex.withLock {
        val centralProfileId = XpCentralAddonSync.ensureProfile(context, connection, localProfileId, profileName, isChild)
        val state = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
        val ownerKey = "owner_$localProfileId"
        val owner = "${connection.accountId}:$centralProfileId"
        val previousOwner = state.getString(ownerKey, null)

        // A local profile belongs permanently to its first Central account/profile binding.
        // NEVER wipe its saved favourites/history/resume on account switch: old accounts must remain
        // intact and dormant. Refuse a cross-account mapping rather than migrating private data.
        if (previousOwner != null && previousOwner != owner) {
            throw IllegalStateException(context.getString(R.string.movioxp_profile_wrong_account))
        }

        val response = XpCentralAddonSync.request(connection.apiBase, "sync?profile_id=$centralProfileId", connection.token)
        val remote = parseRecords(response).associateBy { recordId(it.type, it.key) }.toMutableMap()
        val dirtyPrefs = context.getSharedPreferences(DIRTY_PREFS, Context.MODE_PRIVATE)
        val dirtyPrefix = "$localProfileId::"
        val dirty = dirtyPrefs.all.entries.filter { it.key.startsWith(dirtyPrefix) }.mapNotNull { (rawKey, rawPayload) ->
            val parts = rawKey.split("::", limit = 3)
            val payload = (rawPayload as? String)?.let { runCatching { JSONObject(it) }.getOrNull() }
            if (parts.size != 3 || payload == null) null
            else Dirty(parts[1], parts[2], payload)
        }

        val local = localRecords(context, localProfileId)
        val seedKey = "seeded_${connection.accountId}_$centralProfileId"
        val seeding = !state.getBoolean(seedKey, false)
        val uploadQueue = linkedMapOf<String, Dirty>()
        dirty.forEach { uploadQueue[recordId(it.type, it.key)] = it }
        // Preserve pre-Central device data on the first link, adding only records absent in Central.
        if (seeding) local.forEach { record ->
            val id = recordId(record.type, record.key)
            if (id !in remote && id !in uploadQueue) uploadQueue[id] = Dirty(record.type, record.key, record.payload)
        }

        for ((id, entry) in uploadQueue) {
            val current = remote[id]
            val body = JSONObject()
                .put("profile_id", centralProfileId)
                .put("record_type", entry.type)
                .put("record_key", entry.key)
                .put("payload", entry.payload)
                .put("expected_version", current?.version ?: 0)
            val saved = XpCentralAddonSync.request(connection.apiBase, "sync", connection.token, "PUT", body)
            val result = saved.optJSONObject("record") ?: saved
            remote[id] = Record(entry.type, entry.key, entry.payload, result.optInt("version", (current?.version ?: 0) + 1))
            // A newer playhead/track choice can arrive during the HTTP request. Do not
            // remove or overwrite that still-pending local update.
            val pendingKey = dirtyKey(localProfileId, entry.type, entry.key)
            if (dirtyPrefs.getString(pendingKey, null) == entry.payload.toString()) {
                dirtyPrefs.edit().remove(pendingKey).apply()
            }
        }

        // Clean remote records can be applied. Pending local changes win until uploaded.
        remote.values.forEach { record ->
            if (!dirtyPrefs.contains(dirtyKey(localProfileId, record.type, record.key))) {
                applyRemote(context, localProfileId, record)
            }
        }
        state.edit().putString(ownerKey, owner).putBoolean(seedKey, true).apply()
            }
        }

    private fun clearLocalProfile(context: Context, profileId: Long) {
        XpFavoritesStore.write(context, profileId, emptyList())
        XpContinueStore.writeCloud(context, profileId, emptyList())
        val prefs = context.getSharedPreferences("movioxp_playback", Context.MODE_PRIVATE)
        val editor = prefs.edit().remove("auto_select_source").remove("preferred_addon")
        prefs.all.keys.filter {
            it.startsWith("last_source_${profileId}_") ||
                it.startsWith("xp_audio_${profileId}_") || it.startsWith("xp_subtitle_${profileId}_") ||
                it.startsWith("xp_audio_lang_${profileId}_") || it.startsWith("xp_subtitle_lang_${profileId}_")
        }.forEach(editor::remove)
        editor.apply()
        val dirty = context.getSharedPreferences(DIRTY_PREFS, Context.MODE_PRIVATE)
        dirty.edit().also { edit ->
            dirty.all.keys.filter { it.startsWith("$profileId::") }.forEach(edit::remove)
        }.apply()
    }

    private fun localRecords(context: Context, profileId: Long): List<Record> = buildList {
        XpFavoritesStore.read(context, profileId).forEach { add(Record("favorite", titleKey(it.type, it.id), itemJson(it), 0)) }
        XpContinueStore.read(context, profileId).forEach { add(Record("resume", titleKey(it.type, it.id), itemJson(it), 0)) }
        context.getSharedPreferences("movioxp_playback", Context.MODE_PRIVATE).all.forEach { (key, value) ->
            val cloudKey = canonicalPreferenceKey(key, profileId)
            if (cloudKey != null && value != null) add(
                Record("setting", "xp:$cloudKey", JSONObject().put("key", cloudKey).put("value", value), 0)
            )
        }
        // Retain independent poster sizes for each authenticated profile across phone and TV.
        val posterPrefs = context.getSharedPreferences(POSTER_PREFS, Context.MODE_PRIVATE)
        for (page in listOf("library", "home")) {
            val key = "xp_${page}_zoom_$profileId"
            if (posterPrefs.contains(key)) {
                val zoom = posterPrefs.getFloat(key, 1f)
                if (zoom.isFinite()) {
                    val cloudKey = "poster_zoom:$page"
                    add(Record("setting", "xp:$cloudKey",
                        JSONObject().put("key", cloudKey).put("value", zoom), 0))
                }
            }
        }
    }

    private fun parseRecords(response: JSONObject): List<Record> {
        val source = response.optJSONArray("records") ?: response.optJSONArray("items") ?: JSONArray()
        return buildList {
            for (index in 0 until source.length()) {
                val raw = source.optJSONObject(index) ?: continue
                val type = raw.optString("record_type", raw.optString("type"))
                val key = raw.optString("record_key", raw.optString("key"))
                val payload = when (val value = raw.opt("payload")) {
                    is JSONObject -> value
                    is String -> runCatching { JSONObject(value) }.getOrNull()
                    else -> raw.optJSONObject("payload_json")
                } ?: continue
                if (type in setOf("favorite", "history", "resume", "setting", "last_channel") && key.isNotBlank())
                    add(Record(type, key, payload, raw.optInt("version", 0)))
            }
        }
    }

    private fun applyRemote(context: Context, profileId: Long, record: Record) {
        when (record.type) {
            "favorite", "resume" -> {
                val item = parseMeta(record.payload) ?: return
                val deleted = record.payload.optBoolean("deleted")
                if (record.type == "favorite") {
                    val current = XpFavoritesStore.read(context, profileId).filterNot { it.type == item.type && it.id == item.id }
                    XpFavoritesStore.write(context, profileId, if (deleted) current else listOf(item) + current)
                } else {
                    val current = XpContinueStore.read(context, profileId).filterNot { it.type == item.type && it.id == item.id }
                    XpContinueStore.writeCloud(context, profileId, if (deleted) current else listOf(item) + current)
                }
            }
            "setting" -> {
                val cloudKey = record.payload.optString("key")
                val value = record.payload.opt("value")
                if (cloudKey == "poster_zoom:library" || cloudKey == "poster_zoom:home") {
                    val zoom = (value as? Number)?.toFloat()?.takeIf(Float::isFinite) ?: return
                    val page = cloudKey.removePrefix("poster_zoom:")
                    context.getSharedPreferences(POSTER_PREFS, Context.MODE_PRIVATE)
                        .edit().putFloat("xp_${page}_zoom_$profileId", zoom.coerceIn(0.65f, 1.85f)).apply()
                    return
                }
                val localKey = localPreferenceKey(cloudKey, profileId) ?: return
                val editor = context.getSharedPreferences("movioxp_playback", Context.MODE_PRIVATE).edit()
                when (value) {
                    is Boolean -> editor.putBoolean(localKey, value)
                    is Int -> editor.putInt(localKey, value)
                    is Long -> editor.putLong(localKey, value)
                    is Number -> editor.putInt(localKey, value.toInt())
                    is String -> editor.putString(localKey, value)
                    else -> return
                }
                editor.apply()
            }
        }
    }

    private fun canonicalPreferenceKey(key: String, profileId: Long): String? = when {
        key == XpHomeConfig.orderKey(profileId) -> "home:order"
        key == XpHomeConfig.hiddenKey(profileId) -> "home:hidden"
        key == XpHomeConfig.defaultsKey(profileId) -> "home:defaults"
        key == "xp_library_zoom_$profileId" -> "poster_zoom:library"
        key == "xp_home_zoom_$profileId" -> "poster_zoom:home"
        key == "auto_select_source" || key == "preferred_addon" -> key
        key.startsWith("last_source_${profileId}_") -> "last_source:" + key.removePrefix("last_source_${profileId}_")
        key.startsWith("xp_audio_${profileId}_") -> "audio:" + key.removePrefix("xp_audio_${profileId}_")
        key.startsWith("xp_subtitle_${profileId}_") -> "subtitle:" + key.removePrefix("xp_subtitle_${profileId}_")
        key.startsWith("xp_audio_lang_${profileId}_") -> "audio_lang:" + key.removePrefix("xp_audio_lang_${profileId}_")
        key.startsWith("xp_subtitle_lang_${profileId}_") -> "subtitle_lang:" + key.removePrefix("xp_subtitle_lang_${profileId}_")
        else -> null
    }

    private fun localPreferenceKey(key: String, profileId: Long): String? = when {
        key == "home:order" -> XpHomeConfig.orderKey(profileId)
        key == "home:hidden" -> XpHomeConfig.hiddenKey(profileId)
        key == "home:defaults" -> XpHomeConfig.defaultsKey(profileId)
        key == "auto_select_source" || key == "preferred_addon" -> key
        key.startsWith("last_source:") -> "last_source_${profileId}_" + key.removePrefix("last_source:")
        key.startsWith("audio:") -> "xp_audio_${profileId}_" + key.removePrefix("audio:")
        key.startsWith("subtitle:") -> "xp_subtitle_${profileId}_" + key.removePrefix("subtitle:")
        key.startsWith("audio_lang:") -> "xp_audio_lang_${profileId}_" + key.removePrefix("audio_lang:")
        key.startsWith("subtitle_lang:") -> "xp_subtitle_lang_${profileId}_" + key.removePrefix("subtitle_lang:")
        else -> null
    }

    private fun parseMeta(payload: JSONObject): XpMeta? {
        val id = payload.optString("id")
        val name = payload.optString("name")
        if (id.isBlank() || name.isBlank()) return null
        return XpMeta(
            id, payload.optString("type", "movie"), name,
            payload.optString("poster").takeIf(String::isNotBlank),
            payload.optString("description").takeIf(String::isNotBlank),
            payload.optString("year").takeIf(String::isNotBlank),
            payload.optString("rating").takeIf(String::isNotBlank),
            payload.optLong("positionMs", 0L), payload.optLong("durationMs", 0L),
        )
    }

    private fun itemJson(item: XpMeta) = JSONObject()
        .put("id", item.id).put("type", item.type).put("name", item.name)
        .put("poster", item.poster ?: "").put("description", item.description ?: "")
        .put("year", item.releaseInfo ?: "").put("rating", item.rating ?: "")
        .put("positionMs", item.positionMs).put("durationMs", item.durationMs)

    private fun titleKey(type: String, id: String) = "$type:$id"
    private fun recordId(type: String, key: String) = "$type::$key"
    private fun dirtyKey(profileId: Long, type: String, key: String) = "$profileId::$type::$key"
    private data class Dirty(val type: String, val key: String, val payload: JSONObject)
    private data class Record(val type: String, val key: String, val payload: JSONObject, val version: Int)
}
