package uz.abumme.harfgame.admin

import uz.abumme.harfgame.admin.suggestions.DecisionOutcome
import uz.abumme.harfgame.admin.suggestions.SuggestionTab
import uz.abumme.harfgame.admin.suggestions.SuggestionsQueryState
import uz.abumme.harfgame.admin.suggestions.decisionOutcome
import uz.abumme.harfgame.data.admin.suggestions.DeciderDto
import uz.abumme.harfgame.data.admin.suggestions.DeciderKind
import uz.abumme.harfgame.data.admin.suggestions.ReviewReasons
import uz.abumme.harfgame.data.admin.suggestions.SuggestionDto
import uz.abumme.harfgame.data.api.ApiResult
import uz.abumme.harfgame.data.suggestion.SuggestionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SuggestionsStateTest {

    private val accepted = SuggestionDto(
        id = "s-1", lang = "kk", word = "қалам", author = "Аноним", createdAt = 1L,
        status = SuggestionStatus.ACCEPTED, decidedBy = DeciderDto(DeciderKind.STAFF, "Азиз"), decidedAt = 2L,
    )

    // --- decision outcome mapping (8.3) ---

    @Test
    fun anAppliedDecisionRefreshesTheList() {
        val outcome = assertIs<DecisionOutcome.Applied>(decisionOutcome(ApiResult.Success(accepted)))
        assertEquals(accepted, outcome.suggestion)
        assertTrue(outcome.refreshList)
    }

    @Test
    fun aConflictMeansAlreadyDecidedAndAlsoRefreshes() {
        val outcome = decisionOutcome(ApiResult.Error("conflict", "status: already_decided"))
        assertEquals(DecisionOutcome.AlreadyDecided, outcome)
        assertTrue(outcome.refreshList)
    }

    @Test
    fun forbiddenValidationAndOtherFailuresLeaveTheListAlone() {
        val forbidden = decisionOutcome(ApiResult.Error("forbidden", "Language kk is not assigned to you"))
        assertEquals(DecisionOutcome.Forbidden, forbidden)
        assertFalse(forbidden.refreshList)

        val invalid = decisionOutcome(ApiResult.Error("validation_failed", "word: bad_length"))
        assertEquals(DecisionOutcome.ValidationFailed("bad_length"), invalid)
        assertFalse(invalid.refreshList)

        val network = assertIs<DecisionOutcome.Failed>(decisionOutcome(ApiResult.Error("network_error", "No response")))
        assertEquals("network_error", network.error.code)
        assertIs<DecisionOutcome.Failed>(decisionOutcome(ApiResult.Error("validation_failed", "no field here")))
    }

    // --- view state ---

    @Test
    fun theViewLivesInTheUrlAndMapsToTheApiQuery() {
        val history = SuggestionsQueryState("kk").withTab(SuggestionTab.HISTORY).withPage(1)
        assertEquals("?lang=kk&tab=history&page=2", history.toRouteQuery())
        assertEquals("?lang=kk&status=DECIDED&page=1&size=50", history.toApiQuery(50))
        assertEquals("?lang=kk&status=PENDING&page=0&size=50", SuggestionsQueryState("kk").toApiQuery(50))
        assertEquals(history, SuggestionsQueryState.fromParams(mapOf("lang" to "kk", "tab" to "history", "page" to "2"), listOf("kk")))

        assertEquals(0, history.withTab(SuggestionTab.PENDING).page)
        assertEquals(SuggestionsQueryState("ru"), SuggestionsQueryState("kk").withPage(3).withLanguage("ru"))
        assertEquals(SuggestionsQueryState("kk"), SuggestionsQueryState.fromParams(mapOf("lang" to "en"), listOf("kk")))
        assertNull(SuggestionsQueryState.fromParams(emptyMap(), emptyList()))
    }

    @Test
    fun reasonsAndDecidersHaveRussianLabels() {
        assertEquals("ожидает проверки", Strings.Suggestions.reason(null))
        assertEquals("удалено редакторами", Strings.Suggestions.reason(ReviewReasons.REMOVED_BY_STAFF))
        assertEquals("SOMETHING_NEW", Strings.Suggestions.reason("SOMETHING_NEW"))
        assertEquals("Азиз", Strings.Suggestions.decider(DeciderDto(DeciderKind.STAFF, "Азиз")))
        assertEquals("Telegram 42", Strings.Suggestions.decider(DeciderDto(DeciderKind.TELEGRAM, "42")))
    }
}
