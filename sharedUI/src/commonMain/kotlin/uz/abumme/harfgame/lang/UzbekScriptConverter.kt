package uz.abumme.harfgame.lang

import uz.abumme.harfgame.data.stats.InProgressRound
import uz.abumme.harfgame.data.stats.InProgressRow
import uz.abumme.harfgame.engine.ScoreResult
import uz.abumme.harfgame.engine.Scorer

object UzbekScriptConverter {

    private val latnToCyrl = mapOf(
        "a" to "а", "b" to "б", "d" to "д", "e" to "е", "f" to "ф", "g" to "г",
        "h" to "ҳ", "i" to "и", "j" to "ж", "k" to "к", "l" to "л", "m" to "м",
        "n" to "н", "o" to "о", "p" to "п", "q" to "қ", "r" to "р", "s" to "с",
        "t" to "т", "u" to "у", "v" to "в", "x" to "х", "y" to "й", "z" to "з",
        "sh" to "ш", "ch" to "ч", "ng" to "нг", "oʻ" to "ў", "gʻ" to "ғ",
        "o'" to "ў", "g'" to "ғ",
    )

    private val cyrlToLatn = mapOf(
        "а" to "a", "б" to "b", "д" to "d", "е" to "e", "э" to "e", "ф" to "f",
        "г" to "g", "ҳ" to "h", "х" to "x", "и" to "i", "ж" to "j", "к" to "k",
        "л" to "l", "м" to "m", "н" to "n", "о" to "o", "п" to "p", "қ" to "q",
        "р" to "r", "с" to "s", "т" to "t", "у" to "u", "в" to "v", "й" to "y",
        "з" to "z", "ш" to "sh", "ч" to "ch", "ў" to "oʻ", "ғ" to "gʻ", "нг" to "ng",
        "я" to "ya", "ю" to "yu", "ё" to "yo", "ц" to "s",
    )

    fun translateGraphemes(graphemes: List<String>, fromScript: String, toScript: String): List<String> {
        if (fromScript == toScript) return graphemes
        val map = if (fromScript == "uz-latn" && toScript == "uz-cyrl") latnToCyrl else cyrlToLatn
        return graphemes.map { map[it.lowercase()] ?: it }
    }

    fun convertRound(
        round: InProgressRound,
        targetScript: String,
        targetAnswer: List<String>,
    ): InProgressRound {
        if (round.languageId == targetScript) return round
        val convertedRows = round.rows.map { row ->
            val convertedGraphemes = translateGraphemes(row.graphemes, round.languageId, targetScript)
            val marks = (Scorer.score(convertedGraphemes, targetAnswer) as? ScoreResult.Scored)
                ?.marks?.map { it.ordinal } ?: row.marks
            InProgressRow(convertedGraphemes, marks)
        }
        return round.copy(
            languageId = targetScript,
            rows = convertedRows,
            current = emptyList(),
        )
    }
}
