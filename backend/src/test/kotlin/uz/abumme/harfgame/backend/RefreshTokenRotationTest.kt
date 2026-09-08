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
import java.time.Instant
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.RefreshTokensTable
import uz.abumme.harfgame.backend.db.UsersTable
import uz.abumme.harfgame.data.api.ApiRoutes
import uz.abumme.harfgame.data.auth.AnonymousAuthResponse
import uz.abumme.harfgame.data.auth.RefreshRequest
import uz.abumme.harfgame.data.auth.RefreshResponse
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class RefreshTokenRotationTest {

    @BeforeTest
    fun setup() {
        val db = DatabaseFactory.init()
        transaction(db) {
            UsersTable.deleteAll()
        }
    }

    @Test
    fun testRefreshExchangesValidRefreshTokenForNewSession() = testApplication {
        application { module() }

        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // Create initial anonymous user
        val authResp = client.post(ApiRoutes.AUTH_ANONYMOUS).body<AnonymousAuthResponse>()
        val initialRefreshToken = authResp.tokens.refreshToken

        // Refresh token
        val refreshResp = client.post(ApiRoutes.AUTH_REFRESH) {
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(refreshToken = initialRefreshToken))
        }
        assertEquals(HttpStatusCode.OK, refreshResp.status)

        val newTokens = refreshResp.body<RefreshResponse>().tokens
        assertNotNull(newTokens.accessToken)
        assertNotNull(newTokens.refreshToken)
        assertNotEquals(initialRefreshToken, newTokens.refreshToken)

        // New access token works
        val syncResp = client.get(ApiRoutes.SYNC_STATS) {
            header(HttpHeaders.Authorization, "Bearer ${newTokens.accessToken}")
        }
        assertEquals(HttpStatusCode.OK, syncResp.status)
    }

    @Test
    fun testRetryWithinGraceWindowReturnsSameReplacement() = testApplication {
        application { module() }

        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val authResp = client.post(ApiRoutes.AUTH_ANONYMOUS).body<AnonymousAuthResponse>()
        val initialRefreshToken = authResp.tokens.refreshToken

        // First rotation
        val refreshResp1 = client.post(ApiRoutes.AUTH_REFRESH) {
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(refreshToken = initialRefreshToken))
        }
        assertEquals(HttpStatusCode.OK, refreshResp1.status)
        val replacement1 = refreshResp1.body<RefreshResponse>().tokens

        // Retry with the OLD token (simulating lost in transit) within grace window
        val refreshResp2 = client.post(ApiRoutes.AUTH_REFRESH) {
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(refreshToken = initialRefreshToken))
        }
        assertEquals(HttpStatusCode.OK, refreshResp2.status)
        val replacement2 = refreshResp2.body<RefreshResponse>().tokens

        // Must return the same replacement refresh token!
        assertEquals(replacement1.refreshToken, replacement2.refreshToken)
    }

    @Test
    fun testReusedRefreshTokenOutsideGraceWindowRevokesEntireChain() = testApplication {
        application { module() }

        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val authResp = client.post(ApiRoutes.AUTH_ANONYMOUS).body<AnonymousAuthResponse>()
        val initialRefreshToken = authResp.tokens.refreshToken

        // First rotation
        val refreshResp1 = client.post(ApiRoutes.AUTH_REFRESH) {
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(refreshToken = initialRefreshToken))
        }
        val replacement1 = refreshResp1.body<RefreshResponse>().tokens

        // Manually age the rotatedAt timestamp to be 10 minutes in the past (outside 60s window)
        val db = DatabaseFactory.init()
        transaction(db) {
            val past = Instant.ofEpochMilli(System.currentTimeMillis() - 10 * 60 * 1000L)
            RefreshTokensTable.update({ RefreshTokensTable.userId eq authResp.userId }) {
                it[rotatedAt] = past
            }
        }

        // Now present the old token outside the grace window -> theft detection!
        val reuseResp = client.post(ApiRoutes.AUTH_REFRESH) {
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(refreshToken = initialRefreshToken))
        }
        assertEquals(HttpStatusCode.Unauthorized, reuseResp.status)

        // The replacement token should now ALSO be revoked
        val attemptReplacementResp = client.post(ApiRoutes.AUTH_REFRESH) {
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(refreshToken = replacement1.refreshToken))
        }
        assertEquals(HttpStatusCode.Unauthorized, attemptReplacementResp.status)
    }
}
