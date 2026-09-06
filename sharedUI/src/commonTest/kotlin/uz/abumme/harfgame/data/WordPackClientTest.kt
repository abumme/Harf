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
import uz.abumme.harfgame.data.wordpack.WordPackCache
import uz.abumme.harfgame.data.wordpack.WordPackDto
import uz.abumme.harfgame.data.wordpack.WordPackSchedule
import uz.abumme.harfgame.data.wordpack.WordPackSyncManager
import uz.abumme.harfgame.engine.WordPackRepository
import uz.abumme.harfgame.lang.LanguageRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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

        // valid cache overrides bundle (schedule of 3, not the ~800-entry bundled baseline)
        cache.put(validEnPack("1"))
        val fromCache = WordPackRepository(registry, cache).load("en")
        assertEquals(3, fromCache.schedule.size, "uses the cached schedule")

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
