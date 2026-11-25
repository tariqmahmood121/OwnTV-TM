package tv.own.owntv.features.shell

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import tv.own.owntv.features.home.XpCentralDataSync
import tv.own.owntv.features.home.XpContinueStore
import tv.own.owntv.features.home.XpFavoritesStore
import tv.own.owntv.features.home.XpPlaybackSource
import tv.own.owntv.features.home.XpSourcePreference
import tv.own.owntv.player.MpvPlaybackEngine
import tv.own.owntv.player.OwnTVPlayer

/**
 * Keep MovioXP bookkeeping outside OwnTVShell's Compose-generated method.
 * Huge Compose shell methods can compile, but fail ART's verifier on Android 17.
 */
internal data class MovioXpPlayback(
    val id: String,
    val type: String,
    val title: String,
    val year: String?,
    val poster: String?,
    val profileId: Long,
    val favorite: Boolean = false,
)

internal fun movioXpContentKey(media: MovioXpPlayback): String =
    "stremio:${media.type}:${media.id}"

internal suspend fun seekMovioXpWhenReady(
    player: OwnTVPlayer,
    engine: MpvPlaybackEngine,
    contentKey: String,
    requestedPositionMs: Long,
) {
    if (requestedPositionMs <= 0L) return
    val duration = withTimeoutOrNull(35_000L) {
        player.currentMeta.first { it?.contentKey == contentKey }
        player.duration.first { it > 0L }
    } ?: return
    delay(1_500L)
    if (player.currentMeta.value?.contentKey != contentKey) return
    val target = requestedPositionMs.coerceAtMost((duration - 3_000L).coerceAtLeast(0L))
    val actual = player.position.value.coerceAtLeast(0L)
    // mpv seekBy is RELATIVE. Do not use the desired absolute playhead as the delta.
    if (target > 0L && actual + 8_000L < target) engine.seekBy(target - actual)
}

@Composable
internal fun MovioXpPlaybackEffects(
    context: Context,
    player: OwnTVPlayer,
    contentKey: String?,
    playback: MovioXpPlayback?,
    candidateGeneration: Int,
    candidateIndex: Int,
    source: XpPlaybackSource?,
    activeProfileId: Long?,
) {
    val preferences = remember(context) {
        context.getSharedPreferences("movioxp_playback", Context.MODE_PRIVATE)
    }
    LaunchedEffect(contentKey, playback?.profileId, activeProfileId, candidateGeneration, candidateIndex, source) {
        val media = playback ?: return@LaunchedEffect
        val candidate = source ?: return@LaunchedEffect
        val playedKey = movioXpContentKey(media)
        if (activeProfileId != media.profileId || contentKey != playedKey) return@LaunchedEffect
        val evidence = XpPlaybackEvidence()
        repeat(120) {
            delay(400L)
            if (player.currentMeta.value?.contentKey != contentKey) return@LaunchedEffect
            if (evidence.sample(player.position.value, player.isPlaying.value,
                    player.buffering.value, player.error.value != null)) {
                XpSourcePreference.store(context, media.profileId, playedKey, candidate)
                val suffix = "${media.profileId}_${playedKey.hashCode()}"
                // An existing choice is restored by the track effect below. An observed
                // default must never replace the user's explicit preference.
                if (!preferences.contains("xp_audio_$suffix")) {
                    player.audioTracks().firstOrNull { it.selected }?.let {
                        saveMovioXpAudioPreference(context, player, media, it.mpvId)
                    }
                }
                if (!preferences.contains("xp_subtitle_$suffix")) {
                    val subtitles = player.textTracks()
                    if (subtitles.isNotEmpty()) saveMovioXpSubtitlePreference(
                        context, player, media, subtitles.firstOrNull { it.selected }?.mpvId)
                }
                return@LaunchedEffect
            }
        }
    }
    LaunchedEffect(contentKey, playback?.profileId, candidateGeneration, candidateIndex) {
        val media = playback ?: return@LaunchedEffect
        if (contentKey != movioXpContentKey(media)) return@LaunchedEffect
        val key = "${media.profileId}_${contentKey.hashCode()}"
        val audioId = preferences.getInt("xp_audio_$key", -1)
        val subtitleId = preferences.getInt("xp_subtitle_$key", -2)
        val audioLang = preferences.getString("xp_audio_lang_$key", null)?.takeIf(String::isNotBlank)
        val subtitleLang = preferences.getString("xp_subtitle_lang_$key", null)?.takeIf(String::isNotBlank)
        if (audioId < 0 && audioLang == null && subtitleId == -2 && subtitleLang == null) return@LaunchedEffect
        repeat(60) {
            delay(500L)
            if (player.currentMeta.value?.contentKey != contentKey) return@LaunchedEffect
            val audioTracks = player.audioTracks()
            val subtitleTracks = player.textTracks()
            val audioMatch = audioLang?.let { lang -> audioTracks.firstOrNull { it.lang.equals(lang, true) } }
                ?: if (audioLang == null) audioTracks.firstOrNull { it.mpvId == audioId } else null
            val subtitleMatch = subtitleLang?.let { lang -> subtitleTracks.firstOrNull { it.lang.equals(lang, true) } }
                ?: if (subtitleLang == null) subtitleTracks.firstOrNull { it.mpvId == subtitleId } else null
            audioMatch?.let { player.selectAudio(it.mpvId) }
            if (subtitleId == -1) player.disableSubtitles()
            else subtitleMatch?.let { player.selectSubtitle(it.mpvId) }
            val audioReady = (audioId < 0 && audioLang == null) || audioMatch != null ||
                (audioLang != null && audioTracks.isNotEmpty())
            val subtitleReady = (subtitleId == -2 && subtitleLang == null) || subtitleId == -1 ||
                subtitleMatch != null || (subtitleLang != null && subtitleTracks.isNotEmpty())
            if (audioReady && subtitleReady) return@LaunchedEffect
        }
    }
    LaunchedEffect(playback) {
        val media = playback ?: return@LaunchedEffect
        val key = movioXpContentKey(media)
        var observedAt = android.os.SystemClock.elapsedRealtime()
        var observedPosition = -1L
        var wasPlaying = false
        var lastLocalSaveAt = observedAt
        var pendingSeekUntil = 0L
        var completed = false

        fun saveProgress(upload: Boolean) {
            if (player.currentMeta.value?.contentKey != key || player.error.value != null) return
            val duration = player.duration.value
            val position = player.position.value
            if (position <= 0L || duration <= 0L) return
            XpContinueStore.updateProgress(
                context, media.profileId, media.id, media.type, media.title,
                media.poster, media.year, position, duration,
            )
            if (upload) tv.own.owntv.features.home.XpCentralEventSync.request(context, media.profileId)
        }

        while (true) {
            delay(500L)
            val now = android.os.SystemClock.elapsedRealtime()
            if (player.currentMeta.value?.contentKey != key) {
                observedPosition = -1L
                wasPlaying = false
                observedAt = now
                continue
            }
            val position = player.position.value.coerceAtLeast(0L)
            val duration = player.duration.value
            val playing = player.isPlaying.value
            if (observedPosition >= 0L && duration > 0L && position > 0L) {
                val elapsed = (now - observedAt).coerceAtLeast(0L)
                val expectedAdvance = if (wasPlaying) elapsed else 0L
                val unexpectedJump = kotlin.math.abs(position - observedPosition - expectedAdvance)
                // A jump substantially beyond normal playback represents a manual seek.
                // Reset this 3-second deadline on every further scrub.
                if (unexpectedJump > 4_500L) pendingSeekUntil = now + 3_000L
                if (wasPlaying && !playing) {
                    saveProgress(upload = true)
                    pendingSeekUntil = 0L
                }
                if (pendingSeekUntil > 0L && now >= pendingSeekUntil) {
                    saveProgress(upload = true)
                    pendingSeekUntil = 0L
                }
            }
            if (!completed && duration > 0L && position >= duration * 95L / 100L) {
                saveProgress(upload = true)
                completed = true
            }
            if (now - lastLocalSaveAt >= 5_000L) {
                saveProgress(upload = false)
                lastLocalSaveAt = now
            }
            observedPosition = position
            observedAt = now
            wasPlaying = playing
        }
    }
}

/** Both resume and content identity belong to the playback service, not the Compose tree. */
internal fun startMovioXpStream(
    context: Context,
    player: OwnTVPlayer,
    engine: MpvPlaybackEngine,
    scope: CoroutineScope,
    profileId: Long,
    url: String,
    title: String,
    type: String,
    year: String?,
    id: String,
    poster: String?,
    requestedResume: Long,
): MovioXpPlayback {
    val media = MovioXpPlayback(
        id, type, title, year, poster, profileId,
        favorite = XpFavoritesStore.isFavorite(context, profileId, type, id),
    )
    val latestSaved = XpContinueStore.read(context, profileId)
        .firstOrNull { it.id == id && it.type == type }?.positionMs ?: 0L
    val startAt = requestedResume.takeIf { it > 0L } ?: latestSaved
    val key = movioXpContentKey(media)
    player.play(
        url = url, title = title, year = year,
        contentKey = key, startPositionMs = startAt,
    )
    if (startAt > 0L) scope.launch { seekMovioXpWhenReady(player, engine, key, startAt) }
    return media
}

internal class XpPlaybackEvidence {
    private var previous: Long? = null
    private var advances = 0

    fun sample(position: Long, playing: Boolean, buffering: Boolean, failed: Boolean): Boolean {
        if (!playing || buffering || failed || position < 0L) {
            previous = null
            advances = 0
            return false
        }
        val before = previous
        previous = position
        if (before == null || position <= before || position - before > 2_000L) {
            advances = 0
            return false
        }
        return ++advances >= 2
    }
}

internal fun saveMovioXpAudioPreference(
    context: Context, player: OwnTVPlayer, media: MovioXpPlayback, id: Int,
) {
    val suffix = "${media.profileId}_${movioXpContentKey(media).hashCode()}"
    val language = player.audioTracks().firstOrNull { it.mpvId == id }?.lang.orEmpty()
    val idKey = "xp_audio_$suffix"
    val langKey = "xp_audio_lang_$suffix"
    context.getSharedPreferences("movioxp_playback", Context.MODE_PRIVATE)
        .edit().putInt(idKey, id).putString(langKey, language).apply()
    XpCentralDataSync.markSetting(context, media.profileId, idKey, id)
    XpCentralDataSync.markSetting(context, media.profileId, langKey, language)
}

internal fun saveMovioXpSubtitlePreference(
    context: Context, player: OwnTVPlayer, media: MovioXpPlayback, id: Int?,
) {
    val suffix = "${media.profileId}_${movioXpContentKey(media).hashCode()}"
    val language = id?.let { track -> player.textTracks().firstOrNull { it.mpvId == track }?.lang }.orEmpty()
    val idKey = "xp_subtitle_$suffix"
    val langKey = "xp_subtitle_lang_$suffix"
    context.getSharedPreferences("movioxp_playback", Context.MODE_PRIVATE)
        .edit().putInt(idKey, id ?: -1).putString(langKey, language).apply()
    XpCentralDataSync.markSetting(context, media.profileId, idKey, id ?: -1)
    XpCentralDataSync.markSetting(context, media.profileId, langKey, language)
}

internal fun flushMovioXpProgress(context: Context, player: OwnTVPlayer, media: MovioXpPlayback) {
    if (player.currentMeta.value?.contentKey != movioXpContentKey(media) || player.error.value != null) return
    XpContinueStore.updateProgress(
        context, media.profileId, media.id, media.type, media.title,
        media.poster, media.year,
        player.position.value.coerceAtLeast(0L), player.duration.value.coerceAtLeast(0L),
    )
    tv.own.owntv.features.home.XpCentralEventSync.request(context, media.profileId)
}
