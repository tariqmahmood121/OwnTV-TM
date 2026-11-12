/* MovioTV touch navigation and browsing surfaces. GPL-3.0, Created by ZIGGS. */
package tv.own.owntv.features.home

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.features.profiles.ProfilesViewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import tv.own.owntv.core.nav.MainSection

private val panel = Color(0xFF171123)
private val purple = Color(0xFFB465FF)
private val pale = Color(0xFFD6C5E6)

/** Shell navigation stays outside scrollable content on small touch screens. */
@Composable
fun MovioTouchBottomNav(
    selected: MainSection,
    visible: Set<MainSection>,
    onSelect: (MainSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = listOf(
        Triple(MainSection.HOME, stringResource(R.string.moviotv_nav_home), stringResource(R.string.moviotv_icon_home)),
        Triple(MainSection.LIVE_TV, stringResource(R.string.moviotv_nav_live), stringResource(R.string.moviotv_icon_play)),
        Triple(MainSection.MOVIES, stringResource(R.string.moviotv_nav_movies), stringResource(R.string.moviotv_icon_movies)),
        Triple(MainSection.SERIES, stringResource(R.string.moviotv_nav_series), stringResource(R.string.moviotv_icon_series)),
        Triple(MainSection.MORE, stringResource(R.string.moviotv_nav_more), stringResource(R.string.moviotv_icon_more)),
    ).filter { it.first in visible || it.first == MainSection.HOME || it.first == MainSection.MORE }
    Row(
        modifier = modifier.fillMaxWidth().height(70.dp)
            .background(Color(0xFF140E20)).padding(horizontal = 5.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        entries.forEach { (destination, label, icon) ->
            val active = selected == destination
            Column(
                modifier = Modifier.weight(1f).fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (active) Color(0xFF493065) else Color.Transparent)
                    .clickable { onSelect(destination) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(icon, color = if (active) purple else pale, fontSize = 20.sp)
                Text(label, color = Color.White, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun MovioTouchSearch(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.height(44.dp).clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF30213E)).border(1.dp, Color(0xFF6B4B85), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text(stringResource(R.string.moviotv_search_titles), color = pale, fontSize = 14.sp)
        BasicTextField(
            value = value, onValueChange = onChange,
            singleLine = true, textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(purple),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Category rail is permanent on unfolded/tablet screens and a drawer on folded phones. */
@Composable
fun MovioTouchPosterBrowser(
    title: String,
    category: String,
    query: String,
    onSearch: (String) -> Unit,
    categoryEnabled: Boolean,
    onCategories: () -> Unit,
    categories: @Composable (Modifier) -> Unit,
    onSort: () -> Unit,
    itemCount: Int,
    gridState: LazyGridState,
    emptyText: String,
    poster: @Composable (Int, Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val profiles: ProfilesViewModel = koinViewModel()
    val profileId by profiles.activeProfileId.collectAsStateWithLifecycle()
    val posterZoomKey = "poster_zoom_" + (profileId ?: -1L) + "_" + title
    val posterPrefs = remember(context) {
        context.getSharedPreferences("moviotv_poster_grid_v75", Context.MODE_PRIVATE)
    }
    var posterScale by remember(posterZoomKey) {
        mutableFloatStateOf(posterPrefs.getFloat(posterZoomKey, 1f).coerceIn(0.60f, 2.30f))
    }
    BoxWithConstraints(modifier.fillMaxSize().background(panel)) {
        val tablet = maxWidth >= 700.dp && categoryEnabled
        val categoryWidth = if (tablet) (maxWidth * 0.30f).coerceIn(210.dp, 310.dp) else 0.dp
        Row(Modifier.fillMaxSize()) {
            if (tablet) MovioCategoryTextZoom(
                modifier = Modifier.width(categoryWidth).fillMaxHeight().padding(top = 8.dp),
            ) { categories(Modifier.fillMaxSize()) }
            Column(Modifier.weight(1f).fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!tablet && categoryEnabled) {
                        Box(Modifier.clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF50326D)).clickable(onClick = onCategories)
                            .padding(horizontal = 12.dp, vertical = 12.dp)) {
                            Text(stringResource(R.string.moviotv_categories), color = Color.White, fontSize = 13.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    MovioTouchSearch(query, onSearch, Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF50326D)).clickable(onClick = onSort)
                        .padding(horizontal = 12.dp, vertical = 12.dp)) {
                        Text(stringResource(R.string.moviotv_sort), color = Color.White, fontSize = 13.sp)
                    }
                }
                Text(category, color = pale, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
                if (itemCount == 0) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(emptyText, color = pale, fontSize = 16.sp)
                    }
                } else {
                    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)
                        .pointerInput(posterZoomKey) {
                            // Capture zoom from two fingers; never consume one-finger grid scrolling.
                            awaitEachGesture {
                                do {
                                    val event = awaitPointerEvent()
                                    if (event.changes.count { it.pressed } >= 2) {
                                        val updated = (posterScale * event.calculateZoom()).coerceIn(0.60f, 2.30f)
                                        if (kotlin.math.abs(updated - posterScale) > 0.003f) {
                                            posterScale = updated
                                            posterPrefs.edit().putFloat(posterZoomKey, updated).apply()
                                            event.changes.forEach { it.consume() }
                                        }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }) {
                        val columns = ((maxWidth + 12.dp) / (150.dp * posterScale)).toInt().coerceIn(1, 6)
                        val posterWidth = (maxWidth - 12.dp * (columns - 1) - 8.dp) / columns
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns), state = gridState,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 32.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(itemCount) { index -> poster(index, posterWidth) }
                        }
                    }
                }
            }
        }
    }
}

/** Touch-first channel list; playback is delegated to LiveViewModel unchanged. */
@Composable
fun MovioTouchLiveBrowser(
    category: String,
    query: String,
    onSearch: (String) -> Unit,
    categoryEnabled: Boolean,
    onCategories: () -> Unit,
    categories: @Composable (Modifier) -> Unit,
    count: Int,
    listState: LazyListState,
    row: @Composable (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize().background(panel)) {
        val tablet = maxWidth >= 700.dp && categoryEnabled
        val categoryWidth = if (tablet) (maxWidth * 0.30f).coerceIn(210.dp, 310.dp) else 0.dp
        Row(Modifier.fillMaxSize()) {
            if (tablet) MovioCategoryTextZoom(
        modifier = Modifier.width(categoryWidth).fillMaxHeight().padding(top = 8.dp),
    ) { categories(Modifier.fillMaxSize()) }
            Column(Modifier.weight(1f).fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(stringResource(R.string.moviotv_nav_live_tv), color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!tablet && categoryEnabled) {
                        Box(Modifier.clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF50326D)).clickable(onClick = onCategories)
                            .padding(horizontal = 12.dp, vertical = 12.dp)) {
                            Text(stringResource(R.string.moviotv_categories), color = Color.White, fontSize = 13.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    MovioTouchSearch(query, onSearch, Modifier.weight(1f))
                }
                Text(category, color = pale, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(7.dp),
                    contentPadding = PaddingValues(bottom = 32.dp), modifier = Modifier.weight(1f).fillMaxWidth()) {
                    items(count) { index -> row(index) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MovioTouchChannelRow(
    title: String, subtitle: String?, logo: String?,
    onClick: () -> Unit, onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF29203A)).combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!logo.isNullOrBlank()) {
            AsyncImage(model = logo, contentDescription = null, modifier = Modifier.size(44.dp))
        } else {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.moviotv_icon_play), color = purple, fontSize = 22.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) Text(subtitle, color = pale, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
