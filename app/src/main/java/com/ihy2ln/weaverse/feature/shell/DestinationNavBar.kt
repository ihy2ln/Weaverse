package com.ihy2ln.weaverse.feature.shell

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * Bottom bar for [AppWindowSizeClass.Compact] — one entry per [destinations].
 * [currentRoute] is tracked by the caller (not read back from NavController's
 * back stack). Selection compares by runtime class rather than `==` —
 * [NovelDestination.Write] carries an optional `sceneId` argument, so
 * `Write(null)` and `Write("some-scene-id")` both need to highlight the same
 * tab (every other route is still a parameterless `data object`, where
 * class-equality and value-equality agree anyway).
 */
@Composable
fun <T : Any> PrimaryDestinationBar(
    destinations: List<NavDestinationSpec<T>>,
    currentRoute: T?,
    onNavigate: (T) -> Unit,
) {
    NavigationBar {
        destinations.forEach { spec ->
            NavigationBarItem(
                selected = currentRoute != null && spec.route::class == currentRoute::class,
                onClick = { onNavigate(spec.route) },
                icon = { Icon(imageVector = spec.icon, contentDescription = spec.label) },
                label = { Text(spec.label) },
            )
        }
    }
}

/** Side rail for [AppWindowSizeClass.Medium] and [AppWindowSizeClass.Expanded]. */
@Composable
fun <T : Any> PrimaryDestinationRail(
    destinations: List<NavDestinationSpec<T>>,
    currentRoute: T?,
    onNavigate: (T) -> Unit,
) {
    NavigationRail {
        destinations.forEach { spec ->
            NavigationRailItem(
                selected = currentRoute != null && spec.route::class == currentRoute::class,
                onClick = { onNavigate(spec.route) },
                icon = { Icon(imageVector = spec.icon, contentDescription = spec.label) },
                label = { Text(spec.label) },
            )
        }
    }
}
