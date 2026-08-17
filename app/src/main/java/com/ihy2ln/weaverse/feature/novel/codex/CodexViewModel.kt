package com.ihy2ln.weaverse.feature.novel.codex

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.core.export.ExportFormat
import com.ihy2ln.weaverse.core.media.MediaImporter
import com.ihy2ln.weaverse.data.db.entity.CodexCategoryEntity
import com.ihy2ln.weaverse.data.db.entity.CodexEntryEntity
import com.ihy2ln.weaverse.data.db.entity.CodexEntryLoreEntity
import com.ihy2ln.weaverse.data.db.entity.MediaEntity
import com.ihy2ln.weaverse.data.db.entity.ScopeType
import com.ihy2ln.weaverse.data.repo.CodexRepository
import com.ihy2ln.weaverse.data.repo.LibraryRepository
import com.ihy2ln.weaverse.data.repo.MediaRepository
import com.ihy2ln.weaverse.data.repo.observeCurrentBookId
import com.ihy2ln.weaverse.data.settings.AppSettingsRepository
import com.ihy2ln.weaverse.feature.settings.backup.CodexBackupService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** All / Book / Series scope tabs (spec Revision 02 §1.4/§3) — filters which entries the rail
 * shows without changing which book/series they belong to. */
enum class CodexScopeFilter { All, Book, Series }

/**
 * Backs the Codex rail (spec §9) and its entry editor. [currentBookId]
 * follows whichever book the Books rail tab has selected (see
 * `data/repo/CurrentBook.kt`).
 */
@HiltViewModel
class CodexViewModel @Inject constructor(
    private val codexRepository: CodexRepository,
    private val libraryRepository: LibraryRepository,
    settingsRepository: AppSettingsRepository,
    val mediaRepository: MediaRepository,
    private val mediaImporter: MediaImporter,
    private val codexBackupService: CodexBackupService,
) : ViewModel() {
    private val currentBookId: StateFlow<String?> = observeCurrentBookId(libraryRepository, settingsRepository)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Null when the current book isn't in a series — the Series scope tab then has nothing to
     * show and [scopeCounts] reports 0 for it. */
    val currentSeriesId: StateFlow<String?> = currentBookId.filterNotNull()
        .flatMapLatest { libraryRepository.observeBook(it) }
        .map { it?.seriesId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _scopeFilter = MutableStateFlow(CodexScopeFilter.All)
    val scopeFilter: StateFlow<CodexScopeFilter> = _scopeFilter
    fun setScopeFilter(filter: CodexScopeFilter) {
        _scopeFilter.value = filter
    }

    private val bookCategories: StateFlow<List<CodexCategoryEntity>> = currentBookId.filterNotNull()
        .flatMapLatest { bookId -> codexRepository.observeCategories(ScopeType.Book, bookId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val seriesCategories: StateFlow<List<CodexCategoryEntity>> = currentSeriesId
        .flatMapLatest { seriesId -> if (seriesId == null) flowOf(emptyList()) else codexRepository.observeCategories(ScopeType.Series, seriesId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Categories visible under [scopeFilter] — Book and Series categories are simply concatenated
     * under "All" (spec doesn't distinguish where a *category* came from, only entries carry the
     * globe badge marking them series-wide). */
    val categories: StateFlow<List<CodexCategoryEntity>> = combine(bookCategories, seriesCategories, scopeFilter) { book, series, filter ->
        when (filter) {
            CodexScopeFilter.All -> book + series
            CodexScopeFilter.Book -> book
            CodexScopeFilter.Series -> series
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val bookEntries: StateFlow<List<CodexEntryEntity>> = currentBookId.filterNotNull()
        .flatMapLatest { bookId -> codexRepository.observeEntriesForScope(ScopeType.Book, bookId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val seriesEntries: StateFlow<List<CodexEntryEntity>> = currentSeriesId
        .flatMapLatest { seriesId -> if (seriesId == null) flowOf(emptyList()) else codexRepository.observeEntriesForScope(ScopeType.Series, seriesId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Counts for the scope tabs' badges (spec's reference screenshot: "All 101", "Series 101"). */
    val scopeCounts: StateFlow<Map<CodexScopeFilter, Int>> = combine(bookEntries, seriesEntries) { book, series ->
        mapOf(
            CodexScopeFilter.All to book.size + series.size,
            CodexScopeFilter.Book to book.size,
            CodexScopeFilter.Series to series.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val allEntries: StateFlow<List<CodexEntryEntity>> = combine(bookEntries, seriesEntries, scopeFilter) { book, series, filter ->
        when (filter) {
            CodexScopeFilter.All -> book + series
            CodexScopeFilter.Book -> book
            CodexScopeFilter.Series -> series
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Filtered by [searchQuery] (name or alias match), grouped by category id for the rail's accordions. */
    val entriesByCategory: StateFlow<Map<String, List<CodexEntryEntity>>> = combine(allEntries, _searchQuery) { entries, query ->
        val filtered = if (query.isBlank()) {
            entries
        } else {
            entries.filter { entry ->
                entry.name.contains(query, ignoreCase = true) ||
                    entry.aliases.any { it.contains(query, ignoreCase = true) }
            }
        }
        filtered.groupBy { it.categoryId }.mapValues { (_, v) -> v.sortedBy { it.name } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /** Toggles an entry between its book and series scope (Revision 02 §3: "series-scoped codex
     * entries are visible and injectable in every member book" — this is how an entry becomes
     * one). A no-op if the current book isn't in a series. */
    fun setEntrySeriesWide(entry: CodexEntryEntity, seriesWide: Boolean) {
        val bookId = currentBookId.value ?: return
        viewModelScope.launch {
            val updated = if (seriesWide) {
                val seriesId = currentSeriesId.value ?: return@launch
                entry.copy(scopeType = ScopeType.Series, scopeId = seriesId)
            } else {
                entry.copy(scopeType = ScopeType.Book, scopeId = bookId)
            }
            codexRepository.upsertEntry(updated.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun addCategory(name: String, colorHex: String) {
        val bookId = currentBookId.value ?: return
        viewModelScope.launch {
            codexRepository.upsertCategory(
                CodexCategoryEntity(
                    scopeType = ScopeType.Book,
                    scopeId = bookId,
                    name = name,
                    colorHex = colorHex,
                    sortOrder = bookCategories.value.size,
                ),
            )
        }
    }

    fun updateCategory(category: CodexCategoryEntity) {
        viewModelScope.launch { codexRepository.upsertCategory(category) }
    }

    fun deleteCategory(category: CodexCategoryEntity) {
        viewModelScope.launch { codexRepository.deleteCategory(category) }
    }

    fun createEntry(categoryId: String, name: String) {
        val bookId = currentBookId.value ?: return
        viewModelScope.launch {
            codexRepository.upsertEntry(
                CodexEntryEntity(categoryId = categoryId, scopeType = ScopeType.Book, scopeId = bookId, name = name),
            )
        }
    }

    fun observeEntry(entryId: String) = codexRepository.observeEntry(entryId)

    fun updateEntry(entry: CodexEntryEntity) {
        viewModelScope.launch { codexRepository.upsertEntry(entry.copy(updatedAt = System.currentTimeMillis())) }
    }

    fun deleteEntry(entry: CodexEntryEntity) {
        viewModelScope.launch { codexRepository.deleteEntry(entry) }
    }

    /** Copy action (cog-wheel admin menu): returns the new entry's id so the caller can navigate
     * the editor sheet straight to it, mirroring [createEntry]'s "id back to the caller" shape. */
    fun duplicateEntry(entry: CodexEntryEntity, onCreated: (String) -> Unit) {
        viewModelScope.launch { onCreated(codexRepository.duplicateEntry(entry).id) }
    }

    suspend fun getLore(entryId: String): CodexEntryLoreEntity? = codexRepository.getLore(entryId)

    fun upsertLore(lore: CodexEntryLoreEntity) {
        viewModelScope.launch { codexRepository.upsertLore(lore) }
    }

    /** Scene ids this entry is linked to — the "appears in N scenes" mention list (spec §9). */
    fun observeMentioningSceneIds(entryId: String) = libraryRepository.observeScenesForCodexEntry(entryId)

    /** Imports [uri] (image or video, spec §9) as an entry's illustration — caller then
     * calls [updateEntry] with `imageMediaId` set to the returned entity's id. */
    suspend fun importEntryMedia(uri: Uri): MediaEntity = mediaImporter.importFromUri(uri)

    suspend fun exportCodex(format: ExportFormat): ByteArray? {
        val bookId = currentBookId.value ?: return null
        return codexBackupService.export(bookId, format)
    }

    /** Returns how many entries were imported, merged into the current book's existing codex. */
    suspend fun importCodex(bytes: ByteArray, format: ExportFormat): Int? {
        val bookId = currentBookId.value ?: return null
        return codexBackupService.import(bytes, format, bookId)
    }
}
