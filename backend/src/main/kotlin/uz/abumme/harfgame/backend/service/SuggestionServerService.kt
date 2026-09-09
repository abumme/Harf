package uz.abumme.harfgame.backend.service

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.UsersTable
import uz.abumme.harfgame.backend.db.WordSuggestionsTable
import uz.abumme.harfgame.data.suggestion.SuggestionStatus
import java.util.UUID

/** Result of attempting to store a suggestion. The route maps these to HTTP status codes. */
sealed interface SuggestOutcome {
    /** A new pending suggestion was stored. [author] is a human label (display name, else short id). */
    data class Stored(val id: String, val lang: String, val word: String, val author: String) : SuggestOutcome
    /** An identical pending suggestion already existed; reported as success, nothing new stored. */
    data object DuplicatePending : SuggestOutcome
    /** Failed validation (ill-formed, offensive, gibberish, or already in the pack). */
    data class Rejected(val reason: String) : SuggestOutcome
    /** The author exceeded the per-day cap. */
    data object OverCap : SuggestOutcome
}

/** Result of an editor decision on a suggestion. */
sealed interface DecideOutcome {
    data class Applied(val accepted: Boolean, val lang: String, val word: String) : DecideOutcome
    data object NotFound : DecideOutcome
    data object AlreadyDecided : DecideOutcome
}

/**
 * Validates and stores word suggestions. Validation is string-level: the backend has no grapheme
 * tokenizer (that lives in the client's :sharedUI), so length is bounded by characters, not
 * graphemes. These heuristics only cut obvious noise — editors decide whether a word is real.
 * ponytail: char-length + gibberish heuristics; move to grapheme-exact only if a shared tokenizer
 * is extracted to :sharedData.
 */
class SuggestionServerService(
    private val wordPackService: WordPackServerService,
    private val dailyCap: Int = DEFAULT_DAILY_CAP,
) {
    suspend fun suggest(userId: String, langRaw: String, wordRaw: String): SuggestOutcome {
        val lang = langRaw.trim()
        val word = wordRaw.trim().lowercase()

        if (lang.isEmpty()) return SuggestOutcome.Rejected("bad_lang")
        if (word.length !in MIN_CHARS..MAX_CHARS) return SuggestOutcome.Rejected("bad_length")
        if (word in offensiveWords(lang)) return SuggestOutcome.Rejected("offensive")
        gibberishReason(word)?.let { return SuggestOutcome.Rejected(it) }
        if (isAlreadyInPack(lang, word)) return SuggestOutcome.Rejected("already_present")

        return DatabaseFactory.dbQuery {
            val hasPending = WordSuggestionsTable.selectAll().where {
                (WordSuggestionsTable.lang eq lang) and
                    (WordSuggestionsTable.word eq word) and
                    (WordSuggestionsTable.status eq SuggestionStatus.PENDING.name)
            }.any()
            if (hasPending) return@dbQuery SuggestOutcome.DuplicatePending

            val since = java.time.Instant.ofEpochMilli(System.currentTimeMillis() - DAY_MILLIS)
            val recent = WordSuggestionsTable.selectAll().where {
                (WordSuggestionsTable.suggestedBy eq userId) and
                    (WordSuggestionsTable.createdAt greater since)
            }.count()
            if (recent >= dailyCap) return@dbQuery SuggestOutcome.OverCap

            val id = UUID.randomUUID().toString()
            WordSuggestionsTable.insert {
                it[WordSuggestionsTable.id] = id
                it[WordSuggestionsTable.lang] = lang
                it[WordSuggestionsTable.word] = word
                it[suggestedBy] = userId
                it[status] = SuggestionStatus.PENDING.name
                it[createdAt] = java.time.Instant.ofEpochMilli(System.currentTimeMillis())
            }
            val name = UsersTable.selectAll().where { UsersTable.id eq userId }
                .singleOrNull()?.get(UsersTable.name)?.takeIf { it.isNotBlank() }
            SuggestOutcome.Stored(id, lang, word, author = name ?: "Аноним")
        }
    }

    /**
     * Apply an editor's decision. Acts only while the suggestion is still PENDING (idempotent against
     * a double-tap or a second editor). On accept, the word is added to the pack. Authorization of the
     * editor is the caller's responsibility (the Telegram layer checks the editor allowlist).
     */
    suspend fun decide(suggestionId: String, accept: Boolean, editor: String): DecideOutcome {
        val row = DatabaseFactory.dbQuery {
            WordSuggestionsTable.selectAll().where { WordSuggestionsTable.id eq suggestionId }.singleOrNull()
        } ?: return DecideOutcome.NotFound

        if (row[WordSuggestionsTable.status] != SuggestionStatus.PENDING.name) return DecideOutcome.AlreadyDecided

        val lang = row[WordSuggestionsTable.lang]
        val word = row[WordSuggestionsTable.word]
        val newStatus = if (accept) SuggestionStatus.ACCEPTED else SuggestionStatus.REJECTED

        DatabaseFactory.dbQuery {
            WordSuggestionsTable.update({ WordSuggestionsTable.id eq suggestionId }) {
                it[status] = newStatus.name
                it[decidedBy] = editor
                it[decidedAt] = java.time.Instant.ofEpochMilli(System.currentTimeMillis())
            }
        }
        if (accept) wordPackService.addGuess(lang, word)
        return DecideOutcome.Applied(accept, lang, word)
    }

    private suspend fun isAlreadyInPack(lang: String, word: String): Boolean {
        val pack = wordPackService.getPack(lang) ?: return false
        return pack.guesses.any { it.trim().lowercase() == word }
    }

    /** Cheap gibberish checks. Returns a reason string when the word looks like junk, else null. */
    private fun gibberishReason(word: String): String? {
        // three or more of the same character in a row: "aaa", "sssalom"
        var run = 1
        for (i in 1 until word.length) {
            if (word[i] == word[i - 1]) {
                run++
                if (run >= 3) return "repeat_run"
            } else run = 1
        }
        if (word.toSet().size < MIN_DISTINCT) return "too_few_distinct"
        if (word.none { it in VOWELS }) return "no_vowel"
        return null
    }

    private fun offensiveWords(lang: String): Set<String> {
        val stream = javaClass.getResourceAsStream("/blocklists/${lang}_block.txt") ?: return emptySet()
        return stream.bufferedReader().useLines { lines ->
            lines.map { it.trim().lowercase() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toSet()
        }
    }

    companion object {
        const val DEFAULT_DAILY_CAP = 10
        private const val MIN_CHARS = 2
        private const val MAX_CHARS = 24
        private const val MIN_DISTINCT = 3
        private const val DAY_MILLIS = 24L * 3600 * 1000
        // Latin + Cyrillic (ru/kk) + Uzbek vowels. Broad on purpose — a false "has vowel" is safe.
        private val VOWELS = "aeiouyаеёиоуыэюяәөүұіи".toSet()
    }
}
