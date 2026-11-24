package tv.own.owntv.features.home

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XpPlaybackIdentityTest {
    private val central = XpMeta("tmdb:42", "movie", "A Movie", null, null, "2025-02-03", "7.1")
    private val cinemeta = central.copy(id = "tt1234567", name = "A  MOVIE")

    @Test fun exactFallbackNeedsFullDateAndOneIdentity() {
        assertEquals(cinemeta.id, XpPlaybackIdentity.exactImdbMatch(central, listOf(cinemeta, cinemeta)))
        assertNull(XpPlaybackIdentity.exactImdbMatch(central.copy(releaseInfo = "2025"), listOf(cinemeta)))
        assertNull(XpPlaybackIdentity.exactImdbMatch(central, listOf(cinemeta.copy(releaseInfo = "2025-02-04"))))
        assertNull(XpPlaybackIdentity.exactImdbMatch(central, listOf(cinemeta, cinemeta.copy(id = "tt7654321"))))
        assertNull(XpPlaybackIdentity.exactImdbMatch(central, listOf(cinemeta,
            cinemeta.copy(id = "tt7654321", releaseInfo = "2025"))))
    }

    @Test fun verifiedAliasesMatchSavedTitlesAndAreOrderedForPlayback() {
        val enriched = central.copy(imdbId = cinemeta.id, tmdbId = central.id)
        assertEquals(listOf(cinemeta.id, central.id), XpPlaybackIdentity.ids(enriched, cinemeta.id))
        assertTrue(XpPlaybackIdentity.sameTitle(enriched, cinemeta))
        assertTrue(XpPlaybackIdentity.sameTitle(enriched, central))
        assertFalse(XpPlaybackIdentity.sameTitle(enriched, cinemeta.copy(type = "series")))
        assertFalse(XpPlaybackIdentity.sameTitle(central, cinemeta))
    }

    @Test fun centralCatalogueCardResolvesToIdsSupportedByEachAddon() {
        val parsed = XpCentralMetadata.parseCatalog(JSONObject("""
            {"metas":[{"id":"tmdb:42","type":"movie","name":"A Movie","released":"2025-02-03","rating":7.1}]}
        """))!!.single()
        val imdbOnly = XpResource("stream", listOf("movie"), listOf("tt"))
        val tmdbOnly = XpResource("stream", listOf("movie"), listOf("tmdb:"))
        assertFalse(imdbOnly.supports(parsed.type, parsed.id))
        val verified = XpPlaybackIdentity.exactImdbMatch(parsed, listOf(cinemeta))
        val ids = XpPlaybackIdentity.ids(parsed, verified)
        assertEquals(listOf(cinemeta.id), ids.filter { imdbOnly.supports(parsed.type, it) })
        assertEquals(listOf(parsed.id), ids.filter { tmdbOnly.supports(parsed.type, it) })
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun storedMetadataRetainsLegacyIdentityProgressAndRatingSource() {
        val saved = central.copy(positionMs = 1000, durationMs = 5000, imdbId = cinemeta.id, ratingSource = "TMDB")
        assertEquals(saved, XpCentralDataSync.parseMeta(XpCentralDataSync.itemJson(saved)))
    }

    @Test fun paginationRequiresAnExplicitValidNextPage() {
        assertNull(XpCentralMetadata.parsePage(JSONObject("""{"metas":[]}"""), 1)?.next)
        assertNull(XpCentralMetadata.parsePage(JSONObject("""{"metas":[],"fallback":true}"""), 1))
        assertEquals(2, XpCentralMetadata.parsePage(JSONObject("""{"metas":[],"next_page":2}"""), 1)?.next)
        assertNull(XpCentralMetadata.parsePage(JSONObject("""{"metas":[],"next_page":7}"""), 1)?.next)
        assertTrue(XpCentralMetadata.parsePage(JSONObject("""{"metas":[],"fallback":false,"error":"Unavailable"}"""), 1)?.failed == true)
    }

    @Test fun legacyHistoryIsAvailableUntilAnExplicitCollectionIsSaved() {
        assertEquals(listOf(central), XpPersonalCollections.decode(null) { listOf(central) })
        assertEquals(emptyList<XpMeta>(), XpPersonalCollections.decode("[]") {
            throw AssertionError("an explicit clear must not revive a legacy title")
        })
    }

    @Test fun playbackReturnUsesTheOriginalGridAndStablePosterAfterReordering() {
        val origin = homePosterKey("discover|favorites", central)
        assertEquals(1, xpRestoredPosterIndex("discover|favorites", listOf(cinemeta, central), origin))
        assertEquals(-1, xpRestoredPosterIndex("library|favorites", listOf(cinemeta, central), origin))
    }

    @Test fun explicitSortingOverridesCatalogueDefaults() {
        assertEquals(0, xpCategorySort("xp_trending", null))
        assertEquals(2, xpCategorySort("xp_upcoming", null))
        assertEquals(1, xpCategorySort("xp_latest", null))
        assertEquals(5, xpCategorySort("xp_upcoming", 5))
    }
}
