package uz.abumme.harfgame.backend

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.UsersTable
import uz.abumme.harfgame.backend.security.JwtService
import uz.abumme.harfgame.data.api.ApiRoutes
import uz.abumme.harfgame.data.auth.AnonymousAuthResponse
import uz.abumme.harfgame.data.sync.UserStatsDto
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.milliseconds

class AuthIntegrationTest {

    @BeforeTest
    fun setup() {
        val db = DatabaseFactory.init()
        transaction(db) {
            UsersTable.deleteAll()
        }
    }

    @Test
    fun testFirstLaunchCreatesAnonymousAccountAndIsImmediatelyUsable() = testApplication {
        val jwtService = JwtService()
        application {
            module(jwtService = jwtService)
        }

        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // 1. First launch creates anonymous account
        val response = client.post(ApiRoutes.AUTH_ANONYMOUS)
        assertEquals(HttpStatusCode.Created, response.status)

        val authBody = response.body<AnonymousAuthResponse>()
        assertNotNull(authBody.userId)
        assertNotNull(authBody.tokens.accessToken)
        assertNotNull(authBody.tokens.refreshToken)

        // 2. Access token is immediately usable on authenticated endpoint
        val syncResponse = client.get(ApiRoutes.SYNC_STATS) {
            header(HttpHeaders.Authorization, "Bearer ${authBody.tokens.accessToken}")
        }
        assertEquals(HttpStatusCode.OK, syncResponse.status)
        val stats = syncResponse.body<UserStatsDto>()
        assertEquals(0, stats.records.size)
    }

    @Test
    fun testInvalidOrExpiredAccessTokenIsRejected() = testApplication {
        val shortLivedJwtService = JwtService(validityDuration = 1.milliseconds)
        application {
            module(jwtService = shortLivedJwtService)
        }

        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // Create user
        val response = client.post(ApiRoutes.AUTH_ANONYMOUS)
        val authBody = response.body<AnonymousAuthResponse>()

        // Wait for token to expire
        Thread.sleep(10)

        val syncResponse = client.get(ApiRoutes.SYNC_STATS) {
            header(HttpHeaders.Authorization, "Bearer ${authBody.tokens.accessToken}")
        }
        assertEquals(HttpStatusCode.Unauthorized, syncResponse.status)

        // Invalid token
        val invalidTokenResponse = client.get(ApiRoutes.SYNC_STATS) {
            header(HttpHeaders.Authorization, "Bearer invalid-garbage-token")
        }
        assertEquals(HttpStatusCode.Unauthorized, invalidTokenResponse.status)
    }
}
