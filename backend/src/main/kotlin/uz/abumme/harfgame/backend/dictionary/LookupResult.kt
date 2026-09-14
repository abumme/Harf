package uz.abumme.harfgame.backend.dictionary

/** Outcome of verifying a suggested word against a dictionary. */
sealed interface LookupResult {
    /** A real word of the suggestion's language: accept it automatically. */
    data class Auto(val form: WordForm) : LookupResult

    /** Leave the decision to editors, telling them [reason]. */
    data class Review(val reason: ReviewReason) : LookupResult

    /** The dictionary could not be read; try again later. */
    data object Unavailable : LookupResult
}

enum class WordForm { DICTIONARY, INFLECTED }

/** Why a word goes to editors. [UNVERIFIED] is assigned after the dictionary stayed unavailable on every retry. */
enum class ReviewReason { NOT_FOUND, PROPER_NOUN, ABBREVIATION, MISSPELLING, VULGAR, DISABLED, UNVERIFIED }

/** Verifies a suggested word in a language. */
interface WordLookup {
    suspend fun lookup(lang: String, word: String): LookupResult
}
