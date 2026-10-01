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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import uz.abumme.harfgame.data.api.ApiResult
import uz.abumme.harfgame.data.api.ApiRoutes
import uz.abumme.harfgame.data.auth.SessionStore
import uz.abumme.harfgame.data.entitlement.AccountEntitlementsDto
import uz.abumme.harfgame.data.network.KtorAuthService
import uz.abumme.harfgame.data.network.KtorEntitlementService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KtorEntitlementServiceTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun service(sessionStore: SessionStore, engine: MockEngine): KtorEntitlementService {
        val httpClient = HttpClient(engine) { install(ContentNegotiation) { json(json) } }
        val auth = KtorAuthService(httpClient, baseUrl = "http://test", sessionStore = sessionStore)
        return KtorEntitlementService(httpClient, baseUrl = "http://test", sessionStore = sessionStore, authService = auth)
    }

    @Test
    fun reads_the_account_grant_with_its_token() = runTest {
        val sessionStore = SessionStore(KSafe())
        sessionStore.saveSession(userId = "user-1", accessToken = "access-1", refreshToken = "refresh-1")
        val engine = MockEngine { request ->
            assertEquals(ApiRoutes.ENTITLEMENTS, request.url.encodedPath)
            assertEquals("Bearer access-1", request.headers[HttpHeaders.Authorization])
            respond(json.encodeToString(AccountEntitlementsDto(ownedThemes = setOf("theme_dusk"))), HttpStatusCode.OK, jsonHeaders)
        }

        val result = service(sessionStore, engine).getEntitlements("user-1")

        assertTrue(result is ApiResult.Success)
        assertEquals(setOf("theme_dusk"), result.data.ownedThemes)
    }

    @Test
    fun another_accounts_session_is_never_sent() = runTest {
        val sessionStore = SessionStore(KSafe())
        sessionStore.saveSession(userId = "user-2", accessToken = "access-2", refreshToken = "refresh-2")
        var requests = 0
        val engine = MockEngine { requests++; respond("{}", HttpStatusCode.OK, jsonHeaders) }

        val result = service(sessionStore, engine).getEntitlements("user-1")

        assertEquals(0, requests, "user-2's token would answer with user-2's grant")
        assertTrue(result is ApiResult.Error)
        assertEquals("account_changed", result.code)
    }

    @Test
    fun a_failed_refresh_keeps_the_session() = runTest {
        val sessionStore = SessionStore(KSafe())
        sessionStore.saveSession(userId = "user-1", accessToken = "expired", refreshToken = "refresh-1")
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                ApiRoutes.ENTITLEMENTS -> respond("""{"error":"unauthorized"}""", HttpStatusCode.Unauthorized, jsonHeaders)
                // A transient failure of the refresh itself, not a rejected refresh token.
                ApiRoutes.AUTH_REFRESH -> respond("""{"error":"internal_error"}""", HttpStatusCode.InternalServerError, jsonHeaders)
                else -> error("Unexpected route ${request.url.encodedPath}")
            }
        }

        val result = service(sessionStore, engine).getEntitlements("user-1")

        assertTrue(result is ApiResult.Error)
        assertEquals("user-1", sessionStore.get().userId, "a background check must not sign the player out")
        assertEquals("refresh-1", sessionStore.get().refreshToken)
    }
}
