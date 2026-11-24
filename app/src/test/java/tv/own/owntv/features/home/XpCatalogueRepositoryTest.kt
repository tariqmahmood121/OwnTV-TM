package tv.own.owntv.features.home

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.atomic.AtomicInteger

class XpCatalogueRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    private val key = XpCatalogueRepository.Key("account A, profile 1", "provider", "trending")
    private var now = 1_800_000_000_000L

    @Test fun diskCacheSurvivesRepositoryRestartAndExpiresAfterSixHours() = runBlocking {
        val scope = repositoryScope()
        try {
            assertEquals(listOf("original"), repository(scope).observe(key) { "original" }.toList())
            now += SIX_HOURS - 1_000
            assertEquals(listOf("original"), repository(scope).observe(key) {
                throw AssertionError("fresh data must not trigger a fetch")
            }.toList())
            now += 1_000
            assertEquals(listOf("original", "updated"), repository(scope).observe(key) { "updated" }.toList())
        } finally {
            scope.cancel()
        }
    }

    @Test fun failedRefreshKeepsStaleDataAndDoesNotReplaceDiskEntry() = runBlocking {
        val scope = repositoryScope()
        try {
            val repo = repository(scope)
            repo.observe(key) { "cached" }.toList()
            now += SIX_HOURS
            assertEquals(listOf("cached"), repo.observe(key) { error("offline") }.toList())
            assertEquals(listOf("cached"), repository(scope).observe(key, cacheable = { false }) {
                "server fallback envelope"
            }.toList())
        } finally {
            scope.cancel()
        }
    }

    @Test fun simultaneousCollectorsShareOneRequest() = runBlocking {
        val scope = repositoryScope()
        try {
            val repo = repository(scope)
            val calls = AtomicInteger()
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            suspend fun load(): String {
                calls.incrementAndGet()
                started.complete(Unit)
                release.await()
                return "shared"
            }
            val first = async { repo.observe(key, load = ::load).toList() }
            started.await()
            val second = async { repo.observe(key, load = ::load).toList() }
            release.complete(Unit)
            assertEquals(listOf("shared"), first.await())
            assertEquals(listOf("shared"), second.await())
            assertEquals(1, calls.get())
        } finally {
            scope.cancel()
        }
    }

    @Test fun invalidationAffectsOnlyTheSelectedProviderAndProfile() = runBlocking {
        val scope = repositoryScope()
        try {
            val repo = repository(scope)
            val otherProfile = key.copy(profile = "account A, profile 2")
            val otherProvider = key.copy(provider = "other provider")
            repo.observe(key) { "remove me" }.toList()
            repo.observe(otherProfile) { "other profile" }.toList()
            repo.observe(otherProvider) { "other provider" }.toList()
            repo.invalidate(key.profile, key.provider)
            val restarted = repository(scope)
            assertEquals(listOf("new"), restarted.observe(key) { "new" }.toList())
            assertEquals(listOf("other profile"), restarted.observe(otherProfile) { throw AssertionError("fresh") }.toList())
            assertEquals(listOf("other provider"), restarted.observe(otherProvider) { throw AssertionError("fresh") }.toList())
        } finally {
            scope.cancel()
        }
    }

    @Test fun reopeningAColdCatalogueRetainsExplicitFallbackPolicyDuringCooldown() = runBlocking {
        val scope = repositoryScope()
        try {
            val repo = repository(scope)
            val response = """{"fallback":false,"error":"Unavailable"}"""
            assertEquals(listOf(response), repo.observe(key, cacheable = { false }) { response }.toList())
            assertEquals(listOf(response), repo.observe(key, cacheable = { false }) {
                throw AssertionError("retry cooldown must deduplicate requests")
            }.toList())
        } finally {
            scope.cancel()
        }
    }

    private fun repositoryScope() = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun repository(scope: CoroutineScope) = XpCatalogueRepository(folder.root, scope, now = { now })

    private companion object {
        const val SIX_HOURS = 6 * 60 * 60 * 1000L
    }
}
