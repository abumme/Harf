package uz.abumme.harfgame.feature.result

import uz.abumme.harfgame.engine.Mark

/**
 * Builds the shareable emoji grid. Squares map to the active palette (never Wordle's
 * green/yellow); absent is always ⬜. The text grid is the copy-paste-proof viral artifact.
 */
object ShareGrid {

    private val correct = mapOf(
        "newsprint" to "🟦", "press" to "🟥", "ink" to "⬛", "blueprint" to "🟦", "schoolbook" to "🟩",
    )
    private val present = mapOf(
        "newsprint" to "🟥", "press" to "🟦", "ink" to "🟦", "blueprint" to "🟥", "schoolbook" to "🟧",
    )

    fun emoji(paletteId: String, mark: Mark): String = when (mark) {
        Mark.CORRECT -> correct[paletteId] ?: "🟧"
        Mark.PRESENT -> present[paletteId] ?: "🟦"
        Mark.ABSENT -> "⬜"
    }

    fun build(
        paletteId: String,
        languageDisplay: String,
        puzzleNumber: Long,
        rows: List<List<Mark>>,
        won: Boolean,
        maxAttempts: Int = 6,
        isArchive: Boolean = false,
        hardMode: Boolean = false,
    ): String {
        val score = if (won) rows.size.toString() else "X"
        val hardMarker = if (hardMode) "*" else ""
        val prefix = if (isArchive) "Harf Archive" else "Harf"
        val header = "$prefix · $languageDisplay · №$puzzleNumber  $score/$maxAttempts$hardMarker"
        val grid = rows.joinToString("\n") { row -> row.joinToString("") { emoji(paletteId, it) } }
        return "$header\n$grid"
    }
}
