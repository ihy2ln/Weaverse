package com.ihy2ln.weaverse.feature.storyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.BuildConfig
import com.ihy2ln.weaverse.core.manga.MangaBrowseMode
import com.ihy2ln.weaverse.core.manga.MangaSearchResult
import com.ihy2ln.weaverse.data.db.entities.MangaChapterEntity
import com.ihy2ln.weaverse.data.db.entities.MangaSeriesEntity
import com.ihy2ln.weaverse.feature.library.WorkShelfCard
import com.ihy2ln.weaverse.feature.library.WorkShelfKind
import com.ihy2ln.weaverse.feature.library.WorkShelfScreen
import java.io.File
import coil3.compose.AsyncImage

private enum class MangaHubTab(val label: String) {
    Library("Library"), Browse("Browse"), Downloads("Downloads"), Extensions("Extensions"), Projects("Projects")
}

private val HubBlack: Color @androidx.compose.runtime.Composable get() = com.ihy2ln.weaverse.core.ui.theme.inkTokens().background
private val HubPanel: Color @androidx.compose.runtime.Composable get() = com.ihy2ln.weaverse.core.ui.theme.inkTokens().panel
private val HubText: Color @androidx.compose.runtime.Composable get() = com.ihy2ln.weaverse.core.ui.theme.inkTokens().primaryText
private val HubMuted: Color @androidx.compose.runtime.Composable get() = com.ihy2ln.weaverse.core.ui.theme.inkTokens().secondaryText
private val HubAccent: Color @androidx.compose.runtime.Composable get() = com.ihy2ln.weaverse.core.ui.theme.inkTokens().activePill
private val HubColors = com.ihy2ln.weaverse.core.ui.theme.StreamingColors

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

@Composable
fun StoryboardMangaHubScreen(
    initialTab: String = "Library",
    initialSeriesId: String? = null,
    onCreateProject: () -> Unit,
    onOpenProject: (WorkShelfCard) -> Unit,
    onEditChapter: (MangaEditRequest) -> Unit,
    readerReturnTarget: MangaReaderReturnTarget? = null,
    onReaderReturnConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MangaSourceViewModel = hiltViewModel(),
) {
    LaunchedEffect(initialSeriesId) { initialSeriesId?.let(viewModel::openRecentSeries) }
    val state by viewModel.uiState.collectAsState()
    var tabName by rememberSaveable { mutableStateOf(initialTab) }
    val tab = runCatching { MangaHubTab.valueOf(tabName) }.getOrDefault(MangaHubTab.Library)

    LaunchedEffect(initialTab) {
        tabName = runCatching { MangaHubTab.valueOf(initialTab).name }.getOrDefault(MangaHubTab.Library.name)
    }

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
            online = state.readerOnline,
            onPageChanged = { page -> viewModel.recordReaderPage(chapter.id, page, state.readerPagePaths.size) },
            onAction = { action, chapterId, pageIndex ->
                viewModel.closeReader()
                onEditChapter(MangaEditRequest(chapterId, pageIndex, returnToReader = true, action = action))
            },
        )
        return
    }

    MihonMangaHome(
        initialTab = tab.name,
        state = state,
        viewModel = viewModel,
        onCreateProject = onCreateProject,
        onOpenProject = onOpenProject,
        onEditChapter = onEditChapter,
        modifier = modifier,
    )
}

@Composable
private fun HubLibrary(
    state: MangaSourceUiState,
    viewModel: MangaSourceViewModel,
    onEditChapter: (MangaEditRequest) -> Unit,
    onOpenFavorite: (MangaSearchResult) -> Unit,
) {
    var categoryId by rememberSaveable { mutableStateOf("downloads") }
    // Long-press target for the cover action sheet. Held as the entity rather than an id so
    // the sheet keeps showing the right title even if the download list refreshes underneath.
    var actionChapter by remember { mutableStateOf<MangaChapterEntity?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val series = remember(state.downloads) {
        state.downloads
            .groupBy { it.mangaId.ifBlank { it.mangaTitle } }
            .values
            .map { chapters -> chapters.firstOrNull { it.status == "completed" } ?: chapters.first() }
    }
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Offline library", color = HubText, style = MaterialTheme.typography.titleLarge)
            Text("${series.size} offline · ${state.favoriteSeries.size} saved", color = HubMuted, style = MaterialTheme.typography.labelMedium)
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip(categoryId == "downloads", { categoryId = "downloads" }, { Text("Downloads") })
            state.favoriteCategories.forEach { category ->
                FilterChip(categoryId == category.id, { categoryId = category.id }, { Text(category.name) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = state.favoriteCategoryName,
                onValueChange = viewModel::setFavoriteCategoryName,
                label = { Text("New favorite section") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = viewModel::createFavoriteCategory, enabled = state.favoriteCategoryName.isNotBlank()) { Text("Add") }
        }
        val favoriteIds = state.favorites.filter { it.categoryId == categoryId }.mapTo(hashSetOf()) { it.seriesId }
        val favorites = state.favoriteSeries.filter { it.id in favoriteIds }.map(MangaSeriesEntity::toSearchResult)
        if (categoryId == "downloads" && series.isEmpty() || categoryId != "downloads" && favorites.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (categoryId == "downloads") "Download a chapter or import CBZ/PDF/images to build your library."
                    else "No titles in this favorite section yet.",
                    color = HubMuted,
                )
            }
        } else if (categoryId == "downloads") {
            MangaLibraryCoverGrid(
                downloads = series,
                coverPaths = state.coverPaths,
                onSelectChapter = viewModel::openReader,
                onEditChapter = { chapterId -> onEditChapter(MangaEditRequest(chapterId)) },
                onLongPressChapter = { chapter ->
                    confirmDelete = false
                    actionChapter = chapter
                },
                compact = false,
                modifier = Modifier.fillMaxSize().padding(top = 10.dp),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(145.dp),
                modifier = Modifier.fillMaxSize().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(favorites, key = { "${it.sourceId}:${it.remoteId}" }) { favorite ->
                    HubSearchCover(favorite) { onOpenFavorite(favorite) }
                }
            }
        }
    }

    actionChapter?.let { chapter ->
        val dismiss = {
            actionChapter = null
            confirmDelete = false
        }
        AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(chapter.mangaTitle.ifBlank { chapter.title }) },
            text = {
                Column {
                    if (confirmDelete) {
                        Text("Delete this title and every downloaded page file for it? Favourites and the source stay untouched.")
                    } else {
                        Text(
                            "${chapter.pageCount} downloaded page(s)",
                            style = MaterialTheme.typography.labelMedium,
                            color = HubMuted,
                        )
                        TextButton(
                            onClick = {
                                viewModel.favoriteDownloadedSeries(chapter)
                                dismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Favorite") }
                        TextButton(
                            onClick = {
                                val copied = chapter.canonicalUrl.ifBlank { chapter.mangaTitle }
                                clipboard.setText(AnnotatedString(copied))
                                viewModel.setStatus("Copied $copied")
                                dismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Copy link") }
                        TextButton(
                            onClick = { confirmDelete = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Delete download", color = MaterialTheme.colorScheme.error) }
                    }
                }
            },
            confirmButton = {
                if (confirmDelete) {
                    TextButton(onClick = {
                        viewModel.deleteDownloadedSeries(chapter)
                        dismiss()
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
            },
            dismissButton = {
                TextButton(onClick = dismiss) { Text(if (confirmDelete) "Cancel" else "Close") }
            },
        )
    }
}

@Composable
private fun HubBrowse(state: MangaSourceUiState, viewModel: MangaSourceViewModel) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Sources", color = HubText, style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.sources.forEach { source ->
                    FilterChip(
                        selected = state.activeSourceId == source.id,
                        onClick = { viewModel.selectSource(source.id) },
                        label = { Text(source.name) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    label = { Text("Search title") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = viewModel::search, enabled = !state.busy) { Text("Search") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { viewModel.browse(MangaBrowseMode.Popular) }, enabled = !state.busy) { Text("Popular") }
                TextButton(onClick = { viewModel.browse(MangaBrowseMode.Latest) }, enabled = !state.busy) { Text("Latest") }
            }
        }
        item {
            Text("Download from web link", color = HubText, style = MaterialTheme.typography.titleMedium)
            Text(
                "Paste a public chapter URL you are permitted to use. Weaverse downloads the ordered images that the page already exposes.",
                color = HubMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(state.link, viewModel::setLink, label = { Text("Chapter URL") }, singleLine = true, modifier = Modifier.weight(1f))
                Button(onClick = viewModel::previewLink, enabled = !state.busy) { Text("Preview") }
            }
            state.linkPreview?.let { preview ->
                Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(HubPanel).padding(12.dp)) {
                    Text(preview.title, color = HubText, style = MaterialTheme.typography.titleSmall)
                    Text("${preview.pages.size} ordered pages found", color = HubAccent)
                    Row {
                        Button(onClick = viewModel::confirmLinkDownload) { Text("Download") }
                        TextButton(onClick = viewModel::clearLinkPreview) { Text("Cancel") }
                    }
                }
            }
        }
        if (state.selected == null && state.results.isNotEmpty()) {
            item {
                val gridState = rememberLazyGridState()
                // Ask for the next page a few rows before the last cover scrolls into view,
                // so the grid keeps going instead of stopping dead at the first 20 results.
                LaunchedEffect(gridState, state.results.size, state.canLoadMore) {
                    snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                        .collect { lastVisible ->
                            if (lastVisible >= state.results.lastIndex - 3) viewModel.loadMoreResults()
                        }
                }
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(145.dp),
                    modifier = Modifier.fillMaxWidth().height(420.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.results, key = { "${it.sourceId}:${it.remoteId}" }) { result ->
                        HubSearchCover(result) { viewModel.select(result) }
                    }
                }
            }
            item {
                when {
                    state.loadingMore -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    state.canLoadMore -> TextButton(onClick = viewModel::loadMoreResults) { Text("Load more") }
                    else -> Text(
                        "${state.results.size} titles · end of catalog",
                        color = HubMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        state.selected?.let { selected ->
            item {
                Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(HubPanel).padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(selected.title, color = HubText, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 2)
                        TextButton(onClick = viewModel::clearSelection) { Text("Back") }
                    }
                    if (selected.description.isNotBlank()) {
                        Text(selected.description, color = HubMuted, style = MaterialTheme.typography.bodySmall, maxLines = 4)
                    }
                    state.chapters.forEach { chapter ->
                        val existing = state.downloads.firstOrNull { it.sourceId == chapter.sourceId && it.remoteId == chapter.remoteId }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(chapterLabel(chapter), color = HubText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 2)
                            when (existing?.status) {
                                "completed" -> TextButton(onClick = { viewModel.openReader(existing.id) }) { Text("Read") }
                                "downloading", "queued" -> TextButton(onClick = { viewModel.stop(existing) }) { Text("Stop") }
                                "failed", "stopped" -> TextButton(onClick = { viewModel.retry(existing) }) { Text("Retry") }
                                // Not downloaded: read it streaming, or download it to unlock editing.
                                else -> Row {
                                    TextButton(onClick = { viewModel.openOnlineReader(chapter) }, enabled = !state.busy) { Text("Read") }
                                    TextButton(onClick = { viewModel.enqueue(chapter) }) { Text("Download") }
                                }
                            }
                        }
                    }
                }
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
    LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Downloads", color = HubText, style = MaterialTheme.typography.titleLarge) }
        if (state.downloads.isEmpty()) item { Text("No chapter downloads yet.", color = HubMuted) }
        listItems(state.downloads, key = { it.id }) { chapter ->
            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(HubPanel).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(chapter.mangaTitle, color = HubText, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text("${chapter.title} · ${chapter.status} · ${chapter.progress}%", color = HubMuted, style = MaterialTheme.typography.labelMedium)
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
        }
    }
}

@Composable
private fun HubExtensions(
    state: MangaSourceUiState,
    viewModel: MangaSourceViewModel,
) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Extensions", color = HubText, style = MaterialTheme.typography.titleLarge) }
        item {
            Text(
                "Installed catalogs can browse Popular, Latest, and Search. If a host serves a CAPTCHA or login wall, Weaverse reports that block instead of pretending the catalog is empty. Download from web link still works for a public chapter URL you are permitted to use.",
                color = HubMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            state.sources.forEach { source ->
                val health = state.sourceHealth[source.id]
                val usable = health?.startsWith("Ready") == true || source.authorized
                SourceCapabilityCard(
                    name = source.name,
                    kind = if (source.authorized) "Native API" else "Public HTML catalog",
                    detail = "Browse · Search · Metadata · Chapters · Downloads",
                    status = health ?: if (source.authorized) "Ready" else "Not checked",
                    statusColor = if (usable) Color(0xFF82D993) else if (health == null) HubMuted else Color(0xFFFFA38B),
                    actionLabel = if (health == null) "Test in Browse" else source.baseUrl,
                    onAction = if (health == null) ({
                        viewModel.selectSource(source.id)
                        viewModel.browse(MangaBrowseMode.Popular)
                    }) else null,
                )
            }
        }
        if (state.websites.isNotEmpty()) item { Text("Saved websites", color = HubText, style = MaterialTheme.typography.titleLarge) }
        listItems(state.websites, key = { it.id }) { site ->
            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(HubPanel).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(site.name, color = HubText)
                    Text(site.url, color = HubMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
                if (!site.builtIn) TextButton(onClick = { viewModel.removeWebsite(site) }) { Text("Remove") }
            }
        }
        item {
            Text("Optional saved link", color = HubText, style = MaterialTheme.typography.titleMedium)
            Text("Save a source URL for your own reference. It does not become a scraper; paste its chapter link in Browse to download.", color = HubMuted, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(state.websiteName, viewModel::setWebsiteName, label = { Text("Website name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(state.websiteUrl, viewModel::setWebsiteUrl, label = { Text("Website URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = viewModel::addWebsite, enabled = state.websiteName.isNotBlank() && state.websiteUrl.isNotBlank()) { Text("Save link") }
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
private fun HubSearchCover(result: MangaSearchResult, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        AsyncImage(
            model = result.coverUrl,
            contentDescription = result.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(0.68f).clip(RoundedCornerShape(8.dp)).background(HubPanel),
        )
        Text(result.title, color = HubText, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
private fun SourceCapabilityCard(
    name: String,
    kind: String,
    detail: String,
    status: String,
    statusColor: Color,
    actionLabel: String,
    onAction: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, color = HubText, fontWeight = FontWeight.Bold)
            Text("$kind · $detail", color = HubMuted, style = MaterialTheme.typography.labelSmall)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(status, color = statusColor, style = MaterialTheme.typography.labelMedium)
            if (onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel, color = HubAccent) }
            } else {
                Text(actionLabel, color = HubMuted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
