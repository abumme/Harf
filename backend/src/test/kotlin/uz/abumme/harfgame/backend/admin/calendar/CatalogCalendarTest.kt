package uz.abumme.harfgame.backend.admin.calendar

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import uz.abumme.harfgame.backend.admin.MutableClock
import uz.abumme.harfgame.backend.admin.adminClient
import uz.abumme.harfgame.backend.admin.auditRows
import uz.abumme.harfgame.backend.admin.insertStaff
import uz.abumme.harfgame.backend.admin.jsonBody
import uz.abumme.harfgame.backend.admin.signIn
import uz.abumme.harfgame.backend.admin.withSession
import uz.abumme.harfgame.backend.db.CalendarNoticesTable
import uz.abumme.harfgame.backend.db.DailyWordsTable
import uz.abumme.harfgame.backend.db.StaffAuditLogTable
import uz.abumme.harfgame.data.admin.AdminRoutes
import uz.abumme.harfgame.data.admin.PageDto
import uz.abumme.harfgame.data.admin.Role
import uz.abumme.harfgame.data.admin.answerpool.MarkWordsRequest
import uz.abumme.harfgame.data.admin.audit.AuditActions
import uz.abumme.harfgame.data.admin.audit.AuditEntryDto
import uz.abumme.harfgame.data.admin.calendar.DaySource
import uz.abumme.harfgame.data.admin.calendar.NoticeReason
import uz.abumme.harfgame.data.admin.calendar.PickDayRequest
import uz.abumme.harfgame.data.admin.words.AddWordRequest
import uz.abumme.harfgame.data.admin.words.EditWordRequest
import uz.abumme.harfgame.data.admin.words.WordDto
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Catalog changes by any role reach the calendar in the same transaction, never the other way round in a response. */
class CatalogCalendarTest {
    private val clock = MutableClock(CALENDAR_NOW)

    @BeforeTest
    fun setup() = resetCalendarData()

    /** The WordDto fields: a catalog response carries nothing else. */
    private val wordFields = setOf(
        "id", "lang", "text", "graphemeCount", "status", "source", "suggestionId", "createdBy", "createdAt", "updatedBy",
        "updatedAt", "removedBy", "removedAt", "restored",
    )

    @Test
    fun aWorderRemovingAnAutomaticWordRepicksItsDaySilently() = testApplication {
        insertStaff("boss", Role.ADMIN)
        insertStaff("dilnoza", Role.WORDER, listOf("en"))
        val admin = calendarBackend(clock)
        migrateCalendars(admin.catalog)
        val client = adminClient(admin)
        val worder = client.signIn("dilnoza")
        // A never-used automatic day a week ahead.
        val weekAhead = TODAY.plusDays(7)
        val target = calendarRows("en").first { it[DailyWordsTable.day] >= weekAhead && !it[DailyWordsTable.isRepeat] }
        val day = target[DailyWordsTable.day]
        val word = target[DailyWordsTable.text]
        val version = pack("en").version

        val response = client.post(AdminRoutes.wordRemove(target[DailyWordsTable.wordId]!!)) { withSession(worder) }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(Json.parseToJsonElement(body).jsonObject.keys.all { it in wordFields }, body)
        assertFalse(listOf("daily", "calendar", "eligible", "schedule").any { it in body.lowercase() }, body)

        val after = dayRow("en", day)!!
        assertNotEquals(word, after[DailyWordsTable.text], "the day got a new automatic pick")
        assertEquals(DaySource.AUTO.name, after[DailyWordsTable.daySource])
        assertTrue(calendarRows("en").none { it[DailyWordsTable.day] >= TODAY.plusDays(2) && it[DailyWordsTable.text] == word })
        assertNotEquals(version, pack("en").version)
        assertEquals(after[DailyWordsTable.text], pack("en").wordOn(day))
        assertTrue(noticeRows().isEmpty(), "automatic re-picks raise no notice")
        assertFalse(word in pack("en").answers)
    }

    @Test
    fun removingAManuallyPickedWordReplacesThePickWithANoticeForAdmins() = testApplication {
        insertStaff("boss", Role.ADMIN)
        insertStaff("dilnoza", Role.WORDER, listOf("en"))
        val admin = calendarBackend(clock)
        migrateCalendars(admin.catalog)
        val client = adminClient(admin)
        val boss = client.signIn("boss")
        val worder = client.signIn("dilnoza")
        // Pick a word that is not used yet for ten days ahead, then let the WORDER remove it.
        val day = TODAY.plusDays(10)
        val usedWords = calendarRows("en").filter { it[DailyWordsTable.day] <= TODAY.plusDays(1) && it[DailyWordsTable.day] >= TODAY }
            .map { it[DailyWordsTable.text] }.toSet()
        val picked = EN_POOL.first { it !in usedWords }
        val pickResponse = client.put(AdminRoutes.calendarDay("en", day.toString())) {
            withSession(boss)
            jsonBody(PickDayRequest(wordId = wordId("en", picked)))
        }
        assertEquals(HttpStatusCode.OK, pickResponse.status, pickResponse.bodyAsText())

        val removed = client.post(AdminRoutes.wordRemove(wordId("en", picked))) { withSession(worder) }

        assertEquals(HttpStatusCode.OK, removed.status)
        assertFalse("calendar" in removed.bodyAsText().lowercase())
        val notice = noticeRows().single()
        assertEquals(day, notice[CalendarNoticesTable.day])
        assertEquals(picked, notice[CalendarNoticesTable.wordText])
        assertEquals(NoticeReason.REMOVED.name, notice[CalendarNoticesTable.reason])
        assertEquals("en", notice[CalendarNoticesTable.calendar])
        val row = dayRow("en", day)!!
        assertEquals(DaySource.AUTO.name, row[DailyWordsTable.daySource])
        assertNotEquals(picked, row[DailyWordsTable.text])
        val replaced = auditRows().single { it[StaffAuditLogTable.action] == AuditActions.DAILY_MANUAL_PICK_REPLACED }
        assertEquals("SYSTEM", replaced[StaffAuditLogTable.actorKind])

        // The WORDER's own activity never shows daily-word entries; the ADMIN's log does.
        val own = client.get(AdminRoutes.AUDIT) { withSession(worder) }.body<PageDto<AuditEntryDto>>()
        assertTrue(own.items.none { it.action.startsWith(AuditActions.DAILY_PREFIX) })
        assertTrue(own.items.any { it.action == AuditActions.WORD_REMOVED })
        val forbiddenPeek = client.get("${AdminRoutes.AUDIT}?action=${AuditActions.DAILY_MANUAL_PICK_REPLACED}") { withSession(worder) }
        assertTrue(forbiddenPeek.body<PageDto<AuditEntryDto>>().items.isEmpty())
        val all = client.get(AdminRoutes.AUDIT) { withSession(boss) }.body<PageDto<AuditEntryDto>>()
        assertTrue(all.items.any { it.action == AuditActions.DAILY_MANUAL_PICK_REPLACED })
    }

    @Test
    fun editingYesterdaysWordKeepsThePlayedTextWhileAManualPickFollowsTheEdit() = testApplication {
        insertStaff("boss", Role.ADMIN)
        val admin = calendarBackend(clock)
        migrateCalendars(admin.catalog)
        val client = adminClient(admin)
        val boss = client.signIn("boss")
        val yesterday = TODAY.minusDays(1)
        val played = dayRow("en", yesterday)!![DailyWordsTable.text]
        val publishedYesterday = pack("en").wordOn(yesterday)

        val edited = client.patch(AdminRoutes.word(wordId("en", played))) { withSession(boss); jsonBody(EditWordRequest("zzzzy")) }

        assertEquals(HttpStatusCode.OK, edited.status, edited.bodyAsText())
        assertEquals(played, dayRow("en", yesterday)!![DailyWordsTable.text])
        assertEquals(publishedYesterday, pack("en").wordOn(yesterday))
        assertEquals(played, pack("en").wordOn(yesterday))
        // Today's word is also unchanged, and still accepted as a guess by the app (schedule words are guesses).
        val todayWord = dayRow("en", TODAY)!![DailyWordsTable.text]
        assertEquals(todayWord, pack("en").wordOn(TODAY))

        // A manual pick follows its word's corrected spelling.
        val day = TODAY.plusDays(5)
        val used = calendarRows("en").filter { it[DailyWordsTable.day] in TODAY..TODAY.plusDays(1) }.map { it[DailyWordsTable.text] }.toSet()
        val word = client.post(AdminRoutes.WORDS) { withSession(boss); jsonBody(AddWordRequest("en", "tulip")) }.body<WordDto>()
        client.post(AdminRoutes.answerPoolWords("en")) {
            withSession(boss)
            jsonBody(MarkWordsRequest(wordIds = listOf(word.id)))
        }
        assertFalse("tulip" in used)
        val pick = client.put(AdminRoutes.calendarDay("en", day.toString())) { withSession(boss); jsonBody(PickDayRequest(wordId = word.id)) }
        assertEquals(HttpStatusCode.OK, pick.status, pick.bodyAsText())
        client.patch(AdminRoutes.word(word.id)) { withSession(boss); jsonBody(EditWordRequest("tulips")) }
        val row = dayRow("en", day)!!
        assertEquals(DaySource.MANUAL.name, row[DailyWordsTable.daySource])
        assertEquals("tulips", row[DailyWordsTable.text])
        assertEquals("tulips", pack("en").wordOn(day))
    }
}
