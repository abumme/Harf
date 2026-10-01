package uz.abumme.harfgame.data.auth

import io.ktor.http.URLBuilder
import io.ktor.http.parseQueryString
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
 * Google sign-in through the game's callback page, shared by the browser game ([WebGoogleOAuthClient]) and the
 * desktop app: OpenID Connect implicit flow (`response_type=id_token`) answered at `oauth-callback.html` on the
 * game's site, the redirect URI registered for the Web client. The ID token is bound to a fresh nonce, which the
 * backend checks, and the response to a fresh `state`, so a response meant for another sign-in (or forged) is ignored.
 */
internal object GoogleImplicitFlow {
    const val AUTHORIZATION_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"

    /** The game's callback page, an authorized redirect URI of the Web client (`docs/google-signin-setup.md`). */
    const val CALLBACK_PAGE = "https://harf.lazydevs.uz/oauth-callback.html"

    /**
     * How long a sign-in waits for its answer. Closing Google's window without signing in sends none, so waiting
     * must end eventually; generous, since a second factor or a new account takes a while.
     */
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

    /**
     * The outcome of one callback response (the redirect's parameters, `a=1&b=2`), or null when it belongs to another
     * sign-in (different `state`).
     */
    fun googleResponse(response: String, state: String, nonce: String): OAuthResult? {
        val params = parseQueryString(response)
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

    /** Two random UUIDs (244 random bits, hex) from the platform's secure generator (`crypto.getRandomValues` in the browser). */
    @OptIn(ExperimentalUuidApi::class)
    fun randomToken(): String = Uuid.random().toHexString() + Uuid.random().toHexString()
}
