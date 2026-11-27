package tv.own.owntv.features.home

/** Positive distances scroll towards later rows. Oversized rows keep normal card relocation. */
internal fun xpRowScroll(start: Int, size: Int, viewportStart: Int, viewportEnd: Int): Int {
    if (size <= 0 || viewportEnd <= viewportStart || size > viewportEnd - viewportStart) return 0
    return when {
        start < viewportStart -> start - viewportStart
        start + size > viewportEnd -> start + size - viewportEnd
        else -> 0
    }
}
