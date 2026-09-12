import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIStatusBarStyle
import platform.UIKit.UIStatusBarStyleDarkContent
import platform.UIKit.UIStatusBarStyleLightContent
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.UIKit.UIViewController
import platform.UIKit.addChildViewController
import platform.UIKit.didMoveToParentViewController
import uz.abumme.harfgame.App
import uz.abumme.harfgame.di.initKoin

@OptIn(ExperimentalForeignApi::class)
private class MainAppViewController : UIViewController(nibName = null, bundle = null) {
    private var isDark = false

    override fun preferredStatusBarStyle(): UIStatusBarStyle {
        return if (isDark) UIStatusBarStyleLightContent else UIStatusBarStyleDarkContent
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        val composeVc = ComposeUIViewController {
            App(onThemeChanged = { dark ->
                if (isDark != dark) {
                    isDark = dark
                    setNeedsStatusBarAppearanceUpdate()
                }
            })
        }
        addChildViewController(composeVc)
        view.addSubview(composeVc.view)
        composeVc.view.setFrame(view.bounds)
        composeVc.view.autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
        composeVc.didMoveToParentViewController(this)
    }
}

fun MainViewController(): UIViewController {
    initKoin()
    return MainAppViewController()
}