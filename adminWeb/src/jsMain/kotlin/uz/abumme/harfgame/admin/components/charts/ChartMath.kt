package uz.abumme.harfgame.admin.components.charts

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// The geometry of the panel's inline-SVG charts, kept apart from the composables so it can be tested. Values are
// counts or shares, never negative; a null value is unknown and leaves a gap, never a zero.

/** The value axis: from [min] to [max], labelled at [ticks]. */
data class Domain(val min: Double, val max: Double, val ticks: List<Double>) {
    val span: Double get() = max - min
}

/**
 * A domain from 0 to a "nice" maximum (1, 2 or 5 times a power of ten per step) covering every known value, with about
 * [tickCount] steps; [integers] keeps count axes on whole steps. With no known value, or only zeros, it is 0..1 so an
 * empty chart still draws its axis.
 */
fun niceDomain(values: Iterable<Double?>, tickCount: Int = 4, integers: Boolean = true): Domain {
    val top = values.filterNotNull().filter { !it.isNaN() }.maxOrNull() ?: 0.0
    if (top <= 0.0) return Domain(0.0, 1.0, listOf(0.0, 1.0))
    val step = niceStep(top / tickCount).let { if (integers) max(it, 1.0) else it }
    val max = ceil(top / step) * step
    val ticks = (0..(max / step).toInt()).map { it * step }
    return Domain(0.0, max, ticks)
}

/** The smallest step of 1, 2 or 5 × 10^n that is at least [rough]. */
fun niceStep(rough: Double): Double {
    if (rough <= 0.0) return 1.0
    val magnitude = 10.0.pow(floor(log10(rough)))
    val normalized = rough / magnitude
    val nice = when {
        normalized <= 1.0 -> 1.0
        normalized <= 2.0 -> 2.0
        normalized <= 5.0 -> 5.0
        else -> 10.0
    }
    return nice * magnitude
}

/** The drawing area: [width] × [height] with margins for the axis labels. */
data class Plot(
    val width: Double,
    val height: Double,
    val left: Double = 44.0,
    val right: Double = 12.0,
    val top: Double = 12.0,
    val bottom: Double = 28.0,
) {
    val innerWidth: Double get() = width - left - right
    val innerHeight: Double get() = height - top - bottom
    val baseline: Double get() = top + innerHeight

    /** The vertical position of [value] on [domain]. */
    fun y(value: Double, domain: Domain): Double =
        baseline - (if (domain.span == 0.0) 0.0 else (value - domain.min) / domain.span * innerHeight)

    /** The centre of slot [index] of [count] equal slots (bars), or the position of point [index] of [count] (lines). */
    fun slotCenter(index: Int, count: Int): Double = left + (index + 0.5) * innerWidth / max(count, 1)

    fun pointX(index: Int, count: Int): Double =
        if (count <= 1) left + innerWidth / 2 else left + index * innerWidth / (count - 1)
}

data class Point(val index: Int, val x: Double, val y: Double, val value: Double)

/** A line split into runs of known values: an unknown value ends a run, so the chart shows a gap there. */
fun lineSegments(values: List<Double?>, plot: Plot, domain: Domain): List<List<Point>> {
    val segments = ArrayList<List<Point>>()
    var current = ArrayList<Point>()
    values.forEachIndexed { index, value ->
        if (value == null || value.isNaN()) {
            if (current.isNotEmpty()) segments += current
            current = ArrayList()
        } else {
            current += Point(index, plot.pointX(index, values.size), plot.y(value, domain), value)
        }
    }
    if (current.isNotEmpty()) segments += current
    return segments
}

/** A stretch of a line segment drawn at one opacity. */
data class LineRun(val points: List<Point>, val dimmed: Boolean)

/**
 * Splits a segment into runs by [dimmed]: the piece between two points is dimmed when its later point is (the day it
 * leads to is provisional). A lone point draws no line, so it has no run.
 */
fun lineRuns(segment: List<Point>, dimmed: List<Boolean>): List<LineRun> {
    if (segment.size < 2) return emptyList()
    val runs = ArrayList<LineRun>()
    var current = arrayListOf(segment[0])
    var currentDimmed = dimmed.getOrElse(segment[1].index) { false }
    for (i in 1 until segment.size) {
        val pieceDimmed = dimmed.getOrElse(segment[i].index) { false }
        if (pieceDimmed != currentDimmed) {
            runs += LineRun(current, currentDimmed)
            current = arrayListOf(segment[i - 1])
            currentDimmed = pieceDimmed
        }
        current += segment[i]
    }
    runs += LineRun(current, currentDimmed)
    return runs
}

/** SVG path data through [points]. */
fun pathData(points: List<Point>): String =
    points.mapIndexed { i, p -> (if (i == 0) "M" else "L") + format(p.x) + " " + format(p.y) }.joinToString(" ")

data class BarRect(val index: Int, val x: Double, val y: Double, val width: Double, val height: Double, val value: Double, val dimmed: Boolean)

/**
 * One bar per value in equal slots; [gapRatio] of each slot is left empty between bars (0 for a histogram's touching
 * bins). An unknown value has no bar. [dimmed] marks provisional slots.
 */
fun bars(values: List<Double?>, plot: Plot, domain: Domain, dimmed: List<Boolean> = emptyList(), gapRatio: Double = 0.25): List<BarRect> {
    val slot = plot.innerWidth / max(values.size, 1)
    val width = slot * (1 - gapRatio.coerceIn(0.0, 0.9))
    return values.mapIndexedNotNull { index, value ->
        if (value == null || value.isNaN()) return@mapIndexedNotNull null
        val top = plot.y(value, domain)
        BarRect(index, plot.left + index * slot + (slot - width) / 2, top, width, plot.baseline - top, value, dimmed.getOrElse(index) { false })
    }
}

/** Each slot's total over the stacked [series]; null when no series knows the slot. */
fun stackTotals(series: List<List<Double?>>): List<Double?> {
    val count = series.maxOfOrNull { it.size } ?: 0
    return (0 until count).map { index ->
        val known = series.mapNotNull { it.getOrNull(index) }
        if (known.isEmpty()) null else known.sum()
    }
}

/**
 * Stacked bars: for each slot, one rectangle per series with a known value, bottom to top in series order. A slot whose
 * values are all unknown has none.
 */
fun stackedBars(series: List<List<Double?>>, plot: Plot, domain: Domain, dimmed: List<Boolean> = emptyList(), gapRatio: Double = 0.25): List<List<BarRect>> {
    val count = series.maxOfOrNull { it.size } ?: 0
    val slot = plot.innerWidth / max(count, 1)
    val width = slot * (1 - gapRatio.coerceIn(0.0, 0.9))
    return series.mapIndexed { position, values ->
        val rects = ArrayList<BarRect>()
        values.forEachIndexed { index, value ->
            if (value == null || value.isNaN()) return@forEachIndexed
            val below = series.take(position).sumOf { it.getOrNull(index) ?: 0.0 }
            val bottom = plot.y(below, domain)
            val top = plot.y(below + value, domain)
            rects += BarRect(index, plot.left + index * slot + (slot - width) / 2, top, width, bottom - top, value, dimmed.getOrElse(index) { false })
        }
        rects
    }
}

/** Which of [count] labels to print under the axis: at most [maxLabels], always the first and the last. */
fun labelIndices(count: Int, maxLabels: Int = 6): List<Int> {
    if (count <= 0) return emptyList()
    if (count <= maxLabels) return (0 until count).toList()
    val step = ceil((count - 1).toDouble() / (maxLabels - 1)).toInt()
    val indices = (0 until count step step).toMutableList()
    if (indices.last() != count - 1) {
        if (count - 1 - indices.last() < step / 2 && indices.size > 1) indices.removeAt(indices.lastIndex)
        indices += count - 1
    }
    return indices
}

/** The opacity of a provisional (still settling) day's mark. */
const val DIMMED_OPACITY = 0.45

private fun format(value: Double): String = (kotlin.math.round(value * 10) / 10).toString()

internal fun clamp(value: Double, low: Double, high: Double) = min(max(value, low), high)
