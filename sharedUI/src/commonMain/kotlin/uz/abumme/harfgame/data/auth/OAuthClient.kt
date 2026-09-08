package uz.abumme.harfgame.data.auth

/** Typed outcome of a native sign-in attempt — never a bare null, so the UI can react to each case. */
sealed interface OAuthResult {
    /**
     * A verified provider identity token, plus the nonce the request was bound to (if any) and the
     * provider's suggested display name for prefilling the link-time name confirmation (if any).
     */
    data class Token(
        val idToken: String,
        val nonce: String? = null,
        val suggestedName: String? = null,
    ) : OAuthResult

    /** The user dismissed the native flow. */
    data object Cancelled : OAuthResult

    /** No provider client id is configured for this build/platform yet. */
    data object NotConfigured : OAuthResult

    /** The native flow failed. */
    data class Failed(val message: String) : OAuthResult
}

interface OAuthClient {
    val isGoogleSupported: Boolean get() = true
    val isAppleSupported: Boolean get() = false

    suspend fun signInWithGoogle(): OAuthResult
    suspend fun signInWithApple(): OAuthResult
}

/**
 * Placeholder client for builds without provider client ids wired yet. It never crashes and reports
 * [OAuthResult.NotConfigured] so the UI can show an honest "sign-in unavailable" state. Real native
 * flows (Credential Manager on Android, AuthenticationServices/Google Sign-In on iOS) replace this
 * once the public client ids exist.
 */
class NoOpOAuthClient(
    override val isGoogleSupported: Boolean = false,
    override val isAppleSupported: Boolean = false,
) : OAuthClient {
    override suspend fun signInWithGoogle(): OAuthResult = OAuthResult.NotConfigured
    override suspend fun signInWithApple(): OAuthResult = OAuthResult.NotConfigured
}
