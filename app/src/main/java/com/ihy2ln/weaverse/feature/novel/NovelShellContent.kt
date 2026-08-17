package com.ihy2ln.weaverse.feature.novel

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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Search
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
import com.ihy2ln.weaverse.feature.novel.chat.ChatScreen
import com.ihy2ln.weaverse.feature.novel.plan.PlanScreen
import com.ihy2ln.weaverse.feature.novel.review.ReviewScreen
import com.ihy2ln.weaverse.feature.novel.series.SeriesSheet
import com.ihy2ln.weaverse.feature.novel.write.WriteScreen
import com.ihy2ln.weaverse.feature.search.GlobalSearchScreen
import com.ihy2ln.weaverse.feature.settings.SettingsScreen
import com.ihy2ln.weaverse.feature.shell.AppHeaderBar
import com.ihy2ln.weaverse.feature.shell.AppHeaderHeight
import com.ihy2ln.weaverse.feature.shell.AppTopBar
import com.ihy2ln.weaverse.feature.shell.AppWindowSizeClass
import com.ihy2ln.weaverse.feature.shell.DestinationHistory
import com.ihy2ln.weaverse.feature.shell.ModeSwitch
import com.ihy2ln.weaverse.feature.shell.NavDestinationSpec
import com.ihy2ln.weaverse.feature.shell.NovelDestination
import com.ihy2ln.weaverse.feature.shell.NovelRailPanel
import com.ihy2ln.weaverse.feature.shell.PrimaryDestinationBar
import com.ihy2ln.weaverse.feature.shell.SegmentedDestinationBar

private val destinations: List<NavDestinationSpec<NovelDestination>> = listOf(
    NavDestinationSpec<NovelDestination>(NovelDestination.Plan, "Plan", Icons.Filled.MenuBook),
    NavDestinationSpec<NovelDestination>(NovelDestination.Write(), "Write", Icons.Filled.Edit),
    NavDestinationSpec<NovelDestination>(NovelDestination.Chat, "Chat", Icons.Filled.Forum),
    NavDestinationSpec<NovelDestination>(NovelDestination.Review, "Review", Icons.Filled.RateReview),
)

/**
 * Novel mode's own Scaffold + NavHost, kept permanently composed inside
 * [com.ihy2ln.weaverse.feature.shell.ModeCrossfadeHost] so its back stack
 * (and Roleplay's, symmetrically) survives switching modes and back again.
 *
 * Revision 02 §1 chrome overhaul: on Medium/Expanded, the old side
 * [com.ihy2ln.weaverse.feature.shell.PrimaryDestinationRail] icon rail is
 * gone — Plan/Write/Chat/Review now live in the dark-pill
 * [SegmentedDestinationBar] next to a rail-width [AppHeaderBar] (spec §1.2/
 * §1.3's reference screenshot). Compact keeps the pre-Revision-02
 * [AppTopBar] + bottom [PrimaryDestinationBar] + modal rail sheet — the
 * reference screenshots are unambiguously a tablet/desktop-width layout, and
 * squeezing a resizable/collapsible rail onto a phone-width screen raises
 * design questions the spec doesn't answer, so that combination is scoped
 * out here and documented in BUILD_NOTES rather than guessed at.
 *
 * Scope cut: the header's per-destination contextual controls (Plan's Grid/
 * Matrix/Outline toggle + filter box, Write's scope selector + stat line,
 * Chat's inline thread-name field — spec §1.3) aren't wired into the shared
 * shell header yet; each screen still renders its own equivalent inline.
 * Doing this properly needs a shared "contextual header slot" state holder
 * fed by whichever destination is active, which is more shell-architecture
 * than this pass had room for — see BUILD_NOTES "Revision 02 deviations".
 */
@Composable
fun NovelShellContent(
    windowSizeClass: AppWindowSizeClass,
    mode: AppMode,
    onModeChange: (AppMode) -> Unit,
    shellViewModel: NovelShellViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val history = remember { DestinationHistory<NovelDestination>(NovelDestination.Plan) }
    var railSheetOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var seriesSheetOpen by remember { mutableStateOf(false) }
    val bookTitle by shellViewModel.currentBookTitle.collectAsState()
    val seriesName by shellViewModel.currentSeriesName.collectAsState()
    val persistedRailWidth by shellViewModel.railWidthDp.collectAsState()
    val railCollapsed by shellViewModel.railCollapsed.collectAsState()
    var liveRailWidth by remember { mutableIntStateOf(persistedRailWidth) }
    LaunchedEffect(persistedRailWidth) { liveRailWidth = persistedRailWidth }

    fun goTo(route: NovelDestination) {
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
                        title = bookTitle.ifBlank { "Weaverse" },
                        subtitle = seriesName,
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
                NovelNavHost(navController, ::goTo, modifier = Modifier.padding(innerPadding).fillMaxSize())
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    AppHeaderBar(
                        title = bookTitle.ifBlank { "Weaverse" },
                        seriesName = seriesName,
                        canGoBack = history.canGoBack,
                        canGoForward = history.canGoForward,
                        onBack = ::goBack,
                        onForward = ::goForward,
                        onSettingsClick = { settingsOpen = true },
                        onSeriesClick = { seriesSheetOpen = true },
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
                        NovelRailPanel(
                            modifier = Modifier.width(liveRailWidth.dp).fillMaxHeight(),
                            onOpenScene = { sceneId -> goTo(NovelDestination.Write(sceneId)) },
                        )
                    } else {
                        CollapsedRailEdgeStrip(onClick = { shellViewModel.setRailCollapsed(false) })
                    }
                    NovelNavHost(navController, ::goTo, modifier = Modifier.weight(1f).fillMaxSize())
                }
            }
        }

        if (railSheetOpen) {
            InkModalBottomSheet(onDismiss = { railSheetOpen = false }) {
                NovelRailPanel(
                    onOpenScene = { sceneId ->
                        goTo(NovelDestination.Write(sceneId))
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

        if (seriesSheetOpen) {
            SeriesSheet(onDismiss = { seriesSheetOpen = false })
        }
    }
}

/** The 24dp edge strip that restores a collapsed rail (spec §1.2: "a slim 24dp edge strip with a
 * →| icon restores it"). */
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
private fun NovelNavHost(
    navController: NavHostController,
    onOpenScene: (NovelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = NovelDestination.Plan,
        modifier = modifier,
    ) {
        composable<NovelDestination.Plan> {
            PlanScreen(onOpenScene = { sceneId -> onOpenScene(NovelDestination.Write(sceneId)) })
        }
        composable<NovelDestination.Write> { backStackEntry ->
            val route = backStackEntry.toRoute<NovelDestination.Write>()
            WriteScreen(initialSceneId = route.sceneId)
        }
        composable<NovelDestination.Chat> { ChatScreen() }
        composable<NovelDestination.Review> { ReviewScreen() }
    }
}
