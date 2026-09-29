package uz.abumme.harfgame.tools.wordlists

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UzbekInflectionTest {

    private fun assertForms(lemma: String, pos: UzbekPos, vararg expected: String) {
        val forms = UzbekInflection.forms(lemma, pos, maxTiles = 12)
        val missing = expected.filterNot { it in forms }
        assertTrue(missing.isEmpty(), "$lemma lacks $missing")
    }

    @Test
    fun nounTakesPluralPossessiveAndCaseSuffixes() {
        assertForms(
            "kitob", UzbekPos.NOUN,
            "kitob", "kitoblar", "kitobim", "kitobing", "kitobi", "kitobimiz", "kitobingiz",
            "kitobning", "kitobni", "kitobga", "kitobda", "kitobdan", "kitobimga", "kitoblari", "kitobidan",
        )
    }

    @Test
    fun vowelFinalNounTakesTheShortPossessives() {
        assertForms("ona", UzbekPos.NOUN, "onam", "onang", "onasi", "onamiz", "onangiz", "onasiga")
    }

    @Test
    fun datives() {
        assertForms("tilak", UzbekPos.NOUN, "tilakka")
        assertForms("qishloq", UzbekPos.NOUN, "qishloqqa")
        assertForms("uy", UzbekPos.NOUN, "uyga")
        assertTrue("tilakga" !in UzbekInflection.forms("tilak", UzbekPos.NOUN, maxTiles = 12))
    }

    @Test
    fun possessiveStemAlternations() {
        assertForms("yurak", UzbekPos.NOUN, "yuragi", "yuragim")
        assertForms("tuproq", UzbekPos.NOUN, "tuprogʻi")
        assertForms("ogʻiz", UzbekPos.NOUN, "ogʻzi")
        assertForms("burun", UzbekPos.NOUN, "burni")
    }

    @Test
    fun consonantFinalVerb() {
        assertForms(
            "kel", UzbekPos.VERB,
            "kel", "kelmoq", "keldi", "keldim", "kelib", "kela", "keladi", "kelaman", "kelgan", "kelsa",
            "kelyapti", "kelmadi", "kelmaydi", "kelish", "kelishi", "keling", "kelsin", "kelganda",
        )
    }

    @Test
    fun participleAfterKAndQDoublesTheConsonant() {
        assertForms("chiq", UzbekPos.VERB, "chiqqan", "chiqqach")
        assertForms("ek", UzbekPos.VERB, "ekkan")
    }

    @Test
    fun vowelFinalVerb() {
        assertForms("ishla", UzbekPos.VERB, "ishlaydi", "ishlab", "ishlash", "ishladi", "ishlagan", "ishlay", "ishlang")
        assertForms("oʻqi", UzbekPos.VERB, "oʻqiydi", "oʻqib", "oʻqidi")
    }

    @Test
    fun uninflectedWordsAreOnlyThemselves() {
        assertEquals(setOf("voy"), UzbekInflection.forms("voy", UzbekPos.OTHER, maxTiles = 12))
    }

    @Test
    fun formsLongerThanTheBoardAreLeftOut() {
        val forms = UzbekInflection.forms("uy", UzbekPos.NOUN, maxTiles = 5)
        assertTrue("uydan" in forms && "uylar" in forms, "$forms")
        assertTrue(forms.none { it.length > 10 }, "$forms")
        assertTrue("uylarimiz" !in forms)
    }
}
