package uz.abumme.harfgame.admin.api

/** One HTTP exchange, kept free of browser types so the API client can be tested without a DOM. */
data class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: String?,
    /** `fetch` credentials mode: `same-origin` or `include`. */
    val credentials: String,
)

/** [status] 0 means the request never got an answer (network failure). */
data class HttpResponse(val status: Int, val body: String)

fun interface HttpTransport {
    suspend fun send(request: HttpRequest): HttpResponse
}
