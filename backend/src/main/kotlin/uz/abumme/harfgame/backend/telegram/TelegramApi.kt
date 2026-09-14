package uz.abumme.harfgame.backend.telegram

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/** Telegram Bot API transport. */
interface TelegramApi {
    /** Calls [method]; returns the response's `result` only when Telegram answers `"ok": true`, else null. */
    suspend fun call(method: String, body: JsonObject): JsonElement?
}

/** [TelegramApi] over the JDK HTTP client (no bot library: a handful of JSON calls). */
class HttpTelegramApi(
    private val botToken: String,
    private val http: HttpClient = HttpClient.newHttpClient(),
) : TelegramApi {
    override suspend fun call(method: String, body: JsonObject): JsonElement? = withContext(Dispatchers.IO) {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.telegram.org/bot$botToken/$method"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build()
        telegramResult(http.send(request, HttpResponse.BodyHandlers.ofString()).body())
    }
}

/**
 * The `result` of a Bot API response body when it reports `"ok": true`. Error responses (401 bad token,
 * 409 a second poller, 429 flood control) and unreadable bodies are null — never mistaken for success.
 */
internal fun telegramResult(body: String): JsonElement? {
    val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
    return root["result"].takeIf { (root["ok"] as? JsonPrimitive)?.booleanOrNull == true }
}
