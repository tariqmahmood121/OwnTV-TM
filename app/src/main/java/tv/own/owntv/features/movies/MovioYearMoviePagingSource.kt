package tv.own.owntv.features.movies

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.own.owntv.core.database.dao.MovieDao
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.live.LiveKey

/** Large-catalogue release-year sort; unknown years always last in both directions. */
internal class MovioYearMoviePagingSource(
    private val context: Context,
    private val dao: MovieDao,
    private val sourceIds: List<Long>,
    private val profileId: Long,
    private val key: LiveKey,
    private val search: String,
    private val newestFirst: Boolean,
) : PagingSource<Int, MovieEntity>() {
    override fun getRefreshKey(state: PagingState<Int, MovieEntity>): Int? = state.anchorPosition

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MovieEntity> = try {
        withContext(Dispatchers.IO) {
            val offset = (params.key ?: 0).coerceAtLeast(0)
            val args = mutableListOf<String>()
            val where = mutableListOf<String>()
            val ids = sourceIds.ifEmpty { listOf(-1L) }
            where += "sourceId IN (${ids.joinToString(",") { "?" }})"
            ids.forEach { args += it.toString() }
            if (search.isNotBlank()) { where += "name LIKE ?"; args += "%$search%" }
            when (key) {
                is LiveKey.Folder -> { where += "categoryId = ?"; args += key.id.toString() }
                is LiveKey.Custom -> {
                    where += "id IN (SELECT itemId FROM custom_category_members WHERE profileId = ? AND mediaType = 'MOVIE' AND contextKey = ?)"
                    args += profileId.toString(); args += key.id
                }
                LiveKey.Favorites -> {
                    where += "id IN (SELECT itemId FROM favorites WHERE profileId = ? AND mediaType = 'MOVIE')"
                    args += profileId.toString()
                }
                LiveKey.History -> {
                    where += "id IN (SELECT itemId FROM watch_history WHERE profileId = ? AND mediaType = 'MOVIE')"
                    args += profileId.toString()
                }
                else -> Unit
            }
            val year = "COALESCE(NULLIF(year, 0), parsedYear)"
            val order = if (newestFirst) "DESC" else "ASC"
            val sql = "SELECT id FROM movies WHERE ${where.joinToString(" AND ")} " +
                "ORDER BY CASE WHEN $year BETWEEN 1880 AND 2100 THEN 0 ELSE 1 END ASC, " +
                "$year $order, name COLLATE NOCASE ASC, id ASC LIMIT ? OFFSET ?"
            args += params.loadSize.toString(); args += offset.toString()
            val db = SQLiteDatabase.openDatabase(
                context.getDatabasePath(tv.own.owntv.core.database.OwnTVDatabase.NAME).absolutePath,
                null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
            )
            val rowIds = try {
                db.rawQuery(sql, args.toTypedArray()).use { cur ->
                    buildList { while (cur.moveToNext()) add(cur.getLong(0)) }
                }
            } finally { db.close() }
            val matches = dao.getByIds(rowIds).associateBy { it.id }
            val results = rowIds.mapNotNull { matches[it] }
            LoadResult.Page(
                data = results,
                prevKey = if (offset == 0) null else (offset - params.loadSize).coerceAtLeast(0),
                nextKey = if (rowIds.size < params.loadSize) null else offset + rowIds.size,
            )
        }
    } catch (error: Exception) { LoadResult.Error(error) }
}
