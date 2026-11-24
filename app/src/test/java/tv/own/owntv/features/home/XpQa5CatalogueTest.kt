package tv.own.owntv.features.home

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XpQa5CatalogueTest {
    @Test fun hidesOnlyRequestedTorBoxRowsFromBrowsing() {
        assertTrue(XpCatalogueVisibility.hidden("app.torbox", "torbox-torrents", "Torrents"))
        assertTrue(XpCatalogueVisibility.hidden("app.torbox", "usenet", "Usenet"))
        assertTrue(XpCatalogueVisibility.hidden("other.id", "other", "TorBox WebDL"))
        assertFalse(XpCatalogueVisibility.hidden("app.torbox", "recommendations", "Recommendations"))
        assertFalse(XpCatalogueVisibility.hidden("other.id", "torrents", "Torrents"))
        assertFalse(XpCatalogueVisibility.hidden("com.linvo.cinemeta", "top", "Popular"))
    }

    @Test fun metadataHandlesMissingFieldsAndExistingCinemetaCredits() {
        val raw = JSONObject("""
            {"genres":["Drama","Drama"],"runtime":"102 min",
             "cast":["First",{"name":"Second"}],"director":["Director"],"writer":null}
        """)
        val detail = XpDetailMetadata.parse(raw)
        assertEquals(listOf("Drama"), detail.genres)
        assertEquals("102 min", detail.runtime)
        assertEquals(listOf("First", "Second"), detail.cast)
        assertEquals(listOf("Director"), detail.directors)
        assertTrue(detail.writers.isEmpty())
        assertNull(XpDetailMetadata.parse(JSONObject()).runtime)
    }
}
