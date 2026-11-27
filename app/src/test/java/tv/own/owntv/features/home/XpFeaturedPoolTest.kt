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

    @Test
    fun rotationSkipsEmptyFutureUnknownAndCurrentTitles() {
        val movie = XpMeta("now", "movie", "Title", "poster.jpg", null, "2025-12-01", null, backdrop = "wide.jpg")
        val recent = movie.copy(id = "recent")
        val fresh = movie.copy(id = "fresh")
        val data = mapOf(
            "xp_trending" to listOf(movie, movie.copy(id = "year", releaseInfo = "2025")),
            "xp_action" to listOf(movie.copy(id = "future", releaseInfo = "2027-01-01")),
            "xp_latest" to listOf(fresh, recent),
        )
        val today = java.time.LocalDate.parse("2026-11-27")
        val year = data.getValue("xp_trending").last()
        assertEquals(listOf(0 to listOf(year), 2 to listOf(fresh)), xpFeatureRotationBuckets(data, false, -1, movie.id, listOf(recent.id), today))
        assertEquals(listOf(2 to listOf(fresh)), xpFeatureRotationBuckets(data, false, -1, movie.id, listOf(year.id, recent.id), today))
        assertEquals(listOf(0 to listOf(year)), xpFeatureRotationBuckets(data, true, -1, movie.id, emptyList(), today))
        org.junit.Assert.assertFalse(xpFeatureReleased(movie.copy(releaseInfo = "2026"), today))
        org.junit.Assert.assertFalse(xpFeatureReleased(movie.copy(releaseInfo = "2026-12-01"), today))
        org.junit.Assert.assertTrue(xpFeatureReleased(movie.copy(releaseInfo = "2026-11-27"), today))
        assertEquals(listOf(20, 30, 60, 120), xpFeatureIntervals)
        assertEquals(20, XpFeatureSettings().seconds)
    }
}
