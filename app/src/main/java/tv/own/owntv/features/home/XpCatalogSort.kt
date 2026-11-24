package tv.own.owntv.features.home

import java.time.LocalDate
import java.util.Locale

private val fullDatePattern = Regex("""(?<!\d)\d{4}-\d{2}-\d{2}(?!\d)""")
private val yearPattern = Regex("""(?<!\d)\d{4}(?!\d)""")

internal fun xpReleaseYear(item: XpMeta): String? =
    yearPattern.find(item.releaseInfo.orEmpty())?.value

private fun releaseDate(item: XpMeta): LocalDate? {
    val date = fullDatePattern.find(item.releaseInfo.orEmpty())?.value ?: return null
    return runCatching { LocalDate.parse(date) }.getOrNull()
}

internal fun xpRating(item: XpMeta): Double? =
    item.rating?.toDoubleOrNull()?.takeIf { it > 0 && it <= 10 }

internal fun sortXpTitles(titles: List<XpMeta>, mode: Int): List<XpMeta> = when (mode) {
    1 -> titles.sortedWith(compareBy(nullsLast(reverseOrder()), ::releaseDate))
    2 -> titles.sortedWith(compareBy(nullsLast(naturalOrder()), ::releaseDate))
    3 -> titles.sortedBy { it.name.lowercase(Locale.ROOT) }
    4 -> titles.sortedByDescending { it.name.lowercase(Locale.ROOT) }
    5 -> titles.sortedWith(compareBy(nullsLast(reverseOrder()), ::xpRating))
    6 -> titles.sortedWith(compareBy(nullsLast(naturalOrder()), ::xpRating))
    else -> titles
}
