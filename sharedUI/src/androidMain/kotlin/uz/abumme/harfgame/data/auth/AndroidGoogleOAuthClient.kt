package uz.abumme.harfgame.data.auth

import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.SecureRandom

/**
 * Real Google sign-in on Android via Credential Manager. Returns a Google ID token bound to a nonce
 * (the backend verifies both against the configured server/Web client id). Apple is unsupported on
 * Android. A blank [serverClientId] reports NotConfigured rather than launching anything.
 */
class AndroidGoogleOAuthClient(
    private val serverClientId: String,
) : OAuthClient {

    override val isGoogleSupported: Boolean = serverClientId.isNotBlank()
    override val isAppleSupported: Boolean = false

    override suspend fun signInWithGoogle(): OAuthResult {
        if (serverClientId.isBlank()) return OAuthResult.NotConfigured
        val activity = CurrentActivityProvider.current()
            ?: return OAuthResult.Failed("No active screen to present sign-in")

        val nonce = randomNonce()
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(serverClientId)
            .setFilterByAuthorizedAccounts(false) // let the user pick any Google account
            .setAutoSelectEnabled(false)
            .setNonce(nonce)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        return try {
            val result = CredentialManager.create(activity).getCredential(activity, request)
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                OAuthResult.Token(idToken = idToken, nonce = nonce)
            } else {
                OAuthResult.Failed("Unexpected credential type")
            }
        } catch (e: GetCredentialCancellationException) {
            OAuthResult.Cancelled
        } catch (e: NoCredentialException) {
            OAuthResult.Failed("No Google account available on this device")
        } catch (e: GetCredentialException) {
            OAuthResult.Failed(e.message ?: "Google sign-in failed")
        }
    }

    override suspend fun signInWithApple(): OAuthResult = OAuthResult.NotConfigured

    private fun randomNonce(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
