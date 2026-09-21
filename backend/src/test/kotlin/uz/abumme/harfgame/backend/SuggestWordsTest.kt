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
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import uz.abumme.harfgame.backend.admin.auditRows
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.StaffAuditLogTable
import uz.abumme.harfgame.backend.db.UsersTable
import uz.abumme.harfgame.backend.db.WordSuggestionsTable
import uz.abumme.harfgame.backend.db.WordsTable
import uz.abumme.harfgame.data.admin.audit.AuditActions
import uz.abumme.harfgame.data.admin.words.WordSource
import uz.abumme.harfgame.data.admin.words.WordStatus
import uz.abumme.harfgame.data.api.ApiErrorResponse
import uz.abumme.harfgame.backend.service.DecideOutcome
import uz.abumme.harfgame.backend.service.DecidedVia
import uz.abumme.harfgame.backend.service.SuggestOutcome
import uz.abumme.harfgame.backend.service.SuggestionServerService
import uz.abumme.harfgame.backend.service.WordPackServerService
import uz.abumme.harfgame.data.api.ApiRoutes
import uz.abumme.harfgame.data.auth.AnonymousAuthResponse
import uz.abumme.harfgame.data.suggestion.SuggestWordRequest
import uz.abumme.harfgame.data.suggestion.SuggestWordResponse
import uz.abumme.harfgame.data.suggestion.SuggestionErrors
import uz.abumme.harfgame.data.suggestion.SuggestionStatus
import uz.abumme.harfgame.data.sync.ResultRecordDto
import uz.abumme.harfgame.data.sync.UserStatsDto
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
        // The catalog keeps the origin: automatic or editor acceptance, linked to the suggestion.
        assertEquals(WordSource.AUTO.name, wordRow("en", "hello")!![WordsTable.wordSource])
        assertEquals(auto, wordRow("en", "hello")!![WordsTable.suggestionId])
        assertEquals(WordSource.SUGGESTION.name, wordRow("en", "world")!![WordsTable.wordSource])
        assertEquals(manual, wordRow("en", "world")!![WordsTable.suggestionId])
    }

    // ---- the word catalog (word-catalog 4.1, 4.2) ----

    @Test
    fun wordWithAForeignLetterIsNotPlayable() = runBlocking {
        assertEquals(SuggestOutcome.Rejected("not_playable"), svc().suggest("u1", "en", "héllo"))
        assertEquals(SuggestOutcome.Rejected("not_playable"), svc().suggest("u1", "ru", "книgа"))
        assertEquals(0, suggestionCount())
    }

    @Test
    fun wordOfAnUnsupportedGraphemeLengthIsNotPlayable() = runBlocking {
        // "choy" is four characters but three Uzbek letters (ch-o-y); boards hold 4..7.
        assertEquals(SuggestOutcome.Rejected("not_playable"), svc().suggest("u1", "uz-latn", "choy"))
        assertEquals(SuggestOutcome.Rejected("not_playable"), svc().suggest("u1", "en", "strawberry"))
        assertEquals(0, suggestionCount())
        assertTrue(svc().suggest("u1", "uz-latn", "shahar") is SuggestOutcome.Stored) // sh-a-h-a-r: five letters
    }

    @Test
    fun blocklistedVariantIsOffensive() = runBlocking {
        assertEquals(SuggestOutcome.Rejected("offensive"), svc().suggest("u1", "en", "SHIT"))
    }

    @Test
    fun aWordStaffRemovedCanBeSuggestedAgain() = runBlocking {
        insertPack("en", "1", listOf("crane"))
        removeWord("en", "crane")
        assertTrue(svc().suggest("u1", "en", "crane") is SuggestOutcome.Stored)
    }

    @Test
    fun unplayableSuggestionIsRejectedByTheRoute() = testApplication {
        application { module() }
        val client = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val anon = client.post(ApiRoutes.AUTH_ANONYMOUS).body<AnonymousAuthResponse>()

        val resp = client.post(ApiRoutes.SUGGESTIONS) {
            header(HttpHeaders.Authorization, "Bearer ${anon.tokens.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(SuggestWordRequest(lang = "uz-latn", word = "choy"))
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
        assertEquals(ApiErrorResponse("rejected", "not_playable"), resp.body<ApiErrorResponse>())
        assertEquals(0, suggestionCount())
    }

    @Test
    fun acceptingAnAlreadyActiveWordDoesNotRepublish() = runBlocking {
        insertPack("en", "1", listOf("hello"))
        val id = insertPending("en", "hello")
        assertTrue(svc().decide(id, accept = true, editor = "42") is DecideOutcome.Applied)
        assertEquals(SuggestionStatus.ACCEPTED.name, statusOf(id))
        assertEquals("1", WordPackServerService().getPack("en")!!.version)
        assertEquals(WordSource.BUNDLED.name, wordRow("en", "hello")!![WordsTable.wordSource])
    }

    @Test
    fun acceptingARemovedWordRestoresIt() = runBlocking {
        insertPack("en", "1", listOf("hello"))
        removeWord("en", "hello")
        val id = insertPending("en", "hello")

        assertTrue(svc().decide(id, accept = true, editor = "42") is DecideOutcome.Applied)

        assertEquals(WordStatus.ACTIVE.name, wordRow("en", "hello")!![WordsTable.status])
        val pack = WordPackServerService().getPack("en")!!
        assertTrue("hello" in pack.guesses)
        assertEquals("2", pack.version) // the direct removal published nothing
    }

    @Test
    fun anInvalidLegacyWordStaysPending() = runBlocking {
        insertPack("en", "1", listOf("x"))
        val id = insertPending("en", "cat") // stored before suggestions had to be playable

        assertEquals(DecideOutcome.Invalid("bad_length"), svc().decide(id, accept = true, editor = "42"))

        assertEquals(SuggestionStatus.PENDING.name, statusOf(id))
        assertEquals(null, suggestionColumn(id, WordSuggestionsTable.decidedBy))
        assertEquals(null, wordRow("en", "cat"))
        assertEquals("1", WordPackServerService().getPack("en")!!.version)
        // It can still be rejected.
        assertTrue(svc().decide(id, accept = false, editor = "42") is DecideOutcome.Applied)
    }

    @Test
    fun decisionsAreAudited() = runBlocking {
        insertPack("en", "1", listOf("x"))
        val id = insertPending("en", "hello")
        svc().decide(id, accept = true, editor = "wiktionary", via = DecidedVia.AUTO)

        val rows = auditRows()
        val decided = rows.single { it[StaffAuditLogTable.action] == AuditActions.SUGGESTION_DECIDED }
        assertEquals("SYSTEM", decided[StaffAuditLogTable.actorKind])
        assertEquals(id, decided[StaffAuditLogTable.targetId])
        assertEquals("en", decided[StaffAuditLogTable.lang])
        val added = rows.single { it[StaffAuditLogTable.action] == AuditActions.WORD_ADDED }
        assertEquals("SYSTEM", added[StaffAuditLogTable.actorKind])
        assertTrue("\"hello\"" in added[StaffAuditLogTable.details]!!)
    }

    private fun suggestionCount() = transaction(DatabaseFactory.init()) { WordSuggestionsTable.selectAll().count() }

    /** Marks a catalog word REMOVED directly, as a staff removal would. */
    private fun removeWord(lang: String, text: String) = transaction(DatabaseFactory.init()) {
        WordsTable.update({ (WordsTable.lang eq lang) and (WordsTable.text eq text) }) {
            it[status] = WordStatus.REMOVED.name
            it[removedAt] = Instant.now()
        }
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

    // ---- blocked accounts (player-accounts 3.4) ----

    /** Blocks or unblocks [userId]'s suggestions directly, as an ADMIN's block would. */
    private fun setBlocked(userId: String, blocked: Boolean) = transaction(DatabaseFactory.init()) {
        UsersTable.update({ UsersTable.id eq userId }) {
            it[suggestionsBlockedAt] = if (blocked) Instant.now() else null
            it[suggestionsBlockedBy] = if (blocked) "staff-1" else null
        }
    }

    /** What the daily cap counts: the author's suggestions of the last 24 hours. */
    private fun capCount(userId: String) = transaction(DatabaseFactory.init()) {
        WordSuggestionsTable.selectAll().where {
            (WordSuggestionsTable.suggestedBy eq userId) and (WordSuggestionsTable.createdAt greater Instant.now().minusSeconds(24 * 3600))
        }.count()
    }

    @Test
    fun aBlockedAccountIsRefusedBeforeAnyValidation() = runBlocking {
        insertPending("en", "table")
        setBlocked("u1", blocked = true)

        assertEquals(SuggestOutcome.Blocked, svc().suggest("u1", "en", "hello"))
        assertEquals(SuggestOutcome.Blocked, svc().suggest("u1", "en", "a")) // would be bad_length
        assertEquals(SuggestOutcome.Blocked, svc().suggest("u1", " ", "shit")) // would be bad_lang / offensive
        assertEquals(SuggestOutcome.Blocked, svc().suggest("u1", "en", "table")) // would be a duplicate
        assertEquals(SuggestOutcome.Blocked, svc(cap = 0).suggest("u1", "en", "hello")) // would be over the cap
        assertEquals(1, suggestionCount(), "only the earlier suggestion")
        assertTrue(svc().queued(10).isEmpty(), "nothing queued for the review worker")
    }

    @Test
    fun blockingKeepsEarlierSuggestionsPending() = runBlocking {
        val earlier = insertPending("en", "crane")
        setBlocked("u1", blocked = true)
        assertEquals(SuggestionStatus.PENDING.name, statusOf(earlier))
        insertPack("en", "1", listOf("x"))
        assertTrue(svc().decide(earlier, accept = true, editor = "42") is DecideOutcome.Applied, "editors can still decide it")
    }

    @Test
    fun aBlockedAccountGetsForbiddenStillSyncsAndSuggestsAgainOnceUnblocked() = testApplication {
        application { module() }
        val client = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val anon = client.post(ApiRoutes.AUTH_ANONYMOUS).body<AnonymousAuthResponse>()
        val bearer = "Bearer ${anon.tokens.accessToken}"
        setBlocked(anon.userId, blocked = true)

        val refused = client.post(ApiRoutes.SUGGESTIONS) {
            header(HttpHeaders.Authorization, bearer)
            contentType(ContentType.Application.Json)
            setBody(SuggestWordRequest(lang = "en", word = "world"))
        }
        assertEquals(HttpStatusCode.Forbidden, refused.status)
        assertEquals(ApiErrorResponse(SuggestionErrors.BLOCKED, "Suggestions are blocked for this account"), refused.body<ApiErrorResponse>())
        assertEquals(0, suggestionCount(), "nothing stored")
        assertTrue(svc().queued(10).isEmpty(), "nothing for the review worker to look up or post")
        assertEquals(0, capCount(anon.userId), "the daily cap is untouched")

        // Playing and syncing are not affected by the block.
        val upload = client.post(ApiRoutes.SYNC_STATS) {
            header(HttpHeaders.Authorization, bearer)
            contentType(ContentType.Application.Json)
            setBody(UserStatsDto(kotlinx.datetime.Instant.fromEpochMilliseconds(System.currentTimeMillis() - 1000), listOf(ResultRecordDto("en", 20_000, true, 3))))
        }
        assertEquals(HttpStatusCode.OK, upload.status)

        setBlocked(anon.userId, blocked = false)
        val accepted = client.post(ApiRoutes.SUGGESTIONS) {
            header(HttpHeaders.Authorization, bearer)
            contentType(ContentType.Application.Json)
            setBody(SuggestWordRequest(lang = "en", word = "world"))
        }
        assertEquals(HttpStatusCode.Accepted, accepted.status)
        assertEquals(SuggestionStatus.PENDING.name, accepted.body<SuggestWordResponse>().status)
        assertEquals(1, capCount(anon.userId))
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
