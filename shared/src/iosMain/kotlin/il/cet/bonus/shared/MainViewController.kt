package il.cet.bonus.shared

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * Called from Swift (iosApp) to obtain the root UIViewController hosting the shared
 * Compose Multiplatform UI. See `iosApp/iosApp/ContentView.swift`.
 */
fun MainViewController(): UIViewController = ComposeUIViewController { SharedApp() }
