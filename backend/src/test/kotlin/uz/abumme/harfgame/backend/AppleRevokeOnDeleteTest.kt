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
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import uz.abumme.harfgame.backend.auth.oauth.AppleTokenRevoker
import uz.abumme.harfgame.backend.auth.oauth.OAuthIdentityResult
import uz.abumme.harfgame.backend.auth.oauth.OAuthVerifier
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

    private class RecordingRevoker : AppleTokenRevoker {
        val revoked = mutableListOf<String>()
        override suspend fun revoke(providerSubject: String) { revoked += providerSubject }
    }

    private class FixedVerifier(private val subject: String) : OAuthVerifier {
        override suspend fun verify(idToken: String, expectedNonce: String?) =
            OAuthIdentityResult(OAuthProvider.APPLE, subject)
    }

    @BeforeTest
    fun setup() {
        val db = DatabaseFactory.init()
        transaction(db) { UsersTable.deleteAll() }
    }

    @Test
    fun deletingAnAppleLinkedAccountRevokesAppleTokens() = testApplication {
        val revoker = RecordingRevoker()
        val authService = AuthServerService(
            JwtService(),
            verifiers = mapOf(OAuthProvider.APPLE to FixedVerifier("apple-sub-1")),
            appleRevoker = revoker,
        )
        application { module(authService = authService) }

        val client = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

        val anon = client.post(ApiRoutes.AUTH_ANONYMOUS).body<AnonymousAuthResponse>()
        client.post(ApiRoutes.AUTH_LINK) {
            header(HttpHeaders.Authorization, "Bearer ${anon.tokens.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(LinkAccountRequest(provider = OAuthProvider.APPLE, idToken = "apple-tok"))
        }

        val deleteResp = client.delete(ApiRoutes.ACCOUNT) {
            header(HttpHeaders.Authorization, "Bearer ${anon.tokens.accessToken}")
        }
        assertEquals(HttpStatusCode.OK, deleteResp.status)

        assertTrue(revoker.revoked.contains("apple-sub-1"), "Apple tokens must be revoked on deletion")
    }
}
