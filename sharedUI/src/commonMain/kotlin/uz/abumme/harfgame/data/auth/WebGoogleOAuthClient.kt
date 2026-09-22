package uz.abumme.harfgame.data.auth

class WebGoogleOAuthClient(
    private val clientId: String,
) : OAuthClient {

    override val isGoogleSupported: Boolean get() = clientId.isNotBlank()
    override val isAppleSupported: Boolean get() = false

    override suspend fun signInWithGoogle(): OAuthResult {
        if (!isGoogleSupported) return OAuthResult.NotConfigured
        return OAuthResult.NotConfigured
    }

    override suspend fun signInWithApple(): OAuthResult = OAuthResult.NotConfigured
}
