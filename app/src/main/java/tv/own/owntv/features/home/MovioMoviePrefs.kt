package tv.own.owntv.features.home

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf

data class MovioMovieOptions(
    val ratingSize: String = "SMALL",
    val showYear: Boolean = true,
    val detailsFirst: Boolean = false,
    val yearOrder: Int = 0,
)

object MovioMoviePrefs {
    private val states = mutableStateMapOf<Long, MovioMovieOptions>()
    private fun store(context: Context) = context.getSharedPreferences("moviotv_movie_options_v77", Context.MODE_PRIVATE)
    fun get(context: Context, profileId: Long?): MovioMovieOptions {
        val id = profileId ?: -1L
        return states.getOrPut(id) {
            val p=store(context)
            MovioMovieOptions(
                ratingSize = p.getString("rating_$id", "SMALL")?.takeIf { it in listOf("SMALL", "MEDIUM", "LARGE") } ?: "SMALL",
                showYear = p.getBoolean("year_$id", true),
                detailsFirst = p.getBoolean("details_$id", false),
                yearOrder = p.getInt("sortyear_$id", 0).coerceIn(-1,1),
            )
        }
    }
    fun save(context: Context, profileId: Long?, next: MovioMovieOptions) {
        val id = profileId ?: -1L
        states[id] = next
        store(context).edit().putString("rating_$id", next.ratingSize)
            .putBoolean("year_$id", next.showYear)
            .putBoolean("details_$id", next.detailsFirst)
            .putInt("sortyear_$id", next.yearOrder)
            .apply()
    }
}
