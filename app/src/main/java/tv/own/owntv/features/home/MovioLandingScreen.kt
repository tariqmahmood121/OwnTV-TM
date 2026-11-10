/*
 * MovioTV — Created by ZIGGS. One APK with TV/D-pad and touch-first home layouts.
 * No provider, catalogue, playback or profile data is changed here. GPL-3.0.
 */
package tv.own.owntv.features.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import androidx.tv.material3.Text

private val background = Color(0xFF100C1C)
private val accent = Color(0xFFB465FF)
private val muted = Color(0xFFD1C2E5)

@Composable
fun MovioLandingScreen(
    onLiveTv: () -> Unit,
    onMovies: () -> Unit,
    onSeries: () -> Unit,
    onFocused: () -> Unit,
    layoutChoice: MovioLayoutChoice,
    onLayoutChoice: (MovioLayoutChoice) -> Unit,
    touchMode: Boolean,
    onEntryHook: (((() -> Boolean)?) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val firstFocus = remember { FocusRequester() }
    DisposableEffect(onEntryHook) {
        onEntryHook?.invoke { runCatching { firstFocus.requestFocus() }.getOrDefault(false) }
        onDispose { onEntryHook?.invoke(null) }
    }
    LaunchedEffect(touchMode) { if (!touchMode) runCatching { firstFocus.requestFocus() } }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF211531), background, Color(0xFF141026)))
        ),
    ) {
        val wide = maxWidth >= 700.dp
        val short = maxHeight < 560.dp
        val tileHeight = if (wide) (if (short) 126.dp else 222.dp) else (if (short) 82.dp else 118.dp)
        val edge = if (wide) 48.dp else 20.dp
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = edge, vertical = if (short) 10.dp else 25.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painter = painterResource(R.drawable.moviotv_launcher), contentDescription = null, modifier = Modifier.size(if (wide) 62.dp else 48.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.moviotv_brand_movio), color = Color.White, fontWeight = FontWeight.Black, fontSize = if (wide) 38.sp else 29.sp)
                Text(stringResource(R.string.moviotv_brand_tv), color = accent, fontWeight = FontWeight.Black, fontSize = if (wide) 38.sp else 29.sp)
            }
            Text(stringResource(R.string.moviotv_credit), color = muted, fontSize = 12.sp)
            Spacer(Modifier.height(if (short) 10.dp else 22.dp))
            Text(stringResource(R.string.moviotv_welcome), color = Color.White, fontSize = if (wide) 24.sp else 19.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(if (short) 10.dp else 18.dp))
            if (wide) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    MovioTile(stringResource(R.string.moviotv_home_live), stringResource(R.string.moviotv_watch_channels), stringResource(R.string.moviotv_icon_play), tileHeight, onLiveTv, onFocused, Modifier.weight(1f), firstFocus)
                    MovioTile(stringResource(R.string.moviotv_home_movies), stringResource(R.string.moviotv_browse_films), stringResource(R.string.moviotv_icon_movies), tileHeight, onMovies, onFocused, Modifier.weight(1f))
                    MovioTile(stringResource(R.string.moviotv_home_series), stringResource(R.string.moviotv_watch_episodes), stringResource(R.string.moviotv_icon_series), tileHeight, onSeries, onFocused, Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(if (short) 8.dp else 12.dp)) {
                    MovioTile(stringResource(R.string.moviotv_home_live), stringResource(R.string.moviotv_watch_channels), stringResource(R.string.moviotv_icon_play), tileHeight, onLiveTv, onFocused, Modifier.fillMaxWidth(), firstFocus)
                    MovioTile(stringResource(R.string.moviotv_home_movies), stringResource(R.string.moviotv_browse_films), stringResource(R.string.moviotv_icon_movies), tileHeight, onMovies, onFocused, Modifier.fillMaxWidth())
                    MovioTile(stringResource(R.string.moviotv_home_series), stringResource(R.string.moviotv_watch_episodes), stringResource(R.string.moviotv_icon_series), tileHeight, onSeries, onFocused, Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(if (short) 10.dp else 20.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(stringResource(R.string.moviotv_interface), color = muted, fontSize = 11.sp, modifier = Modifier.padding(end = 3.dp))
                listOf(MovioLayoutChoice.AUTO to stringResource(R.string.moviotv_mode_auto), MovioLayoutChoice.MOBILE to stringResource(R.string.moviotv_mode_touch), MovioLayoutChoice.TV to stringResource(R.string.moviotv_brand_tv)).forEach { (choice, label) ->
                    val active = layoutChoice == choice
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(20.dp))
                            .background(if (active) Color(0xFF7143A5) else Color(0xFF2B2040))
                            .border(1.dp, if (active) accent else Color(0xFF5A486E), RoundedCornerShape(20.dp))
                            .clickable { onLayoutChoice(choice) }
                            .padding(horizontal = 13.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(label, color = Color.White, fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
private fun MovioTile(
    title: String, subtitle: String, symbol: String, height: Dp,
    onClick: () -> Unit, onFocused: () -> Unit, modifier: Modifier, focus: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
            .height(height)
            .scale(if (focused) 1.015f else 1f)
            .clip(shape)
            .background(Brush.linearGradient(if (focused) listOf(Color(0xFF7442A9), Color(0xFF361B52)) else listOf(Color(0xFF39244F), Color(0xFF231934))))
            .border(if (focused) 2.dp else 1.dp, if (focused) accent else Color(0xFF694E7D), shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(symbol, color = accent, fontSize = if (height < 110.dp) 22.sp else 32.sp)
            Text(title, color = Color.White, fontSize = if (height < 110.dp) 16.sp else 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(subtitle, color = muted, fontSize = 11.sp, textAlign = TextAlign.Center)
        }
    }
}
