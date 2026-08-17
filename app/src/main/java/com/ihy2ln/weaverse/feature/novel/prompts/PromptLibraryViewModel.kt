package com.ihy2ln.weaverse.feature.novel.prompts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.core.util.newId
import com.ihy2ln.weaverse.data.db.entity.PromptEntity
import com.ihy2ln.weaverse.data.db.entity.PromptFolderEntity
import com.ihy2ln.weaverse.data.repo.PromptRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PromptLibraryViewModel @Inject constructor(
    private val repository: PromptRepository,
) : ViewModel() {
    val folders: StateFlow<List<PromptFolderEntity>> =
        repository.observeFolders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun promptsInFolder(folderId: String) = repository.observePromptsInFolder(folderId)

    fun observePrompt(promptId: String) = repository.observePrompt(promptId)

    fun updatePrompt(prompt: PromptEntity) {
        viewModelScope.launch { repository.upsertPrompt(prompt) }
    }

    /** "Non-deletable but duplicable" for system prompts (spec §8.2). */
    fun duplicatePrompt(prompt: PromptEntity) {
        viewModelScope.launch {
            repository.upsertPrompt(
                prompt.copy(
                    id = newId(),
                    name = "${prompt.name} (copy)",
                    isSystem = false,
                ),
            )
        }
    }

    fun deletePrompt(prompt: PromptEntity) {
        if (prompt.isSystem) return
        viewModelScope.launch { repository.deletePrompt(prompt) }
    }
}
