import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import uz.abumme.harfgame.App
import uz.abumme.harfgame.di.initKoin

/**
 * Compose Hot Reload dev entry. CHR hosts this composable in a window and reloads it on
 * source changes. Koin is initialized once before the first composition.
 *
 * Run: ./gradlew :desktopApp:hotDevAsync --className=DevMainKt --funName=DevApp --auto
 */
@Composable
fun DevApp() {
    remember { initKoin(); 0 }
    App()
}
