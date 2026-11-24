package tv.own.owntv.features.home

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XpCentralMetadataTest {
    @Test fun deployedResponseRetainsDateAndLabelsTmdbRating() {
        val item = XpCentralMetadata.parseCatalog(JSONObject("""
            {"source":"tmdb","metas":[{"id":"tmdb:12","type":"movie","name":"Movie",
            "released":"2026-08-15","releaseInfo":"2026","poster":null,"rating":7.4,"rating_source":"TMDB"}],
            "fallback":false}
        """))!!.single()
        assertEquals("tmdb:12", item.id)
        assertEquals("2026-08-15", item.releaseInfo)
        assertNull(item.poster)
        assertEquals("7.4", item.rating)
        assertEquals("TMDB", item.ratingSource)
    }

    @Test fun enrichedResponseUsesImdbIdentityAndRetainsTmdbAlias() {
        val item = XpCentralMetadata.parseCatalog(JSONObject("""
            {"metas":[{"id":"tmdb:12","imdb_id":"tt1234567","name":"Movie","rating":8,"rating_source":"IMDb"}]}
        """))!!.single()
        assertEquals("tt1234567", item.id)
        assertEquals("tmdb:12", item.tmdbId)
        assertEquals("IMDb", item.ratingSource)
    }

    @Test fun fallbackFollowsServerPolicy() {
        assertNull(XpCentralMetadata.parseCatalog(JSONObject("""{"metas":[],"fallback":true}""")))
        assertEquals(emptyList<XpMeta>(), XpCentralMetadata.parseCatalog(JSONObject("""{"metas":[],"fallback":false}""")))
    }

    @Test fun onlyContractCategoriesAreRequested() {
        assertEquals("cinema", XpCentralMetadata.category("xp_cinema"))
        assertEquals("upcoming", XpCentralMetadata.category("xp_upcoming"))
        assertEquals("science-fiction", XpCentralMetadata.category("genre:Sci-Fi"))
        assertNull(XpCentralMetadata.category("genre:Independent"))
    }
}
