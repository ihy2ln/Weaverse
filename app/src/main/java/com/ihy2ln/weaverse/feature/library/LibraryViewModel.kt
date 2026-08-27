package com.ihy2ln.weaverse.feature.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.core.media.MediaRepository
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.BookEntity
import com.ihy2ln.weaverse.data.db.entities.MediaEntity
import com.ihy2ln.weaverse.data.db.entities.SeriesEntity
import com.ihy2ln.weaverse.data.export.ProjectExportManager
import com.ihy2ln.weaverse.data.export.SampleBookImporter
import com.ihy2ln.weaverse.data.repo.BookRepository
import com.ihy2ln.weaverse.data.repo.SeriesRepository
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import com.ihy2ln.weaverse.feature.shell.WorkspaceHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class LibraryTab { Novels, Series }

/** Books is the default novel list; Series is the other Novels-dropdown choice. */
fun LibraryTab.novelSubLabel(): String = when (this) {
    LibraryTab.Novels -> "Books"
    LibraryTab.Series -> "Series"
}

data class SeriesGroup(
    val series: SeriesEntity?,
    val books: List<BookEntity>,
)

data class LibraryBookCard(
    val book: BookEntity,
    val seriesTitle: String?,
    val coverPath: String?,
)

private data class LibraryCore(
    val books: List<BookEntity>,
    val series: List<SeriesEntity>,
    val prefs: com.ihy2ln.weaverse.data.settings.UserPreferences,
    val media: List<MediaEntity>,
)

data class LibraryUiState(
    val tab: LibraryTab = LibraryTab.Novels,
    val books: List<BookEntity> = emptyList(),
    val cards: List<LibraryBookCard> = emptyList(),
    val series: List<SeriesEntity> = emptyList(),
    val seriesGroups: List<SeriesGroup> = emptyList(),
    val selectedBookId: String = "",
    val newBookTitle: String = "",
    val newSeriesTitle: String = "",
    val assignSeriesId: String = "",
    val status: String = "",
    val busy: Boolean = false,
    val hasIsekaiGacha: Boolean = false,
    val selectingToRemove: Boolean = false,
    val selectedToRemove: Set<String> = emptySet(),
    val recentNovel: HomeRecentWork? = null,
    val recentRpg: HomeRecentWork? = null,
    val recentChat: HomeRecentWork? = null,
    val recentStoryboard: HomeRecentWork? = null,
    val recentNote: HomeRecentWork? = null,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val seriesRepository: SeriesRepository,
    private val settings: SettingsRepository,
    private val mediaRepository: MediaRepository,
    private val exportManager: ProjectExportManager,
    private val sampleBookImporter: SampleBookImporter,
    private val workspaceHistory: WorkspaceHistory,
    private val db: WeaverseDatabase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    val selectedBook = combine(
        settings.preferences,
        bookRepository.observeBooks(),
    ) { prefs, books ->
        books.find { it.id == prefs.selectedBookId } ?: books.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            combine(
                combine(
                    bookRepository.observeBooks(),
                    seriesRepository.observeSeries(),
                    settings.preferences,
                    mediaRepository.observeAll(),
                ) { books, series, prefs, media ->
                    LibraryCore(books, series, prefs, media)
                },
                db.roleplayDao().observeChats(),
                db.snippetDao().observeCategory("notes"),
            ) { core, rpChats, notes ->
                val books = core.books
                val series = core.series
                val prefs = core.prefs
                val media = core.media
                val groups = buildList {
                    series.forEach { s ->
                        add(SeriesGroup(s, books.filter { it.seriesId == s.id }))
                    }
                    val unassigned = books.filter { it.seriesId.isNullOrBlank() }
                    if (unassigned.isNotEmpty()) {
                        add(SeriesGroup(null, unassigned))
                    }
                }
                val cards = books.map { book ->
                    val cover = book.coverMediaId
                        ?.let { id -> media.find { it.id == id } }
                        ?.let { entity ->
                            mediaRepository.resolveFile(entity).takeIf(File::exists)?.absolutePath
                        }
                    LibraryBookCard(
                        book = book,
                        seriesTitle = book.seriesId?.let { id -> series.find { it.id == id }?.title },
                        coverPath = cover,
                    )
                }
                val activeCard = cards.find { it.book.id == prefs.selectedBookId } ?: cards.firstOrNull()
                val messenger = rpChats.firstOrNull { it.displayMode == "messenger" } ?: rpChats.firstOrNull()
                val dungeon = rpChats.firstOrNull { it.displayMode == "dungeonMaster" } ?: rpChats.firstOrNull()
                val manga = rpChats.firstOrNull { it.displayMode == "roleplay" } ?: rpChats.firstOrNull()
                val note = notes.firstOrNull()
                val prev = _uiState.value
                LibraryUiState(
                    tab = prev.tab,
                    books = books,
                    cards = cards,
                    series = series,
                    seriesGroups = groups,
                    selectedBookId = prefs.selectedBookId,
                    newBookTitle = prev.newBookTitle,
                    newSeriesTitle = prev.newSeriesTitle,
                    assignSeriesId = prev.assignSeriesId,
                    status = prev.status,
                    busy = prev.busy,
                    hasIsekaiGacha = books.any { it.title.equals(SampleBookImporter.BOOK_TITLE, ignoreCase = true) },
                    selectingToRemove = prev.selectingToRemove,
                    selectedToRemove = prev.selectedToRemove.filter { id -> books.any { it.id == id } }.toSet(),
                    recentNovel = activeCard?.let {
                        HomeRecentWork(
                            id = it.book.id,
                            title = it.book.title,
                            subtitle = listOfNotNull(it.seriesTitle, it.book.genre.takeIf(String::isNotBlank)).joinToString(" · "),
                            coverPath = it.coverPath,
                        )
                    },
                    recentRpg = dungeon?.let { HomeRecentWork(it.id, it.title, "RPG campaign") },
                    recentChat = messenger?.let { HomeRecentWork(it.id, it.title, "Messenger") },
                    recentStoryboard = manga?.let { HomeRecentWork(it.id, it.title, "Storyboard") },
                    recentNote = note?.let { HomeRecentWork(it.id, it.title.ifBlank { "Note" }, "Shared board") },
                )
            }.collect { _uiState.value = it }
        }
    }

    fun setTab(tab: LibraryTab) = _uiState.update { it.copy(tab = tab) }
    fun onNewBookTitle(value: String) = _uiState.update { it.copy(newBookTitle = value) }
    fun onNewSeriesTitle(value: String) = _uiState.update { it.copy(newSeriesTitle = value) }
    fun onAssignSeriesId(value: String) = _uiState.update { it.copy(assignSeriesId = value) }

    fun createBook(onOpened: (bookId: String, sceneId: String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val title = _uiState.value.newBookTitle.ifBlank { "Untitled Book" }
            val seriesId = _uiState.value.assignSeriesId.ifBlank { null }
            val book = bookRepository.createBook(title, seriesId)
            settings.setSelectedBookId(book.id)
            val sceneId = bookRepository.firstSceneId(book.id)
            _uiState.update { it.copy(newBookTitle = "", assignSeriesId = "") }
            onOpened(book.id, sceneId)
        }
    }

    fun createSeries() {
        viewModelScope.launch {
            val title = _uiState.value.newSeriesTitle.ifBlank { "Untitled Series" }
            val entity = seriesRepository.createSeries(title)
            workspaceHistory.record(
                undo = { seriesRepository.deleteSeries(entity.id) },
                redo = { seriesRepository.updateSeries(entity) },
            )
            _uiState.update { it.copy(newSeriesTitle = "") }
        }
    }

    fun deleteBook(bookId: String) {
        viewModelScope.launch {
            val wasSelected = _uiState.value.selectedBookId == bookId
            val next = _uiState.value.books.firstOrNull { it.id != bookId }?.id.orEmpty()
            bookRepository.deleteBook(bookId)
            if (wasSelected) {
                settings.setSelectedBookId(next.ifBlank { "book-adams-haven-1" })
            }
            _uiState.update {
                it.copy(selectedToRemove = it.selectedToRemove - bookId)
            }
        }
    }

    fun copyBook(bookId: String) {
        viewModelScope.launch {
            val copy = bookRepository.copyBook(bookId)
            if (copy != null) {
                _uiState.update { it.copy(status = "Copied as ${copy.title}") }
            }
        }
    }

    fun setCoverFromUri(bookId: String, uri: Uri) {
        viewModelScope.launch {
            runCatching { mediaRepository.importFromUri(uri) }
                .onSuccess { media ->
                    bookRepository.setCoverMediaId(bookId, media.id)
                    _uiState.update { it.copy(status = "Cover updated") }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(status = "Cover failed: ${err.message}") }
                }
        }
    }

    fun enterSelectToRemove(initialId: String? = null) {
        _uiState.update {
            it.copy(
                selectingToRemove = true,
                selectedToRemove = if (initialId != null) setOf(initialId) else emptySet(),
            )
        }
    }

    fun exitSelectToRemove() {
        _uiState.update { it.copy(selectingToRemove = false, selectedToRemove = emptySet()) }
    }

    fun toggleSelectedToRemove(bookId: String) {
        _uiState.update { state ->
            val next = if (bookId in state.selectedToRemove) {
                state.selectedToRemove - bookId
            } else {
                state.selectedToRemove + bookId
            }
            state.copy(selectedToRemove = next)
        }
    }

    fun deleteSelectedBooks() {
        val ids = _uiState.value.selectedToRemove
        viewModelScope.launch {
            ids.forEach { bookRepository.deleteBook(it) }
            val remaining = _uiState.value.books.filter { it.id !in ids }
            if (_uiState.value.selectedBookId in ids) {
                settings.setSelectedBookId(remaining.firstOrNull()?.id ?: "book-adams-haven-1")
            }
            _uiState.update {
                it.copy(selectingToRemove = false, selectedToRemove = emptySet(), status = "Removed ${ids.size} novel(s)")
            }
        }
    }

    fun deleteSeries(seriesId: String) {
        viewModelScope.launch {
            val series = seriesRepository.getSeries(seriesId) ?: return@launch
            val assigned = _uiState.value.books.filter { it.seriesId == seriesId }
            seriesRepository.deleteSeries(seriesId)
            workspaceHistory.record(
                undo = {
                    seriesRepository.updateSeries(series)
                    assigned.forEach { bookRepository.updateBook(it) }
                },
                redo = { seriesRepository.deleteSeries(seriesId) },
            )
        }
    }

    fun addBookToSeries(bookId: String, seriesId: String) {
        viewModelScope.launch {
            val before = bookRepository.getBook(bookId) ?: return@launch
            bookRepository.setBookSeries(bookId, seriesId)
            val after = bookRepository.getBook(bookId) ?: return@launch
            workspaceHistory.record(
                undo = { bookRepository.updateBook(before) },
                redo = { bookRepository.updateBook(after) },
            )
        }
    }

    fun removeBookFromSeries(bookId: String) {
        viewModelScope.launch {
            val before = bookRepository.getBook(bookId) ?: return@launch
            bookRepository.setBookSeries(bookId, null)
            val after = bookRepository.getBook(bookId) ?: return@launch
            workspaceHistory.record(
                undo = { bookRepository.updateBook(before) },
                redo = { bookRepository.updateBook(after) },
            )
        }
    }

    fun openBook(bookId: String, onOpened: (sceneId: String?) -> Unit) {
        viewModelScope.launch {
            settings.setSelectedBookId(bookId)
            val sceneId = bookRepository.firstSceneId(bookId)
            onOpened(sceneId)
        }
    }

    fun importUri(uri: Uri, onOpened: (bookId: String?) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, status = "Importing…") }
            runCatching { exportManager.importFromUri(uri) }
                .onSuccess { outcome ->
                    outcome.newBookId?.let { settings.setSelectedBookId(it) }
                    _uiState.update { it.copy(busy = false, status = outcome.message) }
                    onOpened(outcome.newBookId)
                }
                .onFailure { err ->
                    _uiState.update { it.copy(busy = false, status = "Import failed: ${err.message}") }
                    onOpened(null)
                }
        }
    }

    fun importBundledSample(onOpened: (bookId: String?) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, status = "Importing Isekai Gacha…") }
            runCatching { sampleBookImporter.importBundledIsekaiGachaIfMissing() }
                .onSuccess { outcome ->
                    val bookId = outcome?.newBookId
                        ?: _uiState.value.books.firstOrNull {
                            it.title.equals(SampleBookImporter.BOOK_TITLE, ignoreCase = true)
                        }?.id
                    bookId?.let { settings.setSelectedBookId(it) }
                    _uiState.update {
                        it.copy(busy = false, status = outcome?.message ?: "Isekai Gacha is already in the library")
                    }
                    onOpened(bookId)
                }
                .onFailure { err ->
                    _uiState.update { it.copy(busy = false, status = "Import failed: ${err.message}") }
                    onOpened(null)
                }
        }
    }
}
