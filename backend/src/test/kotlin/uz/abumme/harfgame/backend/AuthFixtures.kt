package uz.abumme.harfgame.backend

import uz.abumme.harfgame.backend.auth.oauth.AppleAuthClient
import uz.abumme.harfgame.backend.auth.oauth.OAuthIdentityResult
import uz.abumme.harfgame.backend.auth.oauth.OAuthVerifier
import uz.abumme.harfgame.data.auth.OAuthProvider

// Fakes for the players' sign-in and deletion paths, shared by the account deletion and player admin tests.

/** Exchanges any code for [refreshToken] and records every token a deletion asked Apple to revoke. */
class RecordingAppleAuth(private val refreshToken: String? = "apple-refresh-1") : AppleAuthClient {
    val exchanged = mutableListOf<String>()
    val revoked = mutableListOf<String>()

    override suspend fun exchangeCode(authorizationCode: String): String? {
        exchanged += authorizationCode
        return refreshToken
    }

    override suspend fun revoke(refreshToken: String) {
        revoked += refreshToken
    }
}

/** Accepts any id token as [subject] of [provider]; with no [subject], the id token itself is the subject. */
class FixedOAuthVerifier(private val provider: OAuthProvider, private val subject: String? = null) : OAuthVerifier {
    override suspend fun verify(idToken: String, expectedNonce: String?) = OAuthIdentityResult(provider, subject ?: idToken)
}
