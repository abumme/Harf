package uz.abumme.harfgame.backend.telegram

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import uz.abumme.harfgame.backend.service.DecideOutcome
import uz.abumme.harfgame.backend.service.SuggestionServerService
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.coroutines.coroutineContext

/**
 * Minimal Telegram bot over the Bot API using the JDK HTTP client (no extra dependency — three
 * calls: sendMessage, answerCallbackQuery, getUpdates long-poll). Editors approve/reject pending
 * word suggestions from an inline keyboard; only allowlisted [editorIds] may act.
 *
 * A blank [botToken] disables the bot: [notifyPending] is a no-op and [runPolling] returns
 * immediately, so suggestions are still stored (store-only degrade) and dev/test/CI run without a
 * token. ponytail: single long-poll loop per process; adopt a webhook only if editor volume grows.
 */
class TelegramBot(
    private val botToken: String,
    private val editorChatId: String,
    private val editorIds: Set<Long>,
    private val suggestions: SuggestionServerService,
    /** Per-language forum topic (message_thread_id) to post into; langs absent post to the chat root. */
    private val topics: Map<String, Int> = emptyMap(),
    private val http: HttpClient = HttpClient.newHttpClient(),
) {
    val enabled: Boolean = botToken.isNotBlank()
    private val json = Json { ignoreUnknownKeys = true }

    /** Post a pending suggestion to the editors' chat/topic with accept/reject controls. No-op if disabled. */
    suspend fun notifyPending(id: String, lang: String, word: String, author: String) {
        if (!enabled) return
        runCatching { post("sendMessage", suggestionMessage(id, lang, word, author)) }
    }

    /** The sendMessage payload for a pending suggestion, routed to the language's topic when mapped. */
    internal fun suggestionMessage(id: String, lang: String, word: String, author: String): JsonObject {
        val keyboard = buildJsonObject {
            put("inline_keyboard", buildJsonArray {
                add(buildJsonArray {
                    add(buildJsonObject { put("text", "✅ Принять"); put("callback_data", "accept:$id") })
                    add(buildJsonObject { put("text", "❌ Отклонить"); put("callback_data", "reject:$id") })
                })
            })
        }
        return buildJsonObject {
            put("chat_id", editorChatId)
            topics[lang]?.let { put("message_thread_id", it) }
            put("text", "Новое слово: $word ($lang)\nОт: $author")
            put("reply_markup", keyboard)
        }
    }

    /**
     * Authorize and apply a callback. Network-free: returns the text to answer the callback with.
     * A non-editor tap changes nothing. Used by [runPolling]; also the unit-test seam.
     */
    suspend fun onCallback(fromId: Long, data: String): String {
        if (fromId !in editorIds) return "Недостаточно прав"
        val sep = data.indexOf(':')
        if (sep <= 0) return "Некорректно"
        val action = data.substring(0, sep)
        val id = data.substring(sep + 1)
        val accept = when (action) {
            "accept" -> true
            "reject" -> false
            else -> return "Некорректно"
        }
        return when (val r = suggestions.decide(id, accept, fromId.toString())) {
            is DecideOutcome.Applied -> if (r.accepted) "✅ Принято: ${r.word}" else "❌ Отклонено: ${r.word}"
            DecideOutcome.NotFound -> "Не найдено"
            DecideOutcome.AlreadyDecided -> "Уже обработано"
        }
    }

    /** Long-poll getUpdates and process callbacks until the coroutine is cancelled. No-op if disabled. */
    suspend fun runPolling() {
        if (!enabled) return
        var offset = 0L
        while (coroutineContext.isActive) {
            val updates = runCatching { fetchUpdates(offset) }.getOrElse {
                delay(3000); continue // transient network error: back off and retry
            }
            for (update in updates) {
                offset = maxOf(offset, update["update_id"]!!.jsonPrimitive.long + 1)
                val cb = update["callback_query"]?.jsonObject ?: continue
                val fromId = cb["from"]?.jsonObject?.get("id")?.jsonPrimitive?.long ?: continue
                val data = cb["data"]?.jsonPrimitive?.contentOrNull ?: continue
                val reply = runCatching { onCallback(fromId, data) }.getOrElse { "Ошибка" }
                cb["id"]?.jsonPrimitive?.contentOrNull?.let { answerCallback(it, reply) }
                val msg = cb["message"]?.jsonObject
                val chatId = msg?.get("chat")?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
                val messageId = msg?.get("message_id")?.jsonPrimitive?.int
                if (chatId != null && messageId != null) editMessageText(chatId, messageId, reply)
            }
        }
    }

    private suspend fun fetchUpdates(offset: Long): List<JsonObject> {
        val body = buildJsonObject {
            put("timeout", 25)
            put("offset", offset)
            put("allowed_updates", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("callback_query")) })
        }
        val resp = post("getUpdates", body)
        val result = json.parseToJsonElement(resp).jsonObject["result"]?.jsonArray ?: return emptyList()
        return result.map { it.jsonObject }
    }

    private suspend fun answerCallback(callbackId: String, text: String) {
        runCatching {
            post("answerCallbackQuery", buildJsonObject {
                put("callback_query_id", callbackId)
                put("text", text)
            })
        }
    }

    private suspend fun editMessageText(chatId: String, messageId: Int, text: String) {
        runCatching {
            post("editMessageText", buildJsonObject {
                put("chat_id", chatId)
                put("message_id", messageId)
                put("text", text)
            })
        }
    }

    private suspend fun post(method: String, body: JsonObject): String = withContext(Dispatchers.IO) {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.telegram.org/bot$botToken/$method"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build()
        http.send(request, HttpResponse.BodyHandlers.ofString()).body()
    }
}
