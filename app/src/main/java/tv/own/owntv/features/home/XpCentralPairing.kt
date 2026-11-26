package tv.own.owntv.features.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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

    XpQa5Dialog(stringResource(R.string.movioxp_mobile_login), stringResource(R.string.common_cancel), onDismiss) {
        LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            val session = state
            if (session != null && session.failure == null) {
                Text(stringResource(R.string.movioxp_mobile_scan), color = OwnTVTheme.colors.onSurface)
                Text(stringResource(R.string.movioxp_mobile_trusted_network), color = OwnTVTheme.colors.onSurface)
                session.qr?.let { bitmap ->
                    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Image(bitmap.asImageBitmap(), stringResource(R.string.common_qr_code_companion_url),
                            Modifier.size(224.dp).background(Color.White).padding(8.dp))
                    }
                }
                Text(session.url, color = OwnTVTheme.colors.onSurface)
                Text(stringResource(R.string.moviotv_xp_remote_tv_code, session.pin), color = OwnTVTheme.colors.primary)
            }
            Text(session?.failure ?: stringResource(
                if (session != null) R.string.movioxp_mobile_waiting else status,
            ), color = OwnTVTheme.colors.onSurface)
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
