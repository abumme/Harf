package uz.abumme.harfgame

import io.ktor.http.Url
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import uz.abumme.harfgame.data.auth.JvmGoogleOAuthClient
import uz.abumme.harfgame.data.auth.OAuthResult
import java.net.ConnectException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Drives the desktop sign-in against its real loopback server; the "browser" is a lambda that hands over the URL, and
 * the callback page's forward to 127.0.0.1 is a plain HTTP request built from it.
 */
class JvmGoogleOAuthClientTest {

    private val http = HttpClient.newHttpClient()

    private fun idToken(payload: String) =
        "eyJhbGciOiJSUzI1NiJ9." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray()) + ".sig"

    /** What oauth-callback.html does for a desktop state: forward Google's response to the port in the state. */
    private fun forward(state: String, response: String): HttpResponse<String> {
        val port = Regex("^desktop-(\\d{1,5})-[0-9a-f]+$").find(state)!!.groupValues[1]
        val request = HttpRequest.newBuilder(URI("http://127.0.0.1:$port/callback?$response")).GET().build()
        return http.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun startSignIn(browseResult: Boolean = true): Pair<CompletableDeferred<Url>, Deferred<OAuthResult>> {
        val opened = CompletableDeferred<Url>()
        val client = JvmGoogleOAuthClient("client-1", browse = { url -> opened.complete(Url(url)); browseResult })
        val result = CoroutineScope(Dispatchers.IO).async { client.signInWithGoogle() }
        return opened to result
    }

    @Test
    fun google_answers_through_the_callback_page_to_the_loopback_server() = runBlocking<Unit> {
        val (opened, result) = startSignIn()
        val url = withTimeout(5_000) { opened.await() }
        assertEquals("https://harf.lazydevs.uz/oauth-callback.html", url.parameters["redirect_uri"], "the registered page, never a loopback")
        assertEquals("id_token", url.parameters["response_type"])
        val state = url.parameters["state"]!!
        val nonce = url.parameters["nonce"]!!

        val reply = forward(state, "id_token=${idToken("""{"sub":"1","name":"Desk Top"}""")}&state=$state")
        assertEquals(200, reply.statusCode())

        val token = withTimeout(5_000) { result.await() }
        assertTrue(token is OAuthResult.Token)
        assertEquals(nonce, token.nonce, "the backend checks the token against this nonce")
        assertEquals("Desk Top", token.suggestedName)
        assertFailsWith<ConnectException>("the one-shot server is gone after the answer") { forward(state, "state=$state") }
    }

    @Test
    fun a_request_with_another_state_is_refused_and_the_wait_goes_on() = runBlocking<Unit> {
        val (opened, result) = startSignIn()
        val state = withTimeout(5_000) { opened.await() }.parameters["state"]!!
        val port = state.removePrefix("desktop-").substringBefore('-')

        // Same port, someone else's state: a local process guessing at the server.
        assertEquals(400, forward("desktop-$port-abc", "id_token=${idToken("""{"sub":"evil"}""")}&state=desktop-$port-abc").statusCode())
        assertTrue(result.isActive)

        forward(state, "error=access_denied&state=$state")
        assertEquals(OAuthResult.Cancelled, withTimeout(5_000) { result.await() })
    }

    @Test
    fun no_browser_fails_and_stops_listening() = runBlocking<Unit> {
        val (opened, result) = startSignIn(browseResult = false)
        val state = withTimeout(5_000) { opened.await() }.parameters["state"]!!
        assertTrue(withTimeout(5_000) { result.await() } is OAuthResult.Failed)
        assertFailsWith<ConnectException> { forward(state, "state=$state") }
    }
}
