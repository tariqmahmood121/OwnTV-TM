package tv.own.owntv.features.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

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
}
