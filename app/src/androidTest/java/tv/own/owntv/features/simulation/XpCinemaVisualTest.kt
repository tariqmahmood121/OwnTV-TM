package tv.own.owntv.features.simulation

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.LaunchedEffect
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.features.home.XpContinueStore
import tv.own.owntv.features.profiles.ProfilesViewModel
import java.io.File
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

class XpCinemaVisualTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun captureCinemaHomeCategoryAndExit() {
        val activity = compose.activity
        compose.runOnUiThread {
            WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        val moreInfo = activity.getString(R.string.movioxp_cinema_more_info)
        val action = activity.getString(R.string.movioxp_home_action)
        val exploreAction = activity.getString(R.string.movioxp_explore_collection, action)
        var mountHome by mutableStateOf(true)
        val coldStarted = SystemClock.elapsedRealtime()
        compose.setContent {
            val profiles: ProfilesViewModel = koinViewModel()
            var seeded by remember { mutableStateOf(false) }
            LaunchedEffect(profiles) {
                XpContinueStore.updateProgress(activity, profiles.activeProfileId.value, "tt0133093", "movie",
                    "The Matrix", "https://images.metahub.space/poster/medium/tt0133093/img", "1999", 900_000L, 8_160_000L)
                seeded = true
            }
            var exit by remember { mutableStateOf(false) }
            val simulation = remember { XpTvSimulation(activity).apply { onLongBack = { exit = true } } }
            OwnTVTheme(ThemeMode.DARK, AccentColor.VIOLET, systemInDarkTheme = true) {
                XpTvSimulationViewport(activity, simulation, XpSimulatedScale(), onExit = {}) {
                    if (seeded && mountHome) StremioCatalogScreen(onBack = {}, onPlayStream = { _, _, _, _, _, _, _ -> })
                    if (exit) XpExitConfirmation(onCancel = { exit = false }, onConfirm = {})
                }
            }
        }
        fun awaitText(text: String) = compose.waitUntil(90_000) {
            compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
        fun awaitArtwork(timeout: Long) = compose.waitUntil(timeout) {
            compose.onAllNodes(androidx.compose.ui.test.hasTestTag("cinema-backdrop-loaded"), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        awaitText(moreInfo)
        awaitArtwork(90_000)
        val measurements = File(activity.filesDir, "cinema-visual").apply { mkdirs() }.resolve("timings.txt")
        measurements.writeText("cold_ms=${SystemClock.elapsedRealtime() - coldStarted}\n")
        compose.onNodeWithText(moreInfo).assertIsDisplayed()
        compose.onNodeWithText(activity.getString(R.string.moviotv_xp_continue_watching)).assertIsDisplayed()
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
        back.performTouchInput { click() }
        awaitText(exploreAction)
        compose.onNodeWithText(exploreAction).assertIsDisplayed()
        back.performTouchInput { click() }
        compose.onNodeWithTag("cinema-home-list").assertIsDisplayed()
        compose.onNodeWithText(activity.getString(R.string.movioxp_exit_confirm)).assertDoesNotExist()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command: String) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
        }
        shell("svc wifi disable")
        shell("svc data disable")
        try {
            compose.runOnIdle { mountHome = false }
            compose.onNodeWithTag("cinema-home-list").assertDoesNotExist()
            val connectivity = requireNotNull(activity.getSystemService(android.net.ConnectivityManager::class.java))
            compose.waitUntil(10_000) { connectivity.activeNetwork == null }
            coil3.SingletonImageLoader.get(activity).memoryCache?.clear()
            val warmStarted = SystemClock.elapsedRealtime()
            compose.runOnIdle { mountHome = true }
            awaitArtwork(12_000)
            measurements.appendText("warm_offline_ms=${SystemClock.elapsedRealtime() - warmStarted}\n")
            saveScreenshot("06-warm-offline-home")
        } finally {
            shell("svc wifi enable")
            shell("svc data enable")
        }

    }

    private fun saveScreenshot(name: String) {
        compose.waitForIdle()
        SystemClock.sleep(1_300)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val scaled = Bitmap.createScaledBitmap(screenshot, 1200, screenshot.height * 1200 / screenshot.width, true)
        val output = File(instrumentation.targetContext.filesDir, "cinema-visual").apply { mkdirs() }
        File(output, "$name.jpg").outputStream().use {
            check(scaled.compress(Bitmap.CompressFormat.JPEG, 84, it))
        }
        if (scaled !== screenshot) scaled.recycle()
        screenshot.recycle()
    }
}
