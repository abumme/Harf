package uz.abumme.harfgame.data

import eu.anifantakis.lib.ksafe.KSafe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import uz.abumme.harfgame.data.api.ApiRoutes
import uz.abumme.harfgame.data.wordpack.CalendarSnapshotDto
import uz.abumme.harfgame.data.wordpack.WordPackCache
import uz.abumme.harfgame.data.wordpack.WordPackDto
import uz.abumme.harfgame.data.wordpack.WordPackSchedule
import uz.abumme.harfgame.data.wordpack.WordPackSyncManager
import uz.abumme.harfgame.engine.WordPackRepository
import uz.abumme.harfgame.lang.LanguageRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WordPackClientTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val registry = LanguageRegistry()

    private fun validEnPack(version: String) = WordPackDto(
        lang = "en",
        version = version,
        effectiveFrom = WordPackSchedule.ANCHOR_EPOCH_DAY,
        anchorEpochDay = WordPackSchedule.ANCHOR_EPOCH_DAY,
        answers = listOf("bread", "crane"),
        guesses = listOf("bread", "crane", "slate"),
        schedule = listOf("bread", "crane", "bread"),
    )

    @Test
    fun cachedPackSurvivesRestart() = runTest {
        val ksafe = KSafe()
        WordPackCache(ksafe).put(validEnPack("1"))
        // new instance = simulated restart
        val reloaded = WordPackCache(ksafe).get("en")
        assertNotNull(reloaded)
        assertEquals("1", reloaded.version)
    }

    @Test
    fun syncCachesValidPackThen304AndErrorKeepIt() = runBlocking {
        val ksafe = KSafe()
        val cache = WordPackCache(ksafe).also { /* empty */ }
        val repo = WordPackRepository(registry, cache)
        var mode = "200"
        val engine = MockEngine { _ ->
            when (mode) {
                "200" -> respond(
                    content = json.encodeToString(validEnPack("1")),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                "304" -> respond(content = "", status = HttpStatusCode.NotModified)
                else -> respondError(HttpStatusCode.InternalServerError)
            }
        }
        val client = HttpClient(engine) { install(ContentNegotiation) { json(json) } }
        val sync = WordPackSyncManager(client, "http://test", cache, repo, registry)

        sync.syncOne("en")
        assertEquals("1", cache.get("en")?.version, "200 caches the pack")

        mode = "304"
        sync.syncOne("en")
        assertEquals("1", cache.get("en")?.version, "304 keeps the cached pack")

        mode = "error"
        sync.syncOne("en")
        assertEquals("1", cache.get("en")?.version, "an error leaves the cache intact")
    }

    @Test
    fun repositoryPrefersValidCacheThenFallsBackToBundle() = runTest {
        val ksafe = KSafe()
        val cache = WordPackCache(ksafe)

        // valid cache overrides bundle (schedule of 3, not the ~800-entry bundled baseline) and the build's snapshot
        cache.put(validEnPack("1"))
        val fromCache = WordPackRepository(registry, cache).load("en")
        assertEquals(3, fromCache.schedule.size, "uses the cached schedule")
        val cacheOverSnapshot = WordPackRepository(registry, cache, snapshots = { snapshotJson(scheduleDays = 5) }).load("en")
        assertEquals(3, cacheOverSnapshot.schedule.size, "a cached server pack beats the calendar snapshot")

        // invalid cache (answer too long) → falls back to the bundle
        val badKsafe = KSafe()
        val badCache = WordPackCache(badKsafe)
        badCache.put(validEnPack("2").copy(answers = listOf("waytoolongword"), schedule = listOf("waytoolongword")))
        val fallback = WordPackRepository(registry, badCache).load("en")
        assertTrue(fallback.schedule.size > 3, "invalid cache is ignored; bundled baseline used")

        // empty cache offline → bundle
        val bundled = WordPackRepository(registry, WordPackCache(KSafe())).load("en")
        assertTrue(bundled.schedule.isNotEmpty(), "bundled baseline yields a schedule offline")
    }

    @Test
    fun anAdoptedPackIsLoadedWithoutARebuild() = runTest {
        val repo = WordPackRepository(registry, snapshots = { null })
        val pack = assertNotNull(repo.build(validEnPack("1")))
        repo.adopt(pack)
        assertSame(pack, repo.load("en"), "load returns the adopted instance, not a rebuilt one")
    }

    @Test
    fun aRejectedPackLeavesTheResolvedOneInUse() = runTest {
        val repo = WordPackRepository(registry, snapshots = { null })
        val current = repo.load("en")
        val tooLong = validEnPack("2").copy(answers = listOf("waytoolongword"), schedule = listOf("waytoolongword"))
        assertNull(repo.build(tooLong), "integrity rejects the pack")
        assertSame(current, repo.load("en"), "the previously resolved pack stays active")
    }

    /** A calendar snapshot for English as the refresh task writes it. */
    private fun snapshotJson(scheduleDays: Int, answer: String = "crane", lang: String = "en") = json.encodeToString(
        CalendarSnapshotDto(
            lang = lang,
            version = "42",
            anchorEpochDay = WordPackSchedule.ANCHOR_EPOCH_DAY,
            answers = listOf(answer, "bread"),
            schedule = List(scheduleDays) { if (it % 2 == 0) answer else "bread" },
        ),
    )

    @Test
    fun aValidCalendarSnapshotBeatsTheGeneratedBaseline() = runTest {
        val pack = WordPackRepository(registry, snapshots = { snapshotJson(scheduleDays = 7) }).load("en")
        assertEquals(7, pack.schedule.size, "the snapshot's schedule")
        assertEquals(WordPackSchedule.ANCHOR_EPOCH_DAY, pack.anchorEpochDay)
        val tokenizer = registry.tokenizer("en")!!
        assertTrue(pack.isValidGuess(tokenizer.tokenize("apple")!!), "the bundled guess dictionary still applies")
        assertTrue(pack.isValidGuess(tokenizer.tokenize("crane")!!), "snapshot words are guesses")
        assertEquals(emptyList(), WordPackRepository(registry, snapshots = { snapshotJson(scheduleDays = 7) }).validate("en"))
    }

    @Test
    fun aMissingOrInvalidCalendarSnapshotFallsBackToTheBaseline() = runTest {
        val baseline = WordPackRepository(registry, snapshots = { null }).load("en")
        assertTrue(baseline.schedule.size > 7, "no snapshot: the generated baseline")

        val sources: List<suspend (String) -> String?> = listOf(
            { snapshotJson(scheduleDays = 7, answer = "waytoolongword") }, // fails the integrity check
            { "not json" },
            { snapshotJson(scheduleDays = 7, lang = "ru") }, // another language's file
            { throw IllegalStateException("missing resource") },
        )
        for (source in sources) {
            val pack = WordPackRepository(registry, snapshots = source).load("en")
            assertEquals(baseline.schedule, pack.schedule)
        }
        // An invalid committed snapshot fails the bundled-files check; a missing one does not.
        val problems = WordPackRepository(registry, snapshots = { snapshotJson(scheduleDays = 7, answer = "waytoolongword") }).validate("en")
        assertTrue(problems.any { it.startsWith("calendar snapshot") }, problems.toString())
        assertEquals(emptyList(), WordPackRepository(registry, snapshots = { null }).validate("en"))
    }

    @Test
    fun appendingFutureDaysLeavesPastDaysUnchanged() {
        val anchor = 100L
        val v1 = listOf("a", "b", "c")
        val v2 = v1 + listOf("d", "e") // an update only appends future days
        for (day in 100L..102L) {
            assertEquals(
                WordPackSchedule.answerFor(v1, anchor, day),
                WordPackSchedule.answerFor(v2, anchor, day),
                "day $day is immutable across the appended update",
            )
        }
    }
}
