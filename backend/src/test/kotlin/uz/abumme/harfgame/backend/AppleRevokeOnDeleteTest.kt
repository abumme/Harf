package uz.abumme.harfgame.backend

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
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
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.UsersTable
import uz.abumme.harfgame.backend.security.JwtService
import uz.abumme.harfgame.backend.service.AuthServerService
import uz.abumme.harfgame.data.api.ApiRoutes
import uz.abumme.harfgame.data.auth.AnonymousAuthResponse
import uz.abumme.harfgame.data.auth.LinkAccountRequest
import uz.abumme.harfgame.data.auth.OAuthProvider
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppleRevokeOnDeleteTest {

    @BeforeTest
    fun setup() {
        val db = DatabaseFactory.init()
        transaction(db) { UsersTable.deleteAll() }
    }

    @Test
    fun deletingAnAppleLinkedAccountRevokesAppleTokens() = testApplication {
        val apple = RecordingAppleAuth()
        val authService = AuthServerService(
            JwtService(),
            verifiers = mapOf(OAuthProvider.APPLE to FixedOAuthVerifier(OAuthProvider.APPLE, "apple-sub-1")),
            appleAuth = apple,
        )
        application { module(authService = authService) }

        val client = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

        val anon = client.post(ApiRoutes.AUTH_ANONYMOUS).body<AnonymousAuthResponse>()
        client.post(ApiRoutes.AUTH_LINK) {
            header(HttpHeaders.Authorization, "Bearer ${anon.tokens.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(
                LinkAccountRequest(
                    provider = OAuthProvider.APPLE,
                    idToken = "apple-tok",
                    authorizationCode = "apple-code-1",
                )
            )
        }

        val deleteResp = client.delete(ApiRoutes.ACCOUNT) {
            header(HttpHeaders.Authorization, "Bearer ${anon.tokens.accessToken}")
        }
        assertEquals(HttpStatusCode.OK, deleteResp.status)

        assertEquals(listOf("apple-code-1"), apple.exchanged, "the link exchanges Apple's code for a refresh token")
        assertEquals(listOf("apple-refresh-1"), apple.revoked, "deletion revokes the token the exchange returned")
    }
}
