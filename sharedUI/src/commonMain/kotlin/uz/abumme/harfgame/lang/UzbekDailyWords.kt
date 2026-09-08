package uz.abumme.harfgame.lang

/**
 * Uzbek daily words as paired lexemes — one word, two scripts — so the daily puzzle is the
 * same lexeme whichever script the player picks. First-pass, tile-count-matched, needs review.
 */
object UzbekDailyWords {
    val lexemes: List<Lexeme> = listOf(
        Lexeme("kitob", mapOf("uz-latn" to listOf("k", "i", "t", "o", "b"), "uz-cyrl" to listOf("к", "и", "т", "о", "б"))),
        Lexeme("bahor", mapOf("uz-latn" to listOf("b", "a", "h", "o", "r"), "uz-cyrl" to listOf("б", "а", "ҳ", "о", "р"))),
        Lexeme("shahar", mapOf("uz-latn" to listOf("sh", "a", "h", "a", "r"), "uz-cyrl" to listOf("ш", "а", "ҳ", "а", "р"))),
        Lexeme("qalam", mapOf("uz-latn" to listOf("q", "a", "l", "a", "m"), "uz-cyrl" to listOf("қ", "а", "л", "а", "м"))),
        Lexeme("bulut", mapOf("uz-latn" to listOf("b", "u", "l", "u", "t"), "uz-cyrl" to listOf("б", "у", "л", "у", "т"))),
        // candidate additions — 5 graphemes in both scripts, needs native review before release
        Lexeme("salom", mapOf("uz-latn" to listOf("s", "a", "l", "o", "m"), "uz-cyrl" to listOf("с", "а", "л", "о", "м"))),
        Lexeme("bolta", mapOf("uz-latn" to listOf("b", "o", "l", "t", "a"), "uz-cyrl" to listOf("б", "о", "л", "т", "а"))),
        Lexeme("chiroq", mapOf("uz-latn" to listOf("ch", "i", "r", "o", "q"), "uz-cyrl" to listOf("ч", "и", "р", "о", "қ"))),
        Lexeme("tarix", mapOf("uz-latn" to listOf("t", "a", "r", "i", "x"), "uz-cyrl" to listOf("т", "а", "р", "и", "х"))),
        Lexeme("gilos", mapOf("uz-latn" to listOf("g", "i", "l", "o", "s"), "uz-cyrl" to listOf("г", "и", "л", "о", "с"))),
        Lexeme("somsa", mapOf("uz-latn" to listOf("s", "o", "m", "s", "a"), "uz-cyrl" to listOf("с", "о", "м", "с", "а"))),
        Lexeme("palov", mapOf("uz-latn" to listOf("p", "a", "l", "o", "v"), "uz-cyrl" to listOf("п", "а", "л", "о", "в"))),
        Lexeme("bodom", mapOf("uz-latn" to listOf("b", "o", "d", "o", "m"), "uz-cyrl" to listOf("б", "о", "д", "о", "м"))),
        Lexeme("limon", mapOf("uz-latn" to listOf("l", "i", "m", "o", "n"), "uz-cyrl" to listOf("л", "и", "м", "о", "н"))),
        Lexeme("tovuq", mapOf("uz-latn" to listOf("t", "o", "v", "u", "q"), "uz-cyrl" to listOf("т", "о", "в", "у", "қ"))),
        Lexeme("temir", mapOf("uz-latn" to listOf("t", "e", "m", "i", "r"), "uz-cyrl" to listOf("т", "е", "м", "и", "р"))),
        Lexeme("kamon", mapOf("uz-latn" to listOf("k", "a", "m", "o", "n"), "uz-cyrl" to listOf("к", "а", "м", "о", "н"))),
    )
}
