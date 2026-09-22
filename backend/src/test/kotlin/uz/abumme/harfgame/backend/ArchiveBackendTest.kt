package uz.abumme.harfgame.backend

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uz.abumme.harfgame.backend.db.ArchiveRunsTable
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.UsersTable
import uz.abumme.harfgame.data.api.ApiRoutes
import uz.abumme.harfgame.data.archive.ArchiveHistoryDto
import uz.abumme.harfgame.data.archive.ArchiveRowDto
import uz.abumme.harfgame.data.archive.ArchiveRunDto
import uz.abumme.harfgame.data.archive.ArchiveUploadRequest
import uz.abumme.harfgame.data.archive.ArchiveUploadResponse
import uz.abumme.harfgame.data.auth.AnonymousAuthResponse
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ArchiveBackendTest {

    @BeforeTest
    fun setup() {
        val db = DatabaseFactory.init()
        transaction(db) {
            UsersTable.deleteAll()
        }
    }

    @Test
    fun testUnauthenticatedArchiveRequestsAreRejected() = testApplication {
        application { module() }

        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val getResp = client.get(ApiRoutes.ARCHIVE_RUNS)
        assertEquals(HttpStatusCode.Unauthorized, getResp.status)

        val postResp = client.post(ApiRoutes.ARCHIVE_RUNS) {
            contentType(ContentType.Application.Json)
            setBody(ArchiveUploadRequest(emptyList()))
        }
        assertEquals(HttpStatusCode.Unauthorized, postResp.status)
    }

    @Test
    fun testAccountIsolationAndIdempotentDuplicateUploads() = testApplication {
        application { module() }

        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val userA = client.post(ApiRoutes.AUTH_ANONYMOUS).body<AnonymousAuthResponse>()
        val userB = client.post(ApiRoutes.AUTH_ANONYMOUS).body<AnonymousAuthResponse>()

        val runA = ArchiveRunDto(
            runId = "run-a-1",
            language = "en",
            puzzleDay = 500L,
            won = true,
            attempts = 4,
            hardMode = true,
            rows = listOf(ArchiveRowDto(listOf("a", "p", "p", "l", "e"), listOf(2, 2, 2, 2, 2))),
            completedAt = Instant.parse("2026-08-20T12:00:00Z"),
        )

        val runB = ArchiveRunDto(
            runId = "run-b-1",
            language = "ru",
            puzzleDay = 501L,
            won = false,
            attempts = 6,
            hardMode = false,
            rows = listOf(ArchiveRowDto(listOf("п", "о", "е", "з", "д"), listOf(0, 0, 0, 0, 0))),
            completedAt = Instant.parse("2026-08-21T12:00:00Z"),
        )

        // Upload runA from userA
        val uploadAResp = client.post(ApiRoutes.ARCHIVE_RUNS) {
            header(HttpHeaders.Authorization, "Bearer ${userA.tokens.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(ArchiveUploadRequest(listOf(runA)))
        }
        assertEquals(HttpStatusCode.OK, uploadAResp.status)
        assertEquals(1, uploadAResp.body<ArchiveUploadResponse>().acceptedCount)

        // Upload runB from userB
        val uploadBResp = client.post(ApiRoutes.ARCHIVE_RUNS) {
            header(HttpHeaders.Authorization, "Bearer ${userB.tokens.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(ArchiveUploadRequest(listOf(runB)))
        }
        assertEquals(HttpStatusCode.OK, uploadBResp.status)
        assertEquals(1, uploadBResp.body<ArchiveUploadResponse>().acceptedCount)

        // UserA sees only runA
        val listA = client.get(ApiRoutes.ARCHIVE_RUNS) {
            header(HttpHeaders.Authorization, "Bearer ${userA.tokens.accessToken}")
        }.body<ArchiveHistoryDto>()
        assertEquals(1, listA.runs.size)
        assertEquals("run-a-1", listA.runs[0].runId)

        // UserB sees only runB
        val listB = client.get(ApiRoutes.ARCHIVE_RUNS) {
            header(HttpHeaders.Authorization, "Bearer ${userB.tokens.accessToken}")
        }.body<ArchiveHistoryDto>()
        assertEquals(1, listB.runs.size)
        assertEquals("run-b-1", listB.runs[0].runId)

        // Duplicate upload from UserA with same runId
        client.post(ApiRoutes.ARCHIVE_RUNS) {
            header(HttpHeaders.Authorization, "Bearer ${userA.tokens.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(ArchiveUploadRequest(listOf(runA)))
        }

        // Verify duplicate created no new runs; userA still has exactly 1 run
        val listAAfterDup = client.get(ApiRoutes.ARCHIVE_RUNS) {
            header(HttpHeaders.Authorization, "Bearer ${userA.tokens.accessToken}")
        }.body<ArchiveHistoryDto>()
        assertEquals(1, listAAfterDup.runs.size)
        assertEquals("run-a-1", listAAfterDup.runs[0].runId)
    }

    @Test
    fun testAccountDeletionCascadesToArchiveRuns() = testApplication {
        application { module() }

        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val anon = client.post(ApiRoutes.AUTH_ANONYMOUS).body<AnonymousAuthResponse>()

        val run = ArchiveRunDto(
            runId = "run-cascade-1",
            language = "en",
            puzzleDay = 500L,
            won = true,
            attempts = 3,
            hardMode = false,
            rows = emptyList(),
            completedAt = Instant.parse("2026-08-20T12:00:00Z"),
        )

        client.post(ApiRoutes.ARCHIVE_RUNS) {
            header(HttpHeaders.Authorization, "Bearer ${anon.tokens.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(ArchiveUploadRequest(listOf(run)))
        }

        val db = DatabaseFactory.init()
        transaction(db) {
            assertEquals(1, ArchiveRunsTable.selectAll().where { ArchiveRunsTable.userId eq anon.userId }.count())
            // Cascade delete user
            UsersTable.deleteWhere { UsersTable.id eq anon.userId }
            assertEquals(0, ArchiveRunsTable.selectAll().where { ArchiveRunsTable.userId eq anon.userId }.count())
        }
    }
}
