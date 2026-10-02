package uz.abumme.harfgame.feature.game

import uz.abumme.harfgame.lang.LaunchLanguages

/**
 * The on-screen keyboard layout of each launch language, keyed by [uz.abumme.harfgame.lang.LanguageConfig.id].
 * App-only data: the server, the staff panel and the word-list tools never read it. Every key is a grapheme of
 * the language; digraphs are first-class keys.
 *
 * Layouts are first-pass and flagged for native review.
 */
object KeyboardLayouts {

    val all: Map<String, KeyboardShape> = mapOf(
        LaunchLanguages.uzLatn.id to KeyboardShape(
            letterRows = listOf(
                listOf("q", "e", "r", "t", "y", "u", "i", "o", "p"),
                listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
                listOf("sh", "ch", "ng", "z", "x", "v", "b", "n", "m"),
            ),
            // oʻ gʻ sit in the action row between enter and delete, keeping the letter rows at nine wide keys.
            actionRowKeys = listOf("oʻ", "gʻ"),
        ),
        LaunchLanguages.uzCyrl.id to KeyboardShape(
            letterRows = listOf(
                listOf("й", "ц", "у", "к", "е", "н", "г", "ш", "ў", "з", "х", "ъ"),
                listOf("ф", "қ", "в", "а", "п", "р", "о", "л", "д", "ж", "э", "ё"),
                listOf("я", "ч", "с", "м", "и", "т", "ь", "б", "ю", "ғ", "ҳ"),
            ),
        ),
        LaunchLanguages.ru.id to KeyboardShape(
            letterRows = listOf(
                listOf("й", "ц", "у", "к", "е", "н", "г", "ш", "щ", "з", "х", "ъ"),
                listOf("ф", "ы", "в", "а", "п", "р", "о", "л", "д", "ж", "э"),
                listOf("я", "ч", "с", "м", "и", "т", "ь", "б", "ю"),
            ),
        ),
        LaunchLanguages.en.id to KeyboardShape(
            letterRows = listOf(
                listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
                listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
                listOf("z", "x", "c", "v", "b", "n", "m"),
            ),
        ),
        LaunchLanguages.kk.id to KeyboardShape(
            letterRows = listOf(
                listOf("а", "ә", "б", "г", "ғ", "д", "е", "ж", "з", "и", "й"),
                listOf("к", "қ", "л", "м", "н", "ң", "о", "ө", "п", "р", "с"),
                listOf("т", "у", "ұ", "ү", "х", "һ", "ы", "і", "ш"),
            ),
        ),
    )
}
