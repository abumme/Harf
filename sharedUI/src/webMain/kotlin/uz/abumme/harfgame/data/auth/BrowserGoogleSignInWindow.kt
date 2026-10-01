package uz.abumme.harfgame.data.auth

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import org.w3c.dom.BroadcastChannel

/**
 * [GoogleSignInWindow] in the browser. The callback page (`webApp`'s `oauth-callback.html`, next to `index.html`)
 * posts Google's redirect fragment on a [BroadcastChannel] rather than to `window.opener`: the opener link may not
 * survive the popup's trip through Google's pages, the channel reaches every same-origin page regardless.
 */
class BrowserGoogleSignInWindow : GoogleSignInWindow {

    override val redirectUri: String
        get() = document.baseURI.substringBefore('#').substringBefore('?').substringBeforeLast('/') + "/" + CALLBACK_PAGE

    @OptIn(ExperimentalWasmJsInterop::class)
    override fun open(url: String): ReceiveChannel<String>? {
        val responses = Channel<String>(Channel.UNLIMITED)
        // Missing in old browsers (Safari before 15.4, on the JS build): no way to hear the callback, so no popup.
        val channel = try {
            BroadcastChannel(CHANNEL)
        } catch (e: Throwable) {
            throw IllegalStateException("This browser can't receive the sign-in response (no BroadcastChannel)", e)
        }
        channel.onmessage = { event -> (event.data as? JsString)?.toString()?.let { responses.trySend(it) } }
        responses.invokeOnClose { channel.close() }
        if (window.open(url, POPUP_NAME, POPUP_FEATURES) == null) {
            responses.cancel()
            return null
        }
        return responses
    }

    private companion object {
        // Both names are shared with oauth-callback.html.
        const val CALLBACK_PAGE = "oauth-callback.html"
        const val CHANNEL = "harf-google-signin"
        const val POPUP_NAME = "harf-google-signin"
        const val POPUP_FEATURES = "popup,width=500,height=650"
    }
}
