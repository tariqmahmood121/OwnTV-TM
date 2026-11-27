package tv.own.owntv.features.simulation

import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import tv.own.owntv.R

class XpSimulationRemoteTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun touchRemoteNavigatesTheComposeFocusTreeAndDismissesAnInViewportOverlay() {
        val activity = compose.activity
        var selections = 0
        var backPresses = 0
        var directionRepeats = 0
        var longestSelect = 0L
        compose.setContent {
            val initialFocus = remember { FocusRequester() }
            val simulation = remember { XpTvSimulation(activity) }
            var modal by remember { mutableStateOf(false) }
            BackHandler { backPresses++ }
            XpTvSimulationViewport(activity, simulation, XpSimulatedScale(), onExit = {}) {
                val overlay = checkNotNull(LocalXpSimulationOverlay.current)
                Column(Modifier.onPreviewKeyEvent {
                    val event = it.nativeKeyEvent
                    if (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.action == KeyEvent.ACTION_UP) {
                        longestSelect = maxOf(longestSelect, event.eventTime - event.downTime)
                    }
                    if (event.repeatCount > 0 && event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        directionRepeats++
                        true
                    } else false
                }) {
                    Row {
                        Text("Top left", Modifier.size(120.dp).testTag("tl").focusRequester(initialFocus).clickable {})
                        Text("Top right", Modifier.size(120.dp).testTag("tr").clickable {})
                    }
                    Row {
                        Text("Bottom left", Modifier.size(120.dp).testTag("bl").clickable {})
                        Text("Bottom right", Modifier.size(120.dp).testTag("br").clickable {
                            selections++
                            modal = true
                        })
                    }
                }
                if (modal) {
                    XpSimulationDialog(overlay, { modal = false }) { close ->
                        Text("Dismiss", Modifier.size(140.dp).testTag("dialog").focusRequester(close)
                            .clickable { modal = false })
                    }
                }
                LaunchedEffect(Unit) { initialFocus.requestFocus() }
            }
        }
        fun press(label: Int) {
            compose.onNodeWithContentDescription(activity.getString(label)).performTouchInput { click() }
        }
        compose.onNodeWithTag("tl").assertIsFocused()
        press(R.string.movioxp_sim_right)
        compose.onNodeWithTag("tr").assertIsFocused()
        press(R.string.movioxp_sim_down)
        compose.onNodeWithTag("br").assertIsFocused()
        press(R.string.movioxp_sim_left)
        compose.onNodeWithTag("bl").assertIsFocused()
        press(R.string.movioxp_sim_up)
        compose.onNodeWithTag("tl").assertIsFocused()

        fun hold(label: Int, durationMs: Long) {
            val button = compose.onNodeWithContentDescription(activity.getString(label))
            button.performTouchInput { down(center) }
            SystemClock.sleep(durationMs)
            button.performTouchInput { up() }
        }
        hold(R.string.movioxp_sim_right, 850)
        compose.onNodeWithTag("tr").assertIsFocused()
        press(R.string.movioxp_sim_down)
        hold(R.string.movioxp_sim_ok, 2_200)
        compose.onNodeWithTag("dialog").assertIsFocused()
        press(R.string.common_back)
        compose.onNodeWithTag("dialog").assertDoesNotExist()
        press(R.string.common_back)
        compose.runOnIdle {
            assertEquals(1, selections)
            assertEquals(1, backPresses)
            check(longestSelect >= 2_000) { "Select duration: $longestSelect ms" }
            check(directionRepeats > 0) { "Held direction did not repeat" }
        }
    }
}
