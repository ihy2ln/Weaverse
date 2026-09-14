package com.ihy2ln.weaverse.feature.chatting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.ai.AIChunk
import com.ihy2ln.weaverse.ai.AIError
import com.ihy2ln.weaverse.ai.AiGenerationService
import com.ihy2ln.weaverse.ai.ModelInfo
import com.ihy2ln.weaverse.ai.context.ContextMeter
import com.ihy2ln.weaverse.ai.context.ContextMeterReading
import com.ihy2ln.weaverse.ai.openrouter.OpenRouterModelCache
import com.ihy2ln.weaverse.ai.prompt.RoleplayPromptBuilder
import com.ihy2ln.weaverse.core.roleplay.avatarColorHexFor
import com.ihy2ln.weaverse.core.text.Document
import com.ihy2ln.weaverse.core.text.documentFromJson
import com.ihy2ln.weaverse.core.text.plainText
import com.ihy2ln.weaverse.core.text.toJson
import com.ihy2ln.weaverse.core.ui.util.UsageFormat
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.BookEntity
import com.ihy2ln.weaverse.data.db.entities.RpCharacterEntity
import com.ihy2ln.weaverse.data.db.entities.RpChatEntity
import com.ihy2ln.weaverse.data.db.entities.RpMessageEntity
import com.ihy2ln.weaverse.data.db.entities.RpPersonaEntity
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import com.ihy2ln.weaverse.feature.prompt.PromptModelSelection
import com.ihy2ln.weaverse.feature.prompt.PromptWordLimit
import com.ihy2ln.weaverse.feature.roleplay.friends.monogramOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/** One work (novel or campaign) shown as a Discord "server" icon in the rail. */
data class DiscordServerUi(
    val bookId: String,
    val title: String,
    val workType: String,
    val monogram: String,
    val colorHex: String,
)

/** One room in the channel sidebar, or one DM under Home. */
data class DiscordRoomUi(
    val chatId: String,
    val bookId: String?,
    val name: String,
    /** channel | character | dm */
    val kind: String,
    val characterId: String?,
    val avatarColorHex: String,
    val monogram: String,
    val topic: String,
    val unread: Int = 0,
    val preview: String = "",
    val lastMessageAt: Long = 0L,
)

/** One rendered message row in the Discord pane. */
data class DiscordMessageUi(
    val id: String,
    val authorName: String,
    val authorColorHex: String,
    val isUser: Boolean,
    val isBot: Boolean,
    val text: String,
    val hasMedia: Boolean,
    val mediaPaths: List<String> = emptyList(),
    val createdAt: Long,
)

data class DiscordChatUiState(
    val servers: List<DiscordServerUi> = emptyList(),
    /** null = Home (direct messages). */
    val selectedServerId: String? = null,
    val selectedServer: DiscordServerUi? = null,
    val rooms: List<DiscordRoomUi> = emptyList(),
    val directMessages: List<DiscordRoomUi> = emptyList(),
    val selectedRoomId: String? = null,
    val selectedRoom: DiscordRoomUi? = null,
    val messages: List<DiscordMessageUi> = emptyList(),
    val input: String = "",
    val isStreaming: Boolean = false,
    val streamingText: String = "",
    val errorMessage: String = "",
    val lastUsage: String = "",
    val loading: Boolean = true,
    val minimumWords: Int = 50,
    val maximumWords: Int = 300,
    /** /A = AI generation, \M = manual entry without a model call. */
    val aiMode: Boolean = true,
    val contextMeterLabel: String = "",
    /** Blank means follow the Writing model selected in Settings. */
    val selectedModelRef: String = "",
    val defaultModelRef: String = "",
    val writingModels: List<ModelInfo> = emptyList(),
    /** >0 asks the screen to open the media picker (+ button in the dock). */
    val mediaPickRequestId: Long = 0,
    /** Shows when the + button has staged media for the next message. */
    val hasPendingMedia: Boolean = false,
) {
    val wordRangeValid: Boolean
        get() = minimumWords in PromptWordLimit.Minimum..PromptWordLimit.Maximum &&
            maximumWords in PromptWordLimit.Minimum..PromptWordLimit.Maximum &&
            minimumWords <= maximumWords
}

/** Room-kind marker used for work text channels. */
const val ROOM_KIND_CHANNEL = "channel"

/** Room-kind marker for per-character rooms inside a work's server. */
const val ROOM_KIND_CHARACTER = "character"

/** Room-kind marker for direct messages. */
const val ROOM_KIND_DM = "dm"

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DiscordChatViewModel @Inject constructor(
    private val db: WeaverseDatabase,
    private val aiGeneration: AiGenerationService,
    private val settings: SettingsRepository,
    private val roomSeeder: ChatRoomSeeder,
    private val modelCache: OpenRouterModelCache,
    private val mediaRepository: com.ihy2ln.weaverse.core.media.MediaRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscordChatUiState())
    val uiState: StateFlow<DiscordChatUiState> = _uiState.asStateFlow()

    private var charactersById: Map<String, RpCharacterEntity> = emptyMap()
    private var booksById: Map<String, BookEntity> = emptyMap()
    private var defaultModelRef: String = ""
    private var contextLimit: Int = ContextMeter.DEFAULT_LIMIT
    private var generateJob: Job? = null
    private var boundRoom: RpChatEntity? = null
    private var lastClearedInput: String = ""
    /** Media attached via the dock's + button, sent with the next message. */
    private var pendingMedia: List<com.ihy2ln.weaverse.data.db.entities.MediaEntity> = emptyList()

    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())

    init {
        viewModelScope.launch {
            combine(
                db.bookDao().observeAll(),
                db.roleplayDao().observeChats(),
                db.roleplayDao().observeCharacters(),
            ) { books, chats, characters ->
                Triple(books, chats, characters)
            }.collect { (books, chats, characters) ->
                booksById = books.associateBy { it.id }
                charactersById = characters.associateBy { it.id }
                // Auto-generate rooms for every server, including works the user
                // just created; the seeder's in-memory guard keeps repeats cheap.
                books.filter { it.workType in SERVER_WORK_TYPES }.forEach { book ->
                    roomSeeder.ensureRoomsForBook(book)
                }
                val servers = books
                    .filter { it.workType in SERVER_WORK_TYPES }
                    .sortedByDescending { it.updatedAt }
                    .map { book ->
                        DiscordServerUi(
                            bookId = book.id,
                            title = book.title,
                            workType = book.workType,
                            monogram = monogramOf(book.title).take(1),
                            colorHex = avatarColorHexFor(book.title, null),
                        )
                    }
                _uiState.update {
                    it.copy(
                        servers = servers,
                        selectedServer = it.selectedServerId?.let { id -> servers.find { s -> s.bookId == id } },
                        loading = false,
                    )
                }
                rebuildRooms(chats)
                refreshBadges()
            }
        }
        viewModelScope.launch {
            combine(settings.preferences, modelCache.models) { prefs, dtos ->
                prefs.defaultModelRef to modelCache.toModelInfo(dtos)
            }.collect { (defaultRef, models) ->
                defaultModelRef = defaultRef
                contextLimit = ContextMeter.limitFor(activeModelRef(), models)
                _uiState.update { it.copy(writingModels = models, defaultModelRef = defaultRef) }
                refreshContextMeter()
            }
        }
        viewModelScope.launch {
            _uiState.map { it.selectedRoomId }.distinctUntilChanged().flatMapLatest { roomId ->
                if (roomId.isNullOrBlank()) {
                    flowOf(emptyList())
                } else {
                    db.roleplayDao().observeMessages(roomId, "messenger")
                }
            }.collect { messages ->
                publishMessages(messages)
                refreshBadges()
                refreshContextMeter()
            }
        }
    }

    fun selectServer(bookId: String?) {
        if (_uiState.value.selectedServerId == bookId) return
        generateJob?.cancel()
        generateJob = null
        _uiState.update {
            it.copy(
                selectedServerId = bookId,
                selectedServer = bookId?.let { id -> it.servers.find { s -> s.bookId == id } },
                selectedRoomId = null,
                selectedRoom = null,
                messages = emptyList(),
                isStreaming = false,
                streamingText = "",
            )
        }
        if (bookId != null) {
            // Defensive catch-up for legacy works; new works are seeded at creation.
            viewModelScope.launch { booksById[bookId]?.let { roomSeeder.ensureRoomsForBook(it) } }
        }
    }

    fun selectRoom(chatId: String?) {
        generateJob?.cancel()
        generateJob = null
        _uiState.update {
            it.copy(
                selectedRoomId = chatId,
                selectedRoom = it.rooms.find { r -> r.chatId == chatId }
                    ?: it.directMessages.find { r -> r.chatId == chatId },
                messages = emptyList(),
                isStreaming = false,
                streamingText = "",
                errorMessage = "",
            )
        }
        if (chatId != null) {
            viewModelScope.launch {
                db.roleplayDao().getChat(chatId)?.let { chat ->
                    boundRoom = chat
                    if (chat.lastReadAt < System.currentTimeMillis() - READ_GRACE_MS) {
                        val read = chat.copy(lastReadAt = System.currentTimeMillis())
                        db.roleplayDao().upsertChat(read)
                        boundRoom = read
                    }
                }
            }
        } else {
            boundRoom = null
        }
    }

    fun onInputChange(value: String) {
        _uiState.update { it.copy(input = value, errorMessage = "") }
        refreshContextMeter()
    }

    /** Latest draft, for speech callbacks that must not capture stale state. */
    fun currentInput(): String = _uiState.value.input

    /** 🎲 hold-menu action: append a fresh d20 roll to the draft. */
    fun rollDice() {
        val roll = (1..20).random()
        _uiState.update {
            val base = it.input.trimEnd()
            it.copy(input = if (base.isBlank()) "[d20: $roll]" else "$base [d20: $roll]")
        }
    }

    /** + dock button: attach pictures/videos to the next message. */
    fun requestMediaPick() {
        _uiState.update { it.copy(mediaPickRequestId = it.mediaPickRequestId + 1) }
    }

    fun attachMedia(uris: List<android.net.Uri>) {
        viewModelScope.launch {
            pendingMedia = runCatching { mediaRepository.importFromUris(uris) }.getOrDefault(emptyList())
            _uiState.update { it.copy(hasPendingMedia = pendingMedia.isNotEmpty()) }
        }
    }

    /** ⌫ tap: delete the draft entry (stashed so hold can undo it). */
    fun clearInput() {
        lastClearedInput = _uiState.value.input
        _uiState.update { it.copy(input = "", errorMessage = "") }
    }

    /** ⌫ press-and-hold: restore the last deleted draft. */
    fun undoClearInput() {
        if (lastClearedInput.isBlank()) return
        _uiState.update { it.copy(input = lastClearedInput, errorMessage = "") }
        lastClearedInput = ""
    }

    fun updateMinimumWords(words: Int) {
        if (words !in PromptWordLimit.Minimum..PromptWordLimit.Maximum) return
        _uiState.update { it.copy(minimumWords = words.coerceAtMost(it.maximumWords)) }
    }

    fun updateMaximumWords(words: Int) {
        if (words !in PromptWordLimit.Minimum..PromptWordLimit.Maximum) return
        _uiState.update { it.copy(maximumWords = words.coerceAtLeast(it.minimumWords)) }
    }

    /** /A ↔ \M: AI generation vs manual entry without a model call. */
    fun toggleAiMode() {
        _uiState.update { it.copy(aiMode = !it.aiMode) }
    }

    private fun activeModelRef(): String =
        PromptModelSelection.effectiveModelRef(_uiState.value.selectedModelRef, defaultModelRef)

    fun selectModel(modelId: String) {
        _uiState.update { it.copy(selectedModelRef = PromptModelSelection.modelRef(modelId)) }
        contextLimit = ContextMeter.limitFor(activeModelRef(), _uiState.value.writingModels)
        refreshContextMeter()
    }

    fun useDefaultModel() {
        _uiState.update { it.copy(selectedModelRef = "") }
        contextLimit = ContextMeter.limitFor(activeModelRef(), _uiState.value.writingModels)
        refreshContextMeter()
    }

    fun cancelGeneration() {
        generateJob?.cancel()
        generateJob = null
        _uiState.update { it.copy(isStreaming = false, streamingText = "", errorMessage = "Cancelled") }
    }

    /** Creates a new user-named text channel in the selected server. */
    fun createChannel(name: String) {
        val serverId = _uiState.value.selectedServerId ?: return
        val clean = name.trim().trimStart('#').trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            val book = booksById[serverId] ?: return@launch
            roomSeeder.createRoom(
                book = book,
                name = clean,
                kind = ROOM_KIND_CHANNEL,
                characterId = null,
                topic = "A channel about ${book.title}.",
                character = null,
            )
        }
    }

    /** Creates a per-character room inside the selected server, seeded with their greeting. */
    fun createCharacterRoom(characterId: String) {
        val serverId = _uiState.value.selectedServerId ?: return
        viewModelScope.launch {
            val book = booksById[serverId] ?: return@launch
            val character = db.roleplayDao().getCharacter(characterId) ?: return@launch
            val existing = db.roleplayDao().observeRoomsForBook(serverId).first()
                .firstOrNull { it.roomKind == ROOM_KIND_CHARACTER && it.characterId == characterId }
            if (existing != null) {
                selectRoom(existing.id)
                return@launch
            }
            val chat = roomSeeder.createRoom(
                book = book,
                name = character.name,
                kind = ROOM_KIND_CHARACTER,
                characterId = character.id,
                topic = "A private room where ${character.name} hangs out.",
                character = character,
            )
            selectRoom(chat.id)
        }
    }

    /** Long-press delete: removes the room and every message inside it. */
    fun deleteRoom(chatId: String) {
        viewModelScope.launch {
            db.roleplayDao().getMessages(chatId).forEach { db.roleplayDao().deleteMessage(it.id) }
            db.roleplayDao().deleteChat(chatId)
            if (_uiState.value.selectedRoomId == chatId) {
                _uiState.update { it.copy(selectedRoomId = null, selectedRoom = null, messages = emptyList()) }
            }
        }
    }

    fun send() {
        val state = _uiState.value
        if (state.selectedRoomId == null || state.isStreaming) return
        if (state.input.isBlank() && pendingMedia.isEmpty()) return
        val room = boundRoom ?: return
        val userText = state.input.trim()
        val media = pendingMedia
        if (state.aiMode) {
            if (!state.wordRangeValid) return
            generateJob?.cancel()
            generateJob = viewModelScope.launch {
                val modelRef = activeModelRef()
                if (!aiGeneration.hasApiKey(modelRef)) {
                    _uiState.update { it.copy(errorMessage = AIError.NoApiKey().message.orEmpty()) }
                    return@launch
                }
                val now = System.currentTimeMillis()
                val userMessage = RpMessageEntity(
                    id = "rpm-$now",
                    chatId = room.id,
                    swipeGroupId = "sw-$now",
                    swipeIndex = 0,
                    isActiveSwipe = true,
                    role = "user",
                    contentJson = userMessageDocument(userText, media).toJson(),
                    createdAt = now,
                    displayMode = "messenger",
                )
                db.roleplayDao().upsertMessage(userMessage)
                pendingMedia = emptyList()
                _uiState.update { it.copy(hasPendingMedia = false) }
                _uiState.update { it.copy(input = "", isStreaming = true, streamingText = "", errorMessage = "") }
                generateReply(room, userText, now, userMessageAlreadyStored = true)
            }
        } else {
            // \M manual mode: file the text as a user message without a model call.
            generateJob?.cancel()
            generateJob = viewModelScope.launch {
                val now = System.currentTimeMillis()
                db.roleplayDao().upsertMessage(
                    RpMessageEntity(
                        id = "rpm-$now",
                        chatId = room.id,
                        swipeGroupId = "sw-$now",
                        swipeIndex = 0,
                        isActiveSwipe = true,
                        role = "user",
                        contentJson = userMessageDocument(userText, media).toJson(),
                        createdAt = now,
                        displayMode = "messenger",
                    ),
                )
                pendingMedia = emptyList()
                _uiState.update { it.copy(hasPendingMedia = false) }
                db.roleplayDao().upsertChat(room.copy(updatedAt = now))
                _uiState.update { it.copy(input = "", errorMessage = "") }
            }
        }
    }

    /** Text paragraph plus any attached media blocks, for outgoing messages. */
    private fun userMessageDocument(
        text: String,
        media: List<com.ihy2ln.weaverse.data.db.entities.MediaEntity>,
    ) = Document(
        blocks = buildList {
            if (text.isNotBlank()) {
                add(com.ihy2ln.weaverse.core.text.Paragraph("p-${System.currentTimeMillis()}", listOf(com.ihy2ln.weaverse.core.text.Span(text))))
            }
            media.forEach { item ->
                add(
                    com.ihy2ln.weaverse.core.text.MediaBlock(
                        id = "mb-${UUID.randomUUID()}",
                        mediaId = item.id,
                        kind = com.ihy2ln.weaverse.core.media.MediaRepository.kindForType(item.type),
                    ),
                )
            }
        },
    )

    /** ↻ hold-menu action: delete the latest AI reply and regenerate it. */
    fun retry() {
        val state = _uiState.value
        if (state.isStreaming || state.selectedRoomId == null) return
        val room = boundRoom ?: return
        generateJob?.cancel()
        generateJob = viewModelScope.launch {
            val modelRef = activeModelRef()
            if (!aiGeneration.hasApiKey(modelRef)) {
                _uiState.update { it.copy(errorMessage = AIError.NoApiKey().message.orEmpty()) }
                return@launch
            }
            val messages = db.roleplayDao().getMessagesForMode(room.id, "messenger")
                .filter { it.isActiveSwipe }
            val lastReply = messages.lastOrNull { it.role != "user" } ?: return@launch
            val lastUser = messages.lastOrNull { it.role == "user" } ?: return@launch
            db.roleplayDao().deleteMessage(lastReply.id)
            _uiState.update { it.copy(isStreaming = true, streamingText = "", errorMessage = "") }
            val userText = documentFromJson(lastUser.contentJson).plainText().trim()
            generateReply(room, userText, lastUser.createdAt, userMessageAlreadyStored = true)
        }
    }

    /** » hold-menu action: keep the conversation going without a new prompt. */
    fun continueConversation() {
        if (_uiState.value.isStreaming || _uiState.value.selectedRoomId == null) return
        val room = boundRoom ?: return
        generateJob?.cancel()
        generateJob = viewModelScope.launch {
            val modelRef = activeModelRef()
            if (!aiGeneration.hasApiKey(modelRef)) {
                _uiState.update { it.copy(errorMessage = AIError.NoApiKey().message.orEmpty()) }
                return@launch
            }
            _uiState.update { it.copy(isStreaming = true, streamingText = "", errorMessage = "") }
            generateReply(room, "Continue the conversation.", System.currentTimeMillis(), userMessageAlreadyStored = false)
        }
    }

    /**
     * Streams one AI reply into [room]. When [userMessageAlreadyStored] is false
     * the instruction is sent to the model but never persisted as a message.
     */
    private suspend fun generateReply(
        room: RpChatEntity,
        userText: String,
        baseTimestamp: Long,
        userMessageAlreadyStored: Boolean,
    ) {
        val state = _uiState.value
        val now = baseTimestamp
        val mentioned = mentionedCharacters(userText)
        val roomCharacter = room.characterId?.let { charactersById[it] }
        val replySpeaker = roomCharacter ?: mentioned.singleOrNull()
        val history = db.roleplayDao().getMessagesForMode(room.id, "messenger")
            .filter { it.isActiveSwipe }
            .takeLast(HISTORY_LIMIT)
            .map { msg ->
                val role = if (msg.role == "user") "user" else "assistant"
                role to documentFromJson(msg.contentJson).plainText()
            }
        val persona = roomSeeder.defaultPersona()
        val book = room.bookId?.let { booksById[it] }
        val system = buildSystemBlocks(room, book, roomCharacter, persona, mentioned, state.maximumWords)
        val maxTokens = (state.maximumWords * 1.7 + 192).toInt().coerceIn(192, 8192)
        val builder = StringBuilder()
        var usageText = ""
        var promptTokens = 0
        var completionTokens = 0
        var costUsd = 0.0
        runCatching {
            aiGeneration.stream(
                userMessage = userText,
                assembled = com.ihy2ln.weaverse.ai.context.AssembledPrompt(
                    systemBlocks = system,
                    messages = history,
                    usedEntries = emptyList(),
                    tokenBreakdown = emptyList(),
                ),
                modelRef = activeModelRef(),
                maxTokens = maxTokens,
                temperature = 0.8,
            ).collect { chunk ->
                when (chunk) {
                    is AIChunk.Delta -> {
                        builder.append(chunk.text)
                        _uiState.update { it.copy(streamingText = builder.toString().trim()) }
                    }
                    is AIChunk.Usage -> {
                        promptTokens = chunk.promptTokens
                        completionTokens = chunk.completionTokens
                        costUsd = chunk.cost ?: 0.0
                        usageText = UsageFormat.formatUsage(
                            promptTokens = chunk.promptTokens,
                            completionTokens = chunk.completionTokens,
                            totalTokens = chunk.totalTokens,
                            cost = chunk.cost,
                        )
                    }
                    is AIChunk.RetryWait -> {
                        _uiState.update {
                            it.copy(errorMessage = "Rate limited — retry in ${chunk.secondsLeft}s")
                        }
                    }
                    AIChunk.Done -> Unit
                }
            }
        }.onFailure { err ->
            _uiState.update {
                it.copy(
                    isStreaming = false,
                    streamingText = "",
                    errorMessage = err.message?.takeIf { m -> m.isNotBlank() }
                        ?: "Generation failed — check your model and API key.",
                )
            }
            return
        }
        val replyText = PromptWordLimit.trim(builder.toString().trim(), state.maximumWords)
        if (replyText.isBlank()) {
            if (userMessageAlreadyStored && userText.isNotBlank() && state.input.isBlank()) {
                _uiState.update {
                    it.copy(
                        input = userText,
                        isStreaming = false,
                        streamingText = "",
                        errorMessage = "The model returned nothing. Your message was restored — tap Send to retry.",
                    )
                }
            } else {
                _uiState.update {
                    it.copy(isStreaming = false, streamingText = "", errorMessage = "The model returned nothing.")
                }
            }
            return
        }
        val reply = RpMessageEntity(
            id = "rpm-${now + 1}",
            chatId = room.id,
            swipeGroupId = "sw-$now",
            swipeIndex = 0,
            isActiveSwipe = true,
            role = "char",
            speakerCharacterId = replySpeaker?.id,
            contentJson = Document.fromPlainText(replyText).toJson(),
            createdAt = System.currentTimeMillis(),
            displayMode = "messenger",
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            costUsd = costUsd,
        )
        db.roleplayDao().upsertMessage(reply)
        db.roleplayDao().upsertChat(room.copy(updatedAt = System.currentTimeMillis()))
        _uiState.update {
            it.copy(isStreaming = false, streamingText = "", lastUsage = usageText)
        }
    }

    /** "context: used / limit" estimate from the room's system prompt, history, and draft. */
    private fun refreshContextMeter() {
        val state = _uiState.value
        val room = boundRoom ?: return
        val roomCharacter = room.characterId?.let { charactersById[it] }
        val book = room.bookId?.let { booksById[it] }
        viewModelScope.launch {
            val persona = roomSeeder.defaultPersona()
            val system = buildSystemBlocks(room, book, roomCharacter, persona, emptyList(), state.maximumWords)
            val historyTokens = db.roleplayDao().getMessagesForMode(room.id, "messenger")
                .filter { it.isActiveSwipe }
                .sumOf { ContextMeter.estimateTokens(documentFromJson(it.contentJson).plainText()) }
            val used = system.sumOf { ContextMeter.estimateTokens(it) } +
                historyTokens +
                ContextMeter.estimateTokens(state.input)
            val label = ContextMeterReading(used, contextLimit).label
            _uiState.update { it.copy(contextMeterLabel = label) }
        }
    }

    // ------------------------------------------------------------ rendering

    private fun rebuildRooms(chats: List<RpChatEntity>) {
        val state = _uiState.value
        val rooms = chats
            .filter { it.bookId != null && it.displayMode == "messenger" && it.roomKind in ROOM_KINDS }
            .groupBy { it.bookId!! }
        val selectedRooms = state.selectedServerId?.let { rooms[it] }.orEmpty()
            .sortedWith(
                compareBy<RpChatEntity> { it.roomKind != ROOM_KIND_CHANNEL }
                    .thenBy { it.createdAt },
            )
            .map { it.toRoomUi() }
        val dms = chats
            .filter { it.roomKind == ROOM_KIND_DM || (it.roomKind.isEmpty() && it.displayMode == "messenger" && it.bookId == null) }
            .sortedByDescending { it.updatedAt }
            .map { it.toRoomUi() }
        _uiState.update {
            it.copy(
                rooms = selectedRooms,
                directMessages = dms,
                selectedRoom = (selectedRooms + dms).find { r -> r.chatId == it.selectedRoomId },
            )
        }
    }

    private fun RpChatEntity.toRoomUi(): DiscordRoomUi {
        val character = characterId?.let { charactersById[it] }
        return DiscordRoomUi(
            chatId = id,
            bookId = bookId,
            name = title,
            kind = roomKind.ifBlank { ROOM_KIND_DM },
            characterId = characterId,
            avatarColorHex = character?.let { avatarColorHexFor(it.name, it.colorHex) }
                ?: avatarColorHexFor(title, null),
            monogram = monogramOf(title),
            topic = authorsNote,
            lastMessageAt = updatedAt,
        )
    }

    private suspend fun publishMessages(messages: List<RpMessageEntity>) {
        val state = _uiState.value
        val room = state.selectedRoom
        val roomCharacter = state.selectedRoom?.characterId?.let { charactersById[it] }
        val serverTitle = state.selectedServer?.title
        val rows = messages
            .filter { it.isActiveSwipe }
            .map { msg ->
                val character = msg.speakerCharacterId?.let { charactersById[it] }
                val isUser = msg.role == "user"
                val authorName = when {
                    isUser -> "You"
                    character != null -> character.name
                    roomCharacter != null -> roomCharacter.name
                    serverTitle != null -> "$serverTitle Narrator"
                    else -> "Narrator"
                }
                DiscordMessageUi(
                    id = msg.id,
                    authorName = authorName,
                    authorColorHex = character?.let { avatarColorHexFor(it.name, it.colorHex) }
                        ?: avatarColorHexFor(authorName, null),
                    isUser = isUser,
                    isBot = !isUser && character == null,
                    text = documentFromJson(msg.contentJson).plainText().trim(),
                    hasMedia = documentFromJson(msg.contentJson).hasMedia(),
                    mediaPaths = mediaPathsOf(msg),
                    createdAt = msg.createdAt,
                )
            }
        _uiState.update { it.copy(messages = rows) }
    }

    /** Resolvable image paths from a message's media blocks, for inline display. */
    private suspend fun mediaPathsOf(message: RpMessageEntity): List<String> =
        documentFromJson(message.contentJson).blocks.flatMap { block ->
            if (block is com.ihy2ln.weaverse.core.text.MediaBlock) {
                val entity = mediaRepository.getById(block.mediaId)
                if (entity != null && entity.type == "image") {
                    listOf(mediaRepository.resolveFile(entity).absolutePath)
                } else {
                    emptyList()
                }
            } else {
                emptyList()
            }
        }

    private suspend fun refreshBadges() {
        val state = _uiState.value
        val allRooms = state.rooms + state.directMessages
        if (allRooms.isEmpty()) return
        var changed = false
        val updated = allRooms.map { room ->
            val chat = db.roleplayDao().getChat(room.chatId)
            val unread = chat?.let { db.roleplayDao().countUnread(it.id, it.lastReadAt) } ?: 0
            val latest = db.roleplayDao().getLatestMessage(room.chatId)
            val preview = latest?.let { msg ->
                val text = documentFromJson(msg.contentJson).plainText().replace('\n', ' ').trim()
                (if (msg.role == "user") "You: " else "") + text
            }.orEmpty().take(90)
            if (unread != room.unread || preview != room.preview) changed = true
            room.copy(unread = unread, preview = preview)
        }
        if (!changed) return
        _uiState.update { current ->
            current.copy(
                rooms = updated.filter { it.bookId != null && it.kind != ROOM_KIND_DM }
                    .filter { r -> current.rooms.any { it.chatId == r.chatId } },
                directMessages = updated.filter { r -> current.directMessages.any { it.chatId == r.chatId } },
                selectedRoom = (current.rooms + current.directMessages)
                    .find { it.chatId == current.selectedRoomId },
            )
        }
    }

    // ----------------------------------------------------------- prompting

    private fun mentionedCharacters(text: String): List<RpCharacterEntity> {
        if (!text.contains('@')) return emptyList()
        val matched = mutableSetOf<String>()
        charactersById.values.forEach { character ->
            val full = Regex("@${Regex.escape(character.name)}", RegexOption.IGNORE_CASE)
            if (full.containsMatchIn(text)) {
                matched.add(character.id)
                return@forEach
            }
            val firstName = character.name.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
            if (firstName.length >= 3 &&
                Regex("@${Regex.escape(firstName)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
            ) {
                matched.add(character.id)
            }
        }
        return matched.mapNotNull { charactersById[it] }
    }

    private fun buildSystemBlocks(
        room: RpChatEntity,
        book: BookEntity?,
        roomCharacter: RpCharacterEntity?,
        persona: RpPersonaEntity,
        mentioned: List<RpCharacterEntity>,
        outputWords: Int,
    ): List<String> {
        val blocks = mutableListOf<String>()
        if (roomCharacter != null) {
            blocks += RoleplayPromptBuilder.systemBlocks(
                character = roomCharacter,
                persona = persona,
                outputWords = outputWords,
            )
            book?.let {
                blocks += "This conversation takes place inside the server for \"${it.title}\"" +
                    " (${it.workType}). Stay true to that world."
            }
        } else {
            blocks += buildString {
                appendLine("You are the AI narrator hosting a Discord-style server dedicated to a creative work.")
                if (book != null) {
                    appendLine("The server is for \"${book.title}\" (${book.workType}).")
                    if (book.genre.isNotBlank()) appendLine("Genre: ${book.genre}.")
                    if (book.pov.isNotBlank()) appendLine("Point of view: ${book.pov}.")
                    if (book.tense.isNotBlank()) appendLine("Tense: ${book.tense}.")
                    if (book.styleGuide.isNotBlank()) appendLine("Style guide: ${book.styleGuide}")
                }
                appendLine("You are speaking in the #${room.title} channel.")
                if (room.authorsNote.isNotBlank()) appendLine("Channel topic: ${room.authorsNote}")
                append(
                    "Reply like a knowledgeable, welcoming server host and narrator: discuss the work, " +
                        "answer questions about its world, and keep the conversation lively. Keep it under $outputWords words.",
                )
            }
            persona.takeIf { it.name.isNotBlank() || it.description.isNotBlank() }?.let {
                blocks += "The person messaging you is ${it.name.ifBlank { "the writer" }}. " +
                    "Do not write their messages for them."
            }
        }
        if (mentioned.isNotEmpty()) {
            blocks += "The user @mentioned characters. Voice each mentioned character when they speak, " +
                "preferring their lines formatted as \"Name: what they say\", and stay true to each card:"
            mentioned.forEach { character ->
                blocks += RoleplayPromptBuilder.characterBlock(character)
            }
        }
        return blocks
    }

    // -------------------------------------------------------------- helpers

    fun timestampShort(createdAt: Long): String = timeFormat.format(Date(createdAt))

    fun timestampFull(createdAt: Long): String {
        val now = System.currentTimeMillis()
        return if (isSameDay(createdAt, now)) {
            "Today at ${timeFormat.format(Date(createdAt))}"
        } else {
            "${dateFormat.format(Date(createdAt))} · ${timeFormat.format(Date(createdAt))}"
        }
    }

    fun dayLabel(createdAt: Long): String {
        val now = System.currentTimeMillis()
        return when {
            isSameDay(createdAt, now) -> "Today"
            isSameDay(createdAt, now - DAY_MS) -> "Yesterday"
            else -> dateFormat.format(Date(createdAt))
        }
    }

    private fun isSameDay(a: Long, b: Long): Boolean {
        val ca = java.util.Calendar.getInstance().apply { timeInMillis = a }
        val cb = java.util.Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(java.util.Calendar.YEAR) == cb.get(java.util.Calendar.YEAR) &&
            ca.get(java.util.Calendar.DAY_OF_YEAR) == cb.get(java.util.Calendar.DAY_OF_YEAR)
    }

    companion object {
        private val SERVER_WORK_TYPES = setOf("novel", "campaign")
        private val ROOM_KINDS = setOf(ROOM_KIND_CHANNEL, ROOM_KIND_CHARACTER)
        private const val HISTORY_LIMIT = 24
        private const val DAY_MS = 24L * 60L * 60L * 1000L
        private const val READ_GRACE_MS = 1_000L
    }
}

/** Whether a block-based document carries any media at all. */
private fun com.ihy2ln.weaverse.core.text.Document.hasMedia(): Boolean = blocks.any { block ->
    when (block) {
        is com.ihy2ln.weaverse.core.text.MediaBlock -> true
        is com.ihy2ln.weaverse.core.text.MediaStackBlock -> true
        else -> false
    }
}
