package tv.own.owntv.features.home

import java.util.Locale

internal object XpCatalogueVisibility {
    private val separators = Regex("[^a-z0-9]")
    private val hiddenNames = setOf("torboxtorrents", "torboxusenet", "torboxwebdl")
    private val hiddenIds = setOf("torrent", "torrents", "usenet", "webdl") + hiddenNames

    fun hidden(addonId: String, catalogueId: String, title: String): Boolean {
        fun key(value: String) = value.lowercase(Locale.ROOT).replace(separators, "")
        return key(title) in hiddenNames ||
            ("torbox" in key(addonId) && key(catalogueId) in hiddenIds)
    }
}
