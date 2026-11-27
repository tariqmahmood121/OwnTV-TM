package tv.own.owntv.features.home

import org.junit.Assert.assertEquals
import org.junit.Test

class XpFeaturedPoolTest {
    @Test
    fun candidatesExcludePortraitFallbacksAndDeduplicateMovies() {
        val movie = XpMeta("movie", "movie", "Title", "poster.jpg", null, null, null, backdrop = "wide.jpg")
        val other = movie.copy(id = "other", name = "Another title")
        val candidates = xpFeatureCandidates(listOf(movie.copy(backdrop = movie.poster),
            movie.copy(type = "series"), movie, movie.copy(name = "Duplicate"), other))
        assertEquals(listOf(movie, other), candidates)
    }
}
