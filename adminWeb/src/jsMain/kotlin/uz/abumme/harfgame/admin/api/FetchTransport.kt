package uz.abumme.harfgame.admin.api

import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.fetch.RequestCredentials
import org.w3c.fetch.RequestInit
import org.w3c.fetch.RequestRedirect

/** [HttpTransport] over the browser's `window.fetch`. */
class FetchTransport : HttpTransport {
    override suspend fun send(request: HttpRequest): HttpResponse {
        val headers = js("({})")
        request.headers.forEach { (name, value) -> headers[name] = value }
        val init = RequestInit(
            method = request.method,
            headers = headers,
            body = request.body ?: undefined,
            credentials = request.credentials.unsafeCast<RequestCredentials>(),
            redirect = "error".unsafeCast<RequestRedirect>(),
        )
        return try {
            val response = window.fetch(request.url, init).await()
            HttpResponse(response.status.toInt(), response.text().await())
        } catch (e: Throwable) {
            HttpResponse(0, "")
        }
    }
}
