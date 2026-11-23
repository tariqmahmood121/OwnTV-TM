package tv.own.owntv.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import tv.own.owntv.BuildConfig
import tv.own.owntv.R
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * MovioXP's user-facing identity. Do not expose the legacy studio/tree branding.
 * Legally required original-code copyright and GPLv3 notices remain available on demand.
 */
@Composable
internal fun XpAboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val palette = OwnTVTheme.colors
    var showLegal by remember { mutableStateOf(false) }
    val licence by remember(showLegal) {
        mutableStateOf(if (!showLegal) "" else runCatching {
            context.assets.open("movioxp_licences/gpl-3.0.txt").bufferedReader().use { it.readText() }
        }.getOrDefault(""))
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 640.dp)
                .clip(RoundedCornerShape(22.dp)).background(palette.background)
                .padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.moviotv_xp_about_heading),
                color = palette.primary, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.moviotv_xp_about_version, BuildConfig.VERSION_NAME),
                color = palette.onSurfaceVariant, fontSize = 15.sp)
            Text(stringResource(R.string.moviotv_xp_about_description),
                color = palette.onSurface, fontSize = 15.sp)
            Text(stringResource(R.string.moviotv_xp_about_stream_notice),
                color = palette.onSurfaceVariant, fontSize = 13.sp)
            XpSettingsOption(stringResource(R.string.moviotv_xp_about_licences), selected = showLegal) {
                showLegal = !showLegal
            }
            if (showLegal) {
                Text(stringResource(R.string.moviotv_xp_about_attribution),
                    color = palette.onSurface, fontSize = 14.sp)
                Text(stringResource(R.string.moviotv_xp_about_source),
                    color = palette.onSurfaceVariant, fontSize = 13.sp)
                Text(stringResource(R.string.moviotv_xp_about_font_licences),
                    color = palette.onSurfaceVariant, fontSize = 13.sp)
                Text(licence, color = palette.onSurfaceVariant, fontSize = 11.sp)
            }
            XpSettingsOption(stringResource(R.string.common_close)) { onDismiss() }
        }
    }
}
