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
)

/** How feedback marks are drawn; the shape channel that keeps states colorblind-safe. */
enum class HarfMarkStyle { Scribble, Fill, Outline }

/** A named palette (edition) = an id, a display name, and its color roles. */
@Immutable
data class HarfPalette(
    val id: String,
    val displayName: String,
    val colors: HarfColors,
)

/** The cold "newspaper" editions ported from docs/mockup.html. */
object HarfPalettes {
    val Newsprint = HarfPalette(
        "newsprint", "Newsprint",
        HarfColors(
            paper = Color(0xFFFBFCFE), paper2 = Color(0xFFEEF2F6), card = Color(0xFFFFFFFF),
            ink = Color(0xFF12161B), muted = Color(0xFF59626D), rule = Color(0xFFD8DEE6),
            accent = Color(0xFF1C3F63), correct = Color(0xFF1C3F63), present = Color(0xFFB3282D),
            absent = Color(0xFFDEE3E9), key = Color(0xFFE7ECF1), keyDigraph = Color(0xFFE2EBF3),
            keyAbsent = Color(0xFFDCE1E7),
        ),
    )
    val Press = HarfPalette(
        "press", "Press Red",
        HarfColors(
            paper = Color(0xFFFBFCFE), paper2 = Color(0xFFEEF2F6), card = Color(0xFFFFFFFF),
            ink = Color(0xFF12161B), muted = Color(0xFF59626D), rule = Color(0xFFD8DEE6),
            accent = Color(0xFFB3282D), correct = Color(0xFFB3282D), present = Color(0xFF1C3F63),
            absent = Color(0xFFDEE3E9), key = Color(0xFFE9EDF1), keyDigraph = Color(0xFFF3E3E3),
            keyAbsent = Color(0xFFDCE1E7),
        ),
    )
    val Ink = HarfPalette(
        "ink", "Ink",
        HarfColors(
            paper = Color(0xFFFBFCFD), paper2 = Color(0xFFEEF1F4), card = Color(0xFFFFFFFF),
            ink = Color(0xFF111417), muted = Color(0xFF565D64), rule = Color(0xFFD9DDE2),
            accent = Color(0xFF1F242A), correct = Color(0xFF262B31), present = Color(0xFF2F6F9F),
            absent = Color(0xFFDDE1E5), key = Color(0xFFE7EAEE), keyDigraph = Color(0xFFE6E9ED),
            keyAbsent = Color(0xFFDBDFE3),
        ),
    )
    val Blueprint = HarfPalette(
        "blueprint", "Blueprint",
        HarfColors(
            paper = Color(0xFFEAF0F6), paper2 = Color(0xFFDDE7F0), card = Color(0xFFF4F8FC),
            ink = Color(0xFF0F2338), muted = Color(0xFF4A5B6E), rule = Color(0xFFC6D4E2),
            accent = Color(0xFF1C5A8A), correct = Color(0xFF1C5A8A), present = Color(0xFFB3282D),
            absent = Color(0xFFCDD8E3), key = Color(0xFFDBE6F1), keyDigraph = Color(0xFFD2E3F2),
            keyAbsent = Color(0xFFCDD8E3),
        ),
    )
    val Schoolbook = HarfPalette(
        "schoolbook", "Schoolbook",
        HarfColors(
            paper = Color(0xFFF7FAF5), paper2 = Color(0xFFEAF0E6), card = Color(0xFFFFFFFF),
            ink = Color(0xFF25291F), muted = Color(0xFF5F6555), rule = Color(0xFFD7DDCA),
            accent = Color(0xFF4E7D3F), correct = Color(0xFF4E7D3F), present = Color(0xFFC06A1F),
            absent = Color(0xFFDADBCC), key = Color(0xFFEAEEE0), keyDigraph = Color(0xFFE6EFDB),
            keyAbsent = Color(0xFFDADBCC),
        ),
    )

    val all: List<HarfPalette> = listOf(Newsprint, Press, Ink, Blueprint, Schoolbook)

    fun byId(id: String): HarfPalette = all.firstOrNull { it.id == id } ?: Newsprint
}

val LocalHarfColors = staticCompositionLocalOf { HarfPalettes.Newsprint.colors }
val LocalHarfMarkStyle = staticCompositionLocalOf { HarfMarkStyle.Scribble }
