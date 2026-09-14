package uz.abumme.harfgame.backend.review

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.SuggestionReportsTable
import uz.abumme.harfgame.backend.insertPack
import uz.abumme.harfgame.backend.insertSuggestion
import uz.abumme.harfgame.backend.resetSuggestionData
import uz.abumme.harfgame.backend.service.SuggestionServerService
import uz.abumme.harfgame.backend.service.WordPackServerService
import uz.abumme.harfgame.backend.telegram.FakeTelegramApi
import uz.abumme.harfgame.backend.telegram.TelegramBot
import uz.abumme.harfgame.data.suggestion.SuggestionStatus
import java.time.Instant
import java.time.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DailyReportSchedulerTest {

    @BeforeTest
    fun setup() = resetSuggestionData()

    private val telegram = FakeTelegramApi()
    private val service = SuggestionServerService(WordPackServerService())

    private fun scheduler() = DailyReportScheduler(
        suggestions = service,
        wordPacks = WordPackServerService(),
        telegram = TelegramBot(
            editorChatId = "chat", editorIds = setOf(42L), suggestions = service, topics = mapOf("ru" to 3), api = telegram,
        ),
    )

    private val sep14 = LocalDate.of(2026, 9, 14)
    private val duringSep14 = Instant.parse("2026-09-14T10:00:00Z") // 15:00 on Sep 14, Asia/Tashkent
    private val justAfterMidnight = Instant.parse("2026-09-14T19:00:30Z") // 00:00:30 on Sep 15, Asia/Tashkent

    private fun recorded(lang: String, day: LocalDate): Boolean = transaction(DatabaseFactory.init()) {
        SuggestionReportsTable.selectAll()
            .where { (SuggestionReportsTable.lang eq lang) and (SuggestionReportsTable.day eq day) }
            .count() == 1L
    }

    private fun JsonObject.text() = this["text"]!!.jsonPrimitive.content
    private fun JsonObject.threadId() = this["message_thread_id"]?.jsonPrimitive?.int

    @Test
    fun eachLanguageGetsItsReportOnceInItsTopic() = runBlocking {
        insertPack("ru", "1", listOf("книга"))
        insertPack("en", "1", listOf("crane"))
        insertSuggestion("ru", "книги", SuggestionStatus.ACCEPTED, decidedVia = "AUTO", decidedAt = duringSep14)
        insertSuggestion("en", "zqxta", reviewState = "POSTED") // still pending

        scheduler().sendDue(justAfterMidnight)
        scheduler().sendDue(justAfterMidnight.plusSeconds(60))

        val sent = telegram.sent("sendMessage")
        assertEquals(2, sent.size)
        val ru = sent.single { it.threadId() == 3 }
        assertTrue("14.09" in ru.text() && "книги" in ru.text(), ru.text())
        val en = sent.single { it.threadId() == null }
        assertTrue("Ждут решения: 1" in en.text(), en.text())
        assertTrue(recorded("ru", sep14) && recorded("en", sep14))
    }

    @Test
    fun quietDayIsRecordedWithoutSendingAndNotRevisited() = runBlocking {
        insertPack("kk", "1", listOf("кітап"))

        scheduler().sendDue(justAfterMidnight)
        assertEquals(emptyList(), telegram.sent("sendMessage"))
        assertTrue(recorded("kk", sep14))

        insertSuggestion("kk", "қалам", reviewState = "POSTED") // arrives after Sep 14 was already settled
        scheduler().sendDue(justAfterMidnight.plusSeconds(300))
        assertEquals(emptyList(), telegram.sent("sendMessage"))
    }

    @Test
    fun failedSendIsRetriedOnTheNextRun() = runBlocking {
        insertPack("ru", "1", listOf("книга"))
        insertSuggestion("ru", "бырка", SuggestionStatus.REJECTED, decidedVia = "EDITOR", decidedAt = duringSep14)

        telegram.respond = { _, _ -> null }
        scheduler().sendDue(justAfterMidnight)
        assertFalse(recorded("ru", sep14))

        telegram.respond = { _, _ -> buildJsonObject { put("message_id", 5) } }
        scheduler().sendDue(justAfterMidnight.plusSeconds(60))
        scheduler().sendDue(justAfterMidnight.plusSeconds(120))

        assertEquals(2, telegram.sent("sendMessage").size) // one unconfirmed attempt, one delivered
        assertTrue(recorded("ru", sep14))
    }

    @Test
    fun reportCoversOnlyTheDayThatEnded() = runBlocking {
        insertPack("ru", "1", listOf("книга"))
        insertSuggestion("ru", "вчера", SuggestionStatus.ACCEPTED, decidedVia = "EDITOR", decidedAt = duringSep14)
        insertSuggestion(
            "ru", "сегод", SuggestionStatus.ACCEPTED, decidedVia = "EDITOR",
            decidedAt = Instant.parse("2026-09-14T19:00:10Z"), // 00:00:10 on Sep 15: belongs to tomorrow's report
        )

        scheduler().sendDue(justAfterMidnight)

        val text = telegram.sent("sendMessage").single().text()
        assertTrue("вчера" in text && "сегод" !in text, text)
    }
}
