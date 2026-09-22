package uz.abumme.harfgame.backend

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.UsersTable
import uz.abumme.harfgame.backend.service.EntitlementServerService
import uz.abumme.harfgame.backend.service.RevenueCatCustomerClient
import uz.abumme.harfgame.data.api.ApiRoutes
import uz.abumme.harfgame.data.auth.AnonymousAuthResponse
import uz.abumme.harfgame.data.entitlement.AccountEntitlementsDto
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FakeRevenueCatCustomerClient : RevenueCatCustomerClient {
    val store = mutableMapOf<String, AccountEntitlementsDto>()

    override suspend fun getCustomerEntitlements(userId: String): AccountEntitlementsDto {
        return store[userId] ?: AccountEntitlementsDto()
    }
}

class EntitlementBackendTest {

    @BeforeTest
    fun setup() {
        val db = DatabaseFactory.init()
        transaction(db) {
            UsersTable.deleteAll()
        }
    }

    @Test
    fun testUnauthenticatedEntitlementsRequestIsRejected() = testApplication {
        application { module() }

        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val response = client.get(ApiRoutes.ENTITLEMENTS)
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun testForgedClientOwnershipFlagGrantsNothingAndRefundUpdatesAccess() = testApplication {
        val fakeRevenueCat = FakeRevenueCatCustomerClient()
        val entitlementService = EntitlementServerService(fakeRevenueCat)

        application {
            module(entitlementService = entitlementService)
        }

        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val anon = client.post(ApiRoutes.AUTH_ANONYMOUS).body<AnonymousAuthResponse>()

        // 1. Client attempts to forge ownership via query parameter / header
        val forgedResponse = client.get(ApiRoutes.ENTITLEMENTS) {
            header(HttpHeaders.Authorization, "Bearer ${anon.tokens.accessToken}")
            header("X-Client-Lifetime", "true")
            parameter("lifetime", "true")
        }.body<AccountEntitlementsDto>()

        // Server only trusts verified RevenueCat state
        assertFalse(forgedResponse.lifetime, "Forged client flag must not grant entitlement")

        // 2. Verified purchase in RevenueCat
        fakeRevenueCat.store[anon.userId] = AccountEntitlementsDto(lifetime = true, ownedThemes = setOf("theme_press"))
        val verifiedResponse = client.get(ApiRoutes.ENTITLEMENTS) {
            header(HttpHeaders.Authorization, "Bearer ${anon.tokens.accessToken}")
        }.body<AccountEntitlementsDto>()
        assertTrue(verifiedResponse.lifetime, "Verified RevenueCat entitlement must be granted")
        assertEquals(setOf("theme_press"), verifiedResponse.ownedThemes)

        // 3. Refund occurs -> confirmed change updates access immediately
        fakeRevenueCat.store[anon.userId] = AccountEntitlementsDto(lifetime = false, ownedThemes = emptySet())
        val refundedResponse = client.get(ApiRoutes.ENTITLEMENTS) {
            header(HttpHeaders.Authorization, "Bearer ${anon.tokens.accessToken}")
        }.body<AccountEntitlementsDto>()
        assertFalse(refundedResponse.lifetime, "Refunded purchase must revoke entitlement")
        assertTrue(refundedResponse.ownedThemes.isEmpty())
    }
}
