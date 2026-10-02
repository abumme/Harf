package uz.abumme.harfgame.feature.game

/**
 * What the game screen's status strip shows: the one place for transient feedback and the suggest-to-add
 * offer. The strip has a fixed height, so a message appearing or disappearing never moves the board or
 * the keyboard. The screen resolves a message to text; this type only carries the state.
 */
sealed interface StripMessage {
    /** Auto-dismissed after a short delay; a non-transient message stays until replaced or withdrawn. */
    val transient: Boolean

    data object NotEnoughLetters : StripMessage {
        override val transient: Boolean get() = true
    }

    data class HardMode(val violation: uz.abumme.harfgame.engine.HardModeViolation) : StripMessage {
        override val transient: Boolean get() = true
    }

    /** [word] was rejected as unknown; offers to suggest it until the guess changes or it is sent. */
    data class UnknownWord(val word: String, val sending: Boolean = false) : StripMessage {
        override val transient: Boolean get() = false
    }

    data object SuggestionSent : StripMessage {
        override val transient: Boolean get() = true
    }

    data object SuggestionFailed : StripMessage {
        override val transient: Boolean get() = true
    }
}

/** The strip's rules: one message at a time, transient ones expire, the suggest offer sticks to its word. */
object StripReducer {

    /** The message a game event produces; null clears the strip. [guess] is the guess at the time of the event. */
    fun onEvent(event: GameEvent, guess: String): StripMessage? = when (event) {
        GameEvent.Incomplete -> StripMessage.NotEnoughLetters
        GameEvent.InvalidGuess -> StripMessage.UnknownWord(guess)
        is GameEvent.HardModeViolation -> StripMessage.HardMode(event.violation)
        is GameEvent.RoundEnded -> null
    }

    /** A new or edited guess withdraws a pending suggest offer; transient notices are left to their timer. */
    fun onGuessChanged(current: StripMessage?, guess: String): StripMessage? =
        if (current is StripMessage.UnknownWord && current.word != guess) null else current

    /** Marks the offer as in flight so the action cannot be sent twice. */
    fun onSuggestStarted(current: StripMessage?): StripMessage? =
        (current as? StripMessage.UnknownWord)?.copy(sending = true) ?: current

    fun onSuggestResult(success: Boolean): StripMessage =
        if (success) StripMessage.SuggestionSent else StripMessage.SuggestionFailed

    /** The dismiss timer fired: a transient message goes away, a sticky one stays. */
    fun onExpired(current: StripMessage?): StripMessage? = if (current?.transient == true) null else current
}
