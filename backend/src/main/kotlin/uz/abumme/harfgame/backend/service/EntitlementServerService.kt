package uz.abumme.harfgame.backend.service

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import uz.abumme.harfgame.data.entitlement.AccountEntitlementsDto

interface RevenueCatCustomerClient {
    suspend fun getCustomerEntitlements(userId: String): AccountEntitlementsDto
}

class HttpRevenueCatCustomerClient(
    private val apiKey: String = System.getenv("REVENUECAT_SECRET_KEY") ?: "",
    private val httpClient: HttpClient = HttpClient(),
) : RevenueCatCustomerClient {

    override suspend fun getCustomerEntitlements(userId: String): AccountEntitlementsDto {
        if (apiKey.isBlank()) return AccountEntitlementsDto()
        return try {
            val response = httpClient.get("https://api.revenuecat.com/v1/subscribers/$userId") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
            }
            if (response.status != HttpStatusCode.OK) {
                return AccountEntitlementsDto()
            }
            val body = response.body<RevenueCatSubscriberResponse>()
            val entitlements = body.subscriber.entitlements
            val lifetime = entitlements.containsKey("harf_founder")
            val ownedThemes = entitlements.keys.filter { it.startsWith("theme_") }.toSet()
            AccountEntitlementsDto(lifetime = lifetime, ownedThemes = ownedThemes)
        } catch (_: Exception) {
            AccountEntitlementsDto()
        }
    }
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
    suspend fun getEntitlements(userId: String): AccountEntitlementsDto {
        return revenueCatClient.getCustomerEntitlements(userId)
    }
}
