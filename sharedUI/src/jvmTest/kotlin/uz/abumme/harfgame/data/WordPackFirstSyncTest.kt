package uz.abumme.harfgame.data

import eu.anifantakis.lib.ksafe.KSafe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import uz.abumme.harfgame.data.wordpack.WordPackCache
import uz.abumme.harfgame.data.wordpack.WordPackSyncManager
import uz.abumme.harfgame.engine.WordPackRepository
import uz.abumme.harfgame.lang.LanguageRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/** The sync manager's first-sync signal ends on every outcome of the first attempt. */
class WordPackFirstSyncTest {
    private val registry = LanguageRegistry()

    // Its own store file, so it never shares a DataStore with the other KSafe tests.
    private val cache = WordPackCache(KSafe(fileName = "first_sync_signal_test"))

    private fun manager(status: HttpStatusCode) = WordPackSyncManager(
        httpClient = HttpClient(MockEngine { if (status == HttpStatusCode.NotModified) respond("", status) else respondError(status) }),
        baseUrl = "http://test",
        cache = cache,
        repository = WordPackRepository(registry, cache),
        registry = registry,
    )

    @Test
    fun theFirstSyncEndsOnAnErrorOrNotModified() = runBlocking {
        for (status in listOf(HttpStatusCode.InternalServerError, HttpStatusCode.NotModified)) {
            val sync = manager(status)
            sync.syncOne("ru")
            // Already over: returns at once instead of waiting an hour.
            withTimeout(5.seconds) { sync.awaitFirstSync("ru", 1.hours) }
        }
    }

    @Test
    fun aLanguageNotYetSyncedWaitsOnlyUpToTheTimeout() = runTest {
        val sync = manager(HttpStatusCode.InternalServerError)
        val start = currentTime
        sync.awaitFirstSync("kk", 2.seconds)
        assertEquals(2_000, currentTime - start)
        // A language the app does not know has nothing to wait for.
        sync.awaitFirstSync("xx", 2.seconds)
        assertEquals(2_000, currentTime - start)
    }
}
