package tv.own.owntv.features.home

internal object XpCataloguePaging {
    const val PAGE_SIZE = 20
    const val MAX_PAGES = 50
    const val MAX_TITLES = PAGE_SIZE * MAX_PAGES

    fun next(current: Int, offered: Int): Int? =
        offered.takeIf { current in 1 until MAX_PAGES && it == current + 1 }

    fun <T> unique(
        entries: List<T>,
        identity: (T) -> XpMeta,
    ): List<T> {
        val parent = IntArray(entries.size) { it }
        fun root(index: Int): Int {
            var value = index
            while (value != parent[value]) {
                parent[value] = parent[parent[value]]
                value = parent[value]
            }
            return value
        }
        val owners = mutableMapOf<Pair<String, String>, Int>()
        entries.forEachIndexed { index, entry ->
            val title = identity(entry)
            for (id in XpPlaybackIdentity.ids(title)) {
                val previous = owners.putIfAbsent(title.type to id, index) ?: continue
                val first = root(previous)
                val second = root(index)
                parent[maxOf(first, second)] = minOf(first, second)
            }
        }
        return entries.filterIndexed { index, _ -> root(index) == index }
    }
}
