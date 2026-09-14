package uz.abumme.harfgame.backend.telegram

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import uz.abumme.harfgame.backend.dictionary.ReviewReason
import uz.abumme.harfgame.backend.dictionary.WordForm
import uz.abumme.harfgame.backend.insertPack
import uz.abumme.harfgame.backend.insertPending
import uz.abumme.harfgame.backend.resetSuggestionData
import uz.abumme.harfgame.backend.service.SuggestionServerService
import uz.abumme.harfgame.backend.service.WordPackServerService
import uz.abumme.harfgame.backend.statusOf
import uz.abumme.harfgame.data.suggestion.SuggestionStatus
import java.io.IOException
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class) // advanceTimeBy drives the polling backoff in virtual time
class TelegramBotTest {

    @BeforeTest
    fun setup() = resetSuggestionData()

    private val api = FakeTelegramApi()

    private fun bot(api: TelegramApi? = this.api) = TelegramBot(
        editorChatId = "-1001234567890",
        editorIds = setOf(42L),
        suggestions = SuggestionServerService(WordPackServerService()),
        topics = mapOf("ru" to 3, "uz-latn" to 7),
        api = api,
    )

    private fun JsonObject.text() = this["text"]!!.jsonPrimitive.content
    private fun JsonObject.threadId() = this["message_thread_id"]?.jsonPrimitive?.int
    private fun JsonObject.callbackData() = this["reply_markup"]?.jsonObject?.get("inline_keyboard")?.jsonArray
        ?.flatMap { row -> row.jsonArray.map { it.jsonObject["callback_data"]!!.jsonPrimitive.content } }

    /** A callback_query as Telegram delivers it for a tap on a decision message in a forum topic. */
    private fun callback(fromId: Long, data: String, username: String? = "ada"): JsonObject = buildJsonObject {
        put("id", "4382bfdwdsb323b2d9")
        put("from", buildJsonObject {
            put("id", fromId)
            put("is_bot", false)
            put("first_name", "Ada")
            username?.let { put("username", it) }
            put("language_code", "ru")
        })
        put("message", buildJsonObject {
            put("message_id", 77)
            put("message_thread_id", 4)
            put("from", buildJsonObject { put("id", 999000); put("is_bot", true); put("first_name", "Harf"); put("username", "harf_bot") })
            put("chat", buildJsonObject { put("id", -1001234567890); put("title", "Harf editors"); put("is_forum", true); put("type", "supergroup") })
            put("date", 1757830000)
            put("is_topic_message", true)
            put("text", "Новое слово: hello (en)\nОт: Аноним")
            put("reply_markup", buildJsonObject {
                put("inline_keyboard", buildJsonArray {
                    add(buildJsonArray {
                        add(buildJsonObject { put("text", "✅ Принять"); put("callback_data", data) })
                    })
                })
            })
        })
        put("chat_instance", "-5550001")
        put("data", data)
    }

    // ---- confirmed delivery (3.1) ----

    @Test
    fun sendReportsWhetherTelegramConfirmed() = runBlocking {
        api.respond = { _, _ -> null }
        assertFalse(bot().sendReport("en", "📊"))
        api.respond = { _, _ -> buildJsonObject { put("message_id", 1) } }
        assertTrue(bot().sendReport("en", "📊"))
    }

    @Test
    fun disabledBotHasNothingToDeliver() = runBlocking {
        assertTrue(bot(api = null).announceAutoAccepted("en", "crane", WordForm.DICTIONARY, "Ada"))
        assertTrue(bot(api = null).sendForReview("s1", "en", "crane", "Ada", ReviewReason.NOT_FOUND))
        assertTrue(bot(api = null).sendReport("en", "📊"))
    }

    @Test
    fun telegramResultCountsOnlyWhenOk() {
        assertEquals(JsonPrimitive(true), telegramResult("""{"ok":true,"result":true}"""))
        assertNull(telegramResult("""{"ok":false,"error_code":409,"description":"Conflict: terminated by other getUpdates request"}"""))
        assertNull(telegramResult("""{"ok":false,"error_code":401,"description":"Unauthorized"}"""))
        assertNull(telegramResult("<html>502 Bad Gateway</html>"))
    }

    // ---- message kinds (3.2) ----

    @Test
    fun decisionMessageCarriesWordAuthorAndBothControlsInTheLanguageTopic() = runBlocking {
        bot().sendForReview("s1", "uz-latn", "salom", "Grace", ReviewReason.NOT_FOUND)
        val sent = api.sent("sendMessage").single()
        assertEquals(7, sent.threadId())
        assertTrue("salom" in sent.text() && "uz-latn" in sent.text() && "Grace" in sent.text(), sent.text())
        assertFalse("⚠️" in sent.text(), sent.text())
        assertEquals(listOf("accept:s1", "reject:s1"), sent.callbackData())
    }

    @Test
    fun languageWithoutTopicPostsToTheChatRoot() = runBlocking {
        bot().sendForReview("s1", "en", "hello", "Grace", ReviewReason.NOT_FOUND)
        val sent = api.sent("sendMessage").single()
        assertNull(sent.threadId())
        assertEquals("-1001234567890", sent["chat_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun decisionMessageStatesWhyTheWordNeedsAnEditor() = runBlocking {
        val cases = listOf(
            ReviewReason.PROPER_NOUN to "имя собственное",
            ReviewReason.ABBREVIATION to "сокращение",
            ReviewReason.MISSPELLING to "ошибочное написание",
            ReviewReason.VULGAR to "грубое",
            ReviewReason.UNVERIFIED to "Словарь недоступен",
        )
        for ((reason, phrase) in cases) {
            api.calls.clear()
            bot().sendForReview("s1", "ru", "павел", "Aziz", reason)
            val text = api.sent("sendMessage").single().text()
            assertTrue("⚠️" in text && phrase in text, "$reason: $text")
        }
    }

    @Test
    fun announcementNamesWordFormLinkAndAuthorWithoutControls() = runBlocking {
        bot().announceAutoAccepted("ru", "книги", WordForm.INFLECTED, "Aziz")
        val sent = api.sent("sendMessage").single()
        val text = sent.text()
        assertEquals(3, sent.threadId())
        for (part in listOf("книги", "(ru)", "Форма слова", "https://en.wiktionary.org/wiki/книги#Russian", "Aziz")) {
            assertTrue(part in text, "missing '$part' in: $text")
        }
        assertNull(sent["reply_markup"])
        assertEquals(true, sent["link_preview_options"]?.jsonObject?.get("is_disabled")?.jsonPrimitive?.boolean)
    }

    @Test
    fun announcementNamesADictionaryForm() = runBlocking {
        bot().announceAutoAccepted("en", "crane", WordForm.DICTIONARY, "Ada")
        val text = api.sent("sendMessage").single().text()
        assertTrue("Словарная форма" in text && "#English" in text, text)
    }

    @Test
    fun reportIsSentUnchangedToTheLanguageTopic() = runBlocking {
        bot().sendReport("ru", "📊 Отчёт за 14.09 · ru")
        val sent = api.sent("sendMessage").single()
        assertEquals(3, sent.threadId())
        assertEquals("📊 Отчёт за 14.09 · ru", sent.text())
    }

    // ---- decision taps (3.3) ----

    @Test
    fun nonEditorTapOnlyAnswersTheTapper() = runBlocking {
        insertPack("en", "1", listOf("x"))
        val id = insertPending("en", "hello")
        bot().handleCallback(callback(fromId = 999L, data = "accept:$id"))
        assertEquals(listOf("answerCallbackQuery"), api.calls.map { it.first })
        assertEquals(SuggestionStatus.PENDING.name, statusOf(id))
    }

    @Test
    fun malformedTapOnlyAnswersTheTapper() = runBlocking {
        bot().handleCallback(callback(fromId = 42L, data = "approve-everything"))
        assertEquals(listOf("answerCallbackQuery"), api.calls.map { it.first })
    }

    @Test
    fun editorDecisionKeepsMessageContextAndReplacesControls() = runBlocking {
        insertPack("en", "1", listOf("x"))
        val id = insertPending("en", "hello")
        bot().handleCallback(callback(fromId = 42L, data = "accept:$id"))

        assertEquals(SuggestionStatus.ACCEPTED.name, statusOf(id))
        assertEquals("4382bfdwdsb323b2d9", api.sent("answerCallbackQuery").single()["callback_query_id"]!!.jsonPrimitive.content)
        val edit = api.sent("editMessageText").single()
        assertEquals(-1001234567890L, edit["chat_id"]!!.jsonPrimitive.long)
        assertEquals(77, edit["message_id"]!!.jsonPrimitive.int)
        assertTrue(edit.text().startsWith("Новое слово: hello (en)\nОт: Аноним"), edit.text())
        assertTrue("Принято" in edit.text() && "@ada" in edit.text(), edit.text())
        assertNull(edit["reply_markup"])
    }

    @Test
    fun editorWithoutUsernameIsNamedByFirstName() = runBlocking {
        insertPack("en", "1", listOf("x"))
        val id = insertPending("en", "hello")
        bot().handleCallback(callback(fromId = 42L, data = "reject:$id", username = null))
        assertEquals(SuggestionStatus.REJECTED.name, statusOf(id))
        val text = api.sent("editMessageText").single().text()
        assertTrue("Отклонено" in text && "Ada" in text, text)
    }

    @Test
    fun tapOnAnAlreadyDecidedSuggestionClearsStaleControls() = runBlocking {
        insertPack("en", "1", listOf("x"))
        val id = insertPending("en", "hello")
        bot().handleCallback(callback(fromId = 42L, data = "accept:$id"))
        api.calls.clear()

        bot().handleCallback(callback(fromId = 42L, data = "reject:$id"))
        assertEquals(SuggestionStatus.ACCEPTED.name, statusOf(id))
        assertTrue("Уже обработано" in api.sent("editMessageText").single().text())
    }

    // ---- polling (3.4) ----

    @Test
    fun rejectedGetUpdatesBacksOff() = runTest {
        var polls = 0
        val telegram = FakeTelegramApi { method, _ ->
            if (method == "getUpdates" && ++polls > 50) throw CancellationException("polled without backoff")
            null // e.g. 409 Conflict: another process is polling with the same token
        }
        backgroundScope.launch { bot(api = telegram).runPolling() }
        advanceTimeBy(10_000)
        assertTrue(polls in 1..5, "polls=$polls")
    }

    @Test
    fun throwingGetUpdatesBacksOff() = runTest {
        var polls = 0
        val telegram = FakeTelegramApi { method, _ ->
            if (method == "getUpdates" && ++polls > 50) throw CancellationException("polled without backoff")
            throw IOException("connection reset")
        }
        backgroundScope.launch { bot(api = telegram).runPolling() }
        advanceTimeBy(10_000)
        assertTrue(polls in 1..5, "polls=$polls")
    }

    @Test
    fun pollingHandlesEachUpdateOnce() = runTest {
        val offsets = mutableListOf<Long>()
        val telegram = FakeTelegramApi { method, body ->
            when (method) {
                "getUpdates" -> {
                    offsets += body["offset"]!!.jsonPrimitive.long
                    if (offsets.size > 1) throw CancellationException("done")
                    buildJsonArray {
                        add(buildJsonObject { put("update_id", 500); put("callback_query", callback(fromId = 999L, data = "accept:x")) })
                    }
                }
                else -> JsonPrimitive(true)
            }
        }
        backgroundScope.launch { bot(api = telegram).runPolling() }
        advanceTimeBy(1_000)
        assertEquals(listOf(0L, 501L), offsets)
        assertEquals(1, telegram.sent("answerCallbackQuery").size)
    }
}
