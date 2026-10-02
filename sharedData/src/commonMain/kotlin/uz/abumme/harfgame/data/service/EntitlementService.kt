package uz.abumme.harfgame.data.service

import uz.abumme.harfgame.data.api.ApiResult
import uz.abumme.harfgame.data.entitlement.AccountEntitlementsDto

/**
 * The signed-in account's purchases as the server verified them with the store. Lets a client that cannot
 * buy (web, desktop) show what the player bought on a phone with the same account.
 */
interface EntitlementService {
    /** Purchases of account [userId]; an error if the active session is no longer that account. */
    suspend fun getEntitlements(userId: String): ApiResult<AccountEntitlementsDto>
}
