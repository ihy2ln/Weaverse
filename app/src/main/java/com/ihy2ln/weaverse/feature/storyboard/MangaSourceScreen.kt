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
import com.ihy2ln.weaverse.core.manga.extension.AvailableExtension
import com.ihy2ln.weaverse.core.manga.extension.ExtensionCatalogState
import com.ihy2ln.weaverse.core.manga.extension.ExtensionInstallMode
import com.ihy2ln.weaverse.core.manga.extension.InstalledExtension
import com.ihy2ln.weaverse.core.manga.extension.MangaExtensionManager
import com.ihy2ln.weaverse.core.media.MediaRepository
import com.ihy2ln.weaverse.data.db.entities.MangaChapterEntity
import com.ihy2ln.weaverse.data.db.entities.MangaFavoriteCategoryEntity
import com.ihy2ln.weaverse.data.db.entities.MangaFavoriteEntity
import com.ihy2ln.weaverse.data.db.entities.MangaSeriesEntity
import coil3.compose.AsyncImage
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.ihy2ln.weaverse.core.manga.CatalogTags
import android.content.Context
import java.io.File
import javax.inject.Inject
import eu.kanade.tachiyomi.source.model.FilterList

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
    /** Pull-to-refresh on the title's info page is reloading details and chapters. */
    val refreshingDetails: Boolean = false,
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
    /** Latest real adapter result keyed by source id: ready, empty, blocked, or error text. */
    val sourceHealth: Map<String, String> = emptyMap(),
    val extensionCatalog: ExtensionCatalogState = ExtensionCatalogState(),
    /** The live filter instances exported by the selected extension source. */
    val nativeFilters: FilterList = FilterList(),
    val incognito: Boolean = false,
    val globalTagFilter: String = "",
    val globalMatchAny: Boolean = false,
    val catalogSort: String = "source",
    val globalLanguageFilter: String = "",
    val globalStatusFilter: String = "",
    val globalLibraryOnly: Boolean = false,
    val downloadChoice: MangaChapter? = null,
    val linkDownloadChoice: Boolean = false,
    val downloadTreatments: Map<String, com.ihy2ln.weaverse.core.manga.MangaDownloadTreatment> = emptyMap(),
    val catalogRefinements: com.ihy2ln.weaverse.core.manga.CatalogRefinements = com.ihy2ln.weaverse.core.manga.CatalogRefinements(),
    val pinnedSourceIds: Set<String> = emptySet(),
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
    private val extensionManager: MangaExtensionManager,
    private val mediaRepository: MediaRepository,
    val galleryAccount: com.ihy2ln.weaverse.core.manga.GalleryAccountManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val websitePreferences = context.getSharedPreferences("manga-source-websites", Context.MODE_PRIVATE)
    private val readerPreferences = context.getSharedPreferences("manga-reader-settings", Context.MODE_PRIVATE)
    private val downloadPlans = com.ihy2ln.weaverse.core.manga.MangaDownloadPlanStore(readerPreferences)
    private val local = MutableStateFlow(
        MangaSourceUiState(
            websites = readCustomWebsites(),
            downloadTreatments = downloadPlans.read(),
            sources = registry.sources.map { it.descriptor },
            nativeFilters = registry.sources.firstOrNull()?.nativeFilters() ?: FilterList(),
            incognito = readerPreferences.getBoolean("incognito", false),
            pinnedSourceIds = readerPreferences.getStringSet("pinned_sources", emptySet()).orEmpty(),
        ),
    )
    private val favoriteState = combine(
        repository.observeSeries(),
        repository.observeFavoriteCategories(),
        repository.observeFavorites(),
    ) { series, categories, favorites -> MangaFavoriteState(series, categories, favorites) }
    private val localWithExtensions = combine(local, extensionManager.state, registry.sourcesFlow) { state, extensions, sources ->
        state.copy(
            sources = sources.map { it.descriptor },
            extensionCatalog = extensions,
            status = extensions.message.ifBlank { state.status },
        )
    }
    val uiState: StateFlow<MangaSourceUiState> = combine(
        localWithExtensions,
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
        viewModelScope.launch { repository.repairInvalidLibraryTitles() }
        viewModelScope.launch {
            val sourceId = local.value.activeSourceId
            runCatching { registry.get(sourceId)?.loadNativeFilters() }.onSuccess { filters ->
                if (filters != null && local.value.activeSourceId == sourceId) local.value = local.value.copy(nativeFilters = filters)
            }
        }
    }

    fun setQuery(value: String) = local.value.let { local.value = it.copy(query = value) }

    fun setLink(value: String) = local.value.let { local.value = it.copy(link = value) }

    fun setStatus(value: String) = local.value.let { local.value = it.copy(status = value) }

    fun repairLibraryTitles() = viewModelScope.launch {
        local.value = local.value.copy(busy = true, status = "Checking saved title metadata…")
        try {
            val count = repository.repairInvalidLibraryTitles(onlyInvalid = false)
            local.value = local.value.copy(status = "Repaired $count title records. Unavailable sources were left unchanged; reconnect and retry if a title still needs repair.")
        } finally { local.value = local.value.copy(busy = false) }
    }

    fun setIncognito(value: Boolean) {
        readerPreferences.edit().putBoolean("incognito", value).apply()
        local.value = local.value.copy(incognito = value, status = if (value) "Incognito mode enabled" else "Incognito mode disabled")
    }

    fun togglePinnedSource(sourceId: String) {
        val updated = local.value.pinnedSourceIds.toMutableSet().apply { if (!add(sourceId)) remove(sourceId) }
        readerPreferences.edit().putStringSet("pinned_sources", updated).apply()
        local.value = local.value.copy(pinnedSourceIds = updated)
    }

    fun setFavoriteCategoryName(value: String) = local.value.let { local.value = it.copy(favoriteCategoryName = value) }

    fun addExtensionStore(url: String) = extensionManager.addStore(url)
    fun removeExtensionStore(url: String) = extensionManager.removeStore(url)
    fun refreshExtensions() = extensionManager.refreshAvailable()
    fun trustExtension(packageName: String) = extensionManager.trust(packageName)
    fun installExtension(extension: AvailableExtension, mode: ExtensionInstallMode) = extensionManager.install(extension, mode)
    fun cancelExtensionInstall(packageName: String) = extensionManager.cancel(packageName)
    fun uninstallExtension(extension: InstalledExtension) = extensionManager.uninstall(extension)

    fun selectSource(sourceId: String) {
        val adapter = registry.get(sourceId) ?: return
        val source = adapter.descriptor
        local.value = local.value.copy(
            activeSourceId = sourceId,
            nativeFilters = adapter.nativeFilters(),
            results = emptyList(),
            selected = null,
            chapters = emptyList(),
            status = "${source.name} selected. Choose Popular, Latest, or Search.",
        )
        viewModelScope.launch {
            runCatching { adapter.loadNativeFilters() }.onSuccess { filters ->
                if (local.value.activeSourceId == sourceId) local.value = local.value.copy(nativeFilters = filters)
            }.onFailure { failure ->
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                if (local.value.activeSourceId == sourceId) local.value = local.value.copy(status = "Source filters unavailable: ${failure.message}. Retry by reopening this source.")
            }
        }
    }

    fun resetNativeFilters() {
        val filters = registry.get(local.value.activeSourceId)?.nativeFilters() ?: FilterList()
        local.value = local.value.copy(nativeFilters = filters)
    }

    /** Filter state is owned by extension objects; copying the list publishes their mutation to Compose. */
    fun notifyNativeFiltersChanged() {
        local.value = local.value.copy(nativeFilters = FilterList(local.value.nativeFilters.list.toList()))
    }

    fun setGlobalTagFilter(value: String) { local.value = local.value.copy(globalTagFilter = value) }
    fun setCatalogRefinements(value: com.ihy2ln.weaverse.core.manga.CatalogRefinements) {
        local.value = local.value.copy(catalogRefinements = value)
    }
    fun setGlobalMatchAny(value: Boolean) { local.value = local.value.copy(globalMatchAny = value) }
    fun setCatalogSort(value: String) { local.value = local.value.copy(catalogSort = value) }
    fun setGlobalLanguageFilter(value: String) { local.value = local.value.copy(globalLanguageFilter = value) }
    fun setGlobalStatusFilter(value: String) { local.value = local.value.copy(globalStatusFilter = value) }
    fun setGlobalLibraryOnly(value: Boolean) { local.value = local.value.copy(globalLibraryOnly = value) }

    fun resetGlobalFilters() {
        local.value = local.value.copy(
            globalTagFilter = "",
            globalMatchAny = false,
            catalogSort = "source",
            globalLanguageFilter = "",
            globalStatusFilter = "",
            globalLibraryOnly = false,
            catalogRefinements = com.ihy2ln.weaverse.core.manga.CatalogRefinements(),
        )
    }

    fun applyCatalogFilters() {
        val snapshot = local.value
        // Popular/Latest ask the source for its own listing order, so they must win
        // even when native website filters are also loaded — those two checks used
        // to sit after the native-filters check, which is true for rawkuma/comix/
        // mangafire the moment their filter form loads, making Popular/Latest dead.
        when {
            snapshot.catalogSort == "latest" -> browse(MangaBrowseMode.Latest)
            snapshot.catalogSort == "popular" -> browse(MangaBrowseMode.Popular)
            snapshot.nativeFilters.isNotEmpty() -> search()
            snapshot.query.isBlank() -> browse(snapshot.catalogMode ?: MangaBrowseMode.Popular)
            else -> search()
        }
    }

    private val catalogMetadata = java.util.concurrent.ConcurrentHashMap<String, MangaSearchResult>()
    private val metadataSlots = Semaphore(3)
    private var filterMetadataWarning = ""

    private suspend fun applyGlobalFilters(items: List<MangaSearchResult>): List<MangaSearchResult> {
        val filters = local.value
        val requestedTags = filters.globalTagFilter.split(',').map(String::trim).filter(String::isNotBlank)
        val language = filters.globalLanguageFilter.trim()
        val status = filters.globalStatusFilter.trim()
        val favoriteIds = uiState.value.favorites.mapTo(hashSetOf()) { it.seriesId }
        val saved = uiState.value.favoriteSeries.filter { it.id in favoriteIds }.mapTo(hashSetOf()) { "${it.sourceId}:${it.remoteId}" }
        val failures = java.util.concurrent.atomic.AtomicInteger()
        val enriched = coroutineScope {
            items.map { manga -> async {
                // Catalog cards can have a nonempty but incomplete tag subset. Always use
                // cached/full detail metadata for local tag matching, not that preview subset.
                val needsDetails = requestedTags.isNotEmpty() ||
                    (status.isNotBlank() && (manga.status.isBlank() || manga.status == "Unknown")) ||
                    (filters.catalogRefinements.minimumScore > 0 && manga.score.isBlank()) ||
                    (filters.catalogRefinements.contentRating.isNotBlank() && manga.rating.isBlank())
                if (!needsDetails) manga else metadataSlots.withPermit {
                    val key = "${manga.sourceId}:${manga.remoteId}"
                    catalogMetadata[key] ?: try {
                        repository.loadDetails(manga).also { catalogMetadata[key] = it }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        failures.incrementAndGet()
                        null // Unknown metadata must not be treated as a match, including exclusions.
                    }
                }
            } }.awaitAll().filterNotNull()
        }
        val refined = coroutineScope {
            enriched.map { manga -> async {
                val chapters = if (filters.catalogRefinements.needsChapters) metadataSlots.withPermit {
                    try {
                        registry.get(manga.sourceId)?.chapters(manga)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { failures.incrementAndGet(); null }
                } else null
                manga.takeIf { filters.catalogRefinements.matches(it, chapters) }
            } }.awaitAll().filterNotNull()
        }
        filterMetadataWarning = if (failures.get() == 0) "" else " ${failures.get()} titles skipped because their filter metadata could not load. Retry to check them."
        return refined.filter { manga ->
            CatalogTags.matches(manga.tags + manga.type.takeIf(String::isNotBlank).let { listOfNotNull(it) }, filters.globalTagFilter, filters.globalMatchAny) &&
                (language.isBlank() || manga.languages.any { it.equals(language, true) || it.contains(language, true) }) &&
                (status.isBlank() || manga.status.contains(status, true)) &&
                (!filters.globalLibraryOnly || "${manga.sourceId}:${manga.remoteId}" in saved)
        }
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
        if (local.value.linkPreview != null) local.value = local.value.copy(linkDownloadChoice = true)
    }

    fun confirmLinkDownloadWithTreatment(mode: com.ihy2ln.weaverse.core.manga.MangaDownloadTreatment) {
        val preview = local.value.linkPreview ?: return
        if (local.value.busy) return
        viewModelScope.launch {
            local.value = local.value.copy(busy = true, linkDownloadChoice = false, status = "Queueing ${preview.pages.size} original pages…")
            runCatching {
                repository.enqueueWebSnapshot(preview).also { chapter ->
                    withContext(Dispatchers.IO) { downloadPlans.save(chapter.id, mode) }
                }
            }
                .onSuccess { chapter ->
                    local.value = local.value.copy(
                        busy = false,
                        downloadTreatments = downloadPlans.read(),
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
        if (query.isBlank() && local.value.nativeFilters.isEmpty() &&
            local.value.globalTagFilter.isBlank() && local.value.globalLanguageFilter.isBlank() &&
            local.value.globalStatusFilter.isBlank() && !local.value.globalLibraryOnly && !local.value.catalogRefinements.active
        ) {
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
            runCatching { repository.searchPage(sourceId, query, 0, local.value.nativeFilters).let { it to applyGlobalFilters(it) } }
                .onSuccess { (it, filtered) ->
                    local.value = local.value.copy(
                        results = com.ihy2ln.weaverse.core.manga.CatalogSort.apply(filtered, local.value.catalogSort),
                        busy = false,
                        canLoadMore = it.isNotEmpty(),
                        status = (if (it.isEmpty()) "No results found." else "${filtered.size} matching titles.") + filterMetadataWarning,
                        sourceHealth = local.value.sourceHealth + (sourceId to if (it.isEmpty()) "Reachable · no matching titles" else "Ready · ${it.size} titles loaded"),
                    )
                }
                .onFailure {
                    val message = it.message ?: "Search failed."
                    local.value = local.value.copy(busy = false, status = message, sourceHealth = local.value.sourceHealth + (sourceId to message))
                }
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
                results = emptyList(),
                catalogMode = mode,
                catalogQuery = "",
                catalogPage = 0,
                canLoadMore = false,
                status = if (mode == MangaBrowseMode.Popular) "Loading popular $sourceName titles…" else "Loading latest $sourceName titles…",
            )
            runCatching { repository.browse(sourceId, mode).let { it to applyGlobalFilters(it) } }
                .onSuccess { (it, filtered) ->
                    local.value = local.value.copy(
                        results = com.ihy2ln.weaverse.core.manga.CatalogSort.apply(filtered, local.value.catalogSort),
                        busy = false,
                        canLoadMore = it.isNotEmpty(),
                        status = (if (it.isEmpty()) "No titles were returned." else "${filtered.size} matching titles.") + filterMetadataWarning,
                        sourceHealth = local.value.sourceHealth + (sourceId to if (it.isEmpty()) "Reachable · catalog format unsupported" else "Ready · ${it.size} titles loaded"),
                    )
                }
                .onFailure {
                    val message = it.message ?: "Browse failed."
                    local.value = local.value.copy(busy = false, status = message, sourceHealth = local.value.sourceHealth + (sourceId to message))
                }
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
        if (snapshot.selected != null) return
        val sourceId = snapshot.activeSourceId
        val nextPage = snapshot.catalogPage + 1
        viewModelScope.launch {
            local.value = local.value.copy(loadingMore = true)
            runCatching {
                val batch = if (snapshot.catalogMode != null) {
                    repository.browsePage(sourceId, snapshot.catalogMode, nextPage)
                } else {
                    repository.searchPage(sourceId, snapshot.catalogQuery, nextPage, snapshot.nativeFilters)
                }
                batch to applyGlobalFilters(batch)
            }
                .onSuccess { (more, filtered) ->
                    // Sources can repeat rows across pages; key off the identity the grid uses.
                    val seen = local.value.results.mapTo(hashSetOf()) { "${it.sourceId}:${it.remoteId}" }
                    val fresh = filtered.filterNot { "${it.sourceId}:${it.remoteId}" in seen }
                    local.value = local.value.copy(
                        results = com.ihy2ln.weaverse.core.manga.CatalogSort.apply(local.value.results + fresh, local.value.catalogSort),
                        catalogPage = nextPage,
                        loadingMore = false,
                        canLoadMore = more.isNotEmpty(),
                        status = (if (more.isEmpty()) "That is the end of this catalog." else if (fresh.isEmpty()) "No matches on this page; more pages are available." else "${fresh.size} more matching titles.") + filterMetadataWarning,
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

    fun openRecentSeries(id: String) = viewModelScope.launch {
        val series = repository.recentSeries(id) ?: return@launch
        val chapter = repository.resumeChapter(series)
        if (chapter != null) {
            uiState.first { state -> state.downloads.any { it.id == chapter.id } }
            openReader(chapter.id, chapter.lastPageRead)
        } else select(MangaSearchResult(series.sourceId, series.remoteId, series.title, series.description, series.coverUrl, series.canonicalUrl))
    }
    fun select(manga: MangaSearchResult) {
        viewModelScope.launch {
            local.value = local.value.copy(selected = manga, chapters = emptyList(), busy = true, status = "Loading chapters…")
            runCatching {
                val details = repository.loadDetails(manga)
                details to repository.loadChapters(details)
            }
                .onSuccess { (details, chapters) ->
                    repository.recordHomeAccess(details.sourceId, details.remoteId)
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

    /** Pull-to-refresh: reloads the open title's details and chapter list, keeping what is shown until it arrives. */
    fun refreshSelected() {
        val manga = local.value.selected ?: return
        if (local.value.refreshingDetails) return
        viewModelScope.launch {
            local.value = local.value.copy(refreshingDetails = true, status = "Refreshing…")
            runCatching {
                val details = repository.loadDetails(manga)
                details to repository.loadChapters(details)
            }.onSuccess { (details, chapters) ->
                // Ignore a refresh that finishes after the reader has opened another title.
                if (local.value.selected?.remoteId != manga.remoteId) return@onSuccess
                local.value = local.value.copy(
                    selected = details,
                    chapters = chapters.ifEmpty { local.value.chapters },
                    status = "Refreshed · ${chapters.size} chapters.",
                )
            }.onFailure { local.value = local.value.copy(status = it.message ?: "Refresh failed.") }
            local.value = local.value.copy(refreshingDetails = false)
        }
    }

    fun clearSelection() {
        local.value = local.value.copy(selected = null, chapters = emptyList(), status = "Choose a title or search again.")
    }

    fun enqueue(chapter: MangaChapter) {
        local.value = local.value.copy(downloadChoice = chapter)
    }

    fun downloadOptions(chapter: MangaChapterEntity) = enqueue(MangaChapter(
        sourceId = chapter.sourceId, remoteId = chapter.remoteId, mangaId = chapter.mangaId,
        mangaTitle = chapter.mangaTitle, title = chapter.title, volume = chapter.volume,
        chapterNumber = chapter.chapterNumber, language = chapter.language, canonicalUrl = chapter.canonicalUrl,
        readingOrder = chapter.readingOrder, dateUpload = chapter.dateUpload, scanlator = chapter.scanlator,
    ))

    fun dismissDownloadOptions() { local.value = local.value.copy(downloadChoice = null, linkDownloadChoice = false) }

    fun removeDownloadTreatment(chapterId: String) = viewModelScope.launch {
        runCatching { withContext(Dispatchers.IO) { downloadPlans.save(chapterId, com.ihy2ln.weaverse.core.manga.MangaDownloadTreatment.Original) } }
            .onSuccess { local.value = local.value.copy(downloadTreatments = downloadPlans.read()) }
            .onFailure { local.value = local.value.copy(status = it.message ?: "Could not remove the AI follow-up.") }
    }

    fun confirmDownload(mode: com.ihy2ln.weaverse.core.manga.MangaDownloadTreatment) {
        val chapter = local.value.downloadChoice ?: return
        if (local.value.busy) return
        viewModelScope.launch {
            local.value = local.value.copy(busy = true, downloadChoice = null, status = "Preparing original chapter download…")
            runCatching {
                val entity = repository.addDiscoveredChapter(chapter)
                withContext(Dispatchers.IO) { downloadPlans.save(entity.id, mode) }
                local.value = local.value.copy(downloadTreatments = downloadPlans.read())
                // Completed originals and all edited versions remain untouched when choosing AI.
                if (entity.status != "completed") repository.enqueue(entity)
            }.onSuccess {
                local.value = local.value.copy(busy = false, status = if (mode.editorAction == null)
                    "Original pages kept. Any queued download can resume after the app is closed."
                    else "Originals download first. Open AI ready when complete, review settings, then Run ${mode.label}. Original pages are kept.")
            }.onFailure {
                local.value = local.value.copy(busy = false, status = it.message ?: "Could not queue chapter.")
            }
        }
    }

    fun openOnlineReader(chapter: MangaChapter, pageIndex: Int = 0) = viewModelScope.launch {
        local.value = local.value.copy(busy = true, status = "Loading pages for online reading…")
        runCatching {
            val pages = repository.loadPages(chapter).sortedBy { it.pageIndex }
            val persisted = repository.addDiscoveredChapter(chapter)
            pages to persisted
        }
            .onSuccess { (pages, persisted) ->
                if (pages.isNotEmpty()) repository.recordHomeAccess(persisted.sourceId, persisted.mangaId)
                val readerChapter = persisted.copy(pageCount = pages.size, status = "online")
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

    /**
     * Long-press "Remove from library": drops the title from every category and deletes
     * any chapters downloaded to this device. A title with no source entry (a bare local
     * download) just loses its files.
     */
    /** Bytes used by downloaded chapter images, and by the throwaway image cache. */
    fun storageSummary(): Pair<Long, Long> {
        fun size(dir: java.io.File): Long =
            if (!dir.exists()) 0L else dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
        return size(java.io.File(context.filesDir, "manga")) to size(context.cacheDir)
    }

    /** Where downloaded pages live, shown so the location row is not a dead end. */
    fun storageLocation(): String = java.io.File(context.filesDir, "manga").absolutePath

    /** Clears cached images. Downloads and library entries are untouched. */
    fun clearImageCache() = viewModelScope.launch {
        val freed = runCatching {
            val before = context.cacheDir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
            context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
            before
        }.getOrDefault(0L)
        local.value = local.value.copy(status = "Cleared ${formatStorageSize(freed)} of cached images.")
    }

    fun removeFromLibrary(manga: MangaSearchResult?, chapters: List<MangaChapterEntity>) = viewModelScope.launch {
        val title = manga?.title ?: chapters.firstOrNull()?.mangaTitle.orEmpty()
        runCatching {
            if (manga != null) {
                val seriesId = "manga-series-${manga.sourceId}-${sha256Id(manga.remoteId)}"
                uiState.value.favorites
                    .filter { it.seriesId == seriesId }
                    .forEach { repository.setFavorite(manga, it.categoryId, false) }
            }
            chapters.firstOrNull()?.let { repository.deleteDownloadedSeries(it) }
        }
            .onSuccess {
                local.value = local.value.copy(
                    status = if (chapters.isEmpty()) {
                        "Removed $title from the library."
                    } else {
                        "Removed $title and deleted its downloaded chapters."
                    },
                )
            }
            .onFailure { local.value = local.value.copy(status = it.message ?: "Could not remove this title.") }
    }

    fun deleteDownloadedSeries(chapter: MangaChapterEntity) = viewModelScope.launch {
        runCatching { repository.deleteDownloadedSeries(chapter) }
            .onSuccess { local.value = local.value.copy(status = "Deleted ${chapter.mangaTitle} and its local chapter files.") }
            .onFailure { local.value = local.value.copy(status = it.message ?: "Could not delete this title.") }
    }

    fun favoriteDownloadedSeries(chapter: MangaChapterEntity) {
        val manga = uiState.value.favoriteSeries.firstOrNull { it.sourceId == chapter.sourceId && it.remoteId == chapter.mangaId }?.toMihonSearchResult() ?: MangaSearchResult(
            sourceId = chapter.sourceId,
            remoteId = chapter.mangaId,
            title = chapter.mangaTitle,
            canonicalUrl = "", // A chapter URL is not a series URL.
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
                if (pages.isNotEmpty()) repository.recordHomeAccess(chapter.sourceId, chapter.mangaId)
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

    fun resumeChapter(chapter: MangaChapterEntity) {
        if (chapter.status == "completed") {
            openReader(chapter.id, chapter.lastPageRead)
        } else {
            openOnlineReader(
                MangaChapter(
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
                    dateUpload = chapter.dateUpload,
                    scanlator = chapter.scanlator,
                ),
                chapter.lastPageRead,
            )
        }
    }

    fun closeReader() {
        local.value = local.value.copy(readerChapter = null, readerPagePaths = emptyList(), readerPageIndex = 0, readerOnline = false)
    }

    fun recordReaderPage(chapterId: String, pageIndex: Int, pageCount: Int) {
        if (local.value.incognito) return
        viewModelScope.launch { repository.recordReadingProgress(chapterId, pageIndex, pageCount) }
    }

    fun setChapterRead(chapter: MangaChapterEntity, read: Boolean) = viewModelScope.launch {
        repository.setChapterRead(chapter, read)
    }

    fun setChapterBookmarked(chapter: MangaChapterEntity, bookmarked: Boolean) = viewModelScope.launch {
        repository.setChapterBookmarked(chapter, bookmarked)
    }

    fun clearHistory() = viewModelScope.launch {
        repository.clearReadingHistory()
        local.value = local.value.copy(status = "Reading history cleared")
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
    MangaDownloadOptionsHost(state, viewModel)
    state.readerChapter?.let { chapter ->
        MangaChapterReader(
            chapter = chapter,
            pagePaths = state.readerPagePaths,
            onDismiss = viewModel::closeReader,
            initialPageIndex = state.readerPageIndex,
            online = state.readerOnline,
            onPageChanged = { page -> viewModel.recordReaderPage(chapter.id, page, state.readerPagePaths.size) },
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
                                        if (onTranslateChapter != null) TextButton(onClick = { onTranslateChapter(existing.id) }) { Text("Translate to English") }
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
                                TextButton(onClick = { onTranslateChapter(chapter.id) }) { Text("Translate to English") }
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
    ColorTranslateChapter,
}

@Composable
fun MangaChapterReader(
    chapter: MangaChapterEntity,
    pagePaths: List<String>,
    onDismiss: () -> Unit,
    initialPageIndex: Int = 0,
    onAction: (MangaReaderAction, chapterId: String, pageIndex: Int) -> Unit = { _, _, _ -> },
    online: Boolean = false,
    onPageChanged: (Int) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val currentPage = listState.firstVisibleItemIndex.coerceIn(0, (pagePaths.size - 1).coerceAtLeast(0))
    androidx.compose.runtime.LaunchedEffect(initialPageIndex, pagePaths.size) {
        if (pagePaths.isNotEmpty()) {
            listState.scrollToItem(initialPageIndex.coerceIn(0, pagePaths.lastIndex))
        }
    }
    androidx.compose.runtime.LaunchedEffect(currentPage, pagePaths.size) {
        if (pagePaths.isNotEmpty()) onPageChanged(currentPage)
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
                            TextButton(onClick = { onAction(MangaReaderAction.TranslatePage, chapter.id, currentPage) }) { Text("Translate to English") }
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

/** Human-readable byte size for the storage rows. */
internal fun formatStorageSize(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> String.format(java.util.Locale.US, "%.1f GB", bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1_048_576.0)
    bytes >= 1024 -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
