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
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uz.abumme.harfgame.data.api.ApiResult
import uz.abumme.harfgame.data.api.ApiRoutes
import uz.abumme.harfgame.data.auth.*
import uz.abumme.harfgame.data.network.KtorAuthService
import uz.abumme.harfgame.data.network.KtorSyncService
import uz.abumme.harfgame.data.sync.ResultRecordDto
import uz.abumme.harfgame.data.sync.UserStatsDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClientNetworkAdapterTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testAnonymousBootstrapAndTokenPersistence() = runTest {
        val sessionStore = SessionStore(KSafe())

        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath == ApiRoutes.AUTH_ANONYMOUS) {
                val response = AnonymousAuthResponse(
                    userId = "user-anon-1",
                    tokens = TokenPairDto(
                        accessToken = "access-1",
                        refreshToken = "refresh-1"
                    )
                )
                respond(
                    content = json.encodeToString(response),
                    status = HttpStatusCode.Created,
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

        val result = authService.createAnonymousAccount()
        assertTrue(result is ApiResult.Success)
        assertEquals("user-anon-1", result.data.userId)

        // Verify tokens persisted in SessionStore
        val session = sessionStore.get()
        assertEquals("user-anon-1", session.userId)
        assertEquals("access-1", session.accessToken)
        assertEquals("refresh-1", session.refreshToken)
    }

    @Test
    fun testLinkAccountRefreshesExpiredAccessTokenAndRetries() = runTest {
        val sessionStore = SessionStore(KSafe())
        sessionStore.saveSession(
            userId = "user-anon-1",
            accessToken = "old-access",
            refreshToken = "valid-refresh"
        )

        var linkAttempts = 0
        val mockEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                ApiRoutes.AUTH_LINK -> {
                    linkAttempts++
                    if (request.headers[HttpHeaders.Authorization] == "Bearer new-access") {
                        val resp = LinkAccountResponse(
                            userId = "user-linked-1",
                            tokens = TokenPairDto(accessToken = "linked-access", refreshToken = "linked-refresh"),
                            displayName = "Umid",
                        )
                        respond(
                            content = json.encodeToString(resp),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    } else {
                        respond(
                            content = """{"error":"unauthorized","message":"Missing or invalid access token"}""",
                            status = HttpStatusCode.Unauthorized,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }
                }
                ApiRoutes.AUTH_REFRESH -> {
                    val resp = RefreshResponse(
                        tokens = TokenPairDto(accessToken = "new-access", refreshToken = "new-refresh")
                    )
                    respond(
                        content = json.encodeToString(resp),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> error("Unexpected route ${request.url.encodedPath}")
            }
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }
        val authService = KtorAuthService(httpClient, baseUrl = "http://test", sessionStore = sessionStore)

        val result = authService.linkAccount(
            "old-access",
            LinkAccountRequest(provider = OAuthProvider.APPLE, idToken = "id-token", nonce = "raw-nonce")
        )

        assertTrue(result is ApiResult.Success, "An expired access token must not fail the link")
        assertEquals(2, linkAttempts, "Must have retried the link with the refreshed token")

        val session = sessionStore.get()
        assertEquals("linked-access", session.accessToken)
        assertEquals("linked-refresh", session.refreshToken)
        assertTrue(session.isLinked)
    }

    @Test
    fun testTransparentTokenRefreshOn401() = runTest {
        val sessionStore = SessionStore(KSafe())
        sessionStore.saveSession(
            userId = "user-1",
            accessToken = "old-access",
            refreshToken = "valid-refresh"
        )

        var getStatsAttempts = 0
        val mockEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                ApiRoutes.SYNC_STATS -> {
                    getStatsAttempts++
                    val authHeader = request.headers[HttpHeaders.Authorization]
                    if (authHeader == "Bearer new-access") {
                        val stats = UserStatsDto(
                            updatedAt = Instant.fromEpochMilliseconds(1000L),
                            records = listOf(ResultRecordDto(language = "uz", puzzleDay = 1L, won = true, attempts = 3))
                        )
                        respond(
                            content = json.encodeToString(stats),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    } else {
                        respond(
                            content = """{"error":"unauthorized","message":"expired"}""",
                            status = HttpStatusCode.Unauthorized,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }
                }
                ApiRoutes.AUTH_REFRESH -> {
                    val resp = RefreshResponse(
                        tokens = TokenPairDto(
                            accessToken = "new-access",
                            refreshToken = "new-refresh"
                        )
                    )
                    respond(
                        content = json.encodeToString(resp),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> error("Unexpected route ${request.url.encodedPath}")
            }
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }

        val authService = KtorAuthService(httpClient, baseUrl = "http://test", sessionStore = sessionStore)
        val syncService = KtorSyncService(httpClient, baseUrl = "http://test", sessionStore = sessionStore, authService = authService)

        val result = syncService.getStats("old-access")
        assertTrue(result is ApiResult.Success)
        assertEquals(1, result.data.records.size)
        assertEquals(2, getStatsAttempts, "Must have retried with refreshed token")

        val session = sessionStore.get()
        assertEquals("new-access", session.accessToken)
        assertEquals("new-refresh", session.refreshToken)
    }

    @Test
    fun testFatalRefreshFailureClearsSession() = runTest {
        val sessionStore = SessionStore(KSafe())
        sessionStore.saveSession(
            userId = "user-1",
            accessToken = "expired-access",
            refreshToken = "revoked-refresh"
        )

        val mockEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                ApiRoutes.SYNC_STATS -> {
                    respond(
                        content = """{"error":"unauthorized","message":"expired"}""",
                        status = HttpStatusCode.Unauthorized,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                ApiRoutes.AUTH_REFRESH -> {
                    respond(
                        content = """{"error":"unauthorized","message":"revoked"}""",
                        status = HttpStatusCode.Unauthorized,
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

        val result = syncService.getStats("expired-access")
        assertTrue(result is ApiResult.Error)

        val session = sessionStore.get()
        assertNull(session.accessToken)
        assertNull(session.refreshToken)
    }

    @Test
    fun testConcurrentRefreshTokenCallsDirectlyOnAuthService() = runTest {
        val sessionStore = SessionStore(KSafe())
        sessionStore.saveSession(
            userId = "user-1",
            accessToken = "expired-access",
            refreshToken = "refresh-old"
        )

        var refreshCallCount = 0
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath == ApiRoutes.AUTH_REFRESH) {
                refreshCallCount++
                val resp = RefreshResponse(
                    tokens = TokenPairDto(
                        accessToken = "access-new",
                        refreshToken = "refresh-new"
                    )
                )
                respond(
                    content = json.encodeToString(resp),
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

        val (r1, r2) = kotlinx.coroutines.coroutineScope {
            val a = async { authService.refreshToken(RefreshRequest("refresh-old")) }
            val b = async { authService.refreshToken(RefreshRequest("refresh-old")) }
            a.await() to b.await()
        }

        assertTrue(r1 is ApiResult.Success)
        assertTrue(r2 is ApiResult.Success)
        assertEquals(1, refreshCallCount, "Concurrent refresh calls must only hit network once")
        assertEquals("access-new", sessionStore.get().accessToken)
        assertEquals("refresh-new", sessionStore.get().refreshToken)
    }
}
