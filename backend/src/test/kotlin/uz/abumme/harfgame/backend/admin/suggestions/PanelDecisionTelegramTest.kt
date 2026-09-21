package uz.abumme.harfgame.backend.admin.suggestions

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import uz.abumme.harfgame.backend.admin.AdminApiException
import uz.abumme.harfgame.backend.admin.access.StaffPrincipal
import uz.abumme.harfgame.backend.admin.insertStaff
import uz.abumme.harfgame.backend.db.WordSuggestionsTable
import uz.abumme.harfgame.backend.dictionary.ReviewReason
import uz.abumme.harfgame.backend.insertPack
import uz.abumme.harfgame.backend.insertPending
import uz.abumme.harfgame.backend.resetSuggestionData
import uz.abumme.harfgame.backend.service.SuggestionServerService
import uz.abumme.harfgame.backend.service.WordPackServerService
import uz.abumme.harfgame.backend.statusOf
import uz.abumme.harfgame.backend.suggestionColumn
import uz.abumme.harfgame.backend.telegram.EditorDirectory
import uz.abumme.harfgame.backend.telegram.FakeTelegramApi
import uz.abumme.harfgame.backend.telegram.PostedMessage
import uz.abumme.harfgame.backend.telegram.TelegramBot
import uz.abumme.harfgame.data.admin.Role
import uz.abumme.harfgame.data.suggestion.SuggestionStatus
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A panel decision replaces the known Telegram decision message's controls with the outcome, best-effort. */
class PanelDecisionTelegramTest {

    private val api = FakeTelegramApi()
    private val suggestions = SuggestionServerService(WordPackServerService())
    private lateinit var staff: StaffPrincipal

    private val postedText = "Новое слово: hello (en)\nОт: Аноним"

    @BeforeTest
    fun setup() {
        resetSuggestionData()
        insertPack("en", "1", listOf("x"))
        staff = StaffPrincipal(insertStaff("aziz", Role.WORDER, listOf("en")), "aziz", "Азиз", Role.WORDER, setOf("en"), "s")
    }

    private fun service(api: FakeTelegramApi? = this.api) = SuggestionsService(
        suggestions,
        TelegramBot("-1001234567890", EditorDirectory(), suggestions, api = api),
        packLanguages = { WordPackServerService().languages() },
    )

    private suspend fun posted(): String {
        val id = insertPending("en", "hello")
        suggestions.markPosted(id, ReviewReason.NOT_FOUND, PostedMessage("-1001234567890", 555, postedText))
        return id
    }

    @Test
    fun anAcceptReplacesTheControlsWithTheOutcomeAndTheStaffMember() = runBlocking {
        val id = posted()

        val decided = service().decide(staff, id, accept = true)

        assertEquals(SuggestionStatus.ACCEPTED, decided.status)
        val edit = api.sent("editMessageText").single()
        assertEquals(-1001234567890L, edit["chat_id"]!!.jsonPrimitive.long)
        assertEquals(555L, edit["message_id"]!!.jsonPrimitive.long)
        assertEquals("$postedText\n\n✅ Принято — Азиз (панель)", edit["text"]!!.jsonPrimitive.content)
        assertNull(edit["reply_markup"])
        // Shown once: a later decision must not overwrite it.
        assertNull(suggestionColumn(id, WordSuggestionsTable.telegramMessageId))
    }

    @Test
    fun aRejectShowsTheRejection() = runBlocking {
        val id = posted()
        service().decide(staff, id, accept = false)
        assertEquals("$postedText\n\n❌ Отклонено — Азиз (панель)", api.sent("editMessageText").single()["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun aFailingEditStillReturnsTheDecisionAndKeepsTheMessageForLater() = runBlocking {
        val id = posted()
        api.respond = { method, _ -> if (method == "editMessageText") throw java.io.IOException("Telegram down") else JsonPrimitive(true) }

        val decided = service().decide(staff, id, accept = true)

        assertEquals(SuggestionStatus.ACCEPTED, decided.status)
        assertEquals(SuggestionStatus.ACCEPTED.name, statusOf(id))
        assertEquals(555L, suggestionColumn(id, WordSuggestionsTable.telegramMessageId))
    }

    @Test
    fun aDecisionOnAnAlreadyDecidedSuggestionStripsSurvivingControls() = runBlocking {
        val id = posted()
        suggestions.decide(id, accept = true, editor = "wiktionary")

        val error = runCatching { service().decide(staff, id, accept = false) }.exceptionOrNull()

        assertEquals("status: already_decided", assertIs<AdminApiException>(error).message)
        assertEquals("$postedText\n\nℹ️ Уже обработано", api.sent("editMessageText").single()["text"]!!.jsonPrimitive.content)
        assertEquals(SuggestionStatus.ACCEPTED.name, statusOf(id))
    }

    @Test
    fun nothingIsEditedWithoutARecordedMessageOrWithoutABot() = runBlocking {
        val unrecorded = insertPending("en", "hello")
        service().decide(staff, unrecorded, accept = true)
        assertEquals(emptyList(), api.calls)

        val recorded = insertPending("en", "world")
        suggestions.markPosted(recorded, ReviewReason.NOT_FOUND, PostedMessage("-100", 9, "text"))
        service(api = null).decide(staff, recorded, accept = true)
        assertTrue(api.calls.isEmpty())
        assertEquals(9L, suggestionColumn(recorded, WordSuggestionsTable.telegramMessageId))
    }

    @Test
    fun editPayloadCarriesNoControlsEvenWhenTelegramAnswersWithAMessage() = runBlocking {
        val id = posted()
        api.respond = { _, _ -> buildJsonObject { put("message_id", 555) } }
        service().decide(staff, id, accept = true)
        assertEquals(setOf("chat_id", "message_id", "text"), api.sent("editMessageText").single().keys)
    }
}
