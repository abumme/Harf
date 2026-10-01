package uz.abumme.harfgame.data.auth

import io.ktor.http.URLBuilder
import io.ktor.http.parseQueryString
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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
 * Google sign-in in the browser: OpenID Connect implicit flow (`response_type=id_token`) in a popup, so the game
 * page keeps its state. The ID token is bound to a fresh nonce, which the backend checks, and the response to a
 * fresh `state`, so a response meant for another tab (or forged) is ignored. Closing the popup without signing in
 * isn't reported at once — the browser can sever the link to the popup during Google's pages, so a "closed" signal
 * can't be trusted; Google's own "cancel" reports [OAuthResult.Cancelled], silence does after [RESPONSE_TIMEOUT].
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
            // A popup closed without signing in never answers; stop listening eventually rather than for as long as
            // the screen lives. Generous: a second factor or a new account takes a while.
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

    internal companion object {
        const val AUTHORIZATION_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
        val RESPONSE_TIMEOUT = 10.minutes

        fun authorizationUrl(clientId: String, redirectUri: String, state: String, nonce: String): String =
            URLBuilder(AUTHORIZATION_ENDPOINT).apply {
                parameters.append("client_id", clientId)
                parameters.append("redirect_uri", redirectUri)
                parameters.append("response_type", "id_token")
                parameters.append("scope", "openid email profile")
                parameters.append("state", state)
                parameters.append("nonce", nonce)
                parameters.append("prompt", "select_account")
            }.buildString()

        /** The outcome of one callback response, or null when it belongs to another sign-in (different `state`). */
        fun googleResponse(fragment: String, state: String, nonce: String): OAuthResult? {
            val params = parseQueryString(fragment)
            if (params["state"] != state) return null
            params["error"]?.let { error ->
                return if (error == "access_denied") OAuthResult.Cancelled else OAuthResult.Failed(error)
            }
            val idToken = params["id_token"] ?: return OAuthResult.Failed("No id_token in Google's response")
            return OAuthResult.Token(idToken = idToken, nonce = nonce, suggestedName = nameClaim(idToken))
        }

        /**
         * The token's `name` claim, only to prefill the link-time name confirmation — the backend verifies the token,
         * this just reads it. Null when absent or unreadable.
         */
        @OptIn(ExperimentalEncodingApi::class)
        fun nameClaim(idToken: String): String? = try {
            val payload = idToken.split('.').getOrNull(1) ?: return null
            val json = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(payload).decodeToString()
            ((Json.parseToJsonElement(json) as? JsonObject)?.get("name") as? JsonPrimitive)
                ?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }

        /** Two random UUIDs (244 random bits) from the platform's secure generator (`crypto.getRandomValues` in the browser). */
        @OptIn(ExperimentalUuidApi::class)
        fun randomToken(): String = Uuid.random().toHexString() + Uuid.random().toHexString()
    }
}
