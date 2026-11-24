package tv.own.owntv.features.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.features.home.LocalXpTvControls
import tv.own.owntv.features.home.XpRemotePink
import tv.own.owntv.BuildConfig
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.theme.LocalStagePalette
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

@Composable
fun MovioAboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier, forMovioXp: Boolean = false) {
    BackHandler(onBack = onBack)
    val accent = stageAccent.accent
    val focusOutline = XpRemotePink.takeIf { forMovioXp && LocalXpTvControls.current }
    val context = LocalContext.current
    var showLegal by remember { mutableStateOf(false) }
    Column(
        modifier.fillMaxSize().background(LocalStagePalette.current.page)
            .verticalScroll(rememberScrollState()).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Canvas(Modifier.size(98.dp)) {
            val s = size.minDimension / 100f
            val w = 5f * s
            // Oak crown + ascending branches. Vector-drawn, no bitmap decode at launch.
            drawCircle(accent, radius = 30f * s,
                center = Offset(50f * s, 39f * s), style = Stroke(width = w))
            drawLine(accent, Offset(50f*s, 86f*s), Offset(50f*s, 37f*s), w, cap = StrokeCap.Round)
            drawLine(accent, Offset(50f*s, 65f*s), Offset(28f*s, 43f*s), w, cap = StrokeCap.Round)
            drawLine(accent, Offset(50f*s, 57f*s), Offset(72f*s, 32f*s), w, cap = StrokeCap.Round)
            drawLine(accent, Offset(34f*s, 89f*s), Offset(65f*s, 89f*s), w, cap = StrokeCap.Round)
        }
        Text(stringResource(R.string.moviotv_oak_studios), style = stageText(38, 800), color = accent)
        Text(stringResource(R.string.moviotv_about_studio_subtitle), style = stageText(16, 600), color = StageColors.Muted)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(if (forMovioXp) R.string.moviotv_xp_title else R.string.moviotv_app_name),
            style = stageText(28, 700), color = StageColors.Text)
        Text(stringResource(R.string.moviotv_about_version, BuildConfig.VERSION_NAME), style = stageText(17, 500), color = StageColors.Muted)
        Text(stringResource(R.string.moviotv_about_developed), style = stageText(18, 600), color = StageColors.Text)
        Text(stringResource(R.string.moviotv_about_created), style = stageText(18, 500), color = StageColors.Text)
        Spacer(Modifier.height(6.dp))
        // Oak Studios is our identity. Preserve the original MovioTV About design, and move
        // upstream GPL attribution into the legal view instead of the main studio description.
        StageButton(stringResource(R.string.moviotv_xp_about_licences), onClick = { showLegal = !showLegal }, focusOutline = focusOutline)
        if (showLegal) {
            Text(stringResource(R.string.moviotv_xp_about_attribution),
                style = stageText(15, 500), color = StageColors.Text)
            Text(stringResource(R.string.moviotv_xp_about_source),
                style = stageText(13, 400), color = StageColors.Muted)
            Text(stringResource(R.string.moviotv_xp_about_font_licences),
                style = stageText(13, 400), color = StageColors.Muted)
            val licence = remember(context) { runCatching {
                context.assets.open("movioxp_licences/gpl-3.0.txt").bufferedReader().use { it.readText() }
            }.getOrDefault("") }
            Text(licence, style = stageText(11, 400), color = StageColors.Muted)
        }
        StageButton(stringResource(R.string.common_back), onClick = onBack, focusOutline = focusOutline)
    }
}
