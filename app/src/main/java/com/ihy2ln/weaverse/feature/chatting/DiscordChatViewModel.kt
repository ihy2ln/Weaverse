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
import com.ihy2ln.weaverse.data.db.entities.RpRoomMemberEntity
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
    /** The server this room belongs to; blank for a true DM. Shown on cross-server recent rows. */
    val serverTitle: String = "",
)

/** One rendered message row in the Discord pane. */
data class DiscordMessageUi(
    val id: String,
    val authorName: String,
    val authorColorHex: String,
    val isUser: Boolean,
    val isBot: Boolean,
    val isSystem: Boolean = false,
    val text: String,
    val hasMedia: Boolean,
    val mediaPaths: List<String> = emptyList(),
    val createdAt: Long,
)

/** One seat in a room's member strip. */
data class DiscordMemberUi(
    val characterId: String,
    val name: String,
    val colorHex: String,
    val monogram: String,
    val joinedViaMention: Boolean,
)

/** One server's rooms as shown on the Home screen: its channels, then its character sub-rooms. */
data class DiscordServerSection(
    val bookId: String,
    val title: String,
    val channels: List<DiscordRoomUi>,
    val characterRooms: List<DiscordRoomUi>,
)

data class DiscordChatUiState(
    val servers: List<DiscordServerUi> = emptyList(),
    /** null = Home (direct messages). */
    val selectedServerId: String? = null,
    val selectedServer: DiscordServerUi? = null,
    val rooms: List<DiscordRoomUi> = emptyList(),
    val directMessages: List<DiscordRoomUi> = emptyList(),
    /** Most recently active conversations across every server, channel, and character room — shown on Home. */
    val recentConversations: List<DiscordRoomUi> = emptyList(),
    /** Chat-themed quick messages shown in the prompt window's template picker. */
    val templates: List<com.ihy2ln.weaverse.data.db.entities.PromptEntity> = emptyList(),
    /** Templates ticked for the next send, layered in the order they were picked. */
    val selectedTemplateIds: List<String> = emptyList(),
    /** Prompt window expanded ("More") vs the two-row default. */
    val promptExpanded: Boolean = false,
    /** Every server's channels and character sub-rooms, for browsing from Home. */
    val serverSections: List<DiscordServerSection> = emptyList(),
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
    /** Cast seated in the selected room, for the member strip and @mention autocomplete. */
    val members: List<DiscordMemberUi> = emptyList(),
    /** The work's wider cast, for @mention autocomplete beyond who's already seated. */
    val mentionCandidates: List<DiscordMemberUi> = emptyList(),
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
    private val castResolver: ChatCastResolver,
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
    /** Latest chat rows, so a server switch can rebuild its sidebar without waiting on the DB. */
    private var allChats: List<RpChatEntity> = emptyList()
    private var lastClearedInput: String = ""
    /** Media attached via the dock's + button, sent with the next message. */
    private var pendingMedia: List<com.ihy2ln.weaverse.data.db.entities.MediaEntity> = emptyList()

    private val draftsByRoom = mutableMapOf<String, String>()
    private val messageCacheByRoom = mutableMapOf<String, List<DiscordMessageUi>>()
    private val scrollByRoom = mutableMapOf<String, Pair<Int, Int>>()

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
                allChats = chats
                rebuildRooms(chats)
                refreshBadges()
            }
        }
        viewModelScope.launch {
            db.promptDao().observeAll().collect { prompts ->
                val chat = prompts.filter { it.folderId == CHAT_PROMPT_FOLDER || it.folderId == CUSTOM_PROMPT_FOLDER }
                    .sortedBy { it.name.lowercase() }
                _uiState.update { it.copy(templates = chat) }
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
        viewModelScope.launch {
            _uiState.map { it.selectedRoomId }.distinctUntilChanged().flatMapLatest { roomId ->
                if (roomId.isNullOrBlank()) flowOf(emptyList()) else db.roleplayDao().observeMembers(roomId)
            }.collect { members ->
                publishMembers(members)
            }
        }
    }

    /**
     * [autoRestoreLastRoom] should be false when the caller is about to explicitly select a
     * room of its own right after (e.g. opening a recent conversation from Home) — otherwise
     * the async "return to the last room" lookup below can race and override that choice.
     */
    fun selectServer(bookId: String?, autoRestoreLastRoom: Boolean = true) {
        if (_uiState.value.selectedServerId == bookId) return
        generateJob?.cancel()
        generateJob = null
        _uiState.value.selectedRoomId?.let { draftsByRoom[it] = _uiState.value.input }
        _uiState.update {
            it.copy(
                selectedServerId = bookId,
                selectedServer = bookId?.let { id -> it.servers.find { s -> s.bookId == id } },
                selectedRoomId = null,
                selectedRoom = null,
                messages = emptyList(),
                members = emptyList(),
                mentionCandidates = emptyList(),
                input = "",
                isStreaming = false,
                streamingText = "",
            )
        }
        // The chats flow only re-emits when the database changes, so without this the
        // freshly selected server would show an empty sidebar until something wrote a row.
        rebuildRooms(allChats)
        if (bookId != null) {
            viewModelScope.launch {
                refreshBadges()
                // Defensive catch-up for legacy works; new works are seeded at creation.
                booksById[bookId]?.let { roomSeeder.ensureRoomsForBook(it) }
                if (!autoRestoreLastRoom) return@launch
                // Return to whichever room or sub-room the writer had open last time,
                // instead of always landing back on the room list.
                val lastRoomId = settings.lastChatRoom(bookId)
                if (lastRoomId.isNotBlank() && db.roleplayDao().getChat(lastRoomId)?.bookId == bookId) {
                    selectRoom(lastRoomId)
                }
            }
        }
    }

    fun selectRoom(chatId: String?) {
        if (_uiState.value.selectedRoomId == chatId) return
        generateJob?.cancel()
        generateJob = null
        val previousRoomId = _uiState.value.selectedRoomId
        val previousDraft = _uiState.value.input
        if (previousRoomId != null) draftsByRoom[previousRoomId] = previousDraft
        _uiState.update {
            it.copy(
                selectedRoomId = chatId,
                selectedRoom = it.rooms.find { r -> r.chatId == chatId }
                    ?: it.directMessages.find { r -> r.chatId == chatId },
                messages = chatId?.let { id -> messageCacheByRoom[id] }.orEmpty(),
                members = emptyList(),
                mentionCandidates = emptyList(),
                input = chatId?.let { id -> draftsByRoom[id] }.orEmpty(),
                isStreaming = false,
                streamingText = "",
                errorMessage = "",
            )
        }
        if (chatId != null) {
            viewModelScope.launch {
                if (previousRoomId != null) settings.setChatDraft(previousRoomId, previousDraft)
                if (!draftsByRoom.containsKey(chatId)) {
                    val savedDraft = settings.chatDraft(chatId)
                    if (savedDraft.isNotBlank() && _uiState.value.selectedRoomId == chatId) {
                        draftsByRoom[chatId] = savedDraft
                        _uiState.update { it.copy(input = savedDraft) }
                    }
                }
                db.roleplayDao().getChat(chatId)?.let { chat ->
                    boundRoom = chat
                    if (chat.lastReadAt < System.currentTimeMillis() - READ_GRACE_MS) {
                        val read = chat.copy(lastReadAt = System.currentTimeMillis())
                        db.roleplayDao().upsertChat(read)
                        boundRoom = read
                    }
                    // Remember this room (or sub-room) as the server's last-visited spot,
                    // so reopening the server returns here instead of the room list.
                    chat.bookId?.let { bookId -> settings.setLastChatRoom(bookId, chatId) }
                    val book = chat.bookId?.let { booksById[it] }
                    val cast = book?.let { castResolver.castForBook(it) }.orEmpty().map { character ->
                        DiscordMemberUi(
                            characterId = character.id,
                            name = character.name,
                            colorHex = avatarColorHexFor(character.name, character.colorHex),
                            monogram = monogramOf(character.name),
                            joinedViaMention = false,
                        )
                    }
                    if (_uiState.value.selectedRoomId == chatId) {
                        _uiState.update { it.copy(mentionCandidates = cast) }
                    }
                }
            }
        } else {
            boundRoom = null
            if (previousRoomId != null) {
                viewModelScope.launch { settings.setChatDraft(previousRoomId, previousDraft) }
            }
        }
    }

    /** Height the writer dragged the prompt window to; 0 sizes to content. */
    val promptDockHeight = MutableStateFlow(0f)

    fun setPromptDockHeight(dp: Float) { promptDockHeight.value = dp }

    fun setPromptExpanded(expanded: Boolean) {
        _uiState.update { it.copy(promptExpanded = expanded) }
    }

    fun toggleTemplate(id: String) {
        _uiState.update {
            val next = if (id in it.selectedTemplateIds) it.selectedTemplateIds - id else it.selectedTemplateIds + id
            it.copy(selectedTemplateIds = next)
        }
        refreshContextMeter()
    }

    fun clearTemplates() {
        _uiState.update { it.copy(selectedTemplateIds = emptyList()) }
        refreshContextMeter()
    }

    fun onInputChange(value: String) {
        _uiState.value.selectedRoomId?.let { draftsByRoom[it] = value }
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
            val imported = runCatching { mediaRepository.importFromUris(uris) }
            imported.onFailure { err ->
                // A silently swallowed import used to look exactly like "nothing happened".
                _uiState.update {
                    it.copy(
                        hasPendingMedia = false,
                        errorMessage = "Could not attach that picture: " +
                            (err.message?.takeIf { m -> m.isNotBlank() } ?: err::class.simpleName.orEmpty()),
                    )
                }
            }
            val media = imported.getOrDefault(emptyList())
            if (media.isEmpty()) return@launch
            pendingMedia = pendingMedia + media
            _uiState.update { it.copy(hasPendingMedia = true, errorMessage = "") }
            // Post the picture straight away; waiting for text made it look like nothing attached.
            val room = boundRoom ?: return@launch
            val now = System.currentTimeMillis()
            db.roleplayDao().upsertMessage(
                RpMessageEntity(
                    id = "rpm-$now",
                    chatId = room.id,
                    swipeGroupId = "sw-$now",
                    swipeIndex = 0,
                    isActiveSwipe = true,
                    role = "user",
                    contentJson = userMessageDocument("", media).toJson(),
                    createdAt = now,
                    displayMode = "messenger",
                ),
            )
            pendingMedia = emptyList()
            db.roleplayDao().upsertChat(room.copy(updatedAt = now))
            _uiState.update { it.copy(hasPendingMedia = false) }
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
            draftsByRoom.remove(chatId)
            messageCacheByRoom.remove(chatId)
            scrollByRoom.remove(chatId)
            if (_uiState.value.selectedRoomId == chatId) {
                _uiState.update { it.copy(selectedRoomId = null, selectedRoom = null, messages = emptyList(), members = emptyList()) }
            }
        }
    }

    /** Member strip long-press: removes a character's seat in the selected room. */
    fun removeMember(characterId: String) {
        val roomId = _uiState.value.selectedRoomId ?: return
        viewModelScope.launch { castResolver.removeMember(roomId, characterId) }
    }

    /** Persists the message list's scroll position for the room, so it's restored on return. */
    fun rememberScroll(roomId: String, index: Int, offset: Int) {
        scrollByRoom[roomId] = index to offset
    }

    fun scrollFor(roomId: String): Pair<Int, Int>? = scrollByRoom[roomId]

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
                invitesForMentions(room, userText)
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
            val lastUserIndex = messages.indexOfLast { it.role == "user" }
            if (lastUserIndex == -1) return@launch
            val lastUser = messages[lastUserIndex]
            // A single AI turn can fan out into several messages (one per speaker);
            // retry clears the whole trailing run, not just the last row.
            val trailingReplies = messages.drop(lastUserIndex + 1).filter { it.role == "char" }
            if (trailingReplies.isEmpty()) return@launch
            trailingReplies.forEach { db.roleplayDao().deleteMessage(it.id) }
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
        val members = castResolver.membersOf(room.id)
        val mentioned = resolveMentions(userText, room).map { it.character }
        val roomCharacter = room.characterId?.let { charactersById[it] }
        val history = db.roleplayDao().getMessagesForMode(room.id, "messenger")
            .filter { it.isActiveSwipe && it.role != "system" }
            .takeLast(HISTORY_LIMIT)
            .map { msg ->
                val role = if (msg.role == "user") "user" else "assistant"
                role to documentFromJson(msg.contentJson).plainText()
            }
        val persona = roomSeeder.defaultPersona()
        val book = room.bookId?.let { booksById[it] }
        val system = buildSystemBlocks(room, book, roomCharacter, persona, members, mentioned, state.maximumWords)
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
        // A reply with no parsed "Name:" belongs to whoever was addressed — never a narrator.
        val fallbackSpeaker = roomCharacter ?: mentioned.firstOrNull() ?: members.firstOrNull()
        val bookTitle = book?.title.orEmpty()
        val lines = parseSpeakerLines(replyText, members + listOfNotNull(fallbackSpeaker))
        val replyBase = System.currentTimeMillis()
        lines.forEachIndexed { index, line ->
            db.roleplayDao().upsertMessage(
                RpMessageEntity(
                    id = "rpm-${replyBase + index}",
                    chatId = room.id,
                    swipeGroupId = "sw-$now",
                    swipeIndex = 0,
                    isActiveSwipe = true,
                    role = "char",
                    speakerCharacterId = line.character?.id,
                    speakerName = if (line.character != null) {
                        ""
                    } else {
                        // The model sometimes still signs a line as a narrator/host/app for the
                        // work; rooms only contain people, so that becomes the addressed person.
                        val raw = line.displayName.trim()
                        val narratorish = raw.isBlank() ||
                            NARRATOR_WORDS.any { word -> raw.contains(word, ignoreCase = true) } ||
                            (bookTitle.isNotBlank() && raw.contains(bookTitle, ignoreCase = true))
                        if (narratorish) fallbackSpeaker?.name.orEmpty() else raw
                    },
                    contentJson = Document.fromPlainText(line.text).toJson(),
                    createdAt = replyBase + index * MULTI_SPEAKER_STAGGER_MS,
                    displayMode = "messenger",
                    promptTokens = if (index == 0) promptTokens else 0,
                    completionTokens = if (index == 0) completionTokens else 0,
                    costUsd = if (index == 0) costUsd else 0.0,
                ),
            )
        }
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
            val members = castResolver.membersOf(room.id)
            val system = buildSystemBlocks(room, book, roomCharacter, persona, members, emptyList(), state.maximumWords)
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
        val recent = chats
            .filter {
                it.displayMode == "messenger" &&
                    (it.roomKind in ROOM_KINDS || it.roomKind == ROOM_KIND_DM ||
                        (it.roomKind.isEmpty() && it.bookId == null))
            }
            .sortedByDescending { it.updatedAt }
            .take(RECENT_CONVERSATIONS_LIMIT)
            .map { it.toRoomUi() }
        val sections = chats
            .filter { it.bookId != null && it.displayMode == "messenger" && it.roomKind in ROOM_KINDS }
            .groupBy { it.bookId!! }
            .mapNotNull { (bookId, roomsForBook) ->
                val title = booksById[bookId]?.title ?: return@mapNotNull null
                val ui = roomsForBook.sortedBy { r -> r.createdAt }.map { r -> r.toRoomUi() }
                DiscordServerSection(
                    bookId = bookId,
                    title = title,
                    channels = ui.filter { r -> r.kind == ROOM_KIND_CHANNEL },
                    characterRooms = ui.filter { r -> r.kind == ROOM_KIND_CHARACTER },
                )
            }
            .sortedBy { it.title.lowercase() }
        _uiState.update {
            it.copy(
                rooms = selectedRooms,
                directMessages = dms,
                recentConversations = recent,
                serverSections = sections,
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
            serverTitle = bookId?.let { booksById[it]?.title }.orEmpty(),
        )
    }

    private suspend fun publishMessages(messages: List<RpMessageEntity>) {
        val state = _uiState.value
        val roomId = messages.firstOrNull()?.chatId ?: state.selectedRoomId ?: return
        val room = (state.rooms + state.directMessages).find { it.chatId == roomId } ?: state.selectedRoom
        val roomCharacter = room?.characterId?.let { charactersById[it] }
        val serverTitle = state.selectedServer?.title
        val memberNames = state.members.map { it.name }
        val rows = messages
            .filter { it.isActiveSwipe }
            .map { msg ->
                val character = msg.speakerCharacterId?.let { charactersById[it] }
                val isUser = msg.role == "user"
                val isSystem = msg.role == "system"
                val rawAuthor = when {
                    isUser -> "You"
                    character != null -> character.name
                    msg.speakerName.isNotBlank() -> msg.speakerName
                    roomCharacter != null -> roomCharacter.name
                    else -> ""
                }
                // Also scrubs narrator bylines already sitting in older history.
                val narratorish = !isUser && !isSystem && character == null && (
                    rawAuthor.isBlank() ||
                        NARRATOR_WORDS.any { rawAuthor.contains(it, ignoreCase = true) } ||
                        (serverTitle != null && rawAuthor.contains(serverTitle, ignoreCase = true))
                    )
                val authorName = when {
                    !narratorish && rawAuthor.isNotBlank() -> rawAuthor
                    roomCharacter != null -> roomCharacter.name
                    memberNames.isNotEmpty() -> memberNames.first()
                    else -> "Unknown"
                }
                DiscordMessageUi(
                    id = msg.id,
                    authorName = authorName,
                    authorColorHex = character?.let { avatarColorHexFor(it.name, it.colorHex) }
                        ?: avatarColorHexFor(authorName, null),
                    isUser = isUser,
                    // Only a message we cannot attribute to any person is an app/bot line.
                    isBot = !isUser && !isSystem && authorName == "Unknown",
                    isSystem = isSystem,
                    text = documentFromJson(msg.contentJson).plainText().trim(),
                    hasMedia = documentFromJson(msg.contentJson).hasMedia(),
                    mediaPaths = mediaPathsOf(msg),
                    createdAt = msg.createdAt,
                )
            }
        messageCacheByRoom[roomId] = rows
        // Guards against a late emission for a room the user has already left.
        if (_uiState.value.selectedRoomId == roomId) {
            _uiState.update { it.copy(messages = rows) }
        }
    }

    /** Publishes the selected room's seated cast for the member strip and @mention autocomplete. */
    private fun publishMembers(members: List<RpRoomMemberEntity>) {
        val rows = members.mapNotNull { member ->
            val character = charactersById[member.characterId] ?: return@mapNotNull null
            DiscordMemberUi(
                characterId = character.id,
                name = character.name,
                colorHex = avatarColorHexFor(character.name, character.colorHex),
                monogram = monogramOf(character.name),
                joinedViaMention = !member.seeded,
            )
        }
        _uiState.update { it.copy(members = rows) }
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
        val allRooms = (state.rooms + state.directMessages + state.recentConversations).distinctBy { it.chatId }
        if (allRooms.isEmpty()) return
        var changed = false
        val badges = mutableMapOf<String, Pair<Int, String>>()
        allRooms.forEach { room ->
            val chat = db.roleplayDao().getChat(room.chatId)
            val unread = chat?.let { db.roleplayDao().countUnread(it.id, it.lastReadAt) } ?: 0
            val latest = db.roleplayDao().getLatestMessage(room.chatId)
            val preview = latest?.let { msg ->
                val text = documentFromJson(msg.contentJson).plainText().replace('\n', ' ').trim()
                (if (msg.role == "user") "You: " else "") + text
            }.orEmpty().take(90)
            if (unread != room.unread || preview != room.preview) changed = true
            badges[room.chatId] = unread to preview
        }
        if (!changed) return
        fun List<DiscordRoomUi>.applyBadges() = map { room ->
            val badge = badges[room.chatId] ?: return@map room
            room.copy(unread = badge.first, preview = badge.second)
        }
        _uiState.update { current ->
            current.copy(
                rooms = current.rooms.applyBadges(),
                directMessages = current.directMessages.applyBadges(),
                recentConversations = current.recentConversations.applyBadges(),
                selectedRoom = (current.rooms + current.directMessages)
                    .find { it.chatId == current.selectedRoomId },
            )
        }
    }

    // ----------------------------------------------------------- prompting

    /** An @mentioned character, and whether they were already seated in the room. */
    private data class MentionHit(val character: RpCharacterEntity, val wasAlreadyMember: Boolean)

    /**
     * Resolves @mentions against the room's current members, then the work's wider cast
     * (campaign roster + codex characters, materializing a codex entry on the spot when
     * it has no character card yet). No longer matches every character in the app.
     */
    private suspend fun resolveMentions(text: String, room: RpChatEntity): List<MentionHit> {
        if (!text.contains('@')) return emptyList()
        val members = castResolver.membersOf(room.id)
        val memberIds = members.map { it.id }.toSet()
        val book = room.bookId?.let { booksById[it] }
        val candidates = (members + (book?.let { castResolver.castForBook(it) }.orEmpty())).distinctBy { it.id }
        return matchMentionedCharacters(text, candidates).map { MentionHit(it, it.id in memberIds) }
    }

    /** Pulls any newly @mentioned character into the room, with a visible join line. */
    private suspend fun invitesForMentions(room: RpChatEntity, text: String) {
        resolveMentions(text, room).filterNot { it.wasAlreadyMember }.forEach { hit ->
            castResolver.addMember(room.id, hit.character, seeded = false)
            db.roleplayDao().upsertMessage(
                RpMessageEntity(
                    id = "rpm-${UUID.randomUUID()}",
                    chatId = room.id,
                    swipeGroupId = "sw-${UUID.randomUUID()}",
                    swipeIndex = 0,
                    isActiveSwipe = true,
                    role = "system",
                    contentJson = Document.fromPlainText("@${hit.character.name} joined #${room.title}").toJson(),
                    createdAt = System.currentTimeMillis(),
                    displayMode = "messenger",
                ),
            )
        }
    }

    private fun buildSystemBlocks(
        room: RpChatEntity,
        book: BookEntity?,
        roomCharacter: RpCharacterEntity?,
        persona: RpPersonaEntity,
        members: List<RpCharacterEntity>,
        mentioned: List<RpCharacterEntity>,
        outputWords: Int,
    ): List<String> {
        val blocks = mutableListOf<String>()
        if (roomCharacter != null) {
            blocks += RoleplayPromptBuilder.systemBlocks(
                character = roomCharacter,
                persona = persona,
                outputWords = outputWords,
                mode = com.ihy2ln.weaverse.feature.shell.AppMode.Chatting,
            )
            book?.let {
                blocks += "This conversation takes place inside the server for \"${it.title}\"" +
                    " (${it.workType}). Stay true to that world."
            }
            blocks += "You are texting in a chat app, not writing a story. Reply in first person as " +
                "${roomCharacter.name} with what they would actually type: short, casual, present tense. " +
                "No narration, no third-person description of yourself, no asterisk actions, no markdown."
        } else {
            blocks += buildString {
                appendLine(
                    "You ARE the people in this group chat — never a narrator, host, or app. " +
                        "There is no narrator in this room.",
                )
                if (book != null) {
                    appendLine("Everyone here knows the world of \"${book.title}\" and can talk about it.")
                    if (book.genre.isNotBlank()) appendLine("Genre of that world: ${book.genre}.")
                }
                appendLine("This is the #${room.title} channel.")
                if (room.authorsNote.isNotBlank()) appendLine("Channel topic: ${room.authorsNote}")
                append(
                    "Write ONLY what a person types into a chat app: first person, present tense, " +
                        "casual and short. This is a live conversation, not a story or a novel — " +
                        "no prose, no scene-setting, no third-person description of anyone's body, " +
                        "face, clothing, or movements, no asterisk actions, no markdown. " +
                        "Every line is \"Name: what they type\" and nothing else. " +
                        "Keep each line under $outputWords words.",
                )
            }
            persona.takeIf { it.name.isNotBlank() || it.description.isNotBlank() }?.let {
                blocks += "The person messaging you is ${it.name.ifBlank { "the writer" }}. " +
                    "Do not write their messages for them."
            }
        }
        val rosterMembers = members.filterNot { it.id == roomCharacter?.id }
        if (rosterMembers.isNotEmpty()) {
            blocks += "People in #${room.title}: ${rosterMembers.joinToString(", ") { it.name }}."
            rosterMembers.forEach { character ->
                blocks += RoleplayPromptBuilder.characterBlock(character, com.ihy2ln.weaverse.feature.shell.AppMode.Chatting)
            }
        }
        // Quick-message templates the writer ticked in the prompt window, layered in order.
        val picked = _uiState.value.selectedTemplateIds
        if (picked.isNotEmpty()) {
            val byId = _uiState.value.templates.associateBy { it.id }
            picked.mapNotNull { byId[it] }.forEach { template ->
                val text = com.ihy2ln.weaverse.ai.prompt.decodePromptMessages(template.instructionsJson)
                    .joinToString("\n\n") { it.content }
                if (text.isNotBlank()) blocks += text
            }
        }
        if (mentioned.isNotEmpty()) {
            val cap = members.size.coerceAtMost(3).coerceAtLeast(1)
            blocks += "The user addressed ${mentioned.joinToString(", ") { "@${it.name}" }}. " +
                "${mentioned.first().name} replies first. Any of the other people in the room may reply too, " +
                "but only if they have something relevant to add — silence is fine. Write each line as " +
                "\"Name: what they say\" in plain text, with no markdown, no bold, and no asterisks around the " +
                "name — one speaker per line, never narrate for the user, at most $cap lines total."
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
        private const val CHAT_PROMPT_FOLDER = "folder-chatting"
        private const val CUSTOM_PROMPT_FOLDER = "folder-custom"
        /** Names that mean "not a person in the room" and get reassigned to the addressee. */
        private val NARRATOR_WORDS = listOf("narrator", "narration", "system", "server host", "host bot")
        private const val RECENT_CONVERSATIONS_LIMIT = 12
        private const val MULTI_SPEAKER_STAGGER_MS = 1_200L
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
