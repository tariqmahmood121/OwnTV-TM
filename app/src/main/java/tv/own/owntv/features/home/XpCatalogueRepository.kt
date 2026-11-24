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
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

internal class XpCatalogueRepository(
    private val directory: File,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val ttlMillis: Long = 6 * 60 * 60 * 1000L,
) {
    internal data class Key(
        val profile: String,
        val provider: String,
        val resource: String,
    )

    private data class Cached(val body: String, val timestamp: Long)
    private val lock = Mutex()
    private val requests = Semaphore(4)
    private val inFlight = mutableMapOf<Key, Deferred<String?>>()
    private val failedAt = object : LinkedHashMap<Key, Long>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Long>?): Boolean = size > 128
    }
    private var writesUntilSweep = 0
    private val memory = object : LinkedHashMap<Key, Cached>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Cached>?): Boolean = size > 64
    }

    fun observe(
        key: Key,
        refreshAfter: Long = 0L,
        cacheable: (String) -> Boolean = { true },
        load: suspend () -> String,
    ) = flow {
        val cached = lock.withLock { read(key) }
        xpDebug(if (cached == null) "Catalogue cache miss" else "Catalogue cache hit, age ${now() - cached.timestamp} ms")
        if (cached != null) emit(cached.body)
        val job = lock.withLock {
            inFlight[key]?.let { return@withLock it }
            val latest = read(key)
            val age = latest?.let { now() - it.timestamp }
            if (latest != null && latest.timestamp >= refreshAfter && age != null && age in 0 until ttlMillis) return@withLock null
            if (failedAt[key]?.let { it >= refreshAfter && now() - it in 0 until 60_000L } == true) return@withLock null
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
                                failedAt[key] = now()
                            }
                        }
                    }
                    if (cached != null && !valid) null else body
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    xpDebug("Catalogue refresh failed after ${now() - started} ms")
                    lock.withLock {
                        if (inFlight[key] === coroutineContext[Job]) failedAt[key] = now()
                    }
                    null
                } finally {
                    lock.withLock {
                        if (inFlight[key] === coroutineContext[Job]) inFlight.remove(key)
                    }
                }
            }.also { inFlight[key] = it }
        }
        val result = if (job == null) lock.withLock { read(key)?.body } else {
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

    private fun read(key: Key): Cached? = memory[key] ?: runCatching {
        val file = file(key)
        if (!file.isFile || file.length() > MAX_ENTRY_BYTES) null
        else Cached(file.readText(), file.lastModified()).also { memory[key] = it }
    }.getOrNull()

    private fun write(key: Key, body: String) = runCatching {
        if (body.length > MAX_ENTRY_BYTES) return@runCatching
        val time = now()
        memory[key] = Cached(body, time)
        val file = file(key)
        val parent = requireNotNull(file.parentFile)
        parent.mkdirs()
        val temp = Files.createTempFile(parent.toPath(), null, null)
        try {
            Files.write(temp, body.toByteArray(Charsets.UTF_8))
            Files.move(temp, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            file.setLastModified(time)
        } finally {
            Files.deleteIfExists(temp)
        }
        val entries = parent.listFiles().orEmpty()
        if (entries.size > 128) entries.sortedByDescending(File::lastModified).drop(128).forEach(File::delete)
        if (writesUntilSweep-- <= 0) {
            writesUntilSweep = 8
            val diskEntries = directory.walkTopDown().filter(File::isFile)
                .sortedByDescending(File::lastModified).toList()
            var bytes = 0L
            diskEntries.forEach { entry ->
                bytes += entry.length()
                if (bytes > MAX_DISK_BYTES) entry.delete()
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
            ).also { instance = it }
        }
    }
}
