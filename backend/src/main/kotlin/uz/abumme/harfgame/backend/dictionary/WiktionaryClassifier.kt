package uz.abumme.harfgame.backend.dictionary

/**
 * Decides from an English Wiktionary page's categories whether a word is a real word of the suggestion's
 * language. Only that language's categories count ("Russian lemmas" for ru — a page shared with Bulgarian
 * proves nothing), and a flag needing human judgement beats a lemma: "Павел" is both a lemma and a proper
 * noun, so it goes to editors.
 */
object WiktionaryClassifier {
    private val notFound = LookupResult.Review(ReviewReason.NOT_FOUND)

    private val languageNames = mapOf(
        "en" to "English",
        "ru" to "Russian",
        "kk" to "Kazakh",
        "uz-latn" to "Uzbek",
        "uz-cyrl" to "Uzbek",
    )

    /** Category endings (after "<Language> ") that send a word to editors. */
    private val blocking = listOf(
        "proper nouns" to ReviewReason.PROPER_NOUN,
        "abbreviations" to ReviewReason.ABBREVIATION,
        "acronyms" to ReviewReason.ABBREVIATION,
        "initialisms" to ReviewReason.ABBREVIATION,
        "misspellings" to ReviewReason.MISSPELLING,
        "vulgarities" to ReviewReason.VULGAR,
        "swear words" to ReviewReason.VULGAR,
        "derogatory terms" to ReviewReason.VULGAR,
        "offensive terms" to ReviewReason.VULGAR,
        "slurs" to ReviewReason.VULGAR,
    )

    // Uzbek Cyrillic entries (китоб) carry only "variant lemmas".
    private val dictionaryForms = setOf("lemmas", "variant lemmas")
    private const val INFLECTED_FORMS = "non-lemma forms"

    /** Wiktionary's name for [lang], or null when the language is not verified automatically. */
    fun languageName(lang: String): String? = languageNames[lang]

    /** [categories] are full titles ("Category:Russian lemmas"); an empty list is a missing page. */
    fun classify(lang: String, categories: List<String>): LookupResult {
        val prefix = "Category:${languageName(lang) ?: return notFound} "
        val own = categories.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }

        blocking.firstOrNull { (ending, _) -> own.any { it.endsWith(ending) } }
            ?.let { (_, reason) -> return LookupResult.Review(reason) }
        return when {
            own.any { it in dictionaryForms } -> LookupResult.Auto(WordForm.DICTIONARY)
            INFLECTED_FORMS in own -> LookupResult.Auto(WordForm.INFLECTED)
            else -> notFound
        }
    }
}
