package tv.own.owntv.features.home

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class XpEpisodeTest {
    @Test fun onlyValidEpisodesForTheRequestedSeriesAreAccepted() {
        val result = XpEpisode.parse(JSONObject("""
            {"meta":{"videos":[
              {"id":"tt42:2:1","season":2,"episode":1,"title":"Second season"},
              {"id":"tt42:1:3","season":1,"episode":3,"title":null},
              {"id":"tt42:1:3","season":1,"episode":3},
              {"id":"tt99:1:2","season":1,"episode":2},
              {"id":"tt42:1:0","season":1,"episode":0}
            ]}}
        """), "tt42")
        assertEquals(listOf("tt42:1:3", "tt42:2:1"), result.map { it.id })
        assertEquals("tmdb:12:1:3", result.first().forAlias("tmdb:12"))
    }
}
