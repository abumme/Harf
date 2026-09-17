package uz.abumme.harfgame.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import uz.abumme.harfgame.data.admin.AdminRoutes
import uz.abumme.harfgame.data.admin.FieldError
import uz.abumme.harfgame.data.admin.PageDto
import uz.abumme.harfgame.data.admin.Permission
import uz.abumme.harfgame.data.admin.answerpool.CreatePairRequest
import uz.abumme.harfgame.data.admin.answerpool.CyrlStatus
import uz.abumme.harfgame.data.admin.answerpool.CyrlStatusDto
import uz.abumme.harfgame.data.admin.answerpool.MarkItemResultDto
import uz.abumme.harfgame.data.admin.answerpool.MarkOutcome
import uz.abumme.harfgame.data.admin.answerpool.MarkWordsRequest
import uz.abumme.harfgame.data.admin.answerpool.PairDto
import uz.abumme.harfgame.data.admin.answerpool.PoolCandidateDto
import uz.abumme.harfgame.data.admin.answerpool.PoolPageDto
import uz.abumme.harfgame.data.admin.answerpool.PoolReasons
import uz.abumme.harfgame.data.admin.answerpool.PoolWordDto
import uz.abumme.harfgame.data.admin.audit.AuditActions
import uz.abumme.harfgame.data.admin.calendar.CalendarCandidateDto
import uz.abumme.harfgame.data.admin.calendar.CalendarNoticeDto
import uz.abumme.harfgame.data.admin.calendar.CalendarReasons
import uz.abumme.harfgame.data.admin.calendar.DailyCalendars
import uz.abumme.harfgame.data.admin.calendar.DayDto
import uz.abumme.harfgame.data.admin.calendar.DaySource
import uz.abumme.harfgame.data.admin.calendar.NoticeKind
import uz.abumme.harfgame.data.admin.calendar.NoticeReason
import uz.abumme.harfgame.data.admin.calendar.PickDayRequest
import uz.abumme.harfgame.data.admin.calendar.ScheduledOnDto
import uz.abumme.harfgame.data.admin.words.StaffRefDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdminCalendarDtoSerializationTest {
    // The same settings as the backend's ContentNegotiation.
    private val json = Json { ignoreUnknownKeys = true }

    private inline fun <reified T> roundTrip(value: T) = assertEquals(value, json.decodeFromString<T>(json.encodeToString(value)))

    @Test
    fun calendarDtosRoundTrip() {
        val day = DayDto(
            day = "2026-09-20",
            text = "kitob",
            textCyrl = "китоб",
            source = DaySource.MANUAL,
            isRepeat = true,
            lastUsed = "2026-08-01",
            locked = false,
            pairId = "p-1",
            pickedBy = StaffRefDto("st-1", "boss", "Boss"),
            pickedAt = 1_700_000_000_000,
        )
        roundTrip(day)
        roundTrip(PageDto(listOf(day, day.copy(day = "2026-09-21", source = DaySource.AUTO, pickedBy = null)), 0, 50, 2))
        roundTrip(PickDayRequest(wordId = "w-1"))
        roundTrip(PickDayRequest(pairId = "p-1"))
        roundTrip(CalendarCandidateDto(wordId = "w-1", text = "crane", neverUsed = false, lastUsed = "2026-09-01", scheduledOn = ScheduledOnDto("2026-10-02", DaySource.AUTO)))
        roundTrip(
            CalendarNoticeDto(
                "n-1", "en", "2026-09-30", NoticeKind.MANUAL_PICK_REPLACED, "crane", NoticeReason.REMOVED, 1_700_000_000_000,
                dismissedAt = 1_700_000_100_000, dismissedBy = StaffRefDto("st-1", "boss"),
            ),
        )
        // Optional fields may be absent on the wire.
        assertEquals(
            DayDto("2026-01-01", "bread", source = DaySource.LEGACY, locked = true, pickedAt = 0),
            json.decodeFromString<DayDto>("""{"day":"2026-01-01","text":"bread","source":"LEGACY","locked":true,"pickedAt":0}"""),
        )
    }

    @Test
    fun answerPoolDtosRoundTrip() {
        val entry = PoolWordDto(pairId = "p-1", text = "kitob", textCyrl = "китоб", latnWordId = "w-1", cyrlWordId = "w-2", active = false, lastUsed = "2026-09-17", scheduledOn = ScheduledOnDto("2026-09-25", DaySource.MANUAL))
        roundTrip(PoolPageDto(unusedLeft = 3, page = PageDto(listOf(entry, PoolWordDto(wordId = "w-3", text = "crane")), 0, 50, 2)))
        roundTrip(PageDto(listOf(PoolCandidateDto("w-4", "tiger", 5)), 1, 20, 21))
        roundTrip(MarkWordsRequest(wordIds = listOf("w-1"), texts = listOf("Lemon")))
        assertEquals(MarkWordsRequest(texts = listOf("x")), json.decodeFromString<MarkWordsRequest>("""{"texts":["x"]}"""))
        roundTrip(listOf(MarkItemResultDto("Lemon", "w-5", "lemon", MarkOutcome.MARKED), MarkItemResultDto("zzz", outcome = MarkOutcome.NOT_IN_CATALOG)))
        roundTrip(CyrlStatusDto("шаҳар", "w-2", CyrlStatus.ACTIVE, pairedWith = "shahar"))
        roundTrip(CyrlStatusDto("дарё", cyrlStatus = CyrlStatus.MISSING))
        roundTrip(CreatePairRequest("w-1", "Дарё", addCyrlToCatalog = true))
        assertFalse(json.decodeFromString<CreatePairRequest>("""{"latnWordId":"w-1","cyrlText":"дарё"}""").addCyrlToCatalog)
        roundTrip(PairDto("p-2", "w-1", "daryo", "w-6", "дарё", 1_700_000_000_000, StaffRefDto("st-1", "boss")))
    }

    @Test
    fun calendarsMapToTheirPackLanguages() {
        assertEquals(listOf("en", "ru", "kk", "uz"), DailyCalendars.ALL)
        assertEquals(listOf("uz-cyrl", "uz-latn"), DailyCalendars.packLanguages("uz"))
        assertEquals(listOf("kk"), DailyCalendars.packLanguages("kk"))
        assertEquals(emptyList(), DailyCalendars.packLanguages("xx"))
        assertEquals("uz", DailyCalendars.calendarOf("uz-cyrl"))
        assertEquals("ru", DailyCalendars.calendarOf("ru"))
        assertNull(DailyCalendars.calendarOf("de"))
        assertEquals("uz-latn", DailyCalendars.wordLanguage("uz"))
    }

    @Test
    fun conflictReasonsNameTheirDays() {
        val message = FieldError("word", CalendarReasons.conflict(CalendarReasons.USED, listOf("2026-09-14", "2026-09-15"))).toMessage()
        assertEquals("word: used 2026-09-14,2026-09-15", message)
        val parsed = FieldError.parse(message)!!
        assertEquals(CalendarReasons.USED to listOf("2026-09-14", "2026-09-15"), CalendarReasons.parseConflict(parsed.reason))
        assertEquals(CalendarReasons.PICKED to listOf("2026-10-01"), CalendarReasons.parseConflict("picked 2026-10-01"))
        assertNull(CalendarReasons.parseConflict(CalendarReasons.LOCKED))
        assertEquals("paired kitob/китоб", PoolReasons.paired("kitob", "китоб"))
    }

    @Test
    fun theContractNamesItsRoutesPermissionsAndActions() {
        assertEquals("/api/v1/admin/answer-pool/en/words/w-1", AdminRoutes.answerPoolWord("en", "w-1"))
        assertEquals("/api/v1/admin/answer-pool/uz/cyrl-status", AdminRoutes.ANSWER_POOL_UZ_CYRL_STATUS)
        assertEquals("/api/v1/admin/answer-pool/uz/pairs/p-1", AdminRoutes.answerPoolUzPair("p-1"))
        assertEquals("/api/v1/admin/calendar/kk/days/2026-09-20", AdminRoutes.calendarDay("kk", "2026-09-20"))
        assertEquals("/api/v1/admin/calendar/notices/n-1/dismiss", AdminRoutes.calendarNoticeDismiss("n-1"))
        assertEquals("\"CALENDAR_MANAGE\"", json.encodeToString(Permission.CALENDAR_MANAGE))
        assertTrue(Permission.DAILY_POOL_MANAGE in Permission.entries)
        assertTrue(AuditActions.ALL.filter { it.startsWith(AuditActions.DAILY_PREFIX) }.size == 8)
        assertTrue(json.parseToJsonElement(json.encodeToString(PickDayRequest(wordId = "w"))).jsonObject.keys == setOf("wordId"))
    }
}
