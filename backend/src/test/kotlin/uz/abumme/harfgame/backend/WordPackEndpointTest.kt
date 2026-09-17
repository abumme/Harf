package uz.abumme.harfgame.backend

import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.WordPacksTable
import uz.abumme.harfgame.backend.db.WordsTable
import uz.abumme.harfgame.backend.service.WordPackServerService
import uz.abumme.harfgame.data.api.ApiRoutes
import uz.abumme.harfgame.data.wordpack.WordPackDto
import java.util.zip.GZIPInputStream
import kotlin.test.assertContentEquals
import kotlin.test.assertNull
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WordPackEndpointTest {

    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setup() {
        val db = DatabaseFactory.init()
        transaction(db) {
            clearDailyCalendars()
            WordsTable.deleteAll()
            WordPacksTable.deleteAll()
        }
    }

    @Test
    fun seedIsIdempotentAndProducesValidPacks() = runBlocking {
        val service = WordPackServerService()
        service.seed()
        service.seed() // second run must not duplicate

        val db = DatabaseFactory.init()
        val count = transaction(db) { WordPacksTable.selectAll().count() }
        assertEquals(5, count, "one row per language, no duplicates")

        for (lang in listOf("en", "ru", "kk", "uz-latn", "uz-cyrl")) {
            val pack = service.getPack(lang)
            assertNotNull(pack, "seeded pack for $lang")
            assertTrue(pack.answers.all { it in pack.guesses }, "$lang answers subset of guesses")
            assertTrue(pack.schedule.isNotEmpty() && pack.schedule.all { it in pack.answers }, "$lang schedule from answers")
            // no adjacent repeat (unless there is only one answer)
            if (pack.answers.size > 1) {
                assertTrue(pack.schedule.zipWithNext().none { (a, b) -> a == b }, "$lang schedule has no adjacent repeat")
            }
        }
    }

    @Test
    fun endpointReturnsPackThen304Then404() = testApplication {
        runBlocking { WordPackServerService().seed() }
        application { module() }
        val client = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

        // 200 with ETag
        val resp = client.get(ApiRoutes.wordpack("en"))
        assertEquals(HttpStatusCode.OK, resp.status)
        assertEquals("1", resp.headers[HttpHeaders.ETag])
        val pack = json.decodeFromString<WordPackDto>(resp.bodyAsText())
        assertEquals("en", pack.lang)
        assertEquals("1", pack.version)
        assertTrue(pack.schedule.isNotEmpty())

        // 304 when If-None-Match matches
        val notModified = client.get(ApiRoutes.wordpack("en")) {
            header(HttpHeaders.IfNoneMatch, "1")
        }
        assertEquals(HttpStatusCode.NotModified, notModified.status)

        // 404 for an unserved language
        val missing = client.get(ApiRoutes.wordpack("xx"))
        assertEquals(HttpStatusCode.NotFound, missing.status)
    }

    @Test
    fun packIsCompressedOnlyWhenTheClientAcceptsIt() = testApplication {
        runBlocking { WordPackServerService().seed() }
        application { module() }
        val plain = client.get(ApiRoutes.wordpack("en"))
        assertEquals(HttpStatusCode.OK, plain.status)
        assertNull(plain.headers[HttpHeaders.ContentEncoding])
        val plainBody = plain.readRawBytes()

        val gzipped = client.get(ApiRoutes.wordpack("en")) { header(HttpHeaders.AcceptEncoding, "gzip") }
        assertEquals(HttpStatusCode.OK, gzipped.status)
        assertEquals("gzip", gzipped.headers[HttpHeaders.ContentEncoding])
        assertEquals("1", gzipped.headers[HttpHeaders.ETag])
        val compressed = gzipped.readRawBytes()
        val decoded = GZIPInputStream(compressed.inputStream()).readBytes()
        assertContentEquals(plainBody, decoded)
        assertTrue(compressed.size * 2 < plainBody.size, "gzip ${compressed.size} vs ${plainBody.size} bytes")
        assertEquals(json.decodeFromString<WordPackDto>(plainBody.decodeToString()), json.decodeFromString<WordPackDto>(decoded.decodeToString()))

        val deflated = client.get(ApiRoutes.wordpack("en")) { header(HttpHeaders.AcceptEncoding, "deflate") }
        assertEquals("deflate", deflated.headers[HttpHeaders.ContentEncoding])

        // A conditional request still answers not-modified, compressed or not.
        val notModified = client.get(ApiRoutes.wordpack("en")) {
            header(HttpHeaders.AcceptEncoding, "gzip")
            header(HttpHeaders.IfNoneMatch, "1")
        }
        assertEquals(HttpStatusCode.NotModified, notModified.status)
    }
}
