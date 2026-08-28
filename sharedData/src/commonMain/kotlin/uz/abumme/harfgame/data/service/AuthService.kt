package uz.abumme.harfgame.data.service

import uz.abumme.harfgame.data.api.ApiResult
import uz.abumme.harfgame.data.auth.AnonymousAuthResponse
import uz.abumme.harfgame.data.auth.LinkAccountRequest
import uz.abumme.harfgame.data.auth.LinkAccountResponse
import uz.abumme.harfgame.data.auth.RefreshRequest
import uz.abumme.harfgame.data.auth.RefreshResponse

interface AuthService {
    suspend fun createAnonymousAccount(): ApiResult<AnonymousAuthResponse>
    suspend fun linkAccount(token: String, request: LinkAccountRequest): ApiResult<LinkAccountResponse>
    suspend fun refreshToken(request: RefreshRequest): ApiResult<RefreshResponse>
    suspend fun logout(token: String): ApiResult<Unit>
    suspend fun deleteAccount(token: String): ApiResult<Unit>
}
