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
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uz.abumme.harfgame.backend.auth.oauth.OAuthIdentityResult
import uz.abumme.harfgame.backend.auth.oauth.OAuthVerifier
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.UsersTable
import uz.abumme.harfgame.backend.security.JwtService
import uz.abumme.harfgame.backend.service.AuthServerService
import uz.abumme.harfgame.data.api.ApiRoutes
import uz.abumme.harfgame.data.auth.AnonymousAuthResponse
import uz.abumme.harfgame.data.auth.LinkAccountRequest
import uz.abumme.harfgame.data.auth.LinkAccountResponse
import uz.abumme.harfgame.data.auth.OAuthProvider
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TestMockOAuthVerifier(private val provider: OAuthProvider) : OAuthVerifier {
    val tokens = mutableMapOf<String, String>() // idToken -> subject

    override suspend fun verify(idToken: String, expectedNonce: String?): OAuthIdentityResult? {
        val subject = tokens[idToken] ?: return null
        return OAuthIdentityResult(provider, subject, "$subject@test.com")
    }
}

class CrossPlatformAccountAuthTest {

    @BeforeTest
    fun setup() {
        val db = DatabaseFactory.init()
        transaction(db) {
            UsersTable.deleteAll()
        }
    }

    @Test
    fun testGoogleAccountResolvesToSameUserIdAcrossPlatformsAndRecoversOnReturn() = testApplication {
        val googleVerifier = TestMockOAuthVerifier(OAuthProvider.GOOGLE)
        googleVerifier.tokens["google-token-android"] = "google-subject-1"
        googleVerifier.tokens["google-token-ios"] = "google-subject-1"
        googleVerifier.tokens["google-token-desktop"] = "google-subject-1"

        val authService = AuthServerService(
            jwtService = JwtService(),
            verifiers = mapOf(OAuthProvider.GOOGLE to googleVerifier),
        )

        application {
            module(authService = authService)
        }

        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // 1. First platform (Android) creates anonymous session and links Google
        val anonAndroid = client.post(ApiRoutes.AUTH_ANONYMOUS).body<AnonymousAuthResponse>()
        val linkAndroid = client.post(ApiRoutes.AUTH_LINK) {
            header(HttpHeaders.Authorization, "Bearer ${anonAndroid.tokens.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(LinkAccountRequest(OAuthProvider.GOOGLE, "google-token-android"))
        }.body<LinkAccountResponse>()

        val establishedUserId = linkAndroid.userId

        // 2. Second platform (iOS) launches fresh anonymous account, then links SAME Google identity
        val anonIos = client.post(ApiRoutes.AUTH_ANONYMOUS).body<AnonymousAuthResponse>()
        val linkIos = client.post(ApiRoutes.AUTH_LINK) {
            header(HttpHeaders.Authorization, "Bearer ${anonIos.tokens.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(LinkAccountRequest(OAuthProvider.GOOGLE, "google-token-ios"))
        }.body<LinkAccountResponse>()

        assertEquals(establishedUserId, linkIos.userId, "iOS must resolve to the same backend user id")

        // 3. Third platform (Desktop) launches fresh anonymous account, then links SAME Google identity
        val anonDesktop = client.post(ApiRoutes.AUTH_ANONYMOUS).body<AnonymousAuthResponse>()
        val linkDesktop = client.post(ApiRoutes.AUTH_LINK) {
            header(HttpHeaders.Authorization, "Bearer ${anonDesktop.tokens.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(LinkAccountRequest(OAuthProvider.GOOGLE, "google-token-desktop"))
        }.body<LinkAccountResponse>()

        assertEquals(establishedUserId, linkDesktop.userId, "Desktop must resolve to the same backend user id")

        // 4. Returning owner logs out and signs back in -> recovers established account
        client.post(ApiRoutes.AUTH_LOGOUT) {
            header(HttpHeaders.Authorization, "Bearer ${linkDesktop.tokens.accessToken}")
        }

        val returningAnon = client.post(ApiRoutes.AUTH_ANONYMOUS).body<AnonymousAuthResponse>()
        val recovered = client.post(ApiRoutes.AUTH_LINK) {
            header(HttpHeaders.Authorization, "Bearer ${returningAnon.tokens.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(LinkAccountRequest(OAuthProvider.GOOGLE, "google-token-android"))
        }.body<LinkAccountResponse>()

        assertEquals(establishedUserId, recovered.userId, "Returning owner must recover original account")
    }

    @Test
    fun testLinkingSecondProviderAndConflictingAccountsAreNotMerged() = testApplication {
        val googleVerifier = TestMockOAuthVerifier(OAuthProvider.GOOGLE)
        googleVerifier.tokens["google-token-1"] = "google-sub-1"
        googleVerifier.tokens["google-token-2"] = "google-sub-2"

        val appleVerifier = TestMockOAuthVerifier(OAuthProvider.APPLE)
        appleVerifier.tokens["apple-token-1"] = "apple-sub-1"
        appleVerifier.tokens["apple-token-2"] = "apple-sub-2"

        val authService = AuthServerService(
            jwtService = JwtService(),
            verifiers = mapOf(
                OAuthProvider.GOOGLE to googleVerifier,
                OAuthProvider.APPLE to appleVerifier,
            ),
        )

        application {
            module(authService = authService)
        }

        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // Account 1 links Google 1
        val anon1 = client.post(ApiRoutes.AUTH_ANONYMOUS).body<AnonymousAuthResponse>()
        val user1 = client.post(ApiRoutes.AUTH_LINK) {
            header(HttpHeaders.Authorization, "Bearer ${anon1.tokens.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(LinkAccountRequest(OAuthProvider.GOOGLE, "google-token-1"))
        }.body<LinkAccountResponse>()

        // Account 1 links Apple 1 (second provider to same account)
        val user1SecondLink = client.post(ApiRoutes.AUTH_LINK) {
            header(HttpHeaders.Authorization, "Bearer ${user1.tokens.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(LinkAccountRequest(OAuthProvider.APPLE, "apple-token-1"))
        }
        assertEquals(HttpStatusCode.OK, user1SecondLink.status)
        assertEquals(user1.userId, user1SecondLink.body<LinkAccountResponse>().userId)

        // Account 2 links Apple 2
        val anon2 = client.post(ApiRoutes.AUTH_ANONYMOUS).body<AnonymousAuthResponse>()
        val user2 = client.post(ApiRoutes.AUTH_LINK) {
            header(HttpHeaders.Authorization, "Bearer ${anon2.tokens.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(LinkAccountRequest(OAuthProvider.APPLE, "apple-token-2"))
        }.body<LinkAccountResponse>()

        // Account 2 (already established with Apple 2) attempts to link Google 1 (owned by Account 1)
        val conflictResp = client.post(ApiRoutes.AUTH_LINK) {
            header(HttpHeaders.Authorization, "Bearer ${user2.tokens.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(LinkAccountRequest(OAuthProvider.GOOGLE, "google-token-1"))
        }

        // Must reject conflict! Established accounts must NOT be merged or deleted
        assertEquals(HttpStatusCode.BadRequest, conflictResp.status)
    }
}
