package il.cet.bonus.shared

import androidx.compose.ui.window.ComposeUIViewController
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    val initialScreen = NSUserDefaults.standardUserDefaults.stringForKey("uiTestScreen")
    return ComposeUIViewController { SharedApp(initialScreenOverride = initialScreen) }
}
