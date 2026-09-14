package uz.abumme.harfgame.backend.dictionary

import kotlin.test.Test
import kotlin.test.assertEquals

/** Category lists are trimmed copies of real en.wiktionary responses (probed 2026-09-14). */
class WiktionaryClassifyTest {

    private fun cats(vararg titles: String) = titles.map { "Category:$it" }

    private fun classify(lang: String, categories: List<String>) = WiktionaryClassifier.classify(lang, categories)

    @Test
    fun lemmaIsDictionaryFormEvenOnAPageSharedWithOtherLanguages() {
        // книга: the page also has Bulgarian and Chechen sections.
        val categories = cats("Bulgarian lemmas", "Chechen lemmas", "Chechen terms borrowed from Russian", "Russian lemmas", "Russian nouns")
        assertEquals(LookupResult.Auto(WordForm.DICTIONARY), classify("ru", categories))
    }

    @Test
    fun nonLemmaIsInflectedForm() {
        // книги
        val categories = cats("Bulgarian non-lemma forms", "Macedonian noun forms", "Russian 2-syllable words", "Russian non-lemma forms", "Russian noun forms")
        assertEquals(LookupResult.Auto(WordForm.INFLECTED), classify("ru", categories))
    }

    @Test
    fun uzbekCyrillicVariantLemmaIsDictionaryForm() {
        // китоб carries only "variant lemmas", never plain "lemmas".
        val categories = cats("Tajik lemmas", "Uzbek countable nouns", "Uzbek terms in Cyrillic script", "Uzbek variant lemmas")
        assertEquals(LookupResult.Auto(WordForm.DICTIONARY), classify("uz-cyrl", categories))
    }

    @Test
    fun lemmaWinsOverNonLemmaOnTheSamePage() {
        // fixed: both a lemma (adjective) and a verb form.
        val categories = cats("English lemmas", "English non-lemma forms", "English verb forms")
        assertEquals(LookupResult.Auto(WordForm.DICTIONARY), classify("en", categories))
    }

    @Test
    fun eachLaunchLanguageMapsToItsWiktionaryLanguage() {
        val cases = listOf(
            "en" to "English lemmas",
            "ru" to "Russian lemmas",
            "kk" to "Kazakh lemmas",
            "uz-latn" to "Uzbek lemmas",
            "uz-cyrl" to "Uzbek variant lemmas",
        )
        for ((lang, category) in cases) {
            assertEquals(LookupResult.Auto(WordForm.DICTIONARY), classify(lang, cats(category)), lang)
        }
    }

    @Test
    fun pageOnlyInAnotherLanguageNeedsReview() {
        assertEquals(
            LookupResult.Review(ReviewReason.NOT_FOUND),
            classify("kk", cats("Russian lemmas", "Russian nouns")),
        )
    }

    @Test
    fun missingPageNeedsReview() {
        assertEquals(LookupResult.Review(ReviewReason.NOT_FOUND), classify("en", emptyList()))
    }

    @Test
    fun unmappedLanguageNeedsReview() {
        assertEquals(LookupResult.Review(ReviewReason.NOT_FOUND), classify("de", cats("German lemmas")))
    }

    @Test
    fun everyBlockingCategoryFlagsALemma() {
        val cases = listOf(
            "English proper nouns" to ReviewReason.PROPER_NOUN,
            "English 4-letter abbreviations" to ReviewReason.ABBREVIATION,
            "English acronyms" to ReviewReason.ABBREVIATION,
            "English initialisms" to ReviewReason.ABBREVIATION,
            "English deliberate misspellings" to ReviewReason.MISSPELLING,
            "English misspellings" to ReviewReason.MISSPELLING,
            "English vulgarities" to ReviewReason.VULGAR,
            "English swear words" to ReviewReason.VULGAR,
            "English derogatory terms" to ReviewReason.VULGAR,
            "English offensive terms" to ReviewReason.VULGAR,
            "English ethnic slurs" to ReviewReason.VULGAR,
        )
        for ((category, reason) in cases) {
            assertEquals(LookupResult.Review(reason), classify("en", cats("English lemmas", category)), category)
        }
    }

    @Test
    fun properNounLemmaIsFlagged() {
        // Павел is listed as a lemma and a proper noun.
        val categories = cats("Russian animate nouns", "Russian lemmas", "Russian male given names", "Russian proper nouns")
        assertEquals(LookupResult.Review(ReviewReason.PROPER_NOUN), classify("ru", categories))
    }

    @Test
    fun flagInAnotherLanguageDoesNotBlock() {
        assertEquals(
            LookupResult.Auto(WordForm.DICTIONARY),
            classify("ru", cats("English proper nouns", "Russian lemmas")),
        )
    }

    @Test
    fun eSpelledRussianEntryIsNotAMisspelling() {
        // елка: the ё-folded spelling the client sends.
        val categories = cats("Russian lemmas", "Russian terms spelled with Е instead of Ё")
        assertEquals(LookupResult.Auto(WordForm.DICTIONARY), classify("ru", categories))
    }
}
