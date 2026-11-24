package tv.own.owntv.features.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class XpCatalogSortTest {
    private val spring = title("B", "2024-04-15T00:00:00Z", "7.8")
    private val summer = title("a", "2024-08-02", "8.3")
    private val yearOnly = title("C", "2024", null)
    private val malformed = title("D", "2024-02-30", "NaN")
    private val providerOrder = listOf(yearOnly, summer, malformed, spring)

    @Test fun providerOrderIsPreserved() {
        assertSame(providerOrder, sortXpTitles(providerOrder, 0))
    }

    @Test fun fullDatesSortWithinYearAndIncompleteDatesStayLast() {
        assertEquals(listOf(summer, spring, yearOnly, malformed), sortXpTitles(providerOrder, 1))
        assertEquals(listOf(spring, summer, yearOnly, malformed), sortXpTitles(providerOrder, 2))
        assertEquals("2024", xpReleaseYear(yearOnly))
    }

    @Test fun namesSortIgnoringCase() {
        assertEquals(listOf(summer, spring, yearOnly, malformed), sortXpTitles(providerOrder, 3))
        assertEquals(listOf(malformed, yearOnly, spring, summer), sortXpTitles(providerOrder, 4))
    }

    @Test fun missingAndInvalidRatingsStayLast() {
        assertEquals(listOf(summer, spring, yearOnly, malformed), sortXpTitles(providerOrder, 5))
        assertEquals(listOf(spring, summer, yearOnly, malformed), sortXpTitles(providerOrder, 6))
    }

    private fun title(name: String, release: String, rating: String?) =
        XpMeta(name, "movie", name, null, null, release, rating)
}
