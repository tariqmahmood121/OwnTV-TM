package tv.own.owntv.features.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.features.shell.XpPlaybackEvidence

class XpSourcePreferenceTest {
    private fun source(addon: String, label: String, detail: String = "") =
        XpPlaybackSource(
            url = "https://example.org/stream",
            label = label,
            provider = "Addon",
            identity = XpSourcePreference.identity(addon, label),
            legacyIdentity = XpSourcePreference.legacyIdentity(addon, label, detail),
        )

    @Test
    fun userChosenSecondStreamBeatsFirstStreamFromSameAddon() {
        val first = source("https://example.org/manifest.json", "1080p")
        val second = source("https://example.org/manifest.json", "4K")
        val saved = XpSourcePreference.preference(second)
        assertEquals(0, XpSourcePreference.score(saved, second))
        assertEquals(1, XpSourcePreference.score(saved, first))
        assertEquals(second, listOf(first, second).minBy { XpSourcePreference.score(saved, it) })
    }

    @Test
    fun sourceIdentityRemainsStableWhenDynamicDetailsChange() {
        val addon = "https://example.org/manifest.json"
        val before = source(addon, "4K HEVC", "Seeds 18; Size 5.6GB")
        val after = source(addon, "4K HEVC", "Seeds 42; Size 5.8GB")
        assertEquals(before.identity, after.identity)
        assertNotEquals(before.legacyIdentity, after.legacyIdentity)
        assertEquals(0, XpSourcePreference.score(XpSourcePreference.preference(before), after))
    }

    @Test
    fun oldV2SourceChoiceStillRecognisedIfDetailsMatch() {
        val item = source("https://example.org/manifest.json", "4K", "size 6GB")
        val legacy = "v2:${item.legacyIdentity}:Addon"
        assertEquals(0, XpSourcePreference.score(legacy, item))
    }

    @Test
    fun missingSourceFallsBackToSameAddon() {
        val item = source("https://example.org/manifest.json", "1080p")
        val saved = "v3:00000000000000000000000000000000:Addon"
        assertEquals(1, XpSourcePreference.score(saved, item))
    }

    @Test
    fun userSelectionIsExplicitRatherThanAutomatic() {
        val item = source("https://example.org/manifest.json", "1080p")
        assertEquals(false, item.userSelected)
        assertEquals(true, item.copy(userSelected = true).userSelected)
    }

    @Test
    fun confirmedSourceMatchesRefreshedUrlAndRejectsAmbiguousMatches() {
        val addon = "https://example.org/manifest.json"
        val played = source(addon, "4K").copy(
            stableIdentity = XpSourcePreference.stableIdentity(addon, "4K", "Release A", "provider-id"),
            addonIdentity = XpSourcePreference.identity(addon, ""),
            releaseName = "Release A https://example.org/private-token",
            quality = "4K",
        )
        val saved = XpSourcePreference.playedPreference("series:tt123:1:2", played)
        val refreshed = played.copy(url = "https://example.org/fresh")
        val alternative = source(addon, "1080p")
        assertFalse(saved.contains("example.org"))
        assertEquals(refreshed, XpSourcePreference.playedMatch(saved, listOf(alternative, refreshed)))
        assertNull(XpSourcePreference.playedMatch(saved, listOf(alternative)))
        assertNull(XpSourcePreference.playedMatch(saved, listOf(played, refreshed)))
    }

    @Test
    fun storageKeysSeparateProfilesAndEpisodes() {
        val episode = XpSourcePreference.stableKey(10, "series:tt123:1:2")
        assertNotEquals(episode, XpSourcePreference.stableKey(11, "series:tt123:1:2"))
        assertNotEquals(episode, XpSourcePreference.stableKey(10, "series:tt123:1:3"))
    }

    @Test
    fun centralTranslatesSourceKeyToTheTargetDeviceProfile() {
        val content = "stremio:series:tt123:1:2"
        val firstDevice = XpSourcePreference.stableKey(5, content)
        val cloud = requireNotNull(XpCentralDataSync.canonicalPreferenceKey(firstDevice, 5))
        assertNull(XpCentralDataSync.canonicalPreferenceKey(firstDevice, 6))
        assertEquals(XpSourcePreference.stableKey(19, content),
            XpCentralDataSync.localPreferenceKey(cloud, 19))
    }

    @Test
    fun playbackConfirmationRequiresConsecutiveAdvancesWithoutErrors() {
        val evidence = XpPlaybackEvidence()
        assertFalse(evidence.sample(0, true, false, false))
        assertFalse(evidence.sample(400, true, false, false))
        assertFalse(evidence.sample(800, true, false, true))
        assertFalse(evidence.sample(800, true, false, false))
        assertFalse(evidence.sample(20_000, true, false, false))
        assertFalse(evidence.sample(20_400, true, false, false))
        assertTrue(evidence.sample(20_800, true, false, false))
    }

    @Test
    fun periodicPlayheadUpdatesStillConfirmTheSelectedSource() {
        val evidence = XpPlaybackEvidence()
        listOf(0L, 0L, 1_000L, 1_000L, 1_000L).forEach { position ->
            assertFalse(evidence.sample(position, true, false, false))
        }
        assertTrue(evidence.sample(2_000L, true, false, false))
    }

    @Test
    fun savedSourceCarriesSuccessfulPlaybackTime() {
        val item = source("https://example.org/manifest.json", "4K")
        val saved = XpSourcePreference.playedPreference("stremio:movie:tt123", item, playedAt = 124L)
        assertEquals(124L, XpCentralDataSync.activityTime(org.json.JSONObject().put("value", saved)))
    }
}
