package uz.abumme.harfgame.tools.wordlists

import uz.abumme.harfgame.lang.LaunchLanguages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayableWordFilterTest {

    private val en = PlayableWordFilter(LaunchLanguages.en)
    private val ru = PlayableWordFilter(LaunchLanguages.ru)
    private val kk = PlayableWordFilter(LaunchLanguages.kk)
    private val uzLatn = PlayableWordFilter(LaunchLanguages.uzLatn)
    private val uzCyrl = PlayableWordFilter(LaunchLanguages.uzCyrl)

    @Test
    fun stressMarksAreStrippedWhileLettersWithDiacriticsSurvive() {
        assertEquals("книги", ru.playable("кни́ги")) // combining acute (U+0301) from the inflection table
        assertEquals("война", ru.playable("война́")) // й keeps its breve
        assertEquals("ўрдак", uzCyrl.playable("ўрда́к")) // ў keeps its breve
        assertEquals("война", ru.playable("война")) // decomposed й is recomposed, not stripped
    }

    @Test
    fun russianYoPlaysAsYe() {
        assertEquals("ежики", ru.playable("ёжики"))
    }

    @Test
    fun uzbekApostropheVariantsBecomeTheTutuq() {
        assertEquals("oʻrdak", uzLatn.playable("o'rdak"))
        assertEquals("oʻrdak", uzLatn.playable("o`rdak"))
        assertEquals("gʻalla", uzLatn.playable("g‘alla"))
    }

    @Test
    fun tokenizerExceptionsDecideTheTileCount() {
        // ustunga is u-s-t-u-n-g-a (7 tiles) by exception; greedy longest-match would give 6 (…ng…).
        assertEquals("ustunga", PlayableWordFilter(LaunchLanguages.uzLatn, lengths = setOf(7)).playable("ustunga"))
        assertNull(PlayableWordFilter(LaunchLanguages.uzLatn, lengths = setOf(6)).playable("ustunga"))
    }

    @Test
    fun wordsOutsideTheLanguagesAlphabetAreDropped() {
        assertEquals("кітап", kk.playable("кітап"))
        assertNull(kk.playable("фирма")) // ф is a Russian-loan letter, not in the Kazakh inventory
        assertNull(uzLatn.playable("maʼno")) // tutuq words are excluded content-side
        assertNull(en.playable("naïve"))
        assertNull(en.playable("x-ray"))
    }

    @Test
    fun onlyTheConfiguredBoardLengthsArePlayable() {
        assertEquals("apple", en.playable("Apple"))
        assertNull(en.playable("apples"))
        assertNull(en.playable("app"))
    }
}
