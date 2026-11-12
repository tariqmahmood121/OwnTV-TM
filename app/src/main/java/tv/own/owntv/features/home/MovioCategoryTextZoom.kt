package tv.own.owntv.features.home

import android.content.Context
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    var zoom by remember(key) { mutableFloatStateOf(prefs.getFloat(key, 1f).coerceIn(1f, 1.85f)) }
    val base = LocalDensity.current
    Box(modifier.then(if (enabled) Modifier.pointerInput(key) {
        detectTransformGestures { _, _, amount, _ ->
            val next = (zoom * amount).coerceIn(1f, 1.85f)
            if (kotlin.math.abs(next - zoom) >= 0.004f) {
                zoom = next
                prefs.edit().putFloat(key, next).apply()
            }
        }
    } else Modifier)) {
        CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * zoom)) {
            content()
        }
    }
}
