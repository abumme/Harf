package uz.abumme.harfgame.feature.game

import uz.abumme.harfgame.lang.LanguageConfig

/**
 * The row structure of a language's on-screen keyboard: its letter rows plus the graphemes hosted in the
 * action row between enter and delete. Fixed per language — it never depends on the viewport.
 */
data class KeyboardShape(val letterRows: List<List<String>>, val actionRowKeys: List<String> = emptyList()) {
    /** Letter rows plus the action row. */
    val rowCount: Int get() = letterRows.size + 1

    /** Width of the widest row in letter-key units; the action row is two action keys plus its graphemes. */
    val maxUnits: Int get() = maxOf(letterRows.maxOf { it.size }, 2 * GameLayout.ACTION_UNITS + actionRowKeys.size)

    /** Every grapheme key: the letter rows flattened, then the action-row keys. */
    val keys: List<String> get() = letterRows.flatten() + actionRowKeys

    companion object {
        /** The layout registered for [config]'s language in [KeyboardLayouts]; a language without one fails fast. */
        fun of(config: LanguageConfig): KeyboardShape = KeyboardLayouts.all.getValue(config.id)
    }
}

/** The sizes (dp) the game screen lays out from, computed by [GameLayout] for one viewport. */
data class GameLayoutPlan(
    val tile: Float,
    val tiles: Int,
    val attempts: Int,
    val keyWidth: Float,
    val keyHeight: Float,
    val keyboardWidth: Float,
    val keyboardHeight: Float,
) {
    val boardWidth: Float get() = tile * tiles + GameLayout.TILE_GAP * (tiles - 1)
    val boardHeight: Float get() = tile * attempts + GameLayout.TILE_GAP * (attempts - 1)
    val actionKeyWidth: Float get() = GameLayout.ACTION_UNITS * keyWidth + (GameLayout.ACTION_UNITS - 1) * GameLayout.KEY_GAP
}

/** The on-screen keyboard's sizes (dp) for one slot width and key height; see [GameLayout.keyboard]. */
data class KeyboardMetrics(val keyWidth: Float, val keyHeight: Float, val width: Float, val height: Float) {
    val actionKeyWidth: Float get() = GameLayout.ACTION_UNITS * keyWidth + (GameLayout.ACTION_UNITS - 1) * GameLayout.KEY_GAP
}

/**
 * The game screen's layout budget, pure and in dp so it can be pinned by tests and by the design mockup
 * (`docs/ui-review/game-layout.html`). The composable only measures and lays out from the returned plan.
 *
 * Portrait, top to bottom: a top bar, the board centered in whatever height is left, a fixed status strip,
 * and the keyboard slot. The keyboard's structure is fixed per language ([KeyboardShape]); its key width
 * comes from the viewport width, and its key height steps down (48 → 44 → 40) only when the board would
 * otherwise fall below a comfortable tile size.
 */
object GameLayout {
    /** Vertical padding above the top bar and below the keyboard, inside the window insets. */
    const val PAD_V = 8f
    const val TOP_BAR = 44f
    const val STRIP = 28f
    /** Gap between the stacked sections (top bar / board / strip / keyboard). */
    const val GAP = 8f
    const val BOARD_PAD_H = 16f
    /** The keyboard is full-bleed: only a sliver of side padding, like a system keyboard. */
    const val KB_PAD_H = 4f
    const val KEY_GAP = 2f
    const val ROW_GAP = 6f
    /** Enter and delete are each this many letter-key widths wide. */
    const val ACTION_UNITS = 3
    const val TILE_GAP = 6f
    const val TILE_MAX = 64f
    const val KEY_MAX = 56f
    /** Below this tile size the key height steps down; below [TILE_MIN] it steps down again. */
    const val TILE_COMFORT = 40f
    const val TILE_MIN = 36f
    val KEY_HEIGHTS: List<Float> = listOf(48f, 44f, 40f)
    private val TILE_THRESHOLDS: List<Float> = listOf(TILE_COMFORT, TILE_MIN, Float.NEGATIVE_INFINITY)
    /** Wide layout: outer side padding and the gap between the board half and the keyboard half. */
    const val WIDE_PAD_H = 16f
    const val WIDE_GAP = 16f

    /** [width] × [height] is the area inside the window insets and the vertical paddings. */
    fun portrait(width: Float, height: Float, shape: KeyboardShape, tiles: Int, attempts: Int = 6): GameLayoutPlan {
        val boardWidth = width - 2 * BOARD_PAD_H
        val chrome = TOP_BAR + STRIP + 3 * GAP
        return KEY_HEIGHTS.zip(TILE_THRESHOLDS).firstNotNullOf { (keyHeight, minTile) ->
            val kb = keyboard(width, shape, keyHeight)
            val tile = tileFor(boardWidth, height - chrome - kb.height, tiles, attempts)
            GameLayoutPlan(tile, tiles, attempts, kb.keyWidth, kb.keyHeight, kb.width, kb.height).takeIf { tile >= minTile }
        }
    }

    /** Side by side: the left half holds the top bar, board and strip; the right half the keyboard slot. */
    fun wide(width: Float, height: Float, shape: KeyboardShape, tiles: Int, attempts: Int = 6): GameLayoutPlan {
        val half = (width - 2 * WIDE_PAD_H - WIDE_GAP) / 2
        val keyHeight = KEY_HEIGHTS.firstOrNull { keyboardHeight(shape, it) <= height } ?: KEY_HEIGHTS.last()
        val kb = keyboard(half, shape, keyHeight)
        val tile = tileFor(half - 2 * BOARD_PAD_H, height - TOP_BAR - STRIP - 2 * GAP, tiles, attempts)
        return GameLayoutPlan(tile, tiles, attempts, kb.keyWidth, kb.keyHeight, kb.width, kb.height)
    }

    /**
     * Key and keyboard sizes for a slot of [availableWidth] (the full width the keyboard is given, before its own
     * [KB_PAD_H] side padding). The keyboard view and the screen plan call this with the same width, so they agree.
     */
    fun keyboard(availableWidth: Float, shape: KeyboardShape, keyHeight: Float): KeyboardMetrics {
        val keyWidth = keyWidth(availableWidth, shape)
        return KeyboardMetrics(keyWidth, keyHeight, keyboardWidth(keyWidth, shape), keyboardHeight(shape, keyHeight))
    }

    /** Key labels scale with the key so narrow Cyrillic keys stay legible and wide keys don't look empty. */
    fun keyLabelSp(keyWidth: Float): Float = (keyWidth * 0.48f).coerceIn(12f, 16f)

    private fun keyWidth(availableWidth: Float, shape: KeyboardShape): Float {
        val units = shape.maxUnits
        return ((availableWidth - 2 * KB_PAD_H - KEY_GAP * (units - 1)) / units).coerceIn(1f, KEY_MAX)
    }

    private fun keyboardWidth(keyWidth: Float, shape: KeyboardShape): Float =
        shape.maxUnits * keyWidth + (shape.maxUnits - 1) * KEY_GAP

    private fun keyboardHeight(shape: KeyboardShape, keyHeight: Float): Float =
        shape.rowCount * keyHeight + (shape.rowCount - 1) * ROW_GAP

    private fun tileFor(boardWidth: Float, boardHeight: Float, tiles: Int, attempts: Int): Float = minOf(
        TILE_MAX,
        (boardWidth - TILE_GAP * (tiles - 1)) / tiles,
        (boardHeight - TILE_GAP * (attempts - 1)) / attempts,
    ).coerceAtLeast(1f)
}
