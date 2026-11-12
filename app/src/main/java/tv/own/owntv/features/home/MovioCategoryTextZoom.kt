package tv.own.owntv.features.home

import android.content.Context
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.features.profiles.ProfilesViewModel

/** Two-finger pinch changes ONLY the text density inside the category panel. */
@Composable
fun MovioCategoryTextZoom(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val profiles: ProfilesViewModel = koinViewModel()
    val profileId by profiles.activeProfileId.collectAsStateWithLifecycle()
    val key = "category_font_" + (profileId ?: -1L)
    val prefs = remember(context) { context.getSharedPreferences("moviotv_category_fonts_v74", Context.MODE_PRIVATE) }
    var zoom by remember(key) { mutableFloatStateOf(prefs.getFloat(key, 1f).coerceIn(0.75f, 2.25f)) }
    val base = LocalDensity.current
    // V7.6 category pinch: separate from poster grid, works even over a scrolling list.
    Box(modifier.then(if (enabled) Modifier.pointerInput(key) {
        awaitEachGesture {
            var changed = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } >= 2) {
                    val amount = event.calculateZoom()
                    if (amount.isFinite() && amount > 0f) {
                        val next = (zoom * amount).coerceIn(0.75f, 2.25f)
                        if (kotlin.math.abs(next - zoom) >= 0.004f) {
                            zoom = next
                            changed = true
                        }
                    }
                    // Reserve the two-finger gesture for this rail, not list scrolling.
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
            if (changed) prefs.edit().putFloat(key, zoom).apply()
        }
    } else Modifier)) {
        CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * zoom)) {
            content()
        }
    }
}
