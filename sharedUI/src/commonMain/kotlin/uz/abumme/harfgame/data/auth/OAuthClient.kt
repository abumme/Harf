package uz.abumme.harfgame.data.auth

interface OAuthClient {
    val isGoogleSupported: Boolean get() = true
    val isAppleSupported: Boolean get() = false

    suspend fun signInWithGoogle(): String?
    suspend fun signInWithApple(): String?
}

class NoOpOAuthClient(
    override val isGoogleSupported: Boolean = false,
    override val isAppleSupported: Boolean = false,
) : OAuthClient {
    override suspend fun signInWithGoogle(): String? = null
    override suspend fun signInWithApple(): String? = null
}
