package uz.abumme.harfgame.data.stats

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import uz.abumme.harfgame.data.api.ApiResult
import uz.abumme.harfgame.data.auth.OAuthProvider
import uz.abumme.harfgame.data.auth.SessionStore
import uz.abumme.harfgame.data.auth.LinkAccountRequest
import uz.abumme.harfgame.data.service.AuthService
import uz.abumme.harfgame.data.service.SyncService
import uz.abumme.harfgame.data.sync.ResultRecordDto
import uz.abumme.harfgame.data.sync.UserStatsDto

fun ResultRecord.toDto() = ResultRecordDto(
    language = language,
    puzzleDay = puzzleDay,
    won = won,
    attempts = attempts,
)

fun ResultRecordDto.toDomain() = ResultRecord(
    language = language,
    puzzleDay = puzzleDay,
    won = won,
    attempts = attempts,
)

class SyncManager(
    private val resultLog: ResultLog,
    private val sessionStore: SessionStore,
    private val authService: AuthService,
    private val syncService: SyncService,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) {

    fun bootstrap() {
        scope.launch {
            val session = sessionStore.get()
            if (session.refreshToken == null) {
                val authResult = authService.createAnonymousAccount()
                if (authResult is ApiResult.Success) {
                    pullStats()
                }
            } else {
                pullStats()
            }
        }
    }

    suspend fun pushStats() {
        val session = sessionStore.get()
        val token = session.accessToken ?: return
        val records = resultLog.all()
        val updatedAt = resultLog.getUpdatedAt()

        val dto = UserStatsDto(
            updatedAt = updatedAt,
            records = records.map { it.toDto() }
        )
        syncService.uploadStats(token, dto)
    }

    suspend fun pullStats(): Boolean {
        val session = sessionStore.get()
        val token = session.accessToken ?: return false
        val result = syncService.getStats(token)
        if (result is ApiResult.Success) {
            val dto = result.data
            resultLog.merge(dto.records.map { it.toDomain() }, dto.updatedAt)
            return true
        }
        return false
    }

    suspend fun linkAccount(provider: OAuthProvider, idToken: String): ApiResult<Unit> {
        val session = sessionStore.get()
        val token = session.accessToken ?: return ApiResult.Error("UNAUTHORIZED", "No active session")
        val linkResult = authService.linkAccount(token, LinkAccountRequest(provider, idToken))
        return when (linkResult) {
            is ApiResult.Success -> {
                pullStats()
                ApiResult.Success(Unit)
            }
            is ApiResult.Error -> ApiResult.Error(linkResult.code, linkResult.message)
        }
    }

    suspend fun deleteAccount(): ApiResult<Unit> {
        val session = sessionStore.get()
        val token = session.accessToken
        if (token != null) {
            authService.deleteAccount(token)
        }
        sessionStore.clear()
        resultLog.clear()
        bootstrap()
        return ApiResult.Success(Unit)
    }
}
