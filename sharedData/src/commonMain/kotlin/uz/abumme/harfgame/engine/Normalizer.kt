package uz.abumme.harfgame.engine

/** Pure text-normalization helpers used before tokenization. */
object Normalizer {

    /** Canonical Uzbek modifier-letter apostrophe (U+02BB ʻ). */
    const val TUTUQ: Char = 'ʻ'

    /** Apostrophe-like variants seen in the wild for `oʻ`/`gʻ`. */
    private val apostropheVariants: Set<Char> = setOf(
        '\'', // ' apostrophe
        '’', // ’ right single quote
        '‘', // ‘ left single quote
        '`', // ` grave / backtick
        '´', // ´ acute
        'ʼ', // ʼ modifier apostrophe
        'ʹ', // ʹ modifier prime
        '′', // ′ prime
    )

    /** Replace every apostrophe variant with the canonical tutuq. */
    fun normalizeApostrophes(text: String): String =
        buildString(text.length) {
            for (c in text) append(if (c in apostropheVariants) TUTUQ else c)
        }

    /** Apply a set of literal string replacements (e.g. Russian `ё` → `е`). */
    fun applyReplacements(text: String, replacements: Map<String, String>): String {
        if (replacements.isEmpty()) return text
        var s = text
        for ((from, to) in replacements) s = s.replace(from, to)
        return s
    }
}
