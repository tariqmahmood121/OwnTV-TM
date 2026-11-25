package tv.own.owntv.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import tv.own.owntv.ui.components.OwnTVIcon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import org.json.JSONObject
import tv.own.owntv.ui.theme.OwnTVTheme

@Composable
internal fun XpQa5Dialog(
    title: String,
    closeLabel: String,
    onDismiss: () -> Unit,
    width: Dp = 480.dp,
    primaryAction: (@Composable () -> Unit)? = null,
    titleIcon: OwnTVIcon? = null,
    titleContent: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.(wide: Boolean) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center) {
            val wide = minOf(maxWidth, width) >= 610.dp
            Column(
                Modifier.widthIn(max = width).fillMaxWidth().heightIn(max = maxHeight)
                    .clip(RoundedCornerShape(22.dp)).background(OwnTVTheme.colors.background).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (titleIcon != null) OwnTVIcon(titleIcon, OwnTVTheme.colors.primary, Modifier.size(22.dp))
                    Box(Modifier.weight(1f)) {
                        if (titleContent != null) titleContent()
                        else Text(title, color = OwnTVTheme.colors.onSurface,
                            fontSize = if (wide) 26.sp else 21.sp, fontWeight = FontWeight.Bold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    XpDialogButton(closeLabel, OwnTVIcon.CLOSE, iconOnly = true, onClick = onDismiss)
                }
                content(wide)
                primaryAction?.invoke()
            }
        }
    }
}

private val LocalXpDialogCellWidth = staticCompositionLocalOf<Dp?> { null }

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun XpDialogActions(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 8.dp
        val count = if (maxWidth >= 570.dp) 3 else if (maxWidth >= 250.dp) 2 else 1
        val itemWidth = with(LocalDensity.current) {
            val available = constraints.maxWidth - gap.roundToPx() * (count - 1)
            (available / count).toDp()
        }
        CompositionLocalProvider(LocalXpDialogCellWidth provides itemWidth) {
            FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = count,
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalArrangement = Arrangement.spacedBy(gap)) { content() }
        }
    }
}

@Composable
internal fun XpDialogButton(
    label: String,
    icon: OwnTVIcon,
    enabled: Boolean = true,
    primary: Boolean = false,
    iconOnly: Boolean = false,
    requester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val cellWidth = LocalXpDialogCellWidth.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val layout = if (iconOnly) Modifier.size(44.dp) else
        (if (cellWidth == null) Modifier.fillMaxWidth() else Modifier.width(cellWidth)).height(56.dp)
    val foreground = when {
        !enabled -> colors.onSurfaceVariant
        focused -> XpRemotePink
        else -> colors.onSurface
    }
    Box(layout.clip(shape)
        .background(if (primary && enabled) colors.primary.copy(alpha = .22f) else colors.surfaceContainerLow)
        .border(if (focused) 2.dp else 1.dp, if (focused) XpRemotePink else colors.outline.copy(alpha = .35f), shape)
        .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
        .semantics { contentDescription = label }
        .onFocusChanged { focused = it.isFocused }
        .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            OwnTVIcon(icon, foreground, Modifier.size(18.dp))
            if (!iconOnly) Text(label, color = foreground, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

internal data class XpDetailMetadata(
    val genres: List<String>,
    val runtime: String?,
    val cast: List<String>,
    val directors: List<String>,
    val writers: List<String>,
) {
    companion object {
        fun parse(meta: JSONObject): XpDetailMetadata {
            fun names(key: String): List<String> {
                val array = meta.optJSONArray(key)
                if (array == null) return (meta.opt(key) as? String)?.trim()?.takeIf(String::isNotBlank)?.let(::listOf).orEmpty()
                return (0 until array.length()).mapNotNull { index ->
                    when (val value = array.opt(index)) {
                        is String -> value.trim()
                        is JSONObject -> value.optString("name").trim()
                        else -> null
                    }?.takeIf(String::isNotBlank)
                }.distinct()
            }
            val runtime = meta.opt("runtime")?.takeUnless { it == JSONObject.NULL }
                ?.toString()?.trim()?.takeIf(String::isNotBlank)
            return XpDetailMetadata(names("genres"), runtime, names("cast"), names("director"), names("writer"))
        }
    }
}
