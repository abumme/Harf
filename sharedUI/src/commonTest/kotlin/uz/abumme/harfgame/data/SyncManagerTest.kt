package uz.abumme.harfgame.data

import eu.anifantakis.lib.ksafe.KSafe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uz.abumme.harfgame.data.api.ApiRoutes
import uz.abumme.harfgame.data.auth.SessionStore
import uz.abumme.harfgame.data.network.KtorAuthService
import uz.abumme.harfgame.data.network.KtorSyncService
import uz.abumme.harfgame.data.stats.ResultLog
import uz.abumme.harfgame.data.stats.ResultRecord
import uz.abumme.harfgame.data.stats.SyncManager
import uz.abumme.harfgame.data.sync.ResultRecordDto
import uz.abumme.harfgame.data.sync.UserStatsDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncManagerTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testPushStatsUploadsCurrentResultLog() = runTest {
        val ksafe = KSafe()
        val resultLog = ResultLog(ksafe)
        resultLog.clear()
        val sessionStore = SessionStore(ksafe)
        sessionStore.clear()
        sessionStore.saveSession(
            userId = "user-1",
            accessToken = "access-1",
            refreshToken = "refresh-1"
        )

        val record = ResultRecord("uz-latn", 100L, won = true, attempts = 4)
        resultLog.record(record)

        var uploadedDto: UserStatsDto? = null
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath == ApiRoutes.SYNC_STATS) {
                val bodyStr = request.body.toByteArray().decodeToString()
                uploadedDto = json.decodeFromString<UserStatsDto>(bodyStr)
                respond(
                    content = """{"status":"updated"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                error("Unexpected route ${request.url.encodedPath}")
            }
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }

        val authService = KtorAuthService(httpClient, baseUrl = "http://test", sessionStore = sessionStore)
        val syncService = KtorSyncService(httpClient, baseUrl = "http://test", sessionStore = sessionStore, authService = authService)
        val syncManager = SyncManager(resultLog, sessionStore, authService, syncService)

        syncManager.pushStats()

        assertTrue(uploadedDto != null)
        assertEquals(1, uploadedDto!!.records.size)
        assertEquals("uz-latn", uploadedDto!!.records[0].language)
        assertEquals(100L, uploadedDto!!.records[0].puzzleDay)
        assertEquals(4, uploadedDto!!.records[0].attempts)
    }

    @Test
    fun testPullStatsMergesIntoLocalResultLog() = runTest {
        val ksafe = KSafe()
        val resultLog = ResultLog(ksafe)
        resultLog.clear()
        val sessionStore = SessionStore(ksafe)
        sessionStore.clear()
        sessionStore.saveSession(
            userId = "user-1",
            accessToken = "access-1",
            refreshToken = "refresh-1"
        )

        // Local has day 1
        resultLog.record(ResultRecord("uz-latn", 1L, won = true, attempts = 2))

        val remoteStats = UserStatsDto(
            updatedAt = Instant.fromEpochMilliseconds(5000L),
            records = listOf(
                ResultRecordDto(language = "uz-latn", puzzleDay = 1L, won = true, attempts = 2),
                ResultRecordDto(language = "uz-latn", puzzleDay = 2L, won = true, attempts = 5),
            )
        )

        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath == ApiRoutes.SYNC_STATS) {
                respond(
                    content = json.encodeToString(remoteStats),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                error("Unexpected route")
            }
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }

        val authService = KtorAuthService(httpClient, baseUrl = "http://test", sessionStore = sessionStore)
        val syncService = KtorSyncService(httpClient, baseUrl = "http://test", sessionStore = sessionStore, authService = authService)
        val syncManager = SyncManager(resultLog, sessionStore, authService, syncService)

        val success = syncManager.pullStats()
        assertTrue(success)

        val allLocal = resultLog.all()
        assertEquals(2, allLocal.size, "Should have merged both day 1 and day 2")
        assertTrue(allLocal.any { it.puzzleDay == 1L })
        assertTrue(allLocal.any { it.puzzleDay == 2L })
    }

    @Test
    fun testDeleteAccountClearsSessionAndLocalStats() = runTest {
        val ksafe = KSafe()
        val resultLog = ResultLog(ksafe)
        resultLog.clear()
        val sessionStore = SessionStore(ksafe)
        sessionStore.clear()
        sessionStore.saveSession(
            userId = "user-1",
            accessToken = "access-1",
            refreshToken = "refresh-1"
        )
        resultLog.record(ResultRecord("uz-latn", 1L, won = true, attempts = 2))

        var deleteCalled = false
        val mockEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                ApiRoutes.ACCOUNT -> {
                    deleteCalled = true
                    respond(
                        content = """{"status":"deleted"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                ApiRoutes.AUTH_ANONYMOUS -> {
                    respond(
                        content = json.encodeToString(
                            uz.abumme.harfgame.data.auth.AnonymousAuthResponse(
                                userId = "new-anon",
                                tokens = uz.abumme.harfgame.data.auth.TokenPairDto("new-access", "new-refresh")
                            )
                        ),
                        status = HttpStatusCode.Created,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                ApiRoutes.SYNC_STATS -> {
                    respond(
                        content = json.encodeToString(UserStatsDto(updatedAt = Instant.fromEpochMilliseconds(0))),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> error("Unexpected route")
            }
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }

        val authService = KtorAuthService(httpClient, baseUrl = "http://test", sessionStore = sessionStore)
        val syncService = KtorSyncService(httpClient, baseUrl = "http://test", sessionStore = sessionStore, authService = authService)
        val syncManager = SyncManager(resultLog, sessionStore, authService, syncService)

        syncManager.deleteAccount()
        assertTrue(deleteCalled)
        assertEquals(0, resultLog.all().size, "Local result log must be wiped")
    }
}
