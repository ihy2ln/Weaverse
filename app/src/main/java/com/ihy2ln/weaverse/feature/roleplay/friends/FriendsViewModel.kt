package com.ihy2ln.weaverse.feature.roleplay.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.core.roleplay.DAILY_CHARACTER_TAG
import com.ihy2ln.weaverse.core.roleplay.DailyCharacterGenerator
import com.ihy2ln.weaverse.core.roleplay.avatarColorHexFor
import com.ihy2ln.weaverse.core.text.Document
import com.ihy2ln.weaverse.core.text.decodeAliases
import com.ihy2ln.weaverse.core.text.documentFromJson
import com.ihy2ln.weaverse.core.text.plainText
import com.ihy2ln.weaverse.core.text.toJson
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.RpCharacterEntity
import com.ihy2ln.weaverse.data.db.entities.RpChatEntity
import com.ihy2ln.weaverse.data.db.entities.RpMessageEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** One row in the friends list — a character, plus their conversation if one exists. */
data class FriendUi(
    val characterId: String,
    val name: String,
    val avatarColorHex: String,
    val monogram: String,
    /** Last message if they have a chat, otherwise the character's description. */
    val subtitle: String,
    val chatId: String? = null,
    val lastMessageAt: Long = 0L,
    val isNew: Boolean = false,
) {
    val hasChat: Boolean get() = chatId != null
}

data class FriendsUiState(
    val query: String = "",
    val directMessages: List<FriendUi> = emptyList(),
    val everyoneElse: List<FriendUi> = emptyList(),
    val loading: Boolean = true,
    val generating: Boolean = false,
    val status: String = "",
) {
    val isEmpty: Boolean get() = directMessages.isEmpty() && everyoneElse.isEmpty()
}

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val db: WeaverseDatabase,
    private val dailyCharacterGenerator: DailyCharacterGenerator,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FriendsUiState())
    val uiState: StateFlow<FriendsUiState> = _uiState.asStateFlow()

    private var characters: List<RpCharacterEntity> = emptyList()
    private var chats: List<RpChatEntity> = emptyList()

    init {
        viewModelScope.launch {
            combine(
                db.roleplayDao().observeCharacters(),
                db.roleplayDao().observeChats(),
            ) { chars, allChats -> chars to allChats }
                .collect { (chars, allChats) ->
                    characters = chars
                    chats = allChats
                    rebuild()
                }
        }
    }

    fun onQueryChange(value: String) {
        _uiState.update { it.copy(query = value) }
        viewModelScope.launch { rebuild() }
    }

    /** Opens the character's existing conversation, creating one on first contact. */
    fun openChatWith(characterId: String, onReady: (String) -> Unit) {
        viewModelScope.launch {
            db.roleplayDao().getChatForCharacter(characterId)?.let {
                onReady(it.id)
                return@launch
            }
            val character = db.roleplayDao().getCharacter(characterId) ?: return@launch
            val now = System.currentTimeMillis()
            val chat = RpChatEntity(
                id = "rp-chat-${UUID.randomUUID()}",
                characterId = characterId,
                personaId = defaultPersonaId(),
                title = character.name,
                displayMode = "messenger",
                createdAt = now,
                updatedAt = now,
            )
            db.roleplayDao().upsertChat(chat)
            seedGreeting(chat, character, now)
            onReady(chat.id)
        }
    }

    fun generateNewPersonNow() {
        if (_uiState.value.generating) return
        _uiState.update { it.copy(generating = true, status = "") }
        viewModelScope.launch {
            val result = runCatching { dailyCharacterGenerator.generateNow() }
            _uiState.update {
                it.copy(
                    generating = false,
                    status = result.fold(
                        onSuccess = { made ->
                            if (made != null) "Say hi to ${made.name}." else "Add an OpenRouter API key in Settings to meet new people."
                        },
                        onFailure = { "Couldn't reach the model just now — try again later." },
                    ),
                )
            }
        }
    }

    fun clearStatus() = _uiState.update { it.copy(status = "") }

    private suspend fun defaultPersonaId(): String =
        db.roleplayDao().getPersonas().firstOrNull { it.isDefault }?.id
            ?: db.roleplayDao().getPersonas().firstOrNull()?.id
            ?: "persona-default"

    /** A character's `firstMes` becomes their opening message, the way a DM would start. */
    private suspend fun seedGreeting(chat: RpChatEntity, character: RpCharacterEntity, now: Long) {
        val greeting = character.firstMes.trim()
        if (greeting.isBlank()) return
        db.roleplayDao().upsertMessage(
            RpMessageEntity(
                id = "rpm-${UUID.randomUUID()}",
                chatId = chat.id,
                swipeGroupId = "sw-${UUID.randomUUID()}",
                swipeIndex = 0,
                isActiveSwipe = true,
                role = "assistant",
                speakerCharacterId = character.id,
                contentJson = Document.fromPlainText(greeting).toJson(),
                createdAt = now,
                displayMode = "messenger",
            ),
        )
    }

    private suspend fun rebuild() {
        val query = _uiState.value.query.trim()
        val chatByCharacter = chats
            .filter { it.characterId != null }
            .associateBy { it.characterId!! }

        val rows = characters.map { character ->
            val chat = chatByCharacter[character.id]
            val preview = chat?.let { latestPreview(it.id) }
            val tags = runCatching { decodeAliases(character.tagsJson) }.getOrDefault(emptyList())
            FriendUi(
                characterId = character.id,
                name = character.name,
                avatarColorHex = avatarColorHexFor(character.name, character.colorHex),
                monogram = monogramOf(character.name),
                subtitle = preview?.takeIf { it.isNotBlank() }
                    ?: character.description.lineSequence().firstOrNull()?.trim().orEmpty(),
                chatId = chat?.id,
                lastMessageAt = chat?.updatedAt ?: 0L,
                isNew = tags.any { it.equals(DAILY_CHARACTER_TAG, ignoreCase = true) },
            )
        }.filter { row ->
            query.isBlank() ||
                row.name.contains(query, ignoreCase = true) ||
                row.subtitle.contains(query, ignoreCase = true)
        }

        _uiState.update {
            it.copy(
                directMessages = rows.filter { r -> r.hasChat }.sortedByDescending { r -> r.lastMessageAt },
                everyoneElse = rows.filterNot { r -> r.hasChat }
                    .sortedWith(compareByDescending<FriendUi> { r -> r.isNew }.thenBy { r -> r.name.lowercase() }),
                loading = false,
            )
        }
    }

    private suspend fun latestPreview(chatId: String): String {
        val message = db.roleplayDao().getLatestMessage(chatId) ?: return ""
        val text = documentFromJson(message.contentJson).plainText().replace('\n', ' ').trim()
        val prefix = if (message.role == "user") "You: " else ""
        return (prefix + text).take(120)
    }
}

/** Up to two initials, for the avatar circle when a character has no picture. */
fun monogramOf(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}
