package tv.own.owntv.features.home

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XpQa4NavigationTest {
    @Test fun directionHoldFiresOnceAtTwoSecondsAndSuppressesExtraStep() {
        val hold = XpRemoteHold()
        for (direction in XpRemoteHold.Direction.entries) {
            assertTrue(hold.start(direction, 100L))
            assertFalse(hold.start(direction, 800L))
            assertFalse(hold.trigger(direction, 2_099L))
            assertTrue(hold.trigger(direction, 2_100L))
            assertFalse(hold.trigger(direction, 3_000L))
            assertEquals(XpRemoteHold.Release.CONSUME, hold.release(direction))
            assertEquals(XpRemoteHold.Release.IGNORE, hold.release(direction))
        }
    }

    @Test fun shortTapMovesOnceAndCancellationPreventsJump() {
        val hold = XpRemoteHold()
        val top = XpRemoteHold.Direction.TOP
        hold.start(top, 0)
        assertEquals(XpRemoteHold.Release.STEP, hold.release(top))
        hold.start(top, 0)
        hold.cancel()
        assertFalse(hold.trigger(top, 3_000))
        assertEquals(XpRemoteHold.Release.IGNORE, hold.release(top))
    }

    @Test fun followsOnlyConsecutivePagesUpToFifty() {
        assertEquals(20, XpCataloguePaging.PAGE_SIZE)
        assertEquals(1000, XpCataloguePaging.MAX_TITLES)
        assertEquals(11, XpCataloguePaging.next(10, 11))
        assertEquals(50, XpCataloguePaging.next(49, 50))
        assertNull(XpCataloguePaging.next(50, 51))
        assertNull(XpCataloguePaging.next(4, 6))
        assertNull(XpCataloguePaging.next(4, 4))
    }

    @Test fun centralPageReaderCapsPayloadAndRespectsServerPagination() {
        val payload = JSONObject().put("metas", JSONArray((1..25).map {
            JSONObject().put("id", "tmdb:$it").put("name", "Title $it")
        })).put("next_page", 50)
        val page = requireNotNull(XpCentralMetadata.parsePage(payload, 49))
        assertEquals(20, page.titles.size)
        assertEquals(50, page.next)
        assertNull(XpCentralMetadata.parsePage(payload.put("next_page", 51), 50)?.next)
    }

    @Test fun deduplicatesVerifiedAliasesAcrossPagesAndSearchWithoutMergingByName() {
        val tmdb = XpMeta("tmdb:42", "movie", "Same Title", null, null, null, null)
        val imdb = tmdb.copy(id = "tt1234567")
        val verified = tmdb.copy(imdbId = imdb.id)
        val different = tmdb.copy(id = "tt7654321")
        assertEquals(listOf(tmdb, imdb), XpCataloguePaging.unique(listOf(tmdb, imdb)) { it })
        assertEquals(listOf(tmdb, different), XpCataloguePaging.unique(listOf(tmdb, imdb, verified, different)) { it })
        val series = imdb.copy(type = "series")
        assertEquals(listOf(imdb, series), XpCataloguePaging.unique(listOf(imdb, series)) { it })
    }

    @Test fun defaultsAndExistingProfileOverridesStayDistinct() {
        val defaults = XpHomeConfig.decode(null, null, true)
        assertEquals(listOf("xp_trending", "xp_horror", "xp_action", "xp_thriller", "xp_bollywood",
            "xp_cinema", "xp_upcoming", "xp_latest", "xp_cinemeta_featured"), defaults.order)
        assertEquals(setOf("xp_latest", "xp_cinemeta_featured"), defaults.hidden)
        val existing = XpHomeConfig.decode("""["xp_latest","xp_trending","xp_horror"]""", """["xp_trending"]""", false)
        assertEquals(listOf("xp_latest", "xp_trending", "xp_horror"), existing.order.take(3))
        assertTrue("xp_latest" in existing.visibleDiscoverIds())
        assertFalse("xp_trending" in existing.visibleDiscoverIds())
        assertFalse(existing.showDefaultCatalogs)
    }

    @Test fun individualRowsStayScopedAndCinemetaIsHiddenByDefault() {
        val defaults = XpHomeConfig.decode(null, null, true)
        val row = XpHomeConfig.externalRowId("https://example.test/addon/manifest.json", "movie", "top")
        val different = XpHomeConfig.externalRowId("https://example.test/addon/manifest.json", "series", "top")
        assertNotEquals(row, different)
        assertFalse(XpHomeConfig.externalVisible(defaults, row, true))
        assertTrue(XpHomeConfig.externalVisible(defaults, row, false))
        val enabled = XpHomeConfig.toggleExternal(defaults, row, false)
        assertTrue(XpHomeConfig.externalVisible(enabled, row, true))
        assertFalse(XpHomeConfig.externalVisible(enabled, different, true))
        val off = XpHomeConfig.toggleExternal(enabled, row, true)
        assertFalse(XpHomeConfig.externalVisible(off, row, false))
        assertTrue(off.visibleDiscoverIds().none { it == row })
    }
}
