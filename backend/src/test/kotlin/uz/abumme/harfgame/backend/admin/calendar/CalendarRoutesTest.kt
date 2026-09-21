package uz.abumme.harfgame.backend.admin.calendar

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.jsonPrimitive
import uz.abumme.harfgame.backend.admin.MutableClock
import uz.abumme.harfgame.backend.admin.StaffSession
import uz.abumme.harfgame.backend.admin.adminClient
import uz.abumme.harfgame.backend.admin.auditRows
import uz.abumme.harfgame.backend.admin.error
import uz.abumme.harfgame.backend.admin.insertStaff
import uz.abumme.harfgame.backend.admin.jsonBody
import uz.abumme.harfgame.backend.admin.signIn
import uz.abumme.harfgame.backend.admin.withSession
import uz.abumme.harfgame.backend.db.DailyWordsTable
import uz.abumme.harfgame.backend.db.StaffAuditLogTable
import uz.abumme.harfgame.data.admin.AdminRoutes
import uz.abumme.harfgame.data.admin.PageDto
import uz.abumme.harfgame.data.admin.Role
import uz.abumme.harfgame.data.admin.answerpool.MarkWordsRequest
import uz.abumme.harfgame.data.admin.audit.AuditActions
import uz.abumme.harfgame.data.admin.audit.AuditEntryDto
import uz.abumme.harfgame.data.admin.calendar.CalendarCandidateDto
import uz.abumme.harfgame.data.admin.calendar.CalendarNoticeDto
import uz.abumme.harfgame.data.admin.calendar.DayDto
import uz.abumme.harfgame.data.admin.calendar.DaySource
import uz.abumme.harfgame.data.admin.calendar.NoticeReason
import uz.abumme.harfgame.data.admin.calendar.PickDayRequest
import uz.abumme.harfgame.data.api.ApiErrorResponse
import java.time.Instant
import java.time.LocalDate
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalendarRoutesTest {
    private val clock = MutableClock(CALENDAR_NOW)

    @BeforeTest
    fun setup() = resetCalendarData()

    private suspend fun HttpClient.pick(session: StaffSession, calendar: String, day: LocalDate, request: PickDayRequest): HttpResponse =
        put(AdminRoutes.calendarDay(calendar, day.toString())) { withSession(session); jsonBody(request) }

    private fun textOf(calendar: String, day: LocalDate) = dayRow(calendar, day)!![DailyWordsTable.text]

    /** A pool word neither used nor manually picked (today and tomorrow are the counted days after migration). */
    private fun freeWord(): String {
        val used = calendarRows("en").filter { it[DailyWordsTable.day] in TODAY..TODAY.plusDays(1) }.map { it[DailyWordsTable.text] }.toSet()
        return EN_POOL.first { it !in used }
    }

    @Test
    fun pickingTomorrowOrTooFarAheadIsRefusedAndChangesNothing() = testApplication {
        insertStaff("boss", Role.ADMIN)
        val admin = calendarBackend(clock)
        migrateCalendars(admin.catalog)
        val client = adminClient(admin)
        val boss = client.signIn("boss")
        val request = PickDayRequest(wordId = wordId("en", freeWord()))
        val tomorrow = textOf("en", TODAY.plusDays(1))
        val version = pack("en").version

        assertEquals(ApiErrorResponse("validation_failed", "day: locked"), client.pick(boss, "en", TODAY.plusDays(1), request).error())
        assertEquals(ApiErrorResponse("validation_failed", "day: locked"), client.pick(boss, "en", TODAY.minusDays(3), request).error())
        assertEquals(ApiErrorResponse("validation_failed", "day: too_far"), client.pick(boss, "en", TODAY.plusDays(366), request).error())
        assertEquals(ApiErrorResponse("validation_failed", "day: invalid"), client.put(AdminRoutes.calendarDay("en", "tomorrow")) { withSession(boss); jsonBody(request) }.error())
        assertEquals(ApiErrorResponse("validation_failed", "word: not_eligible"), client.pick(boss, "en", TODAY.plusDays(4), PickDayRequest(wordId = wordId("en", "tiger"))).error())
        assertEquals(ApiErrorResponse("validation_failed", "day: locked"), client.delete(AdminRoutes.calendarDay("en", TODAY.toString())) { withSession(boss) }.error())

        assertEquals(tomorrow, textOf("en", TODAY.plusDays(1)))
        assertEquals(version, pack("en").version)
        assertTrue(auditRows().none { it[StaffAuditLogTable.action].startsWith(AuditActions.DAILY_PREFIX) })

        // The farthest pickable day is a year ahead; it stays manual beyond the horizon.
        val far = client.pick(boss, "en", TODAY.plusDays(365), request)
        assertEquals(HttpStatusCode.OK, far.status, far.bodyAsText())
        assertEquals(DaySource.MANUAL, far.body<DayDto>().source)
    }

    @Test
    fun aUsedWordIsRefusedWithItsDaysAndAWordPickedElsewhereWithItsDay() = testApplication {
        insertStaff("boss", Role.ADMIN)
        // History counted from well before the calendar's first run: legacy days are used days.
        val admin = calendarBackend(clock, DailySettings(historyStart = LocalDate.parse("2026-09-01"), random = Random(5)))
        migrateCalendars(admin.catalog)
        val client = adminClient(admin)
        val boss = client.signIn("boss")
        val threeDaysAgo = textOf("en", TODAY.minusDays(3))
        val usedOn = calendarRows("en")
            .filter { it[DailyWordsTable.day] in LocalDate.parse("2026-09-01")..TODAY.plusDays(1) && it[DailyWordsTable.text] == threeDaysAgo }
            .map { it[DailyWordsTable.day].toString() }

        val used = client.pick(boss, "en", TODAY.plusDays(6), PickDayRequest(wordId = wordId("en", threeDaysAgo)))

        assertEquals(HttpStatusCode.Conflict, used.status)
        assertEquals(ApiErrorResponse("conflict", "word: used ${usedOn.joinToString(",")}"), used.error())
        assertTrue(TODAY.minusDays(3).toString() in usedOn)
    }

    @Test
    fun pickingMovesAnAutomaticWordAndRefusesASecondManualDay() = testApplication {
        insertStaff("boss", Role.ADMIN)
        val admin = calendarBackend(clock)
        migrateCalendars(admin.catalog)
        val client = adminClient(admin)
        val boss = client.signIn("boss")
        // A never-used word scheduled automatically on a later day.
        val laterRow = calendarRows("en").first { it[DailyWordsTable.day] >= TODAY.plusDays(5) && !it[DailyWordsTable.isRepeat] }
        val laterDay = laterRow[DailyWordsTable.day]
        val word = laterRow[DailyWordsTable.text]
        val pickDay = TODAY.plusDays(2)
        val previous = textOf("en", pickDay)
        val version = pack("en").version

        val response = client.pick(boss, "en", pickDay, PickDayRequest(wordId = wordId("en", word)))

        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        val day = response.body<DayDto>()
        assertEquals(DayDto(pickDay.toString(), word, null, DaySource.MANUAL, false, null, false, wordId("en", word), null, day.pickedBy, clock.now.toEpochMilli()), day)
        assertEquals("boss", day.pickedBy?.username)
        assertNotEquals(word, textOf("en", laterDay), "the other day got a different automatic word")
        assertEquals(DaySource.AUTO.name, dayRow("en", laterDay)!![DailyWordsTable.daySource])
        assertEquals(word, pack("en").wordOn(pickDay))
        assertNotEquals(version, pack("en").version)
        val entry = auditRows().single { it[StaffAuditLogTable.action] == AuditActions.DAILY_WORD_PICKED }
        assertEquals("en:$pickDay", entry[StaffAuditLogTable.targetId])
        assertEquals("en", entry[StaffAuditLogTable.lang])
        assertTrue("\"$word\"" in entry[StaffAuditLogTable.details]!! && "\"$previous\"" in entry[StaffAuditLogTable.details]!!)

        // Picking it again for the same day changes nothing; for another day is refused, naming the day.
        assertEquals(HttpStatusCode.OK, client.pick(boss, "en", pickDay, PickDayRequest(wordId = wordId("en", word))).status)
        assertEquals(1, auditRows().count { it[StaffAuditLogTable.action] == AuditActions.DAILY_WORD_PICKED })
        val elsewhere = client.pick(boss, "en", TODAY.plusDays(9), PickDayRequest(wordId = wordId("en", word)))
        assertEquals(ApiErrorResponse("conflict", "word: picked $pickDay"), elsewhere.error())

        // Unpicking returns the day to automatic; an automatic day cannot be unpicked.
        val unpicked = client.delete(AdminRoutes.calendarDay("en", pickDay.toString())) { withSession(boss) }
        assertEquals(HttpStatusCode.OK, unpicked.status, unpicked.bodyAsText())
        val automatic = unpicked.body<DayDto>()
        assertEquals(DaySource.AUTO, automatic.source)
        assertNull(automatic.pickedBy)
        assertEquals(ApiErrorResponse("validation_failed", "day: not_manual"), client.delete(AdminRoutes.calendarDay("en", pickDay.toString())) { withSession(boss) }.error())
        val unpickEntry = auditRows().single { it[StaffAuditLogTable.action] == AuditActions.DAILY_WORD_UNPICKED }
        assertEquals("en:$pickDay", unpickEntry[StaffAuditLogTable.targetId])
        // The word is free again: it can be picked for another day.
        assertEquals(HttpStatusCode.OK, client.pick(boss, "en", TODAY.plusDays(9), PickDayRequest(wordId = wordId("en", word))).status)
    }

    @Test
    fun theLockFollowsTheCalendarsOwnMidnightAndSparesTomorrow() = testApplication {
        insertStaff("boss", Role.ADMIN)
        clock.now = Instant.parse("2026-09-17T20:59:59Z") // 23:59:59 in Moscow
        val admin = calendarBackend(clock)
        migrateCalendars(admin.catalog)
        val client = adminClient(admin)
        val boss = client.signIn("boss")
        val target = LocalDate.parse("2026-09-19")
        val words = calendarRows("ru").filter { it[DailyWordsTable.day] in TODAY..TODAY.plusDays(1) }.map { it[DailyWordsTable.text] }.toSet()
        val (first, second) = listOf("слово", "книга", "город", "время", "стена", "песня").filter { it !in words }.let { it[0] to it[1] }
        val before = pack("ru")

        val accepted = client.pick(boss, "ru", target, PickDayRequest(wordId = wordId("ru", first)))

        assertEquals(HttpStatusCode.OK, accepted.status, accepted.bodyAsText())
        val after = pack("ru")
        for (day in listOf(TODAY, TODAY.plusDays(1))) assertEquals(before.wordOn(day), after.wordOn(day), "late-night change spares $day")
        assertEquals(first, after.wordOn(target))

        clock.now = Instant.parse("2026-09-17T21:00:01Z") // 00:00:01 on the 18th in Moscow
        val refused = client.pick(boss, "ru", target, PickDayRequest(wordId = wordId("ru", second)))
        assertEquals(ApiErrorResponse("validation_failed", "day: locked"), refused.error())
        assertEquals(first, textOf("ru", target))
    }

    @Test
    fun theRangeFiltersRepeatsAndManualDaysAndCandidatesShowTheirUsage() = testApplication {
        insertStaff("boss", Role.ADMIN)
        val admin = calendarBackend(clock)
        migrateCalendars(admin.catalog)
        val client = adminClient(admin)
        val boss = client.signIn("boss")
        val from = TODAY.plusDays(10)
        val to = TODAY.plusDays(30)
        client.pick(boss, "en", TODAY.plusDays(3), PickDayRequest(wordId = wordId("en", freeWord())))

        val repeats = client.get("${AdminRoutes.calendar("en")}?from=$from&to=$to&repeatsOnly=true&size=200") { withSession(boss) }.body<PageDto<DayDto>>()
        val expected = calendarRows("en").filter { it[DailyWordsTable.day] in from..to && it[DailyWordsTable.isRepeat] }.map { it[DailyWordsTable.day].toString() }
        assertTrue(expected.isNotEmpty())
        assertEquals(expected, repeats.items.map { it.day })
        assertTrue(repeats.items.all { it.isRepeat && it.lastUsed != null && it.lastUsed!! < it.day && !it.locked })

        val manual = client.get("${AdminRoutes.calendar("en")}?source=MANUAL&to=${TODAY.plusDays(60)}") { withSession(boss) }.body<PageDto<DayDto>>()
        assertEquals(listOf(TODAY.plusDays(3).toString()), manual.items.map { it.day })
        val month = client.get("${AdminRoutes.calendar("en")}?from=2026-09-01&to=2026-09-30&size=42") { withSession(boss) }.body<PageDto<DayDto>>()
        assertEquals(30, month.items.size)
        assertEquals(listOf(true, true), month.items.filter { it.day in listOf(TODAY.toString(), TODAY.plusDays(1).toString()) }.map { it.locked })
        assertEquals(DaySource.LEGACY, month.items.first().source)

        val candidates = client.get("${AdminRoutes.calendarCandidates("en")}?day=${TODAY.plusDays(20)}") { withSession(boss) }.body<PageDto<CalendarCandidateDto>>()
        assertEquals(EN_POOL.size.toLong(), candidates.total)
        val todays = candidates.items.single { it.text == textOf("en", TODAY) }
        assertTrue(!todays.neverUsed && todays.lastUsed != null)
        assertTrue(candidates.items.first().neverUsed, "never-used words come first")
        assertTrue(candidates.items.all { it.scheduledOn == null || it.scheduledOn!!.day >= TODAY.plusDays(2).toString() })
        assertTrue(candidates.items.any { it.scheduledOn?.source == DaySource.MANUAL })
        val search = client.get("${AdminRoutes.calendarCandidates("en")}?q=HOU") { withSession(boss) }.body<PageDto<CalendarCandidateDto>>()
        assertEquals(listOf("house"), search.items.map { it.text })
    }

    @Test
    fun noticesAreListedAndDismissedOnceByAnAdmin() = testApplication {
        insertStaff("boss", Role.ADMIN)
        val admin = calendarBackend(clock)
        migrateCalendars(admin.catalog)
        val client = adminClient(admin)
        val boss = client.signIn("boss")
        val word = freeWord()
        val day = TODAY.plusDays(8)
        client.pick(boss, "en", day, PickDayRequest(wordId = wordId("en", word)))
        // Taking the word out of the pool replaces the manual pick.
        client.delete(AdminRoutes.answerPoolWord("en", wordId("en", word))) { withSession(boss) }

        val notices = client.get(AdminRoutes.CALENDAR_NOTICES) { withSession(boss) }.body<List<CalendarNoticeDto>>()
        val notice = notices.single()
        assertEquals(day.toString(), notice.day)
        assertEquals(word, notice.wordText)
        assertEquals(NoticeReason.INELIGIBLE, notice.reason)

        assertEquals(HttpStatusCode.NoContent, client.post(AdminRoutes.calendarNoticeDismiss(notice.id)) { withSession(boss) }.status)
        assertEquals(HttpStatusCode.NoContent, client.post(AdminRoutes.calendarNoticeDismiss(notice.id)) { withSession(boss) }.status)
        assertEquals(emptyList(), client.get(AdminRoutes.CALENDAR_NOTICES) { withSession(boss) }.body<List<CalendarNoticeDto>>())
        val dismissed = client.get("${AdminRoutes.CALENDAR_NOTICES}?includeDismissed=true") { withSession(boss) }.body<List<CalendarNoticeDto>>().single()
        assertEquals("boss", dismissed.dismissedBy?.username)
        assertEquals(1, auditRows().count { it[StaffAuditLogTable.action] == AuditActions.DAILY_NOTICE_DISMISSED })
        assertEquals(HttpStatusCode.NotFound, client.post(AdminRoutes.calendarNoticeDismiss("nope")) { withSession(boss) }.status)

        val log = client.get("${AdminRoutes.AUDIT}?action=${AuditActions.DAILY_WORD_PICKED}") { withSession(boss) }.body<PageDto<AuditEntryDto>>()
        assertEquals(word, log.items.single().details!!["word"]!!.jsonPrimitive.content)
    }

    @Test
    fun uzbekPicksAreByPairAndPublishBothScripts() = testApplication {
        insertStaff("boss", Role.ADMIN)
        val admin = calendarBackend(clock)
        migrateCalendars(admin.catalog)
        val client = adminClient(admin)
        val boss = client.signIn("boss")
        val candidates = client.get(AdminRoutes.calendarCandidates("uz")) { withSession(boss) }.body<PageDto<CalendarCandidateDto>>()
        val pair = candidates.items.first()
        val day = TODAY.plusDays(12)

        val response = client.pick(boss, "uz", day, PickDayRequest(pairId = pair.pairId))

        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        assertEquals(pair.text, pack("uz-latn").wordOn(day))
        assertEquals(pair.textCyrl, pack("uz-cyrl").wordOn(day))
        assertEquals(ApiErrorResponse("validation_failed", "word: required"), client.pick(boss, "uz", day, PickDayRequest(wordId = "x")).error())
    }

    @Test
    fun everyCalendarRouteIsForbiddenForAWorderAndUnauthorizedWithoutASession() = testApplication {
        insertStaff("dilnoza", Role.WORDER, listOf("en", "ru", "kk", "uz-latn", "uz-cyrl"))
        val admin = calendarBackend(clock)
        migrateCalendars(admin.catalog)
        val client = adminClient(admin)
        val worder = client.signIn("dilnoza")
        val snapshot = calendarSnapshot("en")
        val day = TODAY.plusDays(5).toString()

        val attempts: List<suspend (StaffSession?) -> HttpResponse> = listOf(
            { s -> client.get(AdminRoutes.calendar("en")) { s?.let { withSession(it) } } },
            { s -> client.get(AdminRoutes.calendarCandidates("en")) { s?.let { withSession(it) } } },
            { s -> client.put(AdminRoutes.calendarDay("en", day)) { s?.let { withSession(it) }; jsonBody(PickDayRequest(wordId = wordId("en", "apple"))) } },
            { s -> client.delete(AdminRoutes.calendarDay("en", day)) { s?.let { withSession(it) } } },
            { s -> client.get(AdminRoutes.CALENDAR_NOTICES) { s?.let { withSession(it) } } },
            { s -> client.post(AdminRoutes.calendarNoticeDismiss("any")) { s?.let { withSession(it) } } },
        )
        for (attempt in attempts) {
            val forbidden = attempt(worder)
            assertEquals(HttpStatusCode.Forbidden, forbidden.status)
            assertEquals("forbidden", forbidden.error().error)
            assertEquals(HttpStatusCode.Unauthorized, attempt(null).status)
        }
        assertEquals(snapshot, calendarSnapshot("en"))
        // The calendar tells nothing about it to a WORDER: no daily-word actions in their activity either.
        val own = client.get(AdminRoutes.AUDIT) { withSession(worder) }.body<PageDto<AuditEntryDto>>()
        assertTrue(own.items.none { it.action.startsWith(AuditActions.DAILY_PREFIX) })
        // An unused pool word stays unused.
        client.post(AdminRoutes.answerPoolWords("en")) { withSession(worder); jsonBody(MarkWordsRequest(texts = listOf("tiger"))) }
        assertEquals(EN_POOL.sorted(), pack("en").answers)
    }
}
