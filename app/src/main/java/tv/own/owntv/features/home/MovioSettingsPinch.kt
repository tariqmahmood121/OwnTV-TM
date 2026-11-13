package tv.own.owntv.features.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/** Two-finger pinch over Settings adjusts existing UI Zoom by one 5% step on RELEASE.
 * Single-finger scrolling and taps remain normal. Scaling is not animated or re-saved on each frame.
 */
fun Modifier.moviotvSettingsPinch(enabled: Boolean, onZoomStep: (Int) -> Unit): Modifier =
    if (!enabled) this else this.pointerInput(enabled, onZoomStep) {
        awaitEachGesture {
            var ratio = 1f
            var hadTwo = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } >= 2) {
                    hadTwo = true
                    val change = event.calculateZoom()
                    if (change.isFinite() && change > 0f) ratio = (ratio * change).coerceIn(0.3f, 3f)
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
            if (hadTwo) when {
                ratio >= 1.12f -> onZoomStep(5)
                ratio <= 0.88f -> onZoomStep(-5)
            }
        }
    }
