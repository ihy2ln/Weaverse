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
import androidx.compose.foundation.layout.padding
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

@Composable
fun StoryboardMangaHubScreen(
    onCreateProject: () -> Unit,
    onOpenProject: (WorkShelfCard) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MangaSourceViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var tabName by rememberSaveable { mutableStateOf(MangaHubTab.Library.name) }
    val tab = runCatching { MangaHubTab.valueOf(tabName) }.getOrDefault(MangaHubTab.Library)

    state.readerChapter?.let { chapter ->
        MangaChapterReader(chapter, state.readerPagePaths, viewModel::closeReader)
        return
    }

    MaterialTheme(colorScheme = HubColors) {
        Surface(modifier = modifier.fillMaxSize(), color = HubBlack) {
            Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Window", style = MaterialTheme.typography.headlineSmall, color = HubText, fontWeight = FontWeight.Bold)
                    Text("Manga · Comic · Manhwa", style = MaterialTheme.typography.labelMedium, color = HubMuted)
                }
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
                Text(state.status, color = HubAccent, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            }
                when (tab) {
                    MangaHubTab.Library -> HubLibrary(
                        state = state,
                        viewModel = viewModel,
                        onOpenFavorite = { manga ->
                            tabName = MangaHubTab.Browse.name
                            viewModel.selectSource(manga.sourceId)
                            viewModel.select(manga)
                        },
                    )
                    MangaHubTab.Browse -> HubBrowse(state, viewModel)
                    MangaHubTab.Downloads -> HubDownloads(state, viewModel)
                    MangaHubTab.Extensions -> HubExtensions(
                        state = state,
                        viewModel = viewModel,
                        onBrowseSource = { sourceId ->
                            tabName = MangaHubTab.Browse.name
                            viewModel.selectSource(sourceId)
                            viewModel.browse(MangaBrowseMode.Popular)
                        },
                        onUseWebsite = { site ->
                            tabName = MangaHubTab.Browse.name
                            viewModel.setLink("")
                            viewModel.setStatus("Paste a public chapter URL from ${site.name}, then tap Preview.")
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
    onOpenFavorite: (MangaSearchResult) -> Unit,
) {
    var categoryId by rememberSaveable { mutableStateOf("downloads") }
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
}

@Composable
private fun HubBrowse(state: MangaSourceUiState, viewModel: MangaSourceViewModel) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Browse sources", color = HubText, style = MaterialTheme.typography.titleLarge)
            Text("Browse installed catalogs, open series metadata, then choose chapters.", color = HubMuted, style = MaterialTheme.typography.bodySmall)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
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
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    label = { Text("Search ${state.sources.firstOrNull { it.id == state.activeSourceId }?.name ?: "source"}") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = viewModel::search, enabled = !state.busy) { Text("Search") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(false, { viewModel.browse(MangaBrowseMode.Popular) }, { Text("Popular") })
                FilterChip(false, { viewModel.browse(MangaBrowseMode.Latest) }, { Text("Latest") })
                FilterChip(false, { viewModel.setStatus("Title filter is available in the search field.") }, { Text("Filter") })
            }
        }
        if (state.selected == null && state.results.isNotEmpty()) {
            item {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(145.dp),
                    modifier = Modifier.fillMaxWidth().aspectRatio(0.9f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.results, key = { it.remoteId }) { result -> HubSearchCover(result) { viewModel.select(result) } }
                }
            }
        }
        state.selected?.let { selected ->
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(selected.title, color = HubText, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = viewModel::clearSelection) { Text("Back", color = HubAccent) }
                }
                if (selected.description.isNotBlank()) {
                    Text(selected.description, color = HubMuted, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
                Text("Favorite sections", color = HubText, style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
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
                            label = { Text(if (saved) "✓ ${category.name}" else "+ ${category.name}") },
                        )
                    }
                }
            }
            if (!state.busy && state.chapters.isEmpty()) {
                item {
                    Text(
                        "No chapter links were returned. The source may be updating or blocking catalog access.",
                        color = HubMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            listItems(state.chapters, key = { it.remoteId }) { chapter -> HubChapterRow(chapter, state, viewModel) }
        }
        item {
            Text("Download from web link", color = HubText, style = MaterialTheme.typography.titleMedium)
            Text("Paste a public chapter reader URL. Weaverse previews the detected pages before downloading.", color = HubMuted, style = MaterialTheme.typography.bodySmall)
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
    }
}

@Composable
private fun HubDownloads(state: MangaSourceUiState, viewModel: MangaSourceViewModel) {
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
                    "completed" -> TextButton(onClick = { viewModel.openReader(chapter.id) }) { Text("Read") }
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
    onBrowseSource: (String) -> Unit,
    onUseWebsite: (com.ihy2ln.weaverse.core.manga.MangaWebsite) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Extensions", color = HubText, style = MaterialTheme.typography.titleLarge) }
        item {
            state.sources.forEach { source ->
                SourceCapabilityCard(
                    name = source.name,
                    kind = if (source.id == "mangadex") "Native API" else "Public HTML adapter",
                    detail = "Browse · Search · Metadata · Chapters · Downloads",
                    status = "Browsable",
                    statusColor = Color(0xFF82D993),
                    actionLabel = "Browse",
                    onAction = { onBrowseSource(source.id) },
                )
            }
        }
        item { Text("Websites", color = HubText, style = MaterialTheme.typography.titleLarge) }
        listItems(state.websites, key = { it.id }) { site ->
            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(HubPanel).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(site.name, color = HubText)
                    Text(site.url, color = HubMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
                TextButton(onClick = { onUseWebsite(site) }) { Text("Use") }
                if (!site.builtIn) TextButton(onClick = { viewModel.removeWebsite(site) }) { Text("Remove") }
            }
        }
        item {
            OutlinedTextField(state.websiteName, viewModel::setWebsiteName, label = { Text("Website name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(state.websiteUrl, viewModel::setWebsiteUrl, label = { Text("Website URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = viewModel::addWebsite) { Text("Add website") }
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
            TextButton(onClick = { onAction?.invoke() }, enabled = onAction != null) {
                Text(actionLabel, color = if (onAction != null) HubAccent else HubMuted)
            }
        }
    }
}

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
private fun HubChapterRow(chapter: MangaChapter, state: MangaSourceUiState, viewModel: MangaSourceViewModel) {
    val existing = state.downloads.firstOrNull { it.sourceId == chapter.sourceId && it.remoteId == chapter.remoteId }
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(HubPanel).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(chapter.title.ifBlank { "Chapter ${chapter.chapterNumber}" }, color = HubText)
            Text("Ch. ${chapter.chapterNumber} · ${existing?.status ?: "not downloaded"}", color = HubMuted, style = MaterialTheme.typography.labelSmall)
        }
        when (existing?.status) {
            "completed" -> TextButton(onClick = { viewModel.openReader(existing.id) }) { Text("Read") }
            "queued", "downloading" -> TextButton(onClick = { viewModel.stop(existing) }) { Text("Stop") }
            "failed", "stopped" -> TextButton(onClick = { viewModel.retry(existing) }) { Text("Retry") }
            else -> TextButton(onClick = { viewModel.enqueue(chapter) }) { Text("Download") }
        }
    }
}
