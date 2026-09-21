package uz.abumme.harfgame.tools.wordlists

import uz.abumme.harfgame.lang.LaunchLanguages
import uz.abumme.harfgame.lang.UzbekTransliteration

/**
 * Uzbek Cyrillic guesses, which Wiktionary barely covers: its own Cyrillic spellings plus every Uzbek Latin word
 * transliterated with the shared [UzbekTransliteration], each re-checked on the Cyrillic board because tile counts
 * change across scripts (tanga, 4 tiles, is танга, 5). Loanwords whose Cyrillic spelling keeps letters Latin doesn't
 * write (ц, ь) come out misspelled; as guesses that is harmless (design.md, "Uzbek Cyrillic by transliteration").
 */
object UzbekCyrillic {
    private val anyLength = (1..64).toSet()

    /** Cyrillic spelling of a canonical Uzbek Latin word. */
    fun transliterate(latin: String): String = UzbekTransliteration.transliterate(latin)

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
