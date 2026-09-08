package uz.abumme.harfgame.data.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import uz.abumme.harfgame.data.api.ApiErrorResponse
import uz.abumme.harfgame.data.api.ApiResult
import uz.abumme.harfgame.data.api.ApiRoutes
import uz.abumme.harfgame.data.service.SuggestionService
import uz.abumme.harfgame.data.suggestion.SuggestWordRequest
import uz.abumme.harfgame.data.suggestion.SuggestWordResponse

class KtorSuggestionService(
    private val httpClient: HttpClient,
    private val baseUrl: String = "http://localhost:8080",
) : SuggestionService {

    override suspend fun suggest(token: String, request: SuggestWordRequest): ApiResult<SuggestWordResponse> {
        return try {
            val response = httpClient.post("$baseUrl${ApiRoutes.SUGGESTIONS}") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (response.status == HttpStatusCode.Accepted || response.status == HttpStatusCode.OK) {
                ApiResult.Success(response.body<SuggestWordResponse>())
            } else {
                val error = response.body<ApiErrorResponse>()
                ApiResult.Error(error.error, error.message ?: "Failed to submit suggestion")
            }
        } catch (e: Exception) {
            ApiResult.Error("NETWORK_ERROR", e.message ?: "Network error")
        }
    }
}
