package uz.abumme.harfgame.tools.wordlists

import org.apache.lucene.analysis.hunspell.Dictionary
import org.apache.lucene.analysis.hunspell.Hunspell
import org.apache.lucene.store.ByteBuffersDirectory
import java.nio.file.Path
import kotlin.io.path.inputStream

/** A Hunspell spelling dictionary (`.aff` rules + `.dic` stems), checked with Lucene's Hunspell implementation. */
class HunspellDictionary(aff: Path, dic: Path) {
    private val hunspell: Hunspell = aff.inputStream().use { affix ->
        dic.inputStream().use { words -> Hunspell(Dictionary(ByteBuffersDirectory(), "hunspell", affix, words)) }
    }

    /** True when the dictionary spells [word] this way, including forms built by its affix rules. */
    fun accepts(word: String): Boolean = hunspell.spell(word)
}
