package uz.abumme.harfgame

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.runDesktopComposeUiTest
import uz.abumme.harfgame.di.initKoin
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders the real App and dumps the Home semantics tree (written to /tmp for inspection).
 * Navigating deeper needs a full Lifecycle host that the plain desktop UI test does not provide,
 * so the Game screen is covered by its Roborazzi golden and the GameViewModel tests instead.
 */
@OptIn(ExperimentalTestApi::class)
class SemanticsDumpTest {

    @Test
    fun home_semantics() = runDesktopComposeUiTest {
        initKoin()
        setContent { App() }
        waitForIdle()

        val tree = onRoot().printToString()
        File("/tmp/harf_home_tree.txt").writeText(tree)

        assertTrue(tree.contains("Harf."), "wordmark present")
        assertTrue(tree.contains("Oʻzbekcha"), "language buttons present")
    }
}
