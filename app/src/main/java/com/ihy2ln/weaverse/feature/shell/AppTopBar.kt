package com.ihy2ln.weaverse.feature.shell

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.ihy2ln.weaverse.core.util.AppMode

/**
 * Shared top bar for both modes (spec §5): a rail-toggle icon (compact/
 * medium only — expanded shows the rail permanently), title + subtitle,
 * a global-search icon (spec §4/§9), and the mode-switch pill pinned to
 * the trailing edge. The title auto-scrolls (marquee) instead of wrapping
 * or clipping when a book's title is too long to fit next to the mode
 * pill — real-device feedback showed long titles overlapping the rail icon.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    subtitle: String?,
    mode: AppMode,
    onModeChange: (AppMode) -> Unit,
    showRailToggle: Boolean,
    onRailToggle: () -> Unit,
    onSearchClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
) {
    TopAppBar(
        navigationIcon = {
            if (showRailToggle) {
                IconButton(onClick = onRailToggle) {
                    Icon(imageVector = Icons.Filled.Menu, contentDescription = "Open rail")
                }
            }
        },
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().basicMarquee(),
                )
                if (subtitle != null) {
                    Text(text = subtitle, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        actions = {
            IconButton(onClick = onSearchClick) {
                Icon(imageVector = Icons.Filled.Search, contentDescription = "Search")
            }
            IconButton(onClick = onSettingsClick) {
                Icon(imageVector = Icons.Filled.Settings, contentDescription = "Settings")
            }
            ModeSwitch(mode = mode, onModeChange = onModeChange)
        },
        colors = TopAppBarDefaults.topAppBarColors(),
    )
}
