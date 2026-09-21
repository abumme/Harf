package uz.abumme.harfgame.data.admin.words

import uz.abumme.harfgame.engine.Tokenizer
import uz.abumme.harfgame.lang.LanguageConfig

/**
 * The language rules a catalog word must pass, shared so the panel previews exactly what the server enforces. The
 * server adds what only it knows: the blocklist and the word's catalog state.
 */
object WordRules {
    const val MAX_BULK_LINES = 1000

    /** [normalized] is always set; [graphemes] is null when the word does not tokenize; [reason] is null when valid. */
    data class Check(
        val normalized: String,
        val graphemes: List<String>?,
        val reason: String?,
    ) {
        val isValid: Boolean get() = reason == null
    }

    /** Normalizes [raw] with [config]'s rules, then checks it is non-empty, tokenizes and has a supported board length. */
    fun check(config: LanguageConfig, raw: String): Check {
        val tokenizer = Tokenizer(config)
        val normalized = tokenizer.normalize(raw.trim()).trim()
        if (normalized.isEmpty()) return Check(normalized, null, WordReasons.EMPTY)
        val graphemes = tokenizer.tokenize(normalized) ?: return Check(normalized, null, WordReasons.NOT_TOKENIZABLE)
        if (graphemes.size !in config.minLength..config.maxLength) return Check(normalized, graphemes, WordReasons.BAD_LENGTH)
        return Check(normalized, graphemes, null)
    }
}
