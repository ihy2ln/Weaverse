package com.ihy2ln.weaverse.feature.shell

import androidx.compose.ui.graphics.vector.ImageVector

/** One entry in a mode's primary destination nav (bottom bar on compact, rail otherwise). */
data class NavDestinationSpec<T : Any>(
    val route: T,
    val label: String,
    val icon: ImageVector,
)
