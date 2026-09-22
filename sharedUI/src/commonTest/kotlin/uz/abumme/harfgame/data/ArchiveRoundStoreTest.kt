package uz.abumme.harfgame.data

import eu.anifantakis.lib.ksafe.KSafe
import kotlinx.coroutines.test.runTest
import uz.abumme.harfgame.data.archive.ArchiveRoundStore
import uz.abumme.harfgame.data.stats.InProgressRound
import uz.abumme.harfgame.data.stats.InProgressRow
import uz.abumme.harfgame.data.stats.RoundStore
import uz.abumme.harfgame.data.sync.RoundKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ArchiveRoundStoreTest {

    @Test
    fun two_archived_days_and_today_round_restore_independently_after_relaunch() = runTest {
        val ksafe = KSafe()
        val dailyStore = RoundStore(ksafe)
        val archiveStore = ArchiveRoundStore(ksafe)

        val owner = "user-123"
        val lang = "en"

        // 1. Today's official daily round
        val todayRow = InProgressRow(listOf("t", "o", "d", "a", "y"), listOf(1, 1, 1, 1, 1))
        val todayRound = InProgressRound(
            languageId = lang,
            puzzleDay = 1000L,
            rows = listOf(todayRow),
            current = listOf("n", "e", "x", "t"),
            roundKind = RoundKind.OFFICIAL,
            hardMode = false,
        )
        dailyStore.save(todayRound)

        // 2. Archive day 1 (e.g. 998)
        val arc1Row = InProgressRow(listOf("o", "l", "d", "e", "r"), listOf(2, 2, 2, 2, 2))
        val arc1Round = InProgressRound(
            languageId = lang,
            puzzleDay = 998L,
            rows = listOf(arc1Row),
            current = listOf("a"),
            roundKind = RoundKind.ARCHIVE,
            hardMode = true,
        )
        archiveStore.save(owner, "run-arc-1", arc1Round)

        // 3. Archive day 2 (e.g. 999)
        val arc2Row = InProgressRow(listOf("p", "a", "s", "t", "s"), listOf(0, 0, 0, 0, 0))
        val arc2Round = InProgressRound(
            languageId = lang,
            puzzleDay = 999L,
            rows = listOf(arc2Row),
            current = listOf("b"),
            roundKind = RoundKind.ARCHIVE,
            hardMode = false,
        )
        archiveStore.save(owner, "run-arc-2", arc2Round)

        // Simulate app relaunch with fresh store instances over persisted KSafe
        val relaunchedDaily = RoundStore(ksafe)
        val relaunchedArchive = ArchiveRoundStore(ksafe)

        // Verify independent restoration
        val restoredToday = relaunchedDaily.load(lang, 1000L)
        assertNotNull(restoredToday)
        assertEquals(todayRound, restoredToday)

        val restoredArc1 = relaunchedArchive.load(owner, lang, 998L, "run-arc-1")
        assertNotNull(restoredArc1)
        assertEquals(arc1Round, restoredArc1)

        val restoredArc2 = relaunchedArchive.load(owner, lang, 999L, "run-arc-2")
        assertNotNull(restoredArc2)
        assertEquals(arc2Round, restoredArc2)

        // Verify clearing one does not affect the others
        relaunchedArchive.clear(owner, lang, 998L, "run-arc-1")
        assertNull(relaunchedArchive.load(owner, lang, 998L, "run-arc-1"))
        assertEquals(todayRound, relaunchedDaily.load(lang, 1000L))
        assertEquals(arc2Round, relaunchedArchive.load(owner, lang, 999L, "run-arc-2"))
    }
}
