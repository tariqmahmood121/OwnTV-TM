package tv.own.owntv.features.activation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.features.home.XpDialogButton
import tv.own.owntv.features.home.XpRemotePink
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.OwnTVTheme

/** A future root-level replacement screen. This composable has no route in QA5 or QA6. */
@Composable
internal fun XpActivationScreen(
    access: XpActivation.Access,
    onSignIn: () -> Unit,
    onCheck: () -> Unit,
    onUpdates: () -> Unit,
    onExit: () -> Unit,
) {
    val signInFocus = remember { FocusRequester() }
    BackHandler(onBack = onExit)
    LaunchedEffect(Unit) { runCatching { signInFocus.requestFocus() } }
    Box(Modifier.fillMaxSize().background(OwnTVTheme.colors.background), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val movio = stringResource(R.string.movioxp_logo_menu_movio)
            val xp = stringResource(R.string.movioxp_logo_menu_xp)
            Text(buildAnnotatedString {
                append(movio)
                pushStyle(SpanStyle(color = XpRemotePink))
                append(xp)
                pop()
            }, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            OwnTVIcon(OwnTVIcon.LOCK, XpRemotePink, Modifier.size(38.dp))
            Text(stringResource(when (access) {
                XpActivation.Access.BLOCKED -> R.string.movioxp_activation_blocked
                XpActivation.Access.EXPIRED -> R.string.movioxp_activation_expired
                else -> R.string.movioxp_activation_required
            }), color = OwnTVTheme.colors.onSurface, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(if (access == XpActivation.Access.EXPIRED) R.string.movioxp_activation_renew
                else R.string.movioxp_activation_contact), color = OwnTVTheme.colors.onSurfaceVariant, textAlign = TextAlign.Center)
            XpDialogButton(stringResource(R.string.moviotv_xp_central_login), OwnTVIcon.PERSON,
                requester = signInFocus, primary = true, onClick = onSignIn)
            XpDialogButton(stringResource(R.string.movioxp_activation_check), OwnTVIcon.REFRESH, onClick = onCheck)
            XpDialogButton(stringResource(R.string.movioxp_update_title), OwnTVIcon.INFO, onClick = onUpdates)
            Text(stringResource(R.string.moviotv_oak_studios), color = OwnTVTheme.colors.onSurface, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.moviotv_about_studio_subtitle), color = OwnTVTheme.colors.onSurfaceVariant)
        }
    }
}
