package com.ihy2ln.weaverse.feature.novel.codex

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.data.db.entities.CodexCategoryEntity
import com.ihy2ln.weaverse.data.db.entities.CodexEntryEntity
import com.ihy2ln.weaverse.data.repo.CodexRepository
import com.ihy2ln.weaverse.feature.shell.WorkspaceHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CodexCategoryGroup(
    val category: CodexCategoryEntity,
    val entries: List<CodexEntryEntity>,
    val expanded: Boolean = true,
)

data class CodexUiState(
    val scope: String = "All",
    val bookId: String = "",
    val entries: List<CodexEntryEntity> = emptyList(),
    val grouped: List<CodexCategoryGroup> = emptyList(),
)

@HiltViewModel
class CodexViewModel @Inject constructor(
    private val codexRepository: CodexRepository,
    private val workspaceHistory: WorkspaceHistory,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CodexUiState())
    val uiState: StateFlow<CodexUiState> = _uiState.asStateFlow()
    private val collapsed = MutableStateFlow<Set<String>>(emptySet())

    init {
        viewModelScope.launch {
            codexRepository.ensureGlobalAndMigrate()
            combine(
                codexRepository.observeAllCategories(),
                codexRepository.observeAllEntries(),
                collapsed,
            ) { categories, entries, collapsedIds ->
                val grouped = categories.map { cat ->
                    CodexCategoryGroup(
                        category = cat,
                        entries = entries.filter { it.categoryId == cat.id },
                        expanded = cat.id !in collapsedIds,
                    )
                }
                CodexUiState(
                    scope = "All",
                    bookId = "",
                    entries = entries,
                    grouped = grouped,
                )
            }.collect { _uiState.value = it }
        }
    }

    fun setScope(scope: String) {
        // Kept so older UI callers compile; Codex is always global.
    }

    fun toggleCategory(categoryId: String) {
        collapsed.update { current ->
            if (categoryId in current) current - categoryId else current + categoryId
        }
    }

    fun addEntry(categoryId: String) {
        viewModelScope.launch {
            val entity = codexRepository.addEntry(categoryId)
            workspaceHistory.record(
                undo = { codexRepository.deleteEntry(entity.id) },
                redo = { codexRepository.saveEntry(entity) },
            )
            collapsed.update { it - categoryId }
        }
    }

    fun removeEntry(entryId: String) {
        viewModelScope.launch {
            val existing = codexRepository.getEntry(entryId) ?: return@launch
            codexRepository.deleteEntry(entryId)
            workspaceHistory.record(
                undo = { codexRepository.saveEntry(existing) },
                redo = { codexRepository.deleteEntry(entryId) },
            )
        }
    }
}
