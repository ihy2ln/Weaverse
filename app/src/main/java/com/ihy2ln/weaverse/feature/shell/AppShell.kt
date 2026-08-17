package com.ihy2ln.weaverse.feature.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.feature.novel.NovelShellContent
import com.ihy2ln.weaverse.feature.roleplay.RoleplayShellContent

/**
 * Root of the app: adaptive breakpoint detection + the mode-switch
 * crossfade between Novel and Roleplay, each with its own permanently-
 * composed Scaffold/NavHost (spec §5).
 */
@Composable
fun AppShell(viewModel: AppShellViewModel = hiltViewModel()) {
    val windowSizeClass = rememberAppWindowSizeClass()
    val mode by viewModel.currentMode.collectAsState()

    ModeCrossfadeHost(
        mode = mode,
        novelContent = {
            NovelShellContent(
                windowSizeClass = windowSizeClass,
                mode = mode,
                onModeChange = viewModel::setMode,
            )
        },
        roleplayContent = {
            RoleplayShellContent(
                windowSizeClass = windowSizeClass,
                mode = mode,
                onModeChange = viewModel::setMode,
            )
        },
    )
}
