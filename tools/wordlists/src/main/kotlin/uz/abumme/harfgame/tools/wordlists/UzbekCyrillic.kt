package uz.abumme.harfgame.tools.wordlists

import uz.abumme.harfgame.engine.Tokenizer
import uz.abumme.harfgame.lang.LaunchLanguages

/**
 * Uzbek Cyrillic guesses, which Wiktionary barely covers: its own Cyrillic spellings plus every Uzbek Latin word
 * transliterated, each re-checked on the Cyrillic board because tile counts change across scripts (tanga, 4 tiles,
 * is танга, 5). Loanwords whose Cyrillic spelling keeps letters Latin doesn't write (ц, ь) come out misspelled;
 * as guesses that is harmless (design.md, "Uzbek Cyrillic by transliteration").
 */
object UzbekCyrillic {
    private val latinTokenizer = Tokenizer(LaunchLanguages.uzLatn)
    private val anyLength = (1..64).toSet()

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

    /** Cyrillic spelling of a canonical Uzbek Latin word. */
    fun transliterate(latin: String): String {
        val tiles = latinTokenizer.tokenize(latin) ?: error("not an Uzbek Latin word: $latin")
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
                    else -> append(letters[tile] ?: error("no Cyrillic letter for '$tile' in $latin"))
                }
                i++
            }
        }
    }

    /** Cyrillic guess words from [rawCandidates] — spellings from the Uzbek dump in either script. */
    fun words(rawCandidates: Iterable<String>, lengths: Set<Int> = setOf(5)): Set<String> {
        val cyrillic = PlayableWordFilter(LaunchLanguages.uzCyrl, lengths)
        val latin = PlayableWordFilter(LaunchLanguages.uzLatn, anyLength)
        val words = HashSet<String>()
        for (raw in rawCandidates) {
            cyrillic.playable(raw)?.let(words::add)
            latin.playable(raw)?.let { cyrillic.playable(transliterate(it)) }?.let(words::add)
        }
        return words
    }
}
