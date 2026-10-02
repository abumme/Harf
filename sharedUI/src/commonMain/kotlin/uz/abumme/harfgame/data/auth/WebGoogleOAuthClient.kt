package uz.abumme.harfgame.data.auth

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withTimeoutOrNull
import uz.abumme.harfgame.data.auth.GoogleImplicitFlow.RESPONSE_TIMEOUT
import uz.abumme.harfgame.data.auth.GoogleImplicitFlow.authorizationUrl
import uz.abumme.harfgame.data.auth.GoogleImplicitFlow.googleResponse
import uz.abumme.harfgame.data.auth.GoogleImplicitFlow.randomToken

/**
 * The browser half of web sign-in: a popup for Google and the game's callback page, which posts what Google
 * redirected back with. Kept apart so [WebGoogleOAuthClient] is plain, testable Kotlin.
 */
interface GoogleSignInWindow {
    /** The game's callback page; it must be an authorized redirect URI of the OAuth client. */
    val redirectUri: String

    /**
     * Starts listening for the callback page, then opens [url] in a popup. Every response the page posts (the
     * redirect's fragment, without `#`) arrives on the channel — from this sign-in or another tab's — until the
     * caller cancels it. Null when the browser blocked the popup; throws when it can't listen or open at all.
     */
    fun open(url: String): ReceiveChannel<String>?
}

/**
 * Google sign-in in the browser ([GoogleImplicitFlow]) in a popup, so the game page keeps its state. A response
 * carrying another tab's `state` is ignored. Closing the popup without signing in isn't reported at once — the
 * browser can sever the link to the popup during Google's pages, so a "closed" signal can't be trusted; Google's own
 * "cancel" reports [OAuthResult.Cancelled], silence does after [RESPONSE_TIMEOUT].
 */
class WebGoogleOAuthClient(
    private val clientId: String,
    private val window: GoogleSignInWindow? = null,
) : OAuthClient {

    override val isGoogleSupported: Boolean get() = clientId.isNotBlank() && window != null
    override val isAppleSupported: Boolean get() = false

    override suspend fun signInWithGoogle(): OAuthResult {
        val window = window
        if (clientId.isBlank() || window == null) return OAuthResult.NotConfigured
        val state = randomToken()
        val nonce = randomToken()
        // No suspension before the popup opens: browsers allow it only right after the click.
        val responses = try {
            window.open(authorizationUrl(clientId, window.redirectUri, state, nonce))
        } catch (e: Exception) {
            return OAuthResult.Failed(e.message ?: "The sign-in popup could not be opened")
        } ?: return OAuthResult.Failed("The browser blocked the sign-in popup")
        try {
            return withTimeoutOrNull(RESPONSE_TIMEOUT) {
                for (fragment in responses) {
                    return@withTimeoutOrNull googleResponse(fragment, state, nonce) ?: continue
                }
                OAuthResult.Failed("The sign-in channel closed")
            } ?: OAuthResult.Cancelled
        } finally {
            responses.cancel()
        }
    }

    override suspend fun signInWithApple(): OAuthResult = OAuthResult.NotConfigured
}
