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
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A conversation as shown in the Chats list. */
data class RpChatRowUi(
    val chatId: String,
    val title: String,
    val avatarColorHex: String,
    val preview: String,
    val updatedAt: Long,
    /** Which workspace this conversation was last left in. */
    val displayMode: String,
)

@HiltViewModel
class RoleplayChatsViewModel @Inject constructor(
    private val db: WeaverseDatabase,
) : ViewModel() {
    private val _chats = MutableStateFlow<List<RpChatRowUi>>(emptyList())
    val chats: StateFlow<List<RpChatRowUi>> = _chats.asStateFlow()

    init {
        viewModelScope.launch {
            db.roleplayDao().observeChats().collect { entities ->
                _chats.value = entities.map { chat ->
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
                    RpChatRowUi(
                        chatId = chat.id,
                        title = chat.title.ifBlank { name },
                        avatarColorHex = avatarColorHexFor(name, character?.colorHex),
                        preview = preview,
                        updatedAt = chat.updatedAt,
                        displayMode = chat.displayMode.ifBlank { "messenger" },
                    )
                }
            }
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
