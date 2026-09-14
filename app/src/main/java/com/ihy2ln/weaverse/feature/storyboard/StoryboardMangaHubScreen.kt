package com.ihy2ln.weaverse.feature.storyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.ihy2ln.weaverse.BuildConfig
import com.ihy2ln.weaverse.core.manga.MangaBrowseMode
import com.ihy2ln.weaverse.core.manga.MangaChapter
import com.ihy2ln.weaverse.core.manga.MangaSearchResult
import com.ihy2ln.weaverse.core.manga.MangaSourceDescriptor
import com.ihy2ln.weaverse.core.manga.MangaWebsite
import com.ihy2ln.weaverse.data.db.entities.MangaChapterEntity
import com.ihy2ln.weaverse.data.db.entities.MangaSeriesEntity
import com.ihy2ln.weaverse.feature.library.WorkShelfCard
import com.ihy2ln.weaverse.feature.library.WorkShelfKind
import com.ihy2ln.weaverse.feature.library.WorkShelfScreen
import java.io.File

private enum class MangaHubTab(val label: String) {
    Library("Library"), Browse("Browse"), Downloads("Downloads"), Extensions("Extensions"), Projects("Projects")
}

private val HubBlack = Color(0xFF101012)
private val HubPanel = Color(0xFF1B1B1F)
private val HubText = Color(0xFFF4F1F5)
private val HubMuted = Color(0xFFB7B1BC)
private val HubAccent = Color(0xFF92A8FF)
private val HubColors = darkColorScheme(
    primary = HubAccent,
    onPrimary = HubBlack,
    background = HubBlack,
    onBackground = HubText,
    surface = HubBlack,
    onSurface = HubText,
    surfaceVariant = HubPanel,
    onSurfaceVariant = HubMuted,
)

data class MangaEditRequest(
    val chapterId: String,
    val pageIndex: Int? = null,
    val returnToReader: Boolean = false,
    val action: MangaReaderAction = MangaReaderAction.EditPage,
)

data class MangaReaderReturnTarget(
    val chapterId: String,
    val pageIndex: Int,
)

private data class HubLibraryItem(
    val key: String,
    val title: String,
    val coverModel: Any?,
    val subtitle: String,
    val chapters: List<MangaChapterEntity>,
    val favorite: MangaSearchResult?,
    val remoteId: String,
    val sourceId: String,
)

@Composable
fun StoryboardMangaHubScreen(
    onCreateProject: () -> Unit,
    onOpenProject: (WorkShelfCard) -> Unit,
    onEditChapter: (MangaEditRequest) -> Unit,
    readerReturnTarget: MangaReaderReturnTarget? = null,
    onReaderReturnConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MangaSourceViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var tabName by rememberSaveable { mutableStateOf(MangaHubTab.Library.name) }
    val tab = runCatching { MangaHubTab.valueOf(tabName) }.getOrDefault(MangaHubTab.Library)

    LaunchedEffect(readerReturnTarget) {
        readerReturnTarget?.let {
            viewModel.openReader(it.chapterId, it.pageIndex)
            onReaderReturnConsumed()
        }
    }

    state.readerChapter?.let { chapter ->
        MangaChapterReader(
            chapter = chapter,
            pagePaths = state.readerPagePaths,
            onDismiss = viewModel::closeReader,
            initialPageIndex = state.readerPageIndex,
            onAction = { action, chapterId, pageIndex ->
                viewModel.closeReader()
                onEditChapter(MangaEditRequest(chapterId, pageIndex, returnToReader = true, action = action))
            },
        )
        return
    }

    MaterialTheme(colorScheme = HubColors) {
        Surface(modifier = modifier.fillMaxSize(), color = HubBlack) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(tab.label, style = MaterialTheme.typography.titleLarge, color = HubText, fontWeight = FontWeight.Bold)
                    Text("Build ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall, color = HubMuted)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MangaHubTab.entries.forEach { item ->
                        FilterChip(
                            selected = tab == item,
                            onClick = { tabName = item.name },
                            label = { Text(item.label) },
                        )
                    }
                }
                if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                if (state.status.isNotBlank()) {
                    Text(
                        state.status,
                        color = HubAccent,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                when (tab) {
                    MangaHubTab.Library -> HubLibrary(
                        state = state,
                        viewModel = viewModel,
                        onEditChapter = onEditChapter,
                        onGoToBrowse = { tabName = MangaHubTab.Browse.name },
                        onOpenFavorite = { manga ->
                            tabName = MangaHubTab.Browse.name
                            viewModel.selectSource(manga.sourceId)
                            viewModel.select(manga)
                        },
                    )
                    MangaHubTab.Browse -> HubBrowse(state, viewModel, onEditChapter)
                    MangaHubTab.Downloads -> HubDownloads(state, viewModel, onEditChapter)
                    MangaHubTab.Extensions -> HubExtensions(
                        state = state,
                        viewModel = viewModel,
                        onOpenSource = { sourceId ->
                            tabName = MangaHubTab.Browse.name
                            viewModel.openSource(sourceId, MangaBrowseMode.Popular)
                        },
                    )
                    MangaHubTab.Projects -> WorkShelfScreen(
                        kind = WorkShelfKind.Storyboard,
                        onCreate = onCreateProject,
                        onOpen = onOpenProject,
                        modifier = Modifier.fillMaxSize().background(HubBlack),
                    )
                }
            }
        }
    }
}

@Composable
private fun HubLibrary(
    state: MangaSourceUiState,
    viewModel: MangaSourceViewModel,
    onEditChapter: (MangaEditRequest) -> Unit,
    onGoToBrowse: () -> Unit,
    onOpenFavorite: (MangaSearchResult) -> Unit,
) {
    var categoryId by rememberSaveable { mutableStateOf("all") }
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val items = remember(state.downloads, state.favoriteSeries, state.favorites, state.coverPaths, categoryId) {
        libraryItems(state, categoryId)
    }
    val selected = items.firstOrNull { it.key == selectedKey }

    if (selected != null) {
        HubLibrarySeries(
            item = selected,
            state = state,
            viewModel = viewModel,
            onBack = { selectedKey = null },
            onEditChapter = onEditChapter,
            onOpenFavorite = onOpenFavorite,
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip(categoryId == "all", { categoryId = "all" }, { Text("All") })
            state.favoriteCategories.forEach { category ->
                FilterChip(categoryId == category.id, { categoryId = category.id }, { Text(category.name) })
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.favoriteCategoryName,
                onValueChange = viewModel::setFavoriteCategoryName,
                label = { Text("New category") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = viewModel::createFavoriteCategory, enabled = state.favoriteCategoryName.isNotBlank()) { Text("Add") }
        }
        if (items.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your library is empty", color = HubText, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = onGoToBrowse) { Text("Browse", color = HubAccent) }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 10.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items, key = { it.key }) { item ->
                    HubCoverCard(
                        title = item.title,
                        coverModel = item.coverModel,
                        subtitle = item.subtitle,
                        onClick = { selectedKey = item.key },
                    )
                }
            }
        }
    }
}

@Composable
private fun HubLibrarySeries(
    item: HubLibraryItem,
    state: MangaSourceUiState,
    viewModel: MangaSourceViewModel,
    onBack: () -> Unit,
    onEditChapter: (MangaEditRequest) -> Unit,
    onOpenFavorite: (MangaSearchResult) -> Unit,
) {
    LaunchedEffect(item.key) {
        item.favorite?.let(viewModel::select)
    }
    val remoteMatches = state.selected?.sourceId == item.sourceId &&
        (state.selected?.remoteId == item.remoteId || state.selected?.title.equals(item.title, true))
    val remoteChapters = if (remoteMatches) state.chapters else emptyList()
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) { Text("Back", color = HubAccent) }
            Text(item.title, color = HubText, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (item.favorite != null) {
                TextButton(onClick = { onOpenFavorite(item.favorite) }) { Text("Source", color = HubAccent) }
            }
        }
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (remoteChapters.isNotEmpty()) {
                listItems(remoteChapters, key = { it.remoteId }) { chapter ->
                    HubChapterRow(chapter, state, viewModel, onEditChapter)
                }
            } else {
                listItems(item.chapters, key = { it.id }) { chapter ->
                    HubDownloadRow(chapter, viewModel, onEditChapter)
                }
            }
            if (remoteChapters.isEmpty() && item.chapters.isEmpty()) {
                item { Text("No chapters yet.", color = HubMuted) }
            }
        }
    }
}

private fun libraryItems(state: MangaSourceUiState, categoryId: String): List<HubLibraryItem> {
    val grouped = state.downloads.groupBy { it.mangaId.ifBlank { it.mangaTitle } }
    fun coverFor(chapters: List<MangaChapterEntity>, favorite: MangaSeriesEntity?): Any? {
        val local = chapters.firstNotNullOfOrNull { chapter ->
            state.coverPaths[chapter.id]?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf(File::isFile)
        }
        return local ?: favorite?.coverUrl?.takeIf { it.isNotBlank() }
    }
    if (categoryId != "all") {
        val favoriteIds = state.favorites.filter { it.categoryId == categoryId }.mapTo(hashSetOf()) { it.seriesId }
        return state.favoriteSeries.filter { it.id in favoriteIds }.map { series ->
            val chapters = grouped.values.firstOrNull { chapters ->
                chapters.any { it.sourceId == series.sourceId && (it.mangaId == series.remoteId || it.mangaTitle.equals(series.title, true)) }
            }.orEmpty()
            HubLibraryItem(
                key = series.id,
                title = series.title,
                coverModel = coverFor(chapters, series),
                subtitle = if (chapters.isEmpty()) series.sourceId else "${chapters.size} ch",
                chapters = chapters.sortedByDescending { it.updatedAt },
                favorite = series.toSearchResult(),
                remoteId = series.remoteId,
                sourceId = series.sourceId,
            )
        }
    }
    val fromDownloads = grouped.map { (key, chapters) ->
        val favorite = state.favoriteSeries.firstOrNull { series ->
            chapters.any { it.sourceId == series.sourceId && (it.mangaId == series.remoteId || it.mangaTitle.equals(series.title, true)) }
        }
        HubLibraryItem(
            key = "dl-$key",
            title = chapters.first().mangaTitle,
            coverModel = coverFor(chapters, favorite),
            subtitle = "${chapters.size} ch",
            chapters = chapters.sortedByDescending { it.updatedAt },
            favorite = favorite?.toSearchResult(),
            remoteId = favorite?.remoteId ?: chapters.first().mangaId,
            sourceId = favorite?.sourceId ?: chapters.first().sourceId,
        )
    }
    val downloadTitles = fromDownloads.map { it.title.lowercase() }.toHashSet()
    val extraFavorites = state.favoriteSeries.filter { it.title.lowercase() !in downloadTitles }.map { series ->
        HubLibraryItem(
            key = series.id,
            title = series.title,
            coverModel = series.coverUrl.takeIf { it.isNotBlank() },
            subtitle = series.sourceId,
            chapters = emptyList(),
            favorite = series.toSearchResult(),
            remoteId = series.remoteId,
            sourceId = series.sourceId,
        )
    }
    return fromDownloads + extraFavorites
}

@Composable
private fun HubBrowse(
    state: MangaSourceUiState,
    viewModel: MangaSourceViewModel,
    onEditChapter: (MangaEditRequest) -> Unit,
) {
    when {
        state.selected != null -> HubSeriesDetail(state, viewModel, onEditChapter)
        !state.browseShowingSources -> HubCatalog(state, viewModel)
        else -> HubSourceList(state, viewModel)
    }
}

@Composable
private fun HubSourceList(state: MangaSourceUiState, viewModel: MangaSourceViewModel) {
    var showWebLink by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listItems(state.sources, key = { it.id }) { source ->
            HubSourceRow(
                source = source,
                onOpen = { viewModel.openSource(source.id, MangaBrowseMode.Popular) },
                onLatest = { viewModel.openSource(source.id, MangaBrowseMode.Latest) },
            )
        }
        item {
            TextButton(onClick = { showWebLink = !showWebLink }) {
                Text(if (showWebLink) "Hide web link" else "Download from web link", color = HubAccent)
            }
        }
        if (showWebLink) {
            item { HubWebLinkCard(state, viewModel) }
        }
    }
}

@Composable
private fun HubCatalog(state: MangaSourceUiState, viewModel: MangaSourceViewModel) {
    val source = state.sources.firstOrNull { it.id == state.activeSourceId }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = viewModel::showSourceList) { Text("Sources", color = HubAccent) }
            Text(
                source?.name ?: "Catalog",
                color = HubText,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip(
                selected = state.browseMode == MangaBrowseMode.Popular,
                onClick = { viewModel.browse(MangaBrowseMode.Popular) },
                label = { Text("Popular") },
            )
            FilterChip(
                selected = state.browseMode == MangaBrowseMode.Latest,
                onClick = { viewModel.browse(MangaBrowseMode.Latest) },
                label = { Text("Latest") },
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                label = { Text("Search") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = viewModel::search, enabled = !state.busy) { Text("Search") }
        }
        if (state.results.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(if (state.busy) "Loading…" else "No titles", color = HubMuted)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 10.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.results, key = { "${it.sourceId}:${it.remoteId}" }) { result ->
                    HubCoverCard(title = result.title, coverModel = result.coverUrl, subtitle = null) {
                        viewModel.select(result)
                    }
                }
            }
        }
    }
}

@Composable
private fun HubSeriesDetail(
    state: MangaSourceUiState,
    viewModel: MangaSourceViewModel,
    onEditChapter: (MangaEditRequest) -> Unit,
) {
    val selected = state.selected ?: return
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = viewModel::clearSelection) { Text("Back", color = HubAccent) }
            Text(selected.title, color = HubText, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        if (selected.description.isNotBlank()) {
            Text(selected.description, color = HubMuted, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        if (state.favoriteCategories.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.favoriteCategories.forEach { category ->
                    val seriesId = state.favoriteSeries.firstOrNull {
                        it.sourceId == selected.sourceId && it.remoteId == selected.remoteId
                    }?.id
                    val saved = seriesId != null && state.favorites.any { it.seriesId == seriesId && it.categoryId == category.id }
                    FilterChip(
                        selected = saved,
                        onClick = { viewModel.toggleFavorite(selected, category.id) },
                        label = { Text(if (saved) category.name else "+ ${category.name}") },
                    )
                }
            }
        }
        if (!state.busy && state.chapters.isEmpty()) {
            Text("No chapters.", color = HubMuted, style = MaterialTheme.typography.bodySmall)
        }
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listItems(state.chapters, key = { it.remoteId }) { chapter ->
                HubChapterRow(chapter, state, viewModel, onEditChapter)
            }
        }
    }
}

@Composable
private fun HubWebLinkCard(state: MangaSourceUiState, viewModel: MangaSourceViewModel) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(HubPanel).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(state.link, viewModel::setLink, label = { Text("Chapter URL") }, singleLine = true, modifier = Modifier.weight(1f))
            Button(onClick = viewModel::previewLink, enabled = !state.busy) { Text("Preview") }
        }
        state.linkPreview?.let { preview ->
            Text(preview.title, color = HubText, style = MaterialTheme.typography.titleSmall)
            Text("${preview.pages.size} pages", color = HubAccent)
            Row {
                Button(onClick = viewModel::confirmLinkDownload) { Text("Download") }
                TextButton(onClick = viewModel::clearLinkPreview) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun HubDownloads(
    state: MangaSourceUiState,
    viewModel: MangaSourceViewModel,
    onEditChapter: (MangaEditRequest) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.downloads.isEmpty()) item { Text("No downloads.", color = HubMuted) }
        listItems(state.downloads, key = { it.id }) { chapter ->
            HubDownloadRow(chapter, viewModel, onEditChapter)
        }
    }
}

@Composable
private fun HubDownloadRow(
    chapter: MangaChapterEntity,
    viewModel: MangaSourceViewModel,
    onEditChapter: (MangaEditRequest) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(HubPanel).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(chapter.mangaTitle, color = HubText, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${chapter.title} · ${chapter.status} · ${chapter.progress}%", color = HubMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            when (chapter.status) {
                "completed" -> Row {
                    TextButton(onClick = { viewModel.openReader(chapter.id) }) { Text("Read") }
                    TextButton(onClick = { onEditChapter(MangaEditRequest(chapter.id)) }) { Text("Edit") }
                }
                "queued", "downloading" -> TextButton(onClick = { viewModel.stop(chapter) }) { Text("Stop") }
                "failed", "stopped" -> TextButton(onClick = { viewModel.retry(chapter) }) { Text("Retry") }
            }
        }
        if (chapter.status == "downloading" || chapter.status == "queued") {
            LinearProgressIndicator(progress = { chapter.progress / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        }
    }
}

@Composable
private fun HubExtensions(
    state: MangaSourceUiState,
    viewModel: MangaSourceViewModel,
    onOpenSource: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { Text("Installed", color = HubMuted, style = MaterialTheme.typography.labelLarge) }
        listItems(state.sources, key = { it.id }) { source ->
            HubSourceRow(
                source = source,
                status = "Installed",
                onOpen = { onOpenSource(source.id) },
                onLatest = { onOpenSource(source.id) },
                latestLabel = "Open",
            )
        }
        if (state.websites.isNotEmpty()) {
            item { Text("Saved links", color = HubMuted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp)) }
            listItems(state.websites, key = { it.id }) { site ->
                HubWebsiteRow(site, viewModel)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedTextField(state.websiteName, viewModel::setWebsiteName, label = { Text("Website name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(state.websiteUrl, viewModel::setWebsiteUrl, label = { Text("Website URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = viewModel::addWebsite, enabled = state.websiteName.isNotBlank() && state.websiteUrl.isNotBlank()) { Text("Save link") }
            }
        }
    }
}

@Composable
private fun HubWebsiteRow(site: MangaWebsite, viewModel: MangaSourceViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(HubPanel).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(site.name, color = HubText)
            Text(site.url, color = HubMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (!site.builtIn) TextButton(onClick = { viewModel.removeWebsite(site) }) { Text("Remove") }
    }
}

@Composable
private fun HubSourceRow(
    source: MangaSourceDescriptor,
    onOpen: () -> Unit,
    onLatest: () -> Unit,
    status: String? = null,
    latestLabel: String = "Latest",
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(HubPanel).clickable(onClick = onOpen).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(HubBlack),
            contentAlignment = Alignment.Center,
        ) {
            Text(source.name.take(1), color = HubAccent, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(source.name, color = HubText, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                "${source.language.uppercase()} · ${source.kind}",
                color = HubMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
            if (status != null) {
                Text(status, color = HubMuted, style = MaterialTheme.typography.labelSmall)
            }
        }
        TextButton(onClick = onLatest) { Text(latestLabel, color = HubAccent) }
    }
}

@Composable
private fun HubCoverCard(title: String, coverModel: Any?, subtitle: String?, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp)).background(HubPanel),
            contentAlignment = Alignment.Center,
        ) {
            if (coverModel != null) {
                AsyncImage(
                    model = coverModel,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(title.take(1), color = HubMuted, style = MaterialTheme.typography.headlineSmall)
            }
        }
        Text(
            title,
            color = HubText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, color = HubMuted, maxLines = 1, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun MangaSeriesEntity.toSearchResult(): MangaSearchResult = MangaSearchResult(
    sourceId = sourceId,
    remoteId = remoteId,
    title = title,
    description = description,
    coverUrl = coverUrl.takeIf(String::isNotBlank),
    canonicalUrl = canonicalUrl,
)

@Composable
private fun HubChapterRow(
    chapter: MangaChapter,
    state: MangaSourceUiState,
    viewModel: MangaSourceViewModel,
    onEditChapter: (MangaEditRequest) -> Unit,
) {
    val existing = state.downloads.firstOrNull { it.sourceId == chapter.sourceId && it.remoteId == chapter.remoteId }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(HubPanel).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(chapter.title.ifBlank { "Chapter ${chapter.chapterNumber}" }, color = HubText, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val number = chapter.chapterNumber.takeIf { it.isNotBlank() }?.let { "Ch. $it · " }.orEmpty()
            Text("$number${existing?.status ?: "not downloaded"}", color = HubMuted, style = MaterialTheme.typography.labelSmall)
        }
        when (existing?.status) {
            "completed" -> Row {
                TextButton(onClick = { viewModel.openReader(existing.id) }) { Text("Read") }
                TextButton(onClick = { onEditChapter(MangaEditRequest(existing.id)) }) { Text("Edit") }
            }
            "queued", "downloading" -> TextButton(onClick = { viewModel.stop(existing) }) { Text("Stop") }
            "failed", "stopped" -> TextButton(onClick = { viewModel.retry(existing) }) { Text("Retry") }
            else -> TextButton(onClick = { viewModel.enqueue(chapter) }) { Text("Download") }
        }
    }
}
