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
import kotlinx.coroutines.test.runTest
import uz.abumme.harfgame.backend.service.EntitlementServerService
import uz.abumme.harfgame.backend.service.HttpRevenueCatCustomerClient
import uz.abumme.harfgame.backend.service.RevenueCatCustomerClient
import uz.abumme.harfgame.backend.service.activeEntitlements
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import uz.abumme.harfgame.data.api.ApiErrorResponse
import uz.abumme.harfgame.data.api.ApiRoutes
import uz.abumme.harfgame.data.auth.AnonymousAuthResponse
import uz.abumme.harfgame.data.entitlement.AccountEntitlementsDto
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeRevenueCatCustomerClient : RevenueCatCustomerClient {
    val store = mutableMapOf<String, AccountEntitlementsDto>()

    /** True mimics RevenueCat being unreachable. */
    var unreachable = false

    override suspend fun getCustomerEntitlements(userId: String): AccountEntitlementsDto? {
        if (unreachable) return null
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

    @Test
    fun onlyActiveRevenueCatEntitlementsAreGranted() {
        val now = Instant.parse("2026-10-01T12:00:00Z")
        fun entitlement(vararg fields: Pair<String, String?>) =
            JsonObject(fields.associate { (k, v) -> k to (v?.let(::JsonPrimitive) ?: JsonNull) })
        val grant = activeEntitlements(
            mapOf(
                "harf_founder" to entitlement("expires_date" to null),
                "theme_dusk" to entitlement("expires_date" to "2026-12-01T00:00:00Z"),
                // A time-limited promotional grant that has run out: the phone already locks it.
                "theme_press" to entitlement("expires_date" to "2026-09-01T00:00:00Z"),
                "theme_aurora" to entitlement(
                    "expires_date" to "2026-09-30T00:00:00Z",
                    "grace_period_expires_date" to "2026-10-05T00:00:00Z",
                ),
                "theme_broken" to entitlement("expires_date" to "not a date"),
            ),
            now,
        )
        assertTrue(grant.lifetime)
        assertEquals(setOf("theme_dusk", "theme_aurora"), grant.ownedThemes)
    }

    @Test
    fun testUnreachableRevenueCatIsAnErrorNotAnEmptyGrant() = testApplication {
        val fakeRevenueCat = FakeRevenueCatCustomerClient()
        application {
            module(entitlementService = EntitlementServerService(fakeRevenueCat))
        }
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        val anon = client.post(ApiRoutes.AUTH_ANONYMOUS).body<AnonymousAuthResponse>()
        fakeRevenueCat.store[anon.userId] = AccountEntitlementsDto(ownedThemes = setOf("theme_press"))
        fakeRevenueCat.unreachable = true

        // An empty 200 would make the client drop the theme the player paid for.
        val response = client.get(ApiRoutes.ENTITLEMENTS) {
            header(HttpHeaders.Authorization, "Bearer ${anon.tokens.accessToken}")
        }
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("entitlements_unavailable", response.body<ApiErrorResponse>().error)
    }

    // Every other test injects a fake, so the default-argument path — the one module() actually uses —
    // went unexercised: a bare HttpClient() with no engine on the runtime classpath threw at
    // construction and crash-looped the server at startup, whether or not the API key was set.
    @Test
    fun defaultRevenueCatClientIsConstructibleAndNeedsNoKey() = runTest {
        val client = HttpRevenueCatCustomerClient(apiKey = "")
        // No key: nothing verified, so "unknown" (503) rather than an empty grant that would wipe client caches.
        assertNull(client.getCustomerEntitlements("user-1"))
        // The production wiring, defaults and all.
        EntitlementServerService()
    }
}
