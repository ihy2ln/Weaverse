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
import kotlinx.serialization.encodeToString
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
    /** Emoji on the server icon instead of the monogram; blank = monogram. */
    val emoji: String = "",
    val description: String = "",
    /** A server the writer made just for chatting, rather than a novel's or campaign's. */
    val isOwn: Boolean = false,
)

/** One codex entry as the server's codex channel and pickers show it. */
data class CodexItemUi(
    val id: String,
    val name: String,
    val category: String,
    val categoryId: String,
    val colorHex: String?,
    val text: String,
    val isCharacter: Boolean,
)

/** One server-wide search result. */
data class ServerSearchHit(val roomId: String, val roomName: String, val message: DiscordMessageUi)

/** The voice channel the writer is connected to, and who is talking right now. */
data class VoiceUi(
    val roomId: String? = null,
    val speakingCharacterId: String? = null,
    val deafened: Boolean = false,
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
    /** The channel a thread or forum post lives under. */
    val parentRoomId: String? = null,
)

/** One emoji reaction chip under a message. */
data class DiscordReactionUi(val emoji: String, val count: Int, val mine: Boolean)

/** The quoted line a Discord reply shows above its message. */
data class DiscordReplyPreviewUi(
    val messageId: String,
    val authorName: String,
    val authorColorHex: String,
    val snippet: String,
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
    val authorCharacterId: String? = null,
    val reactions: List<DiscordReactionUi> = emptyList(),
    val replyTo: DiscordReplyPreviewUi? = null,
    val pinned: Boolean = false,
    val edited: Boolean = false,
    /** The message @mentions the writer, so the row gets Discord's gold highlight. */
    val mentionsYou: Boolean = false,
)

/** One seat in a room's member strip. */
data class DiscordMemberUi(
    val characterId: String,
    val name: String,
    val colorHex: String,
    val monogram: String,
    val joinedViaMention: Boolean,
)

/** The profile popout for a member or message author. */
data class DiscordProfileUi(
    val characterId: String?,
    val name: String,
    val colorHex: String,
    val about: String,
    val memberSince: Long,
    val isMember: Boolean,
    val isYou: Boolean,
    val roles: List<String>,
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
    /** Which tab the last pick request opens the picker on. */
    val mediaPickStart: com.ihy2ln.weaverse.feature.chatting.media.PickerStart =
        com.ihy2ln.weaverse.feature.chatting.media.PickerStart.Library,
    /** Shows when the + button has staged media for the next message. */
    val hasPendingMedia: Boolean = false,
    /** Cast seated in the selected room, for the member strip and @mention autocomplete. */
    val members: List<DiscordMemberUi> = emptyList(),
    /** The work's wider cast, for @mention autocomplete beyond who's already seated. */
    val mentionCandidates: List<DiscordMemberUi> = emptyList(),
    /** Every codex character, listed as a contact on the Direct Messages screen. */
    val dmContacts: List<DiscordMemberUi> = emptyList(),
    /** True while the Direct Messages contact list is showing. */
    val dmContactsOpen: Boolean = false,
    /** Message the next send replies to. */
    val replyingTo: DiscordReplyPreviewUi? = null,
    /** Messages newer than this were unread when the room was opened — the red NEW line. */
    val unreadSince: Long = 0L,
    /** Unread messages per server, for the rail's red badges. */
    val serverUnread: Map<String, Int> = emptyMap(),
    /** Unread total across DMs, for the Home button badge. */
    val dmUnread: Int = 0,
    /** The writer's display name (default persona). */
    val personaName: String = "You",
    /** Open profile popout, if any. */
    val profile: DiscordProfileUi? = null,
    /** The selected server's roles, highest first. */
    val roles: List<ServerRole> = emptyList(),
    /** characterId to role ids in the selected server. */
    val memberRoles: Map<String, List<String>> = emptyMap(),
    /** Who a writer-made server seats; empty = every codex character. */
    val serverMemberIds: List<String> = emptyList(),
    /** Codex entries the selected server's AI may use; empty = the whole codex. */
    val serverCodexIds: List<String> = emptyList(),
    /** The whole shared codex, for the codex channel and the pickers. */
    val codex: List<CodexItemUi> = emptyList(),
    /** Codex categories as id to name. */
    val codexCategories: List<Pair<String, String>> = emptyList(),
    /** Message a thread is being started from (the Create Thread dialog). */
    val threadDraftFor: DiscordMessageUi? = null,
    /** Message being saved to the codex (the Save to Codex dialog). */
    val codexSaveFor: DiscordMessageUi? = null,
    val voice: VoiceUi = VoiceUi(),
    /** Results of the last whole-server search. */
    val serverSearch: List<ServerSearchHit> = emptyList(),
    /** Every character, for picking who's in a server. */
    val people: List<DiscordMemberUi> = emptyList(),
) {
    /** Codex entries in reach of the selected server's AI. */
    val serverCodex: List<CodexItemUi>
        get() = if (serverCodexIds.isEmpty()) codex else serverCodexIds.toSet().let { ids -> codex.filter { it.id in ids } }

    /** characterId to the color of their highest role, as Discord colors names. */
    val roleColors: Map<String, String>
        get() = memberRoles.mapNotNull { (characterId, ids) ->
            roles.firstOrNull { it.id in ids }?.let { characterId to it.colorHex }
        }.toMap()

    val pinnedMessages: List<DiscordMessageUi>
        get() = messages.filter { it.pinned }

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
    private val characterMedia: com.ihy2ln.weaverse.feature.chatting.media.CharacterMediaFetcher,
    private val relations: com.ihy2ln.weaverse.feature.chatting.social.SocialRelations,
    private val tts: com.ihy2ln.weaverse.core.tts.TtsService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscordChatUiState())
    val uiState: StateFlow<DiscordChatUiState> = _uiState.asStateFlow()

    private var charactersById: Map<String, RpCharacterEntity> = emptyMap()
    private var booksById: Map<String, BookEntity> = emptyMap()
    private var serverSettingsById: Map<String, com.ihy2ln.weaverse.data.db.entities.ChatServerEntity> = emptyMap()
    /** The selected room's raw messages, so a role change can recolor names without a DB write. */
    private var lastRawMessages: List<RpMessageEntity> = emptyList()
    /** A forum post's opening message, sent as soon as the post opens. */
    private var pendingAutoSend: String? = null
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
    /** Latest WeaverSocial blocks, kept current so a DM send can be refused immediately. */
    private var safetyCache = com.ihy2ln.weaverse.feature.chatting.social.SocialSafety()
    private val messageCacheByRoom = mutableMapOf<String, List<DiscordMessageUi>>()
    private val scrollByRoom = mutableMapOf<String, Pair<Int, Int>>()

    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())

    init {
        viewModelScope.launch { relations.safety.collect { safetyCache = it } }
        viewModelScope.launch {
            combine(
                db.bookDao().observeAll(),
                db.roleplayDao().observeChats(),
                db.roleplayDao().observeCharacters(),
                db.chatServerDao().observeAll(),
            ) { books, chats, characters, settings ->
                serverSettingsById = settings.associateBy { it.bookId }
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
                        val settings = serverSettingsById[book.id]
                        DiscordServerUi(
                            bookId = book.id,
                            title = book.title,
                            workType = book.workType,
                            monogram = monogramOf(book.title).take(1),
                            colorHex = settings?.colorHex?.takeIf { it.isNotBlank() } ?: avatarColorHexFor(book.title, null),
                            emoji = settings?.emoji.orEmpty(),
                            description = settings?.description.orEmpty(),
                            isOwn = book.workType == WORK_TYPE_SERVER,
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
                publishServerSettings()
                refreshBadges()
            }
        }
        viewModelScope.launch {
            combine(db.codexDao().observeAllEntries(), db.codexDao().observeAllCategories()) { entries, categories ->
                val names = categories.associate { it.id to it.name }
                val items = entries.filterNot { it.disabled }.map { entry ->
                    val category = names[entry.categoryId].orEmpty()
                    CodexItemUi(
                        id = entry.id,
                        name = entry.name,
                        category = category,
                        categoryId = entry.categoryId,
                        colorHex = entry.colorHex,
                        text = castResolver.entryText(entry),
                        isCharacter = category.equals("Characters", ignoreCase = true),
                    )
                }.sortedWith(compareBy({ it.category.lowercase() }, { it.name.lowercase() }))
                items to categories.sortedBy { it.sortOrder }.map { it.id to it.name }
            }.collect { (items, categories) ->
                _uiState.update { it.copy(codex = items, codexCategories = categories) }
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
        publishServerSettings()
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
                replyingTo = null,
                unreadSince = 0L,
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
                    if (pendingAutoSend == chatId && _uiState.value.selectedRoomId == chatId) {
                        pendingAutoSend = null
                        send()
                    }
                    val hasUnread = db.roleplayDao().countUnread(chat.id, chat.lastReadAt) > 0
                    if (hasUnread && chat.lastReadAt > 0L && _uiState.value.selectedRoomId == chatId) {
                        _uiState.update { it.copy(unreadSince = chat.lastReadAt) }
                    }
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

    /**
     * Height of the prompt window in dp. Unlike Novel's dock this never sizes to its
     * content: the message list's weight wins that negotiation and collapses the window
     * to its drag handle the moment the room has messages, so the window always carries a
     * concrete height and a double-tap reset returns to the default rather than to auto.
     */
    val promptDockHeight = MutableStateFlow(DEFAULT_DOCK_HEIGHT_DP)

    fun setPromptDockHeight(dp: Float) {
        promptDockHeight.value = if (dp <= 0f) DEFAULT_DOCK_HEIGHT_DP else dp
    }

    /** Grows the window when the writer opens "More" so the templates have room. */
    fun expandPromptDock(expanded: Boolean) {
        val floor = if (expanded) EXPANDED_DOCK_HEIGHT_DP else DEFAULT_DOCK_HEIGHT_DP
        if (promptDockHeight.value < floor) promptDockHeight.value = floor
    }

    fun setPromptExpanded(expanded: Boolean) {
        _uiState.update { it.copy(promptExpanded = expanded) }
        expandPromptDock(expanded)
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

    /** DM rail button: show every codex character as someone you can write to. */
    fun openDmContacts() {
        _uiState.update { it.copy(dmContactsOpen = true) }
        viewModelScope.launch {
            val contacts = castResolver.allChatContacts().map { character ->
                DiscordMemberUi(
                    characterId = character.id,
                    name = character.name,
                    colorHex = avatarColorHexFor(character.name, character.colorHex),
                    monogram = monogramOf(character.name),
                    joinedViaMention = false,
                )
            }.sortedBy { it.name.lowercase() }
            _uiState.update { it.copy(dmContacts = contacts) }
        }
    }

    fun closeDmContacts() {
        _uiState.update { it.copy(dmContactsOpen = false) }
    }

    /** Opens this contact's direct message, creating the one-to-one room on first use. */
    fun openDirectMessage(characterId: String) {
        viewModelScope.launch {
            val character = db.roleplayDao().getCharacter(characterId) ?: return@launch
            val existing = db.roleplayDao().getChats().firstOrNull { chat ->
                chat.displayMode == "messenger" &&
                    chat.roomKind == ROOM_KIND_DM &&
                    chat.characterId == characterId
            }
            val chatId = existing?.id ?: run {
                val now = System.currentTimeMillis()
                val chat = RpChatEntity(
                    id = "dm-${UUID.randomUUID()}",
                    characterId = characterId,
                    personaId = roomSeeder.defaultPersona().id,
                    title = character.name,
                    authorsNote = "Direct messages with ${character.name}.",
                    displayMode = "messenger",
                    createdAt = now,
                    updatedAt = now,
                    bookId = null,
                    roomKind = ROOM_KIND_DM,
                )
                db.roleplayDao().upsertChat(chat)
                // A DM holds exactly one other person.
                castResolver.addMember(chat.id, character, seeded = true)
                chat.id
            }
            _uiState.update { it.copy(dmContactsOpen = false, selectedServerId = null, selectedServer = null) }
            selectRoom(chatId)
        }
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
        _uiState.update { it.copy(mediaPickRequestId = it.mediaPickRequestId + 1, mediaPickStart = com.ihy2ln.weaverse.feature.chatting.media.PickerStart.Library) }
    }

    fun requestGifPick() {
        _uiState.update { it.copy(mediaPickRequestId = it.mediaPickRequestId + 1, mediaPickStart = com.ihy2ln.weaverse.feature.chatting.media.PickerStart.Gifs) }
    }

    /** Prompt window's search button: opens the picker on web search. */
    fun requestWebPick() {
        _uiState.update { it.copy(mediaPickRequestId = it.mediaPickRequestId + 1, mediaPickStart = com.ihy2ln.weaverse.feature.chatting.media.PickerStart.Web) }
    }

    /** Pictures or GIFs chosen from the Pictures library, posted the same way as device picks. */
    fun attachLibraryMedia(mediaIds: List<String>) {
        viewModelScope.launch {
            val media = mediaIds.mapNotNull { mediaRepository.getById(it) }
            if (media.isNotEmpty()) postMedia(media)
        }
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
            postMedia(media)
        }
    }

    private suspend fun postMedia(media: List<com.ihy2ln.weaverse.data.db.entities.MediaEntity>) {
        run {
            pendingMedia = pendingMedia + media
            _uiState.update { it.copy(hasPendingMedia = true, errorMessage = "") }
            // Post the picture straight away; waiting for text made it look like nothing attached.
            val room = boundRoom ?: return
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
    fun createChannel(name: String) = createChannel(name, ROOM_KIND_CHANNEL)

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
        if (dmBlocked(room)) return
        val userText = state.input.trim()
        val media = pendingMedia
        val replyTarget = state.replyingTo
        _uiState.update { it.copy(replyingTo = null) }
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
                    replyToId = replyTarget?.messageId,
                )
                db.roleplayDao().upsertMessage(userMessage)
                pendingMedia = emptyList()
                _uiState.update { it.copy(hasPendingMedia = false) }
                _uiState.update { it.copy(input = "", isStreaming = true, streamingText = "", errorMessage = "") }
                invitesForMentions(room, userText)
                val prompt = replyTarget?.let {
                    "(Replying to ${it.authorName}: \"${it.snippet}\")\n$userText"
                } ?: userText
                generateReply(room, prompt, now, userMessageAlreadyStored = true)
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
                        replyToId = replyTarget?.messageId,
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
        if (dmBlocked(room)) return
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
            _uiState.update { it.copy(isStreaming = true, streamingText = "", errorMessage = "") }
            val userText = documentFromJson(lastUser.contentJson).plainText().trim()
            // The old replies stay until the new ones are saved, so a failed retry loses nothing.
            generateReply(room, userText, lastUser.createdAt, userMessageAlreadyStored = true, replacing = trailingReplies)
        }
    }

    /** » hold-menu action: keep the conversation going without a new prompt. */
    fun continueConversation() {
        if (_uiState.value.isStreaming || _uiState.value.selectedRoomId == null) return
        val room = boundRoom ?: return
        if (dmBlocked(room)) return
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
        replacing: List<RpMessageEntity> = emptyList(),
    ) {
        val state = _uiState.value
        val now = baseTimestamp
        val members = castResolver.membersOf(room.id)
        val mentioned = resolveMentions(userText, room).map { it.character }
        val roomCharacter = room.characterId?.let { charactersById[it] }
        val replacedIds = replacing.map { it.id }.toSet()
        val history = db.roleplayDao().getMessagesForMode(room.id, "messenger")
            .filter { it.isActiveSwipe && it.role != "system" && it.id !in replacedIds }
            // The stored turn is sent as the user message below; listing it here too sent it twice.
            .filterNot { userMessageAlreadyStored && it.role == "user" && it.createdAt == baseTimestamp }
            .takeLast(HISTORY_LIMIT)
            .map { msg ->
                val role = if (msg.role == "user") "user" else "assistant"
                role to documentFromJson(msg.contentJson).plainText()
            }
        val persona = roomSeeder.defaultPersona()
        val book = room.bookId?.let { booksById[it] }
        // Talked *about* without an @: the work's cast matched by name, plus anyone a
        // codex entry named, so they can answer instead of being discussed in absentia.
        val cast = book?.let { castResolver.castForBook(it) }.orEmpty()
        val fromCodex = castResolver.codexMatchesIn(userText, room.bookId)
            .filter { entry -> cast.any { it.defaultCodexId == entry.id } }
            .map { castResolver.characterForEntry(it) }
        // Everyone the message names, seated or not. A named member is the one being
        // spoken to, so they answer — without this the reply fell to whoever happened to
        // be first in the member list.
        val named = (matchNamedCharacters(userText, cast + members) + fromCodex).distinctBy { it.id }
        // @everyone / @here pings the whole room: several seated people answer.
        val everyone = pingsEveryone(userText)
        val namedMembers = named.filter { person -> members.any { it.id == person.id } }.let { addressed ->
            if (everyone) (addressed + members).distinctBy { it.id }.take(MAX_EVERYONE) else addressed
        }
        val discussed = named
            .filterNot { person -> person.id == roomCharacter?.id || members.any { it.id == person.id } }
            .take(MAX_DISCUSSED)
        // Codex runs on every send, not only when a name matches: the work's always-include
        // entries and the seated cast's own entries go in even for a plain "hey", with the
        // people actually being spoken to leading so their own rules bind first.
        val codexEntries = castResolver.codexContextFor(
            text = userText,
            bookId = room.bookId,
            members = members,
            speakers = (mentioned + namedMembers + discussed).distinctBy { it.id },
        )
        val system = buildSystemBlocks(
            room = room,
            book = book,
            roomCharacter = roomCharacter,
            persona = persona,
            members = members,
            mentioned = mentioned,
            outputWords = state.maximumWords,
            discussed = discussed,
            addressed = namedMembers,
            codexRefs = castResolver.describe(codexEntries),
            everyone = everyone,
        )
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
                    usedEntries = codexEntries.map { entry ->
                        com.ihy2ln.weaverse.ai.context.ContextChip(
                            entryId = entry.id,
                            name = entry.name,
                            colorHex = entry.colorHex,
                            autoDetected = true,
                        )
                    },
                    tokenBreakdown = emptyList(),
                ),
                modelRef = activeModelRef(),
                maxTokens = maxTokens,
                temperature = 0.8,
            ).collect { chunk ->
                when (chunk) {
                    is AIChunk.Delta -> {
                        builder.append(chunk.text)
                        _uiState.update {
                            it.copy(streamingText = com.ihy2ln.weaverse.feature.chatting.media.MediaTags.hideWhileStreaming(builder.toString().trim()))
                        }
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
        val replyText = stripStageDirections(PromptWordLimit.trim(builder.toString().trim(), state.maximumWords))
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
        // A reply with no parsed "Name:" belongs to whoever the message was aimed at —
        // an @mention first, then the person it was about, then the room's own character.
        val fallbackSpeaker = mentioned.firstOrNull()
            ?: namedMembers.firstOrNull()
            ?: discussed.firstOrNull()
            ?: roomCharacter
            ?: members.firstOrNull()
        val bookTitle = book?.title.orEmpty()
        val speakerPool = (members + discussed + listOfNotNull(fallbackSpeaker)).distinctBy { it.id }
        val lines = parseSpeakerLines(replyText, speakerPool)
        val replyBase = System.currentTimeMillis()
        // In a voice channel each saved line is also spoken, by whoever said it.
        val spoken = mutableListOf<Pair<String?, String>>()
        lines.forEachIndexed { index, line ->
            // A character's [gif: …] / [meme: …] / [pic: …] becomes a picture, fetched below.
            // [cw: …] labels are for the feed's filters; in chat they are just dropped. A DM
            // partner can end a line with [block], as on any real app.
            val social = com.ihy2ln.weaverse.feature.chatting.social.SocialTags.parse(line.text)
            if (social.blocksWriter && room.roomKind == ROOM_KIND_DM && room.characterId != null) {
                blockedInDm(room, room.characterId)
            }
            val (lineText, mediaTags) = com.ihy2ln.weaverse.feature.chatting.media.MediaTags.extract(social.text, max = 1)
            if (lineText.isBlank() && mediaTags.isEmpty()) return@forEachIndexed
            val messageId = "rpm-${replyBase + index}"
            if (mediaTags.isNotEmpty()) attachCharacterMedia(messageId, mediaTags)
            if (lineText.isNotBlank()) spoken += line.character?.id to lineText
            db.roleplayDao().upsertMessage(
                RpMessageEntity(
                    id = messageId,
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
                    contentJson = Document.fromPlainText(lineText).toJson(),
                    createdAt = replyBase + index * MULTI_SPEAKER_STAGGER_MS,
                    displayMode = "messenger",
                    promptTokens = if (index == 0) promptTokens else 0,
                    completionTokens = if (index == 0) completionTokens else 0,
                    costUsd = if (index == 0) costUsd else 0.0,
                ),
            )
        }
        replacing.forEach { db.roleplayDao().deleteMessage(it.id) }
        db.roleplayDao().upsertChat(room.copy(updatedAt = System.currentTimeMillis()))
        _uiState.update {
            it.copy(isStreaming = false, streamingText = "", lastUsage = usageText)
        }
        val voice = _uiState.value.voice
        if (room.roomKind == ROOM_KIND_VOICE && voice.roomId == room.id && !voice.deafened) speak(spoken)
        // Now and then someone in the room drops a reaction on what the writer said,
        // the way people do on Discord instead of typing a whole reply.
        if (userMessageAlreadyStored && kotlin.random.Random.nextFloat() < AI_REACTION_CHANCE) {
            db.roleplayDao().getMessagesForMode(room.id, "messenger")
                .lastOrNull { it.role == "user" && it.isActiveSwipe }
                ?.let { target ->
                    val counts = decodeReactions(target.reactionsJson).toMutableMap()
                    val emoji = AI_REACTION_POOL.random()
                    counts[emoji] = (counts[emoji] ?: 0) + 1
                    db.roleplayDao().upsertMessage(target.copy(reactionsJson = encodeReactions(counts)))
                }
        }
    }

    /**
     * Fetches the memes or GIFs a character's message asked for and adds them to that
     * message once they arrive, so the text shows immediately.
     */
    private fun attachCharacterMedia(
        messageId: String,
        tags: List<com.ihy2ln.weaverse.feature.chatting.media.MediaTag>,
    ) {
        viewModelScope.launch {
            val found = tags.mapNotNull { characterMedia.fetch(it, adultAllowed = safetyCache.adultEnabled) }
            if (found.isEmpty()) return@launch
            // The message is written right after this is launched; wait for it briefly.
            var msg = db.roleplayDao().getRpMessage(messageId)
            var tries = 0
            while (msg == null && tries++ < 20) {
                kotlinx.coroutines.delay(150)
                msg = db.roleplayDao().getRpMessage(messageId)
            }
            msg ?: return@launch
            val doc = documentFromJson(msg.contentJson)
            val blocks = doc.blocks + found.map { media ->
                com.ihy2ln.weaverse.core.text.MediaBlock(
                    id = "mb-${UUID.randomUUID()}",
                    mediaId = media.id,
                    kind = com.ihy2ln.weaverse.core.media.MediaRepository.kindForType(media.type),
                )
            }
            db.roleplayDao().upsertMessage(msg.copy(contentJson = doc.copy(blocks = blocks).toJson()))
        }
    }

    /**
     * DMs follow WeaverSocial blocks both ways: nothing can be sent to someone the writer
     * blocked, or to someone who blocked the writer.
     */
    private fun isDmBlocked(room: RpChatEntity): String? {
        if (room.roomKind != ROOM_KIND_DM) return null
        val id = room.characterId ?: return null
        val safety = safetyCache
        val name = charactersById[id]?.name ?: room.title
        return when (id) {
            in safety.blockedBy -> "$name blocked you. You can't message them."
            in safety.blocked -> "You blocked $name. Unblock them in WeaverSocial to message them."
            else -> null
        }
    }

    private fun dmBlocked(room: RpChatEntity): Boolean {
        val reason = isDmBlocked(room) ?: return false
        _uiState.update { it.copy(errorMessage = reason) }
        return true
    }

    private suspend fun blockedInDm(room: RpChatEntity, characterId: String) {
        relations.setBlockedBy(characterId, true)
        val name = charactersById[characterId]?.name ?: room.title
        db.roleplayDao().upsertMessage(
            RpMessageEntity(
                id = "rpm-${UUID.randomUUID()}",
                chatId = room.id,
                swipeGroupId = "sw-${UUID.randomUUID()}",
                swipeIndex = 0,
                isActiveSwipe = true,
                role = "system",
                contentJson = Document.fromPlainText("$name blocked you.").toJson(),
                createdAt = System.currentTimeMillis() + 5_000,
                displayMode = "messenger",
            ),
        )
        _uiState.update { it.copy(errorMessage = "$name blocked you. You can't message them.") }
    }

    // ------------------------------------------------------ message actions

    /** Opens the profile popout for a character, or for the writer when [characterId] is null and [isYou]. */
    fun openProfile(characterId: String?, name: String, colorHex: String, isYou: Boolean = false) {
        viewModelScope.launch {
            if (isYou) {
                val persona = roomSeeder.defaultPersona()
                _uiState.update {
                    it.copy(
                        profile = DiscordProfileUi(
                            characterId = null,
                            name = persona.name.ifBlank { "You" },
                            colorHex = avatarColorHexFor(persona.name.ifBlank { "You" }, null),
                            about = persona.description.ifBlank { "The writer behind every world here." },
                            memberSince = persona.updatedAt.takeIf { t -> t > 0 } ?: System.currentTimeMillis(),
                            isMember = false,
                            isYou = true,
                            roles = listOf("Writer", "Server Owner"),
                        ),
                    )
                }
                return@launch
            }
            val character = characterId?.let { db.roleplayDao().getCharacter(it) }
                ?: charactersById.values.firstOrNull { it.name.equals(name, ignoreCase = true) }
            val member = _uiState.value.members.firstOrNull { m -> m.characterId == character?.id }
            val state = _uiState.value
            val serverRoles = character?.id?.let { id -> state.memberRoles[id] }.orEmpty()
                .let { ids -> state.roles.filter { it.id in ids }.map { it.name } }
            val roles = buildList {
                addAll(serverRoles)
                add(if (character?.inParty == true) "Party" else "Character")
                if (member != null) add(if (member.joinedViaMention) "Invited" else "Room regular")
                _uiState.value.selectedServer?.let { s -> add(if (s.workType == "campaign") "Adventurer" else "Cast") }
            }
            _uiState.update {
                it.copy(
                    profile = DiscordProfileUi(
                        characterId = character?.id,
                        name = character?.name ?: name,
                        colorHex = character?.let { c -> avatarColorHexFor(c.name, c.colorHex) } ?: colorHex,
                        about = character?.description?.trim()?.take(PROFILE_ABOUT_CHARS)
                            ?.ifBlank { null }
                            ?: character?.personality?.trim()?.take(PROFILE_ABOUT_CHARS).orEmpty(),
                        memberSince = character?.createdAt ?: System.currentTimeMillis(),
                        isMember = member != null,
                        isYou = false,
                        roles = roles,
                    ),
                )
            }
        }
    }

    fun closeProfile() {
        _uiState.update { it.copy(profile = null) }
    }

    /** Adds or removes the writer's own [emoji] reaction on a message. */
    fun toggleReaction(messageId: String, emoji: String) {
        viewModelScope.launch {
            val msg = db.roleplayDao().getRpMessage(messageId) ?: return@launch
            val mine = msg.userReactions.split(',').filter { it.isNotBlank() }.toMutableList()
            val counts = decodeReactions(msg.reactionsJson).toMutableMap()
            if (emoji in mine) {
                mine.remove(emoji)
                val next = (counts[emoji] ?: 1) - 1
                if (next <= 0) counts.remove(emoji) else counts[emoji] = next
            } else {
                mine.add(emoji)
                counts[emoji] = (counts[emoji] ?: 0) + 1
            }
            db.roleplayDao().upsertMessage(
                msg.copy(reactionsJson = encodeReactions(counts), userReactions = mine.joinToString(",")),
            )
        }
    }

    fun startReply(message: DiscordMessageUi) {
        _uiState.update {
            it.copy(
                replyingTo = DiscordReplyPreviewUi(
                    messageId = message.id,
                    authorName = message.authorName,
                    authorColorHex = message.authorColorHex,
                    snippet = message.text.replace('\n', ' ').take(REPLY_SNIPPET_CHARS)
                        .ifBlank { "Click to see attachment" },
                ),
            )
        }
    }

    fun cancelReply() {
        _uiState.update { it.copy(replyingTo = null) }
    }

    /** Rewrites a message's text in place and marks it (edited). */
    fun editMessage(messageId: String, newText: String) {
        val clean = newText.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            val msg = db.roleplayDao().getRpMessage(messageId) ?: return@launch
            val old = documentFromJson(msg.contentJson)
            val media = old.blocks.filterNot { it is com.ihy2ln.weaverse.core.text.Paragraph }
            val doc = Document(
                blocks = listOf(
                    com.ihy2ln.weaverse.core.text.Paragraph(
                        "p-${System.currentTimeMillis()}",
                        listOf(com.ihy2ln.weaverse.core.text.Span(clean)),
                    ),
                ) + media,
            )
            db.roleplayDao().upsertMessage(msg.copy(contentJson = doc.toJson(), isEdited = true))
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            db.roleplayDao().deleteMessage(messageId)
            if (_uiState.value.replyingTo?.messageId == messageId) cancelReply()
        }
    }

    fun togglePin(messageId: String) {
        viewModelScope.launch {
            val msg = db.roleplayDao().getRpMessage(messageId) ?: return@launch
            db.roleplayDao().upsertMessage(msg.copy(pinned = !msg.pinned))
        }
    }

    /** Makes the message and everything after it unread again. */
    fun markUnreadFrom(message: DiscordMessageUi) {
        val room = boundRoom ?: return
        viewModelScope.launch {
            val chat = db.roleplayDao().getChat(room.id) ?: return@launch
            val read = chat.copy(lastReadAt = message.createdAt - 1)
            db.roleplayDao().upsertChat(read)
            boundRoom = read
            _uiState.update { it.copy(unreadSince = message.createdAt - 1) }
        }
    }

    /** Marks every room in the selected server (or every DM on Home) as read. */
    fun markServerRead() {
        val serverId = _uiState.value.selectedServerId
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            allChats.filter { chat ->
                chat.displayMode == "messenger" &&
                    if (serverId == null) chat.bookId == null else chat.bookId == serverId
            }.forEach { chat -> db.roleplayDao().upsertChat(chat.copy(lastReadAt = now)) }
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
            // The meter counts the codex too, since every send carries it.
            val codexEntries = castResolver.codexContextFor(state.input, room.bookId, members)
            val system = buildSystemBlocks(
                room = room,
                book = book,
                roomCharacter = roomCharacter,
                persona = persona,
                members = members,
                mentioned = emptyList(),
                outputWords = state.maximumWords,
                codexRefs = castResolver.describe(codexEntries),
            )
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

    // -------------------------------------------------------------- servers you make

    /** Publishes the selected server's roles, members and codex picks. */
    private fun publishServerSettings() {
        val settings = _uiState.value.selectedServerId?.let { serverSettingsById[it] }
        val roles = settings?.let { ServerJson.roles(it.rolesJson) }.orEmpty()
        val memberRoles = settings?.let { ServerJson.memberRoles(it.memberRolesJson) }.orEmpty()
        val changedColors = roles != _uiState.value.roles || memberRoles != _uiState.value.memberRoles
        _uiState.update {
            it.copy(
                roles = roles,
                memberRoles = memberRoles,
                serverMemberIds = settings?.let { s -> ServerJson.ids(s.memberIdsJson) }.orEmpty(),
                serverCodexIds = settings?.let { s -> ServerJson.ids(s.codexIdsJson) }.orEmpty(),
            )
        }
        // Names in chat take their role's color, so a role change repaints the open room.
        if (changedColors && lastRawMessages.isNotEmpty()) viewModelScope.launch { publishMessages(lastRawMessages) }
    }

    /** Loads every character, for choosing who's in a server. */
    fun loadPeople() {
        viewModelScope.launch {
            val people = castResolver.allChatContacts().map { character ->
                DiscordMemberUi(
                    characterId = character.id,
                    name = character.name,
                    colorHex = avatarColorHexFor(character.name, character.colorHex),
                    monogram = monogramOf(character.name),
                    joinedViaMention = false,
                )
            }.sortedBy { it.name.lowercase() }
            _uiState.update { it.copy(people = people) }
        }
    }

    /**
     * Makes a server of the writer's own: not a novel or campaign, just a place to chat.
     * [memberIds] are who's in it (empty = every character); [codexIds] limit what its AI
     * knows (empty = the whole codex).
     */
    fun createServer(
        name: String,
        description: String,
        colorHex: String,
        emoji: String,
        memberIds: List<String>,
        codexIds: List<String>,
        onCreated: (String) -> Unit,
    ) {
        val title = name.trim()
        if (title.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val book = BookEntity(
                id = "server-${UUID.randomUUID()}",
                seriesId = null,
                title = title,
                createdAt = now,
                updatedAt = now,
                workType = WORK_TYPE_SERVER,
            )
            // Settings first: seeding reads who the server seats.
            db.chatServerDao().upsert(
                com.ihy2ln.weaverse.data.db.entities.ChatServerEntity(
                    bookId = book.id,
                    description = description.trim(),
                    colorHex = colorHex,
                    emoji = emoji.trim(),
                    memberIdsJson = ServerJson.ids(memberIds),
                    codexIdsJson = ServerJson.ids(codexIds),
                    updatedAt = now,
                ),
            )
            db.bookDao().upsert(book)
            roomSeeder.ensureRoomsForBook(book)
            onCreated(book.id)
        }
    }

    /** Server Settings → Overview, Members and Codex. [name] and [memberIds] only apply to your own servers. */
    fun updateServer(
        name: String,
        description: String,
        colorHex: String,
        emoji: String,
        memberIds: List<String>,
        codexIds: List<String>,
    ) {
        val serverId = _uiState.value.selectedServerId ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val book = db.bookDao().getById(serverId) ?: return@launch
            val own = book.workType == WORK_TYPE_SERVER
            val current = db.chatServerDao().get(serverId)
                ?: com.ihy2ln.weaverse.data.db.entities.ChatServerEntity(bookId = serverId)
            db.chatServerDao().upsert(
                current.copy(
                    description = description.trim(),
                    colorHex = colorHex,
                    emoji = emoji.trim(),
                    memberIdsJson = if (own) ServerJson.ids(memberIds) else current.memberIdsJson,
                    codexIdsJson = ServerJson.ids(codexIds),
                    updatedAt = now,
                ),
            )
            if (own && name.isNotBlank() && name.trim() != book.title) {
                db.bookDao().upsert(book.copy(title = name.trim(), updatedAt = now))
            }
            if (own) {
                // Someone newly added gets their own room, like the rest of the cast.
                val rooms = db.roleplayDao().getAllRoomsForBook(serverId)
                memberIds.forEach { characterId ->
                    if (rooms.any { it.roomKind == ROOM_KIND_CHARACTER && it.characterId == characterId }) return@forEach
                    val character = db.roleplayDao().getCharacter(characterId) ?: return@forEach
                    val room = roomSeeder.createRoom(
                        book = book,
                        name = character.name,
                        kind = ROOM_KIND_CHARACTER,
                        characterId = character.id,
                        topic = "A private room where ${character.name} hangs out.",
                        character = character,
                    )
                    castResolver.addMember(room.id, character, seeded = true)
                }
            }
        }
    }

    /** Deletes a server you made, with every room and message in it. Novels' and campaigns' servers can't be deleted. */
    fun deleteServer(serverId: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            val book = db.bookDao().getById(serverId) ?: return@launch
            if (book.workType != WORK_TYPE_SERVER) return@launch
            db.roleplayDao().getAllRoomsForBook(serverId).forEach { room ->
                db.roleplayDao().getMessages(room.id).forEach { db.roleplayDao().deleteMessage(it.id) }
                db.roleplayDao().getMembers(room.id).forEach { db.roleplayDao().deleteMember(room.id, it.characterId) }
                db.roleplayDao().deleteChat(room.id)
            }
            db.chatServerDao().delete(serverId)
            db.bookDao().deleteById(serverId)
            onDeleted()
        }
    }

    private fun editServerSettings(
        transform: (com.ihy2ln.weaverse.data.db.entities.ChatServerEntity) -> com.ihy2ln.weaverse.data.db.entities.ChatServerEntity,
    ) {
        val serverId = _uiState.value.selectedServerId ?: return
        viewModelScope.launch {
            val current = db.chatServerDao().get(serverId)
                ?: com.ihy2ln.weaverse.data.db.entities.ChatServerEntity(bookId = serverId)
            db.chatServerDao().upsert(transform(current).copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun addRole(name: String, colorHex: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        editServerSettings { s ->
            s.copy(rolesJson = ServerJson.roles(ServerJson.roles(s.rolesJson) + ServerRole("role-${UUID.randomUUID()}", clean, colorHex)))
        }
    }

    fun deleteRole(roleId: String) = editServerSettings { s ->
        s.copy(
            rolesJson = ServerJson.roles(ServerJson.roles(s.rolesJson).filterNot { it.id == roleId }),
            memberRolesJson = ServerJson.memberRoles(ServerJson.memberRoles(s.memberRolesJson).mapValues { (_, ids) -> ids - roleId }),
        )
    }

    /** Moves a role up (toward the top, which wins the name color) or down. */
    fun moveRole(roleId: String, up: Boolean) = editServerSettings { s ->
        val roles = ServerJson.roles(s.rolesJson).toMutableList()
        val index = roles.indexOfFirst { it.id == roleId }
        val target = if (up) index - 1 else index + 1
        if (index < 0 || target !in roles.indices) s
        else s.copy(rolesJson = ServerJson.roles(roles.apply { add(target, removeAt(index)) }))
    }

    fun toggleMemberRole(characterId: String, roleId: String) = editServerSettings { s ->
        val map = ServerJson.memberRoles(s.memberRolesJson).toMutableMap()
        val current = map[characterId].orEmpty()
        map[characterId] = if (roleId in current) current - roleId else current + roleId
        s.copy(memberRolesJson = ServerJson.memberRoles(map))
    }

    // -------------------------------------------------------------- threads and forums

    fun beginThread(message: DiscordMessageUi?) = _uiState.update { it.copy(threadDraftFor = message) }

    /** Starts a thread off a message: the message opens the thread, and the room's people come along. */
    fun createThread(name: String, onOpen: (String) -> Unit) {
        val message = _uiState.value.threadDraftFor ?: return
        val parent = boundRoom ?: return
        _uiState.update { it.copy(threadDraftFor = null) }
        viewModelScope.launch {
            val book = parent.bookId?.let { booksById[it] } ?: return@launch
            val title = name.trim().ifBlank { message.text.replace('\n', ' ').take(THREAD_TITLE_CHARS) }.ifBlank { "Thread" }
            val thread = roomSeeder.createRoom(
                book = book,
                name = title,
                kind = ROOM_KIND_THREAD,
                characterId = null,
                topic = "Thread from #${parent.title}",
                character = null,
                parentRoomId = parent.id,
            )
            castResolver.membersOf(parent.id).forEach { castResolver.addMember(thread.id, it, seeded = true) }
            val now = System.currentTimeMillis()
            db.roleplayDao().getMessages(parent.id).firstOrNull { it.id == message.id }?.let { original ->
                db.roleplayDao().upsertMessage(
                    original.copy(
                        id = "rpm-${UUID.randomUUID()}",
                        chatId = thread.id,
                        swipeGroupId = "sw-${UUID.randomUUID()}",
                        createdAt = now,
                        pinned = false,
                        replyToId = null,
                        reactionsJson = "{}",
                        userReactions = "",
                    ),
                )
            }
            systemLine(parent.id, "You started a thread: $title", now + 1)
            onOpen(thread.id)
        }
    }

    /** Creates a channel of [kind]: text, forum or voice. */
    fun createChannel(name: String, kind: String) {
        val serverId = _uiState.value.selectedServerId ?: return
        val clean = name.trim().trimStart('#').trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            val book = booksById[serverId] ?: return@launch
            val topic = when (kind) {
                ROOM_KIND_FORUM -> "Start a post; everyone can reply in it."
                ROOM_KIND_VOICE -> "Hang out and talk out loud."
                else -> "A channel about ${book.title}."
            }
            val room = roomSeeder.createRoom(book, clean, kind, null, topic, null)
            if (kind == ROOM_KIND_FORUM || kind == ROOM_KIND_VOICE) {
                val cast = castResolver.castForBook(book)
                cast.shuffled().take(cast.size.coerceIn(0, 5)).forEach { castResolver.addMember(room.id, it, seeded = true) }
            }
        }
    }

    /** New forum post: a thread named [title] whose first message is [body], answered like any message. */
    fun createForumPost(title: String, body: String, onOpen: (String) -> Unit) {
        val forum = boundRoom?.takeIf { it.roomKind == ROOM_KIND_FORUM } ?: return
        if (title.isBlank() && body.isBlank()) return
        viewModelScope.launch {
            val book = forum.bookId?.let { booksById[it] } ?: return@launch
            val post = roomSeeder.createRoom(
                book = book,
                name = title.trim().ifBlank { body.replace('\n', ' ').take(THREAD_TITLE_CHARS) },
                kind = ROOM_KIND_THREAD,
                characterId = null,
                topic = "Post in #${forum.title}",
                character = null,
                parentRoomId = forum.id,
            )
            castResolver.membersOf(forum.id).forEach { castResolver.addMember(post.id, it, seeded = true) }
            if (body.isNotBlank()) {
                draftsByRoom[post.id] = body.trim()
                pendingAutoSend = post.id
            }
            onOpen(post.id)
        }
    }

    private suspend fun systemLine(roomId: String, text: String, at: Long = System.currentTimeMillis()) {
        db.roleplayDao().upsertMessage(
            RpMessageEntity(
                id = "rpm-${UUID.randomUUID()}",
                chatId = roomId,
                swipeGroupId = "sw-${UUID.randomUUID()}",
                swipeIndex = 0,
                isActiveSwipe = true,
                role = "system",
                contentJson = Document.fromPlainText(text).toJson(),
                createdAt = at,
                displayMode = "messenger",
            ),
        )
    }

    // -------------------------------------------------------------- voice

    fun joinVoice() {
        val roomId = _uiState.value.selectedRoomId ?: return
        _uiState.update { it.copy(voice = VoiceUi(roomId = roomId, deafened = it.voice.deafened)) }
    }

    fun leaveVoice() {
        tts.stop()
        _uiState.update { it.copy(voice = VoiceUi(deafened = it.voice.deafened)) }
    }

    fun toggleDeafen() {
        val deafened = !_uiState.value.voice.deafened
        if (deafened) tts.stop()
        _uiState.update { it.copy(voice = it.voice.copy(deafened = deafened, speakingCharacterId = null)) }
    }

    /** Reads lines aloud in order, lighting up whoever is talking. */
    private fun speak(lines: List<Pair<String?, String>>) {
        if (lines.isEmpty()) return
        tts.speakParagraphs(
            lines.map { it.second },
            onProgress = { index ->
                _uiState.update { it.copy(voice = it.voice.copy(speakingCharacterId = lines.getOrNull(index)?.first)) }
            },
            onFinished = { _uiState.update { it.copy(voice = it.voice.copy(speakingCharacterId = null)) } },
        )
    }

    // -------------------------------------------------------------- codex

    fun beginCodexSave(message: DiscordMessageUi?) = _uiState.update { it.copy(codexSaveFor = message) }

    /** Adds a codex entry. In a server limited to picked entries it joins the picks, so its AI knows it. */
    fun createCodexEntry(name: String, categoryId: String, text: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        val serverId = _uiState.value.selectedServerId
        _uiState.update { it.copy(codexSaveFor = null) }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val entry = com.ihy2ln.weaverse.data.db.entities.CodexEntryEntity(
                id = "entry-${UUID.randomUUID()}",
                categoryId = categoryId,
                scopeType = com.ihy2ln.weaverse.data.repo.CodexScopes.TYPE,
                scopeId = com.ihy2ln.weaverse.data.repo.CodexScopes.ID,
                name = clean,
                docJson = Document.fromPlainText(text.trim()).toJson(),
                plainText = text.trim(),
                trackMentions = true,
                createdAt = now,
                updatedAt = now,
            )
            db.codexDao().upsertEntry(entry)
            if (serverId != null && _uiState.value.serverCodexIds.isNotEmpty()) {
                editServerSettings { s -> s.copy(codexIdsJson = ServerJson.ids(ServerJson.ids(s.codexIdsJson) + entry.id)) }
            }
        }
    }

    /** Adds [text] as a new paragraph at the end of an existing codex entry. */
    fun appendToCodexEntry(entryId: String, text: String) {
        val clean = text.trim()
        _uiState.update { it.copy(codexSaveFor = null) }
        if (clean.isBlank()) return
        viewModelScope.launch {
            val entry = db.codexDao().getAllEntries().firstOrNull { it.id == entryId } ?: return@launch
            val doc = documentFromJson(entry.docJson)
            val paragraph = com.ihy2ln.weaverse.core.text.Paragraph(
                "p-${UUID.randomUUID()}",
                listOf(com.ihy2ln.weaverse.core.text.Span(clean)),
            )
            db.codexDao().upsertEntry(
                entry.copy(
                    docJson = doc.copy(blocks = doc.blocks + paragraph).toJson(),
                    plainText = (entry.plainText.trimEnd() + "\n\n" + clean).trim(),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    // -------------------------------------------------------------- server search

    /** Searches every room in the selected server, newest first. */
    fun searchServer(query: String) {
        val serverId = _uiState.value.selectedServerId
        val terms = query.trim()
        if (serverId == null || terms.isBlank()) {
            _uiState.update { it.copy(serverSearch = emptyList()) }
            return
        }
        viewModelScope.launch {
            val rooms = allChats.filter { it.bookId == serverId && it.displayMode == "messenger" && it.roomKind != ROOM_KIND_CODEX }
            val hits = rooms.flatMap { room ->
                db.roleplayDao().getMessagesForMode(room.id, "messenger")
                    .filter { it.isActiveSwipe && it.role != "system" }
                    .mapNotNull { msg ->
                        val text = documentFromJson(msg.contentJson).plainText().trim()
                        if (!text.contains(terms, ignoreCase = true)) return@mapNotNull null
                        val character = msg.speakerCharacterId?.let { charactersById[it] }
                        val author = when {
                            msg.role == "user" -> "You"
                            character != null -> character.name
                            else -> msg.speakerName.ifBlank { "Unknown" }
                        }
                        ServerSearchHit(
                            roomId = room.id,
                            roomName = room.title,
                            message = DiscordMessageUi(
                                id = msg.id,
                                authorName = author,
                                authorColorHex = avatarColorHexFor(author, character?.colorHex),
                                isUser = msg.role == "user",
                                isBot = false,
                                text = text,
                                hasMedia = false,
                                createdAt = msg.createdAt,
                                authorCharacterId = character?.id,
                            ),
                        )
                    }
            }.sortedByDescending { it.message.createdAt }.take(SERVER_SEARCH_LIMIT)
            if (_uiState.value.selectedServerId == serverId) _uiState.update { it.copy(serverSearch = hits) }
        }
    }

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
                    // The codex, voice and forum channels aren't conversations of their own.
                    it.roomKind !in QUIET_KINDS &&
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
            parentRoomId = parentRoomId,
        )
    }

    private suspend fun publishMessages(messages: List<RpMessageEntity>) {
        lastRawMessages = messages
        val state = _uiState.value
        val roleColors = state.roleColors
        val roomId = messages.firstOrNull()?.chatId ?: state.selectedRoomId ?: return
        val room = (state.rooms + state.directMessages).find { it.chatId == roomId } ?: state.selectedRoom
        val roomCharacter = room?.characterId?.let { charactersById[it] }
        val serverTitle = state.selectedServer?.title
        val memberNames = state.members.map { it.name }
        val byId = messages.associateBy { it.id }
        val persona = roomSeeder.defaultPersona().name.ifBlank { "You" }
        if (persona != state.personaName) _uiState.update { it.copy(personaName = persona) }
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
                    authorColorHex = character?.let { roleColors[it.id] ?: avatarColorHexFor(it.name, it.colorHex) }
                        ?: avatarColorHexFor(authorName, null),
                    isUser = isUser,
                    // Only a message we cannot attribute to any person is an app/bot line.
                    isBot = !isUser && !isSystem && authorName == "Unknown",
                    isSystem = isSystem,
                    text = documentFromJson(msg.contentJson).plainText().trim(),
                    hasMedia = documentFromJson(msg.contentJson).hasMedia(),
                    mediaPaths = mediaPathsOf(msg),
                    createdAt = msg.createdAt,
                    authorCharacterId = character?.id ?: if (isUser) null else roomCharacter?.id,
                    reactions = decodeReactions(msg.reactionsJson).map { (emoji, count) ->
                        DiscordReactionUi(emoji, count, emoji in msg.userReactions.split(','))
                    },
                    replyTo = msg.replyToId?.let { id ->
                        val target = byId[id] ?: return@let null
                        val targetCharacter = target.speakerCharacterId?.let { charactersById[it] }
                        val name = when {
                            target.role == "user" -> "You"
                            targetCharacter != null -> targetCharacter.name
                            target.speakerName.isNotBlank() -> target.speakerName
                            else -> roomCharacter?.name ?: "Unknown"
                        }
                        DiscordReplyPreviewUi(
                            messageId = id,
                            authorName = name,
                            authorColorHex = avatarColorHexFor(name, targetCharacter?.colorHex),
                            snippet = documentFromJson(target.contentJson).plainText()
                                .replace('\n', ' ').trim().take(REPLY_SNIPPET_CHARS)
                                .ifBlank { "Click to see attachment" },
                        )
                    },
                    pinned = msg.pinned,
                    edited = msg.isEdited,
                    mentionsYou = !isUser && mentionsWriter(documentFromJson(msg.contentJson).plainText(), persona),
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
        refreshServerBadges()
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

    /** Sums unread messages per server and across DMs for the rail's badges. */
    private suspend fun refreshServerBadges() {
        val perServer = mutableMapOf<String, Int>()
        var dms = 0
        allChats.filter { it.displayMode == "messenger" }.forEach { chat ->
            if (chat.id == _uiState.value.selectedRoomId) return@forEach
            val unread = db.roleplayDao().countUnread(chat.id, chat.lastReadAt)
            if (unread <= 0) return@forEach
            val bookId = chat.bookId
            if (bookId == null) dms += unread else perServer[bookId] = (perServer[bookId] ?: 0) + unread
        }
        _uiState.update { it.copy(serverUnread = perServer, dmUnread = dms) }
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

    /**
     * Drops *asterisk stage directions* from a chat reply. The prompt forbids them and
     * character cards keep reintroducing them; a chat app only carries what someone
     * typed, so they are removed rather than shown.
     */
    private fun stripStageDirections(text: String): String {
        val wrapped = Regex("""\*{1,2}([^*
]{1,200})\*{1,2}""")
        val runOfSpaces = Regex("""\s{2,}""")
        return text.lines()
            .map { line ->
                val trimmed = line.trim()
                val cleaned = wrapped.replace(trimmed) { match ->
                    val inner = match.groupValues[1].trim()
                    when {
                        // *word* is emphasis people actually type — keep the word.
                        inner.split(' ').size < 3 -> inner
                        // A line that is only an aside disappears with it.
                        match.value.length >= trimmed.length -> ""
                        else -> " "
                    }
                }
                runOfSpaces.replace(cleaned, " ").replace(" ,", ",").replace(" .", ".").trim()
            }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    private fun buildSystemBlocks(
        room: RpChatEntity,
        book: BookEntity?,
        roomCharacter: RpCharacterEntity?,
        persona: RpPersonaEntity,
        members: List<RpCharacterEntity>,
        mentioned: List<RpCharacterEntity>,
        outputWords: Int,
        /** People named in the message who are not seated here, and may answer for themselves. */
        discussed: List<RpCharacterEntity> = emptyList(),
        /** Seated people the message names — the ones actually being spoken to. */
        addressed: List<RpCharacterEntity> = emptyList(),
        /** Codex the reply must obey, grouped by the category that governs it. */
        codexRefs: List<ChatCastResolver.CodexRef> = emptyList(),
        /** The writer pinged @everyone or @here. */
        everyone: Boolean = false,
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
                if (book != null && book.workType == WORK_TYPE_SERVER) {
                    appendLine("This is the Discord server \"${book.title}\".")
                    serverSettingsById[book.id]?.description?.takeIf { it.isNotBlank() }?.let { appendLine("About the server: $it") }
                } else if (book != null) {
                    appendLine("Everyone here knows the world of \"${book.title}\" and can talk about it.")
                    if (book.genre.isNotBlank()) appendLine("Genre of that world: ${book.genre}.")
                }
                val parent = room.parentRoomId?.let { id -> allChats.firstOrNull { it.id == id } }
                when {
                    room.roomKind == ROOM_KIND_THREAD && parent?.roomKind == ROOM_KIND_FORUM ->
                        appendLine("This is the forum post \"${room.title}\" in #${parent.title}. Stay on the post's topic.")
                    room.roomKind == ROOM_KIND_THREAD && parent != null ->
                        appendLine("This is the thread \"${room.title}\" off #${parent.title}. Stay on the thread's topic.")
                    room.roomKind == ROOM_KIND_VOICE ->
                        appendLine("This is the voice channel ${room.title}: people are talking out loud, so write what they say aloud. Spoken words only: no emoji, no picture tags, no links.")
                    else -> appendLine("This is the #${room.title} channel.")
                }
                if (room.authorsNote.isNotBlank()) appendLine("Channel topic: ${room.authorsNote}")
                append(
                    "Write ONLY what a person types into a chat app: first person, present tense, " +
                        "casual and short. Emoji are allowed and encouraged where they fit the " +
                        "person speaking — a reaction, a tone-setter, or a reply on their own. This is a live conversation, not a story or a novel — " +
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
        // The codex is the world's rulebook, not trivia: the reply has to behave by it.
        if (codexRefs.isNotEmpty()) {
            val (people, lore) = codexRefs.partition { it.category.equals("Characters", true) }
            blocks += "Codex. This is established canon — obey it in what people say, know, " +
                "want, and are able to do. Apply it; never recite, explain, or narrate it, and " +
                "never mention the codex itself."
            if (people.isNotEmpty()) {
                blocks += "Who these people are:"
                people.forEach { ref -> blocks += "${ref.name}: ${ref.text.take(CODEX_BLOCK_CHARS)}" }
            }
            if (lore.isNotEmpty()) {
                blocks += "Rules of this world, which constrain everyone here:"
                lore.forEach { ref ->
                    val label = ref.category.takeIf { it.isNotBlank() }?.let { "[$it] " }.orEmpty()
                    blocks += "$label${ref.name}: ${ref.text.take(CODEX_BLOCK_CHARS)}"
                }
            }
            blocks += "If the codex and your own guess disagree, the codex wins. If the codex " +
                "does not cover something, keep it vague rather than inventing canon."
        }
        val outsiders = discussed.filterNot { person ->
            person.id == roomCharacter?.id || members.any { it.id == person.id }
        }
        if (outsiders.isNotEmpty()) {
            blocks += "The message talks about ${outsiders.joinToString(", ") { it.name }}, " +
                "who can read this room and may answer for themselves rather than being " +
                "spoken about in the third person. Stay true to each card:"
            outsiders.forEach { character ->
                blocks += RoleplayPromptBuilder.characterBlock(character, com.ihy2ln.weaverse.feature.shell.AppMode.Chatting)
            }
        }
        if (everyone && addressed.isNotEmpty()) {
            blocks += "The writer pinged @everyone, so several people here answer: " +
                "${addressed.joinToString(", ") { it.name }} each reply in their own voice, one line each, " +
                "as \"Name: what they say\". They don't all have to agree."
        } else if (mentioned.isNotEmpty()) {
            val cap = (members.size + outsiders.size).coerceAtMost(3).coerceAtLeast(1)
            blocks += "The user addressed ${mentioned.joinToString(", ") { "@${it.name}" }}. " +
                "${mentioned.first().name} replies first. Any of the other people in the room may reply too, " +
                "but only if they have something relevant to add — silence is fine. Write each line as " +
                "\"Name: what they say\" in plain text, with no markdown, no bold, and no asterisks around the " +
                "name — one speaker per line, never narrate for the user, at most $cap lines total."
        } else if (addressed.isNotEmpty()) {
            blocks += "The message is addressed to ${addressed.first().name}, so " +
                "${addressed.first().name} answers it. Others in the room may add a line " +
                "only if they have something relevant."
        } else if (outsiders.isNotEmpty()) {
            blocks += "The message is about ${outsiders.first().name}, so ${outsiders.first().name} " +
                "answers it personally. Others in the room may add a line only if they have " +
                "something relevant."
        }
        // Last block on purpose: the closest instruction to the reply is the one models
        // follow best, and character cards keep dragging replies back toward prose.
        val firstSpeaker = when {
            mentioned.isNotEmpty() -> mentioned.first().name
            addressed.isNotEmpty() -> addressed.first().name
            outsiders.isNotEmpty() -> outsiders.first().name
            roomCharacter != null -> roomCharacter.name
            else -> members.firstOrNull()?.name.orEmpty()
        }
        // Memes and GIFs people can drop in, and how open the chat is (the app's Age rating).
        blocks += com.ihy2ln.weaverse.feature.chatting.media.MediaTags.PROMPT
        blocks += if (safetyCache.adultEnabled)
            com.ihy2ln.weaverse.feature.chatting.media.SocialContentPolicy.prompt()
        else "WeaverSocial 18+ is off: no sexual messages, nudity, adult creator promotions, or sexual media searches."
        if (room.roomKind == ROOM_KIND_DM) blocks += com.ihy2ln.weaverse.feature.chatting.social.SocialTags.BLOCK_PROMPT
        blocks += buildString {
            appendLine("Output format, no exceptions:")
            appendLine("- Every line is \"Name: what they type\" and nothing else.")
            if (firstSpeaker.isNotBlank()) appendLine("- The first line starts with \"$firstSpeaker:\".")
            appendLine("- No asterisks, no *actions*, no markdown, no bold, no narration.")
            appendLine("- Emoji are fine and in character; they are typed, not narrated.")
            appendLine("- A line may end with one [gif: …], [meme: …] or [pic: …] tag; it becomes the picture.")
            appendLine("- No describing anyone's body, face, clothing, or movements.")
            append("- Never write a line for the user.")
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
        private val SERVER_WORK_TYPES = setOf("novel", "campaign", WORK_TYPE_SERVER)
        private const val MAX_EVERYONE = 5
        private val QUIET_KINDS = setOf(ROOM_KIND_CODEX, ROOM_KIND_VOICE, ROOM_KIND_FORUM)
        private const val THREAD_TITLE_CHARS = 48
        private const val SERVER_SEARCH_LIMIT = 100
        private val ROOM_KINDS = setOf(
            ROOM_KIND_CHANNEL, ROOM_KIND_CHARACTER, ROOM_KIND_THREAD, ROOM_KIND_FORUM, ROOM_KIND_VOICE, ROOM_KIND_CODEX,
        )
        private const val HISTORY_LIMIT = 24
        /** How much of a codex entry to quote per reference block. */
        private const val CODEX_BLOCK_CHARS = 700
        /** Outsiders a single message can summon, so a name-dense line stays a chat. */
        private const val MAX_DISCUSSED = 2
        /** Room for the action row, the message box and the model row. */
        private const val DEFAULT_DOCK_HEIGHT_DP = 172f
        /** Room for the extra chips and the quick-message grid under "More". */
        private const val EXPANDED_DOCK_HEIGHT_DP = 330f
        private const val CHAT_PROMPT_FOLDER = "folder-chatting"
        private const val CUSTOM_PROMPT_FOLDER = "folder-custom"
        /** Names that mean "not a person in the room" and get reassigned to the addressee. */
        private val NARRATOR_WORDS = listOf("narrator", "narration", "system", "server host", "host bot")
        private const val RECENT_CONVERSATIONS_LIMIT = 12
        private const val MULTI_SPEAKER_STAGGER_MS = 1_200L
        private const val DAY_MS = 24L * 60L * 60L * 1000L
        private const val READ_GRACE_MS = 1_000L
        private const val REPLY_SNIPPET_CHARS = 90
        private const val PROFILE_ABOUT_CHARS = 600
        private const val AI_REACTION_CHANCE = 0.35f
        private val AI_REACTION_POOL = listOf("👍", "❤️", "😂", "🔥", "😮", "💯", "👀")
    }
}

private val reactionJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

/** Emoji → count, in insertion order; tolerant of blank or malformed JSON. */
internal fun decodeReactions(json: String): Map<String, Int> = runCatching {
    reactionJson.decodeFromString<Map<String, Int>>(json.ifBlank { "{}" })
}.getOrDefault(emptyMap()).filterValues { it > 0 }

internal fun encodeReactions(counts: Map<String, Int>): String =
    reactionJson.encodeToString<Map<String, Int>>(counts.filterValues { it > 0 })

/** True when [text] @mentions the writer by persona name, @you, @everyone or @here. */
internal fun mentionsWriter(text: String, personaName: String): Boolean {
    val lower = text.lowercase()
    if ("@everyone" in lower || "@here" in lower || Regex("@you\\b").containsMatchIn(lower)) return true
    val name = personaName.trim().lowercase()
    return name.isNotBlank() && name != "you" && "@$name" in lower
}

/** Whether a block-based document carries any media at all. */
private fun com.ihy2ln.weaverse.core.text.Document.hasMedia(): Boolean = blocks.any { block ->
    when (block) {
        is com.ihy2ln.weaverse.core.text.MediaBlock -> true
        is com.ihy2ln.weaverse.core.text.MediaStackBlock -> true
        else -> false
    }
}
