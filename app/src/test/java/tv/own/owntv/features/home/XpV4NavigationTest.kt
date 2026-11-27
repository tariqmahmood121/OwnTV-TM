package tv.own.owntv.features.home

import org.junit.Assert.assertEquals
import org.junit.Test

class XpV4NavigationTest {
    @Test fun completeRowsDoNotMove() {
        assertEquals(0, xpRowScroll(0, 220, 0, 540))
        assertEquals(0, xpRowScroll(235, 240, 0, 540))
    }

    @Test fun clippedRowsAlignToNearestEdge() {
        assertEquals(27, xpRowScroll(345, 102, 0, 420))
        assertEquals(-17, xpRowScroll(-17, 240, 0, 720))
        assertEquals(70, xpRowScroll(940, 210, 0, 1080))
        assertEquals(95, xpRowScroll(2080, 175, 0, 2160))
    }

    @Test fun oversizedRowsLeaveScrollingToFocusedCard() {
        assertEquals(0, xpRowScroll(-53, 600, 0, 350))
        assertEquals(0, xpRowScroll(-1, 20, 0, 0))
    }

    @Test fun recentSearchesAreUniqueAndBounded() {
        val history = listOf("Mission Impossible", "The Walking Dead", "Gladiator", "James Bond", "Interstellar")
        assertEquals(history, XpRecentSearches.add(history, " "))
        assertEquals(listOf("gladiator", "Mission Impossible", "The Walking Dead", "James Bond", "Interstellar"),
            XpRecentSearches.add(history, " gladiator "))
        assertEquals(listOf("The Lord of the Rings") + history.take(4), XpRecentSearches.add(history, "The Lord of the Rings"))
    }

    @Test fun exactThenPrefixThenPartial() {
        assertEquals(0, XpRecentSearches.rank("Missing", "missing"))
        assertEquals(1, XpRecentSearches.rank("Mission: Impossible", "mis"))
        assertEquals(2, XpRecentSearches.rank("The Walking Dead", "walking"))
        assertEquals(3, XpRecentSearches.rank("Interstellar", "walk"))
    }
}
