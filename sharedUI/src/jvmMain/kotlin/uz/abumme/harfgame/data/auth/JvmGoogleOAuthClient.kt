package uz.abumme.harfgame.data.auth

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.awt.Desktop
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI

/**
 * Google sign-in on desktop ([GoogleImplicitFlow]) in the system browser. Google answers at the game's callback page —
 * the Web client's registered redirect; it accepts no loopback one — and the page forwards the answer to this app's
 * one-shot server on 127.0.0.1, whose port travels in `state` (`desktop-<port>-<random>`, the form the page looks for).
 * Requests carrying another `state` are refused and the wait goes on; closing the browser tab sends nothing, so the
 * wait ends as [OAuthResult.Cancelled] after [GoogleImplicitFlow.RESPONSE_TIMEOUT].
 */
class JvmGoogleOAuthClient(
    private val clientId: String,
    private val callbackPage: String = GoogleImplicitFlow.CALLBACK_PAGE,
    private val browse: (url: String) -> Boolean = ::openInSystemBrowser,
) : OAuthClient {

    override val isGoogleSupported: Boolean get() = clientId.isNotBlank()
    override val isAppleSupported: Boolean get() = false

    override suspend fun signInWithGoogle(): OAuthResult {
        if (!isGoogleSupported) return OAuthResult.NotConfigured
        return withContext(Dispatchers.IO) {
            val server = try {
                // IPv4 literally, as the page forwards: getLoopbackAddress() is ::1 when the JVM prefers IPv6.
                HttpServer.create(InetSocketAddress(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)), 0), 0)
            } catch (e: Exception) {
                return@withContext OAuthResult.Failed(e.message ?: "Could not listen for the sign-in answer")
            }
            val nonce = GoogleImplicitFlow.randomToken()
            val state = "$STATE_PREFIX${server.address.port}-${GoogleImplicitFlow.randomToken()}"
            val results = Channel<OAuthResult>(Channel.UNLIMITED)
            server.createContext(CALLBACK_PATH) { exchange ->
                val result = GoogleImplicitFlow.googleResponse(exchange.requestURI.rawQuery.orEmpty(), state, nonce)
                exchange.reply(if (result == null) 400 else 200, if (result is OAuthResult.Token) SIGNED_IN_PAGE else DONE_PAGE)
                if (result != null) results.trySend(result)
            }
            server.start()
            try {
                if (!browse(GoogleImplicitFlow.authorizationUrl(clientId, callbackPage, state, nonce))) {
                    return@withContext OAuthResult.Failed("No browser to sign in with")
                }
                withTimeoutOrNull(GoogleImplicitFlow.RESPONSE_TIMEOUT) { results.receive() } ?: OAuthResult.Cancelled
            } finally {
                results.close()
                server.stop(0)
            }
        }
    }

    override suspend fun signInWithApple(): OAuthResult = OAuthResult.NotConfigured

    internal companion object {
        /** Shared with `oauth-callback.html`, which forwards answers with this `state` prefix to 127.0.0.1. */
        const val STATE_PREFIX = "desktop-"
        const val CALLBACK_PATH = "/callback"

        private val SIGNED_IN_PAGE = page("Signed in. You can close this tab and return to Harf.")
        private val DONE_PAGE = page("You can close this tab and return to Harf.")

        private fun page(text: String) =
            "<!doctype html><html><head><meta charset=\"UTF-8\"><title>Harf</title></head>" +
                "<body style=\"font-family: system-ui, sans-serif; text-align: center; padding-top: 3em\"><p>$text</p></body></html>"

        private fun HttpExchange.reply(status: Int, html: String) {
            val body = html.encodeToByteArray()
            responseHeaders.add("Content-Type", "text/html; charset=utf-8")
            responseHeaders.add("Referrer-Policy", "no-referrer")
            responseHeaders.add("Cache-Control", "no-store")
            sendResponseHeaders(status, body.size.toLong())
            responseBody.use { it.write(body) }
        }
    }
}

private fun openInSystemBrowser(url: String): Boolean = try {
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
        Desktop.getDesktop().browse(URI(url))
        true
    } else {
        false
    }
} catch (_: Exception) {
    false
}
