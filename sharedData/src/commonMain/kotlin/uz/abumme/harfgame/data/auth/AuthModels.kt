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
    /** Raw nonce the client bound to the native sign-in request; verified against the token's nonce claim. */
    val nonce: String? = null,
    /** User-confirmed display name for the account (from the link-time confirmation step). */
    val displayName: String? = null,
)

@Serializable
data class LinkAccountResponse(
    val userId: String,
    val tokens: TokenPairDto,
    /** The account's stored display name after linking, echoed back for the client to display. */
    val displayName: String? = null,
)

@Serializable
data class RefreshRequest(
    val refreshToken: String,
)

@Serializable
data class RefreshResponse(
    val tokens: TokenPairDto,
)
