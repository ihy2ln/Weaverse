package com.ihy2ln.weaverse.feature.novel.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.ai.openrouter.WritingModelSeeds
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.ChatThreadEntity
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class WorkshopThreadsUiState(
    val threads: List<ChatThreadEntity> = emptyList(),
    val selectionMode: Boolean = false,
    val selectedForRemoval: Set<String> = emptySet(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkshopThreadsViewModel @Inject constructor(
    private val db: WeaverseDatabase,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WorkshopThreadsUiState())
    val uiState: StateFlow<WorkshopThreadsUiState> = _uiState.asStateFlow()

    private val threadsFlow = settings.preferences
        .map { it.selectedBookId }
        .flatMapLatest { bookId -> db.workshopChatDao().observeThreads(bookId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            threadsFlow.collect { threads ->
                _uiState.update { it.copy(threads = threads) }
            }
        }
    }

    fun enterSelectionMode() = _uiState.update { it.copy(selectionMode = true, selectedForRemoval = emptySet()) }
    fun exitSelectionMode() = _uiState.update { it.copy(selectionMode = false, selectedForRemoval = emptySet()) }
    fun toggleSelectedForRemoval(threadId: String) = _uiState.update { state ->
        val next = state.selectedForRemoval.toMutableSet()
        if (!next.add(threadId)) next.remove(threadId)
        state.copy(selectedForRemoval = next)
    }

    fun removeSelected() {
        val ids = _uiState.value.selectedForRemoval.toList()
        viewModelScope.launch {
            ids.forEach { deleteThread(it) }
            _uiState.update { it.copy(selectionMode = false, selectedForRemoval = emptySet()) }
        }
    }

    fun createThread() {
        viewModelScope.launch {
            val bookId = settings.preferences.first().selectedBookId
            val now = System.currentTimeMillis()
            val thread = ChatThreadEntity(
                id = "thread-${UUID.randomUUID()}",
                scopeId = bookId,
                name = "New thread",
                pinned = false,
                modelRef = WritingModelSeeds.DEFAULT_MODEL_REF,
                createdAt = now,
                updatedAt = now,
            )
            db.workshopChatDao().upsertThread(thread)
        }
    }

    fun deleteThread(threadId: String) {
        viewModelScope.launch {
            db.workshopChatDao().deleteMessagesForThread(threadId)
            db.workshopChatDao().deleteThread(threadId)
        }
    }
}
