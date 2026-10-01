package uz.abumme.harfgame.data

import io.ktor.http.Url
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import uz.abumme.harfgame.data.auth.GoogleSignInWindow
import uz.abumme.harfgame.data.auth.OAuthResult
import uz.abumme.harfgame.data.auth.WebGoogleOAuthClient
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** A popup that never opens a browser: records the URL and lets the test post callback responses. */
class FakeGoogleSignInWindow(
    override val redirectUri: String = "https://harf.lazydevs.uz/oauth-callback.html",
    private val blocked: Boolean = false,
    private val failure: Exception? = null,
) : GoogleSignInWindow {
    val responses = Channel<String>(Channel.UNLIMITED)
    var openedUrl: String? = null

    override fun open(url: String): ReceiveChannel<String>? {
        openedUrl = url
        failure?.let { throw it }
        return if (blocked) null else responses
    }

    /** The `state`/`nonce` the client put in the URL it opened. */
    fun param(name: String): String = Url(assertNotNull(openedUrl)).parameters[name]!!
}

class WebGoogleOAuthClientTest {

    @OptIn(ExperimentalEncodingApi::class)
    private fun idToken(payload: String) =
        "eyJhbGciOiJSUzI1NiJ9." + Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(payload.encodeToByteArray()) + ".sig"

    @Test
    fun asks_google_for_an_id_token_bound_to_a_fresh_state_and_nonce() = runTest {
        val window = FakeGoogleSignInWindow()
        val client = WebGoogleOAuthClient("client-1", window)
        val result = async { client.signInWithGoogle() }
        yield()

        val url = Url(assertNotNull(window.openedUrl))
        assertEquals("accounts.google.com", url.host)
        assertEquals("/o/oauth2/v2/auth", url.encodedPath)
        assertEquals("client-1", url.parameters["client_id"])
        assertEquals("https://harf.lazydevs.uz/oauth-callback.html", url.parameters["redirect_uri"])
        assertEquals("id_token", url.parameters["response_type"])
        assertEquals("openid email profile", url.parameters["scope"])
        assertNotEquals(window.param("state"), window.param("nonce"))
        assertTrue(window.param("state").length >= 64)

        window.responses.send("id_token=${idToken("""{"sub":"1","name":"Umid"}""")}&state=${window.param("state")}")
        val token = result.await()
        assertTrue(token is OAuthResult.Token)
        assertEquals(window.param("nonce"), token.nonce, "the backend checks the token against this nonce")
        assertEquals("Umid", token.suggestedName)
        assertTrue(window.responses.tryReceive().isClosed, "the callback channel is released after the answer")
    }

    @Test
    fun another_sign_ins_response_is_ignored() = runTest {
        val window = FakeGoogleSignInWindow()
        val result = async { WebGoogleOAuthClient("client-1", window).signInWithGoogle() }
        yield()

        // Another tab's sign-in (or a forged post) carries a different state.
        window.responses.send("id_token=${idToken("""{"sub":"evil"}""")}&state=not-ours")
        yield()
        assertTrue(result.isActive, "a foreign response must not complete this sign-in")

        window.responses.send("id_token=${idToken("""{"sub":"1"}""")}&state=${window.param("state")}")
        val token = result.await()
        assertTrue(token is OAuthResult.Token)
        assertNull(token.suggestedName)
    }

    @Test
    fun googles_errors_map_to_cancelled_or_failed() = runTest {
        val denied = FakeGoogleSignInWindow()
        val cancelled = async { WebGoogleOAuthClient("client-1", denied).signInWithGoogle() }
        yield()
        denied.responses.send("error=access_denied&state=${denied.param("state")}")
        assertEquals(OAuthResult.Cancelled, cancelled.await())

        val broken = FakeGoogleSignInWindow()
        val failed = async { WebGoogleOAuthClient("client-1", broken).signInWithGoogle() }
        yield()
        broken.responses.send("error=invalid_request&error_description=Bad+redirect&state=${broken.param("state")}")
        assertEquals(OAuthResult.Failed("invalid_request"), failed.await())
    }

    @Test
    fun a_blocked_popup_fails_instead_of_waiting() = runTest {
        val result = WebGoogleOAuthClient("client-1", FakeGoogleSignInWindow(blocked = true)).signInWithGoogle()
        assertTrue(result is OAuthResult.Failed)
    }

    @Test
    fun a_browser_that_cannot_listen_fails_instead_of_crashing() = runTest {
        val window = FakeGoogleSignInWindow(failure = IllegalStateException("no BroadcastChannel"))
        assertEquals(OAuthResult.Failed("no BroadcastChannel"), WebGoogleOAuthClient("client-1", window).signInWithGoogle())
    }

    @Test
    fun a_popup_closed_without_an_answer_stops_waiting_eventually() = runTest {
        val window = FakeGoogleSignInWindow()
        val result = async { WebGoogleOAuthClient("client-1", window).signInWithGoogle() }
        advanceTimeBy(WebGoogleOAuthClient.RESPONSE_TIMEOUT - 1.seconds)
        assertTrue(result.isActive, "a slow sign-in (second factor, new account) still completes")

        advanceTimeBy(2.seconds)
        assertEquals(OAuthResult.Cancelled, result.await())
        assertTrue(window.responses.tryReceive().isClosed, "the callback channel is released")
    }

    @Test
    fun the_name_claim_is_optional_and_never_throws() {
        assertEquals("Ada", WebGoogleOAuthClient.nameClaim(idToken("""{"name":" Ada "}""")))
        assertNull(WebGoogleOAuthClient.nameClaim(idToken("""{"name":""}""")))
        assertNull(WebGoogleOAuthClient.nameClaim("not-a-jwt"))
        assertNull(WebGoogleOAuthClient.nameClaim("a.!!!.c"))
    }
}
