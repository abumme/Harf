package uz.abumme.harfgame.backend.auth.oauth

import uz.abumme.harfgame.data.auth.OAuthProvider

data class OAuthIdentityResult(
    val provider: OAuthProvider,
    val subject: String,
    val email: String? = null,
)

interface OAuthVerifier {
    suspend fun verify(idToken: String): OAuthIdentityResult?
}
