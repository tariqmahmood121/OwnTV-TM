package tv.own.owntv.features.home

import android.content.Context
import java.security.MessageDigest

/**
 * Preserve the source that the viewer CHOSE, independently of the automatic candidate
 * selected for playback or a temporary network fallback. Never persist raw stream URLs.
 */
internal object XpSourcePreference {
    private const val STORE = "movioxp_playback"
    private const val PREFIX = "v3:"
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

    fun score(saved: String?, candidate: XpPlaybackSource): Int {
        if (saved.isNullOrBlank()) return 2
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

    fun read(context: Context, profileId: Long, contentKey: String): String? =
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            .getString(key(profileId, contentKey), null)

    /** Only call on an explicit user source choice; auto-start/failover must not call this. */
    fun store(context: Context, profileId: Long, contentKey: String, source: XpPlaybackSource) {
        if (profileId <= 0) return
        val storageKey = key(profileId, contentKey)
        val value = preference(source)
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            .edit().putString(storageKey, value).apply()
        XpCentralDataSync.markSetting(context, profileId, storageKey, value)
    }
}
