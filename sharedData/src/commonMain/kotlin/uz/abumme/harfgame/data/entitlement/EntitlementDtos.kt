package uz.abumme.harfgame.data.entitlement

import kotlinx.serialization.Serializable

@Serializable
data class AccountEntitlementsDto(
    val lifetime: Boolean = false,
    val ownedThemes: Set<String> = emptySet(),
)
