package com.ihy2ln.weaverse.shared

import androidx.compose.ui.window.ComposeUIViewController
import com.ihy2ln.weaverse.shared.ui.RootScreen
import platform.UIKit.UIViewController

/**
 * Swift calls this as `MainViewControllerKt.MainViewController()` — Kotlin/Native
 * exports a file's top-level declarations as static members of a class named
 * after the file, so the file is deliberately named to match.
 */
fun MainViewController(): UIViewController = ComposeUIViewController {
    RootScreen()
}
