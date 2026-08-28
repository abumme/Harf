package uz.abumme.harfgame.backend.auth.oauth

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import uz.abumme.harfgame.data.auth.OAuthProvider

class GoogleOAuthVerifier(
    val audiences: List<String> = emptyList(),
    private val customVerifier: GoogleIdTokenVerifier? = null,
) : OAuthVerifier {

    private val verifier: GoogleIdTokenVerifier by lazy {
        customVerifier ?: GoogleIdTokenVerifier.Builder(NetHttpTransport(), GsonFactory())
            .setAudience(audiences)
            .build()
    }

    override suspend fun verify(idToken: String): OAuthIdentityResult? {
        return try {
            val googleIdToken = verifier.verify(idToken) ?: return null
            val payload = googleIdToken.payload
            val subject = payload.subject ?: return null
            OAuthIdentityResult(
                provider = OAuthProvider.GOOGLE,
                subject = subject,
                email = payload.email
            )
        } catch (_: Exception) {
            null
        }
    }
}
