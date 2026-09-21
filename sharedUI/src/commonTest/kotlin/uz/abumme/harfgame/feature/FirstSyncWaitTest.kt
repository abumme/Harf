package uz.abumme.harfgame.feature

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import uz.abumme.harfgame.data.wordpack.CalendarSnapshotDto
import uz.abumme.harfgame.data.wordpack.FirstPackSync
import uz.abumme.harfgame.data.wordpack.WordPackSchedule
import uz.abumme.harfgame.engine.WordPackRepository
import uz.abumme.harfgame.feature.daily.DailyPuzzleProvider
import uz.abumme.harfgame.lang.LanguageRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** A fresh install waits briefly for its first pack sync before resolving today's word (virtual time). */
@OptIn(ExperimentalTime::class)
class FirstSyncWaitTest {
    private val registry = LanguageRegistry()
    private val now = Instant.parse("2026-09-17T10:00:00Z")

    /** The server's published calendar: "zesty" (not a bundled answer) every day. */
    private val serverPack = Json.encodeToString(
        CalendarSnapshotDto("en", "7", WordPackSchedule.ANCHOR_EPOCH_DAY, listOf("zesty"), listOf("zesty")),
    )

    /**
     * Pack sync as the provider sees it. Until [finish] runs nothing is cached; [finish] "caches" the server pack (the
     * repository then resolves it) and ends the first sync.
     */
    private class FakeSync(private val repository: () -> WordPackRepository) : FirstPackSync {
        var cached: String? = null
        private val first = CompletableDeferred<Unit>()
        var waited = false

        override suspend fun hasCachedPack(lang: String) = cached != null

        override suspend fun awaitFirstSync(lang: String, timeout: Duration) {
            waited = true
            withTimeoutOrNull(timeout) { first.await() }
        }

        suspend fun finish(pack: String?) {
            cached = pack
            repository().invalidate("en")
            first.complete(Unit)
        }
    }

    private fun setup(): Triple<FakeSync, WordPackRepository, DailyPuzzleProvider> {
        lateinit var repository: WordPackRepository
        val sync = FakeSync { repository }
        // The server pack stands in for the cache; without it the build has no snapshot, so the baseline applies.
        repository = WordPackRepository(registry, snapshots = { sync.cached })
        return Triple(sync, repository, DailyPuzzleProvider(registry, repository, sync))
    }

    private suspend fun baselineWord(): List<String> =
        DailyPuzzleProvider(registry, WordPackRepository(registry, snapshots = { null })).daily("en", now).answer

    @Test
    fun aFirstSyncFinishingWithinTheWaitGivesTheFreshlyCachedWord() = runTest {
        val (sync, _, provider) = setup()
        launch {
            delay(500)
            sync.finish(serverPack)
        }
        val start = currentTime

        val puzzle = provider.daily("en", now)

        assertEquals(500, currentTime - start)
        assertEquals(listOf("z", "e", "s", "t", "y"), puzzle.answer)
    }

    @Test
    fun aHangingSyncResolvesFromTheBundleAfterTwoSecondsWithoutAnError() = runTest {
        val (_, _, provider) = setup()
        val start = currentTime

        val puzzle = provider.daily("en", now)

        assertEquals(DailyPuzzleProvider.FIRST_SYNC_WAIT.inWholeMilliseconds, currentTime - start)
        assertEquals(baselineWord(), puzzle.answer)
        assertNotEquals(listOf("z", "e", "s", "t", "y"), puzzle.answer)
    }

    @Test
    fun aLanguageWithACachedPackDoesNotWait() = runTest {
        val (sync, _, provider) = setup()
        sync.cached = serverPack
        val start = currentTime

        val puzzle = provider.daily("en", now)

        assertEquals(0, currentTime - start)
        assertFalse(sync.waited)
        assertEquals(listOf("z", "e", "s", "t", "y"), puzzle.answer)
    }

    @Test
    fun aFailedFirstSyncEndsTheWaitAtOnce() = runTest {
        val (sync, _, provider) = setup()
        launch {
            delay(300)
            sync.finish(null) // offline: nothing cached
        }
        val start = currentTime

        val puzzle = provider.daily("en", now)

        assertEquals(300, currentTime - start)
        assertEquals(baselineWord(), puzzle.answer)
    }
}
