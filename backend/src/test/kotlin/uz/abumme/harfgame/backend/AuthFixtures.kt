package uz.abumme.harfgame.backend

import uz.abumme.harfgame.backend.auth.oauth.AppleTokenRevoker
import uz.abumme.harfgame.backend.auth.oauth.OAuthIdentityResult
import uz.abumme.harfgame.backend.auth.oauth.OAuthVerifier
import uz.abumme.harfgame.data.auth.OAuthProvider

// Fakes for the players' sign-in and deletion paths, shared by the account deletion and player admin tests.

/** Records the Apple subject of every revocation a deletion asked for. */
class RecordingAppleRevoker : AppleTokenRevoker {
    val revoked = mutableListOf<String>()

    override suspend fun revoke(providerSubject: String) {
        revoked += providerSubject
    }
}

/** Accepts any id token as [subject] of [provider]; with no [subject], the id token itself is the subject. */
class FixedOAuthVerifier(private val provider: OAuthProvider, private val subject: String? = null) : OAuthVerifier {
    override suspend fun verify(idToken: String, expectedNonce: String?) = OAuthIdentityResult(provider, subject ?: idToken)
}
