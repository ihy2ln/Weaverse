@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.ihy2ln.weaverse.feature.storyboard

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.NoAccounts
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.network.httpHeaders
import com.ihy2ln.weaverse.core.manga.MangaBrowseMode
import com.ihy2ln.weaverse.core.manga.MangaChapter
import com.ihy2ln.weaverse.core.manga.MangaSearchResult
import com.ihy2ln.weaverse.core.manga.extension.ExtensionInstallMode
import com.ihy2ln.weaverse.core.manga.extension.ExtensionInstallStep
import com.ihy2ln.weaverse.core.manga.extension.InstalledExtension
import com.ihy2ln.weaverse.data.db.entities.MangaChapterEntity
import com.ihy2ln.weaverse.data.db.entities.MangaSeriesEntity
import com.ihy2ln.weaverse.feature.library.WorkShelfCard
import com.ihy2ln.weaverse.feature.library.WorkShelfKind
import com.ihy2ln.weaverse.feature.library.WorkShelfScreen
import java.text.DateFormat
import java.util.Date
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.ConfigurableSource
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.preference.TwoStatePreference
import androidx.preference.ListPreference
import androidx.preference.EditTextPreference
import androidx.preference.MultiSelectListPreference
import androidx.compose.material3.Switch

private val MihonBackground = Color(0xFF111318)
private val MihonSurface = Color(0xFF191C22)
private val MihonSurfaceHigh = Color(0xFF24272E)
private val MihonPrimary = Color(0xFFA9C7FF)
private val MihonOnPrimary = Color(0xFF0B305F)
private val MihonText = Color(0xFFE2E2E9)
private val MihonMuted = Color(0xFFC3C6D0)
private val MihonError = Color(0xFFFFB4AB)

private val MihonColors = darkColorScheme(
    primary = MihonPrimary,
    onPrimary = MihonOnPrimary,
    background = MihonBackground,
    onBackground = MihonText,
    surface = MihonBackground,
    onSurface = MihonText,
    surfaceVariant = MihonSurfaceHigh,
    onSurfaceVariant = MihonMuted,
    error = MihonError,
)

private enum class MihonDestination(val label: String, val icon: ImageVector) {
    Library("Library", Icons.Outlined.CollectionsBookmark),
    Updates("Updates", Icons.Outlined.NewReleases),
    History("History", Icons.Outlined.History),
    Browse("Browse", Icons.Outlined.Explore),
    More("More", Icons.Outlined.MoreHoriz),
}

private enum class MihonBrowseTab(val label: String) { Sources("Sources"), Extensions("Extensions"), Migrate("Migrate") }

private enum class MihonMorePage { Downloads, Categories, Statistics, Data, Settings, About, LinkDownload, Projects }

private enum class LibraryDisplay { Grid, List }

private data class MihonLibraryEntry(
    val key: String,
    val title: String,
    val cover: String?,
    val chapter: MangaChapterEntity? = null,
    val favorite: MangaSearchResult? = null,
    val chapterCount: Int = 0,
)

@Composable
internal fun MihonMangaHome(
    initialTab: String,
    state: MangaSourceUiState,
    viewModel: MangaSourceViewModel,
    onCreateProject: () -> Unit,
    onOpenProject: (WorkShelfCard) -> Unit,
    onEditChapter: (MangaEditRequest) -> Unit,
    modifier: Modifier = Modifier,
) {
    var destinationName by rememberSaveable { mutableStateOf(MihonDestination.Library.name) }
    var browseTabName by rememberSaveable { mutableStateOf(MihonBrowseTab.Sources.name) }
    var morePageName by rememberSaveable { mutableStateOf<String?>(null) }
    var catalogOpen by rememberSaveable { mutableStateOf(false) }
    var downloadedOnly by rememberSaveable { mutableStateOf(false) }
    val destination = runCatching { MihonDestination.valueOf(destinationName) }.getOrDefault(MihonDestination.Library)
    val browseTab = runCatching { MihonBrowseTab.valueOf(browseTabName) }.getOrDefault(MihonBrowseTab.Sources)
    val morePage = morePageName?.let { runCatching { MihonMorePage.valueOf(it) }.getOrNull() }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(initialTab) {
        catalogOpen = false
        when (initialTab) {
            "Browse" -> {
                destinationName = MihonDestination.Browse.name
                browseTabName = MihonBrowseTab.Sources.name
                morePageName = null
            }
            "Extensions" -> {
                destinationName = MihonDestination.Browse.name
                browseTabName = MihonBrowseTab.Extensions.name
                morePageName = null
            }
            "Downloads" -> {
                destinationName = MihonDestination.More.name
                morePageName = MihonMorePage.Downloads.name
            }
            "Projects" -> {
                destinationName = MihonDestination.More.name
                morePageName = MihonMorePage.Projects.name
            }
            else -> {
                destinationName = MihonDestination.Library.name
                morePageName = null
            }
        }
    }

    LaunchedEffect(state.status) {
        if (state.status.isNotBlank()) snackbar.showSnackbar(state.status)
    }

    BackHandler(enabled = catalogOpen || morePage != null) {
        when {
            state.selected != null -> viewModel.clearSelection()
            catalogOpen -> catalogOpen = false
            else -> morePageName = null
        }
    }

    MaterialTheme(colorScheme = MihonColors) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = MihonBackground,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar(containerColor = MihonSurface) {
                    MihonDestination.entries.forEach { item ->
                        NavigationBarItem(
                            selected = destination == item,
                            onClick = {
                                destinationName = item.name
                                catalogOpen = false
                                morePageName = null
                                if (item == MihonDestination.Browse) browseTabName = MihonBrowseTab.Sources.name
                            },
                            icon = { Icon(item.icon, item.label) },
                            label = { Text(item.label, maxLines = 1) },
                            alwaysShowLabel = true,
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (destination) {
                    MihonDestination.Library -> MihonLibraryScreen(
                        state = state,
                        viewModel = viewModel,
                        downloadedOnly = downloadedOnly,
                        onOpenFavorite = { manga ->
                            destinationName = MihonDestination.Browse.name
                            browseTabName = MihonBrowseTab.Sources.name
                            viewModel.selectSource(manga.sourceId)
                            viewModel.select(manga)
                            catalogOpen = true
                        },
                    )
                    MihonDestination.Updates -> MihonUpdatesScreen(state, viewModel)
                    MihonDestination.History -> MihonHistoryScreen(state, viewModel)
                    MihonDestination.Browse -> if (catalogOpen) {
                        MihonCatalogScreen(
                            state = state,
                            viewModel = viewModel,
                            onBack = {
                                if (state.selected != null) viewModel.clearSelection() else catalogOpen = false
                            },
                            onEditChapter = onEditChapter,
                        )
                    } else {
                        MihonBrowseScreen(
                            tab = browseTab,
                            onTab = { browseTabName = it.name },
                            state = state,
                            viewModel = viewModel,
                            onOpenCatalog = { sourceId, mode ->
                                viewModel.selectSource(sourceId)
                                viewModel.browse(mode)
                                catalogOpen = true
                            },
                            onSearchCatalog = { sourceId, title ->
                                viewModel.selectSource(sourceId)
                                viewModel.setQuery(title)
                                viewModel.search()
                                catalogOpen = true
                            },
                        )
                    }
                    MihonDestination.More -> if (morePage == null) {
                        MihonMoreScreen(
                            downloadedOnly = downloadedOnly,
                            onDownloadedOnly = { downloadedOnly = it },
                            incognito = state.incognito,
                            onIncognito = viewModel::setIncognito,
                            downloads = state.downloads.size,
                            onOpen = { morePageName = it.name },
                        )
                    } else {
                        MihonMoreDetail(
                            page = morePage,
                            state = state,
                            viewModel = viewModel,
                            onBack = { morePageName = null },
                            onCreateProject = onCreateProject,
                            onOpenProject = onOpenProject,
                            onEditChapter = onEditChapter,
                        )
                    }
                }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
}

@Composable
private fun MihonLibraryScreen(
    state: MangaSourceUiState,
    viewModel: MangaSourceViewModel,
    downloadedOnly: Boolean,
    onOpenFavorite: (MangaSearchResult) -> Unit,
) {
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf("all") }
    var displayName by rememberSaveable { mutableStateOf(LibraryDisplay.Grid.name) }
    var sortDescending by rememberSaveable { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var overflow by remember { mutableStateOf(false) }
    val display = runCatching { LibraryDisplay.valueOf(displayName) }.getOrDefault(LibraryDisplay.Grid)
    val downloadedEntries = remember(state.downloads, state.coverPaths) {
        state.downloads.groupBy { it.mangaId.ifBlank { it.mangaTitle } }.map { (key, chapters) ->
            val representative = chapters.firstOrNull { it.status == "completed" } ?: chapters.first()
            MihonLibraryEntry(
                key = "download-$key",
                title = representative.mangaTitle.ifBlank { representative.title },
                cover = state.coverPaths[representative.id],
                chapter = representative,
                chapterCount = chapters.count { it.status == "completed" },
            )
        }
    }
    val favoriteCategoryIds = state.favorites.filter { categoryId == "all" || it.categoryId == categoryId }.mapTo(hashSetOf()) { it.seriesId }
    val favoriteEntries = state.favoriteSeries.filter { it.id in favoriteCategoryIds }.map { series ->
        MihonLibraryEntry(
            key = "favorite-${series.id}",
            title = series.title,
            cover = series.coverUrl,
            favorite = series.toMihonSearchResult(),
        )
    }
    val entries = ((if (categoryId == "all") downloadedEntries else emptyList()) + if (downloadedOnly) emptyList() else favoriteEntries)
        .distinctBy { it.title.lowercase() }
        .filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
        .let { list -> if (sortDescending) list.sortedByDescending { it.title.lowercase() } else list.sortedBy { it.title.lowercase() } }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                if (searching) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search library") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Library")
                        if (entries.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Surface(shape = CircleShape, color = MihonSurfaceHigh) {
                                Text("${entries.size}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 13.sp)
                            }
                        }
                    }
                }
            },
            navigationIcon = {
                if (searching) IconButton(onClick = { searching = false; query = "" }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Close search")
                }
            },
            actions = {
                if (!searching) IconButton(onClick = { searching = true }) { Icon(Icons.Outlined.Search, "Search") }
                IconButton(onClick = { showFilters = true }) { Icon(Icons.Outlined.FilterList, "Filter") }
                Box {
                    IconButton(onClick = { overflow = true }) { Icon(Icons.Outlined.MoreVert, "More options") }
                    DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                        DropdownMenuItem(text = { Text("Update library") }, onClick = {
                            overflow = false
                            viewModel.setStatus("Library update started")
                        }, leadingIcon = { Icon(Icons.Outlined.Refresh, null) })
                        DropdownMenuItem(text = { Text("Open random manga") }, onClick = {
                            overflow = false
                            entries.randomOrNull()?.let { entry -> entry.chapter?.let { viewModel.openReader(it.id) } ?: entry.favorite?.let(onOpenFavorite) }
                        })
                    }
                }
            },
        )
        if (state.favoriteCategories.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = categoryId == "all", onClick = { categoryId = "all" }, label = { Text("All") })
                state.favoriteCategories.forEach { category ->
                    FilterChip(selected = categoryId == category.id, onClick = { categoryId = category.id }, label = { Text(category.name) })
                }
            }
        }
        if (entries.isEmpty()) {
            MihonEmptyState(
                face = "(╬ಠ益ಠ)",
                message = if (query.isBlank()) "Your library is empty" else "No results found",
                action = if (query.isBlank()) "Getting started guide" else null,
                onAction = { viewModel.setStatus("Open Browse to add manga to your library") },
            )
        } else if (display == LibraryDisplay.Grid) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(112.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(entries, key = { it.key }) { entry ->
                    MihonLibraryGridItem(entry) {
                        entry.chapter?.takeIf { it.status == "completed" }?.let { viewModel.openReader(it.id) }
                            ?: entry.favorite?.let(onOpenFavorite)
                    }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
                lazyItems(entries, key = { it.key }) { entry ->
                    MihonLibraryListItem(entry) {
                        entry.chapter?.takeIf { it.status == "completed" }?.let { viewModel.openReader(it.id) }
                            ?: entry.favorite?.let(onOpenFavorite)
                    }
                }
            }
        }
    }

    if (showFilters) {
        AlertDialog(
            onDismissRequest = { showFilters = false },
            title = { Text("Library settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Filter", fontWeight = FontWeight.SemiBold)
                    FilterChip(selected = true, onClick = {}, label = { Text("Downloaded") })
                    HorizontalDivider()
                    Text("Sort", fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = sortDescending, onCheckedChange = { sortDescending = it })
                        Text("Descending alphabetical order")
                    }
                    HorizontalDivider()
                    Text("Display", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(display == LibraryDisplay.Grid, { displayName = LibraryDisplay.Grid.name }, { Text("Comfortable grid") }, leadingIcon = { Icon(Icons.Outlined.GridView, null) })
                        FilterChip(display == LibraryDisplay.List, { displayName = LibraryDisplay.List.name }, { Text("List") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ViewList, null) })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFilters = false }) { Text("Done") } },
        )
    }
}

@Composable
private fun MihonLibraryGridItem(entry: MihonLibraryEntry, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box {
            AsyncImage(
                model = entry.cover,
                contentDescription = entry.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(0.68f).clip(RoundedCornerShape(8.dp)).background(MihonSurfaceHigh),
            )
            if (entry.chapterCount > 0) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(5.dp),
                    color = MihonPrimary,
                    contentColor = MihonOnPrimary,
                    shape = RoundedCornerShape(12.dp),
                ) { Text("${entry.chapterCount}", modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun MihonLibraryListItem(entry: MihonLibraryEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(entry.cover, entry.title, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp, 72.dp).clip(RoundedCornerShape(5.dp)).background(MihonSurfaceHigh))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            if (entry.chapterCount > 0) Text("${entry.chapterCount} downloaded chapters", color = MihonMuted, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.AutoMirrored.Outlined.NavigateNext, null, tint = MihonMuted)
    }
}

@Composable
private fun MihonUpdatesScreen(state: MangaSourceUiState, viewModel: MangaSourceViewModel) {
    var filterOpen by remember { mutableStateOf(false) }
    val updates = state.downloads.sortedByDescending { it.updatedAt }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Updates") },
            actions = {
                IconButton(onClick = { filterOpen = true }) { Icon(Icons.Outlined.FilterList, "Filter") }
                IconButton(onClick = { viewModel.setStatus("Upcoming updates are based on your saved sources") }) { Icon(Icons.Outlined.CalendarMonth, "View Upcoming Updates") }
                IconButton(onClick = { viewModel.setStatus("Library update started") }) { Icon(Icons.Outlined.Refresh, "Update library") }
            },
        )
        if (updates.isEmpty()) {
            MihonEmptyState("(･Д･。", "No recent updates")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                item { MihonGroupHeader("Recently updated") }
                lazyItems(updates, key = { it.id }) { chapter ->
                    MihonChapterRow(chapter, state.coverPaths[chapter.id], onClick = {
                        if (chapter.status == "completed") viewModel.openReader(chapter.id)
                    }, trailing = {
                        MihonDownloadAction(chapter, viewModel)
                    })
                }
            }
        }
    }
    if (filterOpen) AlertDialog(
        onDismissRequest = { filterOpen = false },
        title = { Text("Filter updates") },
        text = { Column { Text("Category"); Text("All categories", color = MihonMuted); Spacer(Modifier.height(12.dp)); Text("Status"); Text("Downloaded · Unread · Bookmarked", color = MihonMuted) } },
        confirmButton = { TextButton(onClick = { filterOpen = false }) { Text("Done") } },
    )
}

@Composable
private fun MihonHistoryScreen(state: MangaSourceUiState, viewModel: MangaSourceViewModel) {
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val history = state.downloads.filter { it.lastReadAt > 0 && (query.isBlank() || it.mangaTitle.contains(query, true)) }
        .sortedByDescending { it.lastReadAt }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                if (searching) OutlinedTextField(query, { query = it }, placeholder = { Text("Search history") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                else Text("History")
            },
            navigationIcon = { if (searching) IconButton(onClick = { searching = false; query = "" }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Close search") } },
            actions = {
                if (!searching) IconButton(onClick = { searching = true }) { Icon(Icons.Outlined.Search, "Search") }
                IconButton(onClick = { confirmClear = true }) { Icon(Icons.Outlined.DeleteSweep, "Clear history") }
            },
        )
        if (history.isEmpty()) {
            MihonEmptyState("(；´Д｀)", if (query.isBlank()) "Nothing read recently" else "No results found")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                item { MihonGroupHeader("Today") }
                lazyItems(history, key = { it.id }) { chapter ->
                    MihonChapterRow(chapter, state.coverPaths[chapter.id], onClick = { viewModel.resumeChapter(chapter) }, trailing = {
                        IconButton(onClick = { viewModel.resumeChapter(chapter) }) { Icon(Icons.Outlined.PlayArrow, "Resume") }
                    })
                }
            }
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Clear history") },
        text = { Text("Downloaded chapters and read/unread state are kept. Only resume positions and history timestamps are cleared.") },
        confirmButton = { TextButton(onClick = { confirmClear = false; viewModel.clearHistory() }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
    )
}

@Composable
private fun MihonBrowseScreen(
    tab: MihonBrowseTab,
    onTab: (MihonBrowseTab) -> Unit,
    state: MangaSourceUiState,
    viewModel: MangaSourceViewModel,
    onOpenCatalog: (String, MangaBrowseMode) -> Unit,
    onSearchCatalog: (String, String) -> Unit,
) {
    var globalSearch by rememberSaveable { mutableStateOf(false) }
    var globalQuery by rememberSaveable { mutableStateOf("") }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.importLocalFiles(uris)
    }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                if (globalSearch) OutlinedTextField(
                    globalQuery,
                    { globalQuery = it },
                    placeholder = { Text("Search all sources") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                ) else Text("Browse")
            },
            navigationIcon = { if (globalSearch) IconButton(onClick = { globalSearch = false; globalQuery = "" }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Close search") } },
            actions = {
                if (!globalSearch) IconButton(onClick = { globalSearch = true }) { Icon(Icons.Outlined.Search, "Global search") }
                IconButton(onClick = { viewModel.setStatus("Source filters are available in Extensions") }) { Icon(Icons.Outlined.FilterList, "Filter") }
            },
        )
        ScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 16.dp, containerColor = MihonBackground) {
            MihonBrowseTab.entries.forEach { item -> Tab(selected = tab == item, onClick = { onTab(item) }, text = { Text(item.label) }) }
        }
        if (globalSearch && globalQuery.isNotBlank()) {
            LazyColumn(Modifier.fillMaxSize()) {
                item { MihonGroupHeader("Search in") }
                lazyItems(state.sources, key = { it.id }) { source ->
                    MihonPreferenceRow(Icons.Outlined.Search, source.name, source.description) { onSearchCatalog(source.id, globalQuery) }
                }
            }
        } else when (tab) {
            MihonBrowseTab.Sources -> LazyColumn(Modifier.fillMaxSize()) {
                item { MihonGroupHeader("Other") }
                item {
                    MihonSourceRow(
                        name = "Local source",
                        subtitle = "Read CBZ, ZIP, PDF, or images from this device",
                        status = null,
                        onClick = { importLauncher.launch(arrayOf("application/pdf", "application/zip", "application/x-cbz", "image/*")) },
                        onLatest = { importLauncher.launch(arrayOf("application/pdf", "application/zip", "application/x-cbz", "image/*")) },
                        onPin = null,
                    )
                }
                val pinned = state.sources.filter { it.id in state.pinnedSourceIds }
                if (pinned.isNotEmpty()) {
                    item { MihonGroupHeader("Pinned") }
                    lazyItems(pinned, key = { "pinned-${it.id}" }) { source ->
                        MihonSourceRow(source.name, source.description, state.sourceHealth[source.id], { onOpenCatalog(source.id, MangaBrowseMode.Popular) }, { onOpenCatalog(source.id, MangaBrowseMode.Latest) }, true) { viewModel.togglePinnedSource(source.id) }
                    }
                }
                state.sources.filterNot { it.id in state.pinnedSourceIds }.groupBy { it.language.ifBlank { "other" } }.toSortedMap().forEach { (languageCode, sources) ->
                    item { MihonGroupHeader(languageCode.uppercase()) }
                    lazyItems(sources, key = { it.id }) { source ->
                        MihonSourceRow(source.name, source.description, state.sourceHealth[source.id], { onOpenCatalog(source.id, MangaBrowseMode.Popular) }, { onOpenCatalog(source.id, MangaBrowseMode.Latest) }, false) { viewModel.togglePinnedSource(source.id) }
                    }
                }
            }
            MihonBrowseTab.Extensions -> MihonExtensionsScreen(state, viewModel, onOpenCatalog)
            MihonBrowseTab.Migrate -> MihonMigrateScreen(state, onSearchCatalog)
        }
    }
}

@Composable
private fun MihonSourceRow(
    name: String,
    subtitle: String,
    status: String?,
    onClick: () -> Unit,
    onLatest: () -> Unit,
    pinned: Boolean = false,
    onPin: (() -> Unit)? = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = RoundedCornerShape(10.dp), color = MihonSurfaceHigh, modifier = Modifier.size(48.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Language, null, tint = MihonPrimary) }
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(name, fontWeight = FontWeight.Medium)
            Text(status ?: subtitle, color = if (status?.startsWith("Ready") == true) MihonPrimary else MihonMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        TextButton(onClick = onLatest) { Text("Latest") }
        if (onPin != null) IconButton(onClick = onPin) { Icon(Icons.Outlined.PushPin, if (pinned) "Unpin" else "Pin", tint = if (pinned) MihonPrimary else MihonMuted) }
    }
}

@Composable
private fun MihonExtensionsScreen(
    state: MangaSourceUiState,
    viewModel: MangaSourceViewModel,
    onOpenCatalog: (String, MangaBrowseMode) -> Unit,
) {
    val catalog = state.extensionCatalog
    var query by rememberSaveable { mutableStateOf("") }
    var language by rememberSaveable { mutableStateOf("all") }
    var installModeName by rememberSaveable { mutableStateOf(ExtensionInstallMode.Private.name) }
    var manageStores by rememberSaveable { mutableStateOf(false) }
    var storeUrl by rememberSaveable { mutableStateOf("") }
    var uninstall by remember { mutableStateOf<InstalledExtension?>(null) }
    var details by remember { mutableStateOf<InstalledExtension?>(null) }
    val installMode = ExtensionInstallMode.valueOf(installModeName)
    val languages = (catalog.installed.map { it.language } + catalog.available.map { it.language })
        .filter(String::isNotBlank).distinct().sorted()
    val matches: (String, String) -> Boolean = { name, lang ->
        (query.isBlank() || name.contains(query, ignoreCase = true)) && (language == "all" || lang == language || lang == "all")
    }
    val installedByPackage = catalog.installed.associateBy { it.packageName }
    val updates = catalog.available.filter { available ->
        installedByPackage[available.packageName]?.let { available.versionCode > it.versionCode || available.libVersion > it.libVersion } == true
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(query, { query = it }, placeholder = { Text("Search extensions") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(language == "all", { language = "all" }, label = { Text("All languages") })
                    languages.forEach { lang -> FilterChip(language == lang, { language = lang }, label = { Text(lang.uppercase()) }) }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExtensionInstallMode.entries.forEach { mode ->
                        FilterChip(installMode == mode, { installModeName = mode.name }, label = { Text(when(mode) { ExtensionInstallMode.PackageInstaller -> "Package installer"; ExtensionInstallMode.Private -> "Private"; ExtensionInstallMode.Shizuku -> "Shizuku" }) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.refreshExtensions() }) { Icon(Icons.Outlined.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Refresh") }
                    TextButton(onClick = { manageStores = true }) { Text("Extension stores") }
                }
            }
        }
        val untrusted = catalog.installed.filter { !it.trusted && matches(it.name, it.language) }
        if (untrusted.isNotEmpty()) {
            item { MihonGroupHeader("Untrusted") }
            lazyItems(untrusted, key = { "untrusted-${it.packageName}" }) { extension ->
                MihonPreferenceRow(Icons.Outlined.ErrorOutline, extension.name, "${extension.versionName} · ${extension.signatureHash.take(12)}…",
                    trailing = { TextButton(onClick = { viewModel.trustExtension(extension.packageName) }) { Text("Trust") } }, onClick = {})
            }
        }
        if (updates.isNotEmpty()) {
            item { MihonGroupHeader("Updates") }
            lazyItems(updates, key = { "update-${it.packageName}" }) { extension ->
                MihonPreferenceRow(Icons.Outlined.NewReleases, extension.name, "${installedByPackage[extension.packageName]?.versionName} → ${extension.versionName}",
                    trailing = { TextButton(onClick = { viewModel.installExtension(extension, installMode) }) { Text("Update") } }, onClick = {})
            }
        }
        val installed = catalog.installed.filter { it.trusted && matches(it.name, it.language) }
        if (installed.isNotEmpty()) {
            item { MihonGroupHeader("Installed") }
            lazyItems(installed, key = { "installed-${it.packageName}" }) { extension ->
                MihonPreferenceRow(
                    icon = if (extension.error == null) Icons.Outlined.Extension else Icons.Outlined.ErrorOutline,
                    title = extension.name,
                    subtitle = listOf(extension.versionName, extension.language.uppercase(), if (extension.shared) "Shared" else "Private", extension.error).filterNotNull().joinToString(" · "),
                    trailing = { TextButton(onClick = { details = extension }) { Text("Details") } },
                    onClick = { details = extension },
                )
            }
        }
        val available = catalog.available.filter { it.packageName !in installedByPackage && matches(it.name, it.language) }
        if (available.isNotEmpty()) {
            item { MihonGroupHeader("Available") }
            lazyItems(available, key = { "available-${it.packageName}" }) { extension ->
                val step = catalog.installSteps[extension.packageName] ?: ExtensionInstallStep.Idle
                MihonPreferenceRow(Icons.Outlined.Extension, extension.name, "${extension.language.uppercase()} · ${extension.versionName} · ${extension.storeName}",
                    trailing = {
                        if (step == ExtensionInstallStep.Downloading) {
                            TextButton(onClick = { viewModel.cancelExtensionInstall(extension.packageName) }) { Text("Cancel") }
                        } else {
                            TextButton(onClick = { viewModel.installExtension(extension, installMode) }, enabled = step != ExtensionInstallStep.Installing) {
                                Text(when(step) { ExtensionInstallStep.Installing -> "Installing"; ExtensionInstallStep.Error -> "Retry"; ExtensionInstallStep.Installed -> "Installed"; else -> "Install" })
                            }
                        }
                    }, onClick = {})
            }
        }
        if (catalog.initialized && catalog.installed.isEmpty() && catalog.available.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("(╬ಠ益ಠ)", fontSize = 44.sp, color = MihonMuted)
                    Text("Well, this is awkward", modifier = Modifier.padding(top = 12.dp))
                    TextButton(onClick = { manageStores = true }) { Text("Extension stores") }
                }
            }
        }
    }
    if (manageStores) AlertDialog(
        onDismissRequest = { manageStores = false },
        title = { Text("Extension stores") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                catalog.stores.forEach { store ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(store.name); Text(store.indexUrl, color = MihonMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
                        IconButton(onClick = { viewModel.removeExtensionStore(store.indexUrl) }) { Icon(Icons.Outlined.Delete, "Remove store") }
                    }
                }
                OutlinedTextField(storeUrl, { storeUrl = it }, label = { Text("Store index URL") }, supportingText = { Text("*required") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { viewModel.addExtensionStore(storeUrl); storeUrl = "" }, enabled = storeUrl.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = { manageStores = false }) { Text("Close") } },
    )
    uninstall?.let { extension ->
        AlertDialog(onDismissRequest = { uninstall = null }, title = { Text("Uninstall ${extension.name}?") }, text = { Text("Its sources will immediately disappear from Browse. Library records are preserved for migration.") },
            confirmButton = { TextButton(onClick = { viewModel.uninstallExtension(extension); uninstall = null }) { Text("Uninstall") } }, dismissButton = { TextButton(onClick = { uninstall = null }) { Text("Cancel") } })
    }
    details?.let { extension ->
        MihonExtensionDetailsDialog(
            extension = extension,
            state = state,
            onOpenSource = { sourceId -> details = null; onOpenCatalog(sourceId, MangaBrowseMode.Popular) },
            onUninstall = { details = null; uninstall = extension },
            onDismiss = { details = null },
        )
    }
}

@Composable
private fun MihonExtensionDetailsDialog(
    extension: InstalledExtension,
    state: MangaSourceUiState,
    onOpenSource: (String) -> Unit,
    onUninstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(extension.name) },
        text = {
            Column(Modifier.heightIn(max = 580.dp).verticalScroll(rememberScrollState())) {
                Text("${extension.versionName} · ${if (extension.shared) "Shared" else "Private"}", color = MihonMuted)
                if (extension.error != null) Text(extension.error, color = MihonError, modifier = Modifier.padding(top = 8.dp))
                extension.sources.forEach { source ->
                    val sourceId = "ext:${source.id}"
                    val descriptor = state.sources.firstOrNull { it.id == sourceId }
                    MihonPreferenceRow(
                        icon = Icons.Outlined.Language,
                        title = source.name,
                        subtitle = "${source.lang.uppercase()} · ${descriptor?.baseUrl.orEmpty()}",
                        onClick = { onOpenSource(sourceId) },
                    )
                    val configurable = source as? ConfigurableSource
                    if (configurable != null) {
                        val screen = remember(source.id) {
                            PreferenceManager(context).createPreferenceScreen(context).also(configurable::setupPreferenceScreen)
                        }
                        if (screen.preferenceCount > 0) {
                            Text("Source preferences", style = MaterialTheme.typography.titleSmall, color = MihonPrimary, modifier = Modifier.padding(top = 12.dp))
                            for (index in 0 until screen.preferenceCount) {
                                MihonExtensionPreference(screen.getPreference(index))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = { TextButton(onClick = onUninstall) { Text("Uninstall") } },
    )
}

@Composable
private fun MihonExtensionPreference(preference: Preference) {
    var revision by remember(preference.key) { mutableStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val observedRevision = revision
    when (preference) {
        is PreferenceCategory -> {
            Text(preference.title?.toString().orEmpty(), style = MaterialTheme.typography.labelLarge, color = MihonMuted, modifier = Modifier.padding(top = 10.dp))
            for (index in 0 until preference.preferenceCount) MihonExtensionPreference(preference.getPreference(index))
        }
        is MultiSelectListPreference -> {
            Text(preference.title?.toString().orEmpty(), modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                preference.entries.orEmpty().forEachIndexed { index, label ->
                    val value = preference.entryValues?.getOrNull(index)?.toString() ?: return@forEachIndexed
                    FilterChip(
                        selected = value in preference.values,
                        onClick = {
                            val updated = preference.values.toMutableSet().apply { if (!add(value)) remove(value) }
                            if (preference.callChangeListener(updated)) preference.values = updated
                            revision++
                        },
                        label = { Text(label.toString()) },
                    )
                }
            }
        }
        is ListPreference -> {
            var expanded by remember { mutableStateOf(false) }
            Box {
                MihonPreferenceRow(Icons.AutoMirrored.Outlined.Sort, preference.title?.toString().orEmpty(), preference.entry?.toString() ?: preference.summary?.toString(), onClick = { expanded = true })
                DropdownMenu(expanded, { expanded = false }) {
                    preference.entries.orEmpty().forEachIndexed { index, label ->
                        val value = preference.entryValues?.getOrNull(index)?.toString() ?: return@forEachIndexed
                        DropdownMenuItem(text = { Text(label.toString()) }, onClick = {
                            if (preference.callChangeListener(value)) preference.value = value
                            expanded = false; revision++
                        })
                    }
                }
            }
        }
        is EditTextPreference -> {
            var editing by remember { mutableStateOf(false) }
            var value by remember(editing) { mutableStateOf(preference.text.orEmpty()) }
            MihonPreferenceRow(Icons.Outlined.Edit, preference.title?.toString().orEmpty(), preference.summary?.toString() ?: preference.text, onClick = { editing = true })
            if (editing) AlertDialog(
                onDismissRequest = { editing = false },
                title = { Text(preference.title?.toString().orEmpty()) },
                text = { OutlinedTextField(value, { value = it }, singleLine = true) },
                confirmButton = { TextButton(onClick = { if (preference.callChangeListener(value)) preference.text = value; editing = false; revision++ }) { Text("OK") } },
                dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } },
            )
        }
        is TwoStatePreference -> Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(preference.title?.toString().orEmpty()); preference.summary?.let { Text(it.toString(), color = MihonMuted, style = MaterialTheme.typography.bodySmall) } }
            Switch(preference.isChecked, onCheckedChange = { if (preference.callChangeListener(it)) preference.isChecked = it; revision++ })
        }
        is PreferenceGroup -> for (index in 0 until preference.preferenceCount) MihonExtensionPreference(preference.getPreference(index))
        else -> MihonPreferenceRow(Icons.Outlined.Settings, preference.title?.toString().orEmpty(), preference.summary?.toString(), onClick = { preference.onPreferenceClickListener?.onPreferenceClick(preference) })
    }
}

@Composable
private fun MihonMigrateScreen(state: MangaSourceUiState, onSearchCatalog: (String, String) -> Unit) {
    if (state.favoriteSeries.isEmpty()) {
        MihonEmptyState("( ´△｀)", "Add manga to your library before migrating")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item { MihonGroupHeader("Select a source to migrate from") }
        lazyItems(state.favoriteSeries, key = { it.id }) { series ->
            val target = state.sources.firstOrNull { it.id != series.sourceId } ?: state.sources.firstOrNull()
            MihonPreferenceRow(
                icon = Icons.Outlined.SwapHoriz,
                title = series.title,
                subtitle = "${series.sourceId} · Search for a replacement",
                onClick = { target?.let { onSearchCatalog(it.id, series.title) } },
            )
        }
    }
}

@Composable
private fun MihonCatalogScreen(
    state: MangaSourceUiState,
    viewModel: MangaSourceViewModel,
    onBack: () -> Unit,
    onEditChapter: (MangaEditRequest) -> Unit,
) {
    var searching by rememberSaveable { mutableStateOf(false) }
    var showingFilters by remember { mutableStateOf(false) }
    var sourceBrowserOpen by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    var favoriteTarget by remember { mutableStateOf<MangaSearchResult?>(null) }
    LaunchedEffect(state.results.size, state.busy, state.loadingMore, state.catalogPage, state.selected) {
        // Skip empty filtered pages, with a bound to avoid silently scanning an entire site.
        if (state.selected == null && state.results.isEmpty() && !state.busy && !state.loadingMore && state.canLoadMore && state.catalogPage < 5) {
            viewModel.loadMoreResults()
        }
    }
    val sourceUrl = state.selected?.canonicalUrl?.takeIf { it.isNotBlank() }
        ?: state.sources.firstOrNull { it.id == state.activeSourceId }?.baseUrl.orEmpty()
    if (sourceBrowserOpen && sourceUrl.isNotBlank()) {
        com.ihy2ln.weaverse.core.ui.components.SourceBrowserDialog(sourceUrl) { sourceBrowserOpen = false }
    }
    val sourceName = state.sources.firstOrNull { it.id == state.activeSourceId }?.name ?: "Source"
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                if (searching) OutlinedTextField(
                    state.query,
                    viewModel::setQuery,
                    placeholder = { Text("Search $sourceName") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                ) else Text(state.selected?.title ?: sourceName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            actions = {
                if (sourceUrl.isNotBlank()) IconButton(onClick = { sourceBrowserOpen = true }) { Icon(Icons.Outlined.Language, "Open source website") }
                if (state.selected == null) {
                    IconButton(onClick = { if (searching && state.query.isNotBlank()) viewModel.search() else searching = !searching }) { Icon(Icons.Outlined.Search, "Search") }
                    IconButton(onClick = { showingFilters = true }) { Icon(Icons.Outlined.FilterList, "Filters") }
                    IconButton(onClick = { viewModel.browse(MangaBrowseMode.Latest) }) { Icon(Icons.Outlined.Refresh, "Latest") }
                }
            },
        )
        if (state.selected != null) {
            MihonMangaDetail(state, viewModel, onEditChapter)
        } else if (state.results.isEmpty() && !state.busy) {
            MihonEmptyState("( ´△｀)", if (state.canLoadMore) "No matches on this page. Try the next page or reset filters." else "No matching titles. Adjust filters or retry.", if (state.canLoadMore) "Load next page" else "Retry") {
                if (state.canLoadMore) viewModel.loadMoreResults()
                else if (state.catalogMode != null) viewModel.browse(state.catalogMode) else viewModel.search()
            }
        } else {
            LaunchedEffect(gridState, state.results.size, state.canLoadMore) {
                snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }.collect { last ->
                    if (last >= state.results.lastIndex - 4) viewModel.loadMoreResults()
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(112.dp),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(state.results, key = { "${it.sourceId}:${it.remoteId}" }) { manga ->
                    Column(Modifier.fillMaxWidth().combinedClickable(onClick = { viewModel.select(manga) }, onLongClick = { favoriteTarget = manga })) {
                        AsyncImage(mangaCoverRequest(manga), manga.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(0.68f).clip(RoundedCornerShape(8.dp)).background(MihonSurfaceHigh))
                        Text(manga.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                if (state.loadingMore) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
        }
    }
    favoriteTarget?.let { manga ->
        AlertDialog(onDismissRequest = { favoriteTarget = null }, title = { Text("Favorites · ${manga.title}") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val seriesId = state.favoriteSeries.firstOrNull { it.sourceId == manga.sourceId && it.remoteId == manga.remoteId }?.id
                state.favoriteCategories.forEach { category ->
                    val saved = state.favorites.any { it.seriesId == seriesId && it.categoryId == category.id }
                    Row(Modifier.fillMaxWidth().clickable { viewModel.toggleFavorite(manga, category.id) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(saved, onCheckedChange = null)
                        Text(category.name, Modifier.padding(start = 8.dp))
                    }
                }
                OutlinedTextField(state.favoriteCategoryName, viewModel::setFavoriteCategoryName, label = { Text("New category") })
                TextButton(onClick = viewModel::createFavoriteCategory) { Text("Create category") }
            }
        }, confirmButton = { TextButton(onClick = { favoriteTarget = null }) { Text("Done") } })
    }
    if (showingFilters) {
        MihonNativeFiltersDialog(
            filters = state.nativeFilters.list,
            globalTag = state.globalTagFilter,
            matchAny = state.globalMatchAny,
            sort = state.catalogSort,
            onSort = viewModel::setCatalogSort,
            onMatchAny = viewModel::setGlobalMatchAny,
            globalLanguage = state.globalLanguageFilter,
            globalStatus = state.globalStatusFilter,
            libraryOnly = state.globalLibraryOnly,
            onGlobalTag = viewModel::setGlobalTagFilter,
            onGlobalLanguage = viewModel::setGlobalLanguageFilter,
            onGlobalStatus = viewModel::setGlobalStatusFilter,
            onLibraryOnly = viewModel::setGlobalLibraryOnly,
            onChanged = viewModel::notifyNativeFiltersChanged,
            onReset = { viewModel.resetNativeFilters(); viewModel.resetGlobalFilters() },
            onApply = {
                showingFilters = false
                viewModel.applyCatalogFilters()
            },
            onDismiss = { showingFilters = false },
        )
    }
}

@Composable
private fun MihonNativeFiltersDialog(
    filters: List<Filter<*>>,
    globalTag: String,
    matchAny: Boolean,
    sort: String,
    onSort: (String) -> Unit,
    onMatchAny: (Boolean) -> Unit,
    globalLanguage: String,
    globalStatus: String,
    libraryOnly: Boolean,
    onGlobalTag: (String) -> Unit,
    onGlobalLanguage: (String) -> Unit,
    onGlobalStatus: (String) -> Unit,
    onLibraryOnly: (Boolean) -> Unit,
    onChanged: () -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onReset) { Text("Reset") }
                Button(onClick = onApply) { Text("Filter") }
            }
        },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                Text("Weaverse filters", style = MaterialTheme.typography.titleSmall, color = MihonPrimary)
                CatalogFilterChoice("Sort by", sort, com.ihy2ln.weaverse.core.manga.CatalogSort.options, onSort)
                Text("Popular/Latest use source listings. Other sorts order loaded results only; missing scores/years appear last. Use extension-native sorting below for source-wide search ordering.", style = MaterialTheme.typography.bodySmall)
                Text("Tap once to include ✓, twice to exclude ✕, again to clear. Missing catalog metadata is loaded from title details. AND requires all included tags; OR matches any. Exclusions always apply.", style = MaterialTheme.typography.bodySmall)
                CatalogFilterChoice("Match", if (matchAny) "any" else "all", listOf("All (AND)" to "all", "Any (OR)" to "any")) { onMatchAny(it == "any") }
                CatalogTagSection("Genres", listOf("Action", "Adult", "Adventure", "Boys Love", "Comedy", "Crime", "Drama", "Ecchi", "Fantasy", "Girls Love", "Harem", "Hentai", "Historical", "Horror", "Isekai", "Magical Girls", "Martial Arts", "Mature", "Mecha", "Medical", "Mystery", "Philosophical", "Psychological", "Romance", "Sci-Fi", "Slice of Life", "Smut", "Sports", "Superhero", "Supernatural", "Thriller", "Tragedy", "Wuxia"), globalTag, onGlobalTag)
                CatalogTagSection("Type", listOf("Manga", "Manhwa", "Manhua", "Other"), globalTag, onGlobalTag)
                CatalogTagSection("Formats", listOf("4-Koma", "Adaptation", "Anthology", "Award Winning", "Doujinshi", "Full Color", "Long Strip", "Oneshot", "Web Comic"), globalTag, onGlobalTag)
                CatalogTagSection("Demographic", listOf("Josei", "Seinen", "Shoujo", "Shounen"), globalTag, onGlobalTag)
                CatalogFilterSection("Other tags") {
                    OutlinedTextField(globalTag, onGlobalTag, label = { Text("Selected / custom tags") }, modifier = Modifier.fillMaxWidth())
                }
                CatalogFilterChoice("Language", globalLanguage, listOf("Any" to "", "English" to "en", "Japanese" to "ja", "Korean" to "ko", "Chinese" to "zh", "Spanish" to "es", "French" to "fr", "German" to "de", "Portuguese" to "pt", "Russian" to "ru", "Indonesian" to "id"), onGlobalLanguage)
                CatalogFilterChoice("Publication status", globalStatus, listOf("Any" to "", "Ongoing" to "ongoing", "Completed" to "completed", "Hiatus" to "hiatus", "Cancelled" to "cancelled"), onGlobalStatus)
                Row(
                    Modifier.fillMaxWidth().clickable { onLibraryOnly(!libraryOnly) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(libraryOnly, onCheckedChange = onLibraryOnly)
                    Text("In my library", modifier = Modifier.padding(start = 8.dp))
                }
                if (filters.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    Text("Source filters", style = MaterialTheme.typography.titleSmall, color = MihonPrimary)
                }
                filters.forEach { MihonNativeFilterRow(it, onChanged) }
            }
        },
        confirmButton = {},
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun MihonNativeFilterRow(filter: Filter<*>, onChanged: () -> Unit) {
    when (filter) {
        is Filter.Header -> Text(
            filter.name,
            style = MaterialTheme.typography.titleSmall,
            color = MihonPrimary,
            modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
        )
        is Filter.Separator -> HorizontalDivider(Modifier.padding(vertical = 8.dp))
        is Filter.Text -> OutlinedTextField(
            value = filter.state,
            onValueChange = { filter.state = it; onChanged() },
            label = { Text(filter.name) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
        is Filter.CheckBox -> Row(
            Modifier.fillMaxWidth().clickable { filter.state = !filter.state; onChanged() }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = filter.state, onCheckedChange = { filter.state = it; onChanged() })
            Text(filter.name, modifier = Modifier.padding(start = 8.dp))
        }
        is Filter.TriState -> {
            Text(filter.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                listOf("Any" to Filter.TriState.STATE_IGNORE, "Include" to Filter.TriState.STATE_INCLUDE, "Exclude" to Filter.TriState.STATE_EXCLUDE).forEach { (label, value) ->
                    FilterChip(
                        selected = filter.state == value,
                        onClick = { filter.state = value; onChanged() },
                        label = { Text(label) },
                    )
                }
            }
        }
        is Filter.Select<*> -> MihonSelectFilter(filter, onChanged)
        is Filter.Sort -> MihonSortFilter(filter, onChanged)
        is Filter.Group<*> -> {
            CatalogFilterSection(filter.name) {
                filter.state.filterIsInstance<Filter<*>>().forEach { nested ->
                    MihonNativeFilterRow(nested, onChanged)
                }
            }
        }
    }
}

@Composable
private fun MihonSelectFilter(filter: Filter.Select<*>, onChanged: () -> Unit) {
    CatalogFilterChoice(filter.name, filter.state.toString(), filter.values.mapIndexed { index, value -> value.toString() to index.toString() }) {
        filter.state = it.toInt()
        onChanged()
    }
}

@Composable
private fun MihonSortFilter(filter: Filter.Sort, onChanged: () -> Unit) {
    val selected = filter.state
    CatalogFilterSection(filter.name) {
    filter.values.forEachIndexed { index, value ->
        val active = selected?.index == index
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = active,
                onClick = {
                    filter.state = Filter.Sort.Selection(index, selected?.takeIf { it.index == index }?.ascending ?: true)
                    onChanged()
                },
                label = { Text(value) },
                modifier = Modifier.weight(1f),
            )
            if (active) {
                TextButton(onClick = {
                    filter.state = Filter.Sort.Selection(index, !(selected?.ascending ?: true))
                    onChanged()
                }) { Text(if (selected?.ascending == false) "Descending" else "Ascending") }
            }
        }
    }
    }
}

@Composable
private fun mangaCoverRequest(manga: MangaSearchResult): Any? {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember(context, manga.coverUrl, manga.sourceId) {
        if (manga.sourceId != "rawkuma") manga.coverUrl else coil3.request.ImageRequest.Builder(context)
            .data(manga.coverUrl)
            .httpHeaders(coil3.network.NetworkHeaders.Builder()
                .set("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36 Weaverse/1.0")
                .set("Referer", "https://rawkuma.net/")
                .build())
            .build()
    }
}

@Composable
private fun MihonMangaDetail(state: MangaSourceUiState, viewModel: MangaSourceViewModel, onEditChapter: (MangaEditRequest) -> Unit) {
    val manga = state.selected ?: return
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(16.dp)) {
                AsyncImage(mangaCoverRequest(manga), manga.title, contentScale = ContentScale.Crop, modifier = Modifier.width(112.dp).aspectRatio(0.68f).clip(RoundedCornerShape(8.dp)).background(MihonSurfaceHigh))
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text(manga.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    val creator = (manga.authors + manga.artists).distinct().joinToString()
                    if (creator.isNotBlank()) Text(creator, color = MihonMuted)
                    Text(manga.status.ifBlank { manga.type.ifBlank { "Unknown status" } }, color = MihonMuted, style = MaterialTheme.typography.bodySmall)
                    state.favoriteCategories.firstOrNull()?.let { category ->
                        val seriesId = state.favoriteSeries.firstOrNull { it.sourceId == manga.sourceId && it.remoteId == manga.remoteId }?.id
                        val saved = seriesId != null && state.favorites.any { it.seriesId == seriesId && it.categoryId == category.id }
                        Button(onClick = { viewModel.toggleFavorite(manga, category.id) }, modifier = Modifier.padding(top = 12.dp)) {
                            Icon(if (saved) Icons.Outlined.CheckCircle else Icons.Outlined.Add, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (saved) "In library" else "Add to library")
                        }
                    }
                }
            }
        }
        if (manga.score.isNotBlank()) item {
            Text("★ Score: ${manga.score}", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        if (manga.rating.isNotBlank()) item {
            Text(if (manga.rating.toDoubleOrNull() != null) "★ Score: ${manga.rating}" else "Content rating: ${manga.rating}", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        if (manga.tags.isNotEmpty()) item {
            Text(manga.tags.joinToString(" · "), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MihonPrimary)
        }
        if (manga.description.isNotBlank()) item {
            Text(manga.description, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MihonMuted, maxLines = 6, overflow = TextOverflow.Ellipsis)
        }
        item { MihonGroupHeader("${state.chapters.size} chapters") }
        lazyItems(state.chapters, key = { "${it.sourceId}:${it.remoteId}" }) { chapter ->
            val existing = state.downloads.firstOrNull { it.sourceId == chapter.sourceId && it.remoteId == chapter.remoteId }
            Row(Modifier.fillMaxWidth().clickable {
                if (existing?.status == "completed") viewModel.openReader(existing.id) else viewModel.openOnlineReader(chapter)
            }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(mihonChapterLabel(chapter), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(chapter.language.uppercase(), color = MihonMuted, style = MaterialTheme.typography.bodySmall)
                }
                when (existing?.status) {
                    "completed" -> {
                        IconButton(onClick = { onEditChapter(MangaEditRequest(existing.id)) }) { Icon(Icons.Outlined.Edit, "Edit") }
                        Icon(Icons.Outlined.CheckCircle, "Downloaded", tint = MihonPrimary)
                    }
                    "queued", "downloading" -> IconButton(onClick = { viewModel.stop(existing) }) { Icon(Icons.Outlined.GetApp, "Stop", tint = MihonPrimary) }
                    "failed", "stopped" -> IconButton(onClick = { viewModel.retry(existing) }) { Icon(Icons.Outlined.Refresh, "Retry", tint = MihonError) }
                    else -> IconButton(onClick = { viewModel.enqueue(chapter) }) { Icon(Icons.Outlined.GetApp, "Download") }
                }
            }
        }
    }
}

@Composable
private fun MihonMoreScreen(
    downloadedOnly: Boolean,
    onDownloadedOnly: (Boolean) -> Unit,
    incognito: Boolean,
    onIncognito: (Boolean) -> Unit,
    downloads: Int,
    onOpen: (MihonMorePage) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(shape = CircleShape, color = MihonPrimary, contentColor = MihonOnPrimary, modifier = Modifier.size(72.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text("み", fontSize = 38.sp, fontWeight = FontWeight.Black) }
                }
                Text("Weaverse Manga", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            }
        }
        item { MihonSwitchRow(Icons.Outlined.CloudOff, "Downloaded only", "Filters all entries in your library", downloadedOnly, onDownloadedOnly) }
        item { MihonSwitchRow(Icons.Outlined.NoAccounts, "Incognito mode", "Pauses reading history", incognito, onIncognito) }
        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { MihonPreferenceRow(Icons.Outlined.GetApp, "Download queue", if (downloads > 0) "$downloads chapters" else null) { onOpen(MihonMorePage.Downloads) } }
        item { MihonPreferenceRow(Icons.AutoMirrored.Outlined.Label, "Categories") { onOpen(MihonMorePage.Categories) } }
        item { MihonPreferenceRow(Icons.Outlined.QueryStats, "Statistics") { onOpen(MihonMorePage.Statistics) } }
        item { MihonPreferenceRow(Icons.Outlined.Storage, "Data and storage") { onOpen(MihonMorePage.Data) } }
        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { MihonPreferenceRow(Icons.Outlined.Settings, "Settings") { onOpen(MihonMorePage.Settings) } }
        item { MihonPreferenceRow(Icons.Outlined.Language, "Download from web link", "Import a public chapter URL") { onOpen(MihonMorePage.LinkDownload) } }
        item { MihonPreferenceRow(Icons.Outlined.Folder, "Storyboard projects", "Open work created from manga") { onOpen(MihonMorePage.Projects) } }
        item { MihonPreferenceRow(Icons.Outlined.Info, "About") { onOpen(MihonMorePage.About) } }
        item { MihonPreferenceRow(Icons.AutoMirrored.Outlined.HelpOutline, "Help") { } }
    }
}

@Composable
private fun MihonMoreDetail(
    page: MihonMorePage,
    state: MangaSourceUiState,
    viewModel: MangaSourceViewModel,
    onBack: () -> Unit,
    onCreateProject: () -> Unit,
    onOpenProject: (WorkShelfCard) -> Unit,
    onEditChapter: (MangaEditRequest) -> Unit,
) {
    val title = when (page) {
        MihonMorePage.Downloads -> "Download queue"
        MihonMorePage.Categories -> "Categories"
        MihonMorePage.Statistics -> "Statistics"
        MihonMorePage.Data -> "Data and storage"
        MihonMorePage.Settings -> "Settings"
        MihonMorePage.About -> "About"
        MihonMorePage.LinkDownload -> "Download from web link"
        MihonMorePage.Projects -> "Storyboard projects"
    }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
        )
        when (page) {
            MihonMorePage.Downloads -> MihonDownloadQueue(state, viewModel, onEditChapter)
            MihonMorePage.Categories -> MihonCategories(state, viewModel)
            MihonMorePage.Statistics -> MihonStatistics(state)
            MihonMorePage.Data -> MihonSimpleSettings(listOf("Storage location" to "App private storage", "Automatic backups" to "Off", "Backup and restore" to null, "Clear cache" to null))
            MihonMorePage.Settings -> MihonSimpleSettings(listOf("General" to null, "Appearance" to "Dark theme", "Library" to null, "Reader" to null, "Downloads" to null, "Browse" to null, "Tracking" to null, "Advanced" to null))
            MihonMorePage.About -> MihonAbout()
            MihonMorePage.LinkDownload -> MihonLinkDownload(state, viewModel)
            MihonMorePage.Projects -> WorkShelfScreen(
                kind = WorkShelfKind.Storyboard,
                onCreate = onCreateProject,
                onOpen = onOpenProject,
                modifier = Modifier.fillMaxSize().background(MihonBackground),
            )
        }
    }
}

@Composable
private fun MihonDownloadQueue(state: MangaSourceUiState, viewModel: MangaSourceViewModel, onEditChapter: (MangaEditRequest) -> Unit) {
    if (state.downloads.isEmpty()) {
        MihonEmptyState("( ´△｀)", "No downloads")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        lazyItems(state.downloads, key = { it.id }) { chapter ->
            MihonChapterRow(chapter, state.coverPaths[chapter.id], onClick = { if (chapter.status == "completed") viewModel.openReader(chapter.id) }) {
                Row {
                    if (chapter.status == "completed") IconButton(onClick = { onEditChapter(MangaEditRequest(chapter.id)) }) { Icon(Icons.Outlined.Edit, "Edit") }
                    MihonDownloadAction(chapter, viewModel)
                    IconButton(onClick = { viewModel.deleteDownloadedSeries(chapter) }) { Icon(Icons.Outlined.Delete, "Delete") }
                }
            }
        }
    }
}

@Composable
private fun MihonCategories(state: MangaSourceUiState, viewModel: MangaSourceViewModel) {
    LazyColumn(Modifier.fillMaxSize()) {
        lazyItems(state.favoriteCategories, key = { it.id }) { category ->
            MihonPreferenceRow(Icons.AutoMirrored.Outlined.Label, category.name, "${state.favorites.count { it.categoryId == category.id }} manga") { }
        }
        item {
            OutlinedTextField(
                value = state.favoriteCategoryName,
                onValueChange = viewModel::setFavoriteCategoryName,
                label = { Text("Category name") },
                trailingIcon = { IconButton(onClick = viewModel::createFavoriteCategory, enabled = state.favoriteCategoryName.isNotBlank()) { Icon(Icons.Outlined.Add, "Add") } },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                singleLine = true,
            )
        }
    }
}

@Composable
private fun MihonStatistics(state: MangaSourceUiState) {
    val completed = state.downloads.count { it.status == "completed" }
    val pages = state.downloads.sumOf { it.pageCount }
    LazyColumn(Modifier.fillMaxSize()) {
        item { MihonGroupHeader("Overview") }
        item { MihonStatRow("Manga in library", state.favoriteSeries.size + state.downloads.distinctBy { it.mangaId }.size) }
        item { MihonStatRow("Downloaded chapters", completed) }
        item { MihonStatRow("Downloaded pages", pages) }
        item { MihonStatRow("Sources", state.sources.size) }
    }
}

@Composable
private fun MihonSimpleSettings(items: List<Pair<String, String?>>) {
    LazyColumn(Modifier.fillMaxSize()) {
        lazyItems(items) { (title, subtitle) -> MihonPreferenceRow(Icons.Outlined.Settings, title, subtitle) { } }
    }
}

@Composable
private fun MihonAbout() {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(shape = CircleShape, color = MihonPrimary, contentColor = MihonOnPrimary, modifier = Modifier.size(96.dp)) {
            Box(contentAlignment = Alignment.Center) { Text("み", fontSize = 52.sp, fontWeight = FontWeight.Black) }
        }
        Text("Weaverse Manga", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 18.dp))
        Text("Library, extensions, downloads, and reader", color = MihonMuted)
        Text("Weaverse ${com.ihy2ln.weaverse.BuildConfig.VERSION_NAME}", color = MihonMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun MihonLinkDownload(state: MangaSourceUiState, viewModel: MangaSourceViewModel) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Paste a public chapter URL you are permitted to use. The ordered images exposed by the page will be downloaded.", color = MihonMuted)
        OutlinedTextField(state.link, viewModel::setLink, label = { Text("Chapter URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = viewModel::previewLink, enabled = !state.busy && state.link.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Preview") }
        state.linkPreview?.let { preview ->
            Surface(shape = RoundedCornerShape(16.dp), color = MihonSurfaceHigh) {
                Column(Modifier.padding(16.dp)) {
                    Text(preview.title, fontWeight = FontWeight.SemiBold)
                    Text("${preview.pages.size} ordered pages found", color = MihonMuted)
                    Row { Button(onClick = viewModel::confirmLinkDownload) { Text("Download") }; TextButton(onClick = viewModel::clearLinkPreview) { Text("Cancel") } }
                }
            }
        }
    }
}

@Composable
private fun MihonSwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChecked(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MihonMuted, modifier = Modifier.size(26.dp))
        Column(Modifier.weight(1f).padding(horizontal = 18.dp)) { Text(title); Text(subtitle, color = MihonMuted, style = MaterialTheme.typography.bodySmall) }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun MihonPreferenceRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MihonMuted, modifier = Modifier.size(26.dp))
        Column(Modifier.weight(1f).padding(horizontal = 18.dp)) {
            Text(title)
            if (!subtitle.isNullOrBlank()) Text(subtitle, color = MihonMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke() ?: Icon(Icons.AutoMirrored.Outlined.NavigateNext, null, tint = MihonMuted)
    }
}

@Composable
private fun MihonChapterRow(
    chapter: MangaChapterEntity,
    cover: String?,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(cover, chapter.mangaTitle, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp, 68.dp).clip(RoundedCornerShape(5.dp)).background(MihonSurfaceHigh))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(chapter.mangaTitle.ifBlank { chapter.title }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(chapter.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MihonMuted, style = MaterialTheme.typography.bodySmall)
            Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(chapter.downloadedAt ?: chapter.updatedAt)), color = MihonMuted, style = MaterialTheme.typography.labelSmall)
        }
        trailing()
    }
}

@Composable
private fun MihonDownloadAction(chapter: MangaChapterEntity, viewModel: MangaSourceViewModel) {
    when (chapter.status) {
        "completed" -> Icon(Icons.Outlined.CheckCircle, "Downloaded", tint = MihonPrimary)
        "queued", "downloading" -> IconButton(onClick = { viewModel.stop(chapter) }) { Icon(Icons.Outlined.GetApp, "Stop", tint = MihonPrimary) }
        "failed", "stopped" -> IconButton(onClick = { viewModel.retry(chapter) }) { Icon(Icons.Outlined.ErrorOutline, "Retry", tint = MihonError) }
        else -> Icon(Icons.Outlined.GetApp, "Queued", tint = MihonMuted)
    }
}

@Composable
private fun MihonGroupHeader(text: String) {
    Text(text, color = MihonPrimary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp))
}

@Composable
private fun MihonStatRow(label: String, value: Int) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 15.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text("$value", color = MihonPrimary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MihonEmptyState(face: String, message: String, action: String? = null, onAction: () -> Unit = {}) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text(face, fontSize = 52.sp, color = MihonMuted)
            Text(message, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 18.dp))
            if (action != null) TextButton(onClick = onAction, modifier = Modifier.padding(top = 24.dp)) { Text(action) }
        }
    }
}

private fun MangaSeriesEntity.toMihonSearchResult() = MangaSearchResult(
    sourceId = sourceId,
    remoteId = remoteId,
    title = title,
    description = description,
    coverUrl = coverUrl,
    canonicalUrl = canonicalUrl,
)

private fun mihonChapterLabel(chapter: MangaChapter): String = buildString {
    if (chapter.volume.isNotBlank()) append("Vol. ${chapter.volume} ")
    if (chapter.chapterNumber.isNotBlank()) append("Ch. ${chapter.chapterNumber}")
    if (isBlank()) append(chapter.title) else if (chapter.title.isNotBlank()) append(" — ${chapter.title}")
}
