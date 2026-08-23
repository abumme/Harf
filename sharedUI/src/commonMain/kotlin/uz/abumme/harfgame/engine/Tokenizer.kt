package uz.abumme.harfgame.engine

import uz.abumme.harfgame.lang.LanguageConfig

/**
 * Greedy longest-match grapheme tokenizer for one language. Normalizes input
 * (apostrophe folding + replacements + lowercasing), applies the exception list,
 * then scans left-to-right taking the longest matching grapheme at each position.
 */
class Tokenizer(private val config: LanguageConfig) {

    // longest graphemes first so digraphs win over their prefixes ("ng" before "n")
    private val ordered: List<String> = config.graphemes.sortedByDescending { it.length }

    /** Normalize raw text to the canonical, lowercased form used for matching. */
    fun normalize(raw: String): String {
        var s = raw
        if (config.normalizeApostrophe) s = Normalizer.normalizeApostrophes(s)
        s = s.lowercase() // lowercase before replacements so uppercase sources (e.g. "Ё") fold too
        s = Normalizer.applyReplacements(s, config.replacements)
        return s
    }

    /**
     * Decompose [raw] into graphemes, or return null if a character can't be matched
     * (e.g. a letter outside the language's inventory).
     */
    fun tokenize(raw: String): List<String>? {
        val s = normalize(raw)
        config.exceptions[s]?.let { return it }

        val out = ArrayList<String>()
        var i = 0
        while (i < s.length) {
            val g = ordered.firstOrNull { s.startsWith(it, i) } ?: return null
            out.add(g)
            i += g.length
        }
        return out
    }

    /** Tokenize into a [Word], or null if invalid for this language. */
    fun word(raw: String): Word? = tokenize(raw)?.let { Word(config.id, it) }
}
