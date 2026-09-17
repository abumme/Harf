package uz.abumme.harfgame.admin.components.charts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.varabyte.kobweb.compose.dom.GenericTag
import com.varabyte.kobweb.compose.dom.svg.Circle
import com.varabyte.kobweb.compose.dom.svg.Group
import com.varabyte.kobweb.compose.dom.svg.Line
import com.varabyte.kobweb.compose.dom.svg.Path
import com.varabyte.kobweb.compose.dom.svg.Rect
import com.varabyte.kobweb.compose.dom.svg.Svg
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.silk.style.toModifier
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.ElementScope
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.svg.SVGElement
import uz.abumme.harfgame.admin.Strings
import uz.abumme.harfgame.admin.components.ActionButton
import uz.abumme.harfgame.admin.components.DataTable
import uz.abumme.harfgame.admin.components.MutedTextStyle
import uz.abumme.harfgame.admin.components.TableColumn
import uz.abumme.harfgame.admin.components.Tokens
import uz.abumme.harfgame.admin.components.css
import com.varabyte.kobweb.compose.dom.svg.Text as SvgText

// The panel's inline-SVG charts: line, bar, stacked bar and histogram. Every chart is an image with an accessible name
// summarizing its data, has text axis labels and a plain `<title>` tooltip per point or bar, and can be shown as a table
// instead, so no number exists only as a graphic. Provisional days are drawn dimmed; unknown values leave gaps.

/** One series of a chart; [color] is a CSS color, usually one of [Tokens.CHART]. */
data class ChartSeries(val name: String, val values: List<Double?>, val color: String)

private const val SVG_NS = "http://www.w3.org/2000/svg"
/** The drawing width of a chart in a half-width panel; a full-width panel passes [WIDE]. */
const val WIDTH = 640.0
const val WIDE = 1000.0

/** Up to this many bars, a single-series chart is a category chart (streak buckets, attempts). */
private const val CATEGORY_LABELS = 8

/** A line chart over [labels] (one point per label), for time series. [width] is the drawing's own width. */
@Composable
fun LineChart(
    title: String,
    labels: List<String>,
    series: List<ChartSeries>,
    dimmed: List<Boolean> = emptyList(),
    format: (Double) -> String,
    height: Double = 220.0,
    width: Double = WIDTH,
) {
    val domain = niceDomain(series.flatMap { it.values })
    val plot = Plot(width, height)
    ChartFrame(title, labels, series, dimmed, format) {
        ChartSvg(title, labels, series, plot, domain, format) {
            series.forEach { line ->
                lineSegments(line.values, plot, domain).forEach { segment ->
                    // Provisional stretches of the line are drawn dimmed, like their points.
                    lineRuns(segment, dimmed).forEach { run ->
                        Path {
                            attr("d", pathData(run.points))
                            if (run.dimmed) attr("opacity", DIMMED_OPACITY.toString())
                            style { property("fill", "none"); property("stroke", line.color); property("stroke-width", "2") }
                        }
                    }
                    segment.forEach { point ->
                        Group(attrs = { if (dimmed.getOrElse(point.index) { false }) attr("opacity", DIMMED_OPACITY.toString()) }) {
                            Circle {
                                attr("cx", point.x.toString())
                                attr("cy", point.y.toString())
                                attr("r", if (segment.size == 1) "3.5" else "2.5")
                                style { property("fill", line.color) }
                            }
                            SvgTitle(tooltip(labels.getOrElse(point.index) { "" }, line.name, format(point.value), dimmed.getOrElse(point.index) { false }))
                        }
                    }
                }
            }
        }
    }
}

/** A bar chart: one bar per label. [histogram] draws touching bins (e.g. attempts 1–6). */
@Composable
fun BarChart(
    title: String,
    labels: List<String>,
    values: List<Double?>,
    color: String = Tokens.CHART[0],
    dimmed: List<Boolean> = emptyList(),
    format: (Double) -> String,
    height: Double = 200.0,
    histogram: Boolean = false,
    seriesName: String = title,
    width: Double = WIDTH,
) {
    val series = listOf(ChartSeries(seriesName, values, color))
    val domain = niceDomain(values)
    val plot = Plot(width, height)
    ChartFrame(title, labels, series, dimmed, format) {
        ChartSvg(title, labels, series, plot, domain, format, slots = true) {
            bars(values, plot, domain, dimmed, gapRatio = if (histogram) 0.02 else 0.3).forEach { bar ->
                BarShape(bar, color, tooltip(labels.getOrElse(bar.index) { "" }, seriesName, format(bar.value), bar.dimmed))
            }
        }
    }
}

/** A histogram: [BarChart] with touching bins. */
@Composable
fun Histogram(title: String, labels: List<String>, values: List<Double?>, color: String = Tokens.CHART[0], format: (Double) -> String, seriesName: String = title) =
    BarChart(title, labels, values, color, emptyList(), format, histogram = true, seriesName = seriesName)

/** Stacked bars: per label, one segment per series, bottom to top. */
@Composable
fun StackedBarChart(
    title: String,
    labels: List<String>,
    series: List<ChartSeries>,
    dimmed: List<Boolean> = emptyList(),
    format: (Double) -> String,
    height: Double = 220.0,
    width: Double = WIDTH,
) {
    val domain = niceDomain(stackTotals(series.map { it.values }))
    val plot = Plot(width, height)
    ChartFrame(title, labels, series, dimmed, format) {
        ChartSvg(title, labels, series, plot, domain, format, slots = true) {
            stackedBars(series.map { it.values }, plot, domain, dimmed, gapRatio = 0.3).forEachIndexed { position, rects ->
                val part = series[position]
                rects.forEach { bar -> BarShape(bar, part.color, tooltip(labels.getOrElse(bar.index) { "" }, part.name, format(bar.value), bar.dimmed)) }
            }
        }
    }
}

@Composable
private fun ElementScope<SVGElement>.BarShape(bar: BarRect, color: String, tooltip: String) {
    Group(attrs = { if (bar.dimmed) attr("opacity", DIMMED_OPACITY.toString()) }) {
        Rect {
            attr("x", bar.x.toString())
            attr("y", bar.y.toString())
            attr("width", bar.width.coerceAtLeast(0.5).toString())
            attr("height", bar.height.coerceAtLeast(0.0).toString())
            style { property("fill", color) }
        }
        SvgTitle(tooltip)
    }
}

/** A plain SVG `<title>`: the browser's tooltip for its parent group. */
@Composable
private fun ElementScope<SVGElement>.SvgTitle(text: String) {
    GenericTag<SVGElement>("title", SVG_NS) { Text(text) }
}

private fun tooltip(label: String, series: String, value: String, dimmed: Boolean) =
    "$label · $series: $value" + if (dimmed) " (${Strings.Analytics.PROVISIONAL_SHORT})" else ""

/** The frame, value axis, grid and label axis of a chart; [marks] draws the data. [slots] centres labels under bars. */
@Composable
private fun ChartSvg(
    title: String,
    labels: List<String>,
    series: List<ChartSeries>,
    plot: Plot,
    domain: Domain,
    format: (Double) -> String,
    slots: Boolean = false,
    marks: @Composable ElementScope<SVGElement>.() -> Unit,
) {
    Svg(attrs = {
        attr("viewBox", "0 0 ${plot.width.toInt()} ${plot.height.toInt()}")
        attr("role", "img")
        attr("aria-label", chartSummary(title, labels, series, format))
        attr("preserveAspectRatio", "xMidYMid meet")
        style { property("width", "100%"); property("height", "auto"); property("display", "block"); property("overflow", "visible") }
    }) {
        domain.ticks.forEach { tick ->
            val y = plot.y(tick, domain)
            Line {
                attr("x1", plot.left.toString()); attr("x2", (plot.width - plot.right).toString())
                attr("y1", y.toString()); attr("y2", y.toString())
                style { property("stroke", Tokens.LINE); property("stroke-width", "1") }
            }
            SvgText(format(tick)) {
                attr("x", (plot.left - 6).toString()); attr("y", (y + 4).toString())
                attr("text-anchor", "end")
                style { property("fill", Tokens.MUTED); property("font-size", "12px") }
            }
        }
        labelIndices(labels.size).forEach { index ->
            val x = if (slots) plot.slotCenter(index, labels.size) else plot.pointX(index, labels.size)
            SvgText(labels[index]) {
                attr("x", x.toString()); attr("y", (plot.baseline + 18).toString())
                attr("text-anchor", if (!slots && labels.size > 1 && index == 0) "start" else if (!slots && labels.size > 1 && index == labels.lastIndex) "end" else "middle")
                style { property("fill", Tokens.MUTED); property("font-size", "12px") }
            }
        }
        marks()
    }
}

/**
 * The accessible name of a chart: what it shows, over which labels, and each series' latest known value; a short
 * category chart (one series, a few bars) names every value.
 */
fun chartSummary(title: String, labels: List<String>, series: List<ChartSeries>, format: (Double) -> String): String {
    if (series.size == 1 && labels.size in 1..CATEGORY_LABELS) {
        val values = labels.indices.joinToString("; ") { i -> "${labels[i]}: ${series[0].values.getOrNull(i)?.let(format) ?: Strings.Common.NOT_SET}" }
        return "$title. ${series[0].name}: $values"
    }
    val span = when {
        labels.isEmpty() -> Strings.Analytics.NO_DATA
        labels.size == 1 -> labels.first()
        else -> "${labels.first()} – ${labels.last()}"
    }
    val latest = series.joinToString("; ") { s ->
        val last = s.values.indexOfLast { it != null }
        if (last < 0) "${s.name}: ${Strings.Common.NOT_SET}" else "${s.name}: ${format(s.values[last]!!)} (${labels.getOrElse(last) { "" }})"
    }
    return "$title. $span. $latest"
}

/** Title, the chart or its table, the legend and the provisional note. */
@Composable
private fun ChartFrame(
    title: String,
    labels: List<String>,
    series: List<ChartSeries>,
    dimmed: List<Boolean>,
    format: (Double) -> String,
    chart: @Composable () -> Unit,
) {
    var asTable by remember { mutableStateOf(false) }
    Div(Modifier.css("display" to "flex", "flex-direction" to "column", "gap" to "10px", "min-width" to "0").toAttrs()) {
        Div(Modifier.css("display" to "flex", "justify-content" to "space-between", "align-items" to "center", "gap" to "10px", "flex-wrap" to "wrap").toAttrs()) {
            H3(Modifier.css("font-size" to "15px", "font-weight" to "650", "margin" to "0").toAttrs()) { Text(title) }
            ActionButton(
                if (asTable) Strings.Analytics.SHOW_CHART else Strings.Analytics.SHOW_TABLE,
                onClick = { asTable = !asTable },
                modifier = Modifier.css("height" to "30px", "padding" to "0 10px", "font-size" to "13px"),
            )
        }
        if (asTable) {
            DataTable(
                caption = title,
                columns = listOf(TableColumn<Int>(Strings.Analytics.COLUMN_LABEL) { index ->
                    Text(labels.getOrElse(index) { "" } + if (dimmed.getOrElse(index) { false }) " · ${Strings.Analytics.PROVISIONAL_SHORT}" else "")
                }) + series.map { s -> TableColumn<Int>(s.name, numeric = true) { index -> Text(s.values.getOrNull(index)?.let(format) ?: Strings.Common.NOT_SET) } },
                rows = labels.indices.toList(),
                emptyText = Strings.Analytics.NO_DATA,
            )
        } else if (labels.isEmpty()) {
            P(MutedTextStyle.toModifier().toAttrs()) { Text(Strings.Analytics.NO_DATA) }
        } else {
            chart()
            if (series.size > 1) {
                Div(Modifier.css("display" to "flex", "gap" to "14px", "flex-wrap" to "wrap", "font-size" to "13px").toAttrs()) {
                    series.forEach { s ->
                        Span(Modifier.css("display" to "inline-flex", "align-items" to "center", "gap" to "6px").toAttrs()) {
                            Span(Modifier.css("width" to "12px", "height" to "12px", "border-radius" to "2px", "background-color" to s.color).toAttrs { attr("aria-hidden", "true") })
                            Text(s.name)
                        }
                    }
                }
            }
            if (dimmed.any { it }) {
                P(MutedTextStyle.toModifier().css("font-size" to "12px").toAttrs()) { Text(Strings.Analytics.PROVISIONAL_NOTE) }
            }
        }
    }
}
