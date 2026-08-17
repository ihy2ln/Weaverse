package com.ihy2ln.weaverse.feature.novel.books

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.core.export.ExportFormat
import com.ihy2ln.weaverse.data.db.entity.BookEntity
import com.ihy2ln.weaverse.data.db.entity.ScopeType
import com.ihy2ln.weaverse.data.repo.CodexRepository
import com.ihy2ln.weaverse.data.repo.LibraryRepository
import com.ihy2ln.weaverse.data.repo.observeCurrentBookId
import com.ihy2ln.weaverse.data.settings.AppSettingsRepository
import com.ihy2ln.weaverse.feature.settings.backup.BookBackupService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs the Books rail tab (hamburger menu → Books): switch, create, delete, and
 * duplicate stories. Every other Novel-mode ViewModel reacts to [selectBook] via
 * `data/repo/CurrentBook.kt`'s shared `observeCurrentBookId`. */
@HiltViewModel
class BooksViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val settingsRepository: AppSettingsRepository,
    private val bookBackupService: BookBackupService,
    private val codexRepository: CodexRepository,
) : ViewModel() {
    val books: StateFlow<List<BookEntity>> = libraryRepository.observeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentBookId: StateFlow<String?> = observeCurrentBookId(libraryRepository, settingsRepository)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun selectBook(bookId: String) {
        viewModelScope.launch { settingsRepository.setCurrentBookId(bookId) }
    }

    fun createBook(title: String) {
        viewModelScope.launch {
            val book = BookEntity(title = title)
            libraryRepository.upsertBook(book)
            codexRepository.seedBuiltInCategories(ScopeType.Book, book.id)
            settingsRepository.setCurrentBookId(book.id)
        }
    }

    fun deleteBook(book: BookEntity) {
        viewModelScope.launch { libraryRepository.deleteBook(book) }
    }

    /** Deep-copies [book] (acts/chapters/scenes/codex) via the same JSON structure Settings'
     * export/import uses — a duplicate is just "export, then import back in", renamed so it's
     * distinguishable from the original. Media isn't carried over (same gap as Settings export). */
    fun duplicateBook(book: BookEntity) {
        viewModelScope.launch {
            val bytes = bookBackupService.export(book.id, ExportFormat.Json)
            val copy = bookBackupService.import(bytes, ExportFormat.Json)
            libraryRepository.upsertBook(copy.copy(title = "${book.title} (Copy)"))
            settingsRepository.setCurrentBookId(copy.id)
        }
    }
}
