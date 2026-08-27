package com.ihy2ln.weaverse.feature.roleplay.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.RpChatEntity
import com.ihy2ln.weaverse.data.db.entities.RpPersonaEntity
import com.ihy2ln.weaverse.feature.library.HomeModeRouting
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class RoleplayChatsUiState(
    val chats: List<RpChatEntity> = emptyList(),
    val selectionMode: Boolean = false,
    val selectedForRemoval: Set<String> = emptySet(),
    val pendingOpenId: String? = null,
)

@HiltViewModel
class RoleplayChatsViewModel @Inject constructor(
    private val db: WeaverseDatabase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RoleplayChatsUiState())
    val uiState: StateFlow<RoleplayChatsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            db.roleplayDao().observeChats().collect { chats ->
                _uiState.update { state ->
                    state.copy(
                        chats = chats,
                        selectedForRemoval = state.selectedForRemoval.filter { id ->
                            chats.any { it.id == id }
                        }.toSet(),
                    )
                }
            }
        }
    }

    fun enterSelectionMode() = _uiState.update {
        it.copy(selectionMode = true, selectedForRemoval = emptySet())
    }

    fun exitSelectionMode() = _uiState.update {
        it.copy(selectionMode = false, selectedForRemoval = emptySet())
    }

    fun toggleSelectedForRemoval(chatId: String) = _uiState.update { state ->
        val next = state.selectedForRemoval.toMutableSet()
        if (!next.add(chatId)) next.remove(chatId)
        state.copy(selectedForRemoval = next)
    }

    fun removeSelected() {
        val ids = _uiState.value.selectedForRemoval.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { deleteChat(it) }
            _uiState.update { it.copy(selectionMode = false, selectedForRemoval = emptySet()) }
        }
    }

    fun consumePendingOpen() = _uiState.update { it.copy(pendingOpenId = null) }

    fun createChat(displayMode: String = HomeModeRouting.MESSENGER) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val persona = db.roleplayDao().getPersonas().firstOrNull { it.isDefault }
                ?: db.roleplayDao().getPersonas().firstOrNull()
                ?: RpPersonaEntity(
                    id = "persona-${UUID.randomUUID()}",
                    name = "Writer",
                    isDefault = true,
                ).also { db.roleplayDao().upsertPersona(it) }
            val character = db.roleplayDao().getCharacters().firstOrNull()
            val id = "rp-chat-${UUID.randomUUID()}"
            val chat = RpChatEntity(
                id = id,
                characterId = character?.id,
                personaId = persona.id,
                title = character?.name?.takeIf { it.isNotBlank() } ?: "New chat",
                displayMode = HomeModeRouting.normalizeDisplayMode(displayMode),
                createdAt = now,
                updatedAt = now,
            )
            db.roleplayDao().upsertChat(chat)
            _uiState.update { it.copy(pendingOpenId = id) }
        }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            db.roleplayDao().deleteMessagesForChat(chatId)
            db.roleplayDao().deleteChat(chatId)
        }
    }
}
