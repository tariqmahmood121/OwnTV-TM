package tv.own.owntv.features.home

import android.content.Context
import java.security.MessageDigest

/**
 * A stream preference must identify a specific playable entry, not just its add-on's display name.
 * Do not store signed streams, access tokens or resolver URLs in Central preferences.
 */
internal object XpSourcePreference {
    private const val STORE = "movioxp_playback"
    private const val PREFIX = "v2:"

    fun key(profileId: Long, contentKey: String): String =
        "last_source_${profileId}_${contentKey.hashCode()}"

    fun identity(addonUrl: String, streamLabel: String, detail: String?): String {
        val normalized = listOf(addonUrl.trim(), streamLabel.trim(), detail.orEmpty().trim())
            .joinToString(separator = "\u001f")
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
        return bytes.take(16).joinToString(separator = "") { "%02x".format(it) }
    }

    fun preference(source: XpPlaybackSource): String =
        if (source.identity.isNotBlank()) "$PREFIX${source.identity}:${source.provider}"
        else source.provider

    fun score(saved: String?, candidate: XpPlaybackSource): Int {
        if (saved.isNullOrBlank()) return 2
        if (saved.startsWith(PREFIX)) {
            val after = saved.removePrefix(PREFIX)
            val storedIdentity = after.substringBefore(':')
            val storedProvider = after.substringAfter(':', "")
            return when {
                candidate.identity.isNotBlank() && storedIdentity == candidate.identity -> 0
                storedProvider == candidate.provider -> 1
                else -> 2
            }
        }
        return if (candidate.provider == saved) 1 else 2
    }

    fun read(context: Context, profileId: Long, contentKey: String): String? =
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            .getString(key(profileId, contentKey), null)

    fun store(context: Context, profileId: Long, contentKey: String, source: XpPlaybackSource) {
        if (profileId <= 0) return
        val storageKey = key(profileId, contentKey)
        val value = preference(source)
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            .edit().putString(storageKey, value).apply()
        XpCentralDataSync.markSetting(context, profileId, storageKey, value)
    }
}
