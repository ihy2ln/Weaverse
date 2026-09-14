package com.ihy2ln.weaverse.feature.storyboard

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.core.manga.MangaChapter
import com.ihy2ln.weaverse.core.manga.MangaBrowseMode
import com.ihy2ln.weaverse.core.manga.MangaDownloadRepository
import com.ihy2ln.weaverse.core.manga.MangaSearchResult
import com.ihy2ln.weaverse.core.manga.MangaSourceDescriptor
import com.ihy2ln.weaverse.core.manga.MangaSourceRegistry
import com.ihy2ln.weaverse.core.manga.MangaWebsite
import com.ihy2ln.weaverse.core.manga.WebLinkSnapshot
import com.ihy2ln.weaverse.core.manga.bundledMangaWebsites
import com.ihy2ln.weaverse.core.media.MediaRepository
import com.ihy2ln.weaverse.data.db.entities.MangaChapterEntity
import com.ihy2ln.weaverse.data.db.entities.MangaFavoriteCategoryEntity
import com.ihy2ln.weaverse.data.db.entities.MangaFavoriteEntity
import com.ihy2ln.weaverse.data.db.entities.MangaSeriesEntity
import coil3.compose.AsyncImage
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.content.Context
import java.io.File
import javax.inject.Inject

data class MangaSourceUiState(
    val query: String = "",
    val link: String = "",
    val results: List<MangaSearchResult> = emptyList(),
    val sources: List<MangaSourceDescriptor> = emptyList(),
    val activeSourceId: String = "mangadex",
    val selected: MangaSearchResult? = null,
    val chapters: List<MangaChapter> = emptyList(),
    val downloads: List<MangaChapterEntity> = emptyList(),
    val coverPaths: Map<String, String> = emptyMap(),
    val websites: List<MangaWebsite> = emptyList(),
    val favoriteSeries: List<MangaSeriesEntity> = emptyList(),
    val favoriteCategories: List<MangaFavoriteCategoryEntity> = emptyList(),
    val favorites: List<MangaFavoriteEntity> = emptyList(),
    val favoriteCategoryName: String = "",
    val websiteName: String = "",
    val websiteUrl: String = "",
    val linkPreview: WebLinkSnapshot? = null,
    val readerChapter: MangaChapterEntity? = null,
    val readerPagePaths: List<String> = emptyList(),
    val readerPageIndex: Int = 0,
    val readerOnline: Boolean = false,
    val busy: Boolean = false,
    val status: String = "",
    /** Browse mode the visible results came from, or null when they came from a search. */
    val catalogMode: MangaBrowseMode? = null,
    /** Query the visible results came from, for paging a search further. */
    val catalogQuery: String = "",
    /** Zero-based index of the last catalog page already appended to [results]. */
    val catalogPage: Int = 0,
    /** False once a page comes back empty, so the list stops asking for more. */
    val canLoadMore: Boolean = false,
    val loadingMore: Boolean = false,
)

private data class MangaFavoriteState(
    val series: List<MangaSeriesEntity>,
    val categories: List<MangaFavoriteCategoryEntity>,
    val favorites: List<MangaFavoriteEntity>,
)

@HiltViewModel
class MangaSourceViewModel @Inject constructor(
    private val repository: MangaDownloadRepository,
    private val registry: MangaSourceRegistry,
    private val mediaRepository: MediaRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val websitePreferences = context.getSharedPreferences("manga-source-websites", Context.MODE_PRIVATE)
    private val local = MutableStateFlow(
        MangaSourceUiState(
            websites = readCustomWebsites(),
            sources = registry.sources.map { it.descriptor },
        ),
    )
    private val favoriteState = combine(
        repository.observeSeries(),
        repository.observeFavoriteCategories(),
        repository.observeFavorites(),
    ) { series, categories, favorites -> MangaFavoriteState(series, categories, favorites) }
    val uiState: StateFlow<MangaSourceUiState> = combine(
        local,
        repository.observeChapters(),
        repository.observeCoverPages(),
        mediaRepository.observeAll(),
        favoriteState,
    ) { state, downloads, coverPages, media, favoriteState ->
        val mediaById = media.associateBy { it.id }
        val paths = downloads.associate { chapter ->
            val page = coverPages.firstOrNull { it.chapterId == chapter.id }
            val localPath = page?.localPath?.takeIf { it.isNotBlank() }
                ?.let { File(context.filesDir, it) }
                ?.takeIf(File::isFile)
                ?.absolutePath
            val mediaPath = page?.mediaId?.let(mediaById::get)
                ?.let(mediaRepository::resolveFile)
                ?.takeIf(File::isFile)
                ?.absolutePath
            chapter.id to (localPath ?: mediaPath ?: "")
        }
        state.copy(
            downloads = downloads,
            coverPaths = paths,
            favoriteSeries = favoriteState.series,
            favoriteCategories = favoriteState.categories,
            favorites = favoriteState.favorites,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    val sourceName: String = registry.sources.firstOrNull()?.descriptor?.name ?: "Manga source"
    val sourceDescription: String = registry.sources.firstOrNull()?.descriptor?.description.orEmpty()

    init {
        viewModelScope.launch { repository.ensureDefaultFavoriteCategory() }
    }

    fun setQuery(value: String) = local.value.let { local.value = it.copy(query = value) }

    fun setLink(value: String) = local.value.let { local.value = it.copy(link = value) }

    fun setStatus(value: String) = local.value.let { local.value = it.copy(status = value) }

    fun setFavoriteCategoryName(value: String) = local.value.let { local.value = it.copy(favoriteCategoryName = value) }

    fun selectSource(sourceId: String) {
        val source = registry.get(sourceId)?.descriptor ?: return
        local.value = local.value.copy(
            activeSourceId = sourceId,
            results = emptyList(),
            selected = null,
            chapters = emptyList(),
            status = "${source.name} selected. Choose Popular, Latest, or Search.",
        )
    }

    fun createFavoriteCategory() {
        val name = local.value.favoriteCategoryName.trim()
        viewModelScope.launch {
            runCatching { repository.createFavoriteCategory(name) }
                .onSuccess { local.value = local.value.copy(favoriteCategoryName = "", status = "Favorite category ‘$name’ created.") }
                .onFailure { local.value = local.value.copy(status = it.message ?: "Could not create category.") }
        }
    }

    fun toggleFavorite(manga: MangaSearchResult, categoryId: String) {
        val seriesId = "manga-series-${manga.sourceId}-${sha256Id(manga.remoteId)}"
        val alreadySaved = uiState.value.favorites.any { it.seriesId == seriesId && it.categoryId == categoryId }
        viewModelScope.launch {
            runCatching { repository.setFavorite(manga, categoryId, !alreadySaved) }
                .onSuccess {
                    val category = uiState.value.favoriteCategories.firstOrNull { it.id == categoryId }?.name ?: "Favorites"
                    local.value = local.value.copy(status = if (alreadySaved) "Removed from $category." else "Added to $category.")
                }
                .onFailure { local.value = local.value.copy(status = it.message ?: "Could not update favorite.") }
        }
    }

    fun setWebsiteName(value: String) = local.value.let { local.value = it.copy(websiteName = value) }

    fun setWebsiteUrl(value: String) = local.value.let { local.value = it.copy(websiteUrl = value) }

    fun addWebsite() {
        val input = local.value.websiteUrl.trim()
        val normalized = when {
            input.startsWith("https://", true) || input.startsWith("http://", true) -> input
            input.isNotBlank() -> "https://$input"
            else -> ""
        }
        val parsed = runCatching { Uri.parse(normalized) }.getOrNull()
        if (normalized.isBlank() || parsed?.host.isNullOrBlank()) {
            local.value = local.value.copy(status = "Enter a valid website address.")
            return
        }
        val site = MangaWebsite(
            id = "custom-${normalized.lowercase().hashCode().toUInt()}",
            name = local.value.websiteName.trim().ifBlank { parsed?.host.orEmpty() },
            url = normalized,
        )
        val custom = (local.value.websites.filterNot { it.builtIn } + site)
            .distinctBy { it.url.trimEnd('/').lowercase() }
            .sortedBy { it.name.lowercase() }
        persistCustomWebsites(custom)
        local.value = local.value.copy(
            websites = custom,
            websiteName = "",
            websiteUrl = "",
            status = "Website added. Open it in your browser, then paste a public chapter link above to download exposed page images.",
        )
    }

    fun removeWebsite(site: MangaWebsite) {
        if (site.builtIn) return
        val custom = local.value.websites.filterNot { it.builtIn || it.id == site.id }
        persistCustomWebsites(custom)
        local.value = local.value.copy(websites = custom, status = "Website removed.")
    }

    fun previewLink() {
        val link = local.value.link.trim()
        if (link.isBlank()) {
            local.value = local.value.copy(status = "Paste a chapter, manga, comic, or webtoon link first.")
            return
        }
        viewModelScope.launch {
            local.value = local.value.copy(busy = true, linkPreview = null, status = "Inspecting public chapter images…")
            runCatching { repository.previewWebLink(link) }
                .onSuccess { preview ->
                    local.value = local.value.copy(
                        busy = false,
                        linkPreview = preview,
                        status = "Found ${preview.pages.size} ordered page image(s). Confirm to download.",
                    )
                }
                .onFailure { local.value = local.value.copy(busy = false, status = it.message ?: "Could not read that link.") }
        }
    }

    fun downloadLink() = previewLink()

    fun confirmLinkDownload() {
        val preview = local.value.linkPreview ?: return
        viewModelScope.launch {
            local.value = local.value.copy(busy = true, status = "Queueing ${preview.pages.size} pages…")
            runCatching { repository.enqueueWebSnapshot(preview) }
                .onSuccess { chapter ->
                    local.value = local.value.copy(
                        busy = false,
                        link = "",
                        linkPreview = null,
                        status = "Queued ${chapter.mangaTitle} · ${chapter.pageCount} pages for offline download.",
                    )
                }
                .onFailure { local.value = local.value.copy(busy = false, status = it.message ?: "Could not queue download.") }
        }
    }

    fun clearLinkPreview() {
        local.value = local.value.copy(linkPreview = null, status = "Preview cancelled.")
    }

    fun importLocalFiles(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            local.value = local.value.copy(busy = true, status = "Importing manga/comic files into the local library…")
            runCatching { repository.importLocalFiles(uris) }
                .onSuccess { imported ->
                    local.value = local.value.copy(
                        busy = false,
                        status = if (imported.isEmpty()) "No pages could be read from those files." else "Imported ${imported.sumOf { it.pageCount }} page(s) into the local library.",
                    )
                }
                .onFailure { local.value = local.value.copy(busy = false, status = it.message ?: "Local import failed.") }
        }
    }

    fun search() {
        val query = local.value.query.trim()
        if (query.isBlank()) {
            local.value = local.value.copy(status = "Enter a manga title to search.")
            return
        }
        val sourceId = local.value.activeSourceId
        val sourceName = registry.get(sourceId)?.descriptor?.name ?: sourceId
        viewModelScope.launch {
            local.value = local.value.copy(
                busy = true,
                status = "Searching $sourceName…",
                results = emptyList(),
                selected = null,
                catalogMode = null,
                catalogQuery = query,
                catalogPage = 0,
                canLoadMore = false,
            )
            runCatching { repository.search(sourceId, query) }
                .onSuccess {
                    local.value = local.value.copy(
                        results = it,
                        busy = false,
                        canLoadMore = it.isNotEmpty(),
                        status = if (it.isEmpty()) "No results found." else "Select a title to load chapters.",
                    )
                }
                .onFailure { local.value = local.value.copy(busy = false, status = it.message ?: "Search failed.") }
        }
    }

    fun browse(mode: MangaBrowseMode) {
        val sourceId = local.value.activeSourceId
        val sourceName = registry.get(sourceId)?.descriptor?.name ?: sourceId
        viewModelScope.launch {
            local.value = local.value.copy(
                busy = true,
                selected = null,
                chapters = emptyList(),
                catalogMode = mode,
                catalogQuery = "",
                catalogPage = 0,
                canLoadMore = false,
                status = if (mode == MangaBrowseMode.Popular) "Loading popular $sourceName titles…" else "Loading latest $sourceName titles…",
            )
            runCatching { repository.browse(sourceId, mode) }
                .onSuccess {
                    local.value = local.value.copy(
                        results = it,
                        busy = false,
                        canLoadMore = it.isNotEmpty(),
                        status = if (it.isEmpty()) "No titles were returned." else "Select a cover to view chapters.",
                    )
                }
                .onFailure { local.value = local.value.copy(busy = false, status = it.message ?: "Browse failed.") }
        }
    }

    /**
     * Appends the next catalog page as the grid nears its end. A source that cannot page
     * returns nothing for page 1, which clears [MangaSourceUiState.canLoadMore] so the grid
     * settles instead of re-asking on every scroll.
     */
    fun loadMoreResults() {
        val snapshot = local.value
        if (snapshot.busy || snapshot.loadingMore || !snapshot.canLoadMore) return
        if (snapshot.results.isEmpty() || snapshot.selected != null) return
        val sourceId = snapshot.activeSourceId
        val nextPage = snapshot.catalogPage + 1
        viewModelScope.launch {
            local.value = local.value.copy(loadingMore = true)
            runCatching {
                if (snapshot.catalogMode != null) {
                    repository.browsePage(sourceId, snapshot.catalogMode, nextPage)
                } else {
                    repository.searchPage(sourceId, snapshot.catalogQuery, nextPage)
                }
            }
                .onSuccess { more ->
                    // Sources can repeat rows across pages; key off the identity the grid uses.
                    val seen = local.value.results.mapTo(hashSetOf()) { "${it.sourceId}:${it.remoteId}" }
                    val fresh = more.filterNot { "${it.sourceId}:${it.remoteId}" in seen }
                    local.value = local.value.copy(
                        results = local.value.results + fresh,
                        catalogPage = nextPage,
                        loadingMore = false,
                        canLoadMore = fresh.isNotEmpty(),
                        status = if (fresh.isEmpty()) "That is the end of this catalog." else local.value.status,
                    )
                }
                .onFailure {
                    local.value = local.value.copy(
                        loadingMore = false,
                        canLoadMore = false,
                        status = it.message ?: "Could not load more titles.",
                    )
                }
        }
    }

    fun select(manga: MangaSearchResult) {
        viewModelScope.launch {
            local.value = local.value.copy(selected = manga, chapters = emptyList(), busy = true, status = "Loading chapters…")
            runCatching {
                val details = repository.loadDetails(manga)
                details to repository.loadChapters(details)
            }
                .onSuccess { (details, chapters) ->
                    local.value = local.value.copy(
                        selected = details,
                        chapters = chapters,
                        busy = false,
                        status = if (chapters.isEmpty()) {
                            "${registry.get(manga.sourceId)?.descriptor?.name ?: "Source"} did not expose any chapter links for this title."
                        } else {
                            "Found ${chapters.size} chapters. Choose one to download."
                        },
                    )
                }
                .onFailure { local.value = local.value.copy(busy = false, status = it.message ?: "Chapter lookup failed.") }
        }
    }

    fun clearSelection() {
        local.value = local.value.copy(selected = null, chapters = emptyList(), status = "Choose a title or search again.")
    }

    fun enqueue(chapter: MangaChapter) {
        viewModelScope.launch {
            local.value = local.value.copy(busy = true, status = "Preparing chapter download…")
            runCatching {
                val entity = repository.addDiscoveredChapter(chapter)
                repository.enqueue(entity)
            }.onSuccess {
                local.value = local.value.copy(busy = false, status = "Download queued. It can resume after the app is closed.")
            }.onFailure {
                local.value = local.value.copy(busy = false, status = it.message ?: "Could not queue chapter.")
            }
        }
    }

    fun openOnlineReader(chapter: MangaChapter, pageIndex: Int = 0) = viewModelScope.launch {
        local.value = local.value.copy(busy = true, status = "Loading pages for online reading…")
        runCatching { repository.loadPages(chapter).sortedBy { it.pageIndex } }
            .onSuccess { pages ->
                val readerChapter = MangaChapterEntity(
                    id = "online-${chapter.sourceId}-${chapter.remoteId}",
                    sourceId = chapter.sourceId,
                    remoteId = chapter.remoteId,
                    mangaId = chapter.mangaId,
                    mangaTitle = chapter.mangaTitle,
                    title = chapter.title,
                    volume = chapter.volume,
                    chapterNumber = chapter.chapterNumber,
                    language = chapter.language,
                    canonicalUrl = chapter.canonicalUrl,
                    readingOrder = chapter.readingOrder,
                    pageCount = pages.size,
                    status = "online",
                )
                local.value = local.value.copy(
                    busy = false,
                    readerChapter = readerChapter.takeIf { pages.isNotEmpty() },
                    readerPagePaths = pages.map { it.remoteUrl },
                    readerPageIndex = pageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0)),
                    readerOnline = pages.isNotEmpty(),
                    status = if (pages.isEmpty()) "This source returned no readable page images." else "",
                )
            }
            .onFailure { local.value = local.value.copy(busy = false, status = it.message ?: "Could not stream this chapter.") }
    }

    fun deleteDownloadedSeries(chapter: MangaChapterEntity) = viewModelScope.launch {
        runCatching { repository.deleteDownloadedSeries(chapter) }
            .onSuccess { local.value = local.value.copy(status = "Deleted ${chapter.mangaTitle} and its local chapter files.") }
            .onFailure { local.value = local.value.copy(status = it.message ?: "Could not delete this title.") }
    }

    fun favoriteDownloadedSeries(chapter: MangaChapterEntity) {
        val manga = MangaSearchResult(
            sourceId = chapter.sourceId,
            remoteId = chapter.mangaId,
            title = chapter.mangaTitle,
            canonicalUrl = chapter.canonicalUrl,
        )
        toggleFavorite(manga, uiState.value.favoriteCategories.firstOrNull()?.id ?: "favorites")
    }

    fun stop(chapter: MangaChapterEntity) = viewModelScope.launch {
        repository.stop(chapter)
        local.value = local.value.copy(status = "Download stopped; the original partial files were kept for retry.")
    }

    fun retry(chapter: MangaChapterEntity) = viewModelScope.launch {
        runCatching { repository.retry(chapter) }
            .onSuccess { local.value = local.value.copy(status = "Download retry queued.") }
            .onFailure { local.value = local.value.copy(status = it.message ?: "Retry failed.") }
    }

    fun openReader(chapterId: String, pageIndex: Int = 0) = viewModelScope.launch {
        // Downloads are supplied by the Room flow in uiState. The local input state intentionally
        // does not mirror that database list, so looking there made every completed item unreadable.
        val chapter = uiState.value.downloads.firstOrNull { it.id == chapterId }
        if (chapter == null || chapter.status != "completed") {
            local.value = local.value.copy(status = "Finish downloading the chapter before reading it offline.")
            return@launch
        }
        local.value = local.value.copy(busy = true, status = "Opening offline chapter…")
        runCatching { repository.offlinePageFiles(chapterId).map(File::getAbsolutePath) }
            .onSuccess { pages ->
                local.value = local.value.copy(
                    busy = false,
                    readerChapter = chapter.takeIf { pages.isNotEmpty() },
                    readerPagePaths = pages,
                    readerPageIndex = pageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0)),
                    readerOnline = false,
                    status = if (pages.isEmpty()) {
                        "The download record is complete, but no page files were found. Retry the chapter download."
                    } else {
                        ""
                    },
                )
            }
            .onFailure { local.value = local.value.copy(busy = false, status = it.message ?: "Could not open chapter.") }
    }

    fun closeReader() {
        local.value = local.value.copy(readerChapter = null, readerPagePaths = emptyList(), readerPageIndex = 0, readerOnline = false)
    }

    private fun readCustomWebsites(): List<MangaWebsite> = websitePreferences
        .getStringSet("custom-websites", emptySet())
        .orEmpty()
        .mapNotNull { encoded ->
            val pieces = encoded.split('\t', limit = 2)
            if (pieces.size != 2) null else MangaWebsite(
                id = "custom-${pieces[1].lowercase().hashCode().toUInt()}",
                name = pieces[0],
                url = pieces[1],
            )
        }
        .sortedBy { it.name.lowercase() }

    private fun persistCustomWebsites(sites: List<MangaWebsite>) {
        websitePreferences.edit()
            .putStringSet(
                "custom-websites",
                sites.map { "${it.name.replace('\t', ' ')}\t${it.url}" }.toSet(),
            )
            .apply()
    }

    private fun sha256Id(value: String): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }.take(24)
}

@Composable
fun MangaSourceDialog(
    onImportChapter: ((String) -> Unit)?,
    onTranslateChapter: ((String) -> Unit)?,
    onDismiss: () -> Unit,
    viewModel: MangaSourceViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    state.readerChapter?.let { chapter ->
        MangaChapterReader(
            chapter = chapter,
            pagePaths = state.readerPagePaths,
            onDismiss = viewModel::closeReader,
            initialPageIndex = state.readerPageIndex,
            online = state.readerOnline,
        )
        return
    }
    var showAllDownloads by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val localPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.importLocalFiles(uris)
    }
    val visibleDownloads = if (showAllDownloads) state.downloads else state.downloads.take(4)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manga sources") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 680.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Extensions", style = MaterialTheme.typography.titleMedium)
                Text("${viewModel.sourceName} · ${viewModel.sourceDescription}", style = MaterialTheme.typography.bodySmall)
                Text("Downloads are stored locally first. You choose when to add originals to Storyboard.", style = MaterialTheme.typography.bodySmall)
                Text("Download chapter from link", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = state.link,
                        onValueChange = viewModel::setLink,
                        label = { Text("Paste a chapter or reader link") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = viewModel::previewLink, enabled = !state.busy) { Text("Preview") }
                }
                state.linkPreview?.let { preview ->
                    Text("${preview.title} · ${preview.pages.size} pages", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = viewModel::confirmLinkDownload, enabled = !state.busy) { Text("Download chapter") }
                        TextButton(onClick = viewModel::clearLinkPreview) { Text("Cancel") }
                    }
                }
                TextButton(onClick = { localPicker.launch(arrayOf("*/*")) }, enabled = !state.busy) {
                    Text("Import manga / comic / webtoon file into library")
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
                if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                if (state.status.isNotBlank()) Text(state.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                if (state.selected == null) {
                    Text("Catalog", style = MaterialTheme.typography.titleSmall)
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
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { viewModel.browse(MangaBrowseMode.Popular) }) { Text("Popular") }
                        TextButton(onClick = { viewModel.browse(MangaBrowseMode.Latest) }) { Text("Latest") }
                    }
                }
                if (state.selected == null && state.results.isNotEmpty()) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.heightIn(max = 300.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        gridItems(state.results, key = { it.remoteId }) { result ->
                            MangaSearchCoverCard(result = result, onClick = { viewModel.select(result) })
                        }
                    }
                }
                state.selected?.let { selected ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(selected.title, style = MaterialTheme.typography.titleSmall)
                            mangaMetadataLine(selected)?.let { metadata ->
                                Text(metadata, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            mangaCreditsLine(selected)?.let { credits ->
                                Text(credits, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            selected.tags.takeIf { it.isNotEmpty() }?.let { tags ->
                                Text("Tags: ${tags.joinToString(" · ")}", style = MaterialTheme.typography.labelSmall)
                            }
                            selected.description.takeIf(String::isNotBlank)?.let { description ->
                                Text(description, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                            }
                        }
                        Row {
                            TextButton(onClick = viewModel::clearSelection) { Text("Back") }
                            TextButton(onClick = { viewModel.select(selected) }) { Text("Refresh") }
                        }
                    }
                    LazyColumn(modifier = Modifier.heightIn(max = 210.dp)) {
                        items(state.chapters, key = { it.remoteId }) { chapter ->
                            val existing = state.downloads.firstOrNull { it.sourceId == chapter.sourceId && it.remoteId == chapter.remoteId }
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(chapterLabel(chapter), style = MaterialTheme.typography.bodySmall)
                                    if (existing != null) Text(downloadLabel(existing), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                when (existing?.status) {
                                    "completed" -> Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        TextButton(onClick = { viewModel.openReader(existing.id) }) { Text("Read") }
                                        if (onImportChapter != null) TextButton(onClick = { onImportChapter(existing.id) }) { Text("Add") }
                                        if (onTranslateChapter != null) TextButton(onClick = { onTranslateChapter(existing.id) }) { Text("Translate") }
                                    }
                                    "downloading", "queued" -> TextButton(onClick = { viewModel.stop(existing) }) { Text("Stop") }
                                    "failed", "stopped" -> TextButton(onClick = { viewModel.retry(existing) }) { Text("Retry") }
                                    else -> Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        TextButton(onClick = { viewModel.openOnlineReader(chapter) }, enabled = !state.busy) { Text("Read") }
                                        TextButton(onClick = { viewModel.enqueue(chapter) }) { Text("Download") }
                                    }
                                }
                            }
                        }
                    }
                }
                HorizontalDivider()
                Text("Websites", style = MaterialTheme.typography.titleMedium)
                Text("Open a website, copy a public chapter/reader link, then paste it into Download chapter from link. JavaScript/login/CAPTCHA protected pages require manual file import or an authorized adapter.", style = MaterialTheme.typography.labelSmall)
                LazyColumn(modifier = Modifier.heightIn(max = 150.dp)) {
                    items(state.websites, key = { it.id }) { site ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(site.name, style = MaterialTheme.typography.bodyMedium)
                                Text(site.url, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(site.url))) }) { Text("Open") }
                            if (!site.builtIn) TextButton(onClick = { viewModel.removeWebsite(site) }) { Text("Remove") }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = state.websiteName,
                        onValueChange = viewModel::setWebsiteName,
                        label = { Text("Website name") },
                        singleLine = true,
                        modifier = Modifier.weight(0.8f),
                    )
                    OutlinedTextField(
                        value = state.websiteUrl,
                        onValueChange = viewModel::setWebsiteUrl,
                        label = { Text("Website URL") },
                        singleLine = true,
                        modifier = Modifier.weight(1.2f),
                    )
                }
                Button(onClick = viewModel::addWebsite, enabled = !state.busy) { Text("Add website") }
                if (state.downloads.isNotEmpty()) {
                    Text("Library", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { showAllDownloads = !showAllDownloads }) {
                        Text(if (showAllDownloads) "Hide local downloads" else "Show local downloads")
                    }
                    MangaLibraryCoverGrid(
                        downloads = visibleDownloads,
                        coverPaths = state.coverPaths,
                        onSelectChapter = viewModel::openReader,
                        compact = false,
                        modifier = Modifier.heightIn(max = 330.dp),
                    )
                    visibleDownloads.forEach { chapter ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(downloadLabel(chapter), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                            if (chapter.status == "completed" && onImportChapter != null) {
                                TextButton(onClick = { onImportChapter(chapter.id) }) { Text("Add") }
                            }
                            if (chapter.status == "completed" && onTranslateChapter != null) {
                                TextButton(onClick = { onTranslateChapter(chapter.id) }) { Text("Translate") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

enum class MangaReaderAction {
    EditPage,
    EditChapter,
    TranslatePage,
    TranslateChapter,
    ColorPage,
    ColorChapter,
}

@Composable
fun MangaChapterReader(
    chapter: MangaChapterEntity,
    pagePaths: List<String>,
    onDismiss: () -> Unit,
    initialPageIndex: Int = 0,
    onAction: (MangaReaderAction, chapterId: String, pageIndex: Int) -> Unit = { _, _, _ -> },
    online: Boolean = false,
) {
    val listState = rememberLazyListState()
    val currentPage = listState.firstVisibleItemIndex.coerceIn(0, (pagePaths.size - 1).coerceAtLeast(0))
    androidx.compose.runtime.LaunchedEffect(initialPageIndex, pagePaths.size) {
        if (pagePaths.isNotEmpty()) {
            listState.scrollToItem(initialPageIndex.coerceIn(0, pagePaths.lastIndex))
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(chapter.mangaTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Text(
                            "${chapterLabel(chapter)} · ${pagePaths.size} pages · ${if (online) "online" else "offline"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                HorizontalDivider()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(pagePaths.size, key = { it }) { index ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "Page ${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                            AsyncImage(
                                model = pagePaths[index].takeIf { it.startsWith("http://") || it.startsWith("https://") }
                                    ?: File(pagePaths[index]),
                                contentDescription = "${chapter.mangaTitle} page ${index + 1}",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                HorizontalDivider()
                Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(
                            "Page ${currentPage + 1} of ${pagePaths.size}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                        if (online) {
                            Text(
                                "Download this chapter to unlock Edit, Translate, and Color.",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                            )
                        } else Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            TextButton(onClick = { onAction(MangaReaderAction.EditPage, chapter.id, currentPage) }) { Text("Edit page") }
                            TextButton(onClick = { onAction(MangaReaderAction.EditChapter, chapter.id, currentPage) }) { Text("Edit chapter") }
                            TextButton(onClick = { onAction(MangaReaderAction.TranslatePage, chapter.id, currentPage) }) { Text("Translate page") }
                            TextButton(onClick = { onAction(MangaReaderAction.TranslateChapter, chapter.id, currentPage) }) { Text("Translate all") }
                            TextButton(onClick = { onAction(MangaReaderAction.ColorPage, chapter.id, currentPage) }) { Text("Color page") }
                            TextButton(onClick = { onAction(MangaReaderAction.ColorChapter, chapter.id, currentPage) }) { Text("Color all") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MangaSearchCoverCard(result: MangaSearchResult, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CoverImage(
            model = result.coverUrl,
            contentDescription = result.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f),
        )
        Text(result.title, style = MaterialTheme.typography.labelLarge, maxLines = 2)
        result.tags.takeIf { it.isNotEmpty() }?.let { tags ->
            Text(tags.take(3).joinToString(" · "), style = MaterialTheme.typography.labelSmall, maxLines = 2)
        }
        mangaMetadataLine(result)?.let { metadata ->
            Text(metadata, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

private fun mangaMetadataLine(result: MangaSearchResult): String? = buildList {
    result.languages.takeIf { it.isNotEmpty() }?.let { add("Lang: ${it.joinToString(", ")}") }
    result.type.takeIf(String::isNotBlank)?.let { add(it) }
    result.status.takeIf(String::isNotBlank)?.let { add(it) }
    result.year.takeIf(String::isNotBlank)?.let { add(it) }
    result.rating.takeIf(String::isNotBlank)?.let { add("★ $it") }
}.joinToString(" · ").takeIf(String::isNotBlank)

private fun mangaCreditsLine(result: MangaSearchResult): String? = buildList {
    result.authors.takeIf { it.isNotEmpty() }?.let { add("Author: ${it.joinToString(", ")}") }
    result.artists.takeIf { it.isNotEmpty() }?.let { add("Artist: ${it.joinToString(", ")}") }
}.joinToString(" · ").takeIf(String::isNotBlank)

@Composable
fun MangaLibraryCoverGrid(
    downloads: List<MangaChapterEntity>,
    coverPaths: Map<String, String>,
    onSelectChapter: ((String) -> Unit)?,
    compact: Boolean,
    onEditChapter: ((String) -> Unit)? = null,
    onLongPressChapter: ((MangaChapterEntity) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (downloads.isEmpty()) return
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        gridItems(downloads, key = { it.id }) { chapter ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(chapter.id, onSelectChapter, onLongPressChapter) {
                        detectTapGestures(
                            onTap = { if (chapter.status == "completed") onSelectChapter?.invoke(chapter.id) },
                            onLongPress = { onLongPressChapter?.invoke(chapter) },
                        )
                    },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CoverImage(
                    model = coverPaths[chapter.id]?.takeIf(String::isNotBlank)?.let(::File),
                    contentDescription = chapter.mangaTitle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.68f),
                )
                Text(
                    chapter.mangaTitle,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = if (compact) 1 else 2,
                )
                Text(
                    chapterLabel(chapter),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                if (chapter.status == "completed" && onEditChapter != null) {
                    TextButton(onClick = { onEditChapter(chapter.id) }) {
                        Text("Edit")
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverImage(model: Any?, contentDescription: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                "M",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(18.dp),
            )
        }
    }
}

internal fun chapterLabel(chapter: MangaChapter): String = buildString {
    if (chapter.volume.isNotBlank()) append("Vol. ${chapter.volume} ")
    if (chapter.chapterNumber.isNotBlank()) append("Ch. ${chapter.chapterNumber} ")
    append(chapter.title)
}.trim()

private fun chapterLabel(chapter: MangaChapterEntity): String = buildString {
    if (chapter.volume.isNotBlank()) append("Vol. ${chapter.volume} ")
    if (chapter.chapterNumber.isNotBlank()) append("Ch. ${chapter.chapterNumber} ")
    append(chapter.title)
}.trim()

private fun downloadLabel(chapter: MangaChapterEntity): String = when (chapter.status) {
    "completed" -> "Downloaded · ${chapter.pageCount} pages"
    "downloading", "queued" -> "${chapter.status.replaceFirstChar { it.uppercase() }} · ${chapter.progress}%"
    "failed" -> "Failed: ${chapter.errorMessage.ifBlank { "retry available" }}"
    "stopped" -> "Stopped · retry available"
    else -> chapter.status
}
