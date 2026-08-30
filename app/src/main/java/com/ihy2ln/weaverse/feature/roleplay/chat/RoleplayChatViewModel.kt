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
import com.ihy2ln.weaverse.core.media.TopicMediaLibrary
import com.ihy2ln.weaverse.core.media.TopicMediaSnapshot
import com.ihy2ln.weaverse.core.media.parseTopicMediaReply
import com.ihy2ln.weaverse.core.media.topicMediaRequestsFor
import com.ihy2ln.weaverse.core.media.topicMediaVisibleText
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
import com.ihy2ln.weaverse.data.db.entities.CodexCategoryEntity
import com.ihy2ln.weaverse.data.db.entities.CodexEntryEntity
import com.ihy2ln.weaverse.data.db.entities.RpPageMeta
import com.ihy2ln.weaverse.data.db.entities.RpPersonaEntity
import com.ihy2ln.weaverse.data.db.entities.decodePages
import com.ihy2ln.weaverse.data.db.entities.encodePages
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import com.ihy2ln.weaverse.feature.roleplay.presets.defaultPresets
import com.ihy2ln.weaverse.feature.roleplay.characters.abilityModifier
import com.ihy2ln.weaverse.feature.roleplay.characters.decodeRpgSheet
import com.ihy2ln.weaverse.feature.roleplay.characters.encodeRpgSheet
import com.ihy2ln.weaverse.feature.roleplay.characters.RpgCharacterSheet
import com.ihy2ln.weaverse.ai.prompt.PromptRenderContext
import com.ihy2ln.weaverse.ai.prompt.PromptRenderer
import com.ihy2ln.weaverse.feature.prompt.PromptModelSelection
import com.ihy2ln.weaverse.feature.prompt.PromptWordLimit
import com.ihy2ln.weaverse.feature.shell.WorkspaceHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
import java.util.UUID
import javax.inject.Inject

/** Sentinel mediaId for DM text-only tiles placed on the 3×3 grid. */
const val DM_TEXT_TILE_MEDIA_ID = "__dm_text__"
private const val ADVENTURE_SCENE_ROLE = "scene"
private val ExplicitSceneAdvance = Regex(
    "\\b(next scene|advance (?:the )?scene|move (?:on|forward)|leave this scene|go to the next)\\b",
    RegexOption.IGNORE_CASE,
)
private val ExplicitStayInScene = Regex(
    "\\b(stay (?:here|in (?:this|the) scene)|do not (?:advance|move on)|don't (?:advance|move on)|remain here|go back (?:a scene|to the previous scene))\\b",
    RegexOption.IGNORE_CASE,
)
private val AiSceneAdvanceMarker = Regex(
    "\\[\\[ADVANCE_SCENE(?::\\s*([^]]+))?]]",
    RegexOption.IGNORE_CASE,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RoleplayChatViewModel @Inject constructor(
    private val db: WeaverseDatabase,
    private val aiGeneration: AiGenerationService,
    private val mediaRepository: MediaRepository,
    private val topicMediaLibrary: TopicMediaLibrary,
    private val settings: SettingsRepository,
    private val tts: com.ihy2ln.weaverse.core.tts.TtsService,
    private val mediaClipboard: MediaClipboard,
    private val workspaceHistory: WorkspaceHistory,
    private val generation: RoleplayGeneration,
    private val modelCache: OpenRouterModelCache,
    private val adventureCapture: AdventureCapture,
    private val promptRepository: com.ihy2ln.weaverse.data.repo.PromptRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RoleplayChatUiState())
    val uiState: StateFlow<RoleplayChatUiState> = _uiState.asStateFlow()
    private var bindJob: Job? = null
    private var generateJob: Job? = null
    private var composerStatusJob: Job? = null
    /** Live instructions from the Prompt Collection's RPG → Adventure Scene Beat prompt. */
    private val rpgSceneBeatDirective = MutableStateFlow("")
    private var rawMessages: List<RpMessageEntity> = emptyList()
    private var boundChat: RpChatEntity? = null
    private var boundCharacter: RpCharacterEntity? = null
    private var boundPersona: RpPersonaEntity? = null
    private var contextLimit = ContextMeter.DEFAULT_LIMIT
    /** Null follows the newest scene; a number browses saved scene history. */
    private var viewedSceneNumber: Int? = null

    fun bindChat(chatId: String) {
        if (_uiState.value.chatId == chatId && bindJob?.isActive == true) return
        viewedSceneNumber = null
        _uiState.update { it.copy(chatId = chatId) }
        bindJob?.cancel()
        bindJob = viewModelScope.launch {
            launch {
                // The RPG → Adventure Scene Beat prompt steers every DM generation live.
                promptRepository.observeByType("rpg_scene_beat").collect { prompts ->
                    val prompt = prompts.firstOrNull { it.isDefault } ?: prompts.firstOrNull()
                    val text = prompt?.let {
                        PromptRenderer.render(it, PromptRenderContext()).systemText
                            .ifBlank { it.description }
                    }.orEmpty()
                    rpgSceneBeatDirective.value = text
                }
            }
            launch {
                combine(settings.preferences, modelCache.models) { prefs, dtos ->
                    prefs.defaultModelRef to modelCache.toModelInfo(dtos)
                }.collect { (defaultModelRef, models) ->
                    _uiState.update {
                        it.copy(defaultModelRef = defaultModelRef, writingModels = models)
                    }
                    contextLimit = ContextMeter.limitFor(
                        PromptModelSelection.effectiveModelRef(
                            _uiState.value.selectedModelRef,
                            defaultModelRef,
                        ),
                        models,
                    )
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
                        val selectedRosterId = Regex("Main character IDs:\\s*([^\\n]+)", RegexOption.IGNORE_CASE)
                            .find(chat.authorsNote)?.groupValues?.getOrNull(1)
                            ?.split(',')?.map { it.trim() }
                            ?.firstOrNull { it.startsWith("roster:") }
                            ?.substringAfter("roster:")
                        val character = (chat.characterId ?: selectedRosterId)?.let { id ->
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
                                userIsDungeonMaster = userIsDungeonMaster(chat.authorsNote),
                            )
                        }
                        migrateLegacyAdventureOpeningIfNeeded(chat)
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
        val allActive = rawMessages.filter { it.isActiveSwipe }
        val startupPhase = allActive.asSequence()
            .sortedByDescending { it.createdAt }
            .map { adventureStartupPhase(documentFromJson(it.contentJson).plainText()) }
            .firstOrNull { it != AdventureStartupPhase.None }
            ?: AdventureStartupPhase.None
        val sceneMarkers = allActive
            .filter { it.role == ADVENTURE_SCENE_ROLE }
            .sortedBy { it.createdAt }
        val totalScenes = sceneMarkers.size + 1
        val targetScene = (viewedSceneNumber ?: totalScenes).coerceIn(1, totalScenes)
        if (viewedSceneNumber != null) viewedSceneNumber = targetScene
        val startsAfter = sceneMarkers.getOrNull(targetScene - 2)?.createdAt
        val endsAt = sceneMarkers.getOrNull(targetScene - 1)?.createdAt
        val active = allActive.filter { message ->
            message.role != ADVENTURE_SCENE_ROLE &&
                (startsAfter == null || message.createdAt > startsAfter) &&
                (endsAt == null || message.createdAt < endsAt)
        }
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
            val storedCaption = doc.plainText()
            val isAdventureSetup = adventureStartupPhase(storedCaption) != AdventureStartupPhase.None
            val rollResult = adventureRollFrom(storedCaption)
            val actionResult = rollResult?.outcome?.takeIf { it.isNotBlank() }
                ?: adventureOutcomeFrom(storedCaption)
            val caption = adventureStartupProseFrom(adventureProseFrom(storedCaption))
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
                actionResult = actionResult,
                rollResult = rollResult,
                isAdventureSetup = isAdventureSetup,
            )
        }
        _uiState.update {
            it.copy(
                messages = ui,
                mediaPanels = panels,
                canPasteMedia = mediaClipboard.hasPayload,
                adventureStartupPhase = startupPhase,
                sceneNumber = targetScene,
                totalScenes = totalScenes,
                canGoToPreviousScene = targetScene > 1,
                viewingCurrentScene = targetScene == totalScenes,
                canUndoSceneAdvance = targetScene == totalScenes && sceneMarkers.isNotEmpty(),
            )
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
        refreshContextMeter()
    }

    fun updateMinimumOutputWords(words: Int) {
        _uiState.update { it.copy(minimumOutputWords = words.coerceIn(50, 4000)) }
        refreshContextMeter()
    }

    fun selectModel(modelId: String) {
        val ref = PromptModelSelection.modelRef(modelId)
        _uiState.update { it.copy(selectedModelRef = ref) }
        contextLimit = ContextMeter.limitFor(ref, _uiState.value.writingModels)
        refreshContextMeter()
    }

    fun useDefaultModel() {
        _uiState.update { it.copy(selectedModelRef = "") }
        contextLimit = ContextMeter.limitFor(
            _uiState.value.defaultModelRef,
            _uiState.value.writingModels,
        )
        refreshContextMeter()
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
        val startupPending = _uiState.value.adventureStartupPhase in setOf(
            AdventureStartupPhase.Character,
            AdventureStartupPhase.Choose,
            AdventureStartupPhase.Questions,
        )
        if (_uiState.value.entryMode == "nai" && !startupPending) addManualEntry() else generate()
    }

    /** Explicit tabletop override: submit the typed action and require a resolved check. */
    fun rollAction() {
        val state = _uiState.value
        if (state.input.isBlank() || state.isStreaming) return
        generate(forceAdventureRoll = true)
    }

    /** ✓ hold-menu ↻: reroll the latest AI reply without retyping anything. */
    fun regenerateLatestReply() {
        if (_uiState.value.isStreaming) return
        val latest = rawMessages.lastOrNull { it.role == "char" && it.isActiveSwipe } ?: return
        regenerate(latest.id)
    }

    /** ✓ hold-menu »: keep the adventure going from where it left off. */
    fun continueAdventure() {
        if (_uiState.value.isStreaming) return
        onInputChange("Continue the scene.")
        generate()
    }

    /** ✓ hold-menu 👤: read the recent scene, then confirm what joins the roster. */
    fun addRosterCharacter() {
        startCapture("roster")
    }

    /** ✓ hold-menu 🛍: read the recent scene, then confirm what files into inventories. */
    fun addInventoryItem() {
        startCapture("inventory")
    }

    /** Long-press on a message: scan that highlighted text instead of the whole scene. */
    fun captureFromText(text: String, kind: String) {
        viewModelScope.launch { openCapture(kind, text) }
    }

    private fun startCapture(kind: String) {
        viewModelScope.launch {
            openCapture(kind, recentSceneText())
        }
    }

    private suspend fun openCapture(kind: String, source: String) {
        val chatId = _uiState.value.chatId.ifBlank { boundChat?.id.orEmpty() }
        if (source.isBlank()) {
            setComposerStatus("Nothing to read yet — play a scene first.")
            return
        }
        val extraction = runCatching { adventureCapture.extract(source) }.getOrNull()
        if (kind == "roster") {
            val chars = extraction?.characters.orEmpty().filter { it.name.isNotBlank() }
            if (chars.isEmpty()) {
                adventureCapture.addBlankCharacter()
                setComposerStatus("No characters detected — added a blank roster character to edit.")
                return
            }
            _uiState.update {
                it.copy(
                    captureDialog = CaptureDialogState(
                        kind = kind,
                        sourceText = source,
                        extraction = extraction!!,
                        candidates = chars.map { char ->
                            CaptureCandidate(
                                name = char.name,
                                summary = listOf(
                                    char.characterClass,
                                    char.species,
                                    if (char.level > 0) "Lv ${char.level}" else "",
                                    if (char.maxHp > 0) "HP ${char.currentHp}/${char.maxHp}" else "",
                                    if (char.inParty) "party" else "",
                                ).filter { it.isNotBlank() }.joinToString(" · ")
                                    .ifBlank { char.notes },
                            )
                        },
                    ),
                )
            }
        } else {
            val items = extraction?.items.orEmpty().filter { it.name.isNotBlank() }
            if (items.isEmpty()) {
                val carrier = adventureCapture.addBlankItem(chatId)
                setComposerStatus(
                    if (carrier == null) {
                        "No inventory carrier found — add or mark a character first."
                    } else {
                        "No items detected — added a blank item to $carrier's inventory."
                    },
                )
                return
            }
            _uiState.update {
                it.copy(
                    captureDialog = CaptureDialogState(
                        kind = kind,
                        sourceText = source,
                        extraction = extraction!!,
                        candidates = items.map { item ->
                            CaptureCandidate(
                                name = item.name,
                                summary = listOf(
                                    "×${item.quantity.coerceAtLeast(1)}",
                                    item.carrier.ifBlank { "party" },
                                    item.notes,
                                ).filter { it.isNotBlank() }.joinToString(" · "),
                            )
                        },
                    ),
                )
            }
        }
    }

    fun toggleCaptureCandidate(name: String) {
        _uiState.update { state ->
            val dialog = state.captureDialog ?: return@update state
            state.copy(
                captureDialog = dialog.copy(
                    candidates = dialog.candidates.map {
                        if (it.name == name) it.copy(selected = !it.selected) else it
                    },
                ),
            )
        }
    }

    /** Applies only the checked candidates. */
    fun confirmCapture() {
        val dialog = _uiState.value.captureDialog ?: return
        val chatId = _uiState.value.chatId.ifBlank { boundChat?.id.orEmpty() }
        viewModelScope.launch {
            val selected = dialog.candidates.filter { it.selected }.map { it.name }.toSet()
            val message = when (dialog.kind) {
                "roster" -> {
                    val chosen = dialog.extraction.characters.filter { it.name in selected }
                    if (chosen.isEmpty()) {
                        "Nothing selected."
                    } else {
                        "Added to roster: ${adventureCapture.applyCharacters(chosen).joinToString()}"
                    }
                }
                else -> {
                    val chosen = dialog.extraction.items.filter { it.name in selected }
                    if (chosen.isEmpty()) {
                        "Nothing selected."
                    } else {
                        "Items filed: ${adventureCapture.applyItems(chosen, chatId)}"
                    }
                }
            }
            _uiState.update { it.copy(captureDialog = null, composerStatus = message) }
            startComposerStatusTimer()
        }
    }

    fun dismissCapture() {
        _uiState.update { it.copy(captureDialog = null) }
    }

    private fun setComposerStatus(message: String) {
        _uiState.update { it.copy(composerStatus = message) }
        startComposerStatusTimer()
    }

    private fun recentSceneText(): String = rawMessages
        .filter { it.isActiveSwipe && it.role != ADVENTURE_SCENE_ROLE }
        .takeLast(6)
        .joinToString("\n") { documentFromJson(it.contentJson).plainText() }

    private fun startComposerStatusTimer() {
        composerStatusJob?.cancel()
        composerStatusJob = viewModelScope.launch {
            delay(5000)
            _uiState.update { it.copy(composerStatus = "") }
        }
    }

    /** Non-AI (NAI): insert the typed text as a user message without calling a model. */
    fun addManualEntry() {
        viewedSceneNumber = null
        insertUserText(_uiState.value.input)
    }

    /** Insert dictated / pasted plain text as a user message in the active display mode. */
    fun insertUserText(text: String) {
        val state = _uiState.value
        if (text.isBlank() || state.chatId.isBlank() || state.isStreaming) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (currentDisplayMode() == "dungeonMaster" && ExplicitStayInScene.containsMatchIn(text)) {
                removeLatestSceneMarker()
            }
            if (currentDisplayMode() == "dungeonMaster" && ExplicitSceneAdvance.containsMatchIn(text)) {
                upsertCurrentSceneLore(reason = "The player explicitly advanced the scene.")
                insertSceneMarker(state.chatId, "The player explicitly advanced the scene.", now - 1)
            }
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

    fun generate(forceAdventureRoll: Boolean = false) {
        val state = _uiState.value
        if (state.input.isBlank() || state.chatId.isBlank() || state.isStreaming) return
        val startupPending = state.adventureStartupPhase in setOf(
            AdventureStartupPhase.Character,
            AdventureStartupPhase.Choose,
            AdventureStartupPhase.Questions,
        )
        if (state.entryMode == "nai" && !startupPending && !forceAdventureRoll) {
            addManualEntry()
            return
        }
        generateJob?.cancel()
        viewedSceneNumber = null
        generateJob = viewModelScope.launch {
            val activeModelRef = PromptModelSelection.effectiveModelRef(
                state.selectedModelRef,
                state.defaultModelRef,
            )
            if (!aiGeneration.hasApiKey(activeModelRef)) {
                _uiState.update { it.copy(errorMessage = AIError.NoApiKey().message.orEmpty()) }
                return@launch
            }
            val now = System.currentTimeMillis()
            val groupId = "sw-$now"
            val userText = state.input
            val mode = currentDisplayMode()
            val topicMedia = currentTopicMediaSnapshot()
            val startupPhase = currentAdventureStartupPhase()
            val startupActive = mode == "dungeonMaster" &&
                startupPhase in setOf(
                    AdventureStartupPhase.Character,
                    AdventureStartupPhase.Choose,
                    AdventureStartupPhase.Questions,
                )
            val startupDirective = if (startupActive) {
                adventureStartupDirective(startupPhase, userText)
            } else {
                ""
            }
            val nextStartupPhase = if (startupActive) {
                nextAdventureStartupPhase(startupPhase, userText)
            } else {
                AdventureStartupPhase.None
            }
            val playerAdvancedScene = mode == "dungeonMaster" && ExplicitSceneAdvance.containsMatchIn(userText)
            val playerStayedInScene = mode == "dungeonMaster" && ExplicitStayInScene.containsMatchIn(userText)
            val difficulty = defaultPresets.find { it.id == state.presetId }
            val checkDecision = if (startupActive) {
                AdventureCheckDecision(false, "Adventure setup")
            } else if (mode == "dungeonMaster" && forceAdventureRoll) {
                AdventureCheckDecision(true, "Player-forced action roll")
            } else {
                decideAdventureCheck(userText, state.userIsDungeonMaster)
            }
            val backgroundRoll = checkDecision.takeIf { it.requiresRoll }?.let { decision ->
                simulateAdventureRoll(
                    campaignRules = boundChat?.authorsNote.orEmpty(),
                    modifier = adventureSheetModifier(decision),
                    checkLabel = decision.checkLabel,
                    targetDc = difficulty?.targetDc ?: 12,
                )
            }
            if (mode == "dungeonMaster" && backgroundRoll != null) {
                _uiState.update {
                    it.copy(
                        input = "",
                        isStreaming = true,
                        streamingText = "",
                        errorMessage = "",
                        activeRoll = backgroundRoll,
                        rollAnimationId = System.nanoTime(),
                    )
                }
                // Let the player see the physical roll before the DM begins narrating its consequence.
                delay(850)
            } else if (backgroundRoll == null) {
                _uiState.update { it.copy(activeRoll = null) }
            }
            // Reasoning-capable models and private RPG markers share the completion budget.
            // Reserve headroom, then enforce the user's visible word cap before storage.
            val maxTokens = (state.outputWords * 1.7 + 192).toInt().coerceIn(192, 8192)
            val temperature = difficulty?.temperature?.toDouble() ?: 0.8
            val userMessage = RpMessageEntity(
                id = "rpm-$now",
                chatId = state.chatId,
                swipeGroupId = groupId,
                swipeIndex = 0,
                isActiveSwipe = true,
                role = "user",
                contentJson = Document.fromPlainText(
                    if (startupActive) {
                        withAdventureStartupMarker(userText, startupPhase)
                    } else {
                        userText
                    },
                ).toJson(),
                createdAt = now,
                displayMode = mode,
            )
            if (playerStayedInScene) {
                removeLatestSceneMarker()
            } else if (playerAdvancedScene) {
                upsertCurrentSceneLore(reason = "The player explicitly advanced the scene.")
                insertSceneMarker(state.chatId, "The player explicitly advanced the scene.", now - 1)
            }
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
                .filter { it.isActiveSwipe && it.displayMode == mode && it.role != ADVENTURE_SCENE_ROLE }
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
                        extraSystem = sessionSystemBlocks(mode) +
                            listOfNotNull(topicMedia.promptDirective()) +
                            PromptWordLimit.instruction(state.minimumOutputWords, state.outputWords) +
                            listOfNotNull(startupDirective.takeIf { it.isNotBlank() }) +
                            if (mode == "dungeonMaster") {
                            listOf(
                                backgroundRoll?.asHiddenDmInstruction(
                                    difficultyName = difficulty?.name ?: "Medium",
                                    targetDc = difficulty?.targetDc ?: 12,
                                )
                                    ?: noAdventureRollInstruction(),
                            )
                        } else {
                            emptyList()
                        },
                    ),
                    modelRef = activeModelRef,
                    maxTokens = maxTokens,
                    temperature = temperature,
                ).collect { chunk ->
                    when (chunk) {
                        is AIChunk.Delta -> {
                            builder.append(chunk.text)
                            _uiState.update {
                                it.copy(
                                    streamingText = adventureStartupProseFrom(adventureWorldProseFrom(
                                        adventureProseFrom(
                                            AiSceneAdvanceMarker.replace(
                                                topicMediaVisibleText(builder.toString()),
                                                "",
                                            ).trimStart(),
                                        ),
                                    )),
                                )
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
                workspaceHistory.record(
                    undo = { db.roleplayDao().deleteMessage(userMessage.id) },
                    redo = { db.roleplayDao().upsertMessage(userMessage) },
                )
                _uiState.update {
                    it.copy(isStreaming = false, streamingText = "", errorMessage = formatError(err))
                }
                return@launch
            }
            val rawReply = builder.toString()
            val topicMediaReply = parseTopicMediaReply(rawReply)
            val worldUpdates = adventureWorldUpdatesFrom(topicMediaReply.visibleText)
            val visibleReply = adventureStartupProseFrom(
                adventureProseFrom(AiSceneAdvanceMarker.replace(worldUpdates.prose, "").trim()),
            )
            if (visibleReply.isBlank()) {
                db.roleplayDao().deleteMessage(userMessage.id)
                _uiState.update {
                    it.copy(
                        input = userText,
                        isStreaming = false,
                        streamingText = "",
                        errorMessage = "The selected model returned no visible DM response. Your action was restored; tap ✓ to retry or choose another model.",
                    )
                }
                return@launch
            }
            persistAdventureWorldUpdates(worldUpdates)
            // Auto-bookkeeping: pull character/item facts into roster + inventory.
            if (mode == "dungeonMaster") {
                val captureChatId = state.chatId
                viewModelScope.launch { adventureCapture.captureAndApply(rawReply, captureChatId) }
            }
            val aiAdvanceMatch = AiSceneAdvanceMarker.find(worldUpdates.prose)
            val aiAdvancedScene = mode == "dungeonMaster" && aiAdvanceMatch != null &&
                !playerAdvancedScene && !ExplicitStayInScene.containsMatchIn(userText)
            if (aiAdvancedScene) {
                upsertCurrentSceneLore(
                    reason = aiAdvanceMatch?.groupValues?.getOrNull(1).orEmpty(),
                )
                insertSceneMarker(
                    state.chatId,
                    aiAdvanceMatch?.groupValues?.getOrNull(1)?.ifBlank { "The game master advanced the scene." }
                        ?: "The game master advanced the scene.",
                    now + 1,
                )
            }
            val cleanedReply = PromptWordLimit.trim(
                AiSceneAdvanceMarker.replace(worldUpdates.prose, "").trim(),
                state.outputWords,
            )
            val replyWithRoll = backgroundRoll?.let { withAdventureRollMarker(cleanedReply, it) }
                ?: cleanedReply
            val storedReply = if (startupActive) {
                withAdventureStartupMarker(replyWithRoll, nextStartupPhase)
            } else {
                replyWithRoll
            }
            val mediaRequests = topicMediaRequestsFor(
                topicMediaReply.copy(visibleText = visibleReply),
                topicMedia.topics,
            )
            val reply = RpMessageEntity(
                id = "rpm-${now + 1}",
                chatId = state.chatId,
                swipeGroupId = groupId,
                swipeIndex = 0,
                isActiveSwipe = true,
                role = "char",
                contentJson = documentWithTopicMedia(storedReply, topicMedia, mediaRequests).toJson(),
                createdAt = now + if (aiAdvancedScene) 2 else 1,
                displayMode = mode,
                promptTokens = promptTokens,
                completionTokens = completionTokens,
                costUsd = costUsd,
            )
            db.roleplayDao().upsertMessage(reply)
            if (mode == "dungeonMaster") {
                upsertCurrentSceneLore(
                    summaryOverride = worldUpdates.sceneSynopsis,
                    extraMessages = listOf(userMessage, reply),
                )
            }
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
            val state = _uiState.value
            val activeModelRef = PromptModelSelection.effectiveModelRef(
                state.selectedModelRef,
                state.defaultModelRef,
            )
            if (!aiGeneration.hasApiKey(activeModelRef)) {
                _uiState.update { it.copy(errorMessage = AIError.NoApiKey().message.orEmpty()) }
                return@launch
            }
            val siblings = rawMessages.filter { it.swipeGroupId == current.swipeGroupId && it.role == "char" }
            val words = state.outputWords
            val difficulty = defaultPresets.find { it.id == _uiState.value.presetId }
            val temperature = difficulty?.temperature?.toDouble() ?: 0.8
            val topicMedia = currentTopicMediaSnapshot()
            _uiState.update { it.copy(isStreaming = true, errorMessage = "") }
            val history = rawMessages
                .filter {
                    it.isActiveSwipe && it.displayMode == current.displayMode &&
                        it.id != messageId && it.role != ADVENTURE_SCENE_ROLE
                }
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
                        extraSystem = sessionSystemBlocks(current.displayMode) +
                            listOfNotNull(topicMedia.promptDirective()) +
                            PromptWordLimit.instruction(state.minimumOutputWords, words),
                    ),
                    modelRef = activeModelRef,
                    maxTokens = (words * 1.7 + 192).toInt().coerceIn(192, 8192),
                    temperature = temperature,
                )
            }.onSuccess { reply ->
                val now = System.currentTimeMillis()
                val topicMediaReply = parseTopicMediaReply(reply.text)
                val trimmedReply = PromptWordLimit.trim(topicMediaReply.visibleText, words)
                val mediaRequests = topicMediaRequestsFor(
                    topicMediaReply.copy(visibleText = trimmedReply),
                    topicMedia.topics,
                )
                val deactivated = siblings.map { it.copy(isActiveSwipe = false) }
                val generated = RpMessageEntity(
                    id = "rpm-$now",
                    chatId = current.chatId,
                    swipeGroupId = current.swipeGroupId,
                    swipeIndex = siblings.size,
                    isActiveSwipe = true,
                    role = "char",
                    contentJson = documentWithTopicMedia(trimmedReply, topicMedia, mediaRequests).toJson(),
                    createdAt = now,
                    displayMode = current.displayMode.ifBlank { currentDisplayMode() },
                )
                deactivated.forEach { db.roleplayDao().upsertMessage(it) }
                db.roleplayDao().upsertMessage(generated)
                if (current.displayMode.ifBlank { currentDisplayMode() } == "dungeonMaster") {
                    val captureChatId = current.chatId
                    viewModelScope.launch {
                        adventureCapture.captureAndApply(reply.text, captureChatId)
                    }
                }
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
            .filter { it.isActiveSwipe && it.displayMode == mode && it.role != ADVENTURE_SCENE_ROLE }
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
            extraSystem = sessionSystemBlocks(mode) +
                PromptWordLimit.instruction(state.minimumOutputWords, state.outputWords),
        )
        val reading = generation.meter(assembled, state.input, contextLimit)
        _uiState.update { it.copy(contextMeter = reading) }
    }

    private fun adventureSheetModifier(decision: AdventureCheckDecision): Int {
        val character = boundCharacter ?: return 0
        val sheet = decodeRpgSheet(character.extensionsJson)
        val score = when (decision.ability) {
            AdventureAbility.Strength -> sheet.strength
            AdventureAbility.Dexterity -> sheet.dexterity
            AdventureAbility.Constitution -> sheet.constitution
            AdventureAbility.Intelligence -> sheet.intelligence
            AdventureAbility.Wisdom -> sheet.wisdom
            AdventureAbility.Charisma -> sheet.charisma
            null -> 10
        }
        return abilityModifier(score) + if (decision.addProficiency) sheet.proficiencyBonus else 0
    }

    private fun currentAdventureStartupPhase(): AdventureStartupPhase = rawMessages
        .asSequence()
        .filter { it.isActiveSwipe && it.displayMode == "dungeonMaster" }
        .sortedByDescending { it.createdAt }
        .map { adventureStartupPhase(documentFromJson(it.contentJson).plainText()) }
        .firstOrNull { it != AdventureStartupPhase.None }
        ?: AdventureStartupPhase.None

    private suspend fun migrateLegacyAdventureOpeningIfNeeded(chat: RpChatEntity) {
        if (chat.displayMode != "dungeonMaster") return
        val messages = db.roleplayDao().getMessagesForMode(chat.id, "dungeonMaster")
            .filter { it.isActiveSwipe && it.role != ADVENTURE_SCENE_ROLE }
        if (messages.size != 1) return
        val opening = messages.single()
        val text = documentFromJson(opening.contentJson).plainText()
        if (!isLegacyPassiveAdventureOpening(text)) return
        db.roleplayDao().upsertMessage(
            opening.copy(
                contentJson = Document.fromPlainText(
                    adventureStartupPrompt(
                        userIsDungeonMaster = userIsDungeonMaster(chat.authorsNote),
                        needsCharacter = chat.authorsNote.contains(
                            "Main character IDs: none",
                            ignoreCase = true,
                        ),
                    ),
                ).toJson(),
            ),
        )
    }

    /** Player-owned scene controls always override the AI game master's pacing. */
    fun advanceScene(reason: String = "The player moved the adventure to the next scene.") {
        val state = _uiState.value
        if (state.chatId.isBlank() || currentDisplayMode() != "dungeonMaster") return
        viewModelScope.launch {
            if (!state.viewingCurrentScene) {
                viewedSceneNumber = (state.sceneNumber + 1).coerceAtMost(state.totalScenes)
                publishMessages()
            } else {
                viewedSceneNumber = null
                upsertCurrentSceneLore(reason = reason)
                insertSceneMarker(state.chatId, reason, System.currentTimeMillis())
            }
        }
    }

    fun previousScene() {
        val state = _uiState.value
        if (state.sceneNumber <= 1 || currentDisplayMode() != "dungeonMaster") return
        viewModelScope.launch {
            viewedSceneNumber = state.sceneNumber - 1
            publishMessages()
        }
    }

    fun undoLastSceneAdvance() {
        viewedSceneNumber = null
        viewModelScope.launch { removeLatestSceneMarker() }
    }

    private suspend fun removeLatestSceneMarker() {
        val marker = rawMessages
            .filter { it.role == ADVENTURE_SCENE_ROLE && it.isActiveSwipe }
            .maxByOrNull { it.createdAt }
            ?: return
        db.roleplayDao().deleteMessage(marker.id)
        workspaceHistory.record(
            undo = { db.roleplayDao().upsertMessage(marker) },
            redo = { db.roleplayDao().deleteMessage(marker.id) },
        )
    }

    private suspend fun insertSceneMarker(chatId: String, reason: String, createdAt: Long): RpMessageEntity {
        val marker = RpMessageEntity(
            id = "rpscene-${UUID.randomUUID()}",
            chatId = chatId,
            swipeGroupId = "rpscene-${UUID.randomUUID()}",
            swipeIndex = 0,
            isActiveSwipe = true,
            role = ADVENTURE_SCENE_ROLE,
            contentJson = Document.fromPlainText(reason.ifBlank { "The scene changed." }).toJson(),
            createdAt = createdAt,
            displayMode = "dungeonMaster",
        )
        db.roleplayDao().upsertMessage(marker)
        workspaceHistory.record(
            undo = { db.roleplayDao().deleteMessage(marker.id) },
            redo = { db.roleplayDao().upsertMessage(marker) },
        )
        return marker
    }

    private suspend fun persistAdventureWorldUpdates(updates: AdventureWorldUpdates) {
        val chat = boundChat ?: return
        val scopeId = chat.bookId ?: return
        val now = System.currentTimeMillis()
        updates.characters.forEach { update ->
            val existing = db.roleplayDao().getCharacters()
                .firstOrNull { it.name.equals(update.name, ignoreCase = true) }
            val categoryName = when (update.role.lowercase()) {
                "team", "party", "player" -> "Team"
                "enemy", "enemies", "hostile" -> "Enemies"
                "npc", "npcs" -> "NPCs"
                else -> "Other"
            }
            val codexEntry = upsertAdventureCodexEntry(
                scopeId = scopeId,
                category = "Characters",
                name = update.name,
                summary = buildString {
                    if (update.description.isNotBlank()) appendLine(update.description)
                    append("${update.species.ifBlank { "Unknown species" }} · ${update.characterClass} level ${update.level}")
                    if (update.portraitBrief.isNotBlank()) append("\nPortrait brief: ${update.portraitBrief}")
                },
                now = now,
            )
            val generatedSheet = RpgCharacterSheet(
                species = update.species,
                characterClass = update.characterClass,
                level = update.level,
                background = update.background,
                strength = update.strength,
                dexterity = update.dexterity,
                constitution = update.constitution,
                intelligence = update.intelligence,
                wisdom = update.wisdom,
                charisma = update.charisma,
            )
            val sheet = existing?.let { decodeRpgSheet(it.extensionsJson) } ?: generatedSheet
            val character = (existing ?: RpCharacterEntity(
                id = "rpc-${UUID.randomUUID()}",
                name = update.name,
                createdAt = now,
            )).copy(
                name = update.name,
                description = existing?.description?.takeIf { it.isNotBlank() } ?: update.description,
                creatorNotes = listOfNotNull(
                    existing?.creatorNotes?.takeIf { it.isNotBlank() },
                    update.portraitBrief.takeIf { it.isNotBlank() }?.let { "Portrait brief: $it" },
                ).distinct().joinToString("\n"),
                tagsJson = "[\"$categoryName\"]",
                extensionsJson = encodeRpgSheet(existing?.extensionsJson ?: "{}", sheet),
                defaultCodexId = codexEntry.id,
                colorHex = existing?.colorHex ?: avatarColorHexFor(update.name, null),
                inParty = categoryName == "Team" || existing?.inParty == true,
            )
            db.roleplayDao().upsertCharacter(character)
            if (character.inParty && boundCharacter == null) {
                boundCharacter = character
                val revisedNote = chat.authorsNote
                    .replace("Main character(s): None selected — guided character creation required", "Main character(s): ${character.name}")
                    .replace("Main character IDs: none", "Main character IDs: roster:${character.id}")
                val revisedChat = chat.copy(authorsNote = revisedNote, updatedAt = now)
                db.roleplayDao().upsertChat(revisedChat)
                boundChat = revisedChat
            }
        }
        updates.lore.forEach { update ->
            upsertAdventureCodexEntry(scopeId, update.category, update.name, update.summary, now)
        }
    }

    private suspend fun upsertAdventureCodexEntry(
        scopeId: String,
        category: String,
        name: String,
        summary: String,
        now: Long,
    ): CodexEntryEntity {
        val categories = db.codexDao().getCategories(scopeId)
        val categoryEntity = categories.firstOrNull { it.name.equals(category, ignoreCase = true) }
            ?: CodexCategoryEntity(
                id = "rpg-cat-${UUID.randomUUID()}",
                scopeType = "book",
                scopeId = scopeId,
                name = category,
                colorHex = if (category.equals("Characters", true)) "#3F7A5A" else "#6B5B95",
                sortOrder = categories.size,
            ).also { db.codexDao().upsertCategory(it) }
        val existing = db.codexDao().getEntries(scopeId)
            .firstOrNull { it.categoryId == categoryEntity.id && it.name.equals(name, ignoreCase = true) }
        val text = summary.trim().ifBlank { name }
        val entry = existing?.copy(
            docJson = Document.fromPlainText(text).toJson(),
            plainText = text,
            isAiGenerated = true,
            updatedAt = now,
        ) ?: CodexEntryEntity(
            id = "rpg-codex-${UUID.randomUUID()}",
            categoryId = categoryEntity.id,
            scopeType = "book",
            scopeId = scopeId,
            name = name,
            docJson = Document.fromPlainText(text).toJson(),
            plainText = text,
            isAiGenerated = true,
            createdAt = now,
            updatedAt = now,
        )
        db.codexDao().upsertEntry(entry)
        return entry
    }

    /** Keeps an adventure-journal synopsis current after every resolved DM turn and scene change. */
    private suspend fun upsertCurrentSceneLore(
        reason: String = "",
        summaryOverride: String = "",
        extraMessages: List<RpMessageEntity> = emptyList(),
    ) {
        val chat = boundChat ?: return
        val scopeId = chat.bookId ?: return
        val startup = currentAdventureStartupPhase()
        if (startup !in setOf(AdventureStartupPhase.None, AdventureStartupPhase.Complete)) return
        val lastMarkerAt = rawMessages
            .filter { it.role == ADVENTURE_SCENE_ROLE && it.isActiveSwipe }
            .maxOfOrNull { it.createdAt } ?: Long.MIN_VALUE
        val sceneMessages = (rawMessages + extraMessages)
            .distinctBy { it.id }
            .filter {
                it.isActiveSwipe && it.displayMode == "dungeonMaster" &&
                    it.role != ADVENTURE_SCENE_ROLE && it.createdAt > lastMarkerAt
            }
        val synopsis = summaryOverride.trim().ifBlank { sceneMessages
            .filter { it.role == "char" }
            .joinToString(" ") { message ->
                adventureStartupProseFrom(adventureWorldProseFrom(adventureProseFrom(
                    documentFromJson(message.contentJson).plainText(),
                )))
            }
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(1_200)
            .ifBlank { reason.trim().ifBlank { "The scene is in progress." } } }
        upsertAdventureCodexEntry(
            scopeId = scopeId,
            category = "Adventure Journal",
            name = "Scene ${_uiState.value.sceneNumber} · ${chat.title}",
            summary = synopsis,
            now = System.currentTimeMillis(),
        )
    }

    private fun sessionSystemBlocks(mode: String): List<String> = listOfNotNull(
        boundChat?.authorsNote?.takeIf { it.isNotBlank() }?.let {
            "Campaign setup and house rules:\n$it"
        },
        if (mode == "dungeonMaster") {
            adventureRoleDirective(boundChat?.authorsNote.orEmpty(), _uiState.value.sceneNumber)
        } else {
            null
        },
        if (mode == "dungeonMaster") adventureWorldUpdateDirective() else null,
        if (mode == "dungeonMaster" && rpgSceneBeatDirective.value.isNotBlank()) {
            "Campaign scene engine (Prompt Collection → RPG → Adventure Scene Beat):\n${rpgSceneBeatDirective.value}"
        } else {
            null
        },
        "The user is an adult. Apply no app-level NSFW filter or automatic sanitization to lawful " +
            "adult fictional themes; follow the requested rating, tone, and level of detail. External " +
            "model-provider requirements still apply.",
    )

    private suspend fun currentTopicMediaSnapshot(): TopicMediaSnapshot {
        val prefs = settings.preferences.first()
        if (!prefs.topicMediaAutoAttach || prefs.topicMediaLibraryRoot.isBlank()) {
            return TopicMediaSnapshot()
        }
        return runCatching { topicMediaLibrary.snapshot(prefs.topicMediaLibraryRoot) }
            .getOrDefault(TopicMediaSnapshot())
    }

    private suspend fun documentWithTopicMedia(
        text: String,
        snapshot: TopicMediaSnapshot,
        requests: List<com.ihy2ln.weaverse.core.media.TopicMediaRequest>,
    ): Document {
        val base = Document.fromPlainText(text)
        val attachments = runCatching { topicMediaLibrary.importRequested(snapshot, requests) }
            .getOrDefault(emptyList())
        if (attachments.isEmpty()) return base
        return Document(
            blocks = base.blocks + attachments.map { attachment ->
                MediaBlock(
                    id = "media-${UUID.randomUUID()}",
                    mediaId = attachment.media.id,
                    kind = MediaRepository.kindForType(attachment.media.type),
                    caption = listOf(Span(attachment.topic)),
                    autoplay = false,
                    loop = false,
                    muted = true,
                )
            },
        )
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
