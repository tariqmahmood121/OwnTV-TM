package tv.own.owntv.features.home

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

internal val LocalXpTvControls = staticCompositionLocalOf { false }
internal val XpRemotePink = Color(0xFFFF55BD)

internal class XpRemoteHold {
    enum class Direction { TOP, BOTTOM }
    enum class Release { IGNORE, STEP, CONSUME }

    private var active: Direction? = null
    private var startedAt = 0L
    private var triggered = false

    fun start(direction: Direction, time: Long): Boolean {
        if (active == direction) return false
        active = direction
        startedAt = time
        triggered = false
        return true
    }

    fun trigger(direction: Direction, time: Long): Boolean {
        if (active != direction || triggered || time - startedAt < DELAY) return false
        triggered = true
        return true
    }

    fun release(direction: Direction): Release {
        if (active != direction) return Release.IGNORE
        val result = if (triggered) Release.CONSUME else Release.STEP
        cancel()
        return result
    }

    fun cancel() {
        active = null
        triggered = false
    }

    companion object {
        const val DELAY = 2_000L
    }
}
