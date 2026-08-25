package com.ihy2ln.weaverse.feature.roleplay.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.RpChatEntity
import com.ihy2ln.weaverse.data.db.entities.RpCharacterEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class RoleplayChatsViewModel @Inject constructor(
    private val db: WeaverseDatabase,
) : ViewModel() {
    val chats: StateFlow<List<RpChatEntity>> = db.roleplayDao().observeChats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val characters: StateFlow<List<RpCharacterEntity>> = db.roleplayDao().observeCharacters()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Creates a new chat under [characterId] (null for a general, character-less chat). */
    fun createChat(characterId: String?, title: String, displayMode: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val persona = db.roleplayDao().getPersonas().firstOrNull() ?: return@launch
            val id = "chat-${UUID.randomUUID()}"
            val now = System.currentTimeMillis()
            db.roleplayDao().upsertChat(
                RpChatEntity(
                    id = id,
                    characterId = characterId,
                    personaId = persona.id,
                    title = title,
                    displayMode = displayMode,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            onCreated(id)
        }
    }

    fun deleteChat(id: String) {
        viewModelScope.launch {
            db.roleplayDao().deleteMessagesForChat(id)
            db.roleplayDao().deleteChat(id)
        }
    }
}
