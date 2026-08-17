package com.ihy2ln.weaverse.feature.novel.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.ai.AIChunk
import com.ihy2ln.weaverse.ai.AIMessage
import com.ihy2ln.weaverse.ai.AIMessageRole
import com.ihy2ln.weaverse.ai.AIRequest
import com.ihy2ln.weaverse.ai.AIService
import com.ihy2ln.weaverse.ai.ModelInfo
import com.ihy2ln.weaverse.ai.context.CodexEntryContext
import com.ihy2ln.weaverse.ai.context.ContextBuilder
import com.ihy2ln.weaverse.ai.context.ContextScope
import com.ihy2ln.weaverse.ai.context.ContextTrigger
import com.ihy2ln.weaverse.ai.context.SeriesContext
import com.ihy2ln.weaverse.ai.context.toContext
import com.ihy2ln.weaverse.core.export.ExportFormat
import com.ihy2ln.weaverse.core.text.DocumentJson
import com.ihy2ln.weaverse.core.text.MentionCandidate
import com.ihy2ln.weaverse.core.text.wordCount
import com.ihy2ln.weaverse.data.db.entity.AIProviderType
import com.ihy2ln.weaverse.data.db.entity.ChatMessageEntity
import com.ihy2ln.weaverse.data.db.entity.ChatRole
import com.ihy2ln.weaverse.data.db.entity.ChatThreadEntity
import com.ihy2ln.weaverse.data.db.entity.CodexEntryEntity
import com.ihy2ln.weaverse.data.db.entity.ConnectionProfileEntity
import com.ihy2ln.weaverse.data.db.entity.ScopeType
import com.ihy2ln.weaverse.data.repo.ChatRepository
import com.ihy2ln.weaverse.data.repo.CodexRepository
import com.ihy2ln.weaverse.data.repo.ConnectionProfileRepository
import com.ihy2ln.weaverse.data.repo.LibraryRepository
import com.ihy2ln.weaverse.data.repo.observeCurrentBookId
import com.ihy2ln.weaverse.data.settings.AppSettingsRepository
import com.ihy2ln.weaverse.data.settings.SecretsStore
import com.ihy2ln.weaverse.feature.settings.backup.ChatBackupService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import javax.inject.Inject

/**
 * Backs the Workshop Chat screen (spec §8/§9/§10). [bookId] follows
 * whichever book the Books rail tab has selected (see
 * `data/repo/CurrentBook.kt`). One [ChatThreadEntity] per book is
 * auto-created on first visit (`scopeId` = the book id, matching Codex's
 * `ScopeType.Book` scoping) — the spec's multi-thread-per-book UI
 * (rename/pin/switch threads) isn't built yet, see BUILD_NOTES "Phase 10
 * deviations/gaps".
 *
 * [sendMessage] prepends the thread's own prior turns to
 * [ContextBuilder]'s assembled messages rather than changing
 * [ContextBuilder] itself — Phase 9's `Novel` scope only folds previous
 * *scenes* into the scan text, not previous *chat turns*, into
 * [com.ihy2ln.weaverse.ai.context.AssembledPrompt.messages] (only the
 * `Roleplay` scope does that). Keeping Phase 9's already-tested algorithm
 * untouched and doing the conversation-history concatenation here is lower
 * risk than changing code with no test coverage for this call shape.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    settingsRepository: AppSettingsRepository,
    private val chatRepository: ChatRepository,
    private val codexRepository: CodexRepository,
    private val connectionProfileRepository: ConnectionProfileRepository,
    private val aiService: AIService,
    private val chatBackupService: ChatBackupService,
    private val secretsStore: SecretsStore,
) : ViewModel() {
    private val bookId: StateFlow<String?> = observeCurrentBookId(libraryRepository, settingsRepository)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Backs clickable codex mentions in chat bubbles ([com.ihy2ln.weaverse.core.ui.CodexMentionText]) —
     * the same book-scoped entry list [sendMessage] already scans for AI context injection, now
     * also exposed to the screen so it can render taps on those same names. */
    val bookCodexEntries: StateFlow<List<CodexEntryEntity>> = bookId.filterNotNull()
        .flatMapLatest { codexRepository.observeEntriesForScope(ScopeType.Book, it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** [bookCodexEntries] + their lore's `trackByNameAlias`/case/whole-word settings, mapped into
     * [MentionCandidate] for [com.ihy2ln.weaverse.core.ui.CodexMentionText] — kept as a separate
     * derived flow rather than computed in the screen so the lore fetch (a suspend call) has one
     * home instead of being repeated on every recomposition. */
    val bookMentionCandidates: StateFlow<List<MentionCandidate>> = bookCodexEntries
        .map { entries ->
            val loreByEntryId = codexRepository.getLoreForEntries(entries.map { it.id }).associateBy { it.entryId }
            entries.filterNot { it.disabled }.map { entry ->
                val lore = loreByEntryId[entry.id]
                MentionCandidate(
                    entryId = entry.id,
                    name = entry.name,
                    aliases = entry.aliases,
                    tracked = lore?.trackByNameAlias ?: true,
                    caseSensitive = lore?.caseSensitive ?: false,
                    matchWholeWords = lore?.matchWholeWords ?: true,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val profiles: StateFlow<List<ConnectionProfileEntity>> = connectionProfileRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedProfileId = MutableStateFlow<String?>(null)

    /** Falls back to an unsaved, key-less profile when the user hasn't set up a real connection
     * profile yet (Phase 8's screen isn't linked into nav yet) — [AIService] reports a real,
     * explicit "no API key configured" error for it rather than generating anything. Prefers a
     * profile that actually has a key over blindly picking whichever sorts first — a keyless
     * default at a lower `sortOrder` than a working profile added later would otherwise silently
     * win every send (see the identical fix + its full rationale on `RpChatsViewModel.currentProfile`). */
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

    /** Real model list for [currentProfile] (the same call "Test connection" makes). Every send
     * used to hardcode `model = "default"` (via [ChatThreadEntity.modelRef], a field nothing
     * ever wrote) — not a real model id for any provider, guaranteed to 400 on a real
     * connection. Re-fetches whenever the active profile changes. */
    val availableModels: StateFlow<List<ModelInfo>> = currentProfile
        .flatMapLatest { profile -> flow { emit(aiService.testConnection(profile).getOrNull().orEmpty()) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val thread: StateFlow<ChatThreadEntity?> = bookId.filterNotNull()
        .flatMapLatest { id -> chatRepository.observeThreads(id).map { it.firstOrNull() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val messages: StateFlow<List<ChatMessageEntity>> = thread.filterNotNull()
        .flatMapLatest { chatRepository.observeMessages(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _streamingText = MutableStateFlow<String?>(null)
    val streamingText: StateFlow<String?> = _streamingText

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending

    init {
        viewModelScope.launch {
            bookId.filterNotNull().collect { id ->
                if (chatRepository.observeThreads(id).first().isEmpty()) {
                    chatRepository.upsertThread(ChatThreadEntity(scopeId = id, name = "Workshop Chat"))
                }
            }
        }
    }

    fun sendMessage(userInput: String) {
        val threadEntity = thread.value ?: return
        if (userInput.isBlank() || _isSending.value) return
        val profile = currentProfile.value

        viewModelScope.launch {
            _isSending.value = true

            // Prefer an explicit pick; otherwise the first model this profile's connection
            // actually reported. Fail fast with a clear message rather than ever sending the
            // placeholder "default" — not a real model id for any provider, guaranteed to 400.
            val resolvedModel = selectedModelId.value.ifBlank { availableModels.value.firstOrNull()?.id.orEmpty() }
            if (resolvedModel.isBlank()) {
                chatRepository.upsertMessage(
                    ChatMessageEntity(
                        threadId = threadEntity.id,
                        role = ChatRole.System,
                        plainText = "No model resolved for this connection yet — pick one from the model row, or wait for its model list to finish loading.",
                        wordCount = 0,
                    ),
                )
                _isSending.value = false
                return@launch
            }

            // Snapshot history *before* persisting this turn's user message — ContextBuilder's
            // assembled.messages already appends `userInput` itself (via ContextTrigger), so
            // reading `messages.value` after the upsert could race Room's Flow re-emission and
            // double up the just-sent turn.
            val priorMessages = messages.value.map {
                AIMessage(
                    role = if (it.role == ChatRole.Assistant) AIMessageRole.Assistant else AIMessageRole.User,
                    content = it.plainText,
                )
            }

            chatRepository.upsertMessage(
                ChatMessageEntity(
                    threadId = threadEntity.id,
                    role = ChatRole.User,
                    plainText = userInput,
                    wordCount = userInput.wordCount(),
                ),
            )

            val activeEntries = codexRepository.getActiveEntries(ScopeType.Book, threadEntity.scopeId)
            val loreByEntryId = codexRepository.getLoreForEntries(activeEntries.map { it.id }).associateBy { it.entryId }
            val codexContexts = activeEntries.map { entry -> entry.toContext(loreByEntryId[entry.id]) }

            // Revision 02 §3: when this book belongs to a series, fold in the series premise +
            // prior members' summaries (empty until a future pass adds a per-member summary
            // editor -- SeriesMemberEntity.summary exists in the schema, this just reads it) and
            // every series-scoped constant/matching codex entry, same as book-scoped ones.
            var seriesContext: SeriesContext? = null
            var seriesCodexContexts = emptyList<CodexEntryContext>()
            val seriesId = libraryRepository.getBook(threadEntity.scopeId)?.seriesId
            if (seriesId != null) {
                val series = libraryRepository.getSeries(seriesId)
                val members = libraryRepository.observeSeriesMembers(seriesId).first()
                val currentMember = members.firstOrNull { it.memberId == threadEntity.scopeId }
                val priorSummaries = if (currentMember == null) {
                    emptyList()
                } else {
                    members.filter { it.sortOrder < currentMember.sortOrder && it.summary.isNotBlank() }
                        .sortedByDescending { it.sortOrder }
                        .map { it.summary }
                }
                seriesContext = SeriesContext(
                    premise = series?.premise.orEmpty(),
                    previousMemberSummaries = priorSummaries,
                )

                val seriesActiveEntries = codexRepository.getActiveEntries(ScopeType.Series, seriesId)
                val seriesLoreByEntryId = codexRepository.getLoreForEntries(seriesActiveEntries.map { it.id }).associateBy { it.entryId }
                seriesCodexContexts = seriesActiveEntries.map { entry -> entry.toContext(seriesLoreByEntryId[entry.id]) }
            }

            val assembled = ContextBuilder.build(
                scope = ContextScope.Novel(currentSceneText = "", seriesContext = seriesContext),
                trigger = ContextTrigger(userInput),
                codexEntries = codexContexts + seriesCodexContexts,
            )

            val request = AIRequest(
                model = resolvedModel,
                systemPrompt = assembled.systemBlocks.joinToString("\n\n").takeIf { it.isNotBlank() },
                messages = priorMessages + assembled.messages,
            )

            val builder = StringBuilder()
            _streamingText.value = ""
            aiService.stream(profile, request).collect { chunk ->
                when (chunk) {
                    is AIChunk.Delta -> {
                        builder.append(chunk.text)
                        _streamingText.value = builder.toString()
                    }
                    is AIChunk.Done -> {
                        val finalText = chunk.fullText.ifBlank { builder.toString() }
                        chatRepository.upsertMessage(
                            ChatMessageEntity(
                                threadId = threadEntity.id,
                                role = ChatRole.Assistant,
                                plainText = finalText,
                                wordCount = finalText.wordCount(),
                                tokenCount = chunk.outputTokens ?: 0,
                                contextUsedJson = DocumentJson.encodeToString(assembled.usedEntryIds),
                            ),
                        )
                        _streamingText.value = null
                    }
                    is AIChunk.Error -> {
                        chatRepository.upsertMessage(
                            ChatMessageEntity(
                                threadId = threadEntity.id,
                                role = ChatRole.System,
                                plainText = "Error: ${chunk.message}",
                                wordCount = 0,
                            ),
                        )
                        _streamingText.value = null
                    }
                }
            }
            _isSending.value = false
        }
    }

    /** Edit/copy/delete parity with Roleplay's chat, which already has a per-message pencil
     * action (reported: this screen had no way to edit or delete a message at all). */
    fun editMessage(message: ChatMessageEntity, newText: String) {
        viewModelScope.launch {
            chatRepository.upsertMessage(message.copy(plainText = newText, wordCount = newText.wordCount()))
        }
    }

    fun deleteMessage(message: ChatMessageEntity) {
        viewModelScope.launch { chatRepository.deleteMessage(message) }
    }

    suspend fun exportChat(format: ExportFormat): ByteArray? {
        val threadId = thread.value?.id ?: return null
        return chatBackupService.export(threadId, format)
    }

    /** Returns how many messages were appended, or null if there's no thread to import into yet. */
    suspend fun importChat(bytes: ByteArray, format: ExportFormat): Int? {
        val threadId = thread.value?.id ?: return null
        return chatBackupService.import(bytes, format, threadId)
    }

    private companion object {
        val fallbackProfile = ConnectionProfileEntity(
            providerType = AIProviderType.OpenAICompatible,
            label = "Mock (no connection profile configured)",
            baseUrl = "",
        )
    }
}
