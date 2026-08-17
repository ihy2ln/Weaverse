package com.ihy2ln.weaverse.feature.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.core.media.MediaRepository
import com.ihy2ln.weaverse.data.db.entities.BookEntity
import com.ihy2ln.weaverse.data.db.entities.SeriesEntity
import com.ihy2ln.weaverse.data.repo.BookRepository
import com.ihy2ln.weaverse.data.repo.SeriesRepository
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import com.ihy2ln.weaverse.feature.prompt.PromptEntryBus
import com.ihy2ln.weaverse.feature.prompt.PromptEntryKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ShellBookInfo(
    val book: BookEntity? = null,
    val series: SeriesEntity? = null,
    val backgroundPath: String? = null,
)

@HiltViewModel
class AppShellViewModel @Inject constructor(
    private val settings: SettingsRepository,
    bookRepository: BookRepository,
    seriesRepository: SeriesRepository,
    mediaRepository: MediaRepository,
    private val promptEntryBus: PromptEntryBus,
    private val workspaceHistory: WorkspaceHistory,
) : ViewModel() {
    val preferences = settings.preferences
    val historyState = workspaceHistory.state

    fun undo() {
        viewModelScope.launch { workspaceHistory.undo() }
    }

    fun redo() {
        viewModelScope.launch { workspaceHistory.redo() }
    }

    fun openPrompt(kind: PromptEntryKind) {
        promptEntryBus.requestOpen(kind)
    }

    val shellInfo: StateFlow<ShellBookInfo> = combine(
        settings.preferences,
        bookRepository.observeBooks(),
        seriesRepository.observeSeries(),
        mediaRepository.observeAll(),
    ) { prefs, books, seriesList, media ->
        val book = books.find { it.id == prefs.selectedBookId } ?: books.firstOrNull()
        val series = book?.seriesId?.let { id -> seriesList.find { it.id == id } }
        val bg = prefs.backgroundMediaId.takeIf { it.isNotBlank() }
            ?.let { id -> media.find { it.id == id && it.type == "image" } }
            ?.let { entity ->
                mediaRepository.resolveFile(entity).takeIf(File::exists)?.absolutePath
            }
        ShellBookInfo(book = book, series = series, backgroundPath = bg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShellBookInfo())

    fun setRailWidthDp(width: Float) {
        viewModelScope.launch { settings.setRailWidthDp(width) }
    }

    fun toggleRailCollapsed() {
        viewModelScope.launch {
            val current = settings.preferences.first()
            settings.setRailCollapsed(!current.layout.railCollapsed)
        }
    }

    fun setRailCollapsed(collapsed: Boolean) {
        viewModelScope.launch { settings.setRailCollapsed(collapsed) }
    }

    fun toggleDestBarCollapsed() {
        viewModelScope.launch {
            val prefs = settings.preferences.first()
            settings.setDestBarCollapsed(!prefs.layout.destBarCollapsed)
        }
    }

    fun setDestBarHeightDp(height: Float) {
        viewModelScope.launch { settings.setDestBarHeightDp(height) }
    }

    fun setDestBarCollapsed(collapsed: Boolean) {
        viewModelScope.launch { settings.setDestBarCollapsed(collapsed) }
    }

    fun setSelectedBookId(bookId: String) {
        viewModelScope.launch { settings.setSelectedBookId(bookId) }
    }
}
