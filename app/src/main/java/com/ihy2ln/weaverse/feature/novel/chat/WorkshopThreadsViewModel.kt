package com.ihy2ln.weaverse.feature.novel.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.ChatThreadEntity
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import com.ihy2ln.weaverse.feature.shell.WorkspaceHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class WorkshopThreadsUiState(
    val threads: List<WorkshopThreadUi> = emptyList(),
    val query: String = "",
    val selectingToRemove: Boolean = false,
    val selectedToRemove: Set<String> = emptySet(),
    val renameThreadId: String? = null,
    val renameDraft: String = "",
) {
    val visible: List<WorkshopThreadUi>
        get() = WorkshopThreadList.filter(threads, query)
    val pinned: List<WorkshopThreadUi>
        get() = WorkshopThreadList.pinned(visible)
    val unpinned: List<WorkshopThreadUi>
        get() = WorkshopThreadList.unpinned(visible)
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkshopThreadsViewModel @Inject constructor(
    private val db: WeaverseDatabase,
    private val settings: SettingsRepository,
    private val workspaceHistory: WorkspaceHistory,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val selectState = MutableStateFlow(SelectState())

    private val threadEntities: StateFlow<List<ChatThreadEntity>> = settings.preferences
        .map { it.selectedBookId }
        .flatMapLatest { id ->
            db.workshopChatDao().observeThreads(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<WorkshopThreadsUiState> = combine(
        threadEntities,
        query,
        selectState,
    ) { entities, q, select ->
        val threads = entities.map { entity ->
            WorkshopThreadUi.from(entity, db.workshopChatDao().countMessages(entity.id))
        }
        WorkshopThreadsUiState(
            threads = threads,
            query = q,
            selectingToRemove = select.selecting,
            selectedToRemove = select.ids.filter { id -> entities.any { it.id == id } }.toSet(),
            renameThreadId = select.renameId,
            renameDraft = select.renameDraft,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkshopThreadsUiState())

    /** Kept for the shell rail, which still reads a simple list. */
    val threads: StateFlow<List<ChatThreadEntity>> = threadEntities

    fun onQuery(value: String) {
        query.value = value
    }

    fun createThread(onCreated: (String) -> Unit = {}) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val id = "thread-${UUID.randomUUID()}"
            val entity = ChatThreadEntity(
                id = id,
                scopeId = settings.preferences.first().selectedBookId,
                name = "New thread",
                pinned = false,
                createdAt = now,
                updatedAt = now,
            )
            db.workshopChatDao().upsertThread(entity)
            workspaceHistory.record(
                undo = { db.workshopChatDao().deleteMessagesForThread(id); db.workshopChatDao().deleteThread(id) },
                redo = { db.workshopChatDao().upsertThread(entity) },
            )
            onCreated(id)
        }
    }

    fun deleteThread(threadId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            val entity = threadEntities.value.find { it.id == threadId } ?: return@launch
            val messages = db.workshopChatDao().getMessages(threadId)
            db.workshopChatDao().deleteMessagesForThread(threadId)
            db.workshopChatDao().deleteThread(threadId)
            workspaceHistory.record(
                undo = {
                    db.workshopChatDao().upsertThread(entity)
                    messages.forEach { db.workshopChatDao().upsertMessage(it) }
                },
                redo = {
                    db.workshopChatDao().deleteMessagesForThread(threadId)
                    db.workshopChatDao().deleteThread(threadId)
                },
            )
            onDeleted()
        }
    }

    fun copyThread(threadId: String, onCopied: (String) -> Unit = {}) {
        viewModelScope.launch {
            val entity = threadEntities.value.find { it.id == threadId } ?: return@launch
            val now = System.currentTimeMillis()
            val newId = "thread-${UUID.randomUUID()}"
            val copy = entity.copy(
                id = newId,
                name = "Copy of ${entity.name}",
                createdAt = now,
                updatedAt = now,
            )
            db.workshopChatDao().upsertThread(copy)
            db.workshopChatDao().getMessages(threadId).forEach { message ->
                db.workshopChatDao().upsertMessage(
                    message.copy(id = "msg-${UUID.randomUUID()}", threadId = newId),
                )
            }
            onCopied(newId)
        }
    }

    fun togglePin(threadId: String) {
        viewModelScope.launch {
            val entity = threadEntities.value.find { it.id == threadId } ?: return@launch
            db.workshopChatDao().upsertThread(
                entity.copy(pinned = !entity.pinned, updatedAt = System.currentTimeMillis()),
            )
        }
    }

    fun beginRename(threadId: String) {
        val name = threadEntities.value.find { it.id == threadId }?.name.orEmpty()
        selectState.update { it.copy(renameId = threadId, renameDraft = name) }
    }

    fun onRenameDraft(value: String) {
        selectState.update { it.copy(renameDraft = value) }
    }

    fun confirmRename() {
        viewModelScope.launch {
            val state = selectState.value
            val id = state.renameId ?: return@launch
            val entity = threadEntities.value.find { it.id == id } ?: return@launch
            db.workshopChatDao().upsertThread(
                entity.copy(name = state.renameDraft.ifBlank { entity.name }, updatedAt = System.currentTimeMillis()),
            )
            selectState.update { it.copy(renameId = null, renameDraft = "") }
        }
    }

    fun dismissRename() {
        selectState.update { it.copy(renameId = null, renameDraft = "") }
    }

    fun enterSelectToRemove(initialId: String? = null) {
        selectState.update {
            it.copy(selecting = true, ids = if (initialId != null) setOf(initialId) else emptySet())
        }
    }

    fun exitSelectToRemove() {
        selectState.update { it.copy(selecting = false, ids = emptySet()) }
    }

    fun toggleSelected(threadId: String) {
        selectState.update { state ->
            val next = if (threadId in state.ids) state.ids - threadId else state.ids + threadId
            state.copy(ids = next)
        }
    }

    fun deleteSelected(onDeleted: () -> Unit = {}) {
        val ids = selectState.value.ids
        viewModelScope.launch {
            ids.forEach { id ->
                db.workshopChatDao().deleteMessagesForThread(id)
                db.workshopChatDao().deleteThread(id)
            }
            selectState.update { it.copy(selecting = false, ids = emptySet()) }
            onDeleted()
        }
    }

    private data class SelectState(
        val selecting: Boolean = false,
        val ids: Set<String> = emptySet(),
        val renameId: String? = null,
        val renameDraft: String = "",
    )
}
