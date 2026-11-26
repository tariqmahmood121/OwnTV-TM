package tv.own.owntv.features.home

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tv.own.owntv.R
import tv.own.owntv.core.companion.CompanionLink
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom

/** The MovioTV companion pattern: temporary LAN listener, PIN form and a CompanionLink QR. */
internal class XpAddonRemoteServer(
    private val context: Context,
    private val credentials: Boolean = false,
) : Closeable {
    data class State(val url: String, val pin: String, val qr: Bitmap?, val failure: String? = null)

    sealed interface Submission {
        class Addon(val url: String) : Submission
        class Account(val username: String, val password: String) : Submission
    }

    private class Session(val pin: String, val listener: ServerSocket, val expiresAt: Long) {
        var client: Socket? = null
        var expiry: kotlinx.coroutines.Job? = null
        var attempts = 0
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<State?>(null)
    val state = _state.asStateFlow()
    private val _submissions = MutableSharedFlow<Submission>(extraBufferCapacity = 1)
    val submissions = _submissions.asSharedFlow()
    private var session: Session? = null

    @Synchronized
    fun start() {
        if (session != null) return
        _state.value = null
        val host = runCatching { InetAddress.getByName(URI(CompanionLink.lanUrl(PORT)).host) }.getOrNull()
        if (host == null || !host.isSiteLocalAddress) {
            _state.value = failure(R.string.movioxp_mobile_network_required)
            return
        }
        val listener = try {
            ServerSocket().apply {
                try {
                    bind(InetSocketAddress(host, 0))
                } catch (error: IOException) {
                    close()
                    throw error
                }
            }
        } catch (_: IOException) {
            _state.value = failure(R.string.moviotv_xp_remote_open_failed)
            return
        }
        val current = Session(
            SecureRandom().nextInt(1_000_000).toString().padStart(6, '0'),
            listener,
            SystemClock.elapsedRealtime() + LIFETIME_MS,
        )
        session = current
        current.expiry = scope.launch {
            delay(LIFETIME_MS)
            finish(current, failure(R.string.movioxp_mobile_expired))
        }
        scope.launch {
            try {
                val url = URI("http", null, host.hostAddress, listener.localPort, "/", null, null).toString()
                val qr = CompanionLink.renderQr(url)
                synchronized(this@XpAddonRemoteServer) {
                    if (session !== current) return@launch
                    _state.value = State(url, current.pin, qr)
                }
                while (isActive && !listener.isClosed) {
                    listener.accept().use { client ->
                        synchronized(this@XpAddonRemoteServer) {
                            if (session !== current) return@launch
                            current.client = client
                        }
                        try {
                            handle(current, client)
                        } catch (_: IOException) {
                            // A closed or timed-out phone connection does not end the session.
                        } finally {
                            synchronized(this@XpAddonRemoteServer) { current.client = null }
                        }
                    }
                }
            } catch (_: IOException) {
                finish(current, failure(R.string.moviotv_xp_remote_open_failed))
            }
        }
    }

    private fun handle(current: Session, client: Socket) {
        client.soTimeout = 8_000
        val request = readRequest(BufferedInputStream(client.getInputStream())) ?: return
        val body = request.body.toString(Charsets.UTF_8)
        val parameters = body.split('&').mapNotNull { pair ->
            val split = pair.indexOf('=')
            if (split < 0) null else decode(pair.substring(0, split)) to decode(pair.substring(split + 1))
        }.toMap()

        val response = synchronized(this) {
            if (session !== current || SystemClock.elapsedRealtime() >= current.expiresAt) return
            when {
                request.method == "GET" && request.path == "/" -> pinPage()
                request.method != "POST" || request.path !in setOf("/unlock", "/install") ->
                    errorPage(context.getString(R.string.moviotv_xp_remote_expired))
                !MessageDigest.isEqual(current.pin.toByteArray(), parameters["pin"].orEmpty().toByteArray()) -> {
                    current.attempts++
                    errorPage(context.getString(R.string.moviotv_xp_remote_code_mismatch))
                }
                request.path == "/unlock" -> if (credentials) accountPage(current.pin) else addonPage(current.pin)
                else -> {
                    val submission = if (credentials) {
                        val username = parameters["username"].orEmpty().trim()
                        val password = parameters["password"].orEmpty()
                        if (username.isNotBlank() && password.isNotEmpty()) Submission.Account(username, password) else null
                    } else {
                        parameters["url"].orEmpty().trim().takeIf(::validAddonUrl)?.let { Submission.Addon(it) }
                    }
                    if (submission == null) {
                        errorPage(context.getString(if (credentials) R.string.moviotv_xp_central_details_required else R.string.moviotv_xp_remote_invalid_url))
                    } else {
                        finish(current, null, keepClient = true)
                        _submissions.tryEmit(submission)
                        successPage()
                    }
                }
            }
        }
        try {
            writeResponse(client.getOutputStream(), response)
        } finally {
            if (current.attempts >= MAX_ATTEMPTS) finish(current, failure(R.string.movioxp_mobile_expired))
        }
    }

    private fun failure(message: Int) = State("", "", null, context.getString(message))

    @Synchronized
    private fun finish(current: Session, state: State?, keepClient: Boolean = false) {
        if (session !== current) return
        session = null
        current.expiry?.cancel()
        runCatching { current.listener.close() }
        if (!keepClient) runCatching { current.client?.close() }
        _state.value = state
    }

    private fun validAddonUrl(raw: String): Boolean = runCatching {
        val uri = URI(raw)
        (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) && !uri.host.isNullOrBlank()
    }.getOrDefault(false)

    private fun pinPage() = page(
        context.getString(R.string.moviotv_xp_remote_enter_code_title),
        "<p>${escape(context.getString(R.string.moviotv_xp_remote_enter_code_help))}</p><form method='post' action='/unlock'>" +
            "<label>${escape(context.getString(R.string.moviotv_xp_remote_code_label))}<input name='pin' inputmode='numeric' autocomplete='one-time-code' maxlength='6' required autofocus></label>" +
            "<button>${escape(context.getString(R.string.moviotv_xp_remote_continue))}</button></form>",
    )

    private fun addonPage(code: String) = page(
        context.getString(R.string.moviotv_xp_remote_add_catalogue_title),
        "<p>${escape(context.getString(R.string.moviotv_xp_remote_add_catalogue_help))}</p>" +
            "<form method='post' action='/install'><input type='hidden' name='pin' value='${escape(code)}'>" +
            "<label>${escape(context.getString(R.string.moviotv_xp_remote_url_label))}<input name='url' type='url' inputmode='url' placeholder='${escape(context.getString(R.string.moviotv_xp_remote_url_placeholder))}' required autofocus></label>" +
            "<button>${escape(context.getString(R.string.moviotv_xp_remote_add_to_tv))}</button></form>",
    )

    private fun accountPage(code: String) = page(
        context.getString(R.string.movioxp_account_login_title),
        "<p>${escape(context.getString(R.string.movioxp_mobile_trusted_network))}</p>" +
            "<form method='post' action='/install'><input type='hidden' name='pin' value='${escape(code)}'>" +
            "<label>${escape(context.getString(R.string.moviotv_xp_central_username_label))}<input name='username' autocomplete='username' required autofocus></label>" +
            "<label>${escape(context.getString(R.string.moviotv_xp_central_password_label))}<input name='password' type='password' autocomplete='current-password' required></label>" +
            "<button>${escape(context.getString(R.string.moviotv_xp_central_login))}</button></form>",
    )

    private fun successPage() = page(
        context.getString(R.string.moviotv_xp_remote_sent_title),
        "<p>${escape(context.getString(if (credentials) R.string.movioxp_mobile_sent else R.string.moviotv_xp_remote_sent_help))}</p>",
    )
    private fun errorPage(message: String) = page(
        context.getString(R.string.moviotv_xp_remote_try_again),
        "<p>${escape(message)}</p><p><a href='/'>${escape(context.getString(R.string.moviotv_xp_remote_enter_code_again))}</a></p>",
    )

    private fun page(title: String, content: String): ByteArray = ("""<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><meta charset="utf-8"><title>$title</title><style>
        body{margin:0;background:#100d17;color:#f7f1fb;font:18px system-ui,sans-serif;padding:24px}main{max-width:520px;margin:8vh auto;background:#1c1724;border-radius:22px;padding:28px}h1{font-size:28px;margin:0 0 16px}p{color:#c9bdd1;line-height:1.5}label{display:block;margin:24px 0 14px}input{display:block;box-sizing:border-box;width:100%;margin-top:8px;padding:15px;border-radius:12px;border:1px solid #76598d;background:#100d17;color:#fff;font-size:18px}button{width:100%;padding:15px;border:0;border-radius:14px;background:#70438e;color:#fff;font-size:18px;font-weight:700}a{color:#d5b6ed}
        </style></head><body><main><h1>$title</h1>$content</main></body></html>""").toByteArray(Charsets.UTF_8)

    private fun readRequest(input: BufferedInputStream): RequestData? {
        val headerBytes = ByteArrayOutputStream()
        var matched = 0
        while (headerBytes.size() < MAX_HEADER_BYTES) {
            val next = input.read()
            if (next < 0) return null
            headerBytes.write(next)
            matched = when {
                matched == 0 && next == 13 -> 1
                matched == 1 && next == 10 -> 2
                matched == 2 && next == 13 -> 3
                matched == 3 && next == 10 -> 4
                next == 13 -> 1
                else -> 0
            }
            if (matched == 4) break
        }
        if (matched != 4) return null
        val lines = headerBytes.toByteArray().toString(Charsets.ISO_8859_1).split("\r\n")
        val requestLine = lines.firstOrNull()?.split(' ').orEmpty()
        if (requestLine.size < 2) return null
        val length = lines.drop(1).firstNotNullOfOrNull { line ->
            if (line.startsWith("Content-Length:", true)) line.substringAfter(':').trim().toIntOrNull() else null
        } ?: 0
        if (length !in 0..MAX_BODY_BYTES) return null
        val body = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(body, offset, length - offset)
            if (read < 0) return null
            offset += read
        }
        return RequestData(requestLine[0], requestLine[1], body)
    }

    private fun writeResponse(output: java.io.OutputStream, body: ByteArray) {
        output.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: ${body.size}\r\nConnection: close\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nX-Frame-Options: DENY\r\n\r\n").toByteArray(Charsets.US_ASCII))
        output.write(body)
        output.flush()
    }

    private fun decode(value: String): String = runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault("")
    private fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

    @Synchronized
    override fun close() {
        session?.let { finish(it, null) }
        _state.value = null
    }

    private data class RequestData(val method: String, val path: String, val body: ByteArray)

    private companion object {
        const val MAX_ATTEMPTS = 5
        const val LIFETIME_MS = 180_000L
        const val PORT = 8090
        const val MAX_HEADER_BYTES = 8_192
        const val MAX_BODY_BYTES = 16_384
    }
}
