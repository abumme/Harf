package uz.abumme.harfgame

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.runDesktopComposeUiTest
import eu.anifantakis.lib.ksafe.KSafe
import uz.abumme.harfgame.di.initKoin
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders the real App and dumps the Home semantics tree (written to /tmp for inspection).
 *
 * The Game screen is NOT dumped here: navigating to it goes through a NavBackStackEntry whose
 * Lifecycle (and the screen's `viewModel {}`) require the platform main thread, which the desktop
 * UI-test harness does not run composition on — it throws IllegalStateException at
 * LifecycleRegistry. This is a test-harness limitation, not a headless or navigation bug: in the
 * live app navigation works, and the Game screen is covered by its Roborazzi golden + VM tests.
 */
@OptIn(ExperimentalTestApi::class)
class SemanticsDumpTest {

    @Test
    fun home_semantics() = runDesktopComposeUiTest {
        // Force a "decided" style so the one-time prompt dialog doesn't cover Home
        // (KSafe persists across runs; without this the prompt appears once dayCount > 3).
        KSafe().apply {
            putDirect("cellStyles.phase", 2)
            putDirect("cellStyles.chosen", 0)
        }
        initKoin()
        setContent { App() }
        waitForIdle()

        val tree = onRoot().printToString()
        File("/tmp/harf_home_tree.txt").writeText(tree)
        assertTrue(tree.contains("Harf."), "wordmark present")
        assertTrue(tree.contains("Oʻzbekcha"), "language buttons present")
    }
}
