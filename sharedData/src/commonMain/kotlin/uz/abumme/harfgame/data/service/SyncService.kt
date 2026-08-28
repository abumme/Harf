package uz.abumme.harfgame.data.service

import uz.abumme.harfgame.data.api.ApiResult
import uz.abumme.harfgame.data.sync.UserStatsDto

interface SyncService {
    suspend fun getStats(token: String): ApiResult<UserStatsDto>
    suspend fun uploadStats(token: String, stats: UserStatsDto): ApiResult<Unit>
}
