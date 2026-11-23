package tv.own.owntv.features.home

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Event-driven Central synchronization, independent of whether the catalogue is visible.
 * An authenticated account/profile binding is mandatory for any upload; a background failure
 * never discards the dirty local records, so reconnect/periodic sync can replay them.
 */
internal object XpCentralEventSync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val guard = Any()

    private data class Bound(
        val accountId: Long,
        val token: String,
        val profileId: Long,
        val profileName: String,
        val child: Boolean,
    )

    private var current: Bound? = null
    private var scheduled: Job? = null

    fun bind(connection: XpCentralConnection, profileId: Long, profileName: String, isChild: Boolean) {
        if (profileId <= 0L || profileName.isBlank()) return
        synchronized(guard) {
            val next = Bound(connection.accountId, connection.token, profileId, profileName, isChild)
            if (current != next) {
                scheduled?.cancel()
                scheduled = null
                current = next
            }
        }
    }

    fun clear() {
        synchronized(guard) {
            current = null
            scheduled?.cancel()
            scheduled = null
        }
    }

    fun request(context: Context, profileId: Long) {
        if (profileId <= 0L) return
        val app = context.applicationContext
        synchronized(guard) {
            val selected = current?.takeIf { it.profileId == profileId } ?: return
            // Multiple source/audio/subtitle changes in one interaction are uploaded in one batch.
            scheduled?.cancel()
            scheduled = scope.launch {
                delay(450L)
                val live = synchronized(guard) { current }
                if (live != selected) return@launch
                // Do not send a stale account token or a previous profile's data after logout/switch.
                val saved = XpCentralConnectionStore.read(app) ?: return@launch
                if (saved.accountId != selected.accountId || saved.token != selected.token) return@launch
                runCatching {
                    XpCentralDataSync.sync(
                        app, saved, selected.profileId, selected.profileName, selected.child,
                    )
                }
                // The original dirty records remain when upload fails, for the 90s fallback.
            }
        }
    }
}
