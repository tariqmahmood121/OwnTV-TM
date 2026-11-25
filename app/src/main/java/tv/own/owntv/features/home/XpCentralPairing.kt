package tv.own.owntv.features.home

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import tv.own.owntv.R
import tv.own.owntv.core.companion.CompanionLink
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.OwnTVTheme
import java.util.concurrent.TimeUnit

private class CentralPairing(rawApiBase: String) {
    private val api = XpCentralAddonSync.normalizeApiBase(rawApiBase).toHttpUrl()
    private val mobile = api.newBuilder().encodedPath(
        api.encodedPath.removeSuffix("index.php/") + "mobile-login/",
    ).build()

    // The TV secret stays in POST bodies and is never included in the QR URL.
    private var tvSecret: String? = null

    fun start(): String {
        val response = post("start")
        tvSecret = response.getString("tv_secret")
        val phoneSecret = response.getString("phone_secret")
        return mobile.newBuilder().fragment(phoneSecret).build().toString()
    }

    fun claim(): XpCentralConnection? {
        val response = post("claim")
        return when (response.getString("state")) {
            "waiting" -> null
            "expired" -> throw PairingExpired()
            "connected" -> XpCentralConnection(
                api.toString(),
                response.getString("token").also { require(it.isNotBlank()) },
                response.getLong("account_id").also { require(it > 0L) },
                response.getString("username"),
            ).also { tvSecret = null }
            else -> throw IllegalStateException()
        }
    }

    fun cancel() {
        if (tvSecret != null) post("cancel")
    }

    private fun post(action: String): JSONObject {
        val params = JSONObject().put("action", action).put("tv_secret", tvSecret)
        val request = Request.Builder().url(mobile.resolve("pair.php")!!)
            .post(params.toString().toRequestBody(jsonType))
            .build()
        return client.newCall(request).execute().use { response ->
            check(response.isSuccessful)
            JSONObject(checkNotNull(response.body).string())
        }
    }

    private class PairingExpired : IllegalStateException()

    companion object {
        private val jsonType = "application/json".toMediaType()
        private val client = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .callTimeout(8, TimeUnit.SECONDS)
            .build()

        fun message(error: Exception): Int = if (error is PairingExpired) {
            R.string.movioxp_mobile_expired
        } else R.string.movioxp_mobile_unavailable
    }
}

@Composable
internal fun XpCentralPairingDialog(apiBase: String, onDismiss: () -> Unit, onConnected: (XpCentralConnection) -> Unit) {
    val connected by rememberUpdatedState(onConnected)
    val cancelFocus = remember { FocusRequester() }
    var qr by remember { mutableStateOf<Bitmap?>(null) }
    var status by remember { mutableStateOf(R.string.movioxp_mobile_waiting) }
    val label = stringResource(R.string.movioxp_mobile_login)

    XpQa5Dialog(label, stringResource(R.string.common_cancel), onDismiss) {
        LaunchedEffect(Unit) { cancelFocus.requestFocus() }
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            qr?.let { bitmap ->
                Text(stringResource(R.string.movioxp_mobile_scan), color = OwnTVTheme.colors.onSurface)
                Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Image(
                        bitmap.asImageBitmap(),
                        stringResource(R.string.common_qr_code_companion_url),
                        Modifier.sizeIn(maxWidth = 224.dp, maxHeight = 224.dp)
                            .background(Color.White).padding(8.dp),
                    )
                }
            }
            Text(stringResource(status), color = OwnTVTheme.colors.onSurface)
        }
        XpDialogButton(stringResource(R.string.common_cancel), OwnTVIcon.CLOSE,
            requester = cancelFocus, onClick = onDismiss)
    }
    LaunchedEffect(apiBase) {
        var pairing: CentralPairing? = null
        try {
            val session = CentralPairing(apiBase)
            pairing = session
            val started = SystemClock.elapsedRealtime()
            qr = checkNotNull(withContext(Dispatchers.IO) { CompanionLink.renderQr(session.start()) })
            while (SystemClock.elapsedRealtime() - started < 180_000L) {
                delay(2_500L)
                val connection = withContext(Dispatchers.IO) { session.claim() } ?: continue
                connected(connection)
                return@LaunchedEffect
            }
            status = R.string.movioxp_mobile_expired
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            status = CentralPairing.message(error)
        } finally {
            qr = null
            withContext(NonCancellable + Dispatchers.IO) { runCatching { pairing?.cancel() } }
        }
    }
}
