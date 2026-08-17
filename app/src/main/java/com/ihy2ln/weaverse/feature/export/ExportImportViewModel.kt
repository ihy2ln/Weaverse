package com.ihy2ln.weaverse.feature.export

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.data.export.ExportFormat
import com.ihy2ln.weaverse.data.export.ExportOptions
import com.ihy2ln.weaverse.data.export.ExportSceneNode
import com.ihy2ln.weaverse.data.export.ProjectExportManager
import com.ihy2ln.weaverse.data.export.SceneDivider
import com.ihy2ln.weaverse.data.repo.BookRepository
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExportImportUiState(
    val bookId: String = "",
    val bookTitle: String = "",
    val sceneNodes: List<ExportSceneNode> = emptyList(),
    val format: ExportFormat = ExportFormat.Markdown,
    val options: ExportOptions = ExportOptions(),
    val status: String = "",
    val busy: Boolean = false,
    val tab: ExportTab = ExportTab.Novel,
)

enum class ExportTab { Novel, Roleplay, Notes }

@HiltViewModel
class ExportImportViewModel @Inject constructor(
    private val exportManager: ProjectExportManager,
    private val bookRepository: BookRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ExportImportUiState())
    val uiState: StateFlow<ExportImportUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settings.preferences.collect { prefs ->
                val bookId = prefs.selectedBookId
                val book = bookRepository.observeBook(bookId).first()
                val tree = exportManager.loadSceneTree(bookId)
                _uiState.update {
                    it.copy(
                        bookId = bookId,
                        bookTitle = book?.title ?: bookId,
                        sceneNodes = tree,
                    )
                }
            }
        }
    }

    fun setTab(tab: ExportTab) = _uiState.update { it.copy(tab = tab) }

    fun setFormat(format: ExportFormat) = _uiState.update { it.copy(format = format) }

    fun setDivider(divider: SceneDivider) {
        _uiState.update { it.copy(options = it.options.copy(sceneDivider = divider)) }
    }

    fun toggleAllScenes() {
        _uiState.update { state ->
            val allOn = state.sceneNodes.all { it.selected }
            state.copy(sceneNodes = state.sceneNodes.map { it.copy(selected = !allOn) })
        }
    }

    fun toggleAct(actId: String) {
        _uiState.update { state ->
            val actNodes = state.sceneNodes.filter { it.actId == actId }
            val turnOn = actNodes.any { !it.selected }
            state.copy(
                sceneNodes = state.sceneNodes.map {
                    if (it.actId == actId) it.copy(selected = turnOn) else it
                },
            )
        }
    }

    fun toggleChapter(chapterId: String) {
        _uiState.update { state ->
            val chapterNodes = state.sceneNodes.filter { it.chapterId == chapterId }
            val turnOn = chapterNodes.any { !it.selected }
            state.copy(
                sceneNodes = state.sceneNodes.map {
                    if (it.chapterId == chapterId) it.copy(selected = turnOn) else it
                },
            )
        }
    }

    fun toggleScene(sceneId: String) {
        _uiState.update { state ->
            state.copy(
                sceneNodes = state.sceneNodes.map {
                    if (it.sceneId == sceneId) it.copy(selected = !it.selected) else it
                },
            )
        }
    }

    fun updateOptions(transform: (ExportOptions) -> ExportOptions) {
        _uiState.update { it.copy(options = transform(it.options)) }
    }

    fun export() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, status = "Exporting…") }
            runCatching {
                when (state.tab) {
                    ExportTab.Novel -> {
                        val selected = state.sceneNodes.filter { it.selected }.map { it.sceneId }.toSet()
                        if (selected.isEmpty()) error("Select at least one scene")
                        exportManager.exportNovel(
                            bookId = state.bookId,
                            format = state.format,
                            selectedSceneIds = selected,
                            options = state.options,
                        )
                    }
                    ExportTab.Roleplay -> exportManager.exportRoleplay(
                        state.options.copy(includeRoleplay = true),
                    )
                    ExportTab.Notes -> exportManager.exportNotes()
                }
            }.onSuccess { path ->
                _uiState.update { it.copy(busy = false, status = "Exported to $path") }
            }.onFailure { err ->
                _uiState.update { it.copy(busy = false, status = "Export failed: ${err.message}") }
            }
        }
    }

    fun importUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, status = "Importing…") }
            runCatching { exportManager.importFromUri(uri) }
                .onSuccess { outcome ->
                    outcome.newBookId?.let { settings.setSelectedBookId(it) }
                    val bookId = outcome.newBookId ?: _uiState.value.bookId
                    val book = bookRepository.observeBook(bookId).first()
                    val tree = exportManager.loadSceneTree(bookId)
                    _uiState.update {
                        it.copy(
                            busy = false,
                            bookId = bookId,
                            bookTitle = book?.title ?: bookId,
                            sceneNodes = tree,
                            status = outcome.message,
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(busy = false, status = "Import failed: ${err.message}") }
                }
        }
    }
}
