package uz.abumme.harfgame.admin

import kotlinx.datetime.LocalDate
import uz.abumme.harfgame.admin.analytics.AnalyticsFilter
import uz.abumme.harfgame.admin.analytics.csvHref
import uz.abumme.harfgame.admin.analytics.daysInRange
import uz.abumme.harfgame.admin.analytics.dimmedDays
import uz.abumme.harfgame.admin.analytics.formatCount
import uz.abumme.harfgame.admin.analytics.formatDecimal
import uz.abumme.harfgame.admin.analytics.formatDelta
import uz.abumme.harfgame.admin.analytics.formatDuration
import uz.abumme.harfgame.admin.analytics.formatPercent
import uz.abumme.harfgame.admin.analytics.lastClosedDay
import uz.abumme.harfgame.admin.analytics.nextWordSort
import uz.abumme.harfgame.admin.analytics.perDay
import uz.abumme.harfgame.admin.analytics.retentionShade
import uz.abumme.harfgame.admin.analytics.share
import uz.abumme.harfgame.admin.analytics.wordSortParameters
import uz.abumme.harfgame.admin.api.AdminApi
import uz.abumme.harfgame.admin.api.CredentialsMode
import uz.abumme.harfgame.admin.api.HttpRequest
import uz.abumme.harfgame.admin.api.HttpResponse
import uz.abumme.harfgame.admin.components.TableSort
import uz.abumme.harfgame.data.admin.FieldError
import uz.abumme.harfgame.data.admin.analytics.AnalyticsTable
import uz.abumme.harfgame.data.admin.analytics.WordDifficultyDto
import kotlin.js.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class AnalyticsStateTest {
    private val nbsp = " "
    private fun millis(instant: String) = Date(instant).getTime()

    // --- filter (7.2) ---

    @Test
    fun theDefaultRangeIsTheLastThirtyDaysClosedInEveryLanguage() {
        // 23:30 in Moscow on the 16th is already the 17th in Tashkent: the 16th has not ended everywhere.
        assertEquals(LocalDate.parse("2026-09-15"), lastClosedDay(millis("2026-09-16T20:30:00Z")))
        assertEquals(LocalDate.parse("2026-09-16"), lastClosedDay(millis("2026-09-16T21:00:01Z")))

        val filter = AnalyticsFilter.default(LocalDate.parse("2026-09-16"))
        assertEquals(AnalyticsFilter("2026-08-18", "2026-09-16", null), filter)
        assertEquals(30, daysInRange(filter.from, filter.to).size)
        assertNull(filter.problem)
        assertEquals(filter, AnalyticsFilter.fromRoute(emptyMap(), LocalDate.parse("2026-09-16")))
        assertEquals(
            AnalyticsFilter("2026-01-01", "2026-01-31", "kk"),
            AnalyticsFilter.fromRoute(mapOf("from" to "2026-01-01", "to" to "2026-01-31", "lang" to "kk"), LocalDate.parse("2026-09-16")),
        )
    }

    @Test
    fun aRangeTheServerWouldRefuseIsRefusedBeforeAnyRequest() {
        assertEquals(FieldError("to", "range_too_long"), AnalyticsFilter("2025-08-13", "2026-09-16").problem) // 400 days
        assertNull(AnalyticsFilter("2025-09-16", "2026-09-16").problem) // 366 days
        assertEquals(FieldError("to", "before_from"), AnalyticsFilter("2026-09-16", "2026-09-15").problem)
        assertEquals(FieldError("from", "invalid"), AnalyticsFilter("", "2026-09-15").problem)
        assertFalse(AnalyticsFilter("2026-09-16", "2026-09-15").isValid)
        assertEquals("Период не может быть длиннее 366 дней.", Strings.Analytics.rangeProblem("range_too_long"))
    }

    @Test
    fun allLanguagesOmitsLangAndTheJsonAndCsvRequestsShareTheirParameters() = runTest {
        val all = AnalyticsFilter("2026-08-18", "2026-09-16").withLang(AnalyticsFilter.ALL_LANGUAGES)
        assertNull(all.lang)
        assertEquals("?from=2026-08-18&to=2026-09-16", all.queryFor(AnalyticsTable.ACTIVITY))

        val kazakh = all.withLang("kk")
        assertEquals("?from=2026-08-18&to=2026-09-16&lang=kk", kazakh.queryFor(AnalyticsTable.ACTIVITY))
        // Not per language: accounts and retention take no lang.
        assertEquals("?from=2026-08-18&to=2026-09-16", kazakh.queryFor(AnalyticsTable.ACCOUNTS))
        assertEquals("?from=2026-08-18&to=2026-09-16", kazakh.queryFor(AnalyticsTable.RETENTION))
        // Both Uzbek scripts are one calendar.
        assertEquals("?from=2026-08-18&to=2026-09-16&calendar=uz", all.withLang("uz-cyrl").queryFor(AnalyticsTable.WORDS))
        assertEquals("?from=2026-08-18&to=2026-09-16&lang=kk", kazakh.toRouteQuery())

        // The JSON request and the CSV link of every table carry the same query.
        val requests = mutableListOf<HttpRequest>()
        val api = AdminApi("/harf", CredentialsMode.SAME_ORIGIN, { request -> requests += request; HttpResponse(200, "{}") }, { "" })
        for (table in AnalyticsTable.entries) {
            val sort = TableSort("winRate", descending = false).takeIf { table == AnalyticsTable.WORDS }
            api.analytics(table, kazakh.queryFor(table, sort), WordDifficultyDto.serializer())
            val href = csvHref("/harf", table, kazakh, sort)
            assertEquals(requests.last().url.substringAfter('?'), href.substringAfter('?'), table.name)
            assertEquals("/harf/api/v1/admin/analytics/${table.path}", requests.last().url.substringBefore('?'))
        }
    }

    @Test
    fun csvLinksCarryTheBasePathAndTheCurrentFilters() {
        val filter = AnalyticsFilter("2026-08-18", "2026-09-16", "ru")
        assertEquals("/harf/api/v1/admin/analytics/words.csv?from=2026-08-18&to=2026-09-16&calendar=ru", csvHref("/harf", AnalyticsTable.WORDS, filter))
        assertEquals(
            "/harf/api/v1/admin/analytics/suggestions.csv?from=2026-08-18&to=2026-09-16&lang=ru",
            csvHref("/harf/", AnalyticsTable.SUGGESTIONS, filter),
        )
        assertEquals(
            "http://localhost:8080/api/v1/admin/analytics/words.csv?from=2026-08-18&to=2026-09-16&calendar=ru&sort=players&dir=desc",
            csvHref("http://localhost:8080", AnalyticsTable.WORDS, filter, TableSort("players", descending = true)),
        )
    }

    @Test
    fun aSortChangeMapsToTheRequestedSortParameter() {
        assertEquals(emptyList(), wordSortParameters(null))
        val players = nextWordSort(null, "players")
        assertEquals(listOf("sort" to "players", "dir" to "desc"), wordSortParameters(players))
        val flipped = nextWordSort(players, "players")
        assertEquals(listOf("sort" to "players", "dir" to "asc"), wordSortParameters(flipped))
        assertEquals(listOf("sort" to "winRate", "dir" to "desc"), wordSortParameters(nextWordSort(flipped, "winRate")))
        assertEquals(listOf("sort" to "avgAttempts", "dir" to "asc"), wordSortParameters(TableSort("avgAttempts", descending = false)))
        assertEquals(emptyList(), wordSortParameters(TableSort("word", descending = true)))
        assertEquals(
            "?from=2026-08-18&to=2026-09-16&sort=winRate&dir=desc",
            AnalyticsFilter("2026-08-18", "2026-09-16").queryFor(AnalyticsTable.WORDS, TableSort("winRate", true)),
        )
    }

    @Test
    fun seriesHaveOneValuePerDayAndGapsForMissingDays() {
        val days = daysInRange("2026-02-27", "2026-03-02")
        assertEquals(listOf("2026-02-27", "2026-02-28", "2026-03-01", "2026-03-02"), days)
        assertEquals(emptyList(), daysInRange("2026-03-02", "2026-03-01"))
        assertEquals(listOf(5.0, null, 0.0, null), perDay(days, mapOf("2026-02-27" to 5L, "2026-03-01" to 0L)) { it.toDouble() })
        assertEquals(listOf(false, false, true, true), dimmedDays(days, setOf("2026-03-01", "2026-03-02")))
    }

    // --- formatting (7.4) ---

    @Test
    fun numbersPercentsAndDeltasShowADashForUnknownValues() {
        assertEquals("—", formatCount(null as Long?))
        assertEquals("0", formatCount(0L))
        assertEquals("12${nbsp}345", formatCount(12_345L))
        assertEquals("1${nbsp}000${nbsp}000", formatCount(1_000_000L))
        assertEquals("3,0", formatDecimal(3.0))
        assertEquals("4,4", formatDecimal(4.4))
        assertEquals("—", formatDecimal(null))
        assertEquals("80$nbsp%", formatPercent(0.8))
        assertEquals("75,5$nbsp%", formatPercent(0.755))
        assertEquals("0$nbsp%", formatPercent(0.0))
        assertEquals("—", formatPercent(null))
        assertEquals("+10", formatDelta(120.0, 110.0))
        assertEquals("−5", formatDelta(5.0, 10.0))
        assertEquals("0", formatDelta(7.0, 7.0))
        assertEquals("—", formatDelta(7.0, null))
        assertEquals("—", formatDelta(null, 7.0))
        assertEquals("+5${nbsp}п.п.", formatDelta(0.8, 0.75, share = true))
        assertEquals("2${nbsp}ч", formatDuration(7_200.0))
        assertEquals("10${nbsp}мин", formatDuration(600.0))
        assertEquals("1${nbsp}д 3${nbsp}ч", formatDuration(97_200.0))
        assertEquals("—", formatDuration(null))
        assertEquals(0.4, share(4, 10))
        assertNull(share(null, 10))
        assertNull(share(3, 0))
    }

    @Test
    fun retentionCellsAreShadedByValueAndUnavailableOnesAreNot() {
        assertNull(retentionShade(null))
        assertEquals(0, retentionShade(0.0))
        assertEquals(1, retentionShade(0.1))
        assertEquals(2, retentionShade(0.2))
        assertEquals(3, retentionShade(0.4))
        assertEquals(4, retentionShade(0.5))
        assertEquals(4, retentionShade(1.0))
        assertTrue((retentionShade(0.26) ?: 0) > (retentionShade(0.24) ?: 0))
    }
}
