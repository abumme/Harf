package uz.abumme.harfgame.lang

import uz.abumme.harfgame.engine.Tokenizer

/**
 * Rule-based Uzbek Latin → Cyrillic spelling, shared by the word-list builder (Cyrillic guesses), the server and the
 * staff panel (the suggested Cyrillic word of a lexeme pair). Loanwords whose Cyrillic spelling keeps letters Latin
 * doesn't write (ц, ь) come out misspelled, so a suggestion is always confirmed by a person.
 */
object UzbekTransliteration {
    private val latinTokenizer = Tokenizer(LaunchLanguages.uzLatn)

    private val vowels = setOf("a", "e", "i", "o", "u", "oʻ")

    /** One Cyrillic spelling per Latin grapheme; `y` and `e` depend on their neighbours. */
    private val letters = mapOf(
        "a" to "а", "b" to "б", "d" to "д", "f" to "ф", "g" to "г", "h" to "ҳ", "i" to "и", "j" to "ж",
        "k" to "к", "l" to "л", "m" to "м", "n" to "н", "o" to "о", "p" to "п", "q" to "қ", "r" to "р",
        "s" to "с", "t" to "т", "u" to "у", "v" to "в", "x" to "х", "z" to "з",
        "sh" to "ш", "ch" to "ч", "ng" to "нг", "oʻ" to "ў", "gʻ" to "ғ",
    )

    /** `y` + vowel written as one iotated letter. `oʻ` is its own grapheme, so yoʻl stays йўл. */
    private val iotated = mapOf("o" to "ё", "u" to "ю", "a" to "я")

    /** Cyrillic spelling of a canonical Uzbek Latin word; throws when [latin] is not an Uzbek Latin word. */
    fun transliterate(latin: String): String =
        transliterateOrNull(latin) ?: throw IllegalStateException("not an Uzbek Latin word: $latin")

    /** Cyrillic spelling of [latin] (normalized first), or null when it is not an Uzbek Latin word. */
    fun transliterateOrNull(latin: String): String? {
        val tiles = latinTokenizer.tokenize(latin.trim()) ?: return null
        if (tiles.isEmpty()) return null
        val wordStartOrAfterVowel = { i: Int -> i == 0 || tiles[i - 1] in vowels }
        return buildString {
            var i = 0
            while (i < tiles.size) {
                val tile = tiles[i]
                val next = tiles.getOrNull(i + 1)
                when {
                    tile == "y" && next in iotated -> { append(iotated.getValue(next!!)); i++ }
                    tile == "y" && next == "e" && wordStartOrAfterVowel(i) -> { append("е"); i++ }
                    tile == "y" -> append("й")
                    tile == "e" -> append(if (wordStartOrAfterVowel(i)) "э" else "е")
                    else -> append(letters[tile] ?: return null)
                }
                i++
            }
        }
    }
}
