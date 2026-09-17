package uz.abumme.harfgame.admin

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import uz.abumme.harfgame.admin.answerpool.MarkGroup
import uz.abumme.harfgame.admin.answerpool.PairForm
import uz.abumme.harfgame.admin.answerpool.PoolQueryState
import uz.abumme.harfgame.admin.answerpool.groupMarkResults
import uz.abumme.harfgame.admin.api.AdminApi
import uz.abumme.harfgame.admin.api.CredentialsMode
import uz.abumme.harfgame.admin.api.HttpRequest
import uz.abumme.harfgame.admin.api.HttpResponse
import uz.abumme.harfgame.admin.api.HttpTransport
import uz.abumme.harfgame.admin.calendar.CalendarTableState
import uz.abumme.harfgame.admin.calendar.CalendarViewState
import uz.abumme.harfgame.admin.calendar.DayLock
import uz.abumme.harfgame.admin.calendar.calendarErrorMessage
import uz.abumme.harfgame.admin.calendar.candidateUsage
import uz.abumme.harfgame.admin.calendar.lockOf
import uz.abumme.harfgame.admin.calendar.monthGrid
import uz.abumme.harfgame.admin.calendar.todayIn
import uz.abumme.harfgame.data.admin.answerpool.MarkItemResultDto
import uz.abumme.harfgame.data.admin.answerpool.MarkOutcome
import uz.abumme.harfgame.data.admin.answerpool.PoolCandidateDto
import uz.abumme.harfgame.data.admin.calendar.CalendarCandidateDto
import uz.abumme.harfgame.data.admin.calendar.DaySource
import uz.abumme.harfgame.data.admin.calendar.PickDayRequest
import uz.abumme.harfgame.data.admin.calendar.ScheduledOnDto
import uz.abumme.harfgame.data.admin.words.WordReasons
import uz.abumme.harfgame.data.api.ApiResult
import kotlin.js.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalendarStateTest {

    private fun day(iso: String) = LocalDate.parse(iso)
    private fun millis(iso: String) = Date.parse(iso)

    // --- month grid ---

    @Test
    fun aMonthStartingOnSundayGetsSixLeadingDaysAndWholeWeeks() {
        val november = YearMonth(2026, 11) // 1 November 2026 is a Sunday
        val weeks = monthGrid(november, today = day("2026-11-10"))

        assertEquals(6, weeks.size)
        assertTrue(weeks.all { it.size == 7 })
        assertEquals(day("2026-10-26"), weeks.first().first().date)
        assertEquals(DayOfWeek.MONDAY, weeks.first().first().date.dayOfWeek)
        assertEquals(6, weeks.first().count { !it.inMonth })
        assertEquals(day("2026-11-01"), weeks.first().last().date)
        assertTrue(weeks.first().last().inMonth)
        assertEquals(day("2026-12-06"), weeks.last().last().date)
        assertEquals(30, weeks.flatten().count { it.inMonth })
    }

    @Test
    fun aMonthStartingOnMondayHasNoLeadingDays() {
        val june = YearMonth(2026, 6) // 1 June 2026 is a Monday
        val weeks = monthGrid(june, today = day("2026-06-15"))
        assertEquals(day("2026-06-01"), weeks.first().first().date)
        assertTrue(weeks.first().all { it.inMonth })
        assertEquals(day("2026-07-05"), weeks.last().last().date)
    }

    @Test
    fun locksMarkPastTodayAndTomorrow() {
        val today = day("2026-09-17")
        assertEquals(DayLock.PAST, lockOf(day("2026-09-16"), today))
        assertEquals(DayLock.TODAY, lockOf(today, today))
        assertEquals(DayLock.TOMORROW, lockOf(day("2026-09-18"), today))
        assertEquals(DayLock.OPEN, lockOf(day("2026-09-19"), today))
        val cells = monthGrid(YearMonth(2026, 9), today).flatten().associateBy { it.date }
        assertTrue(cells.getValue(day("2026-09-18")).lock.locked)
        assertFalse(cells.getValue(day("2026-09-19")).lock.locked)
    }

    @Test
    fun theLockTurnsAtEachCalendarsOwnMidnight() {
        // 23:59:59 and 00:00:01 local time: Moscow is UTC+3, Almaty and Tashkent UTC+5.
        val midnights = mapOf(
            "ru" to ("2026-09-17T20:59:59Z" to "2026-09-17T21:00:01Z"),
            "kk" to ("2026-09-17T18:59:59Z" to "2026-09-17T19:00:01Z"),
            "uz" to ("2026-09-17T18:59:59Z" to "2026-09-17T19:00:01Z"),
            "en" to ("2026-09-17T18:59:59Z" to "2026-09-17T19:00:01Z"),
        )
        val dayAfterTomorrow = day("2026-09-19")
        for ((calendar, instants) in midnights) {
            val before = todayIn(calendar, millis(instants.first))
            val after = todayIn(calendar, millis(instants.second))
            assertEquals(day("2026-09-17"), before, calendar)
            assertEquals(day("2026-09-18"), after, calendar)
            assertEquals(DayLock.OPEN, lockOf(dayAfterTomorrow, before), "$calendar before midnight")
            assertEquals(DayLock.TOMORROW, lockOf(dayAfterTomorrow, after), "$calendar after midnight")
        }
        // The same instant is still the 17th in Moscow but the 18th in Asia.
        val at = millis("2026-09-17T20:30:00Z")
        assertEquals(day("2026-09-17"), todayIn("ru", at))
        assertEquals(day("2026-09-18"), todayIn("kk", at))
    }

    // --- view and filter state ---

    @Test
    fun theMonthViewLivesInTheUrlAndAsksForEveryShownDay() {
        val today = day("2026-09-17")
        val view = CalendarViewState.fromParams(mapOf("calendar" to "kk", "month" to "2026-11"), today)
        assertEquals(CalendarViewState("kk", YearMonth(2026, 11)), view)
        assertEquals("?calendar=kk&month=2026-11", view.toRouteQuery())
        assertEquals("?from=2026-10-26&to=2026-12-06&size=200", view.toApiQuery(today))
        assertEquals(YearMonth(2026, 12), view.next().month)
        assertEquals(YearMonth(2026, 10), view.previous().month)

        val fallback = CalendarViewState.fromParams(mapOf("calendar" to "de", "month" to "soon"), today)
        assertEquals(CalendarViewState("en", YearMonth(2026, 9)), fallback)
    }

    @Test
    fun tableFiltersRoundTripThroughTheUrl() {
        val state = CalendarTableState("uz")
            .withFrom("2026-10-01")
            .withTo("2026-10-31")
            .withSource(DaySource.AUTO.name)
            .withRepeatsOnly(true)
            .withPage(2)

        val route = state.toRouteQuery()
        assertEquals("?calendar=uz&from=2026-10-01&to=2026-10-31&source=AUTO&repeats=1&page=3", route)
        val params = route.removePrefix("?").split('&').associate { it.substringBefore('=') to it.substringAfter('=') }
        assertEquals(state, CalendarTableState.fromParams(params))
        assertEquals("?from=2026-10-01&to=2026-10-31&source=AUTO&repeatsOnly=true&page=2&size=50", state.toApiQuery(50))
        assertTrue(state.hasFilters)

        // A filter change starts from the first page; clearing keeps the calendar.
        assertEquals(0, state.withSource("").page)
        assertEquals(CalendarTableState("uz"), state.withoutFilters())
        assertEquals("?page=0&size=50", CalendarTableState("en").toApiQuery(50))
        assertEquals("?calendar=en", CalendarTableState("en").toRouteQuery())
        assertEquals(CalendarTableState("en"), CalendarTableState.fromParams(mapOf("calendar" to "x", "from" to "yesterday", "source" to "MAYBE", "page" to "-4")))
    }

    @Test
    fun poolViewRoundTripsThroughTheUrl() {
        val state = PoolQueryState("ru").withSearch("кни").withPage(1)
        assertEquals("?calendar=ru&q=%D0%BA%D0%BD%D0%B8&page=2", state.toRouteQuery())
        assertEquals(state, PoolQueryState.fromParams(mapOf("calendar" to "ru", "q" to "кни", "page" to "2")))
        assertEquals("?q=%D0%BA%D0%BD%D0%B8&page=1&size=50", state.toApiQuery(50))
        assertEquals(PoolQueryState("kk"), state.withCalendar("kk"))
    }

    // --- messages ---

    @Test
    fun refusalsNameTheDaysAWordIsUsedOrPickedOn() {
        assertEquals(
            "Это слово уже было словом дня: 14 сент. 2026, 15 сент. 2026.",
            calendarErrorMessage(ApiResult.Error("conflict", "word: used 2026-09-14,2026-09-15")),
        )
        assertEquals("Это слово уже выбрано на 1 окт. 2026.", calendarErrorMessage(ApiResult.Error("conflict", "word: picked 2026-10-01")))
        assertEquals(Strings.Calendar.reason("day", "locked"), calendarErrorMessage(ApiResult.Error("validation_failed", "day: locked")))
        assertEquals(Strings.Common.NETWORK_ERROR, calendarErrorMessage(ApiResult.Error("network_error", "No response")))
    }

    @Test
    fun candidatesDescribeTheirUsage() {
        assertEquals("ещё не было", candidateUsage(CalendarCandidateDto(wordId = "w", text = "crane", neverUsed = true)))
        assertEquals(
            "было 1 сент. 2026 · стоит на 20 сент. 2026 (вручную)",
            candidateUsage(CalendarCandidateDto(wordId = "w", text = "crane", neverUsed = false, lastUsed = "2026-09-01", scheduledOn = ScheduledOnDto("2026-09-20", DaySource.MANUAL))),
        )
        assertEquals("повтор, было 3 авг. 2026", Strings.Calendar.repeatSince("2026-08-03"))
    }

    @Test
    fun markResultsAreGroupedByOutcome() {
        val results = listOf(
            MarkItemResultDto("zz", outcome = MarkOutcome.NOT_IN_CATALOG),
            MarkItemResultDto("Lemon", "w-1", "lemon", MarkOutcome.MARKED),
            MarkItemResultDto("apple", "w-2", "apple", MarkOutcome.ALREADY_ELIGIBLE),
            MarkItemResultDto("melon", "w-3", "melon", MarkOutcome.MARKED),
        )
        assertEquals(
            listOf(
                MarkGroup(MarkOutcome.MARKED, listOf(results[1], results[3])),
                MarkGroup(MarkOutcome.ALREADY_ELIGIBLE, listOf(results[2])),
                MarkGroup(MarkOutcome.NOT_IN_CATALOG, listOf(results[0])),
            ),
            groupMarkResults(results),
        )
    }

    // --- Uzbek pair form ---

    @Test
    fun choosingALatinWordSuggestsItsCyrillicSpelling() {
        val shahar = PoolCandidateDto("w-1", "shahar", 5)
        val form = PairForm().withLatin(shahar)
        assertEquals("шаҳар", form.cyrillic)
        assertFalse(form.edited)
        assertTrue(form.check.isValid)
        assertEquals(5, form.check.graphemeCount)

        val corrected = form.withCyrillic("шахар")
        assertTrue(corrected.edited)
        assertTrue(corrected.check.isValid)
        assertEquals("шаҳар", corrected.resetToSuggestion().cyrillic)
        assertEquals("йўл", PairForm.suggestion("yoʻl"))
        // Another Latin word starts again from its own suggestion.
        assertEquals("китоб", corrected.withLatin(PoolCandidateDto("w-2", "kitob", 5)).cyrillic)
    }

    @Test
    fun invalidCyrillicInputIsCaughtBeforeAskingTheServer() {
        val form = PairForm().withLatin(PoolCandidateDto("w-1", "shahar", 5))

        val latinLetters = form.withCyrillic("shahar").check
        assertFalse(latinLetters.isValid)
        assertTrue(latinLetters.notCyrillic)
        assertEquals(WordReasons.NOT_TOKENIZABLE, latinLetters.cyrillicReason)

        assertEquals(WordReasons.BAD_LENGTH, form.withCyrillic("шаҳ").check.cyrillicReason)
        assertEquals(WordReasons.EMPTY, form.withCyrillic("  ").check.cyrillicReason)
        assertEquals("шаҳар", form.withCyrillic(" ШАҲАР ").check.normalized)

        val noLatin = PairForm(cyrillic = "шаҳар").check
        assertTrue(noLatin.latinMissing)
        assertFalse(noLatin.isValid)
        assertNull(PairForm.suggestion("crwth"))
    }

    // --- API ---

    @Test
    fun picksAndUnpicksAreSentWithTheAntiForgeryHeader() = runTest {
        val requests = mutableListOf<HttpRequest>()
        val transport = object : HttpTransport {
            override suspend fun send(request: HttpRequest): HttpResponse {
                requests += request
                return HttpResponse(200, """{"day":"2026-09-20","text":"crane","source":"MANUAL","locked":false,"pickedAt":0}""")
            }
        }
        val api = AdminApi("/harf", CredentialsMode.SAME_ORIGIN, transport, { "XSRF-TOKEN=tok-9" })

        val picked = api.pickDay("en", "2026-09-20", PickDayRequest(wordId = "w-1"))
        api.unpickDay("en", "2026-09-20")
        api.unmarkWord("ru", "w/2")

        assertTrue(picked is ApiResult.Success)
        val (put, delete, unmark) = requests
        assertEquals("PUT", put.method)
        assertEquals("/harf/api/v1/admin/calendar/en/days/2026-09-20", put.url)
        assertEquals("""{"wordId":"w-1"}""", put.body)
        assertEquals("tok-9", put.headers["X-XSRF-TOKEN"])
        assertEquals("DELETE", delete.method)
        assertNull(delete.body)
        assertEquals("tok-9", delete.headers["X-XSRF-TOKEN"])
        assertEquals("/harf/api/v1/admin/answer-pool/ru/words/w%2F2", unmark.url)
    }
}
