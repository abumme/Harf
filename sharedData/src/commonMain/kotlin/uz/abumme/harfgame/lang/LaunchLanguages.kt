package uz.abumme.harfgame.lang

/**
 * The launch language configs. Orthography decisions from docs/01: tutuq words
 * excluded (content-side), Uzbek 1995 with U+02BB apostrophe, Russian ё=е,
 * Kazakh restricted to native-word alphabet, board length 4–7.
 *
 * The Kazakh/Uzbek-Cyrillic inventories are first-pass and flagged for native review.
 */
object LaunchLanguages {

    val uzLatn = LanguageConfig(
        id = "uz-latn",
        displayName = "Oʻzbekcha",
        scriptLabel = "Lotin",
        graphemes = listOf(
            "a", "b", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m",
            "n", "o", "p", "q", "r", "s", "t", "u", "v", "x", "y", "z",
            "sh", "ch", "ng", "oʻ", "gʻ",
        ),
        exceptions = mapOf(
            // -ga postposition: n+g across a morpheme boundary, NOT the digraph "ng"
            "ustunga" to listOf("u", "s", "t", "u", "n", "g", "a"),
        ),
        normalizeApostrophe = true,
    )

    val uzCyrl = LanguageConfig(
        id = "uz-cyrl",
        displayName = "Ўзбекча",
        scriptLabel = "Кирилл",
        graphemes = listOf(
            "а", "б", "в", "г", "ғ", "д", "е", "ё", "ж", "з", "и", "й",
            "к", "қ", "л", "м", "н", "о", "п", "р", "с", "т", "у", "ў",
            "ф", "х", "ҳ", "ц", "ч", "ш", "ъ", "ь", "э", "ю", "я",
        ),
    )

    val ru = LanguageConfig(
        id = "ru",
        displayName = "Русский",
        scriptLabel = "Кириллица",
        graphemes = listOf(
            "а", "б", "в", "г", "д", "е", "ж", "з", "и", "й", "к", "л",
            "м", "н", "о", "п", "р", "с", "т", "у", "ф", "х", "ц", "ч",
            "ш", "щ", "ъ", "ы", "ь", "э", "ю", "я",
        ),
        replacements = mapOf("ё" to "е"), // ё plays as е (universal RU-clone convention)
    )

    val en = LanguageConfig(
        id = "en",
        displayName = "English",
        scriptLabel = "Latin",
        graphemes = ('a'..'z').map { it.toString() },
    )

    val kk = LanguageConfig(
        id = "kk",
        displayName = "Қазақша",
        scriptLabel = "Кирилл",
        // native-word alphabet: в ё ф ц ч щ ъ ь э ю я excluded (Russian-loan-only)
        graphemes = listOf(
            "а", "ә", "б", "г", "ғ", "д", "е", "ж", "з", "и", "й", "к",
            "қ", "л", "м", "н", "ң", "о", "ө", "п", "р", "с", "т", "у",
            "ұ", "ү", "х", "һ", "ы", "і", "ш",
        ),
    )

    val all: Map<String, LanguageConfig> = listOf(uzLatn, uzCyrl, ru, en, kk).associateBy { it.id }
}
