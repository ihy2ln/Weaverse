package com.ihy2ln.weaverse.feature.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.core.media.MediaRepository
import com.ihy2ln.weaverse.data.db.entities.BookEntity
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
                bookRepository.observeBooks(),
                seriesRepository.observeSeries(),
                settings.preferences,
                mediaRepository.observeAll(),
            ) { books, series, prefs, media ->
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
                LibraryUiState(
                    tab = _uiState.value.tab,
                    books = books,
                    cards = cards,
                    series = series,
                    seriesGroups = groups,
                    selectedBookId = prefs.selectedBookId,
                    newBookTitle = _uiState.value.newBookTitle,
                    newSeriesTitle = _uiState.value.newSeriesTitle,
                    assignSeriesId = _uiState.value.assignSeriesId,
                    status = _uiState.value.status,
                    busy = _uiState.value.busy,
                    hasIsekaiGacha = books.any { it.title.equals(SampleBookImporter.BOOK_TITLE, ignoreCase = true) },
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
