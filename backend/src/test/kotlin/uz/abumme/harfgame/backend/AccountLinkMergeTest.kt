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
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uz.abumme.harfgame.backend.auth.oauth.OAuthIdentityResult
import uz.abumme.harfgame.backend.auth.oauth.OAuthVerifier
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.OAuthIdentitiesTable
import uz.abumme.harfgame.backend.db.UsersTable
import uz.abumme.harfgame.data.api.ApiRoutes
import uz.abumme.harfgame.data.auth.*
import kotlin.test.*

class AccountLinkMergeTest {

    private class MockOAuthVerifier(
        val validTokens: Map<String, OAuthIdentityResult>
    ) : OAuthVerifier {
        override suspend fun verify(idToken: String, expectedNonce: String?): OAuthIdentityResult? {
            return validTokens[idToken]
        }
    }

    @BeforeTest
    fun setup() {
        val db = DatabaseFactory.init()
        transaction(db) {
            UsersTable.deleteAll()
        }
    }

    @Test
    fun testSuccessfulLinkToNewProviderIdentity() = testApplication {
        val mockVerifier = MockOAuthVerifier(
            mapOf("valid-google-id-token" to OAuthIdentityResult(OAuthProvider.GOOGLE, "google-sub-123"))
        )
        application {
            module(verifiers = mapOf(OAuthProvider.GOOGLE to mockVerifier))
        }

        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // 1. Create anonymous account
        val anon = client.post(ApiRoutes.AUTH_ANONYMOUS).body<AnonymousAuthResponse>()

        // 2. Link account
        val linkResp = client.post(ApiRoutes.AUTH_LINK) {
            header(HttpHeaders.Authorization, "Bearer ${anon.tokens.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(LinkAccountRequest(provider = OAuthProvider.GOOGLE, idToken = "valid-google-id-token"))
        }
        assertEquals(HttpStatusCode.OK, linkResp.status)
        val linkBody = linkResp.body<LinkAccountResponse>()
        assertEquals(anon.userId, linkBody.userId)

        // Verify DB row exists in oauth_identities
        val db = DatabaseFactory.init()
        transaction(db) {
            val rows = OAuthIdentitiesTable.selectAll().where { OAuthIdentitiesTable.userId eq anon.userId }.toList()
            assertEquals(1, rows.size)
            assertEquals("GOOGLE", rows[0][OAuthIdentitiesTable.provider])
            assertEquals("google-sub-123", rows[0][OAuthIdentitiesTable.providerSubject])
        }
    }

    @Test
    fun testLinkToIdentityAlreadyOwnedByAnotherAccountMergesAndDeleteOrphan() = testApplication {
        val mockVerifier = MockOAuthVerifier(
            mapOf("google-token-existing-user" to OAuthIdentityResult(OAuthProvider.GOOGLE, "google-sub-claimed"))
        )
        application {
            module(verifiers = mapOf(OAuthProvider.GOOGLE to mockVerifier))
        }

        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // 1. Device A creates anonymous account and links Google identity (becomes permanent account A)
        val accountA = client.post(ApiRoutes.AUTH_ANONYMOUS).body<AnonymousAuthResponse>()
        client.post(ApiRoutes.AUTH_LINK) {
            header(HttpHeaders.Authorization, "Bearer ${accountA.tokens.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(LinkAccountRequest(provider = OAuthProvider.GOOGLE, idToken = "google-token-existing-user"))
        }

        // 2. Device B creates another anonymous account B
        val accountB = client.post(ApiRoutes.AUTH_ANONYMOUS).body<AnonymousAuthResponse>()

        // 3. Device B links the SAME Google identity
        val linkRespB = client.post(ApiRoutes.AUTH_LINK) {
            header(HttpHeaders.Authorization, "Bearer ${accountB.tokens.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(LinkAccountRequest(provider = OAuthProvider.GOOGLE, idToken = "google-token-existing-user"))
        }
        assertEquals(HttpStatusCode.OK, linkRespB.status)
        val linkBodyB = linkRespB.body<LinkAccountResponse>()

        // The session on Device B is now switched to Account A!
        assertEquals(accountA.userId, linkBodyB.userId)

        // Orphaned anonymous account B is deleted from DB
        val db = DatabaseFactory.init()
        transaction(db) {
            val userBRows = UsersTable.selectAll().where { UsersTable.id eq accountB.userId }.toList()
            assertEquals(0, userBRows.size, "Orphaned anonymous account B should be deleted")
            val userARows = UsersTable.selectAll().where { UsersTable.id eq accountA.userId }.toList()
            assertEquals(1, userARows.size, "Existing account A must remain")
        }
    }

    @Test
    fun testLinkingIdentityOwnedByAnotherAccountDoesNotDeleteAnAlreadyLinkedCaller() = testApplication {
        val mockVerifier = MockOAuthVerifier(
            mapOf(
                "token-owner-A" to OAuthIdentityResult(OAuthProvider.GOOGLE, "sub-A"),
                "token-owner-B" to OAuthIdentityResult(OAuthProvider.GOOGLE, "sub-B"),
            )
        )
        application {
            module(verifiers = mapOf(OAuthProvider.GOOGLE to mockVerifier))
        }

        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // Account A links its own identity sub-A.
        val accountA = client.post(ApiRoutes.AUTH_ANONYMOUS).body<AnonymousAuthResponse>()
        client.post(ApiRoutes.AUTH_LINK) {
            header(HttpHeaders.Authorization, "Bearer ${accountA.tokens.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(LinkAccountRequest(provider = OAuthProvider.GOOGLE, idToken = "token-owner-A"))
        }

        // Account B links its own identity sub-B — B is now an already-linked account.
        val accountB = client.post(ApiRoutes.AUTH_ANONYMOUS).body<AnonymousAuthResponse>()
        val linkOwnB = client.post(ApiRoutes.AUTH_LINK) {
            header(HttpHeaders.Authorization, "Bearer ${accountB.tokens.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(LinkAccountRequest(provider = OAuthProvider.GOOGLE, idToken = "token-owner-B"))
        }
        assertEquals(HttpStatusCode.OK, linkOwnB.status)

        // B (already linked) now submits A's identity: must be rejected, and B must NOT be deleted.
        val conflict = client.post(ApiRoutes.AUTH_LINK) {
            header(HttpHeaders.Authorization, "Bearer ${accountB.tokens.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(LinkAccountRequest(provider = OAuthProvider.GOOGLE, idToken = "token-owner-A"))
        }
        assertEquals(HttpStatusCode.BadRequest, conflict.status)

        val db = DatabaseFactory.init()
        transaction(db) {
            val userB = UsersTable.selectAll().where { UsersTable.id eq accountB.userId }.count()
            assertEquals(1, userB, "Already-linked account B must not be deleted by a conflicting link")
            val bIdentity = OAuthIdentitiesTable.selectAll()
                .where { OAuthIdentitiesTable.userId eq accountB.userId }.count()
            assertEquals(1, bIdentity, "Account B keeps its own linked identity")
        }
    }

    @Test
    fun testConfirmedDisplayNameIsPersistedAndReturnedOnLink() = testApplication {
        val mockVerifier = MockOAuthVerifier(
            mapOf("valid-google-id-token" to OAuthIdentityResult(OAuthProvider.GOOGLE, "google-sub-name"))
        )
        application {
            module(verifiers = mapOf(OAuthProvider.GOOGLE to mockVerifier))
        }
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val anon = client.post(ApiRoutes.AUTH_ANONYMOUS).body<AnonymousAuthResponse>()
        val linkResp = client.post(ApiRoutes.AUTH_LINK) {
            header(HttpHeaders.Authorization, "Bearer ${anon.tokens.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(
                LinkAccountRequest(
                    provider = OAuthProvider.GOOGLE,
                    idToken = "valid-google-id-token",
                    displayName = "Ada Lovelace",
                )
            )
        }
        assertEquals(HttpStatusCode.OK, linkResp.status)
        assertEquals("Ada Lovelace", linkResp.body<LinkAccountResponse>().displayName)

        val db = DatabaseFactory.init()
        transaction(db) {
            val row = UsersTable.selectAll().where { UsersTable.id eq anon.userId }.single()
            assertEquals("Ada Lovelace", row[UsersTable.name])
        }
    }

    @Test
    fun testConfirmedDisplayNameIsAppliedToAdoptedAccountOnMerge() = testApplication {
        val mockVerifier = MockOAuthVerifier(
            mapOf("google-token-existing-user" to OAuthIdentityResult(OAuthProvider.GOOGLE, "google-sub-merge"))
        )
        application {
            module(verifiers = mapOf(OAuthProvider.GOOGLE to mockVerifier))
        }
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        // Account A links the identity (no name yet).
        val accountA = client.post(ApiRoutes.AUTH_ANONYMOUS).body<AnonymousAuthResponse>()
        client.post(ApiRoutes.AUTH_LINK) {
            header(HttpHeaders.Authorization, "Bearer ${accountA.tokens.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(LinkAccountRequest(provider = OAuthProvider.GOOGLE, idToken = "google-token-existing-user"))
        }

        // Account B links the SAME identity with a confirmed name -> session adopts A, name applied to A.
        val accountB = client.post(ApiRoutes.AUTH_ANONYMOUS).body<AnonymousAuthResponse>()
        val linkRespB = client.post(ApiRoutes.AUTH_LINK) {
            header(HttpHeaders.Authorization, "Bearer ${accountB.tokens.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(
                LinkAccountRequest(
                    provider = OAuthProvider.GOOGLE,
                    idToken = "google-token-existing-user",
                    displayName = "Grace Hopper",
                )
            )
        }
        assertEquals(HttpStatusCode.OK, linkRespB.status)
        val linkBodyB = linkRespB.body<LinkAccountResponse>()
        assertEquals(accountA.userId, linkBodyB.userId)
        assertEquals("Grace Hopper", linkBodyB.displayName)

        val db = DatabaseFactory.init()
        transaction(db) {
            val row = UsersTable.selectAll().where { UsersTable.id eq accountA.userId }.single()
            assertEquals("Grace Hopper", row[UsersTable.name])
        }
    }

    @Test
    fun testInvalidProviderTokenIsRejectedAndDoesNotModifyAccount() = testApplication {
        val mockVerifier = MockOAuthVerifier(emptyMap())
        application {
            module(verifiers = mapOf(OAuthProvider.GOOGLE to mockVerifier))
        }

        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val anon = client.post(ApiRoutes.AUTH_ANONYMOUS).body<AnonymousAuthResponse>()

        val linkResp = client.post(ApiRoutes.AUTH_LINK) {
            header(HttpHeaders.Authorization, "Bearer ${anon.tokens.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(LinkAccountRequest(provider = OAuthProvider.GOOGLE, idToken = "bogus-token"))
        }
        assertEquals(HttpStatusCode.BadRequest, linkResp.status)

        val db = DatabaseFactory.init()
        transaction(db) {
            val count = OAuthIdentitiesTable.selectAll().count()
            assertEquals(0, count)
        }
    }
}
