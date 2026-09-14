package uz.abumme.harfgame.backend

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.UsersTable
import uz.abumme.harfgame.backend.db.WordSuggestionsTable
import uz.abumme.harfgame.backend.service.DecideOutcome
import uz.abumme.harfgame.backend.service.DecidedVia
import uz.abumme.harfgame.backend.service.SuggestOutcome
import uz.abumme.harfgame.backend.service.SuggestionServerService
import uz.abumme.harfgame.backend.service.WordPackServerService
import uz.abumme.harfgame.data.api.ApiRoutes
import uz.abumme.harfgame.data.auth.AnonymousAuthResponse
import uz.abumme.harfgame.data.suggestion.SuggestWordRequest
import uz.abumme.harfgame.data.suggestion.SuggestWordResponse
import uz.abumme.harfgame.data.suggestion.SuggestionStatus
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SuggestWordsTest {

    @BeforeTest
    fun setup() = resetSuggestionData()

    private fun svc(cap: Int = 10) = SuggestionServerService(WordPackServerService(), dailyCap = cap)

    // ---- validation matrix (2.3) ----

    @Test
    fun validSuggestionIsStored() = runBlocking {
        val out = svc().suggest("u1", "en", "hello")
        assertTrue(out is SuggestOutcome.Stored)
    }

    @Test
    fun badLengthRejected() = runBlocking {
        assertEquals(SuggestOutcome.Rejected("bad_length"), svc().suggest("u1", "en", "a"))
    }

    @Test
    fun offensiveRejected() = runBlocking {
        val out = svc().suggest("u1", "en", "shit")
        assertTrue(out is SuggestOutcome.Rejected && out.reason == "offensive")
    }

    @Test
    fun gibberishRepeatRunRejected() = runBlocking {
        val out = svc().suggest("u1", "en", "haaad")
        assertTrue(out is SuggestOutcome.Rejected && out.reason == "repeat_run")
    }

    @Test
    fun gibberishTooFewDistinctRejected() = runBlocking {
        val out = svc().suggest("u1", "en", "abab")
        assertTrue(out is SuggestOutcome.Rejected && out.reason == "too_few_distinct")
    }

    @Test
    fun gibberishNoVowelRejected() = runBlocking {
        val out = svc().suggest("u1", "en", "bcdfg")
        assertTrue(out is SuggestOutcome.Rejected && out.reason == "no_vowel")
    }

    @Test
    fun alreadyInPackRejected() = runBlocking {
        insertPack("en", "1", listOf("salom"))
        val out = svc().suggest("u1", "en", "salom")
        assertTrue(out is SuggestOutcome.Rejected && out.reason == "already_present")
    }

    @Test
    fun duplicatePendingIsCollapsed() = runBlocking {
        insertPending("en", "table")
        assertEquals(SuggestOutcome.DuplicatePending, svc().suggest("u1", "en", "table"))
        // No second pending row created.
        val count = transaction(DatabaseFactory.init()) {
            WordSuggestionsTable.selectAll().where { WordSuggestionsTable.word eq "table" }.count()
        }
        assertEquals(1, count)
    }

    @Test
    fun overDailyCapRejected() = runBlocking {
        assertEquals(SuggestOutcome.OverCap, svc(cap = 0).suggest("u1", "en", "hello"))
    }

    // ---- decision / accept path (3.6) ----

    @Test
    fun acceptAddsWordToPackAndBumpsVersion() = runBlocking {
        insertPack("en", "1", listOf("x"))
        val id = insertPending("en", "hello")
        val out = svc().decide(id, accept = true, editor = "42")
        assertTrue(out is DecideOutcome.Applied && out.accepted)
        assertEquals(SuggestionStatus.ACCEPTED.name, statusOf(id))
        val pack = WordPackServerService().getPack("en")!!
        assertTrue("hello" in pack.guesses)
        assertEquals("2", pack.version)
    }

    @Test
    fun rejectLeavesPackUnchanged() = runBlocking {
        insertPack("en", "1", listOf("x"))
        val id = insertPending("en", "hello")
        val out = svc().decide(id, accept = false, editor = "42")
        assertTrue(out is DecideOutcome.Applied && !out.accepted)
        assertEquals(SuggestionStatus.REJECTED.name, statusOf(id))
        val pack = WordPackServerService().getPack("en")!!
        assertFalse("hello" in pack.guesses)
        assertEquals("1", pack.version)
    }

    @Test
    fun secondDecisionIsNoOp() = runBlocking {
        insertPack("en", "1", listOf("x"))
        val id = insertPending("en", "hello")
        svc().decide(id, accept = true, editor = "42")
        assertEquals(DecideOutcome.AlreadyDecided, svc().decide(id, accept = false, editor = "42"))
    }

    @Test
    fun decideUnknownIsNotFound() = runBlocking {
        assertEquals(DecideOutcome.NotFound, svc().decide("nope", accept = true, editor = "42"))
    }

    @Test
    fun concurrentDecisionsApplyOnce() = runBlocking {
        insertPack("en", "1", listOf("x"))
        val id = insertPending("en", "hello")
        val service = svc()
        val gate = CompletableDeferred<Unit>()
        val decisions = (1..6).map { n ->
            async(Dispatchers.IO) { gate.await(); service.decide(id, accept = n % 2 == 0, editor = "$n") }
        }
        gate.complete(Unit)
        val outcomes = decisions.awaitAll()

        assertEquals(1, outcomes.count { it is DecideOutcome.Applied })
        assertEquals(5, outcomes.count { it == DecideOutcome.AlreadyDecided })
        // The pack agrees with the one decision that took effect.
        val accepted = statusOf(id) == SuggestionStatus.ACCEPTED.name
        assertEquals(accepted, "hello" in WordPackServerService().getPack("en")!!.guesses)
    }

    @Test
    fun decideRecordsDecisionSource() = runBlocking {
        insertPack("en", "1", listOf("x"))
        val auto = insertPending("en", "hello")
        val manual = insertPending("en", "world")
        svc().decide(auto, accept = true, editor = "wiktionary", via = DecidedVia.AUTO)
        svc().decide(manual, accept = true, editor = "42")
        assertEquals("AUTO", suggestionColumn(auto, WordSuggestionsTable.decidedVia))
        assertEquals("EDITOR", suggestionColumn(manual, WordSuggestionsTable.decidedVia))
    }

    // Telegram bot behaviour (routing, messages, decision taps, polling) lives in telegram/TelegramBotTest.

    @Test
    fun storedSuggestionIsQueuedForReview() = runBlocking {
        val out = svc().suggest("u1", "en", "hello") as SuggestOutcome.Stored
        assertEquals("QUEUED", suggestionColumn(out.id, WordSuggestionsTable.reviewState))
    }

    @Test
    fun authorLabelIsDisplayNameElseAnonymous() = runBlocking {
        transaction(DatabaseFactory.init()) {
            UsersTable.insert { it[id] = "u2"; it[createdAt] = Instant.now(); it[name] = "Grace" }
            UsersTable.insert { it[id] = "u3"; it[createdAt] = Instant.now(); it[name] = "  " }
        }
        assertEquals("Grace", svc().authorLabel("u2"))
        assertEquals("Аноним", svc().authorLabel("u1")) // no display name
        assertEquals("Аноним", svc().authorLabel("u3")) // blank display name
        assertEquals("Аноним", svc().authorLabel(null)) // author account deleted
    }

    @Test
    fun parseTelegramTopicsParsesPairsAndSkipsJunk() {
        assertEquals(
            mapOf("en" to 2, "ru" to 3, "uz-latn" to 4),
            parseTelegramTopics("en=2, ru=3 ,uz-latn=4, bad, x=notint, =5")
        )
        assertEquals(emptyMap(), parseTelegramTopics(null))
        assertEquals(emptyMap(), parseTelegramTopics(""))
    }

    // ---- route integration (4.2) ----

    @Test
    fun authenticatedSuggestionStoredStoreOnly() = testApplication {
        application { module() } // no Telegram env -> store-only, no crash
        val client = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

        val anon = client.post(ApiRoutes.AUTH_ANONYMOUS).body<AnonymousAuthResponse>()
        val resp = client.post(ApiRoutes.SUGGESTIONS) {
            header(HttpHeaders.Authorization, "Bearer ${anon.tokens.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(SuggestWordRequest(lang = "en", word = "world"))
        }
        assertEquals(HttpStatusCode.Accepted, resp.status)
        assertEquals(SuggestionStatus.PENDING.name, resp.body<SuggestWordResponse>().status)

        // One row, queued: the review worker — not this request — takes it to Telegram.
        val reviewState = transaction(DatabaseFactory.init()) {
            WordSuggestionsTable.selectAll().where { WordSuggestionsTable.word eq "world" }.single()[WordSuggestionsTable.reviewState]
        }
        assertEquals("QUEUED", reviewState)
    }

    @Test
    fun unauthenticatedSuggestionRejected() = testApplication {
        application { module() }
        val client = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val resp = client.post(ApiRoutes.SUGGESTIONS) {
            contentType(ContentType.Application.Json)
            setBody(SuggestWordRequest(lang = "en", word = "world"))
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }
}
