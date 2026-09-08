package uz.abumme.harfgame.data

import eu.anifantakis.lib.ksafe.KSafe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uz.abumme.harfgame.data.api.ApiResult
import uz.abumme.harfgame.data.api.ApiRoutes
import uz.abumme.harfgame.data.auth.OAuthProvider
import uz.abumme.harfgame.data.auth.SessionStore
import uz.abumme.harfgame.data.network.KtorAuthService
import uz.abumme.harfgame.data.network.KtorSyncService
import uz.abumme.harfgame.data.service.AuthService
import uz.abumme.harfgame.data.service.SuggestionService
import uz.abumme.harfgame.data.service.SyncService
import uz.abumme.harfgame.data.suggestion.SuggestWordRequest
import uz.abumme.harfgame.data.suggestion.SuggestWordResponse
import uz.abumme.harfgame.data.stats.PendingUploadStore
import uz.abumme.harfgame.data.stats.ResultLog
import uz.abumme.harfgame.data.stats.ResultRecord
import uz.abumme.harfgame.data.stats.RoundStore
import uz.abumme.harfgame.data.stats.SyncManager
import uz.abumme.harfgame.data.sync.ResultRecordDto
import uz.abumme.harfgame.data.sync.UserStatsDto
import uz.abumme.harfgame.lang.LanguageRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncManagerTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun manager(
        resultLog: ResultLog,
        sessionStore: SessionStore,
        authService: AuthService,
        syncService: SyncService,
        pendingStore: PendingUploadStore,
        roundStore: RoundStore,
        suggestionService: SuggestionService = RecordingSuggestionService(),
    ) = SyncManager(
        resultLog = resultLog,
        sessionStore = sessionStore,
        authService = authService,
        syncService = syncService,
        suggestionService = suggestionService,
        pendingStore = pendingStore,
        roundStore = roundStore,
        languageRegistry = LanguageRegistry(emptyMap()),
    )

    private class RecordingSuggestionService : SuggestionService {
        var lastToken: String? = null
        var lastRequest: SuggestWordRequest? = null
        override suspend fun suggest(token: String, request: SuggestWordRequest): ApiResult<SuggestWordResponse> {
            lastToken = token
            lastRequest = request
            return ApiResult.Success(SuggestWordResponse(status = "PENDING"))
        }
    }

    @Test
    fun testPushStatsUploadsCurrentResultLog() = runTest {
        val ksafe = KSafe()
        val resultLog = ResultLog(ksafe)
        resultLog.clear()
        val sessionStore = SessionStore(ksafe)
        sessionStore.clear()
        sessionStore.saveSession(userId = "user-1", accessToken = "access-1", refreshToken = "refresh-1")

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

        val httpClient = HttpClient(mockEngine) { install(ContentNegotiation) { json(json) } }
        val authService = KtorAuthService(httpClient, baseUrl = "http://test", sessionStore = sessionStore)
        val syncService = KtorSyncService(httpClient, baseUrl = "http://test", sessionStore = sessionStore, authService = authService)
        val pendingStore = PendingUploadStore(ksafe).also { it.clear() }
        val syncManager = manager(resultLog, sessionStore, authService, syncService, pendingStore, RoundStore(ksafe))

        syncManager.pushStats()

        assertTrue(uploadedDto != null)
        assertEquals(1, uploadedDto!!.records.size)
        assertEquals("uz-latn", uploadedDto!!.records[0].language)
        assertEquals(100L, uploadedDto!!.records[0].puzzleDay)
        assertEquals(4, uploadedDto!!.records[0].attempts)
        assertFalse(pendingStore.isDirty(), "A confirmed upload clears the pending marker")
    }

    @Test
    fun testPullStatsMergesIntoLocalResultLog() = runTest {
        val ksafe = KSafe()
        val resultLog = ResultLog(ksafe)
        resultLog.clear()
        val sessionStore = SessionStore(ksafe)
        sessionStore.clear()
        sessionStore.saveSession(userId = "user-1", accessToken = "access-1", refreshToken = "refresh-1")

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

        val httpClient = HttpClient(mockEngine) { install(ContentNegotiation) { json(json) } }
        val authService = KtorAuthService(httpClient, baseUrl = "http://test", sessionStore = sessionStore)
        val syncService = KtorSyncService(httpClient, baseUrl = "http://test", sessionStore = sessionStore, authService = authService)
        val syncManager = manager(resultLog, sessionStore, authService, syncService, PendingUploadStore(ksafe).also { it.clear() }, RoundStore(ksafe))

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
        sessionStore.saveSession(userId = "user-1", accessToken = "access-1", refreshToken = "refresh-1")
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
                ApiRoutes.AUTH_ANONYMOUS -> respond(
                    content = json.encodeToString(
                        uz.abumme.harfgame.data.auth.AnonymousAuthResponse(
                            userId = "new-anon",
                            tokens = uz.abumme.harfgame.data.auth.TokenPairDto("new-access", "new-refresh")
                        )
                    ),
                    status = HttpStatusCode.Created,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                ApiRoutes.SYNC_STATS -> respond(
                    content = json.encodeToString(UserStatsDto(updatedAt = Instant.fromEpochMilliseconds(0))),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                else -> error("Unexpected route")
            }
        }

        val httpClient = HttpClient(mockEngine) { install(ContentNegotiation) { json(json) } }
        val authService = KtorAuthService(httpClient, baseUrl = "http://test", sessionStore = sessionStore)
        val syncService = KtorSyncService(httpClient, baseUrl = "http://test", sessionStore = sessionStore, authService = authService)
        val syncManager = manager(resultLog, sessionStore, authService, syncService, PendingUploadStore(ksafe).also { it.clear() }, RoundStore(ksafe))

        val result = syncManager.deleteAccount()
        assertTrue(result is ApiResult.Success)
        assertTrue(deleteCalled)
        assertEquals(0, resultLog.all().size, "Local result log must be wiped")
    }

    @Test
    fun testDeleteFailurePreservesSessionAndLocalData() = runTest {
        val ksafe = KSafe()
        val resultLog = ResultLog(ksafe)
        resultLog.clear()
        val sessionStore = SessionStore(ksafe)
        sessionStore.clear()
        sessionStore.saveSession(userId = "user-1", accessToken = "access-1", refreshToken = "refresh-1")
        resultLog.record(ResultRecord("uz-latn", 1L, won = true, attempts = 2))

        // Server delete fails (e.g. server down).
        val mockEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                ApiRoutes.ACCOUNT -> respondError(HttpStatusCode.InternalServerError)
                else -> error("Unexpected route ${request.url.encodedPath}")
            }
        }

        val httpClient = HttpClient(mockEngine) { install(ContentNegotiation) { json(json) } }
        val authService = KtorAuthService(httpClient, baseUrl = "http://test", sessionStore = sessionStore)
        val syncService = KtorSyncService(httpClient, baseUrl = "http://test", sessionStore = sessionStore, authService = authService)
        val syncManager = manager(resultLog, sessionStore, authService, syncService, PendingUploadStore(ksafe).also { it.clear() }, RoundStore(ksafe))

        val result = syncManager.deleteAccount()

        assertTrue(result is ApiResult.Error, "A failed delete must surface an error")
        assertEquals("access-1", sessionStore.get().accessToken, "Session must be preserved on failure")
        assertEquals(1, resultLog.all().size, "Local stats must be preserved on failure")
    }

    @Test
    fun testExpiredAccessTokenTransparentlyRefreshesAndRetries() = runTest {
        val ksafe = KSafe()
        val resultLog = ResultLog(ksafe).also { it.clear() }
        val sessionStore = SessionStore(ksafe).also { it.clear() }
        sessionStore.saveSession(userId = "user-1", accessToken = "access-old", refreshToken = "refresh-old")

        var refreshCount = 0
        val mockEngine = MockEngine { request ->
            val auth = request.headers[HttpHeaders.Authorization]
            when (request.url.encodedPath) {
                ApiRoutes.SYNC_STATS -> if (auth == "Bearer access-new") {
                    respond(
                        content = json.encodeToString(UserStatsDto(updatedAt = Instant.fromEpochMilliseconds(0))),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                } else {
                    // Bare 401 with an EMPTY body (the exact case that used to break the client).
                    respond(content = "", status = HttpStatusCode.Unauthorized)
                }
                ApiRoutes.AUTH_REFRESH -> {
                    refreshCount++
                    respond(
                        content = json.encodeToString(
                            uz.abumme.harfgame.data.auth.RefreshResponse(
                                uz.abumme.harfgame.data.auth.TokenPairDto("access-new", "refresh-new")
                            )
                        ),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> error("Unexpected route ${request.url.encodedPath}")
            }
        }

        val httpClient = HttpClient(mockEngine) { install(ContentNegotiation) { json(json) } }
        val authService = KtorAuthService(httpClient, baseUrl = "http://test", sessionStore = sessionStore)
        val syncService = KtorSyncService(httpClient, baseUrl = "http://test", sessionStore = sessionStore, authService = authService)

        val result = syncService.getStats("access-old")

        assertTrue(result is ApiResult.Success, "an expired token must transparently refresh and succeed")
        assertEquals(1, refreshCount, "exactly one refresh for the expiry")
        assertEquals("access-new", sessionStore.get().accessToken)
    }

    @Test
    fun testConcurrentUnauthorizedCallsShareASingleRefresh() = runTest {
        val ksafe = KSafe()
        ResultLog(ksafe).clear()
        val sessionStore = SessionStore(ksafe).also { it.clear() }
        sessionStore.saveSession(userId = "user-1", accessToken = "access-old", refreshToken = "refresh-old")

        var refreshCount = 0
        val mockEngine = MockEngine { request ->
            val auth = request.headers[HttpHeaders.Authorization]
            when (request.url.encodedPath) {
                ApiRoutes.SYNC_STATS -> if (auth == "Bearer access-new") {
                    respond(
                        content = json.encodeToString(UserStatsDto(updatedAt = Instant.fromEpochMilliseconds(0))),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                } else {
                    respond(content = "", status = HttpStatusCode.Unauthorized)
                }
                ApiRoutes.AUTH_REFRESH -> {
                    refreshCount++
                    respond(
                        content = json.encodeToString(
                            uz.abumme.harfgame.data.auth.RefreshResponse(
                                uz.abumme.harfgame.data.auth.TokenPairDto("access-new", "refresh-new")
                            )
                        ),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> error("Unexpected route ${request.url.encodedPath}")
            }
        }

        val httpClient = HttpClient(mockEngine) { install(ContentNegotiation) { json(json) } }
        val authService = KtorAuthService(httpClient, baseUrl = "http://test", sessionStore = sessionStore)
        val syncService = KtorSyncService(httpClient, baseUrl = "http://test", sessionStore = sessionStore, authService = authService)

        val (ra, rb) = kotlinx.coroutines.coroutineScope {
            val a = async { syncService.getStats("access-old") }
            val b = async { syncService.uploadStats("access-old", UserStatsDto(updatedAt = Instant.fromEpochMilliseconds(0))) }
            a.await() to b.await()
        }

        assertTrue(ra is ApiResult.Success)
        assertTrue(rb is ApiResult.Success)
        assertEquals(1, refreshCount, "concurrent 401s must not rotate the refresh token more than once")
    }

    @Test
    fun testOfflineResultRetriesUploadWithoutNewRound() = runTest {
        val ksafe = KSafe()
        val resultLog = ResultLog(ksafe)
        resultLog.clear()
        val sessionStore = SessionStore(ksafe)
        sessionStore.clear()
        sessionStore.saveSession(userId = "user-1", accessToken = "access-1", refreshToken = "refresh-1")
        resultLog.record(ResultRecord("uz-latn", 1L, won = true, attempts = 3))

        var uploadAttempts = 0
        var online = false
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath == ApiRoutes.SYNC_STATS) {
                uploadAttempts++
                if (online) {
                    respond(
                        content = """{"status":"updated"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                } else {
                    respondError(HttpStatusCode.ServiceUnavailable)
                }
            } else {
                error("Unexpected route ${request.url.encodedPath}")
            }
        }

        val httpClient = HttpClient(mockEngine) { install(ContentNegotiation) { json(json) } }
        val authService = KtorAuthService(httpClient, baseUrl = "http://test", sessionStore = sessionStore)
        val syncService = KtorSyncService(httpClient, baseUrl = "http://test", sessionStore = sessionStore, authService = authService)
        val pendingStore = PendingUploadStore(ksafe).also { it.clear() }
        val syncManager = manager(resultLog, sessionStore, authService, syncService, pendingStore, RoundStore(ksafe))

        // Round finishes offline: upload fails, marker stays dirty.
        syncManager.pushStats()
        assertTrue(pendingStore.isDirty(), "A failed upload keeps the pending marker")

        // Connectivity restored — retry WITHOUT playing another round.
        online = true
        syncManager.syncPendingUploads()

        assertEquals(2, uploadAttempts, "Should retry the upload once connectivity returns")
        assertFalse(pendingStore.isDirty(), "Pending marker clears after a confirmed upload")
    }

    @Test
    fun testSuggestWordSendsActiveLangAndWordWithToken() = runTest {
        val ksafe = KSafe()
        val resultLog = ResultLog(ksafe); resultLog.clear()
        val sessionStore = SessionStore(ksafe); sessionStore.clear()
        sessionStore.saveSession(userId = "user-1", accessToken = "access-1", refreshToken = "refresh-1")

        // No network calls expected (session already exists, suggestion service is a fake).
        val mockEngine = MockEngine { error("no HTTP expected") }
        val httpClient = HttpClient(mockEngine) { install(ContentNegotiation) { json(json) } }
        val authService = KtorAuthService(httpClient, baseUrl = "http://test", sessionStore = sessionStore)
        val syncService = KtorSyncService(httpClient, baseUrl = "http://test", sessionStore = sessionStore, authService = authService)
        val pendingStore = PendingUploadStore(ksafe).also { it.clear() }
        val suggestions = RecordingSuggestionService()
        val syncManager = manager(resultLog, sessionStore, authService, syncService, pendingStore, RoundStore(ksafe), suggestions)

        val result = syncManager.suggestWord("uz-latn", "salom")

        assertTrue(result is ApiResult.Success)
        assertEquals("access-1", suggestions.lastToken)
        assertEquals(SuggestWordRequest("uz-latn", "salom"), suggestions.lastRequest)
    }

    @Test
    fun testLinkAccountStoresConfirmedDisplayName() = runTest {
        val ksafe = KSafe()
        val resultLog = ResultLog(ksafe); resultLog.clear()
        val sessionStore = SessionStore(ksafe); sessionStore.clear()
        sessionStore.saveSession(userId = "user-1", accessToken = "access-1", refreshToken = "refresh-1")

        var sentBody: String? = null
        val mockEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                ApiRoutes.AUTH_LINK -> {
                    sentBody = request.body.toByteArray().decodeToString()
                    respond(
                        content = """{"userId":"user-1","tokens":{"accessToken":"a2","refreshToken":"r2"},"displayName":"Ada Lovelace"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                ApiRoutes.SYNC_STATS -> respond(
                    content = json.encodeToString(UserStatsDto(updatedAt = Instant.fromEpochMilliseconds(0L), records = emptyList())),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                else -> error("Unexpected route ${request.url.encodedPath}")
            }
        }

        val httpClient = HttpClient(mockEngine) { install(ContentNegotiation) { json(json) } }
        val authService = KtorAuthService(httpClient, baseUrl = "http://test", sessionStore = sessionStore)
        val syncService = KtorSyncService(httpClient, baseUrl = "http://test", sessionStore = sessionStore, authService = authService)
        val pendingStore = PendingUploadStore(ksafe).also { it.clear() }
        val syncManager = manager(resultLog, sessionStore, authService, syncService, pendingStore, RoundStore(ksafe))

        val result = syncManager.linkAccount(OAuthProvider.GOOGLE, "id-token", nonce = "n1", displayName = "Ada Lovelace")

        assertTrue(result is ApiResult.Success)
        assertTrue(sentBody!!.contains("\"displayName\":\"Ada Lovelace\""), "Confirmed name is sent in the link request")
        val session = sessionStore.get()
        assertEquals("Ada Lovelace", session.displayName, "Response name is stored in the session")
        assertTrue(session.isLinked)
    }
}
