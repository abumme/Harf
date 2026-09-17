package uz.abumme.harfgame.backend

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uz.abumme.harfgame.backend.admin.analytics.gameResultCount
import uz.abumme.harfgame.backend.admin.analytics.resetAnalyticsData
import uz.abumme.harfgame.backend.admin.analytics.storedResults
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.UsersTable
import uz.abumme.harfgame.data.api.ApiErrorResponse
import uz.abumme.harfgame.data.api.ApiRoutes
import uz.abumme.harfgame.data.auth.AnonymousAuthResponse
import uz.abumme.harfgame.data.sync.ResultRecordDto
import uz.abumme.harfgame.data.sync.UserStatsDto
import java.time.LocalDate
import java.time.ZoneId
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

    // --- analytics: uploads also record game results -----------------------------------------------------------------

    private val today = LocalDate.now(ZoneId.of("Europe/Moscow")).toEpochDay() - 1

    private fun record(lang: String, daysAgo: Long, won: Boolean = true, attempts: Int = 3) = ResultRecordDto(lang, today - daysAgo, won, attempts)

    private fun ApplicationTestBuilder.jsonClient(): HttpClient {
        application { module() }
        return createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
    }

    private suspend fun HttpClient.upload(token: String, updatedAtMillis: Long, records: List<ResultRecordDto>): HttpResponse =
        post(ApiRoutes.SYNC_STATS) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(UserStatsDto(updatedAt = Instant.fromEpochMilliseconds(updatedAtMillis), records = records))
        }

    @Test
    fun uploadedRecordsBecomeGameResultsAndReUploadingAddsNothing() = testApplication {
        resetAnalyticsData()
        val client = jsonClient()
        val anon = client.post(ApiRoutes.AUTH_ANONYMOUS).body<AnonymousAuthResponse>()
        val records = listOf(record("en", 2), record("ru", 1, won = false, attempts = 6))
        val uploadedAt = System.currentTimeMillis() - 60_000
        val first = client.upload(anon.tokens.accessToken, uploadedAt, records)
        assertEquals(HttpStatusCode.OK, first.status)
        assertEquals("""{"status":"updated"}""", first.bodyAsText())
        assertEquals(records.sortedWith(compareBy({ it.language }, { it.puzzleDay })), storedResults(anon.userId))

        // The same snapshot again (kept), and a newer one repeating the records (updated): nothing changes.
        val again = client.upload(anon.tokens.accessToken, uploadedAt, records)
        assertEquals("""{"status":"stored_snapshot_kept"}""", again.bodyAsText())
        val newer = client.upload(anon.tokens.accessToken, System.currentTimeMillis() - 30_000, records)
        assertEquals("""{"status":"updated"}""", newer.bodyAsText())
        assertEquals(2, gameResultCount())
    }

    @Test
    fun aNewerSnapshotWithoutAResultKeepsItAndAChangedOutcomeDoesNotReplaceIt() = testApplication {
        resetAnalyticsData()
        val client = jsonClient()
        val anon = client.post(ApiRoutes.AUTH_ANONYMOUS).body<AnonymousAuthResponse>()
        client.upload(anon.tokens.accessToken, System.currentTimeMillis() - 60_000, listOf(record("en", 3), record("en", 2)))
        val newer = client.upload(anon.tokens.accessToken, System.currentTimeMillis() - 30_000, listOf(record("en", 2, won = false, attempts = 6)))
        assertEquals(HttpStatusCode.OK, newer.status)
        // The stored snapshot follows the upload; the recorded results do not.
        assertEquals(listOf(record("en", 3), record("en", 2)).sortedBy { it.puzzleDay }, storedResults(anon.userId))
    }

    @Test
    fun anOlderSnapshotIsKeptOutOfTheStoredStatsButStillContributesNewResults() = testApplication {
        resetAnalyticsData()
        val client = jsonClient()
        val anon = client.post(ApiRoutes.AUTH_ANONYMOUS).body<AnonymousAuthResponse>()
        client.upload(anon.tokens.accessToken, System.currentTimeMillis() - 30_000, listOf(record("kk", 1)))
        val older = client.upload(anon.tokens.accessToken, System.currentTimeMillis() - 60_000, listOf(record("kk", 1), record("uz-latn", 4, attempts = 5)))
        assertEquals(HttpStatusCode.OK, older.status)
        assertEquals("""{"status":"stored_snapshot_kept"}""", older.bodyAsText())
        assertEquals(listOf(record("kk", 1)), client.get(ApiRoutes.SYNC_STATS) { header(HttpHeaders.Authorization, "Bearer ${anon.tokens.accessToken}") }.body<UserStatsDto>().records)
        assertEquals(listOf(record("kk", 1), record("uz-latn", 4, attempts = 5)), storedResults(anon.userId))
    }

    @Test
    fun aFarFutureUploadRecordsNothing() = testApplication {
        resetAnalyticsData()
        val client = jsonClient()
        val anon = client.post(ApiRoutes.AUTH_ANONYMOUS).body<AnonymousAuthResponse>()
        val response = client.upload(anon.tokens.accessToken, System.currentTimeMillis() + 3_600_000, listOf(record("en", 1)))
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("invalid_timestamp", Json.decodeFromString<ApiErrorResponse>(response.bodyAsText()).error)
        assertEquals(0, gameResultCount())
    }

    @Test
    fun anImplausibleRecordIsSkippedWhileTheOthersAreStored() = testApplication {
        resetAnalyticsData()
        val client = jsonClient()
        val anon = client.post(ApiRoutes.AUTH_ANONYMOUS).body<AnonymousAuthResponse>()
        val response = client.upload(
            anon.tokens.accessToken,
            System.currentTimeMillis() - 60_000,
            listOf(
                record("en", 1),
                record("en", 2, attempts = 0),
                record("ru", 2, attempts = 7),
                record("uz", 2),
                record("ru", -3),
            ),
        )
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"status":"updated"}""", response.bodyAsText())
        assertEquals(listOf(record("en", 1)), storedResults(anon.userId))
        // The snapshot itself is stored as uploaded, implausible records included.
        assertEquals(5, client.get(ApiRoutes.SYNC_STATS) { header(HttpHeaders.Authorization, "Bearer ${anon.tokens.accessToken}") }.body<UserStatsDto>().records.size)
    }
}
