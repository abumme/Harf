package uz.abumme.harfgame.admin

import uz.abumme.harfgame.admin.components.charts.ChartSeries
import uz.abumme.harfgame.admin.components.charts.Domain
import uz.abumme.harfgame.admin.components.charts.Plot
import uz.abumme.harfgame.admin.components.charts.bars
import uz.abumme.harfgame.admin.components.charts.chartSummary
import uz.abumme.harfgame.admin.components.charts.labelIndices
import uz.abumme.harfgame.admin.components.charts.lineRuns
import uz.abumme.harfgame.admin.components.charts.lineSegments
import uz.abumme.harfgame.admin.components.charts.niceDomain
import uz.abumme.harfgame.admin.components.charts.niceStep
import uz.abumme.harfgame.admin.components.charts.pathData
import uz.abumme.harfgame.admin.components.charts.stackTotals
import uz.abumme.harfgame.admin.components.charts.stackedBars
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChartMathTest {
    private val plot = Plot(width = 256.0, height = 140.0, left = 40.0, right = 16.0, top = 10.0, bottom = 30.0) // 200 × 100 inside

    private fun close(expected: Double, actual: Double) = assertTrue(abs(expected - actual) < 1e-9, "expected $expected, got $actual")

    @Test
    fun theValueAxisStartsAtZeroAndEndsOnANiceTick() {
        assertEquals(Domain(0.0, 60.0, listOf(0.0, 20.0, 40.0, 60.0)), niceDomain(listOf(12.0, null, 57.0)))
        assertEquals(listOf(0.0, 2.0, 4.0, 6.0), niceDomain(listOf(5.0)).ticks)
        assertEquals(1_000.0, niceDomain(listOf(987.0)).max)
        assertEquals(listOf(1.0, 2.0, 5.0, 10.0, 0.2), listOf(niceStep(0.7), niceStep(1.3), niceStep(4.0), niceStep(7.5), niceStep(0.13)))
        // Counts never get fractional ticks; shares may.
        assertEquals(listOf(0.0, 1.0), niceDomain(listOf(1.0)).ticks)
        assertEquals(listOf(0.0, 0.2, 0.4, 0.6, 0.8), niceDomain(listOf(0.75), integers = false).ticks.map { kotlin.math.round(it * 10) / 10 })
    }

    @Test
    fun anAllZeroOrEmptySeriesStillDrawsAnAxis() {
        assertEquals(Domain(0.0, 1.0, listOf(0.0, 1.0)), niceDomain(listOf(0.0, 0.0, 0.0)))
        assertEquals(Domain(0.0, 1.0, listOf(0.0, 1.0)), niceDomain(listOf(null, null)))
        assertEquals(Domain(0.0, 1.0, listOf(0.0, 1.0)), niceDomain(emptyList()))
        // Zeros sit on the baseline.
        close(plot.baseline, plot.y(0.0, niceDomain(listOf(0.0))))
    }

    @Test
    fun aSinglePointIsCentredAndUnknownValuesSplitTheLine() {
        val domain = niceDomain(listOf(4.0))
        val single = lineSegments(listOf(4.0), plot, domain)
        assertEquals(1, single.size)
        close(140.0, single.single().single().x) // the middle of 40..240
        close(10.0 + (1 - 4.0 / 4.0) * 100, single.single().single().y)

        val segments = lineSegments(listOf(1.0, 2.0, null, 3.0, null, null, 4.0), plot, niceDomain(listOf(4.0)))
        assertEquals(listOf(listOf(0, 1), listOf(3), listOf(6)), segments.map { segment -> segment.map { it.index } })
        close(40.0, segments[0][0].x)
        close(240.0, segments[2][0].x)
        assertEquals("M40 85 L73.3 60", pathData(segments[0]))
    }

    @Test
    fun provisionalStretchesOfALineAreDimmed() {
        val domain = niceDomain(listOf(4.0))
        val segment = lineSegments(listOf(1.0, 2.0, 3.0, 4.0), plot, domain).single()
        val runs = lineRuns(segment, dimmed = listOf(false, false, true, true))
        assertEquals(listOf(listOf(0, 1), listOf(1, 2, 3)), runs.map { run -> run.points.map { it.index } })
        assertEquals(listOf(false, true), runs.map { it.dimmed })
        assertEquals(listOf(false), lineRuns(segment, emptyList()).map { it.dimmed })
        assertEquals(emptyList(), lineRuns(segment.take(1), listOf(true)))
    }

    @Test
    fun barsFillEqualSlotsAndUnknownValuesHaveNoBar() {
        val domain = Domain(0.0, 10.0, listOf(0.0, 5.0, 10.0))
        val rects = bars(listOf(5.0, null, 10.0, 0.0), plot, domain, dimmed = listOf(false, false, true, false), gapRatio = 0.5)
        assertEquals(listOf(0, 2, 3), rects.map { it.index })
        // Slots of 50 around 40..240; half of each slot is the bar.
        close(52.5, rects[0].x)
        close(25.0, rects[0].width)
        close(60.0, rects[0].y)
        close(50.0, rects[0].height)
        close(152.5, rects[1].x)
        close(100.0, rects[1].height)
        close(0.0, rects[2].height)
        assertEquals(listOf(false, true, false), rects.map { it.dimmed })
        // A histogram's bins touch.
        val bins = bars(listOf(1.0, 2.0), plot, domain, gapRatio = 0.0)
        close(bins[0].x + bins[0].width, bins[1].x)
    }

    @Test
    fun stacksAddUpBottomToTopAndSkipUnknownParts() {
        val linked = listOf(2.0, null, 1.0)
        val anonymous = listOf(3.0, null, null)
        assertEquals(listOf(5.0, null, 1.0), stackTotals(listOf(linked, anonymous)))
        val domain = Domain(0.0, 10.0, listOf(0.0, 10.0))
        val stacks = stackedBars(listOf(linked, anonymous), Plot(width = 256.0, height = 140.0, left = 40.0, right = 16.0, top = 10.0, bottom = 30.0), domain, dimmed = listOf(true, false, false), gapRatio = 0.0)
        assertEquals(listOf(0, 2), stacks[0].map { it.index })
        assertEquals(listOf(0), stacks[1].map { it.index })
        val bottom = stacks[0][0]
        val top = stacks[1][0]
        close(20.0, bottom.height)
        close(30.0, top.height)
        close(bottom.y, top.y + top.height) // the second part starts where the first ends
        assertTrue(bottom.dimmed && top.dimmed)
        close(10.0, stacks[0][1].height)
    }

    @Test
    fun axisLabelsAreThinnedButKeepTheEnds() {
        assertEquals(listOf(0, 1, 2), labelIndices(3))
        val thirty = labelIndices(30)
        assertTrue(thirty.size <= 7)
        assertEquals(0, thirty.first())
        assertEquals(29, thirty.last())
        assertEquals(emptyList(), labelIndices(0))
        assertEquals(365, labelIndices(366).last())
    }

    @Test
    fun theAccessibleNameSummarizesEachSeries() {
        val summary = chartSummary(
            "Игроки",
            listOf("15.09", "16.09"),
            listOf(ChartSeries("Английский", listOf(3.0, null), "red"), ChartSeries("Русский", listOf(null, null), "blue")),
        ) { it.toInt().toString() }
        assertEquals("Игроки. 15.09 – 16.09. Английский: 3 (15.09); Русский: —", summary)
        // A category chart names every bar.
        assertEquals(
            "Серии. Аккаунтов: 1: 4; 2–6: 2; 7–29: —; 30+: 0",
            chartSummary("Серии", listOf("1", "2–6", "7–29", "30+"), listOf(ChartSeries("Аккаунтов", listOf(4.0, 2.0, null, 0.0), "red"))) { it.toInt().toString() },
        )
    }
}
