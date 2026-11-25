package tv.own.owntv.features.home

import android.content.Context
import java.security.MessageDigest
import org.json.JSONObject

/**
 * Persist confirmed playback by content and profile without storing stream URLs.
 */
internal object XpSourcePreference {
    private const val STORE = "movioxp_playback"
    private const val PREFIX = "v3:"
    private const val PLAYED_PREFIX = "v4:"
    private const val LEGACY_PREFIX = "v2:"

    fun key(profileId: Long, contentKey: String): String =
        "last_source_${profileId}_${contentKey.hashCode()}"

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .take(16).joinToString(separator = "") { "%02x".format(it) }

    /**
     * The add-on's detail field frequently includes transient size, availability and seeder
     * information, so basing the stored identity on it made a selected stream become
     * unrecognisable on the next catalogue lookup. Use add-on URL + stream label only.
     */
    fun identity(addonUrl: String, streamLabel: String): String =
        hash(addonUrl.trim() + "\u001f" + streamLabel.trim())

    /** Match source preferences written by 1.0.77 while the same stream detail still exists. */
    fun legacyIdentity(addonUrl: String, streamLabel: String, detail: String?): String =
        hash(listOf(addonUrl.trim(), streamLabel.trim(), detail.orEmpty().trim()).joinToString(separator = "\u001f"))

    fun preference(source: XpPlaybackSource): String =
        if (source.identity.isNotBlank()) "$PREFIX${source.identity}:${source.provider}"
        else source.provider

    fun playedPreference(contentKey: String, source: XpPlaybackSource): String =
        PLAYED_PREFIX + JSONObject().put("content", contentKey)
            .put("source", source.stableIdentity.ifBlank { source.identity })
            .put("addon", source.addonIdentity)
            .put("provider", safeLabel(source.provider))
            .put("quality", safeLabel(source.quality.orEmpty()))
            .put("release", safeLabel(source.releaseName ?: source.label)).toString()

    private fun safeLabel(value: String): String = value.replace(Regex("https?://\\S+", RegexOption.IGNORE_CASE), "").take(240).trim()

    fun stableIdentity(addonUrl: String, label: String, release: String?, providerId: String?): String =
        identity(addonUrl, providerId?.takeIf(String::isNotBlank) ?: listOf(label, release.orEmpty()).joinToString("\u001f"))

    fun playedMatch(saved: String?, candidates: List<XpPlaybackSource>): XpPlaybackSource? =
        candidates.filter { score(saved, it) == 0 }.singleOrNull()

    fun score(saved: String?, candidate: XpPlaybackSource): Int {
        if (saved.isNullOrBlank()) return 2
        if (saved.startsWith(PLAYED_PREFIX)) {
            val record = runCatching { JSONObject(saved.removePrefix(PLAYED_PREFIX)) }.getOrNull() ?: return 2
            val identity = candidate.stableIdentity.ifBlank { candidate.identity }
            return if (identity.isNotBlank() && record.optString("source") == identity &&
                record.optString("addon") == candidate.addonIdentity) 0 else 2
        }
        val prefix = when {
            saved.startsWith(PREFIX) -> PREFIX
            saved.startsWith(LEGACY_PREFIX) -> LEGACY_PREFIX
            else -> null
        }
        if (prefix != null) {
            val after = saved.removePrefix(prefix)
            val storedIdentity = after.substringBefore(':')
            val storedProvider = after.substringAfter(':', "")
            val match = if (prefix == PREFIX) candidate.identity else candidate.legacyIdentity
            return when {
                match.isNotBlank() && storedIdentity == match -> 0
                storedProvider == candidate.provider -> 1
                else -> 2
            }
        }
        return if (candidate.provider == saved) 1 else 2
    }

    internal fun stableKey(profileId: Long, contentKey: String) = "last_source_${profileId}_${hash(contentKey)}"

    fun read(context: Context, profileId: Long, contentKey: String): String? {
        if (profileId <= 0) return null
        val store = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        val value = store.getString(stableKey(profileId, contentKey), null)
        if (value != null && runCatching {
                JSONObject(value.removePrefix(PLAYED_PREFIX)).optString("content") == contentKey
            }.getOrDefault(false)) return value
        return store.getString(key(profileId, contentKey), null)
    }

    fun store(context: Context, profileId: Long, contentKey: String, source: XpPlaybackSource) {
        if (profileId <= 0 || source.stableIdentity.isBlank() && source.identity.isBlank()) return
        val storageKey = stableKey(profileId, contentKey)
        val value = playedPreference(contentKey, source)
        val store = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        if (store.getString(storageKey, null) == value) return
        store.edit().putString(storageKey, value).apply()
        XpCentralDataSync.markSetting(context, profileId, storageKey, value)
    }
}
