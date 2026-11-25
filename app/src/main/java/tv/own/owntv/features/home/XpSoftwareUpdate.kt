package tv.own.owntv.features.home

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.tv.material3.Text
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import tv.own.owntv.BuildConfig
import tv.own.owntv.R
import tv.own.owntv.ui.theme.OwnTVTheme

/** This is a separate MovioXP update feed, never the original MovioTV downloads channel. */
internal object XpUpdatePolicy {
    const val DAY_MS = 86_400_000L
    fun due(now: Long, last: Long, enabled: Boolean) =
        enabled && (last <= 0L || now < last || now - last >= DAY_MS)
    fun eligible(installed: Long, offered: Long, minSupported: Long) =
        offered > installed && minSupported <= installed
}
internal data class XpUpdateManifest(
    val packageName: String, val versionName: String, val versionCode: Long, val channel: String,
    val apkUrl: String, val apkSizeBytes: Long, val sha256: String, val releaseNotes: String,
    val publishedAt: String, val minSupportedVersionCode: Long,
) {
    companion object {
        fun parse(text: String, expectedPackage: String, expectedChannel: String): XpUpdateManifest {
            require(text.length in 20..65536) { "Invalid manifest length" }
            val json = JSONObject(text)
            require(json.getString("app") == "MovioXP") { "Wrong application" }
            require(json.getString("packageName") == expectedPackage) { "Wrong package" }
            require(json.getString("channel") == expectedChannel) { "Wrong release channel" }
            val apk = json.getString("apkUrl")
            val url = URI(apk)
            require(url.scheme.equals("https", true) && !url.host.isNullOrBlank() &&
                url.userInfo == null && url.fragment == null) { "HTTPS APK required" }
            val hash = json.getString("sha256").lowercase(java.util.Locale.US)
            require(hash.matches(Regex("[0-9a-f]{64}"))) { "Invalid SHA-256" }
            val size = json.getLong("apkSizeBytes")
            require(size in 1024L..400_000_000L) { "Invalid APK size" }
            val versionCode = json.getLong("versionCode")
            require(versionCode > 0L) { "Invalid version code" }
            val min = json.optLong("minSupportedVersionCode", 1L)
            require(min > 0L) { "Invalid minimum version" }
            val versionName = json.getString("versionName").trim()
            require(versionName.length in 1..64) { "Invalid version name" }
            return XpUpdateManifest(expectedPackage, versionName, versionCode, expectedChannel,
                apk, size, hash, json.optString("releaseNotes").take(4000),
                json.optString("publishedAt").take(50), min)
        }
    }
}
internal enum class XpUpdateStatus { IDLE, CHECKING, CURRENT, AVAILABLE, FAILED, DOWNLOADING, READY }

internal class XpSoftwareUpdater(private val context: Context) {
    companion object {
        const val STABLE_URL = "https://moviotv.opticalzoo.com/downloads/movioxp-version.json"
        const val QA_URL = "https://moviotv.opticalzoo.com/downloads/qa/movioxp-version.json"
    }
    private val prefs = context.getSharedPreferences("movioxp_software_updates", Context.MODE_PRIVATE)
    private val qa = BuildConfig.VERSION_NAME.contains("-qa", true)
    val channel = if (qa) "qa" else "stable"
    val versionName = BuildConfig.VERSION_NAME
    val versionCode = BuildConfig.VERSION_CODE.toLong()
    var automatic by mutableStateOf(prefs.getBoolean("auto_check", true))
        private set
    var lastChecked by mutableStateOf(prefs.getLong("last_success", 0L))
        private set
    var status by mutableStateOf(XpUpdateStatus.IDLE)
        private set
    var info by mutableStateOf<XpUpdateManifest?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var progress by mutableStateOf(0)
        private set

    init {
        info = runCatching { prefs.getString("cached_manifest", null)?.let {
            XpUpdateManifest.parse(it, context.packageName, channel)
        } }.getOrNull()
    }
    fun setAutomatic(enabled: Boolean) {
        automatic = enabled
        prefs.edit().putBoolean("auto_check", enabled).apply()
    }
    suspend fun checkIfDue() {
        if (XpUpdatePolicy.due(System.currentTimeMillis(), lastChecked, automatic)) check(false)
    }
    suspend fun check(force: Boolean = true) {
        if (status == XpUpdateStatus.DOWNLOADING || status == XpUpdateStatus.CHECKING) return
        if (!force && !XpUpdatePolicy.due(System.currentTimeMillis(), lastChecked, automatic)) return
        status = XpUpdateStatus.CHECKING
        error = null
        try {
            val body = withContext(Dispatchers.IO) { fetchText(if (qa) QA_URL else STABLE_URL) }
            val offered = XpUpdateManifest.parse(body, context.packageName, channel)
            info = offered
            lastChecked = System.currentTimeMillis()
            prefs.edit().putString("cached_manifest", body).putLong("last_success", lastChecked).apply()
            status = when {
                XpUpdatePolicy.eligible(versionCode, offered.versionCode, offered.minSupportedVersionCode) ->
                    XpUpdateStatus.AVAILABLE
                offered.versionCode > versionCode -> {
                    error = context.getString(R.string.movioxp_update_unsupported)
                    XpUpdateStatus.FAILED
                }
                else -> XpUpdateStatus.CURRENT
            }
        } catch (e: CancellationException) {
            status = XpUpdateStatus.IDLE
            throw e
        } catch (e: Exception) {
            error = e.message ?: context.getString(R.string.movioxp_update_failed)
            status = XpUpdateStatus.FAILED
        }
    }
    suspend fun downloadAndInstall() {
        if (status != XpUpdateStatus.AVAILABLE) return
        val offered = info ?: return
        if (!XpUpdatePolicy.eligible(versionCode, offered.versionCode,
                offered.minSupportedVersionCode)) return
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            error = context.getString(R.string.movioxp_update_install_permission)
            runCatching {
                context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            return
        }
        status = XpUpdateStatus.DOWNLOADING
        progress = 0
        error = null
        val directory = File(context.filesDir, "updates")
        val temporary = File(directory, "movioxp-" + offered.versionCode + ".part")
        val target = File(directory, "movioxp-" + offered.versionCode + ".apk")
        try {
            withContext(Dispatchers.IO) {
                require(directory.isDirectory || directory.mkdirs()) { "Cannot create update directory" }
                val url = URL(offered.apkUrl)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 12000
                    instanceFollowRedirects = false
                }
                try {
                    require(connection.responseCode == 200) { "Download HTTP " + connection.responseCode }
                    require(connection.contentLengthLong < 0 ||
                        connection.contentLengthLong == offered.apkSizeBytes) { "Size mismatch" }
                    val hash = MessageDigest.getInstance("SHA-256")
                    var total = 0L
                    val buffer = ByteArray(65536)
                    connection.inputStream.use { input ->
                        FileOutputStream(temporary).use { output ->
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                total += read
                                require(total <= offered.apkSizeBytes) { "Downloaded too much data" }
                                output.write(buffer, 0, read)
                                hash.update(buffer, 0, read)
                                progress = (total * 100L / offered.apkSizeBytes).toInt()
                            }
                            output.fd.sync()
                        }
                    }
                    require(total == offered.apkSizeBytes) { "Incomplete APK" }
                    val digest = hash.digest().joinToString("") { "%02x".format(it) }
                    require(digest == offered.sha256) { "APK checksum mismatch" }
                    require(apkCompatible(temporary, offered)) { "APK package, version or signature mismatch" }
                    if (target.exists()) target.delete()
                    require(temporary.renameTo(target)) { "Could not finalise APK" }
                } finally {
                    connection.disconnect()
                }
            }
            status = XpUpdateStatus.READY
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", target)
            val installer = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installer)
        } catch (cancelled: CancellationException) {
            temporary.delete()
            status = XpUpdateStatus.AVAILABLE
            error = context.getString(R.string.movioxp_update_cancelled)
            throw cancelled
        } catch (failure: Exception) {
            temporary.delete()
            status = XpUpdateStatus.FAILED
            error = failure.message ?: context.getString(R.string.movioxp_update_failed)
        }
    }
    @Suppress("DEPRECATION")
    private fun apkCompatible(apk: File, offered: XpUpdateManifest): Boolean {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
            else PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(apk.absolutePath, flags) ?: return false
        val current = pm.getPackageInfo(context.packageName, flags)
        if (archive.packageName != context.packageName ||
            archive.longVersionCode != offered.versionCode || archive.longVersionCode <= versionCode) return false
        fun fingerprints(p: PackageInfo): Set<String> {
            val signatures = if (Build.VERSION.SDK_INT >= 28)
                p.signingInfo?.apkContentsSigners else p.signatures
            return signatures.orEmpty().map { signature ->
                MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                    .joinToString("") { "%02x".format(it) }
            }.toSet()
        }
        val installed = fingerprints(current)
        return installed.isNotEmpty() && installed == fingerprints(archive)
    }
    private fun fetchText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 4000
            readTimeout = 6000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
        }
        try {
            require(connection.responseCode == 200) { "Manifest HTTP " + connection.responseCode }
            require(connection.contentLengthLong <= 65536L) { "Manifest too large" }
            return connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    require(output.size() + read <= 65536) { "Manifest too large" }
                    output.write(buffer, 0, read)
                }
                output.toString(Charsets.UTF_8.name())
            }
        } finally {
            connection.disconnect()
        }
    }
}
@Composable
internal fun XpSoftwareUpdatePanel(
    model: XpSoftwareUpdater, onCheck: () -> Unit, onInstall: () -> Unit, onCancel: () -> Unit,
) {
    val muted = OwnTVTheme.colors.onSurfaceVariant
    Text(stringResource(R.string.movioxp_update_installed_value, model.versionName),
        color = muted, fontSize = 15.sp)
    Text(stringResource(R.string.movioxp_update_latest_value,
        model.info?.versionName ?: stringResource(R.string.movioxp_update_unknown)),
        color = muted, fontSize = 15.sp)
    val statusRes = when (model.status) {
        XpUpdateStatus.IDLE -> R.string.movioxp_update_not_checked
        XpUpdateStatus.CHECKING -> R.string.movioxp_update_checking
        XpUpdateStatus.CURRENT -> R.string.movioxp_update_current
        XpUpdateStatus.AVAILABLE -> R.string.movioxp_update_available
        XpUpdateStatus.FAILED -> R.string.movioxp_update_failed
        XpUpdateStatus.DOWNLOADING -> R.string.movioxp_update_downloading
        XpUpdateStatus.READY -> R.string.movioxp_update_installer_opened
    }
    Text(stringResource(statusRes), color = OwnTVTheme.colors.primary, fontWeight = FontWeight.Bold)
    if (model.lastChecked > 0) {
        Text(stringResource(R.string.movioxp_update_checked_value,
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(model.lastChecked))), color = muted, fontSize = 13.sp)
    }
    model.error?.let { Text(it, color = Color(0xFFFFA1A1), fontSize = 13.sp) }
    if (model.status == XpUpdateStatus.AVAILABLE && model.info != null) {
        val offered = model.info!!
        Text(offered.releaseNotes.ifBlank { stringResource(R.string.movioxp_update_no_notes) },
            color = muted, fontSize = 14.sp)
        Text(stringResource(R.string.movioxp_update_size_mb, offered.apkSizeBytes / 1024L / 1024L), color = muted)
        XpSettingsOption(stringResource(R.string.movioxp_update_download_install), onClick = onInstall)
    }
    if (model.status == XpUpdateStatus.DOWNLOADING) {
        Text(stringResource(R.string.movioxp_update_percent, model.progress), color = muted)
        XpSettingsOption(stringResource(R.string.movioxp_update_cancel), onClick = onCancel)
    }
    XpSettingsOption(stringResource(R.string.movioxp_update_check),
        enabled = model.status != XpUpdateStatus.CHECKING &&
            model.status != XpUpdateStatus.DOWNLOADING, onClick = onCheck)
    XpSettingsOption(stringResource(
        if (model.automatic) R.string.movioxp_update_auto_on else R.string.movioxp_update_auto_off),
        selected = model.automatic) { model.setAutomatic(!model.automatic) }
    Text(stringResource(R.string.movioxp_update_secure_install), color = muted, fontSize = 12.sp)
}
