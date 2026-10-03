package uz.abumme.harfgame.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic design-system color roles. All Harf UI reads these rather than hard-coded
 * colors. Feedback roles (correct/present/absent) pair with mark shapes so states are
 * distinguishable without color (see [HarfMarkStyle]).
 */
@Immutable
data class HarfColors(
    val paper: Color,
    val paper2: Color,
    val card: Color,
    val ink: Color,
    val muted: Color,
    val rule: Color,
    val accent: Color,
    val correct: Color,
    val present: Color,
    val absent: Color,
    val key: Color,
    val keyDigraph: Color,
    val keyAbsent: Color,
    // Status roles. Defaulted so every edition gets sensible destructive/success colors;
    // an edition may override them. No screen should reach for a literal color for these.
    val danger: Color = Color(0xFFB3282D),
    val onDanger: Color = Color(0xFFFFFFFF),
    val success: Color = Color(0xFF2E7D46),
    val onSuccess: Color = Color(0xFFFFFFFF),
    // Letter color drawn over a filled feedback mark, so a glyph stays legible on the fill
    // (dark-on-navy / dark-on-red otherwise). Light editions fill dark → white letter.
    val onCorrect: Color = Color(0xFFFBFCFE),
    val onPresent: Color = Color(0xFFFBFCFE),
    val onAbsent: Color = Color(0xFFFBFCFE),
)

/** How feedback marks are drawn; the shape channel that keeps states colorblind-safe. */
enum class HarfMarkStyle { Scribble, Fill, Outline }

/** A named palette (edition) = an id, a display name, and its color roles on a light and a dark ground. */
@Immutable
data class HarfPalette(
    val id: String,
    val displayName: String,
    val colors: HarfColors,
    val dark: HarfColors,
)

// Dark editions share one recipe: a near-black ground faintly tinted toward the edition's hue,
// lighter steps for cards and variant surfaces, light ink, and the edition's accent/feedback
// hues lifted to read on dark. Not an inversion: which hue means correct/present and the mark
// shapes stay the same. Filled marks are light, so a letter over them takes the ground color;
// the absent fill stays recessive (mid-grey), so the light ink reads over it.
val dark_absent = Color(0xcc7A818A)
val dark_danger = Color(0xFFE0656A)
val dark_success = Color(0xFF63B981)

private fun darkColors(
    paper: Color,
    paper2: Color,
    card: Color,
    ink: Color,
    muted: Color,
    rule: Color,
    accent: Color,
    correct: Color,
    present: Color,
    key: Color,
    keyDigraph: Color,
    keyAbsent: Color,
) = HarfColors(
    paper = paper,
    paper2 = paper2,
    card = card,
    ink = ink,
    muted = muted,
    rule = rule,
    accent = accent,
    correct = correct,
    present = present,
    absent = dark_absent,
    key = key,
    keyDigraph = keyDigraph,
    keyAbsent = keyAbsent,
    danger = dark_danger,
    onDanger = paper,
    success = dark_success,
    onSuccess = paper,
    onCorrect = paper,
    onPresent = paper,
    onAbsent = ink,
)

// Newsprint
val newsprint_paper = Color(0xFFFBFCFE)
val newsprint_paper2 = Color(0xFFEEF2F6)
val newsprint_card = Color(0xFFFFFFFF)
val newsprint_ink = Color(0xFF12161B)
val newsprint_muted = Color(0xFF59626D)
val newsprint_rule = Color(0xFFD8DEE6)
val newsprint_accent = Color(0xFF1C3F63)
val newsprint_correct = Color(0xFF1C3F63)
val newsprint_present = Color(0xFFB3282D)
val newsprint_absent = Color(0xcc636262)
val newsprint_key = Color(0xFFE7ECF1)
val newsprint_keyDigraph = Color(0xFFE2EBF3)
val newsprint_keyAbsent = Color(0xFFDCE1E7)

// Press
val press_paper = Color(0xFFFBFCFE)
val press_paper2 = Color(0xFFEEF2F6)
val press_card = Color(0xFFFFFFFF)
val press_ink = Color(0xFF12161B)
val press_muted = Color(0xFF59626D)
val press_rule = Color(0xFFD8DEE6)
val press_accent = Color(0xFFB3282D)
val press_correct = Color(0xFFB3282D)
val press_present = Color(0xFF1C3F63)
val press_absent = Color(0xcc636262)
val press_key = Color(0xFFE9EDF1)
val press_keyDigraph = Color(0xFFF3E3E3)
val press_keyAbsent = Color(0xFFDCE1E7)

// Ink
val ink_paper = Color(0xFFFBFCFD)
val ink_paper2 = Color(0xFFEEF1F4)
val ink_card = Color(0xFFFFFFFF)
val ink_ink = Color(0xFF111417)
val ink_muted = Color(0xFF565D64)
val ink_rule = Color(0xFFD9DDE2)
val ink_accent = Color(0xFF1F242A)
val ink_correct = Color(0xFF262B31)
val ink_present = Color(0xFF2F6F9F)
val ink_absent = Color(0xcc636262)
val ink_key = Color(0xFFE7EAEE)
val ink_keyDigraph = Color(0xFFE6E9ED)
val ink_keyAbsent = Color(0xFFDBDFE3)

// Blueprint
val blueprint_paper = Color(0xFFEAF0F6)
val blueprint_paper2 = Color(0xFFDDE7F0)
val blueprint_card = Color(0xFFF4F8FC)
val blueprint_ink = Color(0xFF0F2338)
val blueprint_muted = Color(0xFF4A5B6E)
val blueprint_rule = Color(0xFFC6D4E2)
val blueprint_accent = Color(0xFF1C5A8A)
val blueprint_correct = Color(0xFF1C5A8A)
val blueprint_present = Color(0xFFB3282D)
val blueprint_absent = Color(0xcc636262)
val blueprint_key = Color(0xFFDBE6F1)
val blueprint_keyDigraph = Color(0xFFD2E3F2)
val blueprint_keyAbsent = Color(0xFFCDD8E3)

// Schoolbook
val schoolbook_paper = Color(0xFFF7FAF5)
val schoolbook_paper2 = Color(0xFFEAF0E6)
val schoolbook_card = Color(0xFFFFFFFF)
val schoolbook_ink = Color(0xFF25291F)
val schoolbook_muted = Color(0xFF5F6555)
val schoolbook_rule = Color(0xFFD7DDCA)
val schoolbook_accent = Color(0xFF4E7D3F)
val schoolbook_correct = Color(0xFF4E7D3F)
val schoolbook_present = Color(0xFFC06A1F)
val schoolbook_absent = Color(0xcc636262)
val schoolbook_key = Color(0xFFEAEEE0)
val schoolbook_keyDigraph = Color(0xFFE6EFDB)
val schoolbook_keyAbsent = Color(0xFFDADBCC)

/** The cold "newspaper" editions ported from docs/mockup.html. */
object HarfPalettes {
    val Newsprint = HarfPalette(
        "newsprint", "Newsprint",
        HarfColors(
            paper = newsprint_paper,
            paper2 = newsprint_paper2,
            card = newsprint_card,
            ink = newsprint_ink,
            muted = newsprint_muted,
            rule = newsprint_rule,
            accent = newsprint_accent,
            correct = newsprint_correct,
            present = newsprint_present,
            absent = newsprint_absent,
            key = newsprint_key,
            keyDigraph = newsprint_keyDigraph,
            keyAbsent = newsprint_keyAbsent,
        ),
        darkColors(
            paper = Color(0xFF0F1216),
            paper2 = Color(0xFF1D2229),
            card = Color(0xFF171B21),
            ink = Color(0xFFE9ECF1),
            muted = Color(0xFF8A94A0),
            rule = Color(0xFF2A313A),
            accent = Color(0xFF6FA3D6),
            correct = Color(0xFF6FA3D6),
            present = Color(0xFFE0656A),
            key = Color(0xFF232A33),
            keyDigraph = Color(0xFF23303F),
            keyAbsent = Color(0xFF191E24),
        ),
    )
    val Press = HarfPalette(
        "press", "Press Red",
        HarfColors(
            paper = press_paper,
            paper2 = press_paper2,
            card = press_card,
            ink = press_ink,
            muted = press_muted,
            rule = press_rule,
            accent = press_accent,
            correct = press_correct,
            present = press_present,
            absent = press_absent,
            key = press_key,
            keyDigraph = press_keyDigraph,
            keyAbsent = press_keyAbsent,
        ),
        darkColors(
            paper = Color(0xFF0F1216),
            paper2 = Color(0xFF1D2229),
            card = Color(0xFF171B21),
            ink = Color(0xFFE9ECF1),
            muted = Color(0xFF8A94A0),
            rule = Color(0xFF2A313A),
            accent = Color(0xFFE0656A),
            correct = Color(0xFFE0656A),
            present = Color(0xFF6FA3D6),
            key = Color(0xFF232A33),
            keyDigraph = Color(0xFF322A2E),
            keyAbsent = Color(0xFF191E24),
        ),
    )
    val Ink = HarfPalette(
        "ink", "Ink",
        HarfColors(
            paper = ink_paper,
            paper2 = ink_paper2,
            card = ink_card,
            ink = ink_ink,
            muted = ink_muted,
            rule = ink_rule,
            accent = ink_accent,
            correct = ink_correct,
            present = ink_present,
            absent = ink_absent,
            key = ink_key,
            keyDigraph = ink_keyDigraph,
            keyAbsent = ink_keyAbsent,
        ),
        darkColors(
            paper = Color(0xFF101214),
            paper2 = Color(0xFF1E2124),
            card = Color(0xFF181A1D),
            ink = Color(0xFFE8EAEC),
            muted = Color(0xFF8D939A),
            rule = Color(0xFF2C3035),
            accent = Color(0xFFD5DADF),
            correct = Color(0xFFC9CED4),
            present = Color(0xFF74AEDB),
            key = Color(0xFF24282C),
            keyDigraph = Color(0xFF272B30),
            keyAbsent = Color(0xFF1A1D20),
        ),
    )
    val Blueprint = HarfPalette(
        "blueprint",
        "Blueprint",
        HarfColors(
            paper = blueprint_paper,
            paper2 = blueprint_paper2,
            card = blueprint_card,
            ink = blueprint_ink,
            muted = blueprint_muted,
            rule = blueprint_rule,
            accent = blueprint_accent,
            correct = blueprint_correct,
            present = blueprint_present,
            absent = blueprint_absent,
            key = blueprint_key,
            keyDigraph = blueprint_keyDigraph,
            keyAbsent = blueprint_keyAbsent,
        ),
        darkColors(
            paper = Color(0xFF0D1520),
            paper2 = Color(0xFF1C2839),
            card = Color(0xFF142030),
            ink = Color(0xFFE3ECF5),
            muted = Color(0xFF8798AB),
            rule = Color(0xFF263447),
            accent = Color(0xFF5FA8E0),
            correct = Color(0xFF5FA8E0),
            present = Color(0xFFE0656A),
            key = Color(0xFF1D2A3B),
            keyDigraph = Color(0xFF1E3249),
            keyAbsent = Color(0xFF152030),
        ),
    )
    val Schoolbook = HarfPalette(
        "schoolbook",
        "Schoolbook",
        HarfColors(
            paper = schoolbook_paper,
            paper2 = schoolbook_paper2,
            card = schoolbook_card,
            ink = schoolbook_ink,
            muted = schoolbook_muted,
            rule = schoolbook_rule,
            accent = schoolbook_accent,
            correct = schoolbook_correct,
            present = schoolbook_present,
            absent = schoolbook_absent,
            key = schoolbook_key,
            keyDigraph = schoolbook_keyDigraph,
            keyAbsent = schoolbook_keyAbsent,
        ),
        darkColors(
            paper = Color(0xFF10130F),
            paper2 = Color(0xFF1F241C),
            card = Color(0xFF171B15),
            ink = Color(0xFFE7EBE2),
            muted = Color(0xFF8E9585),
            rule = Color(0xFF2C3328),
            accent = Color(0xFF82B56F),
            correct = Color(0xFF82B56F),
            present = Color(0xFFE39A55),
            key = Color(0xFF242A20),
            keyDigraph = Color(0xFF27311F),
            keyAbsent = Color(0xFF1A1E17),
        ),
    )

    val all: List<HarfPalette> = listOf(Newsprint, Press, Ink, Blueprint, Schoolbook)

    fun byId(id: String): HarfPalette = all.firstOrNull { it.id == id } ?: Newsprint
}

val LocalHarfColors = staticCompositionLocalOf { HarfPalettes.Newsprint.colors }

/** Id of the palette [HarfTheme] is drawing — not necessarily the saved choice, which may be unowned. */
val LocalHarfPaletteId = staticCompositionLocalOf { HarfPalettes.Newsprint.id }
val LocalHarfMarkStyle = staticCompositionLocalOf { HarfMarkStyle.Scribble }
