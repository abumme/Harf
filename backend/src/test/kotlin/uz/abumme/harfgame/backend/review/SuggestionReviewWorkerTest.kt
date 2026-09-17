package uz.abumme.harfgame.backend.review

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.WordSuggestionsTable
import uz.abumme.harfgame.backend.db.WordsTable
import uz.abumme.harfgame.backend.wordRow
import uz.abumme.harfgame.data.admin.words.WordStatus
import uz.abumme.harfgame.backend.dictionary.LookupResult
import uz.abumme.harfgame.backend.dictionary.ReviewReason
import uz.abumme.harfgame.backend.dictionary.WiktionaryLookup
import uz.abumme.harfgame.backend.dictionary.WordForm
import uz.abumme.harfgame.backend.dictionary.WordLookup
import uz.abumme.harfgame.backend.insertPack
import uz.abumme.harfgame.backend.insertSuggestion
import uz.abumme.harfgame.backend.resetSuggestionData
import uz.abumme.harfgame.backend.service.SuggestionServerService
import uz.abumme.harfgame.backend.service.WordPackServerService
import uz.abumme.harfgame.backend.statusOf
import uz.abumme.harfgame.backend.suggestionColumn
import uz.abumme.harfgame.backend.telegram.FakeTelegramApi
import uz.abumme.harfgame.backend.telegram.TelegramApi
import uz.abumme.harfgame.backend.telegram.EditorDirectory
import uz.abumme.harfgame.backend.telegram.TelegramBot
import uz.abumme.harfgame.data.suggestion.SuggestionStatus
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SuggestionReviewWorkerTest {

    @BeforeTest
    fun setup() = resetSuggestionData()

    private val telegram = FakeTelegramApi()
    private val service = SuggestionServerService(WordPackServerService())

    /** Answers every lookup with [result] and records what was looked up. */
    private class FakeLookup(var result: LookupResult) : WordLookup {
        val lookedUp = mutableListOf<String>()
        override suspend fun lookup(lang: String, word: String): LookupResult {
            lookedUp += "$lang:$word"
            return result
        }
    }

    private fun worker(lookup: WordLookup, api: TelegramApi? = telegram) = SuggestionReviewWorker(
        suggestions = service,
        lookup = lookup,
        telegram = TelegramBot(editorChatId = "chat", editors = EditorDirectory(setOf(42L)), suggestions = service, api = api),
    )

    private fun queue(lang: String, word: String) = insertSuggestion(lang, word, reviewState = "QUEUED")

    private fun reviewStateOf(id: String) = suggestionColumn(id, WordSuggestionsTable.reviewState)

    private fun JsonObject.text() = this["text"]!!.jsonPrimitive.content
    private fun JsonObject.hasControls() = this["reply_markup"]?.jsonObject?.get("inline_keyboard")?.jsonArray?.isNotEmpty() == true

    @Test
    fun realWordIsAcceptedIntoThePackAnnouncedAndPosted() = runBlocking {
        insertPack("ru", "1", listOf("книга"))
        val id = queue("ru", "книги")

        worker(FakeLookup(LookupResult.Auto(WordForm.INFLECTED))).runOnce()

        assertEquals(SuggestionStatus.ACCEPTED.name, statusOf(id))
        assertEquals("AUTO", suggestionColumn(id, WordSuggestionsTable.decidedVia))
        assertEquals("wiktionary", suggestionColumn(id, WordSuggestionsTable.decidedBy))
        val pack = WordPackServerService().getPack("ru")!!
        assertTrue("книги" in pack.guesses)
        assertEquals("2", pack.version)
        val announcement = telegram.sent("sendMessage").single()
        assertTrue("Автопринято" in announcement.text() && "книги" in announcement.text(), announcement.text())
        assertFalse(announcement.hasControls())
        assertEquals("POSTED", reviewStateOf(id))
    }

    @Test
    fun unknownWordStaysPendingAndGoesToEditors() = runBlocking {
        insertPack("ru", "1", listOf("книга"))
        val id = queue("ru", "бырка")

        worker(FakeLookup(LookupResult.Review(ReviewReason.NOT_FOUND))).runOnce()

        assertEquals(SuggestionStatus.PENDING.name, statusOf(id))
        val message = telegram.sent("sendMessage").single()
        assertTrue(message.hasControls())
        assertTrue("accept:$id" in message.toString())
        assertEquals("POSTED", reviewStateOf(id))
        assertEquals("1", WordPackServerService().getPack("ru")!!.version)
    }

    @Test
    fun flaggedWordReachesEditorsWithItsReason() = runBlocking {
        val id = queue("ru", "павел")
        worker(FakeLookup(LookupResult.Review(ReviewReason.PROPER_NOUN))).runOnce()

        assertEquals(SuggestionStatus.PENDING.name, statusOf(id))
        assertTrue("имя собственное" in telegram.sent("sendMessage").single().text())
    }

    @Test
    fun unavailableDictionaryIsRetriedThenSentToEditorsAsUnverified() = runBlocking {
        val id = queue("en", "crane")
        val worker = worker(FakeLookup(LookupResult.Unavailable))

        worker.runOnce()
        worker.runOnce()
        assertEquals(emptyList(), telegram.sent("sendMessage"))
        assertEquals("QUEUED", reviewStateOf(id))
        assertEquals(2, suggestionColumn(id, WordSuggestionsTable.lookupAttempts))

        worker.runOnce()
        val message = telegram.sent("sendMessage").single()
        assertTrue(message.hasControls() && "Словарь недоступен" in message.text(), message.text())
        assertEquals(SuggestionStatus.PENDING.name, statusOf(id))
        assertEquals("POSTED", reviewStateOf(id))
    }

    @Test
    fun unconfirmedDecisionMessageStaysQueuedUntilDelivered() = runBlocking {
        val id = queue("en", "zqxta")
        val worker = worker(FakeLookup(LookupResult.Review(ReviewReason.NOT_FOUND)))

        telegram.respond = { _, _ -> null }
        worker.runOnce()
        assertEquals("QUEUED", reviewStateOf(id))

        telegram.respond = { _, _ -> buildJsonObject { put("message_id", 9) } }
        worker.runOnce()
        assertEquals("POSTED", reviewStateOf(id))
        assertEquals(2, telegram.sent("sendMessage").size)
    }

    @Test
    fun unconfirmedAnnouncementIsResentWithItsFormWithoutDecidingAgain() = runBlocking {
        insertPack("en", "1", listOf("x"))
        val id = queue("en", "crane")
        val lookup = FakeLookup(LookupResult.Auto(WordForm.DICTIONARY))
        val worker = worker(lookup)

        telegram.respond = { _, _ -> null }
        worker.runOnce()
        assertEquals(SuggestionStatus.ACCEPTED.name, statusOf(id))
        assertEquals("QUEUED", reviewStateOf(id))

        telegram.respond = { _, _ -> buildJsonObject { put("message_id", 9) } }
        lookup.result = LookupResult.Review(ReviewReason.NOT_FOUND) // a second lookup would change the outcome
        worker.runOnce()

        assertEquals("POSTED", reviewStateOf(id))
        assertEquals(listOf("en:crane"), lookup.lookedUp)
        assertEquals("2", WordPackServerService().getPack("en")!!.version)
        val resent = telegram.sent("sendMessage").last()
        assertTrue("Автопринято" in resent.text() && "Словарная форма" in resent.text(), resent.text())
    }

    @Test
    fun rowsFromBeforeTheWorkerAreLeftAlone() = runBlocking {
        val id = insertSuggestion("en", "older", reviewState = null)
        val lookup = FakeLookup(LookupResult.Auto(WordForm.DICTIONARY))

        worker(lookup).runOnce()

        assertEquals(emptyList(), lookup.lookedUp)
        assertEquals(emptyList(), telegram.calls)
        assertEquals(SuggestionStatus.PENDING.name, statusOf(id))
        assertNull(reviewStateOf(id))
    }

    @Test
    fun disabledLookupSendsEveryWordToEditors() = runBlocking {
        val id = queue("ru", "книги")
        worker(WiktionaryLookup(enabled = false)).runOnce()

        assertEquals(SuggestionStatus.PENDING.name, statusOf(id))
        assertTrue(telegram.sent("sendMessage").single().hasControls())
        assertEquals("POSTED", reviewStateOf(id))
    }

    @Test
    fun withoutABotWordsAreStillAcceptedAndTheQueueClears() = runBlocking {
        insertPack("en", "1", listOf("x"))
        val accepted = queue("en", "crane")

        worker(FakeLookup(LookupResult.Auto(WordForm.DICTIONARY)), api = null).runOnce()

        assertEquals(SuggestionStatus.ACCEPTED.name, statusOf(accepted))
        assertEquals("POSTED", reviewStateOf(accepted))
    }

    // ---- the word catalog (word-catalog 4.3, 5.2) ----

    @Test
    fun aWordStaffRemovedGoesToEditorsEvenWhenTheDictionaryVerifiesIt() = runBlocking {
        insertPack("ru", "1", listOf("книги"))
        transaction(DatabaseFactory.init()) {
            WordsTable.update({ WordsTable.text eq "книги" }) { it[status] = WordStatus.REMOVED.name }
        }
        val id = queue("ru", "книги")
        val lookup = FakeLookup(LookupResult.Auto(WordForm.INFLECTED))

        worker(lookup).runOnce()

        assertEquals(SuggestionStatus.PENDING.name, statusOf(id))
        assertEquals(emptyList(), lookup.lookedUp)
        val message = telegram.sent("sendMessage").single()
        assertTrue(message.hasControls())
        assertTrue("удалено редакторами" in message.text(), message.text())
        assertEquals("REMOVED_BY_STAFF", suggestionColumn(id, WordSuggestionsTable.reviewReason))
        assertEquals(WordStatus.REMOVED.name, wordRow("ru", "книги")!![WordsTable.status])
        assertEquals("1", WordPackServerService().getPack("ru")!!.version)
    }

    @Test
    fun aDecisionMessageIsRecordedWithItsReason() = runBlocking {
        val id = queue("en", "zqxta")
        telegram.respond = { method, _ ->
            if (method == "sendMessage") buildJsonObject {
                put("message_id", 555)
                put("chat", buildJsonObject { put("id", -1001234567890); put("type", "supergroup") })
            } else null
        }

        worker(FakeLookup(LookupResult.Review(ReviewReason.PROPER_NOUN))).runOnce()

        val sent = telegram.sent("sendMessage").single()
        assertEquals("-1001234567890", suggestionColumn(id, WordSuggestionsTable.telegramChatId))
        assertEquals(555L, suggestionColumn(id, WordSuggestionsTable.telegramMessageId))
        assertEquals(sent.text(), suggestionColumn(id, WordSuggestionsTable.telegramText))
        assertEquals("PROPER_NOUN", suggestionColumn(id, WordSuggestionsTable.reviewReason))
    }

    @Test
    fun withoutABotTheReasonIsStillRecorded() = runBlocking {
        val id = queue("en", "zqxta")

        worker(FakeLookup(LookupResult.Review(ReviewReason.NOT_FOUND)), api = null).runOnce()

        assertEquals("POSTED", reviewStateOf(id))
        assertEquals("NOT_FOUND", suggestionColumn(id, WordSuggestionsTable.reviewReason))
        assertNull(suggestionColumn(id, WordSuggestionsTable.telegramMessageId))
    }

    @Test
    fun aQueuedLegacyWordThatIsNotPlayableGoesToEditorsWithoutALookup() = runBlocking {
        val id = queue("en", "cat")
        val lookup = FakeLookup(LookupResult.Auto(WordForm.DICTIONARY))

        worker(lookup).runOnce()

        assertEquals(emptyList(), lookup.lookedUp)
        assertEquals(SuggestionStatus.PENDING.name, statusOf(id))
        assertEquals("NOT_PLAYABLE", suggestionColumn(id, WordSuggestionsTable.reviewReason))
        assertTrue("правила языка" in telegram.sent("sendMessage").single().text())
    }

    @Test
    fun aQueuedSuggestionDecidedByStaffIsNotAnnounced() = runBlocking {
        insertPack("en", "1", listOf("x"))
        val id = queue("en", "crane")
        service.decide(id, accept = true, editor = "staff:someone")

        worker(FakeLookup(LookupResult.Auto(WordForm.DICTIONARY))).runOnce()

        assertEquals(emptyList(), telegram.calls)
        assertEquals("POSTED", reviewStateOf(id))
    }
}
