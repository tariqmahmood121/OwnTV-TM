package tv.own.owntv.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import tv.own.owntv.features.settings.MovioAboutScreen
import tv.own.owntv.ui.theme.OwnTVTheme

/** Reuse MovioTV's familiar Oak Studios About design for MovioXP. */
@Composable
internal fun XpAboutDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = 340.dp, max = 620.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(OwnTVTheme.colors.background),
        ) {
            MovioAboutScreen(
                onBack = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                forMovioXp = true,
            )
        }
    }
}
