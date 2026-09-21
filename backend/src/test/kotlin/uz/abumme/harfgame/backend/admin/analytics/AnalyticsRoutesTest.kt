package uz.abumme.harfgame.backend.admin.analytics

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import uz.abumme.harfgame.backend.admin.AdminBackend
import uz.abumme.harfgame.backend.admin.AdminConfig
import uz.abumme.harfgame.backend.admin.MutableClock
import uz.abumme.harfgame.backend.admin.StaffSession
import uz.abumme.harfgame.backend.admin.adminClient
import uz.abumme.harfgame.backend.admin.analytics.AnalyticsFixture.E
import uz.abumme.harfgame.backend.admin.analytics.AnalyticsFixture.NOW
import uz.abumme.harfgame.backend.admin.analytics.AnalyticsFixture.date
import uz.abumme.harfgame.backend.admin.analytics.AnalyticsFixture.day
import uz.abumme.harfgame.backend.admin.error
import uz.abumme.harfgame.backend.admin.signIn
import uz.abumme.harfgame.backend.admin.testHasher
import uz.abumme.harfgame.backend.admin.withSession
import uz.abumme.harfgame.backend.db.AnalyticsLangDayTable
import uz.abumme.harfgame.backend.db.UsersTable
import uz.abumme.harfgame.backend.service.WordPackServerService
import uz.abumme.harfgame.data.admin.AdminErrors
import uz.abumme.harfgame.data.admin.AdminRoutes
import uz.abumme.harfgame.data.admin.FieldError
import uz.abumme.harfgame.data.admin.analytics.AccountsDto
import uz.abumme.harfgame.data.admin.analytics.ActivityDto
import uz.abumme.harfgame.data.admin.analytics.AnalyticsTable
import uz.abumme.harfgame.data.admin.analytics.ContentDto
import uz.abumme.harfgame.data.admin.analytics.OutcomesDto
import uz.abumme.harfgame.data.admin.analytics.OverviewDto
import uz.abumme.harfgame.data.admin.analytics.RetentionDto
import uz.abumme.harfgame.data.admin.analytics.StaffActivityDto
import uz.abumme.harfgame.data.admin.analytics.StreaksDto
import uz.abumme.harfgame.data.admin.analytics.SuggestionsAnalyticsDto
import uz.abumme.harfgame.data.admin.analytics.WordDifficultyDto
import uz.abumme.harfgame.data.admin.calendar.DaySource
import uz.abumme.harfgame.data.api.ApiRoutes
import uz.abumme.harfgame.data.auth.AnonymousAuthResponse
import java.time.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnalyticsRoutesTest {
    private val clock = MutableClock(NOW)

    @BeforeTest
    fun setup() {
        if (!loaded || rowOf(UsersTable) { id eq AnalyticsFixture.player(69) } == null ||
            rowOf(AnalyticsLangDayTable) { (lang eq "kk") and (puzzleDay eq day(AnalyticsFixture.OUTCOMES)) } == null
        ) {
            resetAnalyticsData()
            AnalyticsFixture.load()
            rollupJob(clock).run(NOW)
            loaded = true
        }
        clock.now = NOW
    }

    private fun backend(): AdminBackend {
        val wordPacks = WordPackServerService()
        return AdminBackend(
            config = AdminConfig(loginAttemptsPerMinute = 1_000),
            packLanguages = { wordPacks.languages() },
            clock = clock,
            passwordHasher = testHasher,
            wordPacks = wordPacks,
            catalog = analyticsCatalog(clock),
        )
    }

    private suspend fun HttpClient.adminGet(session: StaffSession?, path: String, bearer: String? = null): HttpResponse = get(path) {
        session?.let { withSession(it) }
        bearer?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }

    /** Every analytics path: the overview, and each table as JSON and as CSV. */
    private val allPaths: List<String> =
        listOf(AdminRoutes.ANALYTICS_OVERVIEW) + AnalyticsTable.entries.flatMap { listOf(AdminRoutes.analytics(it), AdminRoutes.analyticsCsv(it)) }

    private fun range(from: LocalDate, to: LocalDate, vararg extra: Pair<String, String>) =
        "?from=$from&to=$to" + extra.joinToString("") { (name, value) -> "&$name=$value" }

    @Test
    fun anAdminGetsEveryTableForTheRequestedRange() = testApplication {
        val client = adminClient(backend())
        val boss = client.signIn("boss")

        val overview = client.adminGet(boss, AdminRoutes.ANALYTICS_OVERVIEW).body<OverviewDto>()
        assertEquals("2026-09-16", overview.dau.day)
        assertTrue(overview.dau.provisional)
        assertEquals(1L, overview.today.single { it.lang == "en" }.players)
        assertTrue(overview.today.all { it.partial })
        assertEquals("2026-09-16", overview.newAccounts.day)
        assertEquals(AnalyticsExpected.FULL_ACCOUNTS_TOTAL.toDouble(), overview.totalAccounts.value)

        val accounts = client.adminGet(boss, AdminRoutes.analytics(AnalyticsTable.ACCOUNTS) + range(E.minusDays(1), E.plusDays(2))).body<AccountsDto>()
        assertEquals(4, accounts.days.size)
        assertFalse(accounts.languageFiltered)
        assertEquals(3L, accounts.days.single { it.date == E.toString() }.newAccounts)
        assertNull(accounts.days.single { it.date == E.toString() }.linksGoogle)

        val activity = client.adminGet(boss, AdminRoutes.analytics(AnalyticsTable.ACTIVITY) + range(date(0), date(0), "lang" to "en")).body<ActivityDto>()
        assertEquals(4L, activity.languages.single().players)
        assertEquals("en", activity.languages.single().lang)
        assertEquals(4L, activity.global.single().dau)
        assertFalse(activity.languageFiltered)
        assertEquals(listOf("en"), activity.today.map { it.lang })

        val retention = client.adminGet(boss, AdminRoutes.analytics(AnalyticsTable.RETENTION) + range(date(5), date(5))).body<RetentionDto>()
        assertEquals(listOf(10L, 4L, 2L, 1L), retention.cohorts.single().let { listOf(it.size, it.d1, it.d7, it.d30) })

        val streaks = client.adminGet(boss, AdminRoutes.analytics(AnalyticsTable.STREAKS) + range(date(115), date(115), "lang" to "en")).body<StreaksDto>()
        assertEquals(listOf(1L, 1L, 1L, 1L), streaks.days.single().let { listOf(it.streak1, it.streak2to6, it.streak7to29, it.streak30plus) })

        val outcomes = client.adminGet(boss, AdminRoutes.analytics(AnalyticsTable.OUTCOMES) + range(date(3), date(3), "lang" to "kk")).body<OutcomesDto>()
        assertEquals(0.8, outcomes.days.single().winRate)

        val words = client.adminGet(
            boss,
            AdminRoutes.analytics(AnalyticsTable.WORDS) + range(date(40), date(44), "calendar" to "en", "sort" to "players", "dir" to "desc"),
        ).body<WordDifficultyDto>()
        assertEquals(5, words.rows.size)
        assertEquals("apple", words.rows.first().word)
        assertEquals(DaySource.MANUAL, words.rows.first().marker)
        assertEquals("players", words.sort)
        val ascending = client.adminGet(boss, AdminRoutes.analytics(AnalyticsTable.WORDS) + range(date(40), date(44), "calendar" to "en", "sort" to "players", "dir" to "asc")).body<WordDifficultyDto>()
        assertEquals("apple", ascending.rows.last().word)

        val suggestions = client.adminGet(boss, AdminRoutes.analytics(AnalyticsTable.SUGGESTIONS) + range(E, E, "lang" to "ru")).body<SuggestionsAnalyticsDto>()
        assertEquals(6L, suggestions.days.single().submitted)
        assertEquals(120.0, suggestions.days.single().medianAutoSeconds)

        val content = client.adminGet(boss, AdminRoutes.analytics(AnalyticsTable.CONTENT) + range(E, E, "lang" to "en")).body<ContentDto>()
        assertEquals(12L, content.days.single().addedStaff)
        assertEquals(listOf("en"), content.calendars.map { it.calendar })
        assertEquals(listOf("en"), content.current.map { it.lang })

        val staff = client.adminGet(boss, AdminRoutes.analytics(AnalyticsTable.STAFF) + range(E, E, "lang" to "ru")).body<StaffActivityDto>()
        assertEquals(40L, staff.rows.single { it.staff?.username == "worder" }.added)
        assertEquals(2L, staff.rows.single { it.telegramUnlinked }.decided)
    }

    @Test
    fun withoutDatesTheRangeIsTheLastThirtyClosedDays() = testApplication {
        val client = adminClient(backend())
        val boss = client.signIn("boss")

        val accounts = client.adminGet(boss, AdminRoutes.analytics(AnalyticsTable.ACCOUNTS)).body<AccountsDto>()

        assertEquals("2026-08-18", accounts.range.from)
        assertEquals("2026-09-16", accounts.range.to)
        assertEquals(30, accounts.days.size)
        val last = accounts.days.last()
        assertEquals("2026-09-16", last.date)
        assertTrue(last.provisional)
        assertEquals(AnalyticsExpected.FULL_ACCOUNTS_TOTAL.toLong(), last.total)
        assertFalse(accounts.days.first().provisional)
    }

    @Test
    fun anInvalidRangeIsRefused() = testApplication {
        val client = adminClient(backend())
        val boss = client.signIn("boss")

        suspend fun refusal(query: String): FieldError? {
            val response = client.adminGet(boss, AdminRoutes.analytics(AnalyticsTable.ACTIVITY) + query)
            assertEquals(HttpStatusCode.UnprocessableEntity, response.status, query)
            val error = response.error()
            assertEquals(AdminErrors.VALIDATION_FAILED, error.error)
            return FieldError.parse(error.message)
        }

        assertEquals(FieldError("to", "range_too_long"), refusal(range(LocalDate.parse("2025-08-01"), LocalDate.parse("2026-09-04"))))
        assertEquals(FieldError("to", "before_from"), refusal(range(LocalDate.parse("2026-09-10"), LocalDate.parse("2026-09-09"))))
        assertEquals(FieldError("from", "invalid"), refusal("?from=yesterday"))
        assertEquals(FieldError("lang", "unknown"), refusal("?lang=uz"))
        // Exactly 366 days is allowed; the CSV export refuses the same way.
        assertEquals(HttpStatusCode.OK, client.adminGet(boss, AdminRoutes.analytics(AnalyticsTable.ACTIVITY) + range(LocalDate.parse("2025-09-16"), LocalDate.parse("2026-09-16"))).status)
        assertEquals(
            HttpStatusCode.UnprocessableEntity,
            client.adminGet(boss, AdminRoutes.analyticsCsv(AnalyticsTable.WORDS) + range(LocalDate.parse("2025-08-01"), LocalDate.parse("2026-09-04"))).status,
        )
        assertEquals(FieldError("sort", "invalid"), FieldError.parse(client.adminGet(boss, AdminRoutes.analytics(AnalyticsTable.WORDS) + "?sort=word").error().message))
    }

    @Test
    fun theRussianWordDifficultyExportHasAHeaderAndOneRowPerDayWithCyrillicIntact() = testApplication {
        val client = adminClient(backend())
        val boss = client.signIn("boss")

        val response = client.adminGet(boss, AdminRoutes.analyticsCsv(AnalyticsTable.WORDS) + "?calendar=ru")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Text.CSV.withParameter("charset", "UTF-8").toString().lowercase(), response.contentType().toString().lowercase())
        assertEquals("attachment; filename=\"harf-words-2026-08-18-2026-09-16.csv\"", response.headers[HttpHeaders.ContentDisposition])
        val bytes = response.readRawBytes()
        assertEquals(listOf(0xEF, 0xBB, 0xBF), bytes.take(3).map { it.toInt() and 0xFF })
        val lines = bytes.decodeToString().removePrefix("﻿").split("\r\n").filter { it.isNotEmpty() }
        assertEquals("calendar,day,word,word_cyrl,marker,repeat,players,wins,win_rate,avg_attempts,provisional", lines.first())
        assertEquals(30, lines.size - 1)
        // Every day of the range once; the pack's Russian word, decoded intact.
        assertEquals((0L..29L).map { LocalDate.parse("2026-09-16").minusDays(it).toString() }.toSet(), lines.drop(1).map { it.split(',')[1] }.toSet())
        assertTrue(lines.drop(1).all { it.split(',')[2] == "берег" })
        assertEquals("ru,2026-08-29,берег,,,false,1,1,1,3,false", lines.drop(1).single { it.split(',')[1] == "2026-08-29" })
        assertTrue(lines.single { it.split(',')[1] == "2026-09-14" }.endsWith(",true"))
    }

    @Test
    fun onlyAnAdminSessionReadsAnalytics() = testApplication {
        val client = adminClient(backend())
        val boss = client.signIn("boss")
        val worder = client.signIn("worder")
        val player = client.post(ApiRoutes.AUTH_ANONYMOUS).body<AnonymousAuthResponse>()

        for (path in allPaths) {
            val admin = client.adminGet(boss, path)
            assertEquals(HttpStatusCode.OK, admin.status, "ADMIN $path")
            val body = admin.bodyAsText()
            // Aggregates only: no fixture account id, display name or provider subject.
            (1..69).map(AnalyticsFixture::player).plus(AnalyticsFixture.personalData).forEach { secret ->
                assertFalse(secret in body, "$path leaks $secret")
            }

            val forbidden = client.adminGet(worder, path)
            assertEquals(HttpStatusCode.Forbidden, forbidden.status, "WORDER $path")
            assertEquals(AdminErrors.FORBIDDEN, forbidden.error().error)
            assertFalse("\"days\"" in forbidden.bodyAsText() || "calendar," in forbidden.bodyAsText())

            assertEquals(HttpStatusCode.Unauthorized, client.adminGet(null, path).status, "no session $path")
            assertEquals(HttpStatusCode.Unauthorized, client.adminGet(null, path, bearer = player.tokens.accessToken).status, "player token $path")
        }
    }

    companion object {
        private var loaded = false
    }
}
