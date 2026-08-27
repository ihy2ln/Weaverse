package com.ihy2ln.weaverse.feature.roleplay.chat

import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.ai.AIChunk
import com.ihy2ln.weaverse.ai.AIError
import com.ihy2ln.weaverse.ai.AiGenerationService
import com.ihy2ln.weaverse.ai.context.ContextMeter
import com.ihy2ln.weaverse.ai.openrouter.OpenRouterModelCache
import com.ihy2ln.weaverse.core.media.MediaClipboard
import com.ihy2ln.weaverse.core.media.MediaClipboardPayload
import com.ihy2ln.weaverse.core.media.MediaRepository
import com.ihy2ln.weaverse.core.roleplay.avatarColorHexFor
import com.ihy2ln.weaverse.core.ui.components.MediaEditAction
import com.ihy2ln.weaverse.core.text.Block
import com.ihy2ln.weaverse.core.text.Document
import com.ihy2ln.weaverse.core.text.MediaBlock
import com.ihy2ln.weaverse.core.text.MediaKind
import com.ihy2ln.weaverse.core.text.MediaStackBlock
import com.ihy2ln.weaverse.core.text.Paragraph
import com.ihy2ln.weaverse.core.text.Span
import com.ihy2ln.weaverse.core.text.TextOverlay
import com.ihy2ln.weaverse.core.text.MediaGrid
import com.ihy2ln.weaverse.core.text.PanelTemplates
import com.ihy2ln.weaverse.core.text.documentFromJson
import com.ihy2ln.weaverse.core.text.plainText
import com.ihy2ln.weaverse.core.text.stackMediaOnto
import com.ihy2ln.weaverse.core.text.stackMediaWithAdjacent
import com.ihy2ln.weaverse.core.text.toJson
import com.ihy2ln.weaverse.core.text.gridColOrUnset
import com.ihy2ln.weaverse.core.text.gridColSpanOrOne
import com.ihy2ln.weaverse.core.text.gridRowOrUnset
import com.ihy2ln.weaverse.core.text.gridRowSpanOrOne
import com.ihy2ln.weaverse.core.text.withGridCell
import com.ihy2ln.weaverse.core.text.withGridPlacement
import com.ihy2ln.weaverse.core.ui.theme.InkAccentBlue
import com.ihy2ln.weaverse.core.ui.util.UsageFormat
import com.ihy2ln.weaverse.core.ui.util.parseHexColor
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.RpCharacterEntity
import com.ihy2ln.weaverse.data.db.entities.RpChatEntity
import com.ihy2ln.weaverse.data.db.entities.RpMessageEntity
import com.ihy2ln.weaverse.data.db.entities.RpPageMeta
import com.ihy2ln.weaverse.data.db.entities.RpPersonaEntity
import com.ihy2ln.weaverse.data.db.entities.decodePages
import com.ihy2ln.weaverse.data.db.entities.encodePages
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import com.ihy2ln.weaverse.feature.roleplay.presets.defaultPresets
import com.ihy2ln.weaverse.feature.shell.WorkspaceHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** Sentinel mediaId for DM text-only tiles placed on the 3×3 grid. */
const val DM_TEXT_TILE_MEDIA_ID = "__dm_text__"

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RoleplayChatViewModel @Inject constructor(
    private val db: WeaverseDatabase,
    private val aiGeneration: AiGenerationService,
    private val mediaRepository: MediaRepository,
    private val settings: SettingsRepository,
    private val tts: com.ihy2ln.weaverse.core.tts.TtsService,
    private val mediaClipboard: MediaClipboard,
    private val workspaceHistory: WorkspaceHistory,
    private val generation: RoleplayGeneration,
    private val modelCache: OpenRouterModelCache,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RoleplayChatUiState())
    val uiState: StateFlow<RoleplayChatUiState> = _uiState.asStateFlow()
    private var bindJob: Job? = null
    private var generateJob: Job? = null
    private var rawMessages: List<RpMessageEntity> = emptyList()
    private var boundChat: RpChatEntity? = null
    private var boundCharacter: RpCharacterEntity? = null
    private var boundPersona: RpPersonaEntity? = null
    private var contextLimit = ContextMeter.DEFAULT_LIMIT

    fun bindChat(chatId: String) {
        if (_uiState.value.chatId == chatId && bindJob?.isActive == true) return
        _uiState.update { it.copy(chatId = chatId) }
        bindJob?.cancel()
        bindJob = viewModelScope.launch {
            launch {
                combine(settings.preferences, modelCache.models) { prefs, dtos ->
                    ContextMeter.limitFor(prefs.defaultModelRef, modelCache.toModelInfo(dtos))
                }.collect { limit ->
                    contextLimit = limit
                    refreshContextMeter()
                }
            }
            launch {
                settings.preferences.collect { prefs ->
                    _uiState.update {
                        it.copy(
                            presetId = prefs.roleplayPresetId,
                            showExtraPromptSurfaces = prefs.extraPromptSurfaces.roleplayButtons,
                        )
                    }
                }
            }
            launch {
                db.roleplayDao().observeChats().collect { chats ->
                    chats.find { it.id == chatId }?.let { chat ->
                        boundChat = chat
                        val character = chat.characterId?.let { id ->
                            db.roleplayDao().getCharacter(id)
                        }
                        boundCharacter = character
                        boundPersona = chat.personaId.takeIf { it.isNotBlank() }?.let { id ->
                            db.roleplayDao().getPersona(id)
                        }
                        val preset = chat.presetId?.takeIf { it.isNotBlank() }
                            ?: _uiState.value.presetId
                        var pages = decodePages(chat.pagesJson)
                        if (pages.isEmpty()) {
                            pages = listOf(RpPageMeta(id = "page-1", order = 0))
                            val backfilled = chat.copy(pagesJson = encodePages(pages))
                            boundChat = backfilled
                            db.roleplayDao().upsertChat(backfilled)
                        }
                        val activePage = _uiState.value.activePageId
                            .takeIf { id -> pages.any { it.id == id } }
                            ?: pages.first().id
                        val activeTemplate = pages.firstOrNull { it.id == activePage }?.templateId
                            ?: "classic-6"
                        _uiState.update {
                            it.copy(
                                title = chat.title,
                                displayMode = chat.displayMode.ifBlank { "messenger" },
                                presetId = preset,
                                pages = pages,
                                activePageId = activePage,
                                activeTemplateId = activeTemplate,
                            )
                        }
                        publishMessages()
                    }
                }
            }
            launch {
                _uiState
                    .map { it.displayMode.ifBlank { "messenger" } to it.chatId }
                    .distinctUntilChanged()
                    .flatMapLatest { (mode, id) ->
                        if (id.isBlank()) flowOf(emptyList())
                        else db.roleplayDao().observeMessages(id, mode)
                    }
                    .collect { messages ->
                        rawMessages = messages
                        publishMessages()
                    }
            }
        }
    }

    private fun currentDisplayMode(): String =
        _uiState.value.displayMode.ifBlank { "messenger" }

    private fun activeGridSize(): Int = when (currentDisplayMode()) {
        "dungeonMaster" -> MediaGrid.DM_SIZE
        else -> MediaGrid.SIZE
    }

    private suspend fun publishMessages() {
        val active = rawMessages.filter { it.isActiveSwipe }
        val panels = mutableListOf<RpMediaRef>()
        val statePages = _uiState.value.pages
        val defaultPageId = statePages.firstOrNull()?.id ?: "page-1"
        val activePageId = _uiState.value.activePageId.ifBlank { defaultPageId }
        fun onActivePage(pageId: String?): Boolean = (pageId ?: defaultPageId) == activePageId
        val ui = active.map { m ->
            val groupCount = rawMessages.count { it.swipeGroupId == m.swipeGroupId && it.role == m.role }
            val doc = documentFromJson(m.contentJson)
            val paths = mutableListOf<String>()
            val blockIds = mutableListOf<String>()
            val isAudioFlags = mutableListOf<Boolean>()
            val stackPaths = mutableMapOf<String, List<String>>()
            val collapsedMap = mutableMapOf<String, Boolean>()
            val caption = doc.plainText()
            val isUser = m.role == "user"
            // Real names read like a messenger; fall back only when nothing is bound.
            val speaker = if (isUser) {
                boundPersona?.name?.takeIf { it.isNotBlank() } ?: "You"
            } else {
                boundCharacter?.name?.takeIf { it.isNotBlank() }
                    ?: boundChat?.title?.takeIf { it.isNotBlank() }
                    ?: "Character"
            }
            val avatarColorHex = if (isUser) {
                avatarColorHexFor(speaker, null)
            } else {
                avatarColorHexFor(speaker, boundCharacter?.colorHex)
            }
            doc.blocks.forEach { block ->
                when (block) {
                    is MediaBlock -> {
                        if (block.mediaId == DM_TEXT_TILE_MEDIA_ID) {
                            if (onActivePage(block.pageId)) {
                                panels += RpMediaRef(
                                    messageId = m.id,
                                    blockId = block.id,
                                    path = "",
                                    caption = caption,
                                    speaker = speaker,
                                    role = m.role,
                                    gridCol = block.gridCol,
                                    gridRow = block.gridRow,
                                    gridColSpan = block.gridColSpan,
                                    gridRowSpan = block.gridRowSpan,
                                    collapsed = block.collapsed,
                                    mediaId = block.mediaId,
                                    mediaKind = MediaKind.Image,
                                    isTextTile = true,
                                )
                            }
                            return@forEach
                        }
                        val entity = mediaRepository.getById(block.mediaId)
                        val path = entity?.let { mediaRepository.resolveFile(it).absolutePath }
                        if (path != null) {
                            val audio = entity.type == "audio" || block.kind == MediaKind.Audio
                            paths += path
                            blockIds += block.id
                            isAudioFlags += audio
                            collapsedMap[block.id] = block.collapsed
                            if (onActivePage(block.pageId)) {
                                panels += RpMediaRef(
                                    messageId = m.id,
                                    blockId = block.id,
                                    path = path,
                                    caption = caption,
                                    speaker = speaker,
                                    role = m.role,
                                    gridCol = block.gridCol,
                                    gridRow = block.gridRow,
                                    gridColSpan = block.gridColSpan,
                                    gridRowSpan = block.gridRowSpan,
                                    collapsed = block.collapsed,
                                    isAudio = audio,
                                    mediaId = block.mediaId,
                                    mediaKind = block.kind,
                                    mediaScale = block.mediaScale,
                                    mediaOffsetXPercent = block.mediaOffsetXPercent,
                                    mediaOffsetYPercent = block.mediaOffsetYPercent,
                                    overlays = block.overlays,
                                    panelRotationDeg = block.panelRotationDeg,
                                )
                            }
                        }
                    }
                    is MediaStackBlock -> {
                        val resolved = block.mediaIds.mapNotNull { id ->
                            mediaRepository.getById(id)?.let { mediaRepository.resolveFile(it).absolutePath }
                        }
                        if (resolved.isNotEmpty()) {
                            val idx = block.currentIndex.coerceIn(0, resolved.lastIndex)
                            paths += resolved[idx]
                            blockIds += block.id
                            isAudioFlags += false
                            stackPaths[block.id] = resolved
                            collapsedMap[block.id] = block.collapsed
                            if (onActivePage(block.pageId)) {
                                panels += RpMediaRef(
                                    messageId = m.id,
                                    blockId = block.id,
                                    path = resolved[idx],
                                    caption = caption,
                                    speaker = speaker,
                                    role = m.role,
                                    stackedPaths = resolved,
                                    gridCol = block.gridCol,
                                    gridRow = block.gridRow,
                                    gridColSpan = block.gridColSpan,
                                    gridRowSpan = block.gridRowSpan,
                                    collapsed = block.collapsed,
                                    isAudio = false,
                                    mediaId = block.mediaIds.getOrNull(idx).orEmpty(),
                                    mediaKind = MediaKind.Image,
                                    mediaScale = block.mediaScale,
                                    mediaOffsetXPercent = block.mediaOffsetXPercent,
                                    mediaOffsetYPercent = block.mediaOffsetYPercent,
                                    overlays = block.overlays,
                                    panelRotationDeg = block.panelRotationDeg,
                                )
                            }
                        }
                    }
                    else -> Unit
                }
            }
            RpMessageUi(
                id = m.id,
                swipeGroupId = m.swipeGroupId,
                swipeIndex = m.swipeIndex,
                swipeCount = groupCount.coerceAtLeast(1),
                speaker = speaker,
                text = caption,
                role = m.role,
                createdAt = m.createdAt,
                avatarColorHex = avatarColorHex,
                mediaPaths = paths,
                mediaBlockIds = blockIds,
                mediaIsAudio = isAudioFlags,
                mediaStackPaths = stackPaths,
                mediaCollapsed = collapsedMap,
                usageText = if (m.role != "user" && (m.promptTokens > 0 || m.completionTokens > 0 || m.costUsd > 0.0)) {
                    UsageFormat.formatUsage(m.promptTokens, m.completionTokens, null, m.costUsd.takeIf { it > 0.0 })
                } else {
                    ""
                },
            )
        }
        _uiState.update {
            it.copy(messages = ui, mediaPanels = panels, canPasteMedia = mediaClipboard.hasPayload)
        }
    }

    fun onMediaEditAction(messageId: String, blockId: String, action: MediaEditAction) {
        when (action) {
            MediaEditAction.Cut -> {
                copyMedia(messageId, blockId)
                removeMedia(messageId, blockId)
            }
            MediaEditAction.Copy -> copyMedia(messageId, blockId)
            MediaEditAction.Paste -> pasteMedia(messageId)
            MediaEditAction.Delete -> removeMedia(messageId, blockId)
            MediaEditAction.Shrink -> adjustMediaSpan(messageId, blockId, -1)
            MediaEditAction.Expand -> adjustMediaSpan(messageId, blockId, 1)
            MediaEditAction.Collapse -> setMediaCollapsed(messageId, blockId, true)
            MediaEditAction.Uncollapse -> setMediaCollapsed(messageId, blockId, false)
            MediaEditAction.Stack -> stackMedia(messageId, blockId)
            MediaEditAction.Move -> Unit // manga grid handles Move in UI
            MediaEditAction.AdjustImage -> Unit // manga grid handles Adjust image in UI
            MediaEditAction.AddTextOverlay -> addTextOverlay(messageId, blockId)
        }
    }

    private fun copyMedia(messageId: String, blockId: String) {
        val panel = _uiState.value.mediaPanels.find {
            it.messageId == messageId && it.blockId == blockId
        } ?: return
        mediaClipboard.set(
            MediaClipboardPayload(
                mediaId = panel.mediaId,
                kind = panel.mediaKind,
                gridColSpan = panel.gridColSpan,
                gridRowSpan = panel.gridRowSpan,
                stackedMediaIds = if (panel.stackedPaths.size > 1) {
                    // Re-read ids from document
                    val msg = rawMessages.find { it.id == messageId } ?: return
                    val block = documentFromJson(msg.contentJson).blocks
                        .find { it.id == blockId } as? MediaStackBlock
                    block?.mediaIds.orEmpty()
                } else {
                    emptyList()
                },
            ),
        )
        _uiState.update { it.copy(canPasteMedia = true, errorMessage = "") }
    }

    private fun pasteMedia(messageId: String) {
        val payload = mediaClipboard.payload ?: return
        viewModelScope.launch {
            val current = rawMessages.find { it.id == messageId } ?: return@launch
            val doc = documentFromJson(current.contentJson)
            val pageId = _uiState.value.activePageId
            val block = if (payload.stackedMediaIds.size > 1) {
                MediaStackBlock(
                    id = "msb-${UUID.randomUUID()}",
                    mediaIds = payload.stackedMediaIds,
                    gridColSpan = payload.gridColSpan,
                    gridRowSpan = payload.gridRowSpan,
                    pageId = pageId,
                )
            } else {
                MediaBlock(
                    id = "mb-${UUID.randomUUID()}",
                    mediaId = payload.mediaId,
                    kind = payload.kind,
                    widthPercent = payload.widthPercent,
                    gridColSpan = payload.gridColSpan,
                    gridRowSpan = payload.gridRowSpan,
                    pageId = pageId,
                )
            }
            persistMessageBlocks(current, doc.blocks + block)
            _uiState.update { it.copy(canPasteMedia = mediaClipboard.hasPayload) }
        }
    }

    private fun adjustMediaSpan(messageId: String, blockId: String, delta: Int) {
        viewModelScope.launch {
            val current = rawMessages.find { it.id == messageId } ?: return@launch
            val gridSize = activeGridSize()
            val blocks = documentFromJson(current.contentJson).blocks.toMutableList()
            val index = blocks.indexOfFirst { it.id == blockId }
            if (index < 0) return@launch
            val block = blocks[index]
            val col = block.gridColOrUnset().takeIf { it >= 0 } ?: 0
            val row = block.gridRowOrUnset().takeIf { it >= 0 } ?: 0
            val nextCol = (block.gridColSpanOrOne(gridSize) + delta).coerceIn(1, gridSize - col)
            val nextRow = (block.gridRowSpanOrOne(gridSize) + delta).coerceIn(1, gridSize - row)
            blocks[index] = block.withGridPlacement(col, row, nextCol, nextRow, gridSize)
            persistMessageBlocks(current, blocks)
        }
    }

    private fun setMediaCollapsed(messageId: String, blockId: String, collapsed: Boolean) {
        viewModelScope.launch {
            val current = rawMessages.find { it.id == messageId } ?: return@launch
            val blocks = documentFromJson(current.contentJson).blocks.toMutableList()
            val index = blocks.indexOfFirst { it.id == blockId }
            if (index < 0) return@launch
            blocks[index] = when (val block = blocks[index]) {
                is MediaBlock -> block.copy(collapsed = collapsed)
                is MediaStackBlock -> block.copy(collapsed = collapsed)
                else -> return@launch
            }
            persistMessageBlocks(current, blocks)
        }
    }

    fun onInputChange(value: String) {
        _uiState.update { it.copy(input = value, errorMessage = "") }
        refreshContextMeter()
    }

    fun updateOutputWords(words: Int) {
        _uiState.update { it.copy(outputWords = words.coerceIn(50, 4000)) }
    }

    fun setGenerationVisible(visible: Boolean) {
        _uiState.update { it.copy(generationVisible = visible) }
    }

    fun setEntryMode(mode: String) {
        val normalized = if (mode == "nai") "nai" else "ai"
        _uiState.update { it.copy(entryMode = normalized, errorMessage = "") }
    }

    fun selectMedia(messageId: String?, blockId: String?) {
        val key = if (messageId != null && blockId != null) "$messageId::$blockId" else null
        _uiState.update { it.copy(selectedMediaKey = key) }
    }

    fun editMessage(messageId: String, newText: String) {
        viewModelScope.launch {
            val current = rawMessages.find { it.id == messageId } ?: return@launch
            val doc = documentFromJson(current.contentJson)
            val media = doc.blocks.filter {
                it is MediaBlock || it is MediaStackBlock
            }
            val blocks = buildList {
                if (newText.isNotBlank()) {
                    add(Paragraph("p-${System.currentTimeMillis()}", listOf(Span(newText))))
                }
                addAll(media)
            }
            persistMessageBlocks(current, blocks)
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            val existing = rawMessages.find { it.id == messageId } ?: return@launch
            deleteStoredMessage(existing)
            _uiState.update {
                it.copy(
                    selectedMediaKey = if (it.selectedMediaKey?.startsWith("$messageId::") == true) {
                        null
                    } else {
                        it.selectedMediaKey
                    },
                )
            }
        }
    }

    fun removeMedia(messageId: String, blockId: String) {
        viewModelScope.launch {
            val current = rawMessages.find { it.id == messageId } ?: return@launch
            val doc = documentFromJson(current.contentJson)
            val target = doc.blocks.find { it.id == blockId }
            // Removing a DM text tile removes the whole prose turn.
            if (target is MediaBlock && target.mediaId == DM_TEXT_TILE_MEDIA_ID) {
                deleteStoredMessage(current)
                _uiState.update {
                    it.copy(
                        selectedMediaKey = if (it.selectedMediaKey?.startsWith("$messageId::") == true) {
                            null
                        } else {
                            it.selectedMediaKey
                        },
                    )
                }
                return@launch
            }
            val nextBlocks = doc.blocks.filterNot {
                (it is MediaBlock && it.id == blockId) || (it is MediaStackBlock && it.id == blockId)
            }
            val hasMedia = nextBlocks.any {
                (it is MediaBlock && it.mediaId != DM_TEXT_TILE_MEDIA_ID) || it is MediaStackBlock
            }
            if (!hasMedia && Document(nextBlocks).plainText().isBlank()) {
                deleteStoredMessage(current)
            } else {
                persistMessageBlocks(current, nextBlocks)
            }
            _uiState.update {
                it.copy(
                    selectedMediaKey = if (it.selectedMediaKey == "$messageId::$blockId") null else it.selectedMediaKey,
                )
            }
        }
    }

    /** Long-press menu: stack with adjacent media when present. */
    fun stackMedia(messageId: String, blockId: String) {
        viewModelScope.launch {
            val current = rawMessages.find { it.id == messageId } ?: return@launch
            val blocks = documentFromJson(current.contentJson).blocks
            val index = blocks.indexOfFirst {
                (it is MediaBlock && it.id == blockId) || (it is MediaStackBlock && it.id == blockId)
            }
            if (index < 0) return@launch
            val next = blocks.stackMediaWithAdjacent(index) ?: run {
                _uiState.update {
                    it.copy(errorMessage = "Drag this picture onto another to stack them.")
                }
                return@launch
            }
            persistMessageBlocks(current, next)
            _uiState.update { it.copy(errorMessage = "") }
        }
    }

    /** Drag-onto stack within the same message. */
    fun stackMediaOnto(messageId: String, fromBlockId: String, ontoBlockId: String) {
        if (fromBlockId == ontoBlockId) return
        viewModelScope.launch {
            val current = rawMessages.find { it.id == messageId } ?: return@launch
            val blocks = documentFromJson(current.contentJson).blocks
            val fromIndex = blocks.indexOfFirst {
                (it is MediaBlock && it.id == fromBlockId) || (it is MediaStackBlock && it.id == fromBlockId)
            }
            val ontoIndex = blocks.indexOfFirst {
                (it is MediaBlock && it.id == ontoBlockId) || (it is MediaStackBlock && it.id == ontoBlockId)
            }
            if (fromIndex < 0 || ontoIndex < 0) return@launch
            val next = blocks.stackMediaOnto(fromIndex, ontoIndex) ?: return@launch
            persistMessageBlocks(current, next)
            _uiState.update { it.copy(errorMessage = "", selectedMediaKey = null) }
        }
    }

    /** Persist snap position for a media/stack/text-tile block. */
    fun setMediaGridCell(messageId: String, blockId: String, col: Int, row: Int) {
        viewModelScope.launch {
            val current = rawMessages.find { it.id == messageId } ?: return@launch
            val gridSize = activeGridSize()
            val blocks = documentFromJson(current.contentJson).blocks.toMutableList()
            val index = blocks.indexOfFirst {
                (it is MediaBlock && it.id == blockId) || (it is MediaStackBlock && it.id == blockId)
            }
            if (index < 0) return@launch
            val block = blocks[index]
            blocks[index] = block.withGridPlacement(
                col,
                row,
                block.gridColSpanOrOne(gridSize),
                block.gridRowSpanOrOne(gridSize),
                gridSize,
            )
            persistMessageBlocks(current, blocks)
        }
    }

    fun setMediaGridSpan(messageId: String, blockId: String, colSpan: Int, rowSpan: Int) {
        viewModelScope.launch {
            val current = rawMessages.find { it.id == messageId } ?: return@launch
            val gridSize = activeGridSize()
            val blocks = documentFromJson(current.contentJson).blocks.toMutableList()
            val index = blocks.indexOfFirst {
                (it is MediaBlock && it.id == blockId) || (it is MediaStackBlock && it.id == blockId)
            }
            if (index < 0) return@launch
            val block = blocks[index]
            val col = block.gridColOrUnset().takeIf { it >= 0 } ?: 0
            val row = block.gridRowOrUnset().takeIf { it >= 0 } ?: 0
            blocks[index] = block.withGridPlacement(col, row, colSpan, rowSpan, gridSize)
            persistMessageBlocks(current, blocks)
        }
    }

    /** Auto-place unset panels into free 6×6 cells (row-major), span-aware. */
    fun ensureMangaGridPlacement() {
        viewModelScope.launch { placeUnplacedPanels(MediaGrid.SIZE) }
    }

    /** Auto-place unset panels into free 3×3 cells; ensure text-only messages get tiles. */
    fun ensureDmGridPlacement() {
        viewModelScope.launch {
            ensureTextTilesForDm()
            // Room flow will refresh panels; place whatever is currently known, then again after publish.
            placeUnplacedPanels(MediaGrid.DM_SIZE)
        }
    }

    private suspend fun ensureTextTilesForDm() {
        val active = rawMessages.filter { it.isActiveSwipe }
        for (msg in active) {
            val doc = documentFromJson(msg.contentJson)
            val hasRealMedia = doc.blocks.any {
                (it is MediaBlock && it.mediaId != DM_TEXT_TILE_MEDIA_ID) || it is MediaStackBlock
            }
            val hasTextTile = doc.blocks.any {
                it is MediaBlock && it.mediaId == DM_TEXT_TILE_MEDIA_ID
            }
            val text = doc.plainText()
            if (!hasRealMedia && text.isNotBlank() && !hasTextTile) {
                val tile = MediaBlock(
                    id = "dm-text-${msg.id}",
                    mediaId = DM_TEXT_TILE_MEDIA_ID,
                    kind = MediaKind.Image,
                )
                db.roleplayDao().upsertMessage(
                    msg.copy(contentJson = Document(blocks = doc.blocks + tile).toJson()),
                )
            }
        }
    }

    private suspend fun placeUnplacedPanels(gridSize: Int) {
        val panels = _uiState.value.mediaPanels
        if (panels.isEmpty()) return
        val occupied = mutableSetOf<Pair<Int, Int>>()
        panels.filter { MediaGrid.isPlaced(it.gridCol, it.gridRow, gridSize) }.forEach { panel ->
            occupied += MediaGrid.cellsCovered(
                panel.gridCol,
                panel.gridRow,
                panel.gridColSpan,
                panel.gridRowSpan,
                gridSize,
            )
        }
        // Prefer the page layout's empty slots, so dropped media lands in a panel
        // rather than a bare 1x1 cell somewhere in the corner.
        // Templates are authored against MediaGrid.SIZE, so they only apply to the
        // full-resolution storyboard canvas — never the coarser DM board.
        val slots = if (gridSize == MediaGrid.SIZE) {
            PanelTemplates.byId(_uiState.value.activeTemplateId)?.slots.orEmpty()
        } else {
            emptyList()
        }
        val updates = mutableListOf<Triple<String, String, Pair<Int, Int>>>()
        panels.forEach { panel ->
            if (!MediaGrid.isPlaced(panel.gridCol, panel.gridRow, gridSize)) {
                val freeSlot = slots.firstOrNull { slot ->
                    MediaGrid.cellsCovered(
                        slot.col, slot.row, slot.colSpan, slot.rowSpan, gridSize,
                    ).none { it in occupied }
                }
                val cell = if (freeSlot != null) {
                    occupied += MediaGrid.cellsCovered(
                        freeSlot.col, freeSlot.row, freeSlot.colSpan, freeSlot.rowSpan, gridSize,
                    )
                    freeSlot.col to freeSlot.row
                } else {
                    val next = MediaGrid.nextFreeCell(occupied, gridSize)
                    occupied += MediaGrid.cellsCovered(next.first, next.second, 1, 1, gridSize)
                    next
                }
                updates += Triple(panel.messageId, panel.blockId, cell)
            }
        }
        updates.forEach { (messageId, blockId, cell) ->
            val current = rawMessages.find { it.id == messageId } ?: return@forEach
            val blocks = documentFromJson(current.contentJson).blocks.toMutableList()
            val index = blocks.indexOfFirst {
                (it is MediaBlock && it.id == blockId) || (it is MediaStackBlock && it.id == blockId)
            }
            if (index < 0) return@forEach
            val slot = slots.firstOrNull { it.col == cell.first && it.row == cell.second }
            blocks[index] = if (slot != null) {
                blocks[index].withGridPlacement(
                    slot.col, slot.row, slot.colSpan, slot.rowSpan, gridSize,
                )
            } else {
                blocks[index].withGridCell(cell.first, cell.second, gridSize)
            }
            db.roleplayDao().upsertMessage(
                current.copy(contentJson = Document(blocks = blocks).toJson()),
            )
        }
    }

    fun cycleMediaStack(messageId: String, blockId: String) {
        viewModelScope.launch {
            val current = rawMessages.find { it.id == messageId } ?: return@launch
            val blocks = documentFromJson(current.contentJson).blocks.toMutableList()
            val index = blocks.indexOfFirst { it is MediaStackBlock && it.id == blockId }
            if (index < 0) return@launch
            val stack = blocks[index] as MediaStackBlock
            if (stack.mediaIds.isEmpty()) return@launch
            blocks[index] = stack.copy(currentIndex = (stack.currentIndex + 1) % stack.mediaIds.size)
            persistMessageBlocks(current, blocks)
        }
    }

    fun removeSelectedMedia() {
        val key = _uiState.value.selectedMediaKey ?: return
        val parts = key.split("::", limit = 2)
        if (parts.size != 2) return
        removeMedia(parts[0], parts[1])
    }

    /** Reorder media within a message by swapping with neighbor. */
    fun moveMedia(messageId: String, blockId: String, delta: Int) {
        if (delta == 0) return
        viewModelScope.launch {
            val current = rawMessages.find { it.id == messageId } ?: return@launch
            val blocks = documentFromJson(current.contentJson).blocks.toMutableList()
            val index = blocks.indexOfFirst {
                (it is MediaBlock && it.id == blockId) || (it is MediaStackBlock && it.id == blockId)
            }
            if (index < 0) return@launch
            val target = (index + delta).coerceIn(0, blocks.lastIndex)
            if (target == index) return@launch
            val item = blocks.removeAt(index)
            blocks.add(target, item)
            persistMessageBlocks(current, blocks)
        }
    }

    fun setDisplayMode(mode: String) {
        val chat = boundChat ?: return
        val next = when (mode) {
            "dungeonMaster", "roleplay", "messenger" -> mode
            else -> "messenger"
        }
        viewModelScope.launch {
            val updated = chat.copy(displayMode = next)
            db.roleplayDao().upsertChat(updated)
            boundChat = updated
            _uiState.update { it.copy(displayMode = next) }
        }
    }

    // --- Storyboard pages ---------------------------------------------------

    fun switchPage(pageId: String) {
        if (_uiState.value.activePageId == pageId) return
        val template = _uiState.value.pages.firstOrNull { it.id == pageId }?.templateId
            ?: "classic-6"
        _uiState.update {
            it.copy(activePageId = pageId, activeTemplateId = template, selectedMediaKey = null)
        }
        viewModelScope.launch { publishMessages() }
    }

    fun addPage() {
        val chat = boundChat ?: return
        viewModelScope.launch {
            val pages = _uiState.value.pages
            val next = RpPageMeta(
                id = "page-${UUID.randomUUID()}",
                order = (pages.maxOfOrNull { it.order } ?: -1) + 1,
                title = "Page ${pages.size + 1}",
            )
            val updated = chat.copy(pagesJson = encodePages(pages + next))
            db.roleplayDao().upsertChat(updated)
            boundChat = updated
            _uiState.update {
                it.copy(
                    pages = pages + next,
                    activePageId = next.id,
                    activeTemplateId = next.templateId,
                    selectedMediaKey = null,
                )
            }
            publishMessages()
        }
    }

    fun renamePage(pageId: String, title: String) {
        val chat = boundChat ?: return
        viewModelScope.launch {
            val pages = _uiState.value.pages.map {
                if (it.id == pageId) it.copy(title = title.ifBlank { null }) else it
            }
            val updated = chat.copy(pagesJson = encodePages(pages))
            db.roleplayDao().upsertChat(updated)
            boundChat = updated
            _uiState.update { it.copy(pages = pages) }
        }
    }

    /** Deleting a page leaves its media orphaned under the default page (never data loss). */
    fun deletePage(pageId: String) {
        val chat = boundChat ?: return
        val pages = _uiState.value.pages
        if (pages.size <= 1) return
        viewModelScope.launch {
            val remaining = pages.filterNot { it.id == pageId }
            val updated = chat.copy(pagesJson = encodePages(remaining))
            db.roleplayDao().upsertChat(updated)
            boundChat = updated
            val nextActive = if (_uiState.value.activePageId == pageId) {
                remaining.first().id
            } else {
                _uiState.value.activePageId
            }
            _uiState.update {
                it.copy(pages = remaining, activePageId = nextActive, selectedMediaKey = null)
            }
            publishMessages()
        }
    }

    /**
     * Snap this page's panels into a comic layout. Panels are filled in their
     * existing order, and any beyond the template's slot count keep their current
     * placement — applying a template never drops artwork off the page.
     */
    fun applyPanelTemplate(templateId: String) {
        val template = PanelTemplates.byId(templateId) ?: return
        val gridSize = activeGridSize()
        val pagePanels = _uiState.value.mediaPanels
        viewModelScope.launch {
            // Record the layout first so its frames show even on an empty page.
            val chat = boundChat
            val activeId = _uiState.value.activePageId
            if (chat != null && activeId.isNotBlank()) {
                val pages = _uiState.value.pages.map {
                    if (it.id == activeId) it.copy(templateId = templateId) else it
                }
                val updated = chat.copy(pagesJson = encodePages(pages))
                db.roleplayDao().upsertChat(updated)
                boundChat = updated
                _uiState.update { it.copy(pages = pages, activeTemplateId = templateId) }
            }
            pagePanels.forEachIndexed { index, panel ->
                val slot = template.slots.getOrNull(index) ?: return@forEachIndexed
                val current = rawMessages.find { it.id == panel.messageId } ?: return@forEachIndexed
                val blocks = documentFromJson(current.contentJson).blocks.toMutableList()
                val at = blocks.indexOfFirst { it.id == panel.blockId }
                if (at < 0) return@forEachIndexed
                val placed = blocks[at].withGridPlacement(
                    slot.col,
                    slot.row,
                    slot.colSpan,
                    slot.rowSpan,
                    gridSize,
                )
                blocks[at] = when (placed) {
                    is MediaBlock -> placed.copy(panelRotationDeg = slot.rotationDeg)
                    is MediaStackBlock -> placed.copy(panelRotationDeg = slot.rotationDeg)
                    else -> placed
                }
                persistMessageBlocks(current, blocks)
            }
            _uiState.update { it.copy(errorMessage = "", selectedMediaKey = null) }
        }
    }

    // --- Media transform (pan/zoom within a panel) --------------------------

    fun setMediaTransform(
        messageId: String,
        blockId: String,
        scale: Float,
        offsetXPercent: Float,
        offsetYPercent: Float,
    ) {
        viewModelScope.launch {
            val current = rawMessages.find { it.id == messageId } ?: return@launch
            val blocks = documentFromJson(current.contentJson).blocks.toMutableList()
            val index = blocks.indexOfFirst { it.id == blockId }
            if (index < 0) return@launch
            blocks[index] = when (val block = blocks[index]) {
                is MediaBlock -> block.copy(
                    mediaScale = scale,
                    mediaOffsetXPercent = offsetXPercent,
                    mediaOffsetYPercent = offsetYPercent,
                )
                is MediaStackBlock -> block.copy(
                    mediaScale = scale,
                    mediaOffsetXPercent = offsetXPercent,
                    mediaOffsetYPercent = offsetYPercent,
                )
                else -> return@launch
            }
            persistMessageBlocks(current, blocks)
        }
    }

    // --- Text overlays --------------------------------------------------------

    fun addTextOverlay(messageId: String, blockId: String) {
        viewModelScope.launch {
            val current = rawMessages.find { it.id == messageId } ?: return@launch
            val blocks = documentFromJson(current.contentJson).blocks.toMutableList()
            val index = blocks.indexOfFirst { it.id == blockId }
            if (index < 0) return@launch
            val overlay = TextOverlay(id = "ov-${UUID.randomUUID()}", text = "Text")
            blocks[index] = when (val block = blocks[index]) {
                is MediaBlock -> block.copy(overlays = block.overlays + overlay)
                is MediaStackBlock -> block.copy(overlays = block.overlays + overlay)
                else -> return@launch
            }
            persistMessageBlocks(current, blocks)
            _uiState.update {
                it.copy(editingOverlay = Triple(messageId, blockId, overlay.id))
            }
        }
    }

    fun openOverlayEditor(messageId: String, blockId: String, overlayId: String) {
        _uiState.update { it.copy(editingOverlay = Triple(messageId, blockId, overlayId)) }
    }

    fun closeOverlayEditor() {
        _uiState.update { it.copy(editingOverlay = null) }
    }

    fun moveTextOverlay(messageId: String, blockId: String, overlayId: String, xPercent: Float, yPercent: Float) {
        updateTextOverlay(messageId, blockId, overlayId) { it.copy(xPercent = xPercent, yPercent = yPercent) }
    }

    fun resizeTextOverlay(messageId: String, blockId: String, overlayId: String, widthPercent: Float) {
        updateTextOverlay(messageId, blockId, overlayId) { it.copy(widthPercent = widthPercent) }
    }

    fun saveTextOverlay(messageId: String, blockId: String, overlay: TextOverlay) {
        updateTextOverlay(messageId, blockId, overlay.id) { overlay }
    }

    fun deleteTextOverlay(messageId: String, blockId: String, overlayId: String) {
        viewModelScope.launch {
            val current = rawMessages.find { it.id == messageId } ?: return@launch
            val blocks = documentFromJson(current.contentJson).blocks.toMutableList()
            val index = blocks.indexOfFirst { it.id == blockId }
            if (index < 0) return@launch
            blocks[index] = when (val block = blocks[index]) {
                is MediaBlock -> block.copy(overlays = block.overlays.filterNot { it.id == overlayId })
                is MediaStackBlock -> block.copy(overlays = block.overlays.filterNot { it.id == overlayId })
                else -> return@launch
            }
            persistMessageBlocks(current, blocks)
        }
    }

    private fun updateTextOverlay(
        messageId: String,
        blockId: String,
        overlayId: String,
        transform: (TextOverlay) -> TextOverlay,
    ) {
        viewModelScope.launch {
            val current = rawMessages.find { it.id == messageId } ?: return@launch
            val blocks = documentFromJson(current.contentJson).blocks.toMutableList()
            val index = blocks.indexOfFirst { it.id == blockId }
            if (index < 0) return@launch
            fun applyTo(overlays: List<TextOverlay>) =
                overlays.map { if (it.id == overlayId) transform(it) else it }
            blocks[index] = when (val block = blocks[index]) {
                is MediaBlock -> block.copy(overlays = applyTo(block.overlays))
                is MediaStackBlock -> block.copy(overlays = applyTo(block.overlays))
                else -> return@launch
            }
            persistMessageBlocks(current, blocks)
        }
    }

    fun speakText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val status = runCatching { tts.speak(trimmed) }.getOrElse { it.message ?: "TTS failed" }
            _uiState.update { it.copy(ttsStatus = status) }
        }
    }

    override fun onCleared() {
        tts.stop()
        super.onCleared()
    }

    fun requestMediaPick() {
        _uiState.update { it.copy(mediaPickRequestId = it.mediaPickRequestId + 1) }
    }

    fun requestAudioPick() {
        _uiState.update { it.copy(audioPickRequestId = it.audioPickRequestId + 1) }
    }

    fun clearMediaPickRequest() {
        // no-op keep id; picker cancel does not need state clear
    }

    fun attachMedia(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                val mediaList = mediaRepository.importFromUris(uris)
                val caption = _uiState.value.input.ifBlank { "[media]" }
                val blocks = buildList {
                    add(
                        Paragraph(
                            "p-${System.currentTimeMillis()}",
                            listOf(Span(caption)),
                        ),
                    )
                    val pageId = _uiState.value.activePageId
                    mediaList.forEach { media ->
                        add(
                            MediaBlock(
                                id = UUID.randomUUID().toString(),
                                mediaId = media.id,
                                kind = MediaRepository.kindForType(media.type),
                                pageId = pageId,
                            ),
                        )
                    }
                }
                val doc = Document(blocks = blocks)
                val now = System.currentTimeMillis()
                val entity = RpMessageEntity(
                    id = "rpm-$now",
                    chatId = _uiState.value.chatId,
                    swipeGroupId = "sw-$now",
                    swipeIndex = 0,
                    isActiveSwipe = true,
                    role = "user",
                    contentJson = doc.toJson(),
                    createdAt = now,
                    displayMode = currentDisplayMode(),
                )
                insertStoredMessage(entity)
                _uiState.update { it.copy(input = "") }
            }
        }
    }

    fun expandComposer() {
        _uiState.update { it.copy(composerMinLines = (it.composerMinLines + 1).coerceAtMost(8)) }
    }

    fun send() {
        if (_uiState.value.entryMode == "nai") addManualEntry() else generate()
    }

    /** Non-AI (NAI): insert the typed text as a user message without calling a model. */
    fun addManualEntry() {
        insertUserText(_uiState.value.input)
    }

    /** Insert dictated / pasted plain text as a user message in the active display mode. */
    fun insertUserText(text: String) {
        val state = _uiState.value
        if (text.isBlank() || state.chatId.isBlank() || state.isStreaming) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val entity = RpMessageEntity(
                id = "rpm-$now",
                chatId = state.chatId,
                swipeGroupId = "sw-$now",
                swipeIndex = 0,
                isActiveSwipe = true,
                role = "user",
                contentJson = Document.fromPlainText(text.trim()).toJson(),
                createdAt = now,
                displayMode = currentDisplayMode(),
            )
            insertStoredMessage(entity)
            _uiState.update { it.copy(input = "", errorMessage = "") }
        }
    }

    fun generate() {
        val state = _uiState.value
        if (state.input.isBlank() || state.chatId.isBlank() || state.isStreaming) return
        if (state.entryMode == "nai") {
            addManualEntry()
            return
        }
        generateJob?.cancel()
        generateJob = viewModelScope.launch {
            if (!aiGeneration.hasApiKey()) {
                _uiState.update { it.copy(errorMessage = AIError.NoApiKey().message.orEmpty()) }
                return@launch
            }
            val now = System.currentTimeMillis()
            val groupId = "sw-$now"
            val userText = state.input
            val maxTokens = (state.outputWords * 1.5).toInt().coerceIn(64, 8192)
            val difficulty = defaultPresets.find { it.id == state.presetId }
            val temperature = difficulty?.temperature?.toDouble() ?: 0.8
            val mode = currentDisplayMode()
            val userMessage = RpMessageEntity(
                id = "rpm-$now",
                chatId = state.chatId,
                swipeGroupId = groupId,
                swipeIndex = 0,
                isActiveSwipe = true,
                role = "user",
                contentJson = Document.fromPlainText(userText).toJson(),
                createdAt = now,
                displayMode = mode,
            )
            db.roleplayDao().upsertMessage(userMessage)
            boundChat?.let { chat ->
                if (chat.presetId != state.presetId) {
                    val updated = chat.copy(presetId = state.presetId, updatedAt = now)
                    db.roleplayDao().upsertChat(updated)
                    boundChat = updated
                }
            }
            _uiState.update {
                it.copy(input = "", isStreaming = true, streamingText = "", errorMessage = "")
            }
            // History is already mode-filtered via observeMessages(chatId, displayMode).
            val history = rawMessages
                .filter { it.isActiveSwipe && it.displayMode == mode }
                .map { msg ->
                    val role = if (msg.role == "user") "user" else "assistant"
                    role to documentFromJson(msg.contentJson).plainText()
                }
            val builder = StringBuilder()
            var usageText = ""
            var promptTokens = 0
            var completionTokens = 0
            var costUsd = 0.0
            runCatching {
                aiGeneration.stream(
                    userMessage = userText,
                    assembled = generation.assemble(
                        character = boundCharacter,
                        persona = boundPersona,
                        history = history,
                        outputWords = state.outputWords,
                        difficultyDirective = difficulty?.directive,
                    ),
                    maxTokens = maxTokens,
                    temperature = temperature,
                ).collect { chunk ->
                    when (chunk) {
                        is AIChunk.Delta -> {
                            builder.append(chunk.text)
                            _uiState.update { it.copy(streamingText = builder.toString()) }
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
                workspaceHistory.record(
                    undo = { db.roleplayDao().deleteMessage(userMessage.id) },
                    redo = { db.roleplayDao().upsertMessage(userMessage) },
                )
                _uiState.update {
                    it.copy(isStreaming = false, streamingText = "", errorMessage = formatError(err))
                }
                return@launch
            }
            val reply = RpMessageEntity(
                id = "rpm-${now + 1}",
                chatId = state.chatId,
                swipeGroupId = groupId,
                swipeIndex = 0,
                isActiveSwipe = true,
                role = "char",
                contentJson = Document.fromPlainText(builder.toString()).toJson(),
                createdAt = now + 1,
                displayMode = mode,
                promptTokens = promptTokens,
                completionTokens = completionTokens,
                costUsd = costUsd,
            )
            db.roleplayDao().upsertMessage(reply)
            val added = listOf(userMessage, reply)
            workspaceHistory.record(
                undo = { added.forEach { db.roleplayDao().deleteMessage(it.id) } },
                redo = { added.forEach { db.roleplayDao().upsertMessage(it) } },
            )
            _uiState.update {
                it.copy(isStreaming = false, streamingText = "", lastUsage = usageText)
            }
        }
    }

    fun cancelGeneration() {
        generateJob?.cancel()
        generateJob = null
        _uiState.update { it.copy(isStreaming = false, streamingText = "", errorMessage = "Cancelled") }
    }

    fun swipe(messageId: String, direction: Int) {
        viewModelScope.launch {
            val current = rawMessages.find { it.id == messageId } ?: return@launch
            val siblings = rawMessages
                .filter { it.swipeGroupId == current.swipeGroupId && it.role == current.role }
                .sortedBy { it.swipeIndex }
            val idx = siblings.indexOfFirst { it.id == messageId }.coerceAtLeast(0)
            val nextIdx = (idx + direction).coerceIn(0, siblings.lastIndex)
            if (nextIdx == idx) return@launch
            val after = siblings.mapIndexed { i, msg -> msg.copy(isActiveSwipe = i == nextIdx) }
            after.forEach { db.roleplayDao().upsertMessage(it) }
            workspaceHistory.record(
                undo = { siblings.forEach { db.roleplayDao().upsertMessage(it) } },
                redo = { after.forEach { db.roleplayDao().upsertMessage(it) } },
            )
        }
    }

    fun regenerate(messageId: String) {
        viewModelScope.launch {
            val current = rawMessages.find { it.id == messageId && it.role == "char" } ?: return@launch
            if (!aiGeneration.hasApiKey()) {
                _uiState.update { it.copy(errorMessage = AIError.NoApiKey().message.orEmpty()) }
                return@launch
            }
            val siblings = rawMessages.filter { it.swipeGroupId == current.swipeGroupId && it.role == "char" }
            val words = _uiState.value.outputWords
            val difficulty = defaultPresets.find { it.id == _uiState.value.presetId }
            val temperature = difficulty?.temperature?.toDouble() ?: 0.8
            _uiState.update { it.copy(isStreaming = true, errorMessage = "") }
            val history = rawMessages
                .filter { it.isActiveSwipe && it.displayMode == current.displayMode && it.id != messageId }
                .map { msg ->
                    val role = if (msg.role == "user") "user" else "assistant"
                    role to documentFromJson(msg.contentJson).plainText()
                }
            runCatching {
                aiGeneration.complete(
                    userMessage = "Continue the roleplay from here. Write the character's next beat.",
                    assembled = generation.assemble(
                        character = boundCharacter,
                        persona = boundPersona,
                        history = history,
                        outputWords = words,
                        difficultyDirective = difficulty?.directive,
                    ),
                    maxTokens = (words * 1.5).toInt().coerceIn(64, 8192),
                    temperature = temperature,
                )
            }.onSuccess { reply ->
                val now = System.currentTimeMillis()
                val deactivated = siblings.map { it.copy(isActiveSwipe = false) }
                val generated = RpMessageEntity(
                    id = "rpm-$now",
                    chatId = current.chatId,
                    swipeGroupId = current.swipeGroupId,
                    swipeIndex = siblings.size,
                    isActiveSwipe = true,
                    role = "char",
                    contentJson = Document.fromPlainText(reply.text).toJson(),
                    createdAt = now,
                    displayMode = current.displayMode.ifBlank { currentDisplayMode() },
                )
                deactivated.forEach { db.roleplayDao().upsertMessage(it) }
                db.roleplayDao().upsertMessage(generated)
                workspaceHistory.record(
                    undo = {
                        db.roleplayDao().deleteMessage(generated.id)
                        siblings.forEach { db.roleplayDao().upsertMessage(it) }
                    },
                    redo = {
                        deactivated.forEach { db.roleplayDao().upsertMessage(it) }
                        db.roleplayDao().upsertMessage(generated)
                    },
                )
                _uiState.update {
                    it.copy(
                        isStreaming = false,
                        lastUsage = UsageFormat.formatUsage(
                            promptTokens = reply.promptTokens,
                            completionTokens = reply.completionTokens,
                            cost = reply.cost,
                        ),
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isStreaming = false, errorMessage = formatError(err))
                }
            }
        }
    }

    private fun formatError(err: Throwable): String = when (err) {
        is AIError.HttpFailure -> "HTTP ${err.statusCode}: ${err.message}"
        is AIError -> err.message.orEmpty()
        else -> err.message ?: err.toString()
    }

    private fun refreshContextMeter() {
        val state = _uiState.value
        val mode = currentDisplayMode()
        val history = rawMessages
            .filter { it.isActiveSwipe && it.displayMode == mode }
            .map { msg ->
                val role = if (msg.role == "user") "user" else "assistant"
                role to documentFromJson(msg.contentJson).plainText()
            }
        val difficulty = defaultPresets.find { it.id == state.presetId }
        val assembled = generation.assemble(
            character = boundCharacter,
            persona = boundPersona,
            history = history,
            outputWords = state.outputWords,
            difficultyDirective = difficulty?.directive,
        )
        val reading = generation.meter(assembled, state.input, contextLimit)
        _uiState.update { it.copy(contextMeter = reading) }
    }

    private suspend fun insertStoredMessage(entity: RpMessageEntity) {
        db.roleplayDao().upsertMessage(entity)
        workspaceHistory.record(
            undo = { db.roleplayDao().deleteMessage(entity.id) },
            redo = { db.roleplayDao().upsertMessage(entity) },
        )
    }

    private suspend fun deleteStoredMessage(entity: RpMessageEntity) {
        db.roleplayDao().deleteMessage(entity.id)
        workspaceHistory.record(
            undo = { db.roleplayDao().upsertMessage(entity) },
            redo = { db.roleplayDao().deleteMessage(entity.id) },
        )
    }

    private suspend fun replaceStoredMessage(before: RpMessageEntity, after: RpMessageEntity) {
        if (before == after) return
        db.roleplayDao().upsertMessage(after)
        workspaceHistory.record(
            undo = { db.roleplayDao().upsertMessage(before) },
            redo = { db.roleplayDao().upsertMessage(after) },
        )
    }

    private suspend fun persistMessageBlocks(
        current: RpMessageEntity,
        blocks: List<Block>,
    ) {
        replaceStoredMessage(current, current.copy(contentJson = Document(blocks = blocks).toJson()))
    }
}
