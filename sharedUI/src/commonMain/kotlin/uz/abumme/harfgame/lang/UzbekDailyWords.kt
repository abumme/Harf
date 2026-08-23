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
    )
}
