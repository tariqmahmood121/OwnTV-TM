package tv.own.owntv.features.home

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URLDecoder
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom

/** Short-lived LAN form for entering an add-on URL on a phone while browsing on the TV. */
internal class XpAddonRemoteServer(private val context: Context) : Closeable {
    data class State(val url: String, val pin: String, val qr: Bitmap?, val failure: String? = null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<State?>(null)
    val state = _state.asStateFlow()
    private val _submissions = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val submissions = _submissions.asSharedFlow()
    @Volatile private var pin: String = ""
    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var stopAfterReply: Boolean = false
    private var serverJob: kotlinx.coroutines.Job? = null

    fun start() {
        if (serverJob?.isActive == true) return
        stopAfterReply = false
        pin = SecureRandom().nextInt(1_000_000).toString().padStart(6, '0')
        serverJob = scope.launch {
            try {
                ServerSocket().use { server ->
                    server.reuseAddress = true
                    server.bind(InetSocketAddress(PORT))
                    serverSocket = server
                    val url = CompanionLink.lanUrl(PORT)
                    _state.value = State(url, pin, CompanionLink.renderQr(url))
                    while (isActive) handle(server.accept())
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (scope.isActive && !stopAfterReply) {
                    Log.w(TAG, "Could not start add-on phone entry", error)
                    _state.value = State("", "", null, context.getString(R.string.moviotv_xp_remote_open_failed))
                }
            } finally {
                serverSocket = null
            }
        }
    }

    private suspend fun handle(socket: java.net.Socket) {
        socket.use { client ->
            client.soTimeout = 8_000
            val input = BufferedInputStream(client.getInputStream())
            val request = readRequest(input) ?: return
            val method = request.method
            val body = request.body.toString(Charsets.UTF_8)
            val parameters = body.split('&').mapNotNull { pair ->
                val split = pair.indexOf('=')
                if (split < 0) null else decode(pair.substring(0, split)) to decode(pair.substring(split + 1))
            }.toMap()
            val response = when {
                method == "GET" && request.path == "/" -> pinPage()
                method == "POST" && request.path == "/unlock" -> {
                    if (samePin(parameters["pin"].orEmpty())) addonPage(pin) else errorPage(context.getString(R.string.moviotv_xp_remote_code_mismatch))
                }
                method == "POST" && request.path == "/install" -> {
                    val submittedPin = parameters["pin"].orEmpty()
                    val addonUrl = parameters["url"].orEmpty().trim()
                    if (!samePin(submittedPin)) errorPage(context.getString(R.string.moviotv_xp_remote_code_mismatch))
                    else if (!validAddonUrl(addonUrl)) errorPage(context.getString(R.string.moviotv_xp_remote_invalid_url))
                    else {
                        pin = ""
                        _state.value = null
                        stopAfterReply = true
                        _submissions.emit(addonUrl)
                        successPage()
                    }
                }
                else -> errorPage(context.getString(R.string.moviotv_xp_remote_expired))
            }
            writeResponse(client.getOutputStream(), response)
            if (stopAfterReply) serverSocket?.close()
        }
    }

    private fun samePin(candidate: String): Boolean {
        val current = pin
        return current.isNotBlank() && MessageDigest.isEqual(current.toByteArray(), candidate.toByteArray())
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

    private fun successPage() = page(
        context.getString(R.string.moviotv_xp_remote_sent_title),
        "<p>${escape(context.getString(R.string.moviotv_xp_remote_sent_help))}</p>",
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
            "Content-Length: ${body.size}\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n").toByteArray(Charsets.US_ASCII))
        output.write(body)
        output.flush()
    }

    private fun decode(value: String): String = runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault("")
    private fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

    override fun close() {
        pin = ""
        _state.value = null
        serverSocket?.close()
        serverJob?.cancel()
        serverJob = null
    }

    private data class RequestData(val method: String, val path: String, val body: ByteArray)

    private companion object {
        const val TAG = "XpAddonRemote"
        const val PORT = 8090
        const val MAX_HEADER_BYTES = 8_192
        const val MAX_BODY_BYTES = 16_384
    }
}
