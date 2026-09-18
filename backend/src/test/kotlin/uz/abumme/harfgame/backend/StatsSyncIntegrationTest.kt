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
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.UsersTable
import uz.abumme.harfgame.data.api.ApiRoutes
import uz.abumme.harfgame.data.auth.AnonymousAuthResponse
import uz.abumme.harfgame.data.sync.ResultRecordDto
import uz.abumme.harfgame.data.sync.UserStatsDto
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class StatsSyncIntegrationTest {

    @BeforeTest
    fun setup() {
        val db = DatabaseFactory.init()
        transaction(db) {
            UsersTable.deleteAll()
        }
    }

    @Test
    fun testUnauthenticatedSyncRequestIsRejected() = testApplication {
        application { module() }

        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // Unauthenticated GET
        val getResp = client.get(ApiRoutes.SYNC_STATS)
        assertEquals(HttpStatusCode.Unauthorized, getResp.status)

        // Unauthenticated POST
        val postResp = client.post(ApiRoutes.SYNC_STATS) {
            contentType(ContentType.Application.Json)
            setBody(UserStatsDto(updatedAt = Instant.fromEpochMilliseconds(System.currentTimeMillis())))
        }
        assertEquals(HttpStatusCode.Unauthorized, postResp.status)
    }

    @Test
    fun testFirstEverUploadAndFetchOnSecondDevice() = testApplication {
        application { module() }

        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val anon = client.post(ApiRoutes.AUTH_ANONYMOUS).body<AnonymousAuthResponse>()

        // 1. Fetching stats before any upload returns empty
        val initialGet = client.get(ApiRoutes.SYNC_STATS) {
            header(HttpHeaders.Authorization, "Bearer ${anon.tokens.accessToken}")
        }.body<UserStatsDto>()
        assertEquals(0, initialGet.records.size)

        // 2. Upload stats snapshot
        val uploadTime = Instant.fromEpochMilliseconds(System.currentTimeMillis() - 1000)
        val records = listOf(
            ResultRecordDto(language = "uz", puzzleDay = 100L, won = true, attempts = 3)
        )
        val uploadResp = client.post(ApiRoutes.SYNC_STATS) {
            header(HttpHeaders.Authorization, "Bearer ${anon.tokens.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(UserStatsDto(updatedAt = uploadTime, records = records))
        }
        assertEquals(HttpStatusCode.OK, uploadResp.status)

        // 3. Fetch stats again
        val afterUploadGet = client.get(ApiRoutes.SYNC_STATS) {
            header(HttpHeaders.Authorization, "Bearer ${anon.tokens.accessToken}")
        }.body<UserStatsDto>()
        assertEquals(1, afterUploadGet.records.size)
        assertEquals(3, afterUploadGet.records[0].attempts)
    }

    @Test
    fun testLastWriteWinsAndOlderSnapshotDiscarded() = testApplication {
        application { module() }

        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val anon = client.post(ApiRoutes.AUTH_ANONYMOUS).body<AnonymousAuthResponse>()

        val t1 = Instant.fromEpochMilliseconds(1000000000L)
        val t2 = Instant.fromEpochMilliseconds(2000000000L)

        // Upload t2 first
        client.post(ApiRoutes.SYNC_STATS) {
            header(HttpHeaders.Authorization, "Bearer ${anon.tokens.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(
                UserStatsDto(
                    updatedAt = t2,
                    records = listOf(ResultRecordDto(language = "uz", puzzleDay = 2L, won = true, attempts = 2))
                )
            )
        }

        // Attempt upload with older t1 -> discarded, server keeps t2
        client.post(ApiRoutes.SYNC_STATS) {
            header(HttpHeaders.Authorization, "Bearer ${anon.tokens.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(
                UserStatsDto(
                    updatedAt = t1,
                    records = listOf(ResultRecordDto(language = "uz", puzzleDay = 1L, won = true, attempts = 1))
                )
            )
        }

        val current = client.get(ApiRoutes.SYNC_STATS) {
            header(HttpHeaders.Authorization, "Bearer ${anon.tokens.accessToken}")
        }.body<UserStatsDto>()

        assertEquals(1, current.records.size)
        assertEquals(2, current.records[0].attempts)
    }

    @Test
    fun testFarFutureTimestampIsRejected() = testApplication {
        application { module() }

        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val anon = client.post(ApiRoutes.AUTH_ANONYMOUS).body<AnonymousAuthResponse>()

        // 1 hour into future
        val futureTime = Instant.fromEpochMilliseconds(System.currentTimeMillis() + 3600_000L)
        val response = client.post(ApiRoutes.SYNC_STATS) {
            header(HttpHeaders.Authorization, "Bearer ${anon.tokens.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(
                UserStatsDto(
                    updatedAt = futureTime,
                    records = listOf(ResultRecordDto(language = "uz", puzzleDay = 999L, won = true, attempts = 1))
                )
            )
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
