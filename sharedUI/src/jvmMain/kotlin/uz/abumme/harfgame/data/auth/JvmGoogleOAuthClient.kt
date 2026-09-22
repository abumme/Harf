package uz.abumme.harfgame.data.auth

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.net.InetSocketAddress
import java.net.URI
import java.security.SecureRandom
import java.util.Base64
import kotlin.coroutines.resume

class JvmGoogleOAuthClient(
    private val clientId: String,
) : OAuthClient {

    override val isGoogleSupported: Boolean get() = clientId.isNotBlank()
    override val isAppleSupported: Boolean get() = false

    override suspend fun signInWithGoogle(): OAuthResult {
        if (!isGoogleSupported) return OAuthResult.NotConfigured
        return withContext(Dispatchers.IO) {
            try {
                suspendCancellableCoroutine<OAuthResult> { continuation ->
                    val server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
                    val port = server.address.port
                    val redirectUri = "http://localhost:$port/callback"
                    val stateBytes = ByteArray(16)
                    SecureRandom().nextBytes(stateBytes)
                    val state = Base64.getUrlEncoder().withoutPadding().encodeToString(stateBytes)

                    server.createContext("/callback") { exchange ->
                        val query = exchange.requestURI.query ?: ""
                        val params = query.split('&').associate {
                            val parts = it.split('=')
                            if (parts.size == 2) parts[0] to parts[1] else "" to ""
                        }
                        val receivedState = params["state"]
                        val idToken = params["id_token"] ?: params["token"]
                        val error = params["error"]

                        val responseText = if (idToken != null && receivedState == state) {
                            "<html><body><h2>Sign-in successful!</h2><p>You can close this window and return to Harf.</p></body></html>"
                        } else {
                            "<html><body><h2>Sign-in failed</h2><p>Please return to Harf.</p></body></html>"
                        }
                        exchange.sendResponseHeaders(200, responseText.toByteArray().size.toLong())
                        exchange.responseBody.use { it.write(responseText.toByteArray()) }

                        server.stop(1)

                        if (continuation.isActive) {
                            if (error == "access_denied") {
                                continuation.resume(OAuthResult.Cancelled)
                            } else if (idToken != null && receivedState == state) {
                                continuation.resume(OAuthResult.Token(idToken = idToken))
                            } else {
                                continuation.resume(OAuthResult.Failed(error ?: "Invalid OAuth response"))
                            }
                        }
                    }
                    server.start()

                    continuation.invokeOnCancellation {
                        server.stop(0)
                    }

                    val authUrl = "https://accounts.google.com/o/oauth2/v2/auth?" +
                        "client_id=$clientId&" +
                        "response_type=id_token&" +
                        "scope=openid%20profile%20email&" +
                        "redirect_uri=$redirectUri&" +
                        "state=$state&" +
                        "nonce=$state"

                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                        Desktop.getDesktop().browse(URI(authUrl))
                    } else {
                        server.stop(0)
                        if (continuation.isActive) {
                            continuation.resume(OAuthResult.Failed("Browser not supported"))
                        }
                    }
                }
            } catch (e: Exception) {
                OAuthResult.Failed(e.message ?: "Desktop OAuth failed")
            }
        }
    }

    override suspend fun signInWithApple(): OAuthResult = OAuthResult.NotConfigured
}
