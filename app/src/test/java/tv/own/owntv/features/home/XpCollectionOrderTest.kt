package tv.own.owntv.features.home

import org.junit.Assert.assertEquals
import org.junit.Test

class XpCollectionOrderTest {
    private fun movie(id: String, time: Long, imdb: String? = null) =
        XpMeta(id, "movie", id, null, null, null, null, positionMs = 5_000,
            imdbId = imdb, activityAt = time)

    @Test
    fun verifiedAliasMovesTheSameMovieWithoutDuplicatingIt() {
        val a = movie("tmdb:11", 1, "tt11")
        val b = movie("tt22", 2)
        val c = movie("tt33", 3)
        val updated = XpCollectionOrder.merge(listOf(c, b, a), movie("tt11", 4), deleted = false)
        assertEquals(listOf("tt11", "tt33", "tt22"), updated.map(XpMeta::id))
        assertEquals(updated, XpCollectionOrder.merge(updated, a, deleted = false))
    }

    @Test
    fun removalsAndReAddsRespectActivityTime() {
        val a = movie("tt11", 1)
        val b = movie("tt22", 2)
        val removed = XpCollectionOrder.merge(listOf(b, a), a.copy(activityAt = 3), deleted = true)
        val reAdded = XpCollectionOrder.merge(removed, a.copy(activityAt = 4), deleted = false)
        assertEquals(listOf("tt11", "tt22"), reAdded.map(XpMeta::id))
        assertEquals(reAdded, XpCollectionOrder.merge(reAdded, a.copy(activityAt = 3), deleted = true))
    }

    @Test
    fun resumeShelfShowsTheLatestEpisodeWithoutCombiningEpisodeProgress() {
        val e1 = movie("tt11:1:1", 1).copy(type = "series")
        val e2 = movie("tt11:1:2", 3).copy(type = "series", positionMs = 8_000L)
        val unrelated = movie("tt22", 2)
        assertEquals(listOf(e2, unrelated), XpCollectionOrder.resumeShelf(listOf(e1, unrelated, e2)))
        assertEquals(listOf(e2, unrelated, e1), XpCollectionOrder.unique(listOf(e1, unrelated, e2)))
    }

    @Test
    fun centralPayloadPreservesActivityAndPlayhead() {
        val item = movie("tt11", 456)
        assertEquals(item, XpCentralDataSync.parseMeta(XpCentralDataSync.itemJson(item)))
    }
}
