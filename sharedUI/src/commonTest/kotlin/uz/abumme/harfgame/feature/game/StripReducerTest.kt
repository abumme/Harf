package uz.abumme.harfgame.feature.game

import uz.abumme.harfgame.engine.HardModeViolation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The status strip's message rules: one message at a time, transient ones expire, the suggest offer sticks. */
class StripReducerTest {

    @Test
    fun incomplete_guess_shows_a_transient_notice() {
        val message = StripReducer.onEvent(GameEvent.Incomplete, guess = "kit")
        assertEquals(StripMessage.NotEnoughLetters, message)
        assertTrue(message!!.transient)
    }

    @Test
    fun hard_mode_violation_shows_the_violation_transiently() {
        val violation = HardModeViolation.MinimumCountNotSatisfied(grapheme = "a", requiredCount = 2, actualCount = 1)
        val message = StripReducer.onEvent(GameEvent.HardModeViolation(violation), guess = "kitob")
        assertEquals(StripMessage.HardMode(violation), message)
        assertTrue(message!!.transient)
    }

    @Test
    fun unknown_word_offers_a_suggestion_that_sticks_until_the_guess_changes() {
        val offer = StripReducer.onEvent(GameEvent.InvalidGuess, guess = "kitob")
        assertEquals(StripMessage.UnknownWord("kitob"), offer)
        assertFalse(offer!!.transient)
        assertEquals(offer, StripReducer.onExpired(offer), "a sticky offer does not expire")
        assertEquals(offer, StripReducer.onGuessChanged(offer, guess = "kitob"), "same guess keeps the offer")
        assertNull(StripReducer.onGuessChanged(offer, guess = "kito"), "editing the guess withdraws the offer")
    }

    @Test
    fun transient_notices_expire_but_survive_typing() {
        val notice = StripReducer.onEvent(GameEvent.Incomplete, guess = "kit")
        assertEquals(notice, StripReducer.onGuessChanged(notice, guess = "kito"))
        assertNull(StripReducer.onExpired(notice))
    }

    @Test
    fun sending_marks_the_offer_busy_and_the_result_replaces_it() {
        val offer = StripMessage.UnknownWord("kitob")
        val busy = StripReducer.onSuggestStarted(offer)
        assertEquals(StripMessage.UnknownWord("kitob", sending = true), busy)
        assertEquals(StripMessage.SuggestionSent, StripReducer.onSuggestResult(success = true))
        assertEquals(StripMessage.SuggestionFailed, StripReducer.onSuggestResult(success = false))
        assertTrue(StripMessage.SuggestionSent.transient && StripMessage.SuggestionFailed.transient)
    }

    @Test
    fun round_end_clears_the_strip() {
        assertNull(StripReducer.onEvent(GameEvent.RoundEnded(won = true), guess = "kitob"))
    }
}
