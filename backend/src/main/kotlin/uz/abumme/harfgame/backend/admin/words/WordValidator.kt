package uz.abumme.harfgame.backend.admin.words

import uz.abumme.harfgame.data.admin.words.WordReasons
import uz.abumme.harfgame.data.admin.words.WordRules
import uz.abumme.harfgame.lang.LanguageRegistry

/**
 * Validates a word for one language with the app's own rules (the shared [WordRules]: normalize, non-empty, tokenizes,
 * board length) plus the language blocklist. Catalog state (duplicate, removed) is checked by the caller inside its
 * write transaction.
 */
class WordValidator(
    private val lang: String,
    private val registry: LanguageRegistry = LanguageRegistry(),
    private val blocklists: Blocklists = Blocklists.default,
) {
    sealed interface Result {
        /** [normalized] is the empty string only for [WordReasons.UNSUPPORTED_LANGUAGE]. */
        val normalized: String
    }

    data class Valid(override val normalized: String, val graphemes: List<String>) : Result

    /** [reason] is one of [WordReasons]; [graphemes] is set when the word tokenized. */
    data class Invalid(override val normalized: String, val reason: String, val graphemes: List<String>? = null) : Result

    fun validate(raw: String): Result {
        val config = registry.config(lang) ?: return Invalid("", WordReasons.UNSUPPORTED_LANGUAGE)
        val check = WordRules.check(config, raw)
        check.reason?.let { return Invalid(check.normalized, it, check.graphemes) }
        val graphemes = checkNotNull(check.graphemes)
        if (check.normalized in blocklists.of(lang)) return Invalid(check.normalized, WordReasons.BLOCKLISTED, graphemes)
        return Valid(check.normalized, graphemes)
    }
}
