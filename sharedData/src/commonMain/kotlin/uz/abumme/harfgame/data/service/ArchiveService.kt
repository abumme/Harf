package uz.abumme.harfgame.data.service

import uz.abumme.harfgame.data.api.ApiResult
import uz.abumme.harfgame.data.archive.ArchiveHistoryDto
import uz.abumme.harfgame.data.archive.ArchiveRunDto

interface ArchiveService {
    suspend fun listRuns(token: String): ApiResult<ArchiveHistoryDto>
    suspend fun uploadRuns(token: String, runs: List<ArchiveRunDto>): ApiResult<Int>
}
