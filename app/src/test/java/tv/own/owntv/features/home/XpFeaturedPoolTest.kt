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

    @Test
    fun homeResumeCapsTheShelfAndPreservesNewestProgress() {
        val records = (1..300).map { id ->
            XpMeta("tt$id", "movie", "Title $id", null, null, "2024", null,
                positionMs = id * 1_000L, activityAt = id.toLong())
        }
        for (size in listOf(0, 15, 16, 300)) {
            val profile = records.take(size)
            val shelf = XpCollectionOrder.homeResume(profile + profile.takeLast(1))
            assertEquals(minOf(15, size), shelf.size)
            assertEquals(profile.lastOrNull()?.positionMs, shelf.firstOrNull()?.positionMs)
            assertEquals(size, XpCollectionOrder.resumeShelf(profile).size)
        }
    }

    @Test
    fun queueUsesHiddenEligibleGenresAndRejectsExposedAliasesAndWatchedTitles() {
        val movie = XpMeta("tt1", "movie", "Title", "portrait", null, "2024", null, backdrop = "wide")
        val watched = movie.copy(id = "tt2")
        val exposedAlias = movie.copy(id = "tmdb:3", imdbId = "tt3")
        val eligible = movie.copy(id = "tt4")
        val future = movie.copy(id = "tt5", releaseInfo = "2030-01-01")
        val cinemaOnly = movie.copy(id = "tt6")
        val data = mapOf("xp_trending" to listOf(future, watched, exposedAlias),
            "genre:Drama" to listOf(eligible, eligible.copy(id = "tmdb:4", imdbId = "tt4")),
            "xp_cinema" to listOf(cinemaOnly))
        val queue = xpRankFeatureQueue(data, false, listOf(watched), emptyList(), setOf("tt3"),
            java.time.LocalDate.parse("2026-11-28"))
        assertEquals(listOf(eligible.id), queue.map { it.imdbId ?: it.id })
        assertEquals(emptyList<XpMeta>(), xpRankFeatureQueue(data, true, listOf(watched),
            emptyList(), setOf("tt3"), java.time.LocalDate.parse("2026-11-28")))
    }

    @Test
    fun budgetAdjustsForFreeStorage() {
        val mb = 1024L * 1024
        assertEquals(150 * mb, XpMaintenance.budget(8_000 * mb, 5_000 * mb))
        assertEquals(25 * mb, XpMaintenance.budget(8_000 * mb, 500 * mb))
    }
    @Test
    fun catalogueTypesKeepSeriesAliasesDistinctFromLiveTv() {
        org.junit.Assert.assertTrue("series".isXpSeriesCatalogue())
        org.junit.Assert.assertTrue("TVShows".isXpSeriesCatalogue())
        org.junit.Assert.assertTrue("show".isXpSeriesCatalogue())
        org.junit.Assert.assertFalse("tv".isXpSeriesCatalogue())
        org.junit.Assert.assertFalse("movie".isXpSeriesCatalogue())
    }
}
