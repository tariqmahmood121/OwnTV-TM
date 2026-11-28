package tv.own.owntv.features.simulation

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import tv.own.owntv.features.home.LocalXpFeaturedSourceCheck
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.features.home.XpContinueStore
import tv.own.owntv.features.home.catalogueProfileKey
import tv.own.owntv.features.home.XpCinemaMetadata
import tv.own.owntv.features.home.XpCinemaFont
import tv.own.owntv.features.home.XpMeta
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.tv.material3.ProvideTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.test.assertIsFocused
import tv.own.owntv.features.profiles.ProfilesViewModel
import java.io.File
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
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
        var showBadgeFixtures by mutableStateOf(false)
        var profileScope = ""
        val coldStarted = SystemClock.elapsedRealtime()
        compose.setContent {
            val profiles: ProfilesViewModel = koinViewModel()
            var seeded by remember { mutableStateOf(false) }
            LaunchedEffect(profiles) {
                profileScope = catalogueProfileKey(null, profiles.activeProfileId.value)
                XpContinueStore.updateProgress(activity, profiles.activeProfileId.value, "tt0133093", "movie",
                    "The Matrix", "https://images.metahub.space/poster/medium/tt0133093/img", "1999", 900_000L, 8_160_000L)
                seeded = true
            }
            var exit by remember { mutableStateOf(false) }
            val simulation = remember { XpTvSimulation(activity).apply { onLongBack = { exit = true } } }
            OwnTVTheme(ThemeMode.DARK, AccentColor.VIOLET, systemInDarkTheme = true) {
                XpTvSimulationViewport(activity, simulation, XpSimulatedScale(), onExit = {}) {
                    if (seeded && mountHome) CompositionLocalProvider(LocalXpFeaturedSourceCheck provides { true }) {
                        StremioCatalogScreen(onBack = {}, onPlayStream = { _, _, _, _, _, _, _ -> })
                    }
                    if (showBadgeFixtures) ProvideTextStyle(TextStyle(fontFamily = XpCinemaFont.MONTSERRAT.family)) {
                        Column(Modifier.fillMaxWidth().padding(32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            listOf("U", "PG", "12", "12A", "15", "18").forEach { certificate ->
                                val reference = XpMeta("visual-fixture", "movie", "", null,
                                    "Sample synopsis used to check the native Cinema component at emulator scale.",
                                    "2019", "6.7", ratingSource = "IMDb", genre = "Thriller",
                                    ukCertificate = certificate, runtime = "109")
                                XpCinemaMetadata(reference, details = null, showYear = true, synopsisLines = 2)
                            }
                        }
                    }
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

        fun pressRemote(label: Int) =
            compose.onNodeWithContentDescription(activity.getString(label)).performClick()
        pressRemote(R.string.movioxp_sim_down)
        val home = compose.onNodeWithTag("cinema-home-list")
        home.performScrollToIndex(1)
        val matrix = compose.onNodeWithContentDescription("The Matrix")
        matrix.performSemanticsAction(SemanticsActions.RequestFocus)
        matrix.assertIsFocused()
        compose.mainClock.advanceTimeBy(800)
        val expandedCard = "cinema-expanded-card-metadata"
        compose.waitUntil(10_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasTestTag(expandedCard), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(expandedCard, useUnmergedTree = true).assertIsDisplayed()
        saveScreenshot("11-focused-shelf-card")
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command: String) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
        }
        pressRemote(R.string.movioxp_sim_ok)
        val play = activity.getString(R.string.movioxp_play_content)
        val sources = activity.getString(R.string.movioxp_find_sources)
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText(play) or hasText(sources)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodes(hasText(play) or hasText(sources))[0].assertIsFocused()
        saveScreenshot("12-single-ok-detail")
        pressRemote(R.string.common_back)
        home.performScrollToNode(hasText(action))
        val actionShelf = compose.onNodeWithTag("home-collection:xp_action")
        compose.waitUntil(90_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasTestTag("home-collection:xp_action"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        val shelfCards = compose.onAllNodes(hasAnyAncestor(hasTestTag("home-collection:xp_action")) and
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription) and
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Focused))
        val rightmost = shelfCards.fetchSemanticsNodes().mapIndexed { index, node -> index to node.boundsInRoot.right }
            .maxBy { it.second }.first
        shelfCards[rightmost].performSemanticsAction(SemanticsActions.RequestFocus)
        compose.mainClock.advanceTimeBy(800)
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag(expandedCard), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(expandedCard, useUnmergedTree = true).assertIsDisplayed()
        saveScreenshot("16-right-edge-focus")
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
        fun rail(destination: Int) {
            compose.onNodeWithContentDescription(activity.getString(destination), useUnmergedTree = true).performClick()
        }
        rail(R.string.moviotv_xp_nav_search)
        awaitText(activity.getString(R.string.movioxp_cinema_browse))
        saveScreenshot("07-cinema-search")
        rail(R.string.moviotv_xp_nav_discover)
        val selector = compose.onNodeWithTag("cinema-category-selector")
        selector.assertIsDisplayed()
        selector.performScrollToNode(hasText(action))
        saveScreenshot("08-discover-selector")
        compose.onNodeWithText(action).performClick()
        compose.onNodeWithTag("cinema-category-grid").assertIsDisplayed()
        compose.onNodeWithText(action).assertIsDisplayed()
        saveScreenshot("09-discover-grid")
        rail(R.string.moviotv_xp_nav_search)
        awaitText(activity.getString(R.string.movioxp_cinema_all_titles))
        compose.onNodeWithText("a").assertIsDisplayed()
        compose.onNodeWithText(activity.getString(R.string.movioxp_cinema_delete)).assertIsDisplayed()
        saveScreenshot("13-context-search")
        compose.onNodeWithText(activity.getString(R.string.movioxp_cinema_all_titles)).performClick()
        back.performTouchInput { click() }
        compose.onNodeWithText(action).assertIsDisplayed()
        back.performTouchInput { click() }
        selector.assertIsDisplayed()
        rail(R.string.moviotv_nav_series)
        val allSeries = activity.getString(R.string.moviotv_xp_all_series)
        selector.assertIsDisplayed()
        awaitText(allSeries)
        compose.onNodeWithText(allSeries).performClick()
        compose.onNodeWithTag("cinema-category-grid").assertIsDisplayed()
        compose.waitUntil(25_000) {
            compose.onAllNodes(hasAnyAncestor(hasTestTag("cinema-category-grid")) and
                SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        saveScreenshot("10-series-grid")
        compose.onNodeWithTag("cinema-profile", useUnmergedTree = true).performClick()
        compose.onNodeWithText(activity.getString(R.string.moviotv_xp_nav_settings)).performClick()
        compose.onNodeWithText(activity.getString(R.string.movioxp_cinema_appearance)).performScrollTo().performClick()
        val font = compose.onNodeWithText(activity.getString(R.string.movioxp_font_inter))
        font.performScrollTo().performClick()
        check(activity.getSharedPreferences("movioxp_feature_settings", 0).getString("$profileScope|cinema-font", null) == "INTER")
        compose.onNodeWithTag("cinema-font-preview").performScrollTo().assertIsDisplayed()
        saveScreenshot("14-font-preview")
        compose.onNodeWithText(activity.getString(R.string.movioxp_cinema_restore_font)).performScrollTo().performClick()
        check(activity.getSharedPreferences("movioxp_feature_settings", 0).getString("$profileScope|cinema-font", null) == "MONTSERRAT")

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
        compose.runOnIdle {
            mountHome = false
            showBadgeFixtures = true
        }
        compose.onNodeWithText("12A").assertIsDisplayed()
        saveScreenshot("15-native-age-badge-fixtures")
    }

    @Test
    fun touchHomeRespondsToAvailableWidthWithOneNavigationBar() {
        val activity = compose.activity
        var expanded by mutableStateOf(false)
        compose.setContent {
            val original = androidx.compose.ui.platform.LocalConfiguration.current
            val density = androidx.compose.ui.platform.LocalDensity.current
            val configuration = android.content.res.Configuration(original).apply {
                uiMode = (uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK.inv()) or
                    android.content.res.Configuration.UI_MODE_TYPE_NORMAL
                screenWidthDp = if (expanded) 720 else 390
            }
            OwnTVTheme(ThemeMode.DARK, AccentColor.VIOLET, systemInDarkTheme = true) {
                CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalConfiguration provides configuration,
                    androidx.compose.ui.platform.LocalDensity provides
                        androidx.compose.ui.unit.Density(density.density * if (expanded) .58f else 1f, density.fontScale),
                    LocalXpFeaturedSourceCheck provides { true },
                ) {
                    StremioCatalogScreen(onBack = {}, onPlayStream = { _, _, _, _, _, _, _ -> })
                }
            }
        }
        fun awaitHero() = compose.waitUntil(90_000) {
            compose.onAllNodes(hasTestTag("touch-home-feature"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        val navSettings = activity.getString(R.string.moviotv_xp_nav_settings)
        awaitHero()
        check(compose.onAllNodes(hasText(navSettings)).fetchSemanticsNodes().size == 1)
        saveScreenshot("17-touch-home-compact")
        compose.runOnIdle { expanded = true }
        awaitHero()
        check(compose.onAllNodes(hasText(navSettings)).fetchSemanticsNodes().size == 1)
        saveScreenshot("18-touch-home-expanded")
        compose.onNodeWithText(navSettings).performClick()
        val maintenance = activity.getString(R.string.movioxp_maintenance_heading)
        compose.onNodeWithText(maintenance).performScrollTo().performClick()
        val diagnostics = activity.getString(R.string.movioxp_maintenance_diagnostics)
        compose.onNodeWithText(diagnostics).performScrollTo().performClick()
        compose.onNodeWithText(diagnostics).performScrollTo().assertIsDisplayed()
        saveScreenshot("19-storage-maintenance")
        val updates = File(activity.filesDir, "updates").apply { mkdirs() }
        val installedCode = tv.own.owntv.BuildConfig.VERSION_CODE
        val obsolete = File(updates, "movioxp-${installedCode - 1}.apk").apply {
            writeText("obsolete fixture")
            check(setLastModified(System.currentTimeMillis() - 172_800_000L))
        }
        val pending = File(updates, "movioxp-${installedCode + 1}.pending.apk").apply { writeText("pending fixture") }
        val before = kotlinx.coroutines.runBlocking { tv.own.owntv.features.home.XpMaintenance.snapshot(activity) }
        compose.onNodeWithText(activity.getString(R.string.movioxp_maintenance_optimise)).performScrollTo().performClick()
        compose.waitUntil(10_000) {
            !obsolete.exists() && activity.getSharedPreferences("movioxp_maintenance", 0).getLong("last", 0) > 0
        }
        check(pending.isFile)
        pending.delete()
        val after = kotlinx.coroutines.runBlocking { tv.own.owntv.features.home.XpMaintenance.snapshot(activity) }
        check(after.last > 0)
        File(activity.filesDir, "cinema-visual").resolve("maintenance.txt").writeText(
            "emulator_ram_bytes=${after.ram}\nfree_storage_bytes=${after.free}\n" +
                "java_heap_used_bytes=${after.heap}\nimage_disk_bytes=${after.pictures}\n" +
                "catalogue_disk_bytes=${after.catalogues}\nupdate_disk_before_bytes=${before.updates}\n" +
                "update_disk_after_bytes=${after.updates}\n",
        )
    }

    @Test
    fun forceStoppedProcessRestoresCataloguesAndLibraryOffline() {
        val activity = compose.activity
        val started = SystemClock.elapsedRealtime()
        compose.setContent {
            val simulation = remember { XpTvSimulation(activity) }
            OwnTVTheme(ThemeMode.DARK, AccentColor.VIOLET, systemInDarkTheme = true) {
                XpTvSimulationViewport(activity, simulation, XpSimulatedScale(), onExit = {}) {
                    StremioCatalogScreen(onBack = {}, onPlayStream = { _, _, _, _, _, _, _ -> })
                }
            }
        }
        val connectivity = activity.getSystemService(android.net.ConnectivityManager::class.java)
        check(connectivity?.activeNetwork == null)
        val action = activity.getString(R.string.movioxp_home_action)
        val home = compose.onNodeWithTag("cinema-home-list")
        home.performScrollToNode(hasText(action))
        compose.waitUntil(25_000) {
            compose.onAllNodes(hasTestTag("home-collection:xp_action")).fetchSemanticsNodes().isNotEmpty()
        }
        val elapsed = SystemClock.elapsedRealtime() - started
        File(activity.filesDir, "cinema-visual").resolve("force-stop.txt").writeText("offline_force_stop_home_ms=$elapsed\n")

        fun rail(destination: Int) {
            compose.onNodeWithContentDescription(activity.getString(destination), useUnmergedTree = true).performClick()
        }
        rail(R.string.moviotv_xp_nav_discover)
        val selector = compose.onNodeWithTag("cinema-category-selector")
        selector.performScrollToNode(hasText(action))
        compose.onNodeWithText(action).performClick()
        compose.waitUntil(25_000) {
            compose.onAllNodes(hasAnyAncestor(hasTestTag("cinema-category-grid")) and
                SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        saveScreenshot("20-offline-after-force-stop")
        rail(R.string.movioxp_my_library)
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText(activity.getString(R.string.moviotv_xp_continue_watching)))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun saveScreenshot(name: String) {
        compose.waitForIdle()
        SystemClock.sleep(1_300)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot(compose.activity.window))
        val scaled = Bitmap.createScaledBitmap(screenshot, 1200, screenshot.height * 1200 / screenshot.width, true)
        val output = File(instrumentation.targetContext.filesDir, "cinema-visual").apply { mkdirs() }
        File(output, "$name.jpg").outputStream().use {
            check(scaled.compress(Bitmap.CompressFormat.JPEG, 84, it))
        }
        if (scaled !== screenshot) scaled.recycle()
        screenshot.recycle()
    }
}
