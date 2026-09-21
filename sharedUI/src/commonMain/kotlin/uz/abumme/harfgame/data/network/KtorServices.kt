package uz.abumme.harfgame.data.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    private val refreshMutex = Mutex()

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
                    isLinked = true,
                    displayName = body.displayName,
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

    override suspend fun refreshToken(request: RefreshRequest): ApiResult<RefreshResponse> = refreshMutex.withLock {
        val current = sessionStore.get()
        val currentRefresh = current.refreshToken
        val currentAccess = current.accessToken
        if (currentRefresh != null && currentRefresh != request.refreshToken && currentAccess != null) {
            return@withLock ApiResult.Success(
                RefreshResponse(TokenPairDto(currentAccess, currentRefresh))
            )
        }
        try {
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
        // B6: do NOT clear the session here. Return the true outcome so the caller only clears
        // local data on a confirmed server success. Refresh once and retry if the access token
        // has merely expired, so an idle user can still delete.
        return try {
            var response = httpClient.delete("$baseUrl${ApiRoutes.ACCOUNT}") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            if (response.status == HttpStatusCode.Unauthorized) {
                val refreshed = refreshAccessToken()
                if (refreshed != null) {
                    response = httpClient.delete("$baseUrl${ApiRoutes.ACCOUNT}") {
                        header(HttpHeaders.Authorization, "Bearer $refreshed")
                    }
                }
            }
            if (response.status == HttpStatusCode.OK) {
                ApiResult.Success(Unit)
            } else {
                ApiResult.Error("DELETE_FAILED", "Failed to delete account")
            }
        } catch (e: Exception) {
            ApiResult.Error("NETWORK_ERROR", e.message ?: "Network error")
        }
    }

    /** Exchange the stored refresh token for a fresh access token; null if refresh is impossible. */
    private suspend fun refreshAccessToken(): String? {
        val refreshToken = sessionStore.get().refreshToken ?: return null
        val result = refreshToken(RefreshRequest(refreshToken))
        return (result as? ApiResult.Success)?.data?.tokens?.accessToken
    }
}

class KtorSyncService(
    private val httpClient: HttpClient,
    private val baseUrl: String = "http://localhost:8080",
    private val sessionStore: SessionStore,
    private val authService: AuthService,
) : SyncService {

    override suspend fun getStats(token: String): ApiResult<UserStatsDto> {
        return executeWithAuthRetry { currentToken ->
            val response = httpClient.get("$baseUrl${ApiRoutes.SYNC_STATS}") {
                header(HttpHeaders.Authorization, "Bearer $currentToken")
            }
            when {
                response.status == HttpStatusCode.OK -> ApiResult.Success(response.body<UserStatsDto>())
                response.status == HttpStatusCode.Unauthorized -> UNAUTHORIZED
                else -> errorFrom(response, "Failed to fetch stats")
            }
        }
    }

    override suspend fun uploadStats(token: String, stats: UserStatsDto): ApiResult<Unit> {
        return executeWithAuthRetry { currentToken ->
            val response = httpClient.post("$baseUrl${ApiRoutes.SYNC_STATS}") {
                header(HttpHeaders.Authorization, "Bearer $currentToken")
                contentType(ContentType.Application.Json)
                setBody(stats)
            }
            when {
                response.status == HttpStatusCode.OK -> ApiResult.Success(Unit)
                response.status == HttpStatusCode.Unauthorized -> UNAUTHORIZED
                else -> errorFrom(response, "Failed to upload stats")
            }
        }
    }

    private suspend fun <T> executeWithAuthRetry(
        block: suspend (accessToken: String) -> ApiResult<T>
    ): ApiResult<T> {
        val accessToken = sessionStore.get().accessToken
            ?: return ApiResult.Error("unauthorized", "No active session")

        val firstAttempt = try {
            block(accessToken)
        } catch (e: Exception) {
            return ApiResult.Error("NETWORK_ERROR", e.message ?: "Network error")
        }

        // Only 401 triggers a refresh — detected by HTTP status, never by decoding an (empty) body.
        if (!(firstAttempt is ApiResult.Error && firstAttempt.code == "unauthorized")) {
            return firstAttempt
        }

        val newAccessToken = refreshOnce(accessToken)
        if (newAccessToken == null) {
            sessionStore.clear()
            return firstAttempt
        }
        return try {
            block(newAccessToken)
        } catch (e: Exception) {
            ApiResult.Error("NETWORK_ERROR", e.message ?: "Network error")
        }
    }

    /**
     * Refresh the session at most once for a given expired [usedAccessToken]. If another concurrent
     * caller already refreshed while this one waited on the mutex, the newer token is returned
     * without a second rotation.
     */
    private suspend fun refreshOnce(usedAccessToken: String): String? {
        val current = sessionStore.get()
        val currentAccess = current.accessToken
        if (currentAccess != null && currentAccess != usedAccessToken) {
            return currentAccess
        }
        val refreshToken = current.refreshToken ?: return null
        val result = authService.refreshToken(RefreshRequest(refreshToken))
        return (result as? ApiResult.Success)?.data?.tokens?.accessToken
    }

    /** Decode a server error body, falling back to a status-derived code when the body is empty. */
    private suspend fun errorFrom(
        response: io.ktor.client.statement.HttpResponse,
        fallbackMessage: String,
    ): ApiResult.Error = try {
        val error = response.body<ApiErrorResponse>()
        ApiResult.Error(error.error, error.message ?: fallbackMessage)
    } catch (_: Exception) {
        ApiResult.Error("http_${response.status.value}", fallbackMessage)
    }

    private companion object {
        val UNAUTHORIZED = ApiResult.Error("unauthorized", "Unauthorized")
    }
}
