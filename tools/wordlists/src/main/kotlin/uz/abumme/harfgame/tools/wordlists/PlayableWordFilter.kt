package uz.abumme.harfgame.tools.wordlists

import uz.abumme.harfgame.engine.Tokenizer
import uz.abumme.harfgame.lang.LanguageConfig
import java.text.Normalizer as UnicodeNormalizer

/**
 * Turns a raw dictionary spelling into the canonical playable word for one language — its graphemes joined, as the
 * game stores words — or null when it can't be played: a letter outside the alphabet, or a tile count the puzzles
 * don't use. Normalization and tile splitting are the app's own [Tokenizer], so results match play exactly.
 */
class PlayableWordFilter(config: LanguageConfig, private val lengths: Set<Int> = setOf(5)) {
    private val tokenizer = Tokenizer(config)

    fun playable(raw: String): String? {
        val tiles = tokenizer.tokenize(stripStressMarks(raw)) ?: return null
        return if (tiles.size in lengths) tiles.joinToString("") else null
    }

    /** Removes combining acute and grave stress marks only, so й, ё and ў keep their diacritics. */
    private fun stripStressMarks(raw: String): String {
        val decomposed = UnicodeNormalizer.normalize(raw, UnicodeNormalizer.Form.NFD)
        val unstressed = decomposed.filterNot { it == '́' || it == '̀' }
        return UnicodeNormalizer.normalize(unstressed, UnicodeNormalizer.Form.NFC)
    }
}
