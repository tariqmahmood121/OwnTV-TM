package tv.own.owntv.features.home

import org.json.JSONObject

internal data class XpEpisode(val id: String, val season: Int, val number: Int, val title: String?) {
    fun forAlias(alias: String) = "$alias:$season:$number"

    companion object {
        fun parse(response: JSONObject, seriesId: String): List<XpEpisode> {
            val videos = response.optJSONObject("meta")?.optJSONArray("videos") ?: return emptyList()
            return buildList {
                for (index in 0 until videos.length()) {
                    val entry = videos.optJSONObject(index) ?: continue
                    val season = entry.optInt("season", -1)
                    val number = entry.optInt("episode", -1)
                    if (season < 0 || number < 1) continue
                    val id = "$seriesId:$season:$number"
                    if (entry.optString("id") != id) continue
                    val title = entry.optString("title").takeIf { !entry.isNull("title") && it.isNotBlank() }
                    add(XpEpisode(id, season, number, title))
                }
            }.distinctBy(XpEpisode::id).sortedWith(compareBy(XpEpisode::season, XpEpisode::number))
        }
    }
}
