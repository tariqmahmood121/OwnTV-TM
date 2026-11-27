package tv.own.owntv.features.simulation

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.own.owntv.BuildConfig
import tv.own.owntv.R
import tv.own.owntv.core.theme.UiFontScale
import tv.own.owntv.core.theme.UiZoom
import tv.own.owntv.ui.theme.LocalUiFontScaleFactor

internal enum class XpTvResolution(val width: Int, val height: Int, val label: Int) {
    HD(1280, 720, R.string.movioxp_sim_720),
    FULL_HD(1920, 1080, R.string.movioxp_sim_1080),
    UHD(3840, 2160, R.string.movioxp_sim_4k),
}

internal enum class XpRemoteSide { LEFT, RIGHT, HIDDEN }

internal data class XpSimulatedScale(val zoom: Int = 100, val font: Int = 100)

internal fun xpSimulationFit(width: Int, height: Int, resolution: XpTvResolution): Float =
    minOf(width.toFloat() / resolution.width, height.toFloat() / resolution.height).coerceAtLeast(0f)

internal fun xpSimulationRemoteBelow(width: Float, height: Float): Boolean =
    width > 0f && height > 0f && width / height < 1.5f

internal fun xpSimulationAvailable(release: Boolean, debug: Boolean, versionName: String): Boolean =
    release && (debug || versionName.contains("-qa", ignoreCase = true) || versionName.matches(Regex("[0-9]+\\.[0-9]+ [A-Z]")))

internal val LocalXpSimulationStart = staticCompositionLocalOf<(() -> Unit)?> { null }
internal val LocalXpActiveSimulation = staticCompositionLocalOf<XpTvSimulation?> { null }

internal class XpTvSimulation(context: Context) {
    private val prefs = context.getSharedPreferences("movioxp_tv_simulation", Context.MODE_PRIVATE)
    private val scales = androidx.compose.runtime.mutableStateMapOf<Long, XpSimulatedScale>()

    // An interrupted or crashed session must never resume itself.
    var active by mutableStateOf(false)
        private set
    var resolution by mutableStateOf(XpTvResolution.FULL_HD)
    var side by mutableStateOf(XpRemoteSide.RIGHT)
    val available = xpSimulationAvailable(BuildConfig.MOVIOXP_RELEASE, BuildConfig.DEBUG, BuildConfig.VERSION_NAME)

    fun start() { if (available) active = true }
    fun stop() { active = false }

    fun scale(profile: Long): XpSimulatedScale = scales[profile] ?: XpSimulatedScale(
        UiZoom.clamp(prefs.getInt("zoom_$profile", 100)),
        UiFontScale.clamp(prefs.getInt("font_$profile", 100)),
    )

    fun setScale(profile: Long, zoom: Int, font: Int) {
        val scale = XpSimulatedScale(UiZoom.clamp(zoom), UiFontScale.clamp(font))
        scales[profile] = scale
        prefs.edit().putInt("zoom_$profile", scale.zoom).putInt("font_$profile", scale.font).apply()
    }
}

@Composable
internal fun XpTvSimulationViewport(
    activity: ComponentActivity,
    simulation: XpTvSimulation,
    scale: XpSimulatedScale,
    onExit: () -> Unit,
    content: @Composable () -> Unit,
) {
    DisposableEffect(activity) {
        val orientation = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { activity.requestedOrientation = orientation }
    }
    val resolution = simulation.resolution
    val settingsDensity = UiZoom.factor(scale.zoom) * 1.5f
    val fontScale = UiFontScale.factor(scale.font)
    val configuration = LocalConfiguration.current
    val virtual = remember(configuration, resolution, settingsDensity, fontScale) {
        Configuration(configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_TYPE_MASK.inv()) or Configuration.UI_MODE_TYPE_TELEVISION
            orientation = Configuration.ORIENTATION_LANDSCAPE
            screenWidthDp = (resolution.width / settingsDensity).toInt()
            screenHeightDp = (resolution.height / settingsDensity).toInt()
            smallestScreenWidthDp = screenHeightDp
            densityDpi = (settingsDensity * 160).toInt()
            this.fontScale = fontScale
        }
    }
    val view = LocalView.current
    val input = remember(activity, view) { XpSimulationInput(activity, view) }
    val overlay = remember { XpSimulationOverlay() }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val below = xpSimulationRemoteBelow(maxWidth.value, maxHeight.value)
        val viewport: @Composable (Modifier) -> Unit = { viewportModifier ->
            Layout(content = {
                CompositionLocalProvider(LocalConfiguration provides virtual,
                    LocalDensity provides Density(settingsDensity, fontScale),
                    LocalUiFontScaleFactor provides fontScale,
                    LocalXpActiveSimulation provides simulation,
                    LocalXpSimulationStart provides null,
                    LocalXpSimulationOverlay provides overlay) {
                    Box(Modifier.fillMaxSize()) {
                        content()
                        XpSimulationOverlayContent(overlay)
                    }
                }
            }, modifier = viewportModifier.clipToBounds()) { measurables, constraints ->
                val width = constraints.maxWidth
                val height = constraints.maxHeight
                if (width == 0 || height == 0) {
                    layout(width, height) {}
                } else {
                    val fit = xpSimulationFit(width, height, resolution)
                    val child = measurables.single().measure(Constraints.fixed(resolution.width, resolution.height))
                    layout(width, height) {
                        child.placeWithLayer(
                            ((width - resolution.width * fit) / 2).toInt(),
                            ((height - resolution.height * fit) / 2).toInt(),
                        ) {
                            transformOrigin = TransformOrigin(0f, 0f)
                            scaleX = fit
                            scaleY = fit
                        }
                    }
                }
            }
        }
        if (below) {
            Column(Modifier.fillMaxSize()) {
                viewport(Modifier.weight(1f).fillMaxWidth())
                if (simulation.side != XpRemoteSide.HIDDEN) XpSimulationBottomRemote(input)
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                if (simulation.side == XpRemoteSide.LEFT) XpSimulationRemote(input)
                viewport(Modifier.weight(1f).fillMaxHeight())
                if (simulation.side == XpRemoteSide.RIGHT) XpSimulationRemote(input)
            }
        }
        Row(Modifier.align(Alignment.TopCenter).padding(4.dp).background(Color(0xDD191923), RoundedCornerShape(8.dp)),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val resolutionLabel = stringResource(resolution.label)
            XpSimulationControl(resolutionLabel) {
                simulation.resolution = XpTvResolution.entries[(resolution.ordinal + 1) % XpTvResolution.entries.size]
            }
            val sideLabel = stringResource(when (simulation.side) {
                XpRemoteSide.LEFT -> if (below) R.string.movioxp_sim_touch_mode else R.string.movioxp_sim_remote_left
                XpRemoteSide.RIGHT -> if (below) R.string.movioxp_sim_touch_mode else R.string.movioxp_sim_remote_right
                XpRemoteSide.HIDDEN -> R.string.movioxp_sim_remote_show
            })
            XpSimulationControl(sideLabel) {
                simulation.side = if (below && simulation.side != XpRemoteSide.HIDDEN) XpRemoteSide.HIDDEN else when (simulation.side) {
                    XpRemoteSide.RIGHT -> XpRemoteSide.LEFT
                    XpRemoteSide.LEFT -> XpRemoteSide.HIDDEN
                    XpRemoteSide.HIDDEN -> XpRemoteSide.RIGHT
                }
            }
            XpSimulationControl(stringResource(R.string.movioxp_sim_exit), onClick = onExit)
        }
    }
}

@Composable
private fun XpSimulationControl(label: String, onClick: () -> Unit) {
    Text(label, color = Color.White, fontSize = 11.sp,
        modifier = Modifier.focusProperties { canFocus = false }.clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 8.dp))
}

@Composable
private fun XpSimulationRemote(input: XpSimulationInput) {
    Column(Modifier.width(118.dp).fillMaxHeight().background(Color(0xFF17171F)).verticalScroll(rememberScrollState())
        .padding(horizontal = 5.dp, vertical = 37.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            XpRemoteKey(input, R.string.movioxp_sim_up, KeyEvent.KEYCODE_DPAD_UP, Modifier.width(40.dp), true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            XpRemoteKey(input, R.string.movioxp_sim_left, KeyEvent.KEYCODE_DPAD_LEFT, Modifier.weight(1f), true)
            XpRemoteKey(input, R.string.movioxp_sim_ok, KeyEvent.KEYCODE_DPAD_CENTER, Modifier.weight(1f), false)
            XpRemoteKey(input, R.string.movioxp_sim_right, KeyEvent.KEYCODE_DPAD_RIGHT, Modifier.weight(1f), true)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            XpRemoteKey(input, R.string.movioxp_sim_down, KeyEvent.KEYCODE_DPAD_DOWN, Modifier.width(40.dp), true)
        }
        Spacer(Modifier.heightIn(min = 4.dp))
        XpRemoteKey(input, R.string.common_back, KeyEvent.KEYCODE_BACK, Modifier.fillMaxWidth(), false)
        XpRemoteKey(input, R.string.moviotv_nav_home, KeyEvent.KEYCODE_MOVE_HOME, Modifier.fillMaxWidth(), false)
        XpRemoteKey(input, R.string.movioxp_sim_menu, KeyEvent.KEYCODE_MENU, Modifier.fillMaxWidth(), false)
    }
}

@Composable
private fun XpSimulationBottomRemote(input: XpSimulationInput) {
    Row(Modifier.fillMaxWidth().background(Color(0xFF17171F)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        XpRemoteKey(input, R.string.movioxp_sim_up, KeyEvent.KEYCODE_DPAD_UP, Modifier.weight(1f), true)
        XpRemoteKey(input, R.string.movioxp_sim_down, KeyEvent.KEYCODE_DPAD_DOWN, Modifier.weight(1f), true)
        XpRemoteKey(input, R.string.movioxp_sim_left, KeyEvent.KEYCODE_DPAD_LEFT, Modifier.weight(1f), true)
        XpRemoteKey(input, R.string.movioxp_sim_right, KeyEvent.KEYCODE_DPAD_RIGHT, Modifier.weight(1f), true)
        XpRemoteKey(input, R.string.movioxp_sim_ok, KeyEvent.KEYCODE_DPAD_CENTER, Modifier.weight(1f), false)
        XpRemoteKey(input, R.string.common_back, KeyEvent.KEYCODE_BACK, Modifier.weight(1f), false)
        XpRemoteKey(input, R.string.moviotv_nav_home, KeyEvent.KEYCODE_MOVE_HOME, Modifier.weight(1f), false)
        XpRemoteKey(input, R.string.movioxp_sim_menu, KeyEvent.KEYCODE_MENU, Modifier.weight(1f), false)
    }
}

/** Deliver directly to the Compose Android view that owns the simulated focus tree. */
internal class XpSimulationInput(private val activity: ComponentActivity, private val owner: View) {
    fun send(down: Long, key: Int, action: Int, repeats: Int = 0, flags: Int = 0) {
        if (key == KeyEvent.KEYCODE_BACK) {
            if (action == KeyEvent.ACTION_UP && flags and KeyEvent.FLAG_CANCELED == 0) {
                activity.onBackPressedDispatcher.onBackPressed()
            }
            return
        }
        if (action == KeyEvent.ACTION_DOWN && repeats == 0 && (owner.isInTouchMode || !owner.hasFocus())) {
            owner.requestFocusFromTouch()
        }
        owner.dispatchKeyEvent(KeyEvent(down, SystemClock.uptimeMillis(), action, key, repeats, 0,
            KeyCharacterMap.VIRTUAL_KEYBOARD, 0, flags, InputDevice.SOURCE_DPAD))
    }
}

@Composable
private fun XpRemoteKey(input: XpSimulationInput, label: Int, key: Int, modifier: Modifier, repeat: Boolean) {
    val title = stringResource(label)
    Box(modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF2A2734))
        .focusProperties { canFocus = false }.semantics {
            contentDescription = title
            onClick {
                val down = SystemClock.uptimeMillis()
                input.send(down, key, KeyEvent.ACTION_DOWN)
                input.send(down, key, KeyEvent.ACTION_UP)
                true
            }
        }
        .pointerInput(input, key, repeat) {
            detectTapGestures(onPress = {
                coroutineScope {
                    val down = SystemClock.uptimeMillis()
                    input.send(down, key, KeyEvent.ACTION_DOWN)
                    val repeating = if (repeat) launch {
                        delay(420)
                        var count = 1
                        while (true) {
                            input.send(down, key, KeyEvent.ACTION_DOWN, count++)
                            delay(90)
                        }
                    } else null
                    var released = false
                    try {
                        released = tryAwaitRelease()
                    } finally {
                        repeating?.cancel()
                        input.send(down, key, KeyEvent.ACTION_UP, flags = if (released) 0 else KeyEvent.FLAG_CANCELED)
                    }
                }
            })
        }, contentAlignment = Alignment.Center) {
        val icon = when (key) {
            KeyEvent.KEYCODE_DPAD_UP -> tv.own.owntv.ui.components.OwnTVIcon.CHEVRON_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> tv.own.owntv.ui.components.OwnTVIcon.CHEVRON_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> tv.own.owntv.ui.components.OwnTVIcon.CHEVRON_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> tv.own.owntv.ui.components.OwnTVIcon.CHEVRON
            else -> null
        }
        if (icon != null) tv.own.owntv.ui.components.OwnTVIcon(icon, Color.White, Modifier.size(19.dp))
        else Text(title, color = Color.White, fontSize = 12.sp)
    }
}
