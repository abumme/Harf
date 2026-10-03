package uz.abumme.harfgame

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import harf_game.sharedui.generated.resources.Res
import harf_game.sharedui.generated.resources.archive_not_played
import harf_game.sharedui.generated.resources.result_out_of_tries
import harf_game.sharedui.generated.resources.result_solved
import org.jetbrains.compose.resources.stringResource
import uz.abumme.harfgame.feature.archive.ArchiveDayBrowser
import uz.abumme.harfgame.feature.archive.ArchiveDayRow
import uz.abumme.harfgame.theme.HarfTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** Archive rows: each day shows its date and its own status badge, and a tap opens that day. */
@OptIn(ExperimentalTestApi::class)
class ArchiveDayRowTest {

    private val won = 20_726L
    private val lost = 20_725L
    private val unplayed = 20_724L

    @Test
    fun played_and_unplayed_days_show_their_own_date_and_status() = runDesktopComposeUiTest {
        var opened: Long? = null
        lateinit var solved: String
        lateinit var outOfTries: String
        lateinit var notPlayed: String
        setContent {
            solved = stringResource(Res.string.result_solved, 4, 6)
            outOfTries = stringResource(Res.string.result_out_of_tries)
            notPlayed = stringResource(Res.string.archive_not_played)
            HarfTheme(paletteId = "newsprint") {
                Column(Modifier.width(360.dp)) {
                    ArchiveDayRow(won, ArchiveDayBrowser.DayResult(won = true, attempts = 4), onClick = { opened = won })
                    ArchiveDayRow(lost, ArchiveDayBrowser.DayResult(won = false, attempts = 6), onClick = { opened = lost })
                    ArchiveDayRow(unplayed, null, onClick = { opened = unplayed })
                }
            }
        }
        for (day in listOf(won, lost, unplayed)) onNodeWithText(ArchiveDayBrowser.dateLabel(day)).assertExists()
        // The badges read out the full result; each state has its own.
        onNodeWithContentDescription(solved).assertExists()
        onNodeWithContentDescription(outOfTries).assertExists()
        onNodeWithContentDescription(notPlayed).assertExists()

        onNodeWithText(ArchiveDayBrowser.dateLabel(lost)).performClick()
        assertEquals(lost, opened)
    }
}
