package tv.own.owntv.features.home

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.own.owntv.BuildConfig
import tv.own.owntv.R
import java.io.File
import java.nio.file.Files

internal object XpMaintenance {
    private const val MB = 1024L * 1024
    private const val DAY = 24L * 60 * 60 * 1000
    private val installedApk = Regex("movioxp-([0-9]+)\\.apk")
    private fun prefs(context: Context) = context.getSharedPreferences("movioxp_maintenance", Context.MODE_PRIVATE)

    fun budget(storage: Long, available: Long): Long {
        val limit = when {
            storage < 12_000 * MB -> 150L
            storage < 24_000 * MB -> 300L
            storage < 48_000 * MB -> 500L
            else -> 1_000L
        } * MB
        return minOf(limit, available.coerceAtLeast(0) / 20).coerceAtLeast(16 * MB)
    }

    fun diskBudget(context: Context): Long {
        val fs = StatFs(context.cacheDir.path)
        return budget(fs.totalBytes, fs.availableBytes)
    }

    data class Snapshot(val total: Long, val free: Long, val pictures: Long, val catalogues: Long,
        val updates: Long, val ram: Long, val heap: Long, val last: Long)

    private fun bytes(root: File): Long = root.listFiles().orEmpty()
        .filterNot { Files.isSymbolicLink(it.toPath()) }.sumOf { if (it.isDirectory) bytes(it) else it.length() }

    suspend fun snapshot(context: Context) = withContext(Dispatchers.IO) {
        val fs = StatFs(context.cacheDir.path)
        val manager = context.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo().also { manager?.getMemoryInfo(it) }
        val runtime = Runtime.getRuntime()
        Snapshot(fs.totalBytes, fs.availableBytes, bytes(context.cacheDir.resolve("image_cache")),
            bytes(context.noBackupFilesDir.resolve("xp_catalogues")), bytes(context.filesDir.resolve("updates")),
            memory.totalMem, runtime.totalMemory() - runtime.freeMemory(), prefs(context).getLong("last", 0))
    }

    suspend fun run(context: Context, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - prefs(context).getLong("last", 0) < DAY) return
        XpCatalogueRepository.get(context).maintain()
        withContext(Dispatchers.IO) {
            context.filesDir.resolve("updates").listFiles().orEmpty().forEach { file ->
                val code = installedApk.matchEntire(file.name)?.groupValues?.get(1)?.toLongOrNull()
                if (code != null && code <= BuildConfig.VERSION_CODE && file.isFile &&
                    !Files.isSymbolicLink(file.toPath()) && now - file.lastModified() >= DAY) file.delete()
            }
            prefs(context).edit().putLong("last", now).apply()
        }
    }
}

@Composable
internal fun XpMaintenancePanel(context: Context, playbackActive: Boolean) {
    var requests by remember { mutableStateOf(0) }
    var metrics by remember { mutableStateOf<XpMaintenance.Snapshot?>(null) }
    var diagnostics by remember { mutableStateOf(false) }
    var lastRequest by remember { mutableStateOf(0) }
    LaunchedEffect(requests, playbackActive) {
        if (requests != lastRequest && !playbackActive) {
            XpMaintenance.run(context, force = true)
            lastRequest = requests
        }
        metrics = XpMaintenance.snapshot(context)
    }
    val values = metrics
    Column {
        Text(stringResource(R.string.movioxp_maintenance_auto))
        if (values != null) {
            fun mb(bytes: Long) = android.text.format.Formatter.formatShortFileSize(context, bytes)
            Text(stringResource(R.string.movioxp_maintenance_storage, mb(values.free), mb(values.total)))
            Text(stringResource(R.string.movioxp_maintenance_cache, mb(values.pictures), mb(values.catalogues), mb(values.updates)))
            val last = values.last.takeIf { it > 0 }?.let {
                java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it))
            } ?: stringResource(R.string.movioxp_maintenance_never)
            Text(stringResource(R.string.movioxp_maintenance_last, last))
            if (diagnostics) Text(stringResource(R.string.movioxp_maintenance_device,
                Build.MODEL, Build.VERSION.RELEASE, mb(values.ram), mb(values.heap)))
        }
        XpSettingsOption(stringResource(R.string.movioxp_maintenance_optimise), enabled = !playbackActive) { requests++ }
        XpSettingsOption(stringResource(R.string.movioxp_maintenance_diagnostics), selected = diagnostics) { diagnostics = !diagnostics }
    }
}
