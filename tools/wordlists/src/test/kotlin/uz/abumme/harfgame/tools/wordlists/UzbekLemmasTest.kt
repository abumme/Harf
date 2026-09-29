package uz.abumme.harfgame.tools.wordlists

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class UzbekLemmasTest {

    private val lemmas = UzbekLemmas.read(Path.of(javaClass.getResource("/uzbek-lemmas")!!.toURI()))

    @Test
    fun readsTheLemmasOfEveryPartOfSpeechFile() {
        assertEquals(
            setOf(
                UzbekLemma("boʻl", UzbekPos.VERB),
                UzbekLemma("boʻldir", UzbekPos.VERB),
                UzbekLemma("chaqmoq", UzbekPos.VERB),
                UzbekLemma("chaqmoqla", UzbekPos.VERB),
                UzbekLemma("kel", UzbekPos.VERB),
                UzbekLemma("abgor", UzbekPos.NOUN),
                UzbekLemma("uy", UzbekPos.NOUN),
                UzbekLemma("uychi", UzbekPos.NOUN),
                UzbekLemma("voy", UzbekPos.OTHER),
            ),
            lemmas.toSet(),
        )
    }

    // Folded into the expectation above, spelled out: apostrophe variants become the tutuq, morpheme slashes go
    // (chaq/moq), hyphenated compounds can't be typed (bo‘lmoq-qo‘y), and ta'lim's glottal stop isn't a tile.
}
