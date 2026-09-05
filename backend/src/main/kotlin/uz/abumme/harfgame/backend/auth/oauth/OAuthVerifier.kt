package uz.abumme.harfgame.backend.auth.oauth

import uz.abumme.harfgame.data.auth.OAuthProvider

data class OAuthIdentityResult(
    val provider: OAuthProvider,
    val subject: String,
    val email: String? = null,
)

interface OAuthVerifier {
    /**
     * Verify a provider identity token. [expectedNonce] is the raw nonce the client bound to this
     * request; when non-null the token's nonce claim MUST match it (provider-specific hashing
     * applied by the implementation). Returns null on any verification failure.
     */
    suspend fun verify(idToken: String, expectedNonce: String? = null): OAuthIdentityResult?
}
