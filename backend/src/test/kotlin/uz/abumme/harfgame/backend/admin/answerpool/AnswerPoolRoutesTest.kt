package uz.abumme.harfgame.backend.admin.answerpool

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import uz.abumme.harfgame.backend.admin.MutableClock
import uz.abumme.harfgame.backend.admin.StaffSession
import uz.abumme.harfgame.backend.admin.adminClient
import uz.abumme.harfgame.backend.admin.auditRows
import uz.abumme.harfgame.backend.admin.calendar.CALENDAR_NOW
import uz.abumme.harfgame.backend.admin.calendar.CalendarService
import uz.abumme.harfgame.backend.admin.calendar.DailyCalendarScheduler
import uz.abumme.harfgame.backend.admin.calendar.EN_POOL
import uz.abumme.harfgame.backend.admin.calendar.TODAY
import uz.abumme.harfgame.backend.admin.calendar.calendarBackend
import uz.abumme.harfgame.backend.admin.calendar.calendarRows
import uz.abumme.harfgame.backend.admin.calendar.dayRow
import uz.abumme.harfgame.backend.admin.calendar.migrateCalendars
import uz.abumme.harfgame.backend.admin.calendar.pack
import uz.abumme.harfgame.backend.admin.calendar.resetCalendarData
import uz.abumme.harfgame.backend.admin.calendar.wordId
import uz.abumme.harfgame.backend.admin.calendar.wordOn
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
import uz.abumme.harfgame.data.admin.answerpool.CreatePairRequest
import uz.abumme.harfgame.data.admin.answerpool.CyrlStatus
import uz.abumme.harfgame.data.admin.answerpool.CyrlStatusDto
import uz.abumme.harfgame.data.admin.answerpool.MarkItemResultDto
import uz.abumme.harfgame.data.admin.answerpool.MarkOutcome
import uz.abumme.harfgame.data.admin.answerpool.MarkWordsRequest
import uz.abumme.harfgame.data.admin.answerpool.PairDto
import uz.abumme.harfgame.data.admin.answerpool.PoolCandidateDto
import uz.abumme.harfgame.data.admin.answerpool.PoolPageDto
import uz.abumme.harfgame.data.admin.audit.AuditActions
import uz.abumme.harfgame.data.api.ApiErrorResponse
import java.time.Duration
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AnswerPoolRoutesTest {
    private val clock = MutableClock(CALENDAR_NOW)

    @BeforeTest
    fun setup() = resetCalendarData()

    private suspend fun HttpClient.pool(session: StaffSession, calendar: String, query: String = ""): PoolPageDto =
        get(AdminRoutes.answerPool(calendar) + query) { withSession(session) }.body()

    private suspend fun HttpClient.mark(session: StaffSession, calendar: String, request: MarkWordsRequest): HttpResponse =
        post(AdminRoutes.answerPoolWords(calendar)) { withSession(session); jsonBody(request) }

    @Test
    fun markingReportsEveryOutcomeAndPublishesTheValidWords() = testApplication {
        insertStaff("boss", Role.ADMIN)
        val admin = calendarBackend(clock)
        migrateCalendars(admin.catalog)
        val client = adminClient(admin)
        val boss = client.signIn("boss")
        client.post(AdminRoutes.wordRemove(wordId("en", "grape"))) { withSession(boss) }
        val version = pack("en").version

        val response = client.mark(
            boss, "en",
            MarkWordsRequest(
                wordIds = listOf(wordId("en", "melon"), "no-such-id"),
                texts = listOf("Lemon", "apple", "zzzzq", "grape", "oak", "lemon"),
            ),
        )

        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        val results = response.body<List<MarkItemResultDto>>()
        assertEquals(
            listOf(
                MarkOutcome.MARKED, MarkOutcome.NOT_IN_CATALOG, MarkOutcome.MARKED, MarkOutcome.ALREADY_ELIGIBLE,
                MarkOutcome.NOT_IN_CATALOG, MarkOutcome.REMOVED, MarkOutcome.UNSUPPORTED_LENGTH, MarkOutcome.ALREADY_ELIGIBLE,
            ),
            results.map { it.outcome },
        )
        assertEquals("lemon", results[2].text)
        assertEquals("Lemon", results[2].input)
        val published = pack("en")
        assertTrue("lemon" in published.answers && "melon" in published.answers)
        assertFalse("grape" in published.answers || "oak" in published.answers)
        assertNotEquals(version, published.version)
        val marked = auditRows().filter { it[StaffAuditLogTable.action] == AuditActions.DAILY_ELIGIBILITY_MARKED }
        assertEquals(2, marked.size)
        assertTrue(marked.all { it[StaffAuditLogTable.lang] == "en" && it[StaffAuditLogTable.actorKind] == "STAFF" })

        // Candidates are active words of a board length that are not eligible yet.
        val candidates = client.get(AdminRoutes.answerPoolCandidates("en")) { withSession(boss) }.body<PageDto<PoolCandidateDto>>()
        assertEquals(listOf("tiger"), candidates.items.map { it.text })

        assertEquals(ApiErrorResponse("validation_failed", "calendar: pairs_only"), client.mark(boss, "uz", MarkWordsRequest(texts = listOf("daryo"))).error())
        assertEquals(HttpStatusCode.NotFound, client.mark(boss, "xx", MarkWordsRequest(texts = listOf("a"))).status)
    }

    @Test
    fun theNeverUsedCounterGrowsWithThePoolAndDropsWhenAWordIsPlayed() = testApplication {
        insertStaff("boss", Role.ADMIN)
        val admin = calendarBackend(clock)
        migrateCalendars(admin.catalog)
        val client = adminClient(admin)
        val boss = client.signIn("boss")

        val start = client.pool(boss, "en")
        val usedNow = calendarRows("en").filter { it[DailyWordsTable.day] in TODAY..TODAY.plusDays(1) }.map { it[DailyWordsTable.text] }.toSet()
        assertEquals(EN_POOL.count { it !in usedNow }, start.unusedLeft)
        assertEquals(EN_POOL.size.toLong(), start.page.total)

        client.mark(boss, "en", MarkWordsRequest(texts = listOf("tiger")))
        assertEquals(start.unusedLeft + 1, client.pool(boss, "en").unusedLeft)

        // A day later the never-used word of the day after tomorrow has become tomorrow's.
        val dayAfterTomorrow = dayRow("en", TODAY.plusDays(2))!!
        assertFalse(dayAfterTomorrow[DailyWordsTable.isRepeat])
        clock.advance(Duration.ofDays(1))
        DailyCalendarScheduler(CalendarService(admin.catalog)).tick(clock.now)
        val later = client.pool(client.signIn("boss"), "en") // a day idle ends the earlier session
        assertEquals(start.unusedLeft, later.unusedLeft)
        assertEquals(TODAY.plusDays(2).toString(), later.page.items.single { it.text == dayAfterTomorrow[DailyWordsTable.text] }.lastUsed)
    }

    @Test
    fun unmarkingLeavesThePoolButNeverTouchesTodayOrTomorrow() = testApplication {
        insertStaff("boss", Role.ADMIN)
        val admin = calendarBackend(clock)
        migrateCalendars(admin.catalog)
        val client = adminClient(admin)
        val boss = client.signIn("boss")
        val todayRow = dayRow("en", TODAY)!!
        val tomorrowText = dayRow("en", TODAY.plusDays(1))!![DailyWordsTable.text]
        val word = todayRow[DailyWordsTable.text]

        val response = client.delete(AdminRoutes.answerPoolWord("en", todayRow[DailyWordsTable.wordId]!!)) { withSession(boss) }

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals(word, dayRow("en", TODAY)!![DailyWordsTable.text])
        assertEquals(tomorrowText, dayRow("en", TODAY.plusDays(1))!![DailyWordsTable.text])
        assertEquals(word, pack("en").wordOn(TODAY))
        assertFalse(word in pack("en").answers)
        assertTrue(calendarRows("en").none { it[DailyWordsTable.day] >= TODAY.plusDays(2) && it[DailyWordsTable.text] == word })
        assertTrue(client.pool(boss, "en").page.items.none { it.text == word })
        assertEquals(1, auditRows().count { it[StaffAuditLogTable.action] == AuditActions.DAILY_ELIGIBILITY_UNMARKED })
        // A word that is not in the pool: nothing to do.
        assertEquals(HttpStatusCode.NoContent, client.delete(AdminRoutes.answerPoolWord("en", todayRow[DailyWordsTable.wordId]!!)) { withSession(boss) }.status)
        assertEquals(1, auditRows().count { it[StaffAuditLogTable.action] == AuditActions.DAILY_ELIGIBILITY_UNMARKED })
    }

    @Test
    fun uzbekPairsAreCreatedFromConfirmedSpellingsAndPublishBothScripts() = testApplication {
        insertStaff("boss", Role.ADMIN)
        val admin = calendarBackend(clock)
        migrateCalendars(admin.catalog)
        val client = adminClient(admin)
        val boss = client.signIn("boss")
        val versions = listOf("uz-latn", "uz-cyrl").associateWith { pack(it).version }

        val paired = client.get("${AdminRoutes.ANSWER_POOL_UZ_CYRL_STATUS}?cyrl=%D0%A8%D0%B0%D2%B3%D0%B0%D1%80") { withSession(boss) }.body<CyrlStatusDto>()
        assertEquals(CyrlStatusDto("шаҳар", wordId("uz-cyrl", "шаҳар"), CyrlStatus.ACTIVE, pairedWith = "shahar"), paired)
        val missing = client.get("${AdminRoutes.ANSWER_POOL_UZ_CYRL_STATUS}?cyrl=%D0%B4%D0%B0%D1%80%D1%91") { withSession(boss) }.body<CyrlStatusDto>()
        assertEquals(CyrlStatus.MISSING, missing.cyrlStatus)

        val candidates = client.get(AdminRoutes.answerPoolCandidates("uz")) { withSession(boss) }.body<PageDto<PoolCandidateDto>>()
        assertEquals(listOf("bolta", "daryo", "osmon"), candidates.items.map { it.text })

        val daryo = wordId("uz-latn", "daryo")
        val notInCatalog = client.post(AdminRoutes.ANSWER_POOL_UZ_PAIRS) { withSession(boss); jsonBody(CreatePairRequest(daryo, "дарё")) }
        assertEquals(ApiErrorResponse("validation_failed", "cyrlText: not_in_catalog"), notInCatalog.error())
        val invalid = client.post(AdminRoutes.ANSWER_POOL_UZ_PAIRS) { withSession(boss); jsonBody(CreatePairRequest(daryo, "дар", addCyrlToCatalog = true)) }
        assertEquals(ApiErrorResponse("validation_failed", "cyrlText: bad_length"), invalid.error())

        val created = client.post(AdminRoutes.ANSWER_POOL_UZ_PAIRS) { withSession(boss); jsonBody(CreatePairRequest(daryo, "Дарё", addCyrlToCatalog = true)) }

        assertEquals(HttpStatusCode.Created, created.status, created.bodyAsText())
        val pair = created.body<PairDto>()
        assertEquals("daryo", pair.latnText)
        assertEquals("дарё", pair.cyrlText)
        assertEquals("boss", pair.createdBy?.username)
        assertTrue("daryo" in pack("uz-latn").answers)
        assertTrue("дарё" in pack("uz-cyrl").answers && "дарё" in pack("uz-cyrl").guesses)
        for ((lang, version) in versions) assertNotEquals(version, pack(lang).version, lang)
        assertEquals(1, auditRows().count { it[StaffAuditLogTable.action] == AuditActions.DAILY_PAIR_CREATED })
        assertEquals(1, auditRows().count { it[StaffAuditLogTable.action] == AuditActions.WORD_ADDED })
        assertTrue(client.pool(boss, "uz").page.items.any { it.pairId == pair.id && it.textCyrl == "дарё" })

        // A word belongs to one pair.
        val takenLatin = client.post(AdminRoutes.ANSWER_POOL_UZ_PAIRS) { withSession(boss); jsonBody(CreatePairRequest(wordId("uz-latn", "kitob"), "осмон")) }
        assertEquals(ApiErrorResponse("conflict", "latnWordId: paired kitob/китоб"), takenLatin.error())
        val takenCyrillic = client.post(AdminRoutes.ANSWER_POOL_UZ_PAIRS) { withSession(boss); jsonBody(CreatePairRequest(wordId("uz-latn", "osmon"), "китоб")) }
        assertEquals(HttpStatusCode.Conflict, takenCyrillic.status)
        assertEquals(ApiErrorResponse("conflict", "cyrlText: paired kitob/китоб"), takenCyrillic.error())

        // Removing the pair removes the eligibility of both words.
        assertEquals(HttpStatusCode.NoContent, client.delete(AdminRoutes.answerPoolUzPair(pair.id)) { withSession(boss) }.status)
        assertFalse("daryo" in pack("uz-latn").answers)
        assertFalse("дарё" in pack("uz-cyrl").answers)
        assertEquals(HttpStatusCode.NotFound, client.delete(AdminRoutes.answerPoolUzPair(pair.id)) { withSession(boss) }.status)
    }

    @Test
    fun aWorderIsRefusedEveryAnswerPoolRouteAndNoSessionIsUnauthorized() = testApplication {
        insertStaff("dilnoza", Role.WORDER, listOf("en", "uz-latn", "uz-cyrl"))
        val admin = calendarBackend(clock)
        migrateCalendars(admin.catalog)
        val client = adminClient(admin)
        val worder = client.signIn("dilnoza")
        val eligibleBefore = pack("en").answers
        val apple = wordId("en", "apple")

        val attempts: List<suspend (StaffSession?) -> HttpResponse> = listOf(
            { s -> client.get(AdminRoutes.answerPool("en")) { s?.let { withSession(it) } } },
            { s -> client.get(AdminRoutes.answerPoolCandidates("en")) { s?.let { withSession(it) } } },
            { s -> client.post(AdminRoutes.answerPoolWords("en")) { s?.let { withSession(it) }; jsonBody(MarkWordsRequest(texts = listOf("tiger"))) } },
            { s -> client.delete(AdminRoutes.answerPoolWord("en", apple)) { s?.let { withSession(it) } } },
            { s -> client.get("${AdminRoutes.ANSWER_POOL_UZ_CYRL_STATUS}?cyrl=x") { s?.let { withSession(it) } } },
            { s -> client.post(AdminRoutes.ANSWER_POOL_UZ_PAIRS) { s?.let { withSession(it) }; jsonBody(CreatePairRequest(apple, "осмон")) } },
            { s -> client.delete(AdminRoutes.answerPoolUzPair("any")) { s?.let { withSession(it) } } },
        )
        for (attempt in attempts) {
            val forbidden = attempt(worder)
            assertEquals(HttpStatusCode.Forbidden, forbidden.status)
            assertEquals("forbidden", forbidden.error().error)
            assertEquals(HttpStatusCode.Unauthorized, attempt(null).status)
        }
        assertEquals(eligibleBefore, pack("en").answers)
        assertTrue(auditRows().none { it[StaffAuditLogTable.action].startsWith(AuditActions.DAILY_PREFIX) })
    }
}
