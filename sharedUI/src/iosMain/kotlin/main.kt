import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.ComposeUIViewController
import uz.abumme.harfgame.App
import uz.abumme.harfgame.di.initKoin
import platform.UIKit.UIApplication
import platform.UIKit.UIStatusBarStyleDarkContent
import platform.UIKit.UIStatusBarStyleLightContent
import platform.UIKit.UIViewController
import platform.UIKit.setStatusBarStyle

fun MainViewController(): UIViewController {
    initKoin()
    return ComposeUIViewController {
        App(onThemeChanged = { ThemeChanged(it) })
    }
}

@Composable
private fun ThemeChanged(isDark: Boolean) {
    LaunchedEffect(isDark) {
        UIApplication.sharedApplication.setStatusBarStyle(
            // dark content = dark icons; a light (paper) background needs dark content
            if (isDark) UIStatusBarStyleLightContent else UIStatusBarStyleDarkContent
        )
    }
}