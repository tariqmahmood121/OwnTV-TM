package tv.own.owntv.features.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tv.own.owntv.R
import tv.own.owntv.features.settings.MovioAboutScreen

@Composable
internal fun XpAboutDialog(onDismiss: () -> Unit) {
    XpQa5Dialog(
        stringResource(R.string.moviotv_xp_about_heading),
        stringResource(R.string.movioxp_close),
        onDismiss,
        width = 640.dp,
    ) {
        MovioAboutScreen(onBack = onDismiss, modifier = Modifier.weight(1f),
            forMovioXp = true, embeddedInDialog = true)
    }
}
