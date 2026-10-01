package uz.abumme.harfgame.billing

import uz.abumme.harfgame.data.api.ApiResult
import uz.abumme.harfgame.data.entitlement.AccountEntitlementsDto
import uz.abumme.harfgame.data.service.EntitlementService

/** The server's view of an account's purchases; [result] is what the next call answers. */
class FakeEntitlementService(
    var result: ApiResult<AccountEntitlementsDto> = ApiResult.Success(AccountEntitlementsDto()),
) : EntitlementService {
    val askedFor = mutableListOf<String>()
    val calls: Int get() = askedFor.size
    var beforeAnswer: () -> Unit = {}

    override suspend fun getEntitlements(userId: String): ApiResult<AccountEntitlementsDto> {
        askedFor += userId
        beforeAnswer()
        return result
    }
}
