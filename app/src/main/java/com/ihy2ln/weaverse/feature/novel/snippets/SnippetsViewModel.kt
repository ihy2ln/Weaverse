package com.ihy2ln.weaverse.feature.novel.snippets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.core.export.ExportFormat
import com.ihy2ln.weaverse.data.db.entity.ScopeType
import com.ihy2ln.weaverse.data.db.entity.SnippetEntity
import com.ihy2ln.weaverse.data.repo.LibraryRepository
import com.ihy2ln.weaverse.data.repo.SnippetLabelRepository
import com.ihy2ln.weaverse.data.repo.observeCurrentBookId
import com.ihy2ln.weaverse.data.settings.AppSettingsRepository
import com.ihy2ln.weaverse.feature.settings.backup.SnippetBackupService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs the Snippets rail tab (hamburger menu → Snippets) — reusable bits of prose (character
 * voice notes, recurring SFX descriptions, etc.) scoped to the current book, same
 * `observeCurrentBookId` pattern as every other Novel-mode ViewModel. */
@HiltViewModel
class SnippetsViewModel @Inject constructor(
    private val snippetLabelRepository: SnippetLabelRepository,
    private val libraryRepository: LibraryRepository,
    settingsRepository: AppSettingsRepository,
    private val snippetBackupService: SnippetBackupService,
) : ViewModel() {
    private val bookId: StateFlow<String?> = observeCurrentBookId(libraryRepository, settingsRepository)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val snippets: StateFlow<List<SnippetEntity>> = bookId.filterNotNull()
        .flatMapLatest { snippetLabelRepository.observeSnippets(ScopeType.Book, it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createSnippet(title: String) {
        val id = bookId.value ?: return
        viewModelScope.launch {
            snippetLabelRepository.upsertSnippet(SnippetEntity(scopeType = ScopeType.Book, scopeId = id, title = title, body = ""))
        }
    }

    fun updateSnippet(snippet: SnippetEntity) {
        viewModelScope.launch { snippetLabelRepository.upsertSnippet(snippet) }
    }

    fun deleteSnippet(snippet: SnippetEntity) {
        viewModelScope.launch { snippetLabelRepository.deleteSnippet(snippet) }
    }

    fun togglePinned(snippet: SnippetEntity) {
        viewModelScope.launch { snippetLabelRepository.upsertSnippet(snippet.copy(pinned = !snippet.pinned)) }
    }

    suspend fun exportSnippets(format: ExportFormat): ByteArray? {
        val id = bookId.value ?: return null
        return snippetBackupService.export(id, format)
    }

    suspend fun importSnippets(bytes: ByteArray, format: ExportFormat): Int? {
        val id = bookId.value ?: return null
        return snippetBackupService.import(bytes, format, id)
    }
}
