package com.ihy2ln.weaverse.feature.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.BookEntity
import com.ihy2ln.weaverse.data.db.entities.ChatThreadEntity
import com.ihy2ln.weaverse.data.repo.ChatScopes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** A book and every Workshop chat thread scoped to it — the "main item -> sub chats" grouping. */
data class BookChatGroup(
    val book: BookEntity,
    val threads: List<ChatThreadEntity>,
)

/**
 * Standalone chat hub: aggregates every book's Workshop chat threads (grouped by book, mirroring
 * Library's book selection) plus freestanding "mini chats" that aren't tied to any book at all.
 */
@HiltViewModel
class ChatsHubViewModel @Inject constructor(
    private val db: WeaverseDatabase,
) : ViewModel() {
    val bookGroups: StateFlow<List<BookChatGroup>> = combine(
        db.bookDao().observeAll(),
        db.workshopChatDao().observeAllThreads(),
    ) { books, threads ->
        val byBook = threads.filter { it.scopeId != ChatScopes.MINI }.groupBy { it.scopeId }
        books.mapNotNull { book -> byBook[book.id]?.let { BookChatGroup(book, it) } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val miniChats: StateFlow<List<ChatThreadEntity>> = db.workshopChatDao().observeAllThreads()
        .map { threads -> threads.filter { it.scopeId == ChatScopes.MINI } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createMiniChat(onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val id = "thread-${UUID.randomUUID()}"
            val now = System.currentTimeMillis()
            db.workshopChatDao().upsertThread(
                ChatThreadEntity(
                    id = id,
                    scopeId = ChatScopes.MINI,
                    name = "New chat",
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            onCreated(id)
        }
    }

    fun createThreadForBook(bookId: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val id = "thread-${UUID.randomUUID()}"
            val now = System.currentTimeMillis()
            db.workshopChatDao().upsertThread(
                ChatThreadEntity(
                    id = id,
                    scopeId = bookId,
                    name = "New chat",
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            onCreated(id)
        }
    }

    fun deleteThread(id: String) {
        viewModelScope.launch {
            db.workshopChatDao().deleteMessagesForThread(id)
            db.workshopChatDao().deleteThread(id)
        }
    }
}
