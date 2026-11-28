package tv.own.owntv.features.home

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.security.MessageDigest

internal class XpCatalogueRepository(
    private val directory: File,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val ttlMillis: Long = 6 * 60 * 60 * 1000L,
    private val diskBudget: () -> Long = { MAX_DISK_BYTES },
    maxRequests: Int = 4,
) {
    internal data class Key(
        val profile: String,
        val provider: String,
        val resource: String,
    )

    private data class Cached(val body: String, val timestamp: Long)
    private data class Retry(val timestamp: Long, val response: String?)
    private val lock = Mutex()
    private val requests = Semaphore(maxRequests)
    private val inFlight = mutableMapOf<Key, Deferred<String?>>()
    private val failedAt = object : LinkedHashMap<Key, Retry>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Retry>?): Boolean = size > 128
    }
    private var writesUntilSweep = 0
    private val memory = object : LinkedHashMap<Key, Cached>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Cached>?): Boolean = size > 64
    }

    fun observe(
        key: Key,
        refreshAfter: Long = 0L,
        cacheable: (String) -> Boolean = { true },
        legacyKey: Key? = null,
        load: suspend () -> String,
    ) = flow {
        val cached = lock.withLock { read(key, legacyKey) }
        xpDebug(if (cached == null) "Catalogue cache miss" else "Catalogue cache hit, age ${now() - cached.timestamp} ms")
        if (cached != null) emit(cached.body)
        var retryResponse: String? = null
        val job = lock.withLock {
            inFlight[key]?.let { return@withLock it }
            val latest = read(key, legacyKey)
            val age = latest?.let { now() - it.timestamp }
            if (latest != null && latest.timestamp >= refreshAfter && age != null && age in 0 until ttlMillis) return@withLock null
            val retry = failedAt[key]
            if (retry != null && retry.timestamp >= refreshAfter && now() - retry.timestamp in 0 until 60_000L) {
                retryResponse = retry.response
                return@withLock null
            }
            scope.async {
                val started = now()
                try {
                    val body = requests.withPermit { load() }
                    xpDebug("Catalogue refresh completed in ${now() - started} ms")
                    val valid = cacheable(body)
                    lock.withLock {
                        // Invalidation cancels the old request and removes its entry before allowing new writes.
                        if (inFlight[key] === coroutineContext[Job]) {
                            if (valid) {
                                write(key, body)
                                failedAt.remove(key)
                            } else {
                                failedAt[key] = Retry(now(), body)
                            }
                        }
                    }
                    if (cached != null && !valid) null else body
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    xpDebug("Catalogue refresh failed after ${now() - started} ms")
                    lock.withLock {
                        if (inFlight[key] === coroutineContext[Job]) failedAt[key] = Retry(now(), null)
                    }
                    null
                } finally {
                    lock.withLock {
                        if (inFlight[key] === coroutineContext[Job]) inFlight.remove(key)
                    }
                }
            }.also { inFlight[key] = it }
        }
        val result = if (job == null) lock.withLock { read(key, legacyKey)?.body ?: retryResponse } else {
            try {
                job.await()
            } catch (error: CancellationException) {
                currentCoroutineContext().ensureActive()
                null
            }
        }
        if (result != null && result != cached?.body) emit(result)
        if (result == null && cached == null) emit(null)
    }.flowOn(Dispatchers.IO)

    suspend fun cachedBody(key: Key, legacyKey: Key? = null): String? = withContext(Dispatchers.IO) {
        lock.withLock { read(key, legacyKey)?.body }
    }

    suspend fun invalidate(profile: String, provider: String) = withContext(Dispatchers.IO) {
        lock.withLock {
            inFlight.keys.filter { it.profile == profile && it.provider == provider }.forEach { key ->
                inFlight.remove(key)?.cancel()
            }
            failedAt.keys.removeAll { it.profile == profile && it.provider == provider }
            memory.keys.removeAll { it.profile == profile && it.provider == provider }
            providerDirectory(profile, provider).deleteRecursively()
        }
    }

    private fun read(key: Key, legacyKey: Key? = null): Cached? {
        val current = readEntry(key)
        if (current != null || legacyKey == null || legacyKey == key) return current
        val legacy = readEntry(legacyKey) ?: return null
        write(key, legacy.body, legacy.timestamp)
        return legacy
    }

    private fun readEntry(key: Key): Cached? = memory[key] ?: runCatching {
        val file = file(key)
        if (!file.isFile || file.length() > MAX_ENTRY_BYTES) null
        else Cached(file.readText(), file.lastModified()).also { memory[key] = it }
    }.getOrNull()

    private fun write(key: Key, body: String, time: Long = now()) = runCatching {
        val encoded = body.toByteArray(Charsets.UTF_8)
        if (encoded.size > MAX_ENTRY_BYTES) return@runCatching
        memory[key] = Cached(body, time)
        val file = file(key)
        val parent = requireNotNull(file.parentFile)
        parent.mkdirs()
        val temp = Files.createTempFile(parent.toPath(), null, null)
        try {
            FileOutputStream(temp.toFile()).use { output ->
                output.write(encoded)
                output.fd.sync()
            }
            temp.toFile().setLastModified(time)
            try {
                Files.move(temp, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                // Both paths are in the same directory. Android's rename still replaces the final entry.
                check(temp.toFile().renameTo(file))
            }
        } finally {
            Files.deleteIfExists(temp)
        }
        if (writesUntilSweep-- <= 0) {
            writesUntilSweep = 8
            sweepDisk()
        }
    }.onFailure { xpDebug("Could not persist catalogue cache entry") }

    suspend fun maintain() = withContext(Dispatchers.IO) {
        lock.withLock {
            if (inFlight.isEmpty()) sweepDisk()
        }
    }

    private fun sweepDisk() {
        val diskEntries = directory.walkTopDown().filter { it.isFile && !Files.isSymbolicLink(it.toPath()) }
            .sortedByDescending(File::lastModified).toList()
        val budget = diskBudget()
        val time = now()
        var bytes = diskEntries.sumOf(File::length)
        for (entry in diskEntries.asReversed()) {
            if (bytes <= budget) break
            val age = time - entry.lastModified()
            // Stale responses remain useful offline unless the cache exceeds its budget.
            // Only evict a fresh entry if retaining the fresh set exceeds a second hard limit.
            if (age >= ttlMillis || bytes > budget * 2) {
                val size = entry.length()
                if (entry.delete()) bytes -= size
            }
        }
    }

    private fun providerDirectory(profile: String, provider: String) =
        directory.resolve(digest(profile)).resolve(digest(provider))

    private fun file(key: Key) = providerDirectory(key.profile, key.provider).resolve(digest(key.resource))

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    companion object {
        private const val MAX_ENTRY_BYTES = 2_000_000L
        private const val MAX_DISK_BYTES = 64L * 1024 * 1024
        @Volatile private var instance: XpCatalogueRepository? = null

        fun get(context: Context): XpCatalogueRepository = instance ?: synchronized(this) {
            instance ?: XpCatalogueRepository(
                context.applicationContext.noBackupFilesDir.resolve("xp_catalogues"),
                CoroutineScope(SupervisorJob() + Dispatchers.IO),
                diskBudget = { minOf(MAX_DISK_BYTES, XpMaintenance.diskBudget(context.applicationContext) / 4) },
                maxRequests = if (context.getSystemService(android.app.ActivityManager::class.java)?.isLowRamDevice == true) 2 else 4,
            ).also { instance = it }
        }
    }
}
