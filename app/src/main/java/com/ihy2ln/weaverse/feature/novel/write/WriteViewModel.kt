package com.ihy2ln.weaverse.feature.novel.write

import android.net.Uri
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.ihy2ln.weaverse.ai.AIChunk
import com.ihy2ln.weaverse.ai.AiGenerationService
import com.ihy2ln.weaverse.ai.context.ContextMeter
import com.ihy2ln.weaverse.ai.openrouter.OpenRouterModelCache
import com.ihy2ln.weaverse.ai.prompt.PromptRenderContext
import com.ihy2ln.weaverse.core.media.MediaRepository
import com.ihy2ln.weaverse.core.media.AiMediaRequestParser
import com.ihy2ln.weaverse.core.media.AiMediaResolver
import com.ihy2ln.weaverse.core.text.Block
import com.ihy2ln.weaverse.core.text.toJson
import com.ihy2ln.weaverse.core.text.Document
import com.ihy2ln.weaverse.core.text.Mark
import com.ihy2ln.weaverse.core.text.MediaKind
import com.ihy2ln.weaverse.core.text.MediaStackBlock
import com.ihy2ln.weaverse.core.text.Paragraph
import com.ihy2ln.weaverse.core.text.SceneBeatBlock
import com.ihy2ln.weaverse.core.text.Span
import com.ihy2ln.weaverse.core.text.appendParagraphs
import com.ihy2ln.weaverse.core.text.appendSceneBeat
import com.ihy2ln.weaverse.core.text.withSceneBeatCollapsedToggled
import com.ihy2ln.weaverse.core.text.withSceneBeatPrompt
import com.ihy2ln.weaverse.core.text.applyColor
import com.ihy2ln.weaverse.core.text.documentFromJson
import com.ihy2ln.weaverse.core.text.plainText
import com.ihy2ln.weaverse.core.text.replaceRangeText
import com.ihy2ln.weaverse.core.text.stackMediaOnto
import com.ihy2ln.weaverse.core.text.stackMediaWithAdjacent
import com.ihy2ln.weaverse.core.text.withGridCell
import com.ihy2ln.weaverse.core.text.toggleMark
import com.ihy2ln.weaverse.core.text.wordCount
import com.ihy2ln.weaverse.core.ui.util.UsageFormat
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.SceneEntity
import com.ihy2ln.weaverse.data.repo.CodexRepository
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import com.ihy2ln.weaverse.feature.novel.write.editor.SlashCommand
import com.ihy2ln.weaverse.core.ui.components.MediaEditAction
import com.ihy2ln.weaverse.feature.prompt.PromptEntryBus
import com.ihy2ln.weaverse.feature.prompt.PromptEntryKind
import com.ihy2ln.weaverse.feature.prompt.PromptInsertAnchor
import com.ihy2ln.weaverse.feature.shell.WorkspaceHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import com.ihy2ln.weaverse.data.db.entities.NovelPromptDraft
import com.ihy2ln.weaverse.data.db.entities.NovelWritingSettings
import com.ihy2ln.weaverse.data.repo.PromptRepository
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class WriteViewModel @Inject constructor(
    private val promptRepository: PromptRepository,
    private val documentOps: WriteDocumentOps,
    private val writeGeneration: WriteGeneration,
    private val mediaOps: WriteMediaOps,
    private val aiGeneration: AiGenerationService,
    private val promptAssembler: WritePromptAssembler,
    private val settings: SettingsRepository,
    private val codexRepository: CodexRepository,
    private val db: WeaverseDatabase,
    private val tts: com.ihy2ln.weaverse.core.tts.TtsService,
    private val promptEntryBus: PromptEntryBus,
    private val workspaceHistory: WorkspaceHistory,
    private val modelCache: OpenRouterModelCache,
    private val writeStamps: com.ihy2ln.weaverse.data.repo.SceneWriteStamps,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WriteUiState())
    val uiState: StateFlow<WriteUiState> = _uiState.asStateFlow()
    private val json = Json { ignoreUnknownKeys = true }
    private var bookId: String = "book-adams-haven-1"

    val writingSettings = MutableStateFlow(NovelWritingSettings(""))
    val styleGuide = MutableStateFlow("")
    val effectiveModel = MutableStateFlow("")
    val templates = promptRepository.observePrompts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val availableModels = modelCache.models

    /** Text-generation models only, ready for the composer's model picker. */
    val textModels = modelCache.models
        .map { dtos -> modelCache.writingModels(dtos).filter { it.available } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val promptRequests = promptEntryBus.openRequests
    private var caretNonce = 0L
    private val draftCache = mutableMapOf<String, AiOverlayState>()
    private val positions = mutableMapOf<String, SelectionState>()
    private var generationEpoch = 0L
    private var requestedSceneId = ""

    fun openComposer() {
        if (_uiState.value.aiOverlay != null) resumeAiOverlay() else startSelectionAi("custom", "Custom")
    }

    fun selectTemplate(id: String?) {
        _uiState.update { it.copy(aiOverlay = it.aiOverlay?.copy(promptIds = listOfNotNull(id), contextPreview = "")) }
    }

    /** Ticks or unticks one template; several can be combined for a single generation. */
    fun toggleTemplate(id: String) {
        _uiState.update { state ->
            val overlay = state.aiOverlay ?: return@update state
            val next = if (id in overlay.promptIds) overlay.promptIds - id else overlay.promptIds + id
            state.copy(aiOverlay = overlay.copy(promptIds = next, contextPreview = ""))
        }
    }

    /**
     * Moves the caret into [blockIndex] — used when a tap lands anywhere on the page
     * rather than directly on a line of text.
     */
    fun placeCaret(blockIndex: Int, offset: Int? = null) {
        val blocks = _uiState.value.blocks
        val index = blockIndex.coerceIn(0, (blocks.size - 1).coerceAtLeast(0))
        if (blocks.getOrNull(index) !is Paragraph) return
        val length = (blocks[index] as Paragraph).plainText().length
        val caret = (offset ?: length).coerceIn(0, length)
        caretNonce += 1
        _uiState.update {
            it.copy(
                selection = SelectionState(index, caret, caret),
                caretRequest = CaretRequest(index, caret, caretNonce),
            )
        }
    }

    /**
     * Height in dp the writer dragged the prompt dock to. 0 means fit the content.
     * Kept here so the dock reopens at the chosen size instead of snapping back.
     */
    val promptDockHeight = MutableStateFlow(0f)

    fun setPromptDockHeight(dp: Float) { promptDockHeight.value = dp }

    /** Hand edits to the draft before it is inserted. Ignored mid-stream. */
    fun updateGeneratedDraft(text: String) {
        _uiState.update { state ->
            val overlay = state.aiOverlay ?: return@update state
            if (overlay.isStreaming) return@update state
            state.copy(aiOverlay = overlay.copy(streamingText = text))
        }
    }

    /** Taps below the last line land on the final paragraph, at its end. */
    fun placeCaretAtEnd() {
        val last = _uiState.value.blocks.indexOfLast { it is Paragraph }
        if (last >= 0) placeCaret(last)
    }
    fun saveTemplate(name: String, duplicateId: String? = null) = viewModelScope.launch {
        val original = duplicateId?.let { promptRepository.getPrompt(it) }
        if (original != null) {
            val copy = original.copy(id = "prompt-${UUID.randomUUID()}", name = name, isDefault = false)
            promptRepository.upsert(copy); selectTemplate(copy.id)
        } else {
            val folder = promptRepository.observeFolders().first().firstOrNull()
                ?: promptRepository.createFolder("Writing prompts")
            val prompt = promptRepository.createPrompt(folder.id, name, "custom", _uiState.value.aiOverlay?.prompt.orEmpty())
            selectTemplate(prompt.id)
        }
    }
    fun saveWritingSettings(value: NovelWritingSettings, style: String) {
        writingSettings.value = value
        styleGuide.value = style
        viewModelScope.launch {
            db.novelWritingDao().saveSettings(value)
            db.novelWritingDao().saveStyle(value.bookId, style, nextWriteStamp())
            effectiveModel.value = aiGeneration.resolveModelRef(value.modelRef)
        }
    }
    fun chooseCandidate(index: Int) {
        _uiState.update { state ->
            val current = state.aiOverlay ?: return@update state
            val chosen = current.candidates.getOrNull(index) ?: return@update state
            val history = current.candidates.toMutableList().also { it.removeAt(index) }
            if (current.streamingText.isNotBlank()) history.add(current.copy(candidates = emptyList(), contextPreview = ""))
            state.copy(aiOverlay = chosen.copy(candidates = history, hidden = false, isStreaming = false))
        }
    }
    fun retargetCandidate(replace: Boolean) {
        val state = _uiState.value
        val selection = state.selection
        val paragraph = state.blocks.getOrNull(selection.blockIndex) as? Paragraph ?: return
        if (replace && !selection.hasSelection) {
            _uiState.update { it.copy(aiOverlay = it.aiOverlay?.copy(errorMessage = "Select text in the manuscript, then retarget.")) }; return
        }
        _uiState.update { it.copy(aiOverlay = it.aiOverlay?.copy(targetSceneId = state.sceneId, anchorBlockId = paragraph.id,
            sourceParagraphText = paragraph.plainText(), cursorOffset = selection.max,
            replaceBlockIndex = if (replace) selection.blockIndex else null,
            replaceStart = if (replace) selection.min else null, replaceEnd = if (replace) selection.max else null,
            errorMessage = "", contextPreview = "")) }
    }

    private var loadedScene: SceneEntity? = null
    private var sceneJob: Job? = null
    private var generationJob: Job? = null
    private var applyingHistory = false
    /** Snapshot taken at the start of a typing burst; flushed before discrete edits. */
    private var typingBaseline: List<Block>? = null
    private val unregisterHistoryFlush = workspaceHistory.registerPreUndo { flushTypingHistory() }
    private var contextLimit = ContextMeter.DEFAULT_LIMIT
    private var revisionJob: Job? = null
    /** docJson of the most recent local write — identifies our own Room echoes. */
    private var lastPersistedDocJson: String? = null
    private val pendingDocuments = mutableMapOf<String, Document>()

    /** Serialized across scenes; a scene switch must never drop another scene's write. */
    private val persistQueue = Channel<PendingSceneWrite>(Channel.UNLIMITED)

    private data class PendingSceneWrite(val sceneId: String, val doc: Document)

    init {
        viewModelScope.launch {
            uiState.mapNotNull { it.aiOverlay }.distinctUntilChanged().onEach { overlay ->
                if (overlay.targetSceneId.isNotBlank()) draftCache[overlay.targetSceneId] = overlay
            }.buffer(Channel.UNLIMITED).collect { overlay ->
                if (overlay.targetSceneId.isNotBlank()) db.novelWritingDao().saveDraft(NovelPromptDraft(
                    overlay.targetSceneId, json.encodeToString(overlay.copy(isStreaming = false, pickBeatImageRequestId = 0L,
                        errorMessage = if (overlay.isStreaming) "Interrupted. Review partial output or Generate again." else overlay.errorMessage))))
            }
        }

        viewModelScope.launch {
            settings.preferences.collect { prefs ->
                if (bookId != prefs.selectedBookId || writingSettings.value.bookId.isBlank()) {
                    bookId = prefs.selectedBookId
                    writingSettings.value = db.novelWritingDao().settings(bookId) ?: NovelWritingSettings(bookId)
                    styleGuide.value = db.bookDao().getById(bookId)?.styleGuide.orEmpty()
                }
                effectiveModel.value = aiGeneration.resolveModelRef(writingSettings.value.modelRef)
                _uiState.update {
                    it.copy(
                        showInlineWritingPrompt = prefs.extraPromptSurfaces.inlineWriting,
                        showSceneBeatCard = prefs.extraPromptSurfaces.sceneBeatCard,
                        showContinuationBox = prefs.extraPromptSurfaces.continuation,
                    )
                }
            }
        }
        viewModelScope.launch {
            workspaceHistory.state.collect { hist ->
                _uiState.update { it.copy(canUndo = hist.canUndo, canRedo = hist.canRedo) }
            }
        }
        viewModelScope.launch {
            combine(settings.preferences, modelCache.models) { prefs, dtos ->
                ContextMeter.limitFor(prefs.defaultModelRef, modelCache.toModelInfo(dtos))
            }.collect { limit ->
                contextLimit = limit
                refreshContextMeter()
            }
        }
        viewModelScope.launch {
            codexRepository.observeAllEntries().collect { entries ->
                val names = entries
                    .filter { !it.disabled }
                    .flatMap { entry ->
                        val aliases = runCatching {
                            json.decodeFromString<List<String>>(entry.aliasesJson)
                        }.getOrDefault(emptyList())
                        listOf(entry.name) + aliases
                    }
                    .map { it.trim() }
                    .filter { it.length >= 2 }
                    .distinct()
                val mentionTargets = entries
                    .filter { !it.disabled && it.trackMentions }
                    .map { entry ->
                        com.ihy2ln.weaverse.core.text.CodexMentionTarget(
                            entryId = entry.id,
                            name = entry.name,
                            aliases = com.ihy2ln.weaverse.core.text.decodeAliases(entry.aliasesJson),
                            caseSensitive = entry.caseSensitiveMatching,
                        )
                    }
                    .filter { it.name.trim().length >= 2 }
                _uiState.update { it.copy(codexNames = names, codexMentionTargets = mentionTargets) }
            }
        }
        viewModelScope.launch { drainPersistQueue() }
    }

    private var pendingJumpKind: String = "Scene"

    fun loadScene(sceneId: String, jumpKind: String = "Scene") {
        pendingJumpKind = jumpKind
        if ((loadedScene?.id == sceneId || requestedSceneId == sceneId) && sceneJob?.isActive == true) {
            if (jumpKind == "SceneBeat") startSceneBeatFromPlan()
            return
        }
        requestedSceneId = sceneId
        positions[_uiState.value.sceneId] = _uiState.value.selection
        _uiState.value.aiOverlay?.let { draftCache[it.targetSceneId] = it.copy(isStreaming = false) }
        flushTypingHistory()
        cancelAiGeneration()
        _uiState.update { it.copy(aiOverlay = null) }
        sceneJob?.cancel()
        revisionJob?.cancel()
        if (typingBaseline != null) {
            typingBaseline = null
            workspaceHistory.removePendingUndo()
        }
        // Scene switched: drop the previous scene's echo identity so its persisted
        // docJson can never suppress the first echo of the newly loaded scene.
        loadedScene = null
        lastPersistedDocJson = null
        sceneJob = viewModelScope.launch {
            val restored = draftCache[sceneId] ?: db.novelWritingDao().draft(sceneId)?.let {
                runCatching { json.decodeFromString<AiOverlayState>(it.stateJson) }.getOrNull()
            }
            if (requestedSceneId != sceneId) return@launch
            _uiState.update { it.copy(aiOverlay = restored?.copy(hidden = true, isStreaming = false, pickBeatImageRequestId = 0L),
                selection = positions[sceneId] ?: SelectionState()) }
            documentOps.observeScene(sceneId).collect { scene ->
                if (scene != null) applyScene(scene)
            }
        }
        revisionJob = viewModelScope.launch {
            documentOps.observeRevisions(sceneId).collect { list ->
                _uiState.update { it.copy(revisions = documentOps.revisionUi(list)) }
            }
        }
    }

    fun startSceneBeatFromPlan() = startSelectionAi("scene_beat", "Scene beat")

    fun insertContinuation(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        updateBlocksSync(recordHistory = true) { blocks ->
            val next = blocks.appendParagraphs(trimmed)
            blocks.clear()
            blocks.addAll(next)
        }
    }

    fun updateSceneBeatPrompt(index: Int, prompt: String) {
        beginTypingHistory()
        updateBlocks(recordHistory = false) { blocks ->
            val next = blocks.withSceneBeatPrompt(index, prompt)
            blocks.clear()
            blocks.addAll(next)
        }
        _uiState.update { state ->
            val overlay = state.aiOverlay
            if (overlay != null &&
                overlay.commandId == "scene_beat" &&
                overlay.insertAfterIndex == index
            ) {
                state.copy(aiOverlay = overlay.copy(prompt = prompt, errorMessage = ""))
            } else {
                state
            }
        }
    }

    fun toggleSceneBeat(index: Int) {
        updateBlocks(recordHistory = false) { blocks ->
            val next = blocks.withSceneBeatCollapsedToggled(index)
            blocks.clear()
            blocks.addAll(next)
        }
    }

    fun generateFromSceneBeat(index: Int) {
        val beat = _uiState.value.blocks.getOrNull(index) as? SceneBeatBlock ?: return
        if (beat.prompt.isBlank()) {
            _uiState.update { it.copy(statusMessage = "Write a scene beat prompt first") }
            return
        }
        viewModelScope.launch {
            val library = promptAssembler.libraryPromptBundle("scene_beat", PromptRenderContext())
            _uiState.update {
                it.copy(
                    aiOverlay = AiOverlayState(
                        targetSceneId = _uiState.value.sceneId,
                        commandId = "scene_beat",
                        label = "SCENE BEAT",
                        insertAfterIndex = index,
                        anchorBlockId = beat.id,
                        prompt = beat.prompt,
                        systemInstructions = library.systemInstructions,
                        promptIds = library.promptIds,
                    ),
                )
            }
            // The composer requires an explicit Generate action.
        }
    }

    fun clearSceneBeat(index: Int) {
        updateSceneBeatPrompt(index, "")
        val overlay = _uiState.value.aiOverlay
        if (overlay?.commandId == "scene_beat" && overlay.insertAfterIndex == index) {
            discardAiResult()
        }
    }

    private fun applyScene(scene: SceneEntity) {
        val pending = pendingDocuments[scene.id]
        if (pending != null && pending.blocks != documentFromJson(scene.docJson).blocks) return
        // Every local keystroke writes the scene to Room and the observe flow echoes it
        // back here. While the user keeps typing, an echo can lag behind newer local
        // state; applying it would clobber fresher keystrokes (dropped/jumping text).
        // Own echoes carry the last persisted docJson; stale echoes carry an older stamp.
        val knownUpdatedAt = loadedScene?.updatedAt ?: 0L
        if (scene.updatedAt < knownUpdatedAt) return
        if (scene.docJson == lastPersistedDocJson) return
        loadedScene = scene
        val doc = documentFromJson(scene.docJson)
        val blocks = doc.blocks.ifEmpty { listOf(Paragraph("new-p", listOf(Span("")))) }
        viewModelScope.launch {
            val mediaIds = blocks.flatMap { WriteMediaOps.mediaIdsOf(it) }
            val paths = mediaOps.resolvePaths(mediaIds)
            if (loadedScene?.id != scene.id) return@launch
            _uiState.update {
                it.copy(
                    sceneId = scene.id,
                    sceneTitle = scene.title,
                    blocks = blocks,
                    mediaPaths = paths,
                    wordCount = doc.wordCount(),
                    canUndo = workspaceHistory.state.value.canUndo,
                    canRedo = workspaceHistory.state.value.canRedo,
                )
            }
            // Back-end insert target: as soon as a scene is open, anchor ⌖ at the end
            // of its last paragraph so dock prompts always have a valid destination.
            // Tapping anywhere in the editor re-anchors it from then on.
            if (promptEntryBus.insertAnchor.value?.sceneId != scene.id) {
                val lastPara = blocks.indexOfLast { it is Paragraph }
                if (lastPara >= 0) {
                    val caret = (blocks[lastPara] as Paragraph).plainText().length
                    promptEntryBus.setInsertAnchor(
                        PromptInsertAnchor(sceneId = scene.id, blockIndex = lastPara, caret = caret),
                    )
                }
            }
            val jump = pendingJumpKind
            pendingJumpKind = "Scene"
            if (jump == "SceneBeat") startSceneBeatFromPlan()
        }
    }

    fun updateParagraph(index: Int, paragraph: Paragraph) {
        beginTypingHistory()
        updateBlocks(recordHistory = false) { blocks ->
            blocks[index] = paragraph
        }
    }

    fun onPromptShortcut(kind: PromptEntryKind) {
        openComposer()
    }

    fun onSelectionChange(blockIndex: Int, range: TextRange) {
        _uiState.update {
            it.copy(
                selection = SelectionState(
                    blockIndex = blockIndex,
                    start = range.start,
                    end = range.end,
                ),
            )
        }
        // Publish the caret so the prompt dock can target "⌖ Cursor" insertions.
        loadedScene?.id?.let { sceneId ->
            promptEntryBus.setInsertAnchor(
                PromptInsertAnchor(
                    sceneId = sceneId,
                    blockIndex = blockIndex,
                    caret = range.max,
                ),
            )
        }
    }

    fun setEditPopupBlock(index: Int?) {
        _uiState.update { it.copy(editPopupBlockIndex = index) }
    }

    fun clearStatus() = _uiState.update { it.copy(statusMessage = "", pendingCodexEntryId = null) }

    fun toggleFindReplace() {
        _uiState.update {
            val visible = !it.findReplace.visible
            it.copy(findReplace = it.findReplace.copy(visible = visible))
        }
        if (_uiState.value.findReplace.visible) recomputeFindMatches()
    }

    fun updateFindQuery(query: String) {
        _uiState.update { it.copy(findReplace = it.findReplace.copy(query = query, matchIndex = 0)) }
        recomputeFindMatches()
    }

    fun updateFindReplacement(value: String) {
        _uiState.update { it.copy(findReplace = it.findReplace.copy(replacement = value)) }
    }

    fun findNext() = stepFind(1)
    fun findPrev() = stepFind(-1)

    fun replaceCurrent() {
        val fr = _uiState.value.findReplace
        val next = documentOps.replaceCurrent(_uiState.value.blocks, fr) ?: return
        updateBlocksSync(recordHistory = true) { blocks ->
            blocks.clear()
            blocks.addAll(next)
        }
        recomputeFindMatches()
    }

    fun replaceAllInScene() {
        val fr = _uiState.value.findReplace
        if (fr.query.isEmpty()) return
        updateBlocksSync(recordHistory = true) { blocks ->
            val (next, count) = documentOps.replaceAll(blocks, fr)
            blocks.clear()
            blocks.addAll(next)
            _uiState.update { it.copy(statusMessage = "Replaced $count") }
        }
        recomputeFindMatches()
    }

    fun toggleHistory() {
        _uiState.update { it.copy(showHistory = !it.showHistory) }
    }

    fun restoreRevision(revisionId: String) {
        viewModelScope.launch {
            val restored = documentOps.restoreRevision(revisionId) ?: return@launch
            applyScene(restored)
            _uiState.update { it.copy(showHistory = false, statusMessage = "Restored snapshot") }
        }
    }

    fun snapshotNow() {
        val scene = loadedScene ?: return
        val document = Document(_uiState.value.blocks)
        viewModelScope.launch {
            documentOps.snapshotNow(scene.copy(docJson = document.toJson(), plainText = document.plainText(), wordCount = document.wordCount()), kind = "manual")
            _uiState.update { it.copy(statusMessage = "Snapshot saved") }
        }
    }

    fun dismissColorPicker() = _uiState.update { it.copy(showColorPicker = false) }

    fun toggleMarkOnSelection(mark: Mark) {
        val sel = _uiState.value.selection
        if (!sel.hasSelection) return
        val block = _uiState.value.blocks.getOrNull(sel.blockIndex) as? Paragraph ?: return
        val next = block.copy(spans = block.spans.toggleMark(sel.min, sel.max, mark))
        flushTypingHistory()
        updateBlocks(recordHistory = true) { it[sel.blockIndex] = next }
    }

    fun applyColorOnSelection(colorHex: String) {
        val sel = _uiState.value.selection
        if (!sel.hasSelection) return
        val block = _uiState.value.blocks.getOrNull(sel.blockIndex) as? Paragraph ?: return
        val next = block.copy(spans = block.spans.applyColor(sel.min, sel.max, colorHex))
        flushTypingHistory()
        updateBlocks(recordHistory = true) { it[sel.blockIndex] = next }
        _uiState.update { it.copy(showColorPicker = false) }
    }

    fun requestColorPicker() {
        if (!_uiState.value.selection.hasSelection) return
        _uiState.update { it.copy(showColorPicker = true, editPopupBlockIndex = null) }
    }

    fun selectAllInFocusedBlock() {
        val sel = _uiState.value.selection
        val block = _uiState.value.blocks.getOrNull(sel.blockIndex) as? Paragraph ?: return
        val len = block.plainText().length
        _uiState.update {
            it.copy(selection = sel.copy(start = 0, end = len))
        }
    }

    fun selectedText(): String {
        val sel = _uiState.value.selection
        val block = _uiState.value.blocks.getOrNull(sel.blockIndex) as? Paragraph ?: return ""
        val text = block.plainText()
        if (!sel.hasSelection) return text
        return text.substring(sel.min.coerceIn(0, text.length), sel.max.coerceIn(0, text.length))
    }

    fun pasteIntoSelection(clipboardText: String) {
        val sel = _uiState.value.selection
        val block = _uiState.value.blocks.getOrNull(sel.blockIndex) as? Paragraph ?: return
        val next = block.copy(
            spans = block.spans.replaceRangeText(sel.min, sel.max, clipboardText),
        )
        flushTypingHistory()
        updateBlocks(recordHistory = true) { it[sel.blockIndex] = next }
        val caret = sel.min + clipboardText.length
        _uiState.update {
            it.copy(selection = sel.copy(start = caret, end = caret))
        }
    }

    /** Returns cut text for the clipboard; removes the selection. */
    fun cutSelection(): String {
        val text = selectedText()
        if (text.isEmpty() || !_uiState.value.selection.hasSelection) return ""
        deleteSelection()
        return text
    }

    fun deleteSelection() {
        val sel = _uiState.value.selection
        if (!sel.hasSelection) return
        pasteIntoSelection("")
    }

    fun selectMediaBlock(index: Int?) {
        _uiState.update { it.copy(selectedMediaBlockIndex = index) }
    }

    fun removeMediaBlock(index: Int) {
        val block = _uiState.value.blocks.getOrNull(index) ?: return
        val removedIds = WriteMediaOps.mediaIdsOf(block)
        if (removedIds.isEmpty()) return
        updateBlocks(recordHistory = true) { blocks ->
            blocks.removeAt(index)
        }
        _uiState.update {
            it.copy(
                selectedMediaBlockIndex = null,
                mediaPaths = it.mediaPaths - removedIds.toSet(),
            )
        }
    }

    fun removeSelectedMediaBlock() {
        val index = _uiState.value.selectedMediaBlockIndex ?: return
        removeMediaBlock(index)
    }

    fun onMediaEditAction(index: Int, action: MediaEditAction) {
        when (action) {
            MediaEditAction.Cut -> cutMediaBlock(index)
            MediaEditAction.Copy -> copyMediaBlock(index)
            MediaEditAction.Paste -> pasteMediaBlock(afterIndex = index)
            MediaEditAction.Delete -> removeMediaBlock(index)
            MediaEditAction.Shrink -> adjustMediaWidth(index, -15f)
            MediaEditAction.Expand -> adjustMediaWidth(index, 15f)
            MediaEditAction.Collapse -> setMediaCollapsed(index, true)
            MediaEditAction.Uncollapse -> setMediaCollapsed(index, false)
            MediaEditAction.Stack -> stackMediaWithAdjacent(index)
            MediaEditAction.Move -> Unit
            // Panel-canvas only (Roleplay/DM storyboard).
            MediaEditAction.AdjustImage, MediaEditAction.EditImage, MediaEditAction.SeparatePanels, MediaEditAction.SeparatePanelsAuto, MediaEditAction.AddMedia, MediaEditAction.GenerateMedia, MediaEditAction.AddTextOverlay -> Unit
        }
    }

    private fun copyMediaBlock(index: Int) {
        val block = _uiState.value.blocks.getOrNull(index) ?: return
        if (!mediaOps.copyToClipboard(block)) return
        _uiState.update { it.copy(canPasteMedia = true, statusMessage = "Media copied") }
    }

    private fun cutMediaBlock(index: Int) {
        copyMediaBlock(index)
        removeMediaBlock(index)
        _uiState.update { it.copy(statusMessage = "Media cut") }
    }

    fun pasteMediaBlock(afterIndex: Int? = _uiState.value.selectedMediaBlockIndex) {
        val payload = mediaOps.clipboardPayload ?: return
        viewModelScope.launch {
            val insertAt = ((afterIndex ?: (_uiState.value.blocks.lastIndex)) + 1)
                .coerceIn(0, _uiState.value.blocks.size)
            val block = WriteMediaOps.blockFromPayload(payload)
            val paths = mediaOps.resolvePaths(WriteMediaOps.mediaIdsOf(block))
            updateBlocks(recordHistory = true) { blocks ->
                blocks.add(insertAt, block)
            }
            _uiState.update {
                it.copy(
                    selectedMediaBlockIndex = insertAt,
                    mediaPaths = it.mediaPaths + paths,
                    canPasteMedia = mediaOps.canPaste,
                    statusMessage = "Media pasted",
                )
            }
        }
    }

    private fun adjustMediaWidth(index: Int, delta: Float) {
        updateBlocks(recordHistory = true) { blocks ->
            val next = WriteMediaOps.adjustWidth(blocks.getOrNull(index) ?: return@updateBlocks, delta)
            if (next != null) blocks[index] = next
        }
    }

    private fun setMediaCollapsed(index: Int, collapsed: Boolean) {
        updateBlocks(recordHistory = true) { blocks ->
            val next = WriteMediaOps.setCollapsed(blocks.getOrNull(index) ?: return@updateBlocks, collapsed)
            if (next != null) blocks[index] = next
        }
    }

    /** Stack the media at [index] with an adjacent media/stack block; persists JSON. */
    fun stackMediaWithAdjacent(index: Int) {
        val next = _uiState.value.blocks.stackMediaWithAdjacent(index)
        if (next == null) {
            _uiState.update {
                it.copy(statusMessage = "Drag this picture onto another to stack them.")
            }
            return
        }
        updateBlocks(recordHistory = true) { blocks ->
            blocks.clear()
            blocks.addAll(next)
        }
        _uiState.update {
            it.copy(
                selectedMediaBlockIndex = index.coerceAtMost(next.lastIndex),
                statusMessage = "Pictures stacked",
            )
        }
    }

    /** Drag-onto stack: merge [fromIndex] onto [ontoIndex]. */
    fun stackMediaOnto(fromIndex: Int, ontoIndex: Int) {
        val next = _uiState.value.blocks.stackMediaOnto(fromIndex, ontoIndex) ?: return
        updateBlocks(recordHistory = true) { blocks ->
            blocks.clear()
            blocks.addAll(next)
        }
        _uiState.update {
            it.copy(
                selectedMediaBlockIndex = minOf(fromIndex, ontoIndex).coerceAtMost(next.lastIndex),
                statusMessage = "Pictures stacked",
            )
        }
    }

    /**
     * After a long-press drag: if released over another media block, stack onto it;
     * otherwise reorder by vertical threshold (legacy).
     */
    fun onMediaDragRelease(index: Int, dragOffsetY: Float) {
        when (val action = WriteMediaOps.dragRelease(_uiState.value.blocks, index, dragOffsetY)) {
            is WriteMediaDragAction.StackOnto -> stackMediaOnto(action.fromIndex, action.ontoIndex)
            is WriteMediaDragAction.Move -> moveBlock(action.index, action.delta)
            WriteMediaDragAction.None -> Unit
        }
    }

    fun setMediaGridCell(index: Int, col: Int, row: Int) {
        updateBlocks(recordHistory = true) { blocks ->
            if (index !in blocks.indices) return@updateBlocks
            blocks[index] = blocks[index].withGridCell(col, row)
        }
    }

    fun cycleMediaStack(index: Int) {
        updateBlocks(recordHistory = false) { blocks ->
            val stack = blocks.getOrNull(index) as? MediaStackBlock ?: return@updateBlocks
            blocks[index] = WriteMediaOps.cycleStack(stack)
        }
    }

    /** Move a media (or any) block by [delta] slots; persists document order. */
    fun moveBlock(index: Int, delta: Int) {
        if (delta == 0) return
        updateBlocks(recordHistory = true) { blocks ->
            val target = (index + delta).coerceIn(0, blocks.lastIndex)
            if (target == index) return@updateBlocks
            val item = blocks.removeAt(index)
            blocks.add(target, item)
        }
        _uiState.update { state ->
            val selected = state.selectedMediaBlockIndex
            val nextSelected = when {
                selected == null -> null
                selected == index -> (index + delta).coerceIn(0, state.blocks.lastIndex)
                else -> selected
            }
            state.copy(selectedMediaBlockIndex = nextSelected)
        }
    }

    fun undo() {
        flushTypingHistory()
        viewModelScope.launch { workspaceHistory.undo() }
    }

    fun redo() {
        flushTypingHistory()
        viewModelScope.launch { workspaceHistory.redo() }
    }

    fun startSelectionAi(commandId: String, label: String) {
        val state = _uiState.value
        val focused = state.blocks.getOrNull(state.selection.blockIndex) as? Paragraph
        val fallback = state.blocks.indexOfLast { it is Paragraph }
        val sel = if (focused != null || fallback < 0) state.selection else SelectionState(fallback,
            (state.blocks[fallback] as Paragraph).plainText().length, (state.blocks[fallback] as Paragraph).plainText().length)
        val block = state.blocks.getOrNull(sel.blockIndex)
        val paragraph = block as? Paragraph
        cancelAiGeneration()
        val old = state.aiOverlay
        val history = old?.candidates.orEmpty() + listOfNotNull(old?.takeIf { it.streamingText.isNotBlank() }?.copy(candidates = emptyList(), contextPreview = ""))
        val replace = commandId in listOf("replace", "expand", "extend", "shorten") && sel.hasSelection && paragraph != null
        _uiState.update { it.copy(editPopupBlockIndex = null, aiOverlay = AiOverlayState(
            targetSceneId = state.sceneId, commandId = commandId, label = label,
            anchorBlockId = block?.id, sourceParagraphText = paragraph?.plainText(),
            cursorOffset = if (paragraph != null) sel.max else null, insertAfterIndex = sel.blockIndex,
            prompt = old?.prompt.orEmpty(), outputWords = old?.outputWords ?: writingSettings.value.outputWords,
            promptIds = old?.promptIds.orEmpty(), candidates = history,
            replaceBlockIndex = if (replace) sel.blockIndex else null,
            replaceStart = if (replace) sel.min else null, replaceEnd = if (replace) sel.max else null,
        )) }
    }

    fun addSelectionToCodex() {
        val text = selectedText().trim()
        if (text.isBlank()) {
            _uiState.update { it.copy(statusMessage = "Select text to add to Codex") }
            return
        }
        viewModelScope.launch {
            val categories = db.codexDao().getCategories(bookId)
            val category = categories.firstOrNull()
                ?: run {
                    _uiState.update { it.copy(statusMessage = "No Codex categories in this book") }
                    return@launch
                }
            val name = text.lineSequence().first().trim().take(48).ifBlank { "New entry" }
            val entry = codexRepository.addEntry(category.id, bookId, name)
            codexRepository.updateEntryText(entry.id, name, text)
            _uiState.update {
                it.copy(
                    editPopupBlockIndex = null,
                    statusMessage = "Added to Codex: $name",
                    pendingCodexEntryId = entry.id,
                )
            }
        }
    }

    fun onSlashTrigger(index: Int) {
        _uiState.update { it.copy(slashBlockIndex = index, slashFilter = "") }
    }

    fun onBackslashTrigger(index: Int) {
        _uiState.update { it.copy(slashBlockIndex = null, slashFilter = "", statusMessage = "Continue typing here. Use Text for formatting or Media to insert an attachment.") }
    }

    fun dismissSlash() {
        _uiState.update { it.copy(slashBlockIndex = null, slashFilter = "") }
    }

    fun applySlashCommand(command: SlashCommand) {
        val index = _uiState.value.slashBlockIndex ?: return
        dismissSlash()
        if (command.id in listOf("image", "video")) { requestAddMedia(); return }
        onSelectionChange(index, TextRange(_uiState.value.selection.max))
        startSelectionAi(command.id, command.label)
    }

    fun updateAiPrompt(value: String) {
        _uiState.update { state ->
            state.copy(aiOverlay = state.aiOverlay?.copy(prompt = value, errorMessage = ""))
        }
        refreshContextMeter()
    }

    fun updateOutputWords(words: Int) {
        _uiState.update { state ->
            state.copy(aiOverlay = state.aiOverlay?.copy(outputWords = words.coerceIn(50, 4000)))
        }
    }

    fun consumeBeatImageRequest() { _uiState.update { it.copy(aiOverlay = it.aiOverlay?.copy(pickBeatImageRequestId = 0L)) } }

    fun requestBeatImage() {
        _uiState.update { state ->
            val overlay = state.aiOverlay ?: return@update state
            state.copy(
                aiOverlay = overlay.copy(pickBeatImageRequestId = overlay.pickBeatImageRequestId + 1),
            )
        }
    }

    fun attachBeatImage(uri: Uri) {
        val target = _uiState.value.aiOverlay ?: return
        viewModelScope.launch {
            runCatching {
                val media = mediaOps.importFromUri(uri)
                val path = mediaOps.resolveFile(media).absolutePath
                if (_uiState.value.sceneId != target.targetSceneId) return@launch
                _uiState.update { state ->
                    state.copy(
                        aiOverlay = state.aiOverlay?.copy(
                            imageMediaId = media.id,
                            imagePath = path,
                            errorMessage = "",
                        ),
                    )
                }
            }.onFailure { err ->
                if (err is kotlinx.coroutines.CancellationException) throw err
                _uiState.update {
                    it.copy(
                        aiOverlay = it.aiOverlay?.copy(
                            errorMessage = err.message ?: "Failed to attach image",
                        ),
                    )
                }
            }
        }
    }

    fun clearBeatImage() {
        _uiState.update {
            it.copy(aiOverlay = it.aiOverlay?.copy(imageMediaId = null, imagePath = null))
        }
    }

    fun speakText(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            runCatching { tts.speak(text) }
        }
    }

    fun dismissAiOverlay() {
        _uiState.update { it.copy(aiOverlay = it.aiOverlay?.copy(hidden = true)) }
    }

    fun resumeAiOverlay() = _uiState.update { it.copy(aiOverlay = it.aiOverlay?.copy(hidden = false)) }

    fun previewAiContext() {
        val overlay = _uiState.value.aiOverlay ?: return
        val scene = loadedScene ?: return
        val prose = Document(_uiState.value.blocks).plainText()
        viewModelScope.launch {
            val prep = writeGeneration.prepareStream(overlay, prose, scene, bookId,
                hasApiKey = true, modelSupportsImages = aiGeneration.modelSupportsImages(effectiveModel.value),
                contextLimit = ContextMeter.limitFor(effectiveModel.value, modelCache.toModelInfo(modelCache.models.first())))
            if (loadedScene?.id != scene.id || _uiState.value.aiOverlay?.anchorBlockId != overlay.anchorBlockId) return@launch
            val preview = when (prep) {
                is WriteGenerationPrep.Failed -> prep.message
                is WriteGenerationPrep.Ready -> buildString {
                    appendLine("Provider/model: ${effectiveModel.value}; output: ${overlay.outputWords} words")
                    appendLine(prep.plan.overlay.contextMeter?.label + " (includes output reserve and image allowance)")
                    appendLine("Dropped entries: " + prep.plan.assembled.droppedEntryIds.joinToString())
                    appendLine("Story knowledge: " + prep.plan.assembled.usedEntries.joinToString { it.name })
                    appendLine("Attached images: ${prep.plan.imageAttachments.size}")
                    appendLine(prep.plan.assembled.systemBlocks.joinToString("\n\n"))
                    prep.plan.assembled.messages.forEach { appendLine("${it.first}: ${it.second}") }
                    appendLine(prep.plan.userMessage)
                }
            }
            _uiState.update { it.copy(aiOverlay = it.aiOverlay?.copy(contextPreview = preview)) }
        }
    }

    fun runAiGeneration() {
        val overlay = _uiState.value.aiOverlay ?: return
        if (overlay.isStreaming) return
        generationJob?.cancel()
        val epoch = ++generationEpoch
        val targetScene = loadedScene
        val prose = Document(_uiState.value.blocks).plainText()
        val model = effectiveModel.value
        _uiState.update { it.copy(aiOverlay = it.aiOverlay?.copy(isStreaming = true)) }
        generationJob = viewModelScope.launch {
            val prep = writeGeneration.prepareStream(
                overlay = overlay,
                sceneText = prose,
                scene = targetScene,
                bookId = bookId,
                hasApiKey = aiGeneration.hasApiKey(model),
                modelSupportsImages = aiGeneration.modelSupportsImages(model),
                contextLimit = ContextMeter.limitFor(model, modelCache.toModelInfo(modelCache.models.first())),
            )
            if (epoch != generationEpoch || loadedScene?.id != targetScene?.id) return@launch
            val plan = when (prep) {
                is WriteGenerationPrep.Failed -> {
                    _uiState.update {
                        it.copy(aiOverlay = it.aiOverlay?.copy(errorMessage = prep.message, isStreaming = false))
                    }
                    return@launch
                }
                is WriteGenerationPrep.Ready -> prep.plan
            }
            _uiState.update {
                it.copy(
                    aiOverlay = plan.overlay.copy(
                        candidates = overlay.candidates + listOfNotNull(overlay.takeIf { it.streamingText.isNotBlank() }?.copy(candidates = emptyList(), contextPreview = "")),
                        isStreaming = true,
                        streamingText = "",
                        errorMessage = "",
                        usageLog = "",
                    ),
                )
            }
            val builder = StringBuilder()
            var usageLog = ""
            runCatching {
                aiGeneration.stream(
                    modelRef = model,
                    userMessage = plan.userMessage,
                    assembled = plan.assembled,
                    maxTokens = plan.maxTokens,
                    imageAttachments = plan.imageAttachments,
                ).collect { chunk ->
                    if (epoch != generationEpoch || loadedScene?.id != targetScene?.id) return@collect
                    when (chunk) {
                        is AIChunk.Delta -> {
                            builder.append(chunk.text)
                            _uiState.update {
                                it.copy(aiOverlay = it.aiOverlay?.copy(streamingText = builder.toString()))
                            }
                        }
                        is AIChunk.Usage -> {
                            usageLog = UsageFormat.formatUsage(
                                promptTokens = chunk.promptTokens,
                                completionTokens = chunk.completionTokens,
                                totalTokens = chunk.totalTokens,
                                cost = chunk.cost,
                            )
                        }
                        is AIChunk.RetryWait -> {
                            _uiState.update {
                                it.copy(
                                    aiOverlay = it.aiOverlay?.copy(
                                        errorMessage = "Rate limited — retry in ${chunk.secondsLeft}s",
                                    ),
                                )
                            }
                        }
                        AIChunk.Done -> Unit
                    }
                }
            }.onFailure { err ->
                if (err is kotlinx.coroutines.CancellationException) throw err
                if (epoch != generationEpoch) return@launch
                _uiState.update {
                    it.copy(
                        aiOverlay = it.aiOverlay?.copy(
                            isStreaming = false,
                            errorMessage = writeGeneration.formatError(err),
                        ),
                    )
                }
                return@launch
            }
            _uiState.update {
                it.copy(
                    aiOverlay = it.aiOverlay?.copy(
                        isStreaming = false,
                        streamingText = builder.toString(),
                        usageLog = usageLog,
                    ),
                )
            }
        }
    }

    fun cancelAiGeneration() {
        generationEpoch++
        generationJob?.cancel()
        generationJob = null
        _uiState.update {
            it.copy(aiOverlay = it.aiOverlay?.copy(isStreaming = false, errorMessage = "Cancelled"))
        }
    }

    /** Runs the Scene Summarizations prompt against the current scene and saves the result into its summary. */
    fun summarizeScene() {
        val scene = loadedScene ?: return
        if (_uiState.value.isSummarizing) return
        viewModelScope.launch {
            val sceneText = Document(_uiState.value.blocks).plainText()
            val prep = writeGeneration.prepareSummarize(
                sceneText = sceneText,
                scene = scene,
                bookId = bookId,
                hasApiKey = aiGeneration.hasApiKey(),
            ).getOrElse { err ->
                _uiState.update { it.copy(statusMessage = writeGeneration.formatError(err)) }
                return@launch
            }
            _uiState.update { it.copy(isSummarizing = true, statusMessage = "Summarizing…") }
            runCatching {
                aiGeneration.complete(
                    userMessage = prep.userMessage,
                    assembled = prep.assembled,
                    maxTokens = prep.maxTokens,
                )
            }.onSuccess { result ->
                val summary = result.text.trim()
                db.novelMediaDao().updateSceneSummary(scene.id, summary, nextWriteStamp())
                _uiState.update { it.copy(isSummarizing = false, statusMessage = "Scene summarized") }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSummarizing = false,
                        statusMessage = writeGeneration.formatError(err),
                    )
                }
            }
        }
    }

    fun acceptAiResult() {
        val overlay = _uiState.value.aiOverlay ?: return
        if (overlay.isStreaming) return
        if (overlay.targetSceneId.isNotBlank() && overlay.targetSceneId != _uiState.value.sceneId) {
            _uiState.update { it.copy(aiOverlay = it.aiOverlay?.copy(errorMessage = "This candidate belongs to another scene.")) }; return
        }
        val extracted = AiMediaRequestParser.extract(overlay.streamingText.trim())
        val text = extracted.first
        if (text.isBlank()) {
            finishAiDraft()
            return
        }
        val candidate = try { writeGeneration.acceptIntoBlocks(_uiState.value.blocks, overlay, text) }
        catch (failure: IllegalStateException) {
            _uiState.update { it.copy(aiOverlay = it.aiOverlay?.copy(errorMessage = failure.message.orEmpty())) }
            return
        }
        snapshotNow()
        flushTypingHistory()
        updateBlocksSync(recordHistory = true) { blocks ->
            blocks.clear()
            blocks.addAll(candidate)
        }
        if (extracted.second.isNotEmpty()) {
            viewModelScope.launch {
                val resolver = AiMediaResolver { db.mediaDao().observeAll().first() }
                extracted.second.mapNotNull { resolver.resolve(it) }.forEach { media ->
                    val index = _uiState.value.blocks.lastIndex
                    insertMediaBlock(index, media.id, MediaRepository.kindForType(media.type))
                }
                _uiState.update { it.copy(statusMessage = "Scene media requests resolved") }
            }
        }
        finishAiDraft()
    }

    /**
     * Retires an accepted draft. The prose now lives in the manuscript, so the inline
     * candidate panel must not keep showing a second copy of it.
     */
    private fun finishAiDraft() {
        _uiState.update {
            it.copy(aiOverlay = it.aiOverlay?.copy(hidden = true, streamingText = "", usageLog = ""))
        }
    }

    fun discardAiResult() {
        _uiState.update {
            it.copy(aiOverlay = it.aiOverlay?.copy(streamingText = "", usageLog = "", errorMessage = ""))
        }
    }

    fun retryAiGeneration() = runAiGeneration()

    fun updateMediaWidth(index: Int, widthPercent: Float) {
        updateBlocks(recordHistory = true) { blocks ->
            val next = WriteMediaOps.setWidthPercent(blocks.getOrNull(index) ?: return@updateBlocks, widthPercent)
            if (next != null) blocks[index] = next
        }
    }

    fun importImages(uris: List<Uri>) {
        if (uris.isEmpty()) {
            cancelImagePick()
            return
        }
        val blockIndex = _uiState.value.pickImageBlockIndex ?: return
        val targetSceneId = loadedScene?.id ?: return
        val anchorId = _uiState.value.blocks.getOrNull(blockIndex)?.id
        viewModelScope.launch {
            runCatching {
                val mediaList = mediaOps.importFromUris(uris)
                check(loadedScene?.id == targetSceneId) { "The scene changed during import. Imported files remain in your media library; insert them in the intended scene." }
                var index = if (anchorId == null) _uiState.value.blocks.lastIndex
                    else _uiState.value.blocks.indexOfFirst { it.id == anchorId }.also {
                        check(it >= 0) { "The insertion paragraph was removed. Imported files remain in your media library." }
                    }
                mediaList.forEach { media ->
                    insertMediaBlock(index, media.id, MediaRepository.kindForType(media.type))
                    index += 1
                }
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                _uiState.update { state -> state.copy(pickImageBlockIndex = null, statusMessage = error.message ?: "Media import failed. Your prose was kept.") }
            }
        }
    }

    fun cancelImagePick() {
        _uiState.update { it.copy(pickImageBlockIndex = null) }
    }

    fun requestAddMedia() {
        _uiState.update {
            it.copy(
                pickImageBlockIndex = it.selection.blockIndex,
                pickImageRequestId = it.pickImageRequestId + 1,
            )
        }
    }

    fun requestAddAudio() {
        _uiState.update {
            it.copy(
                pickImageBlockIndex = it.selection.blockIndex,
                pickAudioRequestId = it.pickAudioRequestId + 1,
            )
        }
    }

    private suspend fun insertMediaBlock(index: Int, mediaId: String, kind: MediaKind) {
        val sceneId = loadedScene?.id ?: return
        val anchor = _uiState.value.blocks.getOrNull(index)?.id
        val paths = mediaOps.resolvePaths(listOf(mediaId))
        val path = paths[mediaId] ?: return
        check(loadedScene?.id == sceneId) { "The scene changed before media could be placed. Your prose was kept." }
        val resolvedIndex = if (anchor == null) index else _uiState.value.blocks.indexOfFirst { it.id == anchor }
        check(anchor == null || resolvedIndex >= 0) { "The insertion paragraph was removed. Your prose was kept." }
        val block = WriteMediaOps.newMediaBlock(mediaId, kind)
        updateBlocksSync(recordHistory = true) { blocks ->
            val next = WriteMediaOps.insertAfter(blocks, resolvedIndex, block)
            blocks.clear(); blocks.addAll(next)
        }
        _uiState.update {
            it.copy(mediaPaths = it.mediaPaths + (mediaId to path), pickImageBlockIndex = null)
        }
    }

    private fun beginTypingHistory() {
        if (applyingHistory) return
        if (typingBaseline == null) {
            typingBaseline = _uiState.value.blocks.toList()
            workspaceHistory.addPendingUndo()
        }
    }

    private fun flushTypingHistory() {
        val baseline = typingBaseline ?: return
        typingBaseline = null
        workspaceHistory.removePendingUndo()
        val current = _uiState.value.blocks
        if (baseline != current) {
            recordDocumentEdit(baseline, current)
        }
    }

    private fun recordDocumentEdit(before: List<Block>, after: List<Block>) {
        if (applyingHistory) return
        val sceneId = loadedScene?.id ?: return
        if (before == after) return
        val beforeCopy = before.toList()
        val afterCopy = after.toList()
        workspaceHistory.record(
            undo = { restoreSceneBlocks(sceneId, beforeCopy) },
            redo = { restoreSceneBlocks(sceneId, afterCopy) },
        )
    }

    private suspend fun restoreSceneBlocks(sceneId: String, blocks: List<Block>) {
        applyingHistory = true
        try {
            // Queued behind keystroke persists so a pending write can never land after
            // the snapshot and resurrect newer text over it.
            val doc = Document(blocks)
            pendingDocuments[sceneId] = doc
            if (loadedScene?.id == sceneId) {
                _uiState.update { it.copy(blocks = blocks, wordCount = doc.wordCount(), saveStatus = "Saving…") }
            }
            persistQueue.send(PendingSceneWrite(sceneId, doc))
        } finally {
            applyingHistory = false
        }
    }

    private fun updateBlocks(recordHistory: Boolean = false, mutator: (MutableList<Block>) -> Unit) {
        if (recordHistory) flushTypingHistory()
        var before: List<Block> = emptyList()
        var after: List<Block> = emptyList()
        var nextDoc: Document? = null
        _uiState.update { state ->
            before = state.blocks.toList()
            val blocks = state.blocks.toMutableList()
            mutator(blocks)
            after = blocks.toList()
            val doc = Document(blocks)
            nextDoc = doc
            state.copy(
                blocks = blocks,
                wordCount = doc.wordCount(),
                canUndo = workspaceHistory.state.value.canUndo,
                canRedo = workspaceHistory.state.value.canRedo,
            )
        }
        nextDoc?.let { persistScene(it) }
        if (recordHistory) recordDocumentEdit(before, after)
        _uiState.update {
            it.copy(
                canUndo = workspaceHistory.state.value.canUndo,
                canRedo = workspaceHistory.state.value.canRedo,
            )
        }
    }

    private fun updateBlocksSync(recordHistory: Boolean = false, mutator: (MutableList<Block>) -> Unit) {
        updateBlocks(recordHistory, mutator)
    }

    private fun persistScene(doc: Document) {
        val sceneId = loadedScene?.id ?: return
        pendingDocuments[sceneId] = doc
        _uiState.update { it.copy(saveStatus = "Saving…") }
        persistQueue.trySend(PendingSceneWrite(sceneId, doc))
    }

    /**
     * Single consumer persists scene writes strictly in order. Keystrokes arrive faster
     * than Room writes; out-of-order writes would let a stale document win on disk.
     */
    private suspend fun drainPersistQueue() {
        for (write in persistQueue) {
            try {
                val updated = db.withTransaction {
                    val base = documentOps.getScene(write.sceneId) ?: return@withTransaction null
                    documentOps.persist(base, write.doc, nextWriteStamp())
                } ?: continue
                if (pendingDocuments[write.sceneId] == write.doc) pendingDocuments.remove(write.sceneId)
                if (loadedScene?.id == write.sceneId) {
                    loadedScene = updated
                    lastPersistedDocJson = updated.docJson
                    _uiState.update { it.copy(saveStatus = if (it.blocks == write.doc.blocks) "Saved" else "Saving…") }
                    refreshContextMeter()
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(saveStatus = "Save failed", statusMessage = "Could not save the scene. Keep this editor open and retry your edit.") }
            }
        }
    }

    fun insertExistingMedia(mediaId: String) {
        val sceneId = loadedScene?.id ?: return
        val anchor = _uiState.value.blocks.getOrNull(_uiState.value.selection.blockIndex)?.id
        viewModelScope.launch {
            val asset = db.mediaDao().observeAll().first().firstOrNull { it.id == mediaId } ?: return@launch
            if (loadedScene?.id != sceneId) return@launch
            val index = _uiState.value.blocks.indexOfFirst { it.id == anchor }.takeIf { it >= 0 } ?: _uiState.value.blocks.lastIndex
            insertMediaBlock(index, mediaId, MediaRepository.kindForType(asset.type))
        }
    }

    private fun nextWriteStamp(): Long = writeStamps.next()

    private fun recomputeFindMatches() {
        val next = documentOps.recomputeFind(_uiState.value.blocks, _uiState.value.findReplace)
        _uiState.update { it.copy(findReplace = next) }
        next.matches.getOrNull(next.matchIndex)?.let { hit ->
            _uiState.update { it.copy(selection = documentOps.selectionFor(hit)) }
        }
    }

    private fun stepFind(delta: Int) {
        val next = documentOps.stepFind(_uiState.value.findReplace, delta)
        _uiState.update { it.copy(findReplace = next) }
        next.matches.getOrNull(next.matchIndex)?.let { hit ->
            _uiState.update { it.copy(selection = documentOps.selectionFor(hit)) }
        }
    }

    private fun refreshContextMeter() {
        val state = _uiState.value
        val sceneText = Document(state.blocks).plainText()
        val extra = state.aiOverlay?.prompt.orEmpty()
        val reading = writeGeneration.meter(sceneText, extra, contextLimit)
        _uiState.update { current ->
            current.copy(
                contextMeter = reading,
                aiOverlay = current.aiOverlay?.copy(contextMeter = reading),
            )
        }
    }

    override fun onCleared() {
        if (typingBaseline != null) {
            typingBaseline = null
            workspaceHistory.removePendingUndo()
        }
        unregisterHistoryFlush()
        super.onCleared()
    }
}
