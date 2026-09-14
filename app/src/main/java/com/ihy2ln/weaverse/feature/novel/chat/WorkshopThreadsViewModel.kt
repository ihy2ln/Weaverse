package com.ihy2ln.weaverse.feature.novel.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.ChatThreadEntity
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkshopThreadsViewModel @Inject constructor(
    private val db: WeaverseDatabase,
    private val settings: SettingsRepository,
) : ViewModel() {
    val threads: StateFlow<List<ChatThreadEntity>> = settings.preferences
        .map { it.selectedBookId }
        .flatMapLatest { bookId -> db.workshopChatDao().observeThreads(bookId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createThread(onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val bookId = settings.preferences.first().selectedBookId
            val now = System.currentTimeMillis()
            val id = "thread-${UUID.randomUUID()}"
            val number = threads.value.size + 1
            db.workshopChatDao().upsertThread(
                ChatThreadEntity(
                    id = id,
                    scopeId = bookId,
                    name = "Chat $number",
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            onCreated(id)
        }
    }

    fun deleteThreads(ids: Set<String>, onRemainingSelected: (String?) -> Unit) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { id ->
                db.workshopChatDao().deleteMessagesForThread(id)
                db.workshopChatDao().deleteThread(id)
            }
            onRemainingSelected(threads.value.firstOrNull { it.id !in ids }?.id)
        }
    }
}
