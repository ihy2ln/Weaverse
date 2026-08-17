package com.ihy2ln.weaverse.feature.roleplay.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.ai.AIChunk
import com.ihy2ln.weaverse.ai.AIMessage
import com.ihy2ln.weaverse.ai.AIMessageRole
import com.ihy2ln.weaverse.ai.AIRequest
import com.ihy2ln.weaverse.ai.AIService
import com.ihy2ln.weaverse.ai.ModelInfo
import com.ihy2ln.weaverse.ai.context.ContextBuilder
import com.ihy2ln.weaverse.ai.context.ContextScope
import com.ihy2ln.weaverse.ai.context.ContextTrigger
import com.ihy2ln.weaverse.ai.context.toContext
import com.ihy2ln.weaverse.core.util.newId
import com.ihy2ln.weaverse.data.db.entity.AIProviderType
import com.ihy2ln.weaverse.data.db.entity.ConnectionProfileEntity
import com.ihy2ln.weaverse.data.db.entity.RpChatEntity
import com.ihy2ln.weaverse.data.db.entity.RpDisplayMode
import com.ihy2ln.weaverse.data.db.entity.RpMessageEntity
import com.ihy2ln.weaverse.data.db.entity.RpMessageRole
import com.ihy2ln.weaverse.data.db.entity.RpCharacterEntity
import com.ihy2ln.weaverse.data.db.entity.RpPersonaEntity
import com.ihy2ln.weaverse.data.db.entity.ScopeType
import com.ihy2ln.weaverse.data.db.entity.nowEpochMillis
import com.ihy2ln.weaverse.data.repo.CodexRepository
import com.ihy2ln.weaverse.data.repo.ConnectionProfileRepository
import com.ihy2ln.weaverse.data.repo.RoleplayRepository
import com.ihy2ln.weaverse.data.settings.AppSettingsRepository
import com.ihy2ln.weaverse.data.settings.SecretsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs Roleplay's Chats screen (spec §8/§10/§11): real streaming chats
 * against one character at a time (spec's core scenario) — group chats
 * ([com.ihy2ln.weaverse.data.db.entity.RpGroupEntity], multiple activation
 * strategies) exist in the Phase 3 schema but aren't wired into any screen
 * yet, deferred to a follow-up (see BUILD_NOTES "Phase 11 deviations/gaps").
 *
 * [sendMessage]/[regenerate] build their own correctly role-tagged
 * `List<AIMessage>` rather than trusting [ContextBuilder]'s own `messages`
 * output for the `Roleplay` scope — reading `ContextBuilder.buildCandidateSections`
 * shows every chat-history section becomes an `AIMessageRole.User` message
 * regardless of the original speaker, which would flatten a real back-and-forth
 * into a wall of "User" turns. That's Phase 9 code with no real caller (and
 * no test) to have caught this until now; rather than change already-shipped,
 * unit-tested code for a call shape it was never exercised against, this
 * ViewModel only uses [com.ihy2ln.weaverse.ai.context.AssembledPrompt.systemBlocks]
 * from `ContextBuilder.build()` (character card / codex / persona /
 * author's note) and constructs history itself — the same pattern Phase 10's
 * Novel Chat already uses for its own reason (see its own KDoc).
 *
 * Swipe cycling ([cycleSwipe]) is button-driven (prev/next), not a drag
 * gesture — the underlying data path (`RoleplayRepository.activateSwipe`)
 * is spec-correct either way; a real swipe gesture is lower-risk to add
 * later against a device than to guess at now (see Phase 6 BUILD_NOTES
 * entry on why gesture code gets extra scrutiny in this project).
 */
@HiltViewModel
class RpChatsViewModel @Inject constructor(
    private val roleplayRepository: RoleplayRepository,
    private val codexRepository: CodexRepository,
    private val connectionProfileRepository: ConnectionProfileRepository,
    private val aiService: AIService,
    private val settingsRepository: AppSettingsRepository,
    private val secretsStore: SecretsStore,
) : ViewModel() {
    val characters: StateFlow<List<RpCharacterEntity>> = roleplayRepository.observeCharacters()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val personas: StateFlow<List<RpPersonaEntity>> = roleplayRepository.observePersonas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val chats: StateFlow<List<RpChatEntity>> = roleplayRepository.observeChats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedChatId = MutableStateFlow<String?>(null)
    val selectedChatId: StateFlow<String?> = _selectedChatId

    fun selectChat(chatId: String?) {
        _selectedChatId.value = chatId
    }

    val currentChat: StateFlow<RpChatEntity?> = combine(chats, _selectedChatId) { list, id -> list.firstOrNull { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val messages: StateFlow<List<RpMessageEntity>> = _selectedChatId.filterNotNull()
        .flatMapLatest { chatId -> roleplayRepository.observeActiveMessages(chatId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val profiles: StateFlow<List<ConnectionProfileEntity>> = connectionProfileRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedProfileId = MutableStateFlow<String?>(null)

    /** Falls back to the first profile that actually has a key over blindly picking whichever
     * sorts first — with no profile-picker UI shown by default (see [profiles]/[selectProfile]
     * for the one this screen now offers), a brand-new keyless default profile at a lower
     * `sortOrder` than a working one the user added later would otherwise silently win and every
     * send would degrade to the mock provider with no obvious reason why (reported: a validated,
     * credential-tested OpenRouter profile still produced "no API key configured" replies). */
    val currentProfile: StateFlow<ConnectionProfileEntity> = combine(profiles, _selectedProfileId) { list, selectedId ->
        list.firstOrNull { it.id == selectedId }
            ?: list.firstOrNull { secretsStore.getApiKey(it.id)?.isNotBlank() == true }
            ?: list.firstOrNull()
            ?: fallbackProfile
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), fallbackProfile)

    fun selectProfile(profileId: String) {
        _selectedProfileId.value = profileId
    }

    private val _selectedModelId = MutableStateFlow("")
    val selectedModelId: StateFlow<String> = _selectedModelId

    fun selectModelId(modelId: String) {
        _selectedModelId.value = modelId
    }

    /** Real model list for [currentProfile] (reusing the exact same call "Test connection"
     * makes) so replies resolve a real, provider-valid model id instead of the previous
     * hardcoded literal `"default"` (not a real model id for any provider, guaranteed to 400).
     * Re-fetches whenever the active profile changes. */
    val availableModels: StateFlow<List<ModelInfo>> = currentProfile
        .flatMapLatest { profile -> flow { emit(aiService.testConnection(profile).getOrNull().orEmpty()) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _streamingText = MutableStateFlow<String?>(null)
    val streamingText: StateFlow<String?> = _streamingText

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending

    /** [personaId] null falls back to the first existing persona, auto-creating a default "You"
     * one if none exist yet — [RpChatEntity.personaId] is a required FK, and there's no dedicated
     * "create a persona" step forced before a first chat. */
    fun createChat(characterId: String, personaId: String?) {
        viewModelScope.launch {
            val character = roleplayRepository.getCharacter(characterId) ?: return@launch
            val resolvedPersonaId = personaId ?: ensureDefaultPersona().id
            val chat = RpChatEntity(
                characterId = characterId,
                personaId = resolvedPersonaId,
                title = character.name,
                displayMode = settingsRepository.defaultRpDisplayMode.first(),
            )
            roleplayRepository.upsertChat(chat)
            if (character.firstMes.isNotBlank()) {
                roleplayRepository.upsertMessage(
                    RpMessageEntity(
                        chatId = chat.id,
                        swipeGroupId = newId(),
                        role = RpMessageRole.Char,
                        speakerCharacterId = characterId,
                        plainText = character.firstMes,
                    ),
                )
            }
            _selectedChatId.value = chat.id
        }
    }

    private suspend fun ensureDefaultPersona(): RpPersonaEntity {
        val existing = personas.value.ifEmpty { roleplayRepository.observePersonas().first() }
        return existing.firstOrNull() ?: RpPersonaEntity(name = "You", isDefault = true).also { roleplayRepository.upsertPersona(it) }
    }

    fun deleteChat(chat: RpChatEntity) {
        viewModelScope.launch {
            roleplayRepository.deleteChat(chat)
            if (_selectedChatId.value == chat.id) _selectedChatId.value = null
        }
    }

    fun sendMessage(userInput: String) {
        val chat = currentChat.value ?: return
        if (userInput.isBlank() || _isSending.value) return

        viewModelScope.launch {
            _isSending.value = true
            val priorHistory = messages.value
            val userMessage = RpMessageEntity(chatId = chat.id, swipeGroupId = newId(), role = RpMessageRole.User, plainText = userInput)
            roleplayRepository.upsertMessage(userMessage)
            generateReply(chat, history = priorHistory + userMessage)
            _isSending.value = false
        }
    }

    fun regenerate() {
        val chat = currentChat.value ?: return
        val last = messages.value.lastOrNull() ?: return
        if (last.role != RpMessageRole.Char || _isSending.value) return

        viewModelScope.launch {
            _isSending.value = true
            generateReply(chat, history = messages.value.dropLast(1), swipeGroupId = last.swipeGroupId)
            _isSending.value = false
        }
    }

    /** Per-chat toggle (spec §9) — switches rendering and the prompt template on the next send;
     * existing messages are unaffected data-wise, they just re-render under the new mode. */
    fun setDisplayMode(mode: RpDisplayMode) {
        val chat = currentChat.value ?: return
        viewModelScope.launch { roleplayRepository.upsertChat(chat.copy(displayMode = mode, updatedAt = nowEpochMillis())) }
    }

    /** Each setter takes `null` to clear that one colour back to the theme default (spec: pickers
     * "reset to theme default") without touching the other two. */
    fun setNarrationColor(hex: String?) {
        val chat = currentChat.value ?: return
        viewModelScope.launch { roleplayRepository.upsertChat(chat.copy(narrationColorHex = hex, updatedAt = nowEpochMillis())) }
    }

    fun setSpeechColor(hex: String?) {
        val chat = currentChat.value ?: return
        viewModelScope.launch { roleplayRepository.upsertChat(chat.copy(speechColorHex = hex, updatedAt = nowEpochMillis())) }
    }

    fun setOocColor(hex: String?) {
        val chat = currentChat.value ?: return
        viewModelScope.launch { roleplayRepository.upsertChat(chat.copy(oocColorHex = hex, updatedAt = nowEpochMillis())) }
    }

    fun editMessage(message: RpMessageEntity, newText: String) {
        viewModelScope.launch {
            roleplayRepository.upsertMessage(message.copy(plainText = newText, isEdited = true))
        }
    }

    fun deleteMessage(message: RpMessageEntity) {
        viewModelScope.launch { roleplayRepository.deleteMessage(message) }
    }

    fun observeSwipeGroup(swipeGroupId: String) = roleplayRepository.observeSwipeGroup(swipeGroupId)

    fun cycleSwipe(swipeGroupId: String, direction: Int) {
        viewModelScope.launch {
            val group = roleplayRepository.observeSwipeGroup(swipeGroupId).first()
            if (group.size <= 1) return@launch
            val activeIndex = group.indexOfFirst { it.isActiveSwipe }.coerceAtLeast(0)
            val nextIndex = (activeIndex + direction + group.size) % group.size
            roleplayRepository.activateSwipe(swipeGroupId, group[nextIndex].id)
        }
    }

    private suspend fun generateReply(chat: RpChatEntity, history: List<RpMessageEntity>, swipeGroupId: String = newId()) {
        val character = chat.characterId?.let { roleplayRepository.getCharacter(it) } ?: return
        val persona = personas.value.firstOrNull { it.id == chat.personaId }

        // Prefer an explicit pick (see [selectModelId]); otherwise the first model this
        // profile's connection actually reported. Fail fast with a clear in-chat message
        // rather than ever sending the placeholder "default" — not a real model id for any
        // provider, guaranteed to 400.
        val resolvedModel = selectedModelId.value.ifBlank { availableModels.value.firstOrNull()?.id.orEmpty() }
        if (resolvedModel.isBlank()) {
            roleplayRepository.upsertMessage(
                RpMessageEntity(
                    chatId = chat.id,
                    swipeGroupId = newId(),
                    role = RpMessageRole.System,
                    plainText = "No model resolved for this connection yet — pick one from the model row, or wait for its model list to finish loading.",
                ),
            )
            return
        }

        val activeEntries = chat.characterId?.let { codexRepository.getActiveEntries(ScopeType.Character, it) }.orEmpty()
        val loreByEntryId = codexRepository.getLoreForEntries(activeEntries.map { it.id }).associateBy { it.entryId }
        val codexContexts = activeEntries.map { entry -> entry.toContext(loreByEntryId[entry.id]) }

        val historyAiMessages = history.map { message ->
            AIMessage(
                role = when (message.role) {
                    RpMessageRole.User -> AIMessageRole.User
                    RpMessageRole.Char -> AIMessageRole.Assistant
                    RpMessageRole.System, RpMessageRole.Narrator -> AIMessageRole.System
                },
                content = message.plainText,
            )
        }
        val lastUserText = history.lastOrNull { it.role == RpMessageRole.User }?.plainText.orEmpty()

        val characterCard = buildString {
            append(character.name)
            if (character.description.isNotBlank()) append("\n${character.description}")
            if (character.personality.isNotBlank()) append("\nPersonality: ${character.personality}")
            if (character.systemPrompt.isNotBlank()) append("\n${character.systemPrompt}")
            // Revision 02 §9: "the chat's prompt template changes accordingly (messenger ->
            // chat-style instruct formatting; DM -> narrative completion formatting)". Messenger
            // keeps the existing plain-turn framing (already chat-style, one AIMessage per
            // turn); DM mode adds the one instruction that actually changes model *output* -
            // everything else about the request (history shape, codex context, system blocks)
            // stays identical, since both modes read the same rp_messages and the same
            // ContextBuilder.build() call above/below this.
            if (chat.displayMode == RpDisplayMode.DungeonMaster) {
                append(
                    "\nWrite your reply as narrative prose, not dialogue-only chat turns: " +
                        "wrap physical actions and narration in *asterisks*, wrap spoken " +
                        "dialogue in \"quotation marks\", and use [square brackets] only for " +
                        "genuine out-of-character asides.",
                )
            }
        }

        val assembled = ContextBuilder.build(
            scope = ContextScope.Roleplay(
                characterCard = characterCard,
                personaText = persona?.description.orEmpty(),
                scenario = character.scenario,
                chatHistory = historyAiMessages,
                authorsNote = chat.authorsNote.takeIf { it.isNotBlank() },
            ),
            trigger = ContextTrigger(lastUserText),
            codexEntries = codexContexts,
        )

        val request = AIRequest(
            model = resolvedModel,
            systemPrompt = assembled.systemBlocks.joinToString("\n\n").takeIf { it.isNotBlank() },
            messages = historyAiMessages,
        )

        val builder = StringBuilder()
        _streamingText.value = ""
        aiService.stream(currentProfile.value, request).collect { chunk ->
            when (chunk) {
                is AIChunk.Delta -> {
                    builder.append(chunk.text)
                    _streamingText.value = builder.toString()
                }
                is AIChunk.Done -> {
                    val finalText = chunk.fullText.ifBlank { builder.toString() }
                    val existingCount = roleplayRepository.observeSwipeGroup(swipeGroupId).first().size
                    val newMessageId = newId()
                    roleplayRepository.upsertMessage(
                        RpMessageEntity(
                            id = newMessageId,
                            chatId = chat.id,
                            swipeGroupId = swipeGroupId,
                            swipeIndex = existingCount,
                            role = RpMessageRole.Char,
                            speakerCharacterId = character.id,
                            plainText = finalText,
                            tokenCount = chunk.outputTokens ?: 0,
                        ),
                    )
                    roleplayRepository.activateSwipe(swipeGroupId, newMessageId)
                    _streamingText.value = null
                }
                is AIChunk.Error -> {
                    roleplayRepository.upsertMessage(
                        RpMessageEntity(
                            chatId = chat.id,
                            swipeGroupId = newId(),
                            role = RpMessageRole.System,
                            plainText = "Error: ${chunk.message}",
                        ),
                    )
                    _streamingText.value = null
                }
            }
        }
    }

    private companion object {
        val fallbackProfile = ConnectionProfileEntity(
            providerType = AIProviderType.OpenAICompatible,
            label = "Mock (no connection profile configured)",
            baseUrl = "",
        )
    }
}
