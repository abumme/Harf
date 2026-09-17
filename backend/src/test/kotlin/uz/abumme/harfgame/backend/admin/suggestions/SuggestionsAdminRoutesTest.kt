package uz.abumme.harfgame.backend.admin.suggestions

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.admin.StaffSession
import uz.abumme.harfgame.backend.admin.adminClient
import uz.abumme.harfgame.backend.admin.error
import uz.abumme.harfgame.backend.admin.insertStaff
import uz.abumme.harfgame.backend.admin.jsonBody
import uz.abumme.harfgame.backend.admin.resetAdminData
import uz.abumme.harfgame.backend.admin.signIn
import uz.abumme.harfgame.backend.admin.withSession
import uz.abumme.harfgame.backend.db.WordSuggestionsTable
import uz.abumme.harfgame.backend.db.WordsTable
import uz.abumme.harfgame.backend.dictionary.ReviewReason
import uz.abumme.harfgame.backend.insertSuggestion
import uz.abumme.harfgame.backend.service.SuggestionServerService
import uz.abumme.harfgame.backend.service.WordPackServerService
import uz.abumme.harfgame.backend.statusOf
import uz.abumme.harfgame.backend.suggestionColumn
import uz.abumme.harfgame.backend.wordRow
import uz.abumme.harfgame.data.admin.AdminRoutes
import uz.abumme.harfgame.data.admin.PageDto
import uz.abumme.harfgame.data.admin.Role
import uz.abumme.harfgame.data.admin.suggestions.DeciderDto
import uz.abumme.harfgame.data.admin.suggestions.DeciderKind
import uz.abumme.harfgame.data.admin.suggestions.DecisionRequest
import uz.abumme.harfgame.data.admin.suggestions.SuggestionDto
import uz.abumme.harfgame.data.admin.suggestions.SuggestionParams
import uz.abumme.harfgame.data.admin.words.WordSource
import uz.abumme.harfgame.data.api.ApiErrorResponse
import uz.abumme.harfgame.data.suggestion.SuggestionStatus
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SuggestionsAdminRoutesTest {

    private val t0 = Instant.parse("2026-09-15T08:00:00Z")

    @BeforeTest
    fun setup() {
        resetAdminData()
    }

    private fun pending(lang: String, word: String, minutes: Long) =
        insertSuggestion(lang, word, createdAt = t0.plusSeconds(minutes * 60), author = null)

    private suspend fun HttpClient.suggestions(session: StaffSession, query: String = ""): HttpResponse =
        get("${AdminRoutes.SUGGESTIONS}?$query") { withSession(session) }

    private suspend fun HttpClient.decide(session: StaffSession, id: String, accept: Boolean): HttpResponse =
        post(AdminRoutes.suggestionDecision(id)) { withSession(session); jsonBody(DecisionRequest(accept)) }

    @Test
    fun aWorderSeesOnlyTheirLanguagesPendingOldestFirst() = testApplication {
        insertStaff("aigerim", Role.WORDER, listOf("kk"))
        val newer = pending("kk", "қалам", minutes = 10)
        val older = pending("kk", "кітап", minutes = 1)
        pending("ru", "книги", minutes = 0)
        val client = adminClient()
        val worder = client.signIn("aigerim")

        val all = client.suggestions(worder).body<PageDto<SuggestionDto>>()
        assertEquals(listOf(older, newer), all.items.map { it.id })
        assertEquals(2, all.total)
        assertEquals("Аноним", all.items.first().author)
        assertEquals(listOf(older, newer), client.suggestions(worder, "${SuggestionParams.LANG}=kk").body<PageDto<SuggestionDto>>().items.map { it.id })
        assertEquals(HttpStatusCode.Forbidden, client.suggestions(worder, "${SuggestionParams.LANG}=ru").status)
    }

    @Test
    fun pendingSuggestionsShowWhyTheyNeedReview() = testApplication {
        insertStaff("boss", Role.ADMIN)
        val queued = pending("en", "zqxta", minutes = 1)
        val reviewed = pending("en", "plumb", minutes = 2)
        runBlocking { SuggestionServerService(WordPackServerService()).markPosted(reviewed, ReviewReason.REMOVED_BY_STAFF) }
        val client = adminClient()
        val boss = client.signIn("boss")

        val items = client.suggestions(boss, "${SuggestionParams.LANG}=en").body<PageDto<SuggestionDto>>().items
        assertNull(items.single { it.id == queued }.reason)
        assertEquals("REMOVED_BY_STAFF", items.single { it.id == reviewed }.reason)
    }

    @Test
    fun decidedSuggestionsAreListedNewestFirstWithTheirDecider() = testApplication {
        val bossId = insertStaff("boss", Role.ADMIN)
        insertSuggestion("en", "older", SuggestionStatus.ACCEPTED, decidedVia = "AUTO", decidedAt = t0, author = null)
            .also { setDecidedBy(it, "wiktionary") }
        insertSuggestion("en", "newer", SuggestionStatus.REJECTED, decidedVia = "EDITOR", decidedAt = t0.plusSeconds(60), author = null)
            .also { setDecidedBy(it, "42") }
        insertSuggestion("en", "panel", SuggestionStatus.ACCEPTED, decidedVia = "EDITOR", decidedAt = t0.plusSeconds(120), author = null)
            .also { setDecidedBy(it, "staff:$bossId") }
        pending("en", "hello", minutes = 0)
        val client = adminClient()
        val boss = client.signIn("boss")

        val decided = client.suggestions(boss, "${SuggestionParams.LANG}=en&${SuggestionParams.STATUS}=DECIDED").body<PageDto<SuggestionDto>>()
        assertEquals(listOf("panel", "newer", "older"), decided.items.map { it.word })
        assertEquals(
            listOf(DeciderDto(DeciderKind.STAFF, "boss"), DeciderDto(DeciderKind.TELEGRAM, "42"), DeciderDto(DeciderKind.AUTO, "wiktionary")),
            decided.items.map { it.decidedBy },
        )
        assertEquals(t0.plusSeconds(120).toEpochMilli(), decided.items.first().decidedAt)
    }

    @Test
    fun aDecisionOutsideTheWordersLanguagesIsRefused() = testApplication {
        insertStaff("aigerim", Role.WORDER, listOf("kk"))
        val russian = pending("ru", "книги", minutes = 0)
        val client = adminClient()
        val worder = client.signIn("aigerim")

        val response = client.decide(worder, russian, accept = true)

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(SuggestionStatus.PENDING.name, statusOf(russian))
        assertNull(wordRow("ru", "книги"))
    }

    @Test
    fun acceptingInThePanelAddsThePublishedWordAndIsAttributedToTheStaffMember() = testApplication {
        val staffId = insertStaff("aigerim", Role.WORDER, listOf("kk"))
        val id = pending("kk", "қалам", minutes = 0)
        val client = adminClient()
        val worder = client.signIn("aigerim")

        val response = client.decide(worder, id, accept = true)

        assertEquals(HttpStatusCode.OK, response.status)
        val decided = response.body<SuggestionDto>()
        assertEquals(SuggestionStatus.ACCEPTED, decided.status)
        assertEquals(DeciderDto(DeciderKind.STAFF, "aigerim"), decided.decidedBy)
        assertEquals("staff:$staffId", suggestionColumn(id, WordSuggestionsTable.decidedBy))
        assertEquals(WordSource.SUGGESTION.name, wordRow("kk", "қалам")!![WordsTable.wordSource])
        val pack = runBlocking { WordPackServerService().getPack("kk")!! }
        assertEquals("2", pack.version)
        assertTrue("қалам" in pack.guesses)

        val again = client.decide(worder, id, accept = false)
        assertEquals(HttpStatusCode.Conflict, again.status)
        assertEquals(ApiErrorResponse("conflict", "status: already_decided"), again.error())
        assertEquals(SuggestionStatus.ACCEPTED.name, statusOf(id))
    }

    @Test
    fun aLegacyWordThatFailsValidationCannotBeAcceptedButCanBeRejected() = testApplication {
        insertStaff("boss", Role.ADMIN)
        val id = pending("en", "cat", minutes = 0)
        val client = adminClient()
        val boss = client.signIn("boss")

        val refused = client.decide(boss, id, accept = true)
        assertEquals(HttpStatusCode.UnprocessableEntity, refused.status)
        assertEquals(ApiErrorResponse("validation_failed", "word: bad_length"), refused.error())
        assertEquals(SuggestionStatus.PENDING.name, statusOf(id))

        assertEquals(HttpStatusCode.OK, client.decide(boss, id, accept = false).status)
        assertEquals(SuggestionStatus.REJECTED.name, statusOf(id))
        assertEquals(HttpStatusCode.NotFound, client.decide(boss, "no-such-id", accept = true).status)
    }

    private fun setDecidedBy(id: String, decidedBy: String) {
        transaction(DatabaseFactory.init()) {
            WordSuggestionsTable.update({ WordSuggestionsTable.id eq id }) { it[WordSuggestionsTable.decidedBy] = decidedBy }
        }
    }
}
