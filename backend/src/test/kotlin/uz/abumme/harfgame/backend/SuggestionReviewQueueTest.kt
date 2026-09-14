package uz.abumme.harfgame.backend

import kotlinx.coroutines.runBlocking
import uz.abumme.harfgame.backend.db.WordSuggestionsTable
import uz.abumme.harfgame.backend.service.DailySummary
import uz.abumme.harfgame.backend.service.QueuedSuggestion
import uz.abumme.harfgame.backend.service.SuggestionServerService
import uz.abumme.harfgame.backend.service.WordPackServerService
import uz.abumme.harfgame.data.suggestion.SuggestionStatus
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SuggestionReviewQueueTest {

    @BeforeTest
    fun setup() = resetSuggestionData()

    private fun svc() = SuggestionServerService(WordPackServerService())

    @Test
    fun queuedReturnsOnlyQueuedRowsOldestFirst() = runBlocking {
        val t0 = Instant.parse("2026-09-14T08:00:00Z")
        val newer = insertSuggestion("en", "newer", reviewState = "QUEUED", createdAt = t0.plusSeconds(60))
        val older = insertSuggestion("ru", "older", reviewState = "QUEUED", createdAt = t0)
        insertSuggestion("en", "legacy", reviewState = null, createdAt = t0.minusSeconds(60)) // predates the worker
        insertSuggestion("en", "posted", reviewState = "POSTED", createdAt = t0.minusSeconds(30))

        assertEquals(listOf(older, newer), svc().queued(limit = 10).map { it.id })
        assertEquals(listOf(older), svc().queued(limit = 1).map { it.id })
    }

    @Test
    fun queuedRowCarriesWhatTheWorkerNeeds() = runBlocking {
        val id = insertSuggestion("uz-latn", "kitob", reviewState = "QUEUED", author = "u1")
        assertEquals(
            listOf(QueuedSuggestion(id, "uz-latn", "kitob", "u1", SuggestionStatus.PENDING, lookupAttempts = 0, autoForm = null)),
            svc().queued(limit = 10),
        )
    }

    @Test
    fun markPostedTakesRowOutOfTheQueue() = runBlocking {
        val id = insertSuggestion("en", "hello", reviewState = "QUEUED")
        svc().markPosted(id)
        assertEquals(emptyList(), svc().queued(limit = 10))
        assertEquals("POSTED", suggestionColumn(id, WordSuggestionsTable.reviewState))
    }

    @Test
    fun lookupAttemptsCountUpFromZero() = runBlocking {
        val id = insertSuggestion("en", "hello", reviewState = "QUEUED")
        assertEquals(1, svc().incrementLookupAttempts(id))
        assertEquals(2, svc().incrementLookupAttempts(id))
        assertEquals(2, svc().queued(limit = 10).single().lookupAttempts)
    }

    @Test
    fun dailySummaryGroupsDecisionsInsideTheHalfOpenDay() = runBlocking {
        val from = Instant.parse("2026-09-13T19:00:00Z") // 00:00 Asia/Tashkent on Sep 14
        val to = Instant.parse("2026-09-14T19:00:00Z")
        val accepted = SuggestionStatus.ACCEPTED
        val rejected = SuggestionStatus.REJECTED
        insertSuggestion("ru", "начал", accepted, decidedVia = "AUTO", decidedAt = from) // at start: in
        insertSuggestion("ru", "конец", rejected, decidedVia = "EDITOR", decidedAt = to) // at end: out
        insertSuggestion("ru", "вчера", accepted, decidedVia = "EDITOR", decidedAt = from.minusSeconds(1)) // before: out
        insertSuggestion("ru", "старо", accepted, decidedVia = null, decidedAt = from.plusSeconds(3600)) // legacy: editor
        insertSuggestion("ru", "ручка", accepted, decidedVia = "EDITOR", decidedAt = from.plusSeconds(7200))
        insertSuggestion("ru", "бырка", rejected, decidedVia = "EDITOR", decidedAt = from.plusSeconds(10800))
        insertSuggestion("ru", "ждать", reviewState = "QUEUED")
        insertSuggestion("ru", "ждите", reviewState = "POSTED")
        insertSuggestion("en", "other", reviewState = "POSTED") // other language
        insertSuggestion("en", "elsew", accepted, decidedVia = "AUTO", decidedAt = from.plusSeconds(60)) // other language

        assertEquals(
            DailySummary(
                autoAccepted = listOf("начал"),
                editorAccepted = listOf("старо", "ручка"),
                rejected = listOf("бырка"),
                pending = 2,
            ),
            svc().dailySummary("ru", from, to),
        )
    }
}
