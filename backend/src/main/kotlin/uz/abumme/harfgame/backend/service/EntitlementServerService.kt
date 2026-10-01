package uz.abumme.harfgame.backend.service

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import uz.abumme.harfgame.data.entitlement.AccountEntitlementsDto
import java.time.Instant

interface RevenueCatCustomerClient {
    /**
     * The customer's active verified entitlements, or null when RevenueCat could not be asked (no key, network,
     * outage, an unexpected status). Null is "unknown", never "owns nothing": a client mirrors this answer, so an empty
     * one would take away what the player paid for until the next successful check.
     */
    suspend fun getCustomerEntitlements(userId: String): AccountEntitlementsDto?
}

/**
 * The engine is named explicitly: a bare `HttpClient()` resolves one off the runtime classpath and
 * throws when there is none, and this client is built by [EntitlementServerService]'s default
 * argument — so a missing engine crashed the whole server at startup, blank API key or not.
 * ContentNegotiation is what makes [body] work; RevenueCat's payload carries far more fields than
 * this app reads, hence `ignoreUnknownKeys`.
 */
class HttpRevenueCatCustomerClient(
    private val apiKey: String = System.getenv("REVENUECAT_SECRET_KEY") ?: "",
    private val httpClient: HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    },
) : RevenueCatCustomerClient {

    override suspend fun getCustomerEntitlements(userId: String): AccountEntitlementsDto? {
        // Without the key nothing can be verified: unknown, not "owns nothing" (a misconfigured deploy must not
        // wipe the grants clients have cached).
        if (apiKey.isBlank()) return null
        return try {
            val response = httpClient.get("https://api.revenuecat.com/v1/subscribers/${userId.encodeURLPathPart()}") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
            }
            // GET /subscribers is get-or-create: a customer RevenueCat hasn't seen yet comes back 201.
            if (!response.status.isSuccess()) {
                return null
            }
            val body = response.body<RevenueCatSubscriberResponse>()
            activeEntitlements(body.subscriber.entitlements, Instant.now())
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * The grant from RevenueCat's `subscriber.entitlements`, which lists every entitlement the customer ever had.
 * Only the active ones count — what the mobile SDK reports as `entitlements.active`: no `expires_date` (a lifetime
 * purchase), or an expiry (or billing grace period) still ahead of [now]. A date that doesn't parse grants nothing.
 */
internal fun activeEntitlements(entitlements: Map<String, JsonObject>, now: Instant): AccountEntitlementsDto {
    fun JsonObject.date(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull
    fun String.isAfterNow(): Boolean = runCatching { Instant.parse(this) }.getOrNull()?.isAfter(now) == true
    val active = entitlements.filterValues { e ->
        val expires = e.date("expires_date")
        expires == null || expires.isAfterNow() || e.date("grace_period_expires_date")?.isAfterNow() == true
    }.keys
    return AccountEntitlementsDto(
        lifetime = "harf_founder" in active,
        ownedThemes = active.filter { it.startsWith("theme_") }.toSet(),
    )
}

@Serializable
private data class RevenueCatSubscriberResponse(
    val subscriber: RevenueCatSubscriber,
)

@Serializable
private data class RevenueCatSubscriber(
    val entitlements: Map<String, JsonObject> = emptyMap(),
)

class EntitlementServerService(
    private val revenueCatClient: RevenueCatCustomerClient = HttpRevenueCatCustomerClient(),
) {
    suspend fun getEntitlements(userId: String): AccountEntitlementsDto? {
        return revenueCatClient.getCustomerEntitlements(userId)
    }
}
