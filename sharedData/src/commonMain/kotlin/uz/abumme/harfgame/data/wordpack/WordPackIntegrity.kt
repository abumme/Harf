package uz.abumme.harfgame.data.wordpack

import uz.abumme.harfgame.engine.Tokenizer
import uz.abumme.harfgame.lang.LanguageConfig

/**
 * The integrity check a fetched [WordPackDto] must pass before the app adopts it, shared so the server can refuse to
 * publish a pack the app would reject. A pack is valid when its answers and schedule are non-empty, every answer and
 * schedule entry tokenizes in the language, and every answer's grapheme count is within the language's board lengths.
 * Guesses that do not tokenize are dropped, not refused; answers and schedule words are always valid guesses.
 */
object WordPackIntegrity {

    sealed interface Result

    /** The tokenized pack: [guesses] already include every answer and schedule word. */
    data class Valid(
        val answers: List<List<String>>,
        val guesses: Set<List<String>>,
        val schedule: List<List<String>>,
    ) : Result

    /** Why the pack would be rejected; never empty. */
    data class Invalid(val problems: List<String>) : Result

    fun check(dto: WordPackDto, config: LanguageConfig): Result {
        val tokenizer = Tokenizer(config)
        val problems = ArrayList<String>()

        if (dto.answers.isEmpty()) problems += "no answers"
        if (dto.schedule.isEmpty()) problems += "no schedule"
        val answers = dto.answers.mapNotNull { raw ->
            val graphemes = tokenizer.tokenize(raw)
            when {
                graphemes == null -> problems += "answer not tokenizable: '$raw'"
                graphemes.size !in config.minLength..config.maxLength ->
                    problems += "answer length ${graphemes.size} out of ${config.minLength}..${config.maxLength}: '$raw'"
            }
            graphemes
        }
        val schedule = dto.schedule.mapNotNull { raw ->
            tokenizer.tokenize(raw).also { if (it == null) problems += "schedule entry not tokenizable: '$raw'" }
        }
        if (problems.isNotEmpty()) return Invalid(problems)

        val guesses = dto.guesses.mapNotNullTo(LinkedHashSet()) { tokenizer.tokenize(it) }
        guesses += answers
        guesses += schedule
        return Valid(answers, guesses, schedule)
    }
}
