package uz.abumme.harfgame.backend.admin.words

import uz.abumme.harfgame.data.admin.words.WordReasons
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WordValidatorTest {

    private fun validate(lang: String, raw: String, blocked: List<String> = emptyList()) =
        WordValidator(lang, blocklists = Blocklists(lines = { if (it == lang) blocked else emptyList() })).validate(raw)

    private fun reasonOf(lang: String, raw: String, blocked: List<String> = emptyList()) =
        assertIs<WordValidator.Invalid>(validate(lang, raw, blocked)).reason

    @Test
    fun englishWordIsNormalizedAndCountedByLetters() {
        val valid = assertIs<WordValidator.Valid>(validate("en", "  Crane "))
        assertEquals("crane", valid.normalized)
        assertEquals(5, valid.graphemes.size)
    }

    @Test
    fun everyRefusalHasItsReason() {
        assertEquals(WordReasons.EMPTY, reasonOf("en", "   "))
        assertEquals(WordReasons.NOT_TOKENIZABLE, reasonOf("en", "crâne"))
        assertEquals(WordReasons.NOT_TOKENIZABLE, reasonOf("en", "ice cream"))
        assertEquals(WordReasons.BAD_LENGTH, reasonOf("en", "cat"))
        assertEquals(WordReasons.BAD_LENGTH, reasonOf("en", "strawberry"))
        assertEquals(WordReasons.BLOCKLISTED, reasonOf("en", "Quilt", blocked = listOf("quilt")))
        assertEquals(WordReasons.UNSUPPORTED_LANGUAGE, reasonOf("xx", "crane"))
    }

    @Test
    fun russianYoFoldsToYe() {
        val valid = assertIs<WordValidator.Valid>(validate("ru", "Ёлка"))
        assertEquals("елка", valid.normalized)
        assertEquals(WordReasons.BLOCKLISTED, reasonOf("ru", "ЕЛКА", blocked = listOf("ёлка")))
        assertEquals(WordReasons.BAD_LENGTH, reasonOf("ru", "ёж"))
        assertEquals(WordReasons.NOT_TOKENIZABLE, reasonOf("ru", "кофe")) // a Latin "e"
    }

    @Test
    fun uzbekLatinApostropheVariantsFoldAndDigraphsCountAsOneLetter() {
        for (spelling in listOf("o'g'il", "o‘g‘il", "o’g’il", "o`g`il", "oʼgʼil", "OʻGʻIL")) {
            val valid = assertIs<WordValidator.Valid>(validate("uz-latn", spelling), spelling)
            assertEquals("oʻgʻil", valid.normalized, spelling)
            assertEquals(listOf("oʻ", "gʻ", "i", "l"), valid.graphemes, spelling)
        }
        assertTrue(validate("uz-latn", "shahar") is WordValidator.Valid) // sh-a-h-a-r: 5 letters in 6 characters
        assertEquals(WordReasons.BAD_LENGTH, reasonOf("uz-latn", "choy")) // ch-o-y: 3 letters in 4 characters
    }
}
