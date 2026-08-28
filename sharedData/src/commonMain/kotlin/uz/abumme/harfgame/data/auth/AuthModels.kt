package uz.abumme.harfgame.data.auth

import kotlinx.serialization.Serializable

@Serializable
enum class OAuthProvider {
    GOOGLE,
    APPLE,
}

@Serializable
data class TokenPairDto(
    val accessToken: String,
    val refreshToken: String,
)

@Serializable
data class AnonymousAuthResponse(
    val userId: String,
    val tokens: TokenPairDto,
)

@Serializable
data class LinkAccountRequest(
    val provider: OAuthProvider,
    val idToken: String,
)

@Serializable
data class LinkAccountResponse(
    val userId: String,
    val tokens: TokenPairDto,
)

@Serializable
data class RefreshRequest(
    val refreshToken: String,
)

@Serializable
data class RefreshResponse(
    val tokens: TokenPairDto,
)
