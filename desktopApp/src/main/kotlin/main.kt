import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension
import uz.abumme.harfgame.App
import uz.abumme.harfgame.di.initKoin

fun main() {
    initKoin()
    application {
    Window(
        title = "Harf Game",
        state = rememberWindowState(width = 800.dp, height = 600.dp),
        onCloseRequest = ::exitApplication,
    ) {
        window.minimumSize = Dimension(350, 600)
        App()
    }
    }
}

