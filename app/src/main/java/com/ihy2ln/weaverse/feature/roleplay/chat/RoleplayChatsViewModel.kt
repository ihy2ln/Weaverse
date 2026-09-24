package com.ihy2ln.weaverse.feature.roleplay.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.core.roleplay.avatarColorHexFor
import com.ihy2ln.weaverse.core.text.documentFromJson
import com.ihy2ln.weaverse.core.text.plainText
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A conversation as shown in the Chats list. */
data class RpChatRowUi(
    val chatId: String,
    val title: String,
    val avatarColorHex: String,
    val preview: String,
    val updatedAt: Long,
    val displayMode: String,
    val unreadCount: Int = 0,
    val isGroup: Boolean = false,
)

enum class ChatFilter(val label: String) {
    All("All"),
    Unread("Unread"),
    Groups("Groups"),
}

data class RoleplayChatsUiState(
    val chats: List<RpChatRowUi> = emptyList(),
    val query: String = "",
    val filter: ChatFilter = ChatFilter.All,
    val loading: Boolean = true,
)

@HiltViewModel
class RoleplayChatsViewModel @Inject constructor(
    private val db: WeaverseDatabase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RoleplayChatsUiState())
    val uiState: StateFlow<RoleplayChatsUiState> = _uiState.asStateFlow()

    private var allChats: List<RpChatRowUi> = emptyList()

    init {
        viewModelScope.launch {
            db.roleplayDao().observeChats().collect { entities ->
                allChats = entities.map { chat ->
                    val character = chat.characterId?.let { db.roleplayDao().getCharacter(it) }
                    val name = character?.name?.takeIf { it.isNotBlank() } ?: chat.title
                    val latest = db.roleplayDao().getLatestMessage(chat.id)
                    val preview = latest?.let {
                        val text = documentFromJson(it.contentJson).plainText()
                            .replace('\n', ' ')
                            .trim()
                        val prefix = if (it.role == "user") "You: " else ""
                        (prefix + text).take(120)
                    }.orEmpty()
                    // Only messages the character sent after your last visit count.
                    val unread = if (
                        latest != null && latest.role != "user" && latest.createdAt > chat.lastReadAt
                    ) {
                        db.roleplayDao().countUnread(chat.id, chat.lastReadAt)
                    } else {
                        0
                    }
                    RpChatRowUi(
                        chatId = chat.id,
                        title = chat.title.ifBlank { name },
                        avatarColorHex = avatarColorHexFor(name, character?.colorHex),
                        preview = preview,
                        updatedAt = chat.updatedAt,
                        displayMode = chat.displayMode.ifBlank { "messenger" },
                        unreadCount = unread,
                        isGroup = chat.groupId != null,
                    )
                }
                republish()
            }
        }
    }

    fun onQueryChange(value: String) {
        _uiState.update { it.copy(query = value) }
        republish()
    }

    fun onFilterChange(filter: ChatFilter) {
        _uiState.update { it.copy(filter = filter) }
        republish()
    }

    /** Clears the unread badge; called as the chat opens. */
    fun markRead(chatId: String) {
        viewModelScope.launch {
            val chat = db.roleplayDao().getChat(chatId) ?: return@launch
            db.roleplayDao().upsertChat(chat.copy(lastReadAt = System.currentTimeMillis()))
        }
    }

    private fun republish() {
        val state = _uiState.value
        val query = state.query.trim()
        _uiState.update {
            it.copy(
                chats = allChats.filter { row ->
                    val matchesFilter = when (state.filter) {
                        ChatFilter.All -> true
                        ChatFilter.Unread -> row.unreadCount > 0
                        ChatFilter.Groups -> row.isGroup
                    }
                    val matchesQuery = query.isBlank() ||
                        row.title.contains(query, ignoreCase = true) ||
                        row.preview.contains(query, ignoreCase = true)
                    matchesFilter && matchesQuery
                },
                loading = false,
            )
        }
    }
}

/** Short "3h", "2d" style stamp for list rows. */
fun relativeStamp(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
    if (epochMillis <= 0L) return ""
    val delta = (now - epochMillis).coerceAtLeast(0L)
    val minutes = delta / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        hours < 24 -> "${hours}h"
        days < 7 -> "${days}d"
        else -> "${days / 7}w"
    }
}
