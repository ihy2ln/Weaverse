package com.ihy2ln.weaverse.feature.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/** Compact/Medium/Expanded breakpoints per Material Design guidance (spec §5). */
enum class AppWindowSizeClass { Compact, Medium, Expanded }

@Composable
fun rememberAppWindowSizeClass(): AppWindowSizeClass {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return when {
        widthDp < 600 -> AppWindowSizeClass.Compact
        widthDp < 840 -> AppWindowSizeClass.Medium
        else -> AppWindowSizeClass.Expanded
    }
}
