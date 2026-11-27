package tv.own.owntv.features.simulation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color

internal val LocalXpSimulationOverlay = staticCompositionLocalOf<XpSimulationOverlay?> { null }

internal class XpSimulationOverlay {
    val panels = mutableStateListOf<@Composable () -> Unit>()
}

@Composable
internal fun XpSimulationOverlayContent(overlay: XpSimulationOverlay) {
    overlay.panels.lastOrNull()?.invoke()
}

@Composable
internal fun XpSimulationDialog(
    overlay: XpSimulationOverlay,
    onDismiss: () -> Unit,
    content: @Composable (FocusRequester) -> Unit,
) {
    val dismiss = rememberUpdatedState(onDismiss)
    val currentContent = rememberUpdatedState(content)
    val panel: @Composable () -> Unit = remember {
        {
            val focus = remember { FocusRequester() }
            BackHandler { dismiss.value() }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .68f))
                    .focusProperties { canFocus = false }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        dismiss.value()
                    })
                Box(Modifier.focusProperties { onExit = { cancelFocusChange() } }.focusGroup()) {
                    currentContent.value(focus)
                }
            }
            LaunchedEffect(focus) {
                withFrameNanos { }
                focus.requestFocus()
            }
        }
    }
    DisposableEffect(overlay, panel) {
        overlay.panels.add(panel)
        onDispose { overlay.panels.remove(panel) }
    }
}
