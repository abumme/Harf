package uz.abumme.harfgame.backend.telegram

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import uz.abumme.harfgame.backend.dictionary.ReviewReason
import uz.abumme.harfgame.backend.dictionary.WiktionaryClassifier
import uz.abumme.harfgame.backend.dictionary.WordForm
import uz.abumme.harfgame.backend.service.DecideOutcome
import uz.abumme.harfgame.backend.service.SuggestionServerService

/**
 * Editor-facing Telegram bot. Posts suggestions awaiting a decision (with accept/reject controls),
 * announcements of automatically accepted words, and daily reports — each into the language's forum topic
 * when [topics] maps one, else the chat root — and applies decision taps from allowlisted [editorIds] only.
 * Send methods return true once Telegram confirms the message.
 *
 * A null [api] disables the bot: sends deliver nothing and report success (there is nothing to retry) and
 * [runPolling] returns immediately, so dev/test/CI run without a token.
 * ponytail: single long-poll loop per process; adopt a webhook only if editor volume grows.
 */
class TelegramBot(
    private val editorChatId: String,
    private val editorIds: Set<Long>,
    private val suggestions: SuggestionServerService,
    /** Per-language forum topic (message_thread_id) to post into; langs absent post to the chat root. */
    private val topics: Map<String, Int> = emptyMap(),
    private val api: TelegramApi? = null,
) {
    val enabled: Boolean = api != null

    /** Post a suggestion awaiting editors with accept/reject controls, noting [reason] when it adds anything. */
    suspend fun sendForReview(id: String, lang: String, word: String, author: String, reason: ReviewReason): Boolean {
        val text = listOfNotNull("Новое слово: $word ($lang)", "От: $author", reviewNote(reason)).joinToString("\n")
        return deliver(message(lang, text) {
            put("reply_markup", buildJsonObject {
                put("inline_keyboard", buildJsonArray {
                    add(buildJsonArray {
                        add(button("✅ Принять", "accept:$id"))
                        add(button("❌ Отклонить", "reject:$id"))
                    })
                })
            })
        })
    }

    /** Announce a word accepted by dictionary verification. Informational: no decision controls. */
    suspend fun announceAutoAccepted(lang: String, word: String, form: WordForm, author: String): Boolean {
        val formLabel = when (form) {
            WordForm.DICTIONARY -> "Словарная форма"
            WordForm.INFLECTED -> "Форма слова"
        }
        val link = "https://en.wiktionary.org/wiki/$word#${WiktionaryClassifier.languageName(lang).orEmpty()}"
        return deliver(message(lang, "🤖 Автопринято: $word ($lang)\n$formLabel · $link\nОт: $author") {
            put("link_preview_options", buildJsonObject { put("is_disabled", true) })
        })
    }

    /** Send an already rendered daily report to the language's topic. */
    suspend fun sendReport(lang: String, text: String): Boolean = deliver(message(lang, text))

    /**
     * Handle one tap on a decision control. The tapper always gets a popup answer; the message is edited
     * only when the tap settled the suggestion (or found it already settled), keeping its text and replacing
     * the controls with the outcome — so an unauthorized, malformed, or failing tap never strands a suggestion.
     */
    internal suspend fun handleCallback(callback: JsonObject) {
        val api = api ?: return
        val from = callback["from"]?.jsonObject ?: return
        val fromId = from["id"]?.jsonPrimitive?.longOrNull ?: return
        val data = callback["data"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val reply = try {
            decideFromTap(fromId, editorName(from), data)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TapReply("Ошибка")
        }

        callback["id"]?.jsonPrimitive?.contentOrNull?.let { callbackId ->
            api.tryCall("answerCallbackQuery", buildJsonObject {
                put("callback_query_id", callbackId)
                put("text", reply.popup)
            })
        }
        val outcome = reply.outcome ?: return
        val message = callback["message"]?.jsonObject ?: return
        val chatId = message["chat"]?.jsonObject?.get("id") ?: return
        val messageId = message["message_id"] ?: return
        val original = message["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
        // No reply_markup: the edit removes the controls.
        api.tryCall("editMessageText", buildJsonObject {
            put("chat_id", chatId)
            put("message_id", messageId)
            put("text", "$original\n\n$outcome")
        })
    }

    /** Long-poll getUpdates and handle decision taps until cancelled. No-op if disabled. */
    suspend fun runPolling() {
        val api = api ?: return
        var offset = 0L
        while (currentCoroutineContext().isActive) {
            val updates = api.tryCall("getUpdates", buildJsonObject {
                put("timeout", 25)
                put("offset", offset)
                put("allowed_updates", buildJsonArray { add(JsonPrimitive("callback_query")) })
            }) as? JsonArray
            if (updates == null) {
                // Rejected (401 bad token, 409 another poller) or unreachable: back off rather than hot-loop.
                delay(POLL_BACKOFF_MILLIS)
                continue
            }
            for (update in updates) {
                val fields = update.jsonObject
                val updateId = fields["update_id"]?.jsonPrimitive?.longOrNull ?: continue
                offset = maxOf(offset, updateId + 1)
                fields["callback_query"]?.jsonObject?.let { handleCallback(it) }
            }
        }
    }

    private data class TapReply(val popup: String, val outcome: String? = null)

    private suspend fun decideFromTap(fromId: Long, editor: String, data: String): TapReply {
        if (fromId !in editorIds) return TapReply("Недостаточно прав")
        val id = data.substringAfter(':', missingDelimiterValue = "")
        val accept = when (data.substringBefore(':', missingDelimiterValue = "")) {
            "accept" -> true
            "reject" -> false
            else -> return TapReply("Некорректно")
        }
        if (id.isEmpty()) return TapReply("Некорректно")
        return when (val result = suggestions.decide(id, accept, fromId.toString())) {
            is DecideOutcome.Applied ->
                if (result.accepted) TapReply("✅ Принято: ${result.word}", "✅ Принято — $editor")
                else TapReply("❌ Отклонено: ${result.word}", "❌ Отклонено — $editor")
            DecideOutcome.AlreadyDecided -> TapReply("Уже обработано", "ℹ️ Уже обработано")
            DecideOutcome.NotFound -> TapReply("Не найдено")
        }
    }

    private fun editorName(from: JsonObject): String =
        from["username"]?.jsonPrimitive?.contentOrNull?.let { "@$it" }
            ?: from["first_name"]?.jsonPrimitive?.contentOrNull
            ?: from["id"].toString()

    /** Editor-facing explanation of why [reason] needs a human; null when the plain message says it all. */
    private fun reviewNote(reason: ReviewReason): String? = when (reason) {
        ReviewReason.NOT_FOUND, ReviewReason.DISABLED -> null
        ReviewReason.PROPER_NOUN -> "⚠️ Wiktionary: имя собственное"
        ReviewReason.ABBREVIATION -> "⚠️ Wiktionary: сокращение"
        ReviewReason.MISSPELLING -> "⚠️ Wiktionary: ошибочное написание"
        ReviewReason.VULGAR -> "⚠️ Wiktionary: грубое или оскорбительное слово"
        ReviewReason.UNVERIFIED -> "⚠️ Словарь недоступен — проверьте вручную"
    }

    private fun message(lang: String, text: String, extra: JsonObjectBuilder.() -> Unit = {}): JsonObject =
        buildJsonObject {
            put("chat_id", editorChatId)
            topics[lang]?.let { put("message_thread_id", it) }
            put("text", text)
            extra()
        }

    private fun button(text: String, callbackData: String) =
        buildJsonObject { put("text", text); put("callback_data", callbackData) }

    private suspend fun deliver(message: JsonObject): Boolean {
        val api = api ?: return true
        return api.tryCall("sendMessage", message) != null
    }

    /** A failed call is reported like an unconfirmed one; cancellation still propagates. */
    private suspend fun TelegramApi.tryCall(method: String, body: JsonObject): JsonElement? = try {
        call(method, body)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val POLL_BACKOFF_MILLIS = 3_000L
    }
}
