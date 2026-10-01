package uz.abumme.harfgame.lang

import kotlinx.serialization.Serializable

/**
 * Data-defined configuration for one language/script. Adding or adjusting a language
 * is a data change, not code: the tokenizer, keyboard, and packs all read this.
 */
@Serializable
data class LanguageConfig(
    val id: String,                 // "uz-latn" | "uz-cyrl" | "ru" | "en" | "kk"
    val displayName: String,
    val scriptLabel: String,
    /** Grapheme inventory, lowercase. Multi-char entries are digraphs (e.g. "sh", "oʻ"). */
    val graphemes: List<String>,
    /** Per-word explicit decomposition overriding longest-match (morpheme-boundary cases). Keys are normalized+lowercased. */
    val exceptions: Map<String, List<String>> = emptyMap(),
    /** Literal replacements applied during normalization (e.g. "ё" -> "е"). */
    val replacements: Map<String, String> = emptyMap(),
    /** Whether to fold apostrophe variants to the canonical tutuq (Uzbek Latin). */
    val normalizeApostrophe: Boolean = false,
    /** On-screen keyboard: the letter rows of grapheme keys (digraphs are first-class keys). The UI appends one action row. */
    val keyboard: List<List<String>>,
    /**
     * Graphemes hosted in the action row, between the enter and delete keys (Uzbek Latin's `oʻ` `gʻ`), so every
     * language has the same number of rows. Never repeats a letter-row key; empty for most languages.
     */
    val actionRowKeys: List<String> = emptyList(),
    val minLength: Int = 4,
    val maxLength: Int = 7,
) {
    /** Digraphs = graphemes longer than one character. */
    val digraphs: List<String> get() = graphemes.filter { it.length > 1 }
}

/** A single word that exists in one or more scripts, keyed by [LanguageConfig.id]. */
@Serializable
data class Lexeme(
    val id: String,
    /** language/script id -> grapheme decomposition in that script. */
    val perScript: Map<String, List<String>>,
) {
    fun graphemes(scriptId: String): List<String>? = perScript[scriptId]
}
