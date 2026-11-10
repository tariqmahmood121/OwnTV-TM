package tv.own.owntv.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

/** Per-window screen brightness and hardware music volume. Never modifies system brightness. */
@Composable
internal fun MovioTouchGestures(onTap: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) {
        var current: Context? = context
        var match: Activity? = null
        while (current != null) {
            if (current is Activity) { match = current; break }
            current = (current as? ContextWrapper)?.baseContext
        }
        match
    }
    val audio = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val volumeMax = remember(audio) { audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 1 }
    var brightness by remember(activity) {
        mutableFloatStateOf(activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f }
            ?: (runCatching { android.provider.Settings.System.getInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f }.getOrDefault(0.5f)))
    }
    var volume by remember(audio) {
        mutableFloatStateOf((audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0).toFloat() / volumeMax)
    }
    var activeSide by remember { mutableStateOf<Boolean?>(null) }
    var feedbackTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(feedbackTick) {
        if (feedbackTick > 0) { delay(1200); activeSide = null }
    }
    Box(Modifier.fillMaxSize()) {
        listOf(true, false).forEach { left ->
            Box(
                modifier = Modifier.align(if (left) Alignment.CenterStart else Alignment.CenterEnd)
                    .fillMaxHeight().fillMaxWidth(0.5f)
                    .pointerInput(left, activity, audio) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                activeSide = left
                                feedbackTick++
                            },
                            onVerticalDrag = { change, distance ->
                                val increment = -distance / size.height.toFloat().coerceAtLeast(1f)
                                if (left) {
                                    brightness = (brightness + increment).coerceIn(0.02f, 1f)
                                    activity?.window?.let { window ->
                                        val params = window.attributes
                                        params.screenBrightness = brightness
                                        window.attributes = params
                                    }
                                } else {
                                    volume = (volume + increment).coerceIn(0f, 1f)
                                    audio?.setStreamVolume(AudioManager.STREAM_MUSIC, (volume * volumeMax).toInt(), 0)
                                }
                                feedbackTick++
                                change.consume()
                            },
                            onDragEnd = { feedbackTick++ },
                            onDragCancel = { feedbackTick++ },
                        )
                    }
                    .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) },
            )
        }
        if (activeSide == true) MovioProgressRing(stringResource(R.string.moviotv_brightness_symbol), brightness, Modifier.align(Alignment.CenterStart).padding(start = 40.dp))
        if (activeSide == false) MovioProgressRing(stringResource(R.string.moviotv_volume_symbol), volume, Modifier.align(Alignment.CenterEnd).padding(end = 40.dp))
    }
}

@Composable
private fun MovioProgressRing(symbol: String, level: Float, modifier: Modifier = Modifier) {
    Box(modifier.size(100.dp).background(Color(0xD9181128), CircleShape), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(7.dp)) {
            drawArc(Color(0x775F4776), startAngle = -90f, sweepAngle = 360f, useCenter = false,
                style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))
            drawArc(Color(0xFFB465FF), startAngle = -90f, sweepAngle = level.coerceIn(0f,1f) * 360f, useCenter = false,
                style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))
        }
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(symbol, color = Color.White, fontSize = 22.sp)
            Text(stringResource(R.string.moviotv_progress_percent, (level * 100).toInt()), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}
