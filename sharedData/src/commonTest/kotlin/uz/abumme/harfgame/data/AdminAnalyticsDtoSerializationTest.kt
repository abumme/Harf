package uz.abumme.harfgame.data

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import uz.abumme.harfgame.data.admin.AdminRoutes
import uz.abumme.harfgame.data.admin.FieldError
import uz.abumme.harfgame.data.admin.Permission
import uz.abumme.harfgame.data.admin.analytics.AccountsDayDto
import uz.abumme.harfgame.data.admin.analytics.AccountsDto
import uz.abumme.harfgame.data.admin.analytics.ActivityDto
import uz.abumme.harfgame.data.admin.analytics.ActivityLangDayDto
import uz.abumme.harfgame.data.admin.analytics.AnalyticsRange
import uz.abumme.harfgame.data.admin.analytics.AnalyticsTable
import uz.abumme.harfgame.data.admin.analytics.CalendarContentDto
import uz.abumme.harfgame.data.admin.analytics.CohortDto
import uz.abumme.harfgame.data.admin.analytics.ContentCurrentDto
import uz.abumme.harfgame.data.admin.analytics.ContentDayDto
import uz.abumme.harfgame.data.admin.analytics.ContentDto
import uz.abumme.harfgame.data.admin.analytics.DayRangeDto
import uz.abumme.harfgame.data.admin.analytics.GlobalDayDto
import uz.abumme.harfgame.data.admin.analytics.KpiDto
import uz.abumme.harfgame.data.admin.analytics.OutcomeDayDto
import uz.abumme.harfgame.data.admin.analytics.OutcomeTotalsDto
import uz.abumme.harfgame.data.admin.analytics.OutcomesDto
import uz.abumme.harfgame.data.admin.analytics.OverviewDto
import uz.abumme.harfgame.data.admin.analytics.PoolDayDto
import uz.abumme.harfgame.data.admin.analytics.RetentionDto
import uz.abumme.harfgame.data.admin.analytics.StaffActivityDto
import uz.abumme.harfgame.data.admin.analytics.StaffDayDto
import uz.abumme.harfgame.data.admin.analytics.StreakDayDto
import uz.abumme.harfgame.data.admin.analytics.StreaksDto
import uz.abumme.harfgame.data.admin.analytics.SuggestionDayDto
import uz.abumme.harfgame.data.admin.analytics.SuggestionsAnalyticsDto
import uz.abumme.harfgame.data.admin.analytics.TodaySoFarDto
import uz.abumme.harfgame.data.admin.analytics.WordDayDto
import uz.abumme.harfgame.data.admin.analytics.WordDifficultyDto
import uz.abumme.harfgame.data.admin.analytics.WordSort
import uz.abumme.harfgame.data.admin.calendar.DaySource
import uz.abumme.harfgame.data.admin.words.StaffRefDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AdminAnalyticsDtoSerializationTest {
    // The same settings as the backend's ContentNegotiation.
    private val json = Json { ignoreUnknownKeys = true }
    private val range = DayRangeDto("2026-08-18", "2026-09-16")

    private inline fun <reified T> roundTrip(value: T) = assertEquals(value, json.decodeFromString<T>(json.encodeToString(value)))

    @Test
    fun unavailableValuesTravelAsNullsNotZeros() {
        val day = AccountsDayDto("2026-09-16", 3, linksGoogle = null, linksApple = null, deletions = 0, total = null, linked = null, googleLinked = null, appleLinked = null, anonymous = null, provisional = true)
        val encoded = json.parseToJsonElement(json.encodeToString(day)).jsonObject
        assertEquals(JsonNull, encoded["linksGoogle"])
        assertEquals(JsonNull, encoded["total"])
        assertEquals(JsonPrimitive(true), encoded["provisional"])
        roundTrip(AccountsDto(range, languageFiltered = false, days = listOf(day)))

        val cohort = CohortDto("2026-09-01", size = 10, d1 = 4, d7 = 2, d30 = null, provisional = true)
        assertEquals(JsonNull, json.parseToJsonElement(json.encodeToString(cohort)).jsonObject["d30"])
        roundTrip(RetentionDto(range, languageFiltered = false, cohorts = listOf(cohort)))
    }

    @Test
    fun provisionalAndPartialFlagsArePreserved() {
        val today = TodaySoFarDto("en", "2026-09-17", players = 5, games = 5, partial = true)
        roundTrip(
            OverviewDto(
                dau = KpiDto("2026-09-16", 120.0, "2026-09-15", 110.0, provisional = true),
                wau = KpiDto("2026-09-16", 300.0, "2026-09-15", null, provisional = true),
                mau = KpiDto(null, null, null, null, provisional = false),
                games = KpiDto("2026-09-16", 150.0, "2026-09-15", 140.0, provisional = true),
                winRate = KpiDto("2026-09-16", 0.8, "2026-09-15", 0.75, provisional = true),
                newAccounts = KpiDto("2026-09-16", 7.0, "2026-09-15", 9.0, provisional = true),
                totalAccounts = KpiDto("2026-09-16", null, "2026-09-15", null, provisional = true),
                backlog = KpiDto("2026-09-16", 2.0, "2026-09-15", 4.0, provisional = true),
                today = listOf(today),
            ),
        )
        assertEquals(JsonPrimitive(true), json.parseToJsonElement(json.encodeToString(today)).jsonObject["partial"])
        roundTrip(
            ActivityDto(
                range, lang = "kk",
                languages = listOf(ActivityLangDayDto("kk", "2026-09-16", 4, 4, provisional = true), ActivityLangDayDto("kk", "2026-09-01", 2, 2, provisional = false)),
                global = listOf(GlobalDayDto("2026-09-16", 10, 40, 90, provisional = true)),
                languageFiltered = false,
                today = listOf(today),
            ),
        )
    }

    @Test
    fun everyTableRoundTrips() {
        roundTrip(StreaksDto(range, "en", listOf(StreakDayDto("en", "2026-09-16", 1, 2, 3, 4, provisional = true))))
        roundTrip(
            OutcomesDto(
                range, null,
                listOf(OutcomeDayDto("kk", "2026-09-01", 5, 4, 1, listOf(0, 1, 2, 1, 0, 0), 0.8, 3.0, provisional = false)),
                listOf(OutcomeTotalsDto(null, 0, 0, 0, listOf(0, 0, 0, 0, 0, 0), winRate = null, avgAttempts = null, provisional = true)),
            ),
        )
        roundTrip(
            WordDifficultyDto(
                range, "uz", WordSort.WIN_RATE.param, "asc",
                listOf(WordDayDto("uz", "2026-09-01", "kitob", "китоб", DaySource.AUTO, repeat = true, players = 10, wins = 7, winRate = 0.7, avgAttempts = 4.4, provisional = false)),
            ),
        )
        roundTrip(SuggestionsAnalyticsDto(range, "ru", listOf(SuggestionDayDto("2026-08-20", "ru", 6, 3, 1, 1, 2, 120.0, null, provisional = false))))
        roundTrip(
            ContentDto(
                range, null,
                listOf(ContentDayDto("2026-08-20", "en", null, 0, 0, 3, 12, 1, 1, provisional = false)),
                listOf(PoolDayDto("2026-09-16", "kk", 30, 7, provisional = true), PoolDayDto("2026-09-16", "en", null, null, provisional = true)),
                listOf(ContentCurrentDto("en", 15)),
                listOf(CalendarContentDto("kk", 30, 7, repeatDays = 0, upcomingRepeatDays = 2), CalendarContentDto("uz", null, null, 0, 0)),
            ),
        )
        roundTrip(
            StaffActivityDto(
                range, "ru",
                listOf(
                    StaffDayDto("2026-08-20", StaffRefDto("s-1", "worder", "Ира"), telegramUnlinked = false, lang = "ru", added = 40, edited = 2, removed = 1, decided = 5, provisional = false),
                    StaffDayDto("2026-08-20", null, telegramUnlinked = true, lang = "ru", added = 0, edited = 0, removed = 0, decided = 2, provisional = false),
                ),
            ),
        )
    }

    @Test
    fun rangeRulesSharedByPanelAndServer() {
        val to = LocalDate.parse("2026-09-16")
        assertEquals(LocalDate.parse("2026-08-18"), AnalyticsRange.defaultFrom(to))
        assertEquals(30, AnalyticsRange.days(AnalyticsRange.defaultFrom(to), to))
        assertNull(AnalyticsRange.problem(LocalDate.parse("2025-09-16"), to)) // exactly 366 days
        assertEquals(FieldError("to", "range_too_long"), AnalyticsRange.problem(LocalDate.parse("2025-09-15"), to))
        assertEquals(FieldError("to", "before_from"), AnalyticsRange.problem(to, LocalDate.parse("2026-09-15")))
        assertNull(AnalyticsRange.problem(to, to))
    }

    @Test
    fun routesSortsAndThePermission() {
        assertEquals("/api/v1/admin/analytics/overview", AdminRoutes.ANALYTICS_OVERVIEW)
        assertEquals("/api/v1/admin/analytics/words", AdminRoutes.analytics(AnalyticsTable.WORDS))
        assertEquals("/api/v1/admin/analytics/words.csv", AdminRoutes.analyticsCsv(AnalyticsTable.WORDS))
        assertEquals(WordSort.AVG_ATTEMPTS, WordSort.fromParam("avgAttempts"))
        assertNull(WordSort.fromParam("word"))
        assertEquals(Permission.ANALYTICS_READ, Json.decodeFromString<Permission>("\"ANALYTICS_READ\""))
    }
}
