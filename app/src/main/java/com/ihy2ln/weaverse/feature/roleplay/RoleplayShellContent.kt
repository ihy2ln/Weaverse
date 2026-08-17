package com.ihy2ln.weaverse.feature.roleplay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.ihy2ln.weaverse.core.ui.InkModalBottomSheet
import com.ihy2ln.weaverse.core.ui.Spacing
import com.ihy2ln.weaverse.core.util.AppMode
import com.ihy2ln.weaverse.data.settings.AppSettingsRepository
import com.ihy2ln.weaverse.feature.roleplay.characters.CharactersScreen
import com.ihy2ln.weaverse.feature.roleplay.chats.RpChatsScreen
import com.ihy2ln.weaverse.feature.roleplay.codex.RoleplayCodexScreen
import com.ihy2ln.weaverse.feature.roleplay.personas.PersonasScreen
import com.ihy2ln.weaverse.feature.roleplay.presets.PresetsScreen
import com.ihy2ln.weaverse.feature.search.GlobalSearchScreen
import com.ihy2ln.weaverse.feature.settings.SettingsScreen
import com.ihy2ln.weaverse.feature.shell.AppHeaderBar
import com.ihy2ln.weaverse.feature.shell.AppHeaderHeight
import com.ihy2ln.weaverse.feature.shell.AppTopBar
import com.ihy2ln.weaverse.feature.shell.AppWindowSizeClass
import com.ihy2ln.weaverse.feature.shell.DestinationHistory
import com.ihy2ln.weaverse.feature.shell.ModeSwitch
import com.ihy2ln.weaverse.feature.shell.NavDestinationSpec
import com.ihy2ln.weaverse.feature.shell.PrimaryDestinationBar
import com.ihy2ln.weaverse.feature.shell.RoleplayDestination
import com.ihy2ln.weaverse.feature.shell.RoleplayRailPanel
import com.ihy2ln.weaverse.feature.shell.SegmentedDestinationBar

private val destinations: List<NavDestinationSpec<RoleplayDestination>> = listOf(
    NavDestinationSpec<RoleplayDestination>(RoleplayDestination.Chats(), "Chats", Icons.Filled.Forum),
    NavDestinationSpec<RoleplayDestination>(RoleplayDestination.Characters, "Characters", Icons.Filled.Groups),
    NavDestinationSpec<RoleplayDestination>(RoleplayDestination.Personas, "Personas", Icons.Filled.Person),
    NavDestinationSpec<RoleplayDestination>(RoleplayDestination.Codex, "Codex", Icons.Filled.MenuBook),
    NavDestinationSpec<RoleplayDestination>(RoleplayDestination.Presets, "Presets", Icons.Filled.Tune),
)

/**
 * Roleplay mode's own Scaffold + NavHost. Revision 02 §1.4 completes what
 * rev02-01/03 deferred: a real Sessions/Codex/Snippets/Chats [RoleplayRailPanel]
 * (this mode had none before) plus the same rail-width [AppHeaderBar]
 * treatment [com.ihy2ln.weaverse.feature.novel.NovelShellContent] has —
 * real back/forward via [DestinationHistory], drag-to-resize/collapse
 * (sharing [com.ihy2ln.weaverse.feature.novel.NovelShellViewModel]'s rail-
 * width/collapsed settings keys, so it's one preference across both modes,
 * not two independent ones). Compact keeps the pre-Revision-02 [AppTopBar]
 * + bottom [PrimaryDestinationBar], same scope decision as Novel mode.
 *
 * Scope cut: the header's title has no "which session is open" concept to
 * show yet (unlike Novel's book title) — showing "Character Chats"
 * generically rather than the removed hardcoded "Mara Voss" placeholder
 * (deliberately not the literal string "Roleplay": that collides with
 * `ModeSwitch`'s own "Roleplay" pill label, which would make any
 * `onNodeWithText("Roleplay")` UI-test lookup match two nodes at once). A
 * real title needs a shell-level "current session" state this pass didn't build.
 */
@Composable
fun RoleplayShellContent(
    windowSizeClass: AppWindowSizeClass,
    mode: AppMode,
    onModeChange: (AppMode) -> Unit,
    shellViewModel: RoleplayShellViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val history = remember { DestinationHistory<RoleplayDestination>(RoleplayDestination.Chats()) }
    var railSheetOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    val persistedRailWidth by shellViewModel.railWidthDp.collectAsState()
    val railCollapsed by shellViewModel.railCollapsed.collectAsState()
    var liveRailWidth by remember { mutableIntStateOf(persistedRailWidth) }
    LaunchedEffect(persistedRailWidth) { liveRailWidth = persistedRailWidth }

    fun goTo(route: RoleplayDestination) {
        history.navigate(route)
        navController.navigate(route) { launchSingleTop = true }
    }
    fun goBack() {
        history.back()?.let { navController.navigate(it) { launchSingleTop = true } }
    }
    fun goForward() {
        history.forward()?.let { navController.navigate(it) { launchSingleTop = true } }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (windowSizeClass == AppWindowSizeClass.Compact) {
            Scaffold(
                topBar = {
                    AppTopBar(
                        title = "Character Chats",
                        subtitle = null,
                        mode = mode,
                        onModeChange = onModeChange,
                        showRailToggle = true,
                        onRailToggle = { railSheetOpen = true },
                        onSearchClick = { searchOpen = true },
                        onSettingsClick = { settingsOpen = true },
                    )
                },
                bottomBar = { PrimaryDestinationBar(destinations, history.current, ::goTo) },
            ) { innerPadding ->
                RoleplayNavHost(navController, modifier = Modifier.padding(innerPadding).fillMaxSize())
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    AppHeaderBar(
                        title = "Character Chats",
                        seriesName = null,
                        canGoBack = history.canGoBack,
                        canGoForward = history.canGoForward,
                        onBack = ::goBack,
                        onForward = ::goForward,
                        onSettingsClick = { settingsOpen = true },
                        showCollapseAndResize = true,
                        railCollapsed = railCollapsed,
                        onToggleCollapse = { shellViewModel.setRailCollapsed(!railCollapsed) },
                        onRailResize = { delta ->
                            liveRailWidth = (liveRailWidth + delta.value.toInt())
                                .coerceIn(AppSettingsRepository.RailWidthMin, AppSettingsRepository.RailWidthMax)
                            shellViewModel.setRailWidthDp(liveRailWidth)
                        },
                        modifier = Modifier.width(liveRailWidth.dp),
                    )
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(AppHeaderHeight)
                            .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                            .padding(horizontal = Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        SegmentedDestinationBar(
                            destinations = destinations,
                            currentRoute = history.current,
                            onNavigate = ::goTo,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { searchOpen = true }) {
                            Icon(imageVector = Icons.Filled.Search, contentDescription = "Search")
                        }
                        ModeSwitch(mode = mode, onModeChange = onModeChange)
                    }
                }
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (!railCollapsed) {
                        RoleplayRailPanel(
                            modifier = Modifier.width(liveRailWidth.dp).fillMaxHeight(),
                            onOpenChat = { chatId -> goTo(RoleplayDestination.Chats(chatId)) },
                        )
                    } else {
                        CollapsedRailEdgeStrip(onClick = { shellViewModel.setRailCollapsed(false) })
                    }
                    RoleplayNavHost(navController, modifier = Modifier.weight(1f).fillMaxSize())
                }
            }
        }

        if (railSheetOpen) {
            InkModalBottomSheet(onDismiss = { railSheetOpen = false }) {
                RoleplayRailPanel(
                    onOpenChat = { chatId ->
                        goTo(RoleplayDestination.Chats(chatId))
                        railSheetOpen = false
                    },
                )
            }
        }

        if (searchOpen) {
            GlobalSearchScreen(onDismiss = { searchOpen = false }, modifier = Modifier.fillMaxSize())
        }

        if (settingsOpen) {
            SettingsScreen(onDismiss = { settingsOpen = false }, modifier = Modifier.fillMaxSize())
        }
    }
}

/** The 24dp edge strip that restores a collapsed rail — same treatment as
 * [com.ihy2ln.weaverse.feature.novel.NovelShellContent]'s own. */
@Composable
private fun CollapsedRailEdgeStrip(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(24.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClickLabel = "Expand rail", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = "Expand rail",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RoleplayNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = RoleplayDestination.Chats(),
        modifier = modifier,
    ) {
        composable<RoleplayDestination.Chats> { backStackEntry ->
            val route = backStackEntry.toRoute<RoleplayDestination.Chats>()
            RpChatsScreen(initialChatId = route.chatId)
        }
        composable<RoleplayDestination.Characters> { CharactersScreen() }
        composable<RoleplayDestination.Personas> { PersonasScreen() }
        composable<RoleplayDestination.Codex> { RoleplayCodexScreen() }
        composable<RoleplayDestination.Presets> { PresetsScreen() }
    }
}
