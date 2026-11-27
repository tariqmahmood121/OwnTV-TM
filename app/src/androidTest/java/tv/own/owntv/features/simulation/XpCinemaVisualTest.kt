package tv.own.owntv.features.simulation

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import tv.own.owntv.R
import tv.own.owntv.core.theme.AccentColor
import tv.own.owntv.core.theme.ThemeMode
import tv.own.owntv.features.home.StremioCatalogScreen
import tv.own.owntv.features.home.XpExitConfirmation
import tv.own.owntv.ui.theme.OwnTVTheme
import java.io.File

class XpCinemaVisualTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun captureCinemaHomeCategoryAndExit() {
        val activity = compose.activity
        val moreInfo = activity.getString(R.string.movioxp_cinema_more_info)
        val action = activity.getString(R.string.movioxp_home_action)
        val exploreAction = activity.getString(R.string.movioxp_explore_collection, action)
        compose.setContent {
            var exit by remember { mutableStateOf(false) }
            val simulation = remember { XpTvSimulation(activity).apply { onLongBack = { exit = true } } }
            OwnTVTheme(ThemeMode.DARK, AccentColor.VIOLET, systemInDarkTheme = true) {
                XpTvSimulationViewport(activity, simulation, XpSimulatedScale(), onExit = {}) {
                    StremioCatalogScreen(onBack = {}, onPlayStream = { _, _, _, _, _, _, _ -> })
                    if (exit) XpExitConfirmation(onCancel = { exit = false }, onConfirm = {})
                }
            }
        }
        fun awaitText(text: String) = compose.waitUntil(90_000) {
            compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
        awaitText(moreInfo)
        compose.onNodeWithText(moreInfo).assertIsDisplayed()
        saveScreenshot("01-cinema-home")
        saveScreenshot("05-simulator-remote")

        val home = compose.onNodeWithTag("cinema-home-list")
        home.performScrollToNode(hasText(action))
        val actionShelf = compose.onNodeWithTag("home-collection:xp_action")
        compose.waitUntil(90_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasTestTag("home-collection:xp_action"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        actionShelf.performScrollToNode(hasText(exploreAction))
        compose.onNodeWithText(exploreAction).assertIsDisplayed()
        saveScreenshot("02-explore-more")
        compose.onNodeWithText(exploreAction).performClick()

        val grid = compose.onNodeWithTag("cinema-category-grid")
        compose.waitUntil(90_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasTestTag("cinema-category-grid"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(action).assertIsDisplayed()
        SystemClock.sleep(1_500)
        saveScreenshot("03-action-category")
        grid.performScrollToIndex(14)
        compose.onNodeWithText(action).assertIsDisplayed()

        val back = compose.onNodeWithContentDescription(activity.getString(R.string.common_back))
        back.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(2_200)
        SystemClock.sleep(2_200)
        back.performTouchInput { up() }
        compose.onNodeWithText(activity.getString(R.string.movioxp_exit_confirm)).assertIsDisplayed()
        saveScreenshot("04-exit-confirmation")
        back.performTouchInput { click() }
        compose.onNodeWithText(action).assertIsDisplayed()
    }

    private fun saveScreenshot(name: String) {
        compose.waitForIdle()
        SystemClock.sleep(1_300)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val scaled = Bitmap.createScaledBitmap(screenshot, 1200, screenshot.height * 1200 / screenshot.width, true)
        val output = File(instrumentation.targetContext.filesDir, "cinema-visual").apply { mkdirs() }
        File(output, "$name.jpg").outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 84, it) }
        if (scaled !== screenshot) scaled.recycle()
        screenshot.recycle()
    }
}
