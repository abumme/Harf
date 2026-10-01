package uz.abumme.harfgame.feature.game

import uz.abumme.harfgame.lang.LaunchLanguages
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The game screen's vertical budget and key sizing, pinned against the reference mockup
 * (`docs/ui-review/game-layout.html`, sections 02 and 04). Sizes are in dp.
 */
class GameLayoutTest {

    private data class Device(val name: String, val width: Float, val height: Float)

    /** Status bar 24 + gesture navigation 24; the plan receives the height left inside the insets and paddings. */
    private val insets = 48f

    private val devices = listOf(
        Device("iPhone SE 1", 320f, 568f),
        Device("16:9 phone", 360f, 640f),
        Device("Galaxy S8", 360f, 740f),
        Device("Galaxy A", 360f, 800f),
        Device("iPhone 15", 393f, 852f),
        Device("Pixel 7", 412f, 915f),
        Device("Pro Max", 430f, 932f),
    )

    private fun shape(lang: String) = KeyboardShape.of(LaunchLanguages.all.getValue(lang))

    private fun portrait(d: Device, lang: String, tiles: Int = 5) = GameLayout.portrait(
        width = d.width,
        height = d.height - insets - 2 * GameLayout.PAD_V,
        shape = shape(lang),
        tiles = tiles,
    )

    private fun assertNear(expected: Float, actual: Float, what: String, tolerance: Float = 0.06f) {
        assertTrue(abs(expected - actual) <= tolerance, "$what: expected $expected, got $actual")
    }

    @Test
    fun every_launch_language_has_four_keyboard_rows() {
        for ((id, config) in LaunchLanguages.all) {
            assertEquals(4, KeyboardShape.of(config).rowCount, "$id: three letter rows plus the action row")
        }
    }

    @Test
    fun action_row_counts_toward_the_widest_row() {
        val narrowLetters = KeyboardShape(letterRows = listOf(listOf("a", "b", "c")), actionRowKeys = listOf("d", "e", "f", "g"))
        // two 3-unit action keys plus four graphemes beat the three-key letter row
        assertEquals(10, narrowLetters.maxUnits)
        assertEquals(9, shape("uz-latn").maxUnits, "uz-latn: nine-key letter rows are wider than ENTER oʻ gʻ ⌫")
        assertEquals(12, shape("uz-cyrl").maxUnits)
    }

    @Test
    fun phone_matrix_keeps_board_and_keys_in_range() {
        for (d in devices) for (lang in LaunchLanguages.all.keys) {
            val plan = portrait(d, lang)
            val where = "${d.name} $lang"
            if (d.height >= 640f) assertTrue(plan.tile >= 36f, "$where: tile ${plan.tile} below the 36 dp minimum")
            assertTrue(plan.keyWidth in 21f..56f, "$where: key width ${plan.keyWidth}")
            assertTrue(plan.keyHeight in GameLayout.KEY_HEIGHTS, "$where: key height ${plan.keyHeight}")
            assertTrue(plan.keyboardWidth <= d.width - 2 * GameLayout.KB_PAD_H + 0.01f, "$where: keyboard wider than the viewport")
            val stack = GameLayout.TOP_BAR + GameLayout.STRIP + 3 * GameLayout.GAP + plan.boardHeight + plan.keyboardHeight
            assertTrue(stack <= d.height - insets - 2 * GameLayout.PAD_V + 0.01f, "$where: stack $stack overflows")
        }
    }

    @Test
    fun portrait_sizes_match_the_mockup() {
        val small = devices[1] // 360×640
        portrait(small, "en").let { assertNear(33.4f, it.keyWidth, "en key"); assertEquals(48f, it.keyHeight); assertNear(40f, it.tile, "en tile") }
        portrait(small, "uz-latn").let { assertNear(37.33f, it.keyWidth, "uz-latn key"); assertNear(40f, it.tile, "uz-latn tile") }
        portrait(small, "uz-cyrl").let { assertNear(27.5f, it.keyWidth, "uz-cyrl key"); assertNear(40f, it.tile, "uz-cyrl tile") }
        portrait(small, "kk").let { assertNear(30.18f, it.keyWidth, "kk key") }
        portrait(devices[4], "uz-latn").let { assertEquals(64f, it.tile); assertNear(41f, it.keyWidth, "iPhone 15 uz-latn key") }
        portrait(devices[5], "uz-cyrl").let { assertEquals(64f, it.tile); assertNear(31.83f, it.keyWidth, "Pixel 7 uz-cyrl key") }
    }

    @Test
    fun keyboard_height_is_the_same_for_every_language_on_one_device() {
        for (d in devices) {
            val heights = LaunchLanguages.all.keys.map { portrait(d, it).keyboardHeight }.toSet()
            assertEquals(1, heights.size, "${d.name}: keyboard heights differ across languages: $heights")
        }
    }

    @Test
    fun key_height_steps_down_only_on_short_viewports() {
        portrait(devices[0], "uz-latn").let { assertEquals(40f, it.keyHeight); assertNear(33.33f, it.tile, "SE1 tile") }
        assertEquals(48f, portrait(devices[1], "uz-latn").keyHeight, "360×640 keeps 48 dp keys (tile exactly 40)")
        assertEquals(48f, portrait(devices[2], "ru").keyHeight)
        // a viewport just too short for 48 dp keys and 40 dp tiles steps to 44 first
        val plan = GameLayout.portrait(width = 360f, height = 560f, shape = shape("en"), tiles = 5)
        assertEquals(44f, plan.keyHeight)
        assertTrue(plan.tile >= 36f, "44 dp keys keep the tile above the minimum: ${plan.tile}")
    }

    @Test
    fun key_width_is_capped_on_wide_viewports() {
        val plan = GameLayout.portrait(width = 700f, height = 1000f, shape = shape("en"), tiles = 5)
        assertEquals(56f, plan.keyWidth)
        assertNear(10 * 56f + 9 * GameLayout.KEY_GAP, plan.keyboardWidth, "keyboard width at the cap")
        assertEquals(64f, plan.tile)
    }

    @Test
    fun wide_layout_matches_the_mockup() {
        // Pixel 7 landscape: 915×412, left half holds the board, right half the keyboard
        val plan = GameLayout.wide(width = 915f, height = 412f - insets - 2 * GameLayout.PAD_V, shape = shape("uz-latn"), tiles = 5)
        assertNear(38.33f, plan.tile, "landscape tile")
        assertEquals(48f, plan.keyHeight)
        assertNear(45.5f, plan.keyWidth, "landscape key")
        // desktop window: keys stay well under the cap, the board hits its maximum
        val desktop = GameLayout.wide(width = 1100f, height = 720f - insets - 2 * GameLayout.PAD_V, shape = shape("en"), tiles = 5)
        assertEquals(64f, desktop.tile)
        assertNear(50f, desktop.keyWidth, "desktop key")
    }

    @Test
    fun label_size_scales_with_key_width_within_bounds() {
        assertEquals(12f, GameLayout.keyLabelSp(24f))
        assertEquals(16f, GameLayout.keyLabelSp(37.3f))
        assertNear(14.4f, GameLayout.keyLabelSp(30f), "mid-size label", tolerance = 0.01f)
    }
}
