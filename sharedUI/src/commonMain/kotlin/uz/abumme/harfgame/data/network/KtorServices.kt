package uz.abumme.harfgame.data.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import uz.abumme.harfgame.data.api.ApiErrorResponse
import uz.abumme.harfgame.data.api.ApiResult
import uz.abumme.harfgame.data.api.ApiRoutes
import uz.abumme.harfgame.data.auth.*
import uz.abumme.harfgame.data.service.AuthService
import uz.abumme.harfgame.data.service.SyncService
import uz.abumme.harfgame.data.sync.UserStatsDto

class KtorAuthService(
    private val httpClient: HttpClient,
    private val baseUrl: String = "http://localhost:8080",
    private val sessionStore: SessionStore,
) : AuthService {

    override suspend fun createAnonymousAccount(): ApiResult<AnonymousAuthResponse> {
        return try {
            val response = httpClient.post("$baseUrl${ApiRoutes.AUTH_ANONYMOUS}")
            if (response.status == HttpStatusCode.Created || response.status == HttpStatusCode.OK) {
                val body = response.body<AnonymousAuthResponse>()
                sessionStore.saveSession(
                    userId = body.userId,
                    accessToken = body.tokens.accessToken,
                    refreshToken = body.tokens.refreshToken,
                    isLinked = false
                )
                ApiResult.Success(body)
            } else {
                val error = response.body<ApiErrorResponse>()
                ApiResult.Error(error.error, error.message ?: "Failed to create anonymous account")
            }
        } catch (e: Exception) {
            ApiResult.Error("NETWORK_ERROR", e.message ?: "Network error")
        }
    }

    override suspend fun linkAccount(token: String, request: LinkAccountRequest): ApiResult<LinkAccountResponse> {
        return try {
            val response = httpClient.post("$baseUrl${ApiRoutes.AUTH_LINK}") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (response.status == HttpStatusCode.OK) {
                val body = response.body<LinkAccountResponse>()
                sessionStore.saveSession(
                    userId = body.userId,
                    accessToken = body.tokens.accessToken,
                    refreshToken = body.tokens.refreshToken,
                    isLinked = true
                )
                ApiResult.Success(body)
            } else {
                val error = response.body<ApiErrorResponse>()
                ApiResult.Error(error.error, error.message ?: "Failed to link account")
            }
        } catch (e: Exception) {
            ApiResult.Error("NETWORK_ERROR", e.message ?: "Network error")
        }
    }

    override suspend fun refreshToken(request: RefreshRequest): ApiResult<RefreshResponse> {
        return try {
            val response = httpClient.post("$baseUrl${ApiRoutes.AUTH_REFRESH}") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (response.status == HttpStatusCode.OK) {
                val body = response.body<RefreshResponse>()
                sessionStore.updateTokens(
                    accessToken = body.tokens.accessToken,
                    refreshToken = body.tokens.refreshToken
                )
                ApiResult.Success(body)
            } else {
                val error = response.body<ApiErrorResponse>()
                if (response.status == HttpStatusCode.Unauthorized) {
                    sessionStore.clear()
                }
                ApiResult.Error(error.error, error.message ?: "Failed to refresh token")
            }
        } catch (e: Exception) {
            ApiResult.Error("NETWORK_ERROR", e.message ?: "Network error")
        }
    }

    override suspend fun logout(token: String): ApiResult<Unit> {
        return try {
            val response = httpClient.post("$baseUrl${ApiRoutes.AUTH_LOGOUT}") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            sessionStore.clear()
            if (response.status == HttpStatusCode.OK) {
                ApiResult.Success(Unit)
            } else {
                ApiResult.Error("LOGOUT_FAILED", "Failed to logout")
            }
        } catch (e: Exception) {
            sessionStore.clear()
            ApiResult.Error("NETWORK_ERROR", e.message ?: "Network error")
        }
    }

    override suspend fun deleteAccount(token: String): ApiResult<Unit> {
        return try {
            val response = httpClient.delete("$baseUrl${ApiRoutes.ACCOUNT}") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            sessionStore.clear()
            if (response.status == HttpStatusCode.OK) {
                ApiResult.Success(Unit)
            } else {
                ApiResult.Error("DELETE_FAILED", "Failed to delete account")
            }
        } catch (e: Exception) {
            sessionStore.clear()
            ApiResult.Error("NETWORK_ERROR", e.message ?: "Network error")
        }
    }
}

class KtorSyncService(
    private val httpClient: HttpClient,
    private val baseUrl: String = "http://localhost:8080",
    private val sessionStore: SessionStore,
    private val authService: AuthService,
) : SyncService {

    override suspend fun getStats(token: String): ApiResult<UserStatsDto> {
        return executeWithAuthRetry(token) { currentToken ->
            val response = httpClient.get("$baseUrl${ApiRoutes.SYNC_STATS}") {
                header(HttpHeaders.Authorization, "Bearer $currentToken")
            }
            if (response.status == HttpStatusCode.OK) {
                val body = response.body<UserStatsDto>()
                ApiResult.Success(body)
            } else {
                val error = response.body<ApiErrorResponse>()
                ApiResult.Error(error.error, error.message ?: "Failed to fetch stats")
            }
        }
    }

    override suspend fun uploadStats(token: String, stats: UserStatsDto): ApiResult<Unit> {
        return executeWithAuthRetry(token) { currentToken ->
            val response = httpClient.post("$baseUrl${ApiRoutes.SYNC_STATS}") {
                header(HttpHeaders.Authorization, "Bearer $currentToken")
                contentType(ContentType.Application.Json)
                setBody(stats)
            }
            if (response.status == HttpStatusCode.OK) {
                ApiResult.Success(Unit)
            } else {
                val error = response.body<ApiErrorResponse>()
                ApiResult.Error(error.error, error.message ?: "Failed to upload stats")
            }
        }
    }

    private suspend fun <T> executeWithAuthRetry(
        token: String,
        block: suspend (currentToken: String) -> ApiResult<T>
    ): ApiResult<T> {
        val firstAttempt = try {
            block(token)
        } catch (e: Exception) {
            return ApiResult.Error("NETWORK_ERROR", e.message ?: "Network error")
        }

        if (firstAttempt is ApiResult.Error && firstAttempt.code == "unauthorized") {
            val refreshToken = sessionStore.get().refreshToken
            if (refreshToken != null) {
                val refreshResult = authService.refreshToken(RefreshRequest(refreshToken))
                if (refreshResult is ApiResult.Success) {
                    val newAccessToken = refreshResult.data.tokens.accessToken
                    return try {
                        block(newAccessToken)
                    } catch (e: Exception) {
                        ApiResult.Error("NETWORK_ERROR", e.message ?: "Network error")
                    }
                }
            }
            sessionStore.clear()
        }
        return firstAttempt
    }
}
