package uz.abumme.harfgame.backend

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
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
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import uz.abumme.harfgame.backend.db.*
import uz.abumme.harfgame.data.api.ApiRoutes
import uz.abumme.harfgame.data.auth.AnonymousAuthResponse
import uz.abumme.harfgame.data.auth.RefreshRequest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LogoutAndAccountDeletionTest {

    @BeforeTest
    fun setup() {
        val db = DatabaseFactory.init()
        transaction(db) {
            UsersTable.deleteAll()
        }
    }

    @Test
    fun testLogoutRevokesAllRefreshTokens() = testApplication {
        application { module() }

        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val anon = client.post(ApiRoutes.AUTH_ANONYMOUS).body<AnonymousAuthResponse>()

        // Call logout endpoint
        val logoutResp = client.post(ApiRoutes.AUTH_LOGOUT) {
            header(HttpHeaders.Authorization, "Bearer ${anon.tokens.accessToken}")
        }
        assertEquals(HttpStatusCode.OK, logoutResp.status)

        // Attempting to refresh with the revoked token must fail
        val refreshResp = client.post(ApiRoutes.AUTH_REFRESH) {
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(refreshToken = anon.tokens.refreshToken))
        }
        assertEquals(HttpStatusCode.Unauthorized, refreshResp.status)
    }

    @Test
    fun testDeletingAccountRemovesAllServerSideData() = testApplication {
        application { module() }

        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val anon = client.post(ApiRoutes.AUTH_ANONYMOUS).body<AnonymousAuthResponse>()

        // Delete account
        val deleteResp = client.delete(ApiRoutes.ACCOUNT) {
            header(HttpHeaders.Authorization, "Bearer ${anon.tokens.accessToken}")
        }
        assertEquals(HttpStatusCode.OK, deleteResp.status)

        // Verify database rows are completely removed
        val db = DatabaseFactory.init()
        transaction(db) {
            val users = UsersTable.selectAll().where { UsersTable.id eq anon.userId }.count()
            assertEquals(0, users)

            val refreshTokens = RefreshTokensTable.selectAll().where { RefreshTokensTable.userId eq anon.userId }.count()
            assertEquals(0, refreshTokens)

            val stats = UserStatsTable.selectAll().where { UserStatsTable.userId eq anon.userId }.count()
            assertEquals(0, stats)
        }
    }
}
