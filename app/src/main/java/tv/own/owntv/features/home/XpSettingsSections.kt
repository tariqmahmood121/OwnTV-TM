package tv.own.owntv.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.core.theme.AccentColor
import tv.own.owntv.core.theme.AppFontFamily
import tv.own.owntv.core.theme.ThemeMode
import tv.own.owntv.core.theme.UiFontScale
import tv.own.owntv.core.theme.UiZoom
import tv.own.owntv.features.settings.SettingsViewModel
import tv.own.owntv.features.shell.ShellViewModel
import tv.own.owntv.features.shell.components.fontFamilyLabel
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.ui.theme.labelRes

/**
 * Small single-column accordions: unlike the legacy TV settings layout, these keep labels readable
 * on phones. Every section starts folded and preserves its own state while Settings stays open.
 */
@Composable
internal fun XpSettingsAccordion(
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val palette = OwnTVTheme.colors
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(17.dp)
    val tv = LocalXpTvControls.current
    val pink = focused && tv
    Column(modifier.fillMaxWidth().clip(shape).background(palette.surfaceContainerLow)
        .border(if (focused && !tv) 2.dp else 1.dp,
            if (focused && !tv) palette.primary else palette.outline.copy(alpha = .45f), shape)) {
        Row(
            Modifier.fillMaxWidth().border(if (pink) 2.dp else 0.dp,
                if (pink) XpRemotePink else Color.Transparent, shape)
                .onFocusChanged { focused = it.isFocused }.clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = if (pink) XpRemotePink else palette.onSurface, fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 2)
                Text(summary, color = if (pink) XpRemotePink else palette.onSurfaceVariant, fontSize = 12.sp,
                    maxLines = if (expanded) 3 else 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                stringResource(if (expanded) R.string.moviotv_xp_settings_collapse else R.string.moviotv_xp_settings_expand),
                color = if (pink) XpRemotePink else palette.primary, fontSize = 20.sp,
            )
        }
        if (expanded) {
            Column(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) { content() }
        }
    }
}

/** A real, keyboard/remote-accessible setting action, not an oversized TV action pill. */
@Composable
internal fun XpSettingsOption(
    title: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val palette = OwnTVTheme.colors
    val shape = RoundedCornerShape(12.dp)
    var focused by remember { mutableStateOf(false) }
    val tv = LocalXpTvControls.current
    val pink = focused && tv
    Text(
        title,
        color = if (pink) XpRemotePink else if (focused || selected) palette.primary else palette.onSurface,
        fontSize = 15.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        maxLines = 3,
        modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp).clip(shape)
            .background(if (selected || (focused && !tv)) palette.primary.copy(alpha = .15f) else palette.surfaceContainerHigh.copy(alpha = .45f))
            .border(if (focused) 2.dp else 1.dp,
                if (pink) XpRemotePink else if (focused || selected) palette.primary else Color.Transparent, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

/**
 * Compact, focusable Home row controls for phone and D-pad TV remotes.
 * One toggle and two reorder controls, no drag gestures required on TV.
 */
@Composable
internal fun XpHomeCategoryControls(
    title: String,
    enabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        XpHomeButton(
            label = stringResource(R.string.movioxp_home_row_toggle, title,
                stringResource(if (enabled) R.string.movioxp_home_on else R.string.movioxp_home_off)),
            selected = enabled, active = true, modifier = Modifier.weight(1f), onClick = onToggle,
        )
        XpHomeButton(
            label = stringResource(R.string.movioxp_home_arrow_up), accessibility = stringResource(R.string.movioxp_home_move_up, title),
            active = canMoveUp, modifier = Modifier.width(49.dp), onClick = onMoveUp,
        )
        XpHomeButton(
            label = stringResource(R.string.movioxp_home_arrow_down), accessibility = stringResource(R.string.movioxp_home_move_down, title),
            active = canMoveDown, modifier = Modifier.width(49.dp), onClick = onMoveDown,
        )
    }
}

@Composable
private fun XpHomeButton(
    label: String,
    accessibility: String = label,
    selected: Boolean = false,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = OwnTVTheme.colors
    var focused by remember { mutableStateOf(false) }
    val tv = LocalXpTvControls.current
    val pink = focused && tv
    val shape = RoundedCornerShape(10.dp)
    Text(
        label,
        color = if (!active) palette.onSurfaceVariant.copy(alpha = .55f)
                else if (pink) XpRemotePink else if (focused || selected) palette.primary else palette.onSurface,
        fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = modifier.heightIn(min = 47.dp).clip(shape)
            .background(if (selected || (focused && !tv)) palette.primary.copy(alpha = .15f)
                else palette.surfaceContainerHigh.copy(alpha = .45f))
            .border(if (focused) 2.dp else 1.dp,
                if (pink) XpRemotePink else if (focused) palette.primary else Color.Transparent, shape)
            .semantics { contentDescription = accessibility }
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = active, onClick = onClick)

            .padding(horizontal = 12.dp, vertical = 12.dp),
    )
}

@Composable
internal fun XpSettingsFieldLabel(title: String) {
    Text(title, color = OwnTVTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun XpChoice(title: String, selected: Boolean, onClick: () -> Unit) {
    val palette = OwnTVTheme.colors
    val shape = RoundedCornerShape(12.dp)
    var focused by remember { mutableStateOf(false) }
    val tv = LocalXpTvControls.current
    val pink = focused && tv
    Text(title, color = if (pink) XpRemotePink else if (selected || focused) palette.primary else palette.onSurface,
        fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier.clip(shape)
            .background(if (selected || (focused && !tv)) palette.primary.copy(alpha = .15f) else palette.surfaceContainerHigh)
            .border(if (focused) 2.dp else 1.dp, if (pink) XpRemotePink else if (selected || focused) palette.primary else Color.Transparent, shape)
            .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 12.dp))
}

@Composable
internal fun XpAppearanceOptions(
    onMore: () -> Unit,
    preview: (zoom: Int, font: Int) -> Unit = { _, _ -> },
) {
    val shell: ShellViewModel = koinViewModel()
    val settings: SettingsViewModel = koinViewModel()
    val theme by shell.themeMode.collectAsStateWithLifecycle()
    val accent by shell.accent.collectAsStateWithLifecycle()
    val font by shell.fontCustomization.collectAsStateWithLifecycle()
    val zoom by shell.uiZoomPercent.collectAsStateWithLifecycle()
    val muted = OwnTVTheme.colors.onSurfaceVariant
    XpSettingsFieldLabel(stringResource(R.string.moviotv_xp_display_theme))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeMode.entries.forEach { mode ->
            val label = stringResource(when (mode) {
                ThemeMode.DARK -> R.string.settings_theme_dark
                ThemeMode.LIGHT -> R.string.settings_theme_light
                ThemeMode.SYSTEM -> R.string.settings_theme_system
            })
            XpChoice(label, mode == theme) { settings.setThemeMode(mode) }
        }
    }
    XpSettingsFieldLabel(stringResource(R.string.moviotv_xp_display_accent))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AccentColor.entries.forEach { option ->
            XpChoice(stringResource(option.labelRes), option == accent) {
                settings.setCustomAccent("")
                settings.setAccent(option)
            }
        }
    }
    XpSettingsFieldLabel(stringResource(R.string.moviotv_xp_display_font))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppFontFamily.entries.forEach { family ->
            XpChoice(fontFamilyLabel(family), family == font.mainFamily) {
                shell.setFontCustomization(font.copy(mainFamily = family, popupFamily = family))
            }
        }
    }
    XpSettingsFieldLabel(stringResource(R.string.settings_font_size))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        XpChoice(stringResource(R.string.moviotv_xp_settings_decrease), false) {
            preview(zoom, UiFontScale.clamp(font.sizePercent - UiFontScale.STEP))
        }
        Text(stringResource(R.string.common_percent, font.sizePercent), color = OwnTVTheme.colors.onSurface, fontSize = 16.sp)
        XpChoice(stringResource(R.string.moviotv_xp_settings_increase), false) {
            preview(zoom, UiFontScale.clamp(font.sizePercent + UiFontScale.STEP))
        }
    }
    XpSettingsFieldLabel(stringResource(R.string.moviotv_xp_display_zoom))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        XpChoice(stringResource(R.string.moviotv_xp_settings_decrease), false) {
            preview(UiZoom.clamp(zoom - 5), font.sizePercent)
        }
        Text(stringResource(R.string.common_percent, zoom), color = OwnTVTheme.colors.onSurface, fontSize = 16.sp)
        XpChoice(stringResource(R.string.moviotv_xp_settings_increase), false) {
            preview(UiZoom.clamp(zoom + 5), font.sizePercent)
        }
    }
    Text(stringResource(R.string.moviotv_xp_display_help), color = muted, fontSize = 12.sp)
    XpSettingsOption(stringResource(R.string.moviotv_xp_display_more)) { onMore() }
}
