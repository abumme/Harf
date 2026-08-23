package uz.abumme.harfgame.feature

import eu.anifantakis.lib.ksafe.KSafe
import kotlinx.coroutines.test.runTest
import uz.abumme.harfgame.feature.cellstyles.ExperimentPhase
import uz.abumme.harfgame.feature.cellstyles.StyleChoiceLog
import uz.abumme.harfgame.feature.cellstyles.StyleChoiceSource
import uz.abumme.harfgame.feature.cellstyles.StyleExperimentController
import uz.abumme.harfgame.theme.marks.HarfMarkStyleId
import kotlin.test.Test
import kotlin.test.assertEquals

class CellStylesTest {

    // reset the controller's persisted keys so the shared KSafe store can't leak between runs
    private fun freshKsafe(): KSafe {
        val k = KSafe()
        k.putDirect("cellStyles.dayCount", 0)
        k.putDirect("cellStyles.lastDayCounted", Long.MIN_VALUE)
        k.putDirect("cellStyles.phase", 0)
        k.putDirect("cellStyles.chosen", 0)
        return k
    }

    @Test
    fun style_rotates_one_per_day_and_prompts_after_three() {
        val ksafe = freshKsafe()
        var day = 100L
        val ctl = StyleExperimentController(ksafe, StyleChoiceLog(ksafe), today = { day })

        ctl.onAppOpen(); assertEquals(HarfMarkStyleId.Scribble, ctl.activeStyle.value)
        ctl.onAppOpen(); assertEquals(HarfMarkStyleId.Scribble, ctl.activeStyle.value) // same day, no advance
        day = 101; ctl.onAppOpen(); assertEquals(HarfMarkStyleId.Fill, ctl.activeStyle.value)
        day = 102; ctl.onAppOpen(); assertEquals(HarfMarkStyleId.Outline, ctl.activeStyle.value)
        day = 103; ctl.onAppOpen(); assertEquals(ExperimentPhase.PromptPending, ctl.phase.value)
    }

    @Test
    fun resumes_after_restart() {
        val ksafe = freshKsafe()
        var day = 100L
        val ctl = StyleExperimentController(ksafe, StyleChoiceLog(ksafe), today = { day })
        day = 101; ctl.onAppOpen() // dayCount now 2 (fresh->1 at 100 was never opened; opened once here)

        val restarted = StyleExperimentController(ksafe, StyleChoiceLog(ksafe), today = { day })
        restarted.onAppOpen() // same day 101 -> no advance
        assertEquals(ctl.activeStyle.value, restarted.activeStyle.value)
    }

    @Test
    fun choosing_locks_style_and_records_choice() = runTest {
        val ksafe = freshKsafe()
        val log = StyleChoiceLog(ksafe)
        val ctl = StyleExperimentController(ksafe, log, today = { 200L })
        ctl.onAppOpen()

        ctl.choose(HarfMarkStyleId.Outline)
        assertEquals(HarfMarkStyleId.Outline, ctl.activeStyle.value)
        assertEquals(ExperimentPhase.Decided, ctl.phase.value)

        val last = log.all().last()
        assertEquals("Outline", last.styleId)
        assertEquals(StyleChoiceSource.Experiment.name, last.source)
    }

    @Test
    fun settings_override_is_sticky_and_recorded() = runTest {
        val ksafe = freshKsafe()
        val log = StyleChoiceLog(ksafe)
        val ctl = StyleExperimentController(ksafe, log, today = { 300L })
        ctl.onAppOpen()

        ctl.setFromSettings(HarfMarkStyleId.Fill)
        assertEquals(HarfMarkStyleId.Fill, ctl.activeStyle.value)
        assertEquals(ExperimentPhase.Decided, ctl.phase.value)
        assertEquals(StyleChoiceSource.Settings.name, log.all().last().source)
    }
}
