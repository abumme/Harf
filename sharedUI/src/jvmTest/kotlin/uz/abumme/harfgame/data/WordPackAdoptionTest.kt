package uz.abumme.harfgame.data

import eu.anifantakis.lib.ksafe.KSafe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import uz.abumme.harfgame.data.wordpack.WordPackCache
import uz.abumme.harfgame.data.wordpack.WordPackDto
import uz.abumme.harfgame.data.wordpack.WordPackSchedule
import uz.abumme.harfgame.data.wordpack.WordPackSyncManager
import uz.abumme.harfgame.engine.WordPackRepository
import uz.abumme.harfgame.lang.LanguageRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Sync hands the pack its integrity check built to the repository instead of evicting and rebuilding it. */
class WordPackAdoptionTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val registry = LanguageRegistry()

    private val validPack = WordPackDto(
        lang = "en",
        version = "1",
        effectiveFrom = WordPackSchedule.ANCHOR_EPOCH_DAY,
        anchorEpochDay = WordPackSchedule.ANCHOR_EPOCH_DAY,
        answers = listOf("bread", "crane"),
        guesses = listOf("bread", "crane", "slate"),
        schedule = listOf("bread", "crane", "bread"),
    )

    /** A store emptied first: the file outlives the run, so leftovers must not satisfy the assertions. */
    private suspend fun freshStore(name: String) = KSafe(fileName = name).also { it.delete("wordpack.cache.en") }

    private fun manager(served: WordPackDto, cache: WordPackCache, repository: WordPackRepository) = WordPackSyncManager(
        httpClient = HttpClient(MockEngine {
            respond(json.encodeToString(served), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json(json) } },
        baseUrl = "http://test",
        cache = cache,
        repository = repository,
        registry = registry,
    )

    @Test
    fun aValidFetchedPackIsAdoptedAsBuilt() = runBlocking {
        val store = freshStore("wordpack_adoption_valid_test")
        val cache = WordPackCache(store)
        // No cache and no snapshot behind the repository: a rebuild could only produce the long bundled baseline.
        val repository = WordPackRepository(registry, cache = null, snapshots = { null })
        val baseline = repository.load("en")
        assertTrue(baseline.schedule.size > 3)

        manager(validPack, cache, repository).syncOne("en")

        val adopted = repository.load("en")
        assertEquals(3, adopted.schedule.size, "the next game uses the fetched pack")
        assertSame(adopted, repository.load("en"), "and keeps using that instance")
        assertEquals("1", WordPackCache(store).get("en")?.version, "the pack is cached for later launches")
    }

    @Test
    fun anInvalidFetchedPackChangesNothing() = runBlocking {
        val cache = WordPackCache(freshStore("wordpack_adoption_invalid_test"))
        val repository = WordPackRepository(registry, cache = null, snapshots = { null })
        val current = repository.load("en")
        val invalid = validPack.copy(version = "2", answers = listOf("waytoolongword"), schedule = listOf("waytoolongword"))

        val sync = manager(invalid, cache, repository)
        sync.syncOne("en")

        assertSame(current, repository.load("en"), "the resolved pack stays in use")
        assertNull(cache.get("en"), "nothing is cached")
        assertTrue(sync.hasCachedPack("en"), "after the first sync there is nothing left to wait for")
    }
}
