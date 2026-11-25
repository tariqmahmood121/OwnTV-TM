package tv.own.owntv.features.home

import android.content.Context
import tv.own.owntv.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import tv.own.owntv.features.profiles.ProfileLoadState
import tv.own.owntv.features.profiles.ProfilesViewModel

/**
 * Retain Room profiles across logouts, but expose only the local profile IDs bound to the signed-in
 * Central account. Identity is (accountId, centralProfileId), never a recycled profile name.
 * Central GET /profiles is authoritative and refresh never creates a Central profile.
 */
internal object XpCentralProfileScope {
    private const val PREFS = "movioxp_central_profile_membership"
    private val accountMutable = MutableStateFlow<Long?>(null)
    val accountId = accountMutable.asStateFlow()
    private val revisionMutable = MutableStateFlow(0)
    val revision = revisionMutable.asStateFlow()

    private val refreshLock = Mutex()

    fun initialize(context: Context) {
        accountMutable.value = XpCentralConnectionStore.read(context)?.accountId
    }

    fun bind(accountId: Long) {
        require(accountId > 0L)
        accountMutable.value = accountId
        revisionMutable.value += 1
    }

    fun unbind() {
        accountMutable.value = null
        revisionMutable.value += 1
    }

    private fun membershipKey(account: Long) = "members_$account"
    private fun bindingKey(account: Long, remoteProfile: Long) = "central_${account}_$remoteProfile"

    fun allowedLocalIds(context: Context, account: Long): Set<Long> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(membershipKey(account), emptySet()).orEmpty()
            .mapNotNull { it.toLongOrNull()?.takeIf { id -> id > 0L } }.toSet()

    fun remoteIdFor(context: Context, account: Long, localId: Long): Long? {
        if (localId !in allowedLocalIds(context, account)) return null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.all.entries.firstOrNull { (key, value) ->
            key.startsWith("central_${account}_") && value == localId
        }?.key?.substringAfterLast('_')?.toLongOrNull()
    }

    suspend fun refresh(context: Context, connection: XpCentralConnection, vm: ProfilesViewModel): Unit = refreshLock.withLock {
        if (accountMutable.value != connection.accountId) return@withLock
        val remote: JSONArray = withContext(Dispatchers.IO) {
            XpCentralAddonSync.request(connection.apiBase, "profiles", connection.token)
                .getJSONArray("profiles")
        }
        if (accountMutable.value != connection.accountId) return@withLock
        val loaded = vm.profileState.first { it is ProfileLoadState.Loaded } as ProfileLoadState.Loaded
        val existing = loaded.profiles.associateBy { it.id }.toMutableMap()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val linked = linkedSetOf<Long>()
        for (index in 0 until remote.length()) {
            if (accountMutable.value != connection.accountId) return@withLock
            val item = remote.getJSONObject(index)
            val cloudId = item.getLong("id")
            require(cloudId > 0L)
            val name = item.optString("name").ifBlank { context.getString(R.string.movioxp_default_profile_name) }
            val child = item.optBoolean("is_child")
            val key = bindingKey(connection.accountId, cloudId)
            var localId = prefs.getLong(key, 0L)
            if (localId <= 0L || localId !in existing) {
                // Do not claim an unrelated local profile, even when both names are "V10".
                val result = CompletableDeferred<Long>()
                vm.create(name, 0, child, null, name, onCreated = { result.complete(it) })
                localId = withTimeout(30_000L) { result.await() }
                prefs.edit().putLong(key, localId).apply()
                val updated = vm.profileState.first {
                    it is ProfileLoadState.Loaded && it.profiles.any { p -> p.id == localId }
                } as ProfileLoadState.Loaded
                existing.clear()
                existing.putAll(updated.profiles.associateBy { it.id })
            } else if (existing.getValue(localId).name != name) {
                vm.rename(existing.getValue(localId), name)
            }
            linked += localId
        }
        if (accountMutable.value != connection.accountId) return@withLock
        // Replace instead of union: a deleted Central profile must lose membership.
        prefs.edit().putStringSet(membershipKey(connection.accountId), linked.map(Long::toString).toSet()).apply()
        revisionMutable.value += 1
        if (vm.activeProfileId.value !in linked && linked.isNotEmpty()) {
            val updated = vm.profileState.first {
                it is ProfileLoadState.Loaded && it.profiles.any { p -> p.id == linked.first() }
            } as ProfileLoadState.Loaded
            vm.switchTo(updated.profiles.first { it.id == linked.first() })
        }
    }
}
