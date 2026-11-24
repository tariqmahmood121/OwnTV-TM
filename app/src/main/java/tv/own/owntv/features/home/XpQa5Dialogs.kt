package tv.own.owntv.features.home

import androidx.compose.foundation.background
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
                    Text(title, modifier = Modifier.weight(1f), color = OwnTVTheme.colors.onSurface,
                        fontSize = if (wide) 26.sp else 21.sp, fontWeight = FontWeight.Bold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    XpAction(closeLabel, false, compact = true, onClick = onDismiss)
                }
                content(wide)
                primaryAction?.invoke()
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun XpDialogActions(content: @Composable () -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
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
