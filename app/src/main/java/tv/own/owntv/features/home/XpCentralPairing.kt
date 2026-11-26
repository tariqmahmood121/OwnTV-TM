package tv.own.owntv.features.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import tv.own.owntv.R
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.OwnTVTheme

@Composable
internal fun XpCentralPairingDialog(apiBase: String, onDismiss: () -> Unit, onConnected: (XpCentralConnection) -> Unit) {
    val context = LocalContext.current
    val server = remember(context) { XpAddonRemoteServer(context, credentials = true) }
    val state by server.state.collectAsStateWithLifecycle()
    val connected by rememberUpdatedState(onConnected)
    val cancelFocus = remember { FocusRequester() }
    var status by remember { mutableStateOf(R.string.movioxp_mobile_opening) }
    var attempt by remember { mutableStateOf(0) }

    LaunchedEffect(server, attempt) {
        status = R.string.movioxp_mobile_opening
        try {
            withContext(Dispatchers.IO) { server.start() }
            awaitCancellation()
        } finally {
            server.close()
        }
    }
    LaunchedEffect(server, apiBase) {
        server.submissions.collect { submission ->
            val account = submission as? XpAddonRemoteServer.Submission.Account ?: return@collect
            status = R.string.moviotv_xp_central_connecting
            val connection = try {
                XpCentralAddonSync.signIn(apiBase, account.username, account.password)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                status = R.string.movioxp_mobile_login_failed
                return@collect
            }
            connected(connection)
        }
    }

    XpQa5Dialog(stringResource(R.string.movioxp_mobile_login), stringResource(R.string.common_cancel), onDismiss, width = 370.dp) {
        LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }
        BoxWithConstraints(Modifier.weight(1f, fill = false).fillMaxWidth()) {
            val qrSize = (maxHeight - 76.dp).coerceIn(72.dp, 148.dp)
            val session = state
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                if (session != null && session.failure == null) {
                    session.qr?.let { bitmap ->
                        Image(bitmap.asImageBitmap(), stringResource(R.string.common_qr_code_companion_url),
                            Modifier.size(qrSize).background(Color.White).padding(5.dp))
                    }
                    Text(stringResource(R.string.moviotv_xp_remote_tv_code, session.pin), color = XpRemotePink,
                        fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(session.url, color = OwnTVTheme.colors.onSurface, fontSize = 13.sp, maxLines = 1)
                } else {
                    Text(session?.failure ?: stringResource(status), color = OwnTVTheme.colors.onSurface)
                }
            }
        }
        XpDialogActions {
            XpDialogButton(stringResource(R.string.common_cancel), OwnTVIcon.CLOSE,
                requester = cancelFocus, onClick = onDismiss)
            if (state?.failure != null || status == R.string.movioxp_mobile_login_failed) {
                XpDialogButton(stringResource(R.string.movioxp_mobile_retry), OwnTVIcon.CHEVRON) { attempt++ }
            }
        }
    }
}

@Composable
internal fun XpCentralSignOutDialog(action: Int, enabled: Boolean, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val cancelFocus = remember { FocusRequester() }
    val label = stringResource(action)
    XpQa5Dialog(label, stringResource(R.string.common_cancel), onCancel) {
        LaunchedEffect(Unit) { cancelFocus.requestFocus() }
        Text(stringResource(R.string.movioxp_confirm_account_logout), color = OwnTVTheme.colors.onSurface)
        XpDialogActions {
            XpDialogButton(stringResource(R.string.common_cancel), OwnTVIcon.CLOSE,
                requester = cancelFocus, onClick = onCancel)
            XpDialogButton(label, if (action == R.string.movioxp_switch_account) OwnTVIcon.SWAP else OwnTVIcon.POWER,
                enabled = enabled, onClick = onConfirm)
        }
    }
}


@Composable
internal fun XpResumePrompt(positionMs: Long, onCancel: () -> Unit, onSelect: (Boolean) -> Unit) {
    val focus = remember { FocusRequester() }
    val seconds = positionMs / 1_000
    val time = String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", seconds / 3_600, seconds / 60 % 60, seconds % 60)
    XpQa5Dialog(stringResource(R.string.moviotv_xp_continue_watching), stringResource(R.string.common_cancel), onCancel, width = 390.dp) {
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        XpDialogActions(stacked = true) {
            XpDialogButton(stringResource(R.string.movioxp_resume_at, time), OwnTVIcon.PLAY, requester = focus, primary = true) { onSelect(false) }
            XpDialogButton(stringResource(R.string.movioxp_restart_content), OwnTVIcon.REFRESH) { onSelect(true) }
            XpDialogButton(stringResource(R.string.common_cancel), OwnTVIcon.CLOSE, onClick = onCancel)
        }
    }
}
