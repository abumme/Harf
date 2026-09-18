package uz.abumme.harfgame.data

import platform.UIKit.UIApplication
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

/** Resolves the current key [UIWindow], preferring the foreground-active scene. */
internal fun keyWindow(): UIWindow? =
    UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .firstOrNull { it.activationState == UISceneActivationStateForegroundActive }
        ?.windows?.filterIsInstance<UIWindow>()?.firstOrNull { it.isKeyWindow() }
        ?: UIApplication.sharedApplication.windows.filterIsInstance<UIWindow>().firstOrNull { it.isKeyWindow() }
