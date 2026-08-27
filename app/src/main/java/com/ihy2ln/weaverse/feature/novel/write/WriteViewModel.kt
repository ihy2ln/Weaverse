package com.ihy2ln.weaverse.feature.novel.write

import android.net.Uri
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.ai.AIChunk
import com.ihy2ln.weaverse.ai.AiGenerationService
import com.ihy2ln.weaverse.ai.context.ContextMeter
import com.ihy2ln.weaverse.ai.openrouter.OpenRouterModelCache
import com.ihy2ln.weaverse.ai.prompt.PromptRenderContext
import com.ihy2ln.weaverse.core.media.MediaRepository
import com.ihy2ln.weaverse.core.text.Block
import com.ihy2ln.weaverse.core.text.Document
import com.ihy2ln.weaverse.core.text.Mark
import com.ihy2ln.weaverse.core.text.MediaKind
import com.ihy2ln.weaverse.core.text.MediaStackBlock
import com.ihy2ln.weaverse.core.text.Paragraph
import com.ihy2ln.weaverse.core.text.SceneBeatBlock
import com.ihy2ln.weaverse.core.text.Span
import com.ihy2ln.weaverse.core.text.insertMediaAfter
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
import com.ihy2ln.weaverse.feature.shell.WorkspaceHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class WriteViewModel @Inject constructor(
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
) : ViewModel() {
    private val _uiState = MutableStateFlow(WriteUiState())
    val uiState: StateFlow<WriteUiState> = _uiState.asStateFlow()
    private val json = Json { ignoreUnknownKeys = true }
    private var bookId: String = "book-adams-haven-1"

    private var loadedScene: SceneEntity? = null
    private var sceneJob: Job? = null
    private var generationJob: Job? = null
    private var applyingHistory = false
    /** Snapshot taken at the start of a typing burst; flushed before discrete edits. */
    private var typingBaseline: List<Block>? = null
    private val unregisterHistoryFlush = workspaceHistory.registerPreUndo { flushTypingHistory() }
    private var contextLimit = ContextMeter.DEFAULT_LIMIT
    private var revisionJob: Job? = null

    init {
        viewModelScope.launch {
            settings.preferences.collect { prefs ->
                bookId = prefs.selectedBookId
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
    }

    private var pendingJumpKind: String = "Scene"

    fun loadScene(sceneId: String, jumpKind: String = "Scene") {
        pendingJumpKind = jumpKind
        if (loadedScene?.id == sceneId && sceneJob?.isActive == true) {
            if (jumpKind == "SceneBeat") startSceneBeatFromPlan()
            return
        }
        sceneJob?.cancel()
        revisionJob?.cancel()
        if (typingBaseline != null) {
            typingBaseline = null
            workspaceHistory.removePendingUndo()
        }
        sceneJob = viewModelScope.launch {
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

    fun startSceneBeatFromPlan() {
        updateBlocksSync(recordHistory = true) { blocks ->
            val last = blocks.lastOrNull() as? SceneBeatBlock
            if (last != null && last.prompt.isBlank()) return@updateBlocksSync
            val next = blocks.appendSceneBeat()
            blocks.clear()
            blocks.addAll(next)
        }
    }

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
                        commandId = "scene_beat",
                        label = "SCENE BEAT",
                        insertAfterIndex = index,
                        prompt = beat.prompt,
                        systemInstructions = library.systemInstructions,
                        promptId = library.promptId,
                    ),
                )
            }
            runAiGeneration()
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
        loadedScene = scene
        val doc = documentFromJson(scene.docJson)
        val blocks = doc.blocks.ifEmpty { listOf(Paragraph("new-p", listOf(Span("")))) }
        viewModelScope.launch {
            val mediaIds = blocks.flatMap { WriteMediaOps.mediaIdsOf(it) }
            val paths = mediaOps.resolvePaths(mediaIds)
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
        promptEntryBus.requestOpen(kind)
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
        viewModelScope.launch {
            documentOps.snapshotNow(scene, kind = "manual")
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
            MediaEditAction.AdjustImage, MediaEditAction.AddTextOverlay -> Unit
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
        val sel = _uiState.value.selection
        val block = _uiState.value.blocks.getOrNull(sel.blockIndex) as? Paragraph
        val sceneText = Document(_uiState.value.blocks).plainText()
        val selected = selectedText().ifBlank { sceneText }
        viewModelScope.launch {
            val library = promptAssembler.libraryPromptBundle(commandId, PromptRenderContext())
            val replaceInPlace = sel.hasSelection && block != null
            _uiState.update {
                it.copy(
                    editPopupBlockIndex = null,
                    aiOverlay = AiOverlayState(
                        commandId = commandId,
                        label = label.uppercase(),
                        insertAfterIndex = sel.blockIndex,
                        prompt = "",
                        systemInstructions = buildString {
                            append(library.systemInstructions)
                            if (selected.isNotBlank()) {
                                append("\n\nPassage:\n")
                                append(selected)
                            }
                        },
                        promptId = library.promptId,
                        outputWords = when (commandId) {
                            "shorten" -> 300
                            else -> 750
                        },
                        replaceBlockIndex = if (replaceInPlace) sel.blockIndex else null,
                        replaceStart = if (replaceInPlace) sel.min else null,
                        replaceEnd = if (replaceInPlace) sel.max else null,
                    ),
                )
            }
        }
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
        promptEntryBus.requestOpen(PromptEntryKind.Ai)
        _uiState.update { it.copy(slashBlockIndex = null, slashFilter = "") }
    }

    fun onBackslashTrigger(index: Int) {
        promptEntryBus.requestOpen(PromptEntryKind.Manual)
        _uiState.update { it.copy(slashBlockIndex = null, slashFilter = "", statusMessage = "") }
    }

    fun dismissSlash() {
        _uiState.update { it.copy(slashBlockIndex = null, slashFilter = "") }
    }

    fun applySlashCommand(command: SlashCommand) {
        val index = _uiState.value.slashBlockIndex ?: return
        viewModelScope.launch {
            when (command.id) {
                "image" -> {
                    _uiState.update {
                        it.copy(
                            pickImageBlockIndex = index,
                            pickImageRequestId = it.pickImageRequestId + 1,
                            slashBlockIndex = null,
                            slashFilter = "",
                        )
                    }
                    return@launch
                }
                "video" -> {
                    val media = mediaOps.registerPlaceholderImage()
                    val path = mediaOps.resolveFile(media).absolutePath
                    val block = WriteMediaOps.newMediaBlock(media.id, MediaKind.Video)
                    updateBlocksSync(recordHistory = true) { blocks ->
                        val next = blocks.insertMediaAfter(index, block)
                        blocks.clear()
                        blocks.addAll(next)
                    }
                    _uiState.update { it.copy(mediaPaths = it.mediaPaths + (media.id to path)) }
                }
                "scene_beat" -> {
                    updateBlocksSync(recordHistory = true) { blocks ->
                        if (blocks[index] is Paragraph) {
                            blocks[index] = Paragraph(blocks[index].id, listOf(Span("")))
                        }
                        blocks.add(
                            index + 1,
                            SceneBeatBlock(
                                id = UUID.randomUUID().toString(),
                                prompt = "",
                            ),
                        )
                    }
                    _uiState.update { it.copy(slashBlockIndex = null, slashFilter = "") }
                    return@launch
                }
                "continue", "expand", "shorten", "extend", "replace" -> {
                    updateBlocksSync(recordHistory = true) { blocks ->
                        if (blocks[index] is Paragraph) {
                            blocks[index] = Paragraph(blocks[index].id, listOf(Span("")))
                        }
                    }
                    val library = promptAssembler.libraryPromptBundle(command.id, PromptRenderContext())
                    _uiState.update {
                        it.copy(
                            aiOverlay = AiOverlayState(
                                commandId = command.id,
                                label = command.label.uppercase(),
                                insertAfterIndex = index,
                                prompt = "",
                                systemInstructions = library.systemInstructions,
                                promptId = library.promptId,
                            ),
                            slashBlockIndex = null,
                            slashFilter = "",
                        )
                    }
                    return@launch
                }
                else -> updateBlocksSync(recordHistory = true) { blocks ->
                    if (blocks[index] is Paragraph) {
                        blocks[index] = Paragraph(blocks[index].id, listOf(Span("")))
                    }
                }
            }
            dismissSlash()
        }
    }

    fun updateAiPrompt(value: String) {
        _uiState.update { state ->
            state.copy(aiOverlay = state.aiOverlay?.copy(prompt = value, errorMessage = ""))
        }
        val overlay = _uiState.value.aiOverlay ?: return
        if (overlay.commandId == "scene_beat") {
            updateBlocks(recordHistory = false) { blocks ->
                val next = blocks.withSceneBeatPrompt(overlay.insertAfterIndex, value)
                blocks.clear()
                blocks.addAll(next)
            }
        }
        refreshContextMeter()
    }

    fun updateOutputWords(words: Int) {
        _uiState.update { state ->
            state.copy(aiOverlay = state.aiOverlay?.copy(outputWords = words.coerceIn(50, 4000)))
        }
    }

    fun requestBeatImage() {
        _uiState.update { state ->
            val overlay = state.aiOverlay ?: return@update state
            state.copy(
                aiOverlay = overlay.copy(pickBeatImageRequestId = overlay.pickBeatImageRequestId + 1),
            )
        }
    }

    fun attachBeatImage(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val media = mediaOps.importFromUri(uri)
                val path = mediaOps.resolveFile(media).absolutePath
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
        generationJob?.cancel()
        _uiState.update { it.copy(aiOverlay = null) }
    }

    fun runAiGeneration() {
        val overlay = _uiState.value.aiOverlay ?: return
        if (overlay.isStreaming) return
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            val prep = writeGeneration.prepareStream(
                overlay = overlay,
                sceneText = Document(_uiState.value.blocks).plainText(),
                scene = loadedScene,
                bookId = bookId,
                hasApiKey = aiGeneration.hasApiKey(),
                modelSupportsImages = aiGeneration.modelSupportsImages(),
            )
            val plan = when (prep) {
                is WriteGenerationPrep.Failed -> {
                    _uiState.update {
                        it.copy(aiOverlay = it.aiOverlay?.copy(errorMessage = prep.message))
                    }
                    return@launch
                }
                is WriteGenerationPrep.Ready -> prep.plan
            }
            _uiState.update {
                it.copy(
                    aiOverlay = plan.overlay.copy(
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
                    userMessage = plan.userMessage,
                    assembled = plan.assembled,
                    maxTokens = plan.maxTokens,
                    imageAttachments = plan.imageAttachments,
                ).collect { chunk ->
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
                val updated = scene.copy(summary = summary, updatedAt = System.currentTimeMillis())
                documentOps.saveScene(updated)
                loadedScene = updated
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
        val text = overlay.streamingText.trim()
        if (text.isBlank()) {
            dismissAiOverlay()
            return
        }
        flushTypingHistory()
        updateBlocksSync(recordHistory = true) { blocks ->
            val next = writeGeneration.acceptIntoBlocks(blocks, overlay, text)
            blocks.clear()
            blocks.addAll(next)
        }
        dismissAiOverlay()
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
        viewModelScope.launch {
            runCatching {
                val mediaList = mediaOps.importFromUris(uris)
                if (blockIndex < 0) {
                    var index = (_uiState.value.blocks.size - 1).coerceAtLeast(0)
                    mediaList.forEach { media ->
                        val kind = MediaRepository.kindForType(media.type)
                        insertMediaBlock(index, media.id, kind)
                        index += 1
                    }
                } else {
                    var index = blockIndex
                    mediaList.forEach { media ->
                        val kind = MediaRepository.kindForType(media.type)
                        insertMediaBlock(index, media.id, kind)
                        index += 1
                    }
                }
            }.onFailure {
                _uiState.update { state -> state.copy(pickImageBlockIndex = null) }
            }
        }
    }

    fun cancelImagePick() {
        _uiState.update { it.copy(pickImageBlockIndex = null) }
    }

    fun requestAddMedia() {
        _uiState.update {
            it.copy(
                pickImageBlockIndex = -1,
                pickImageRequestId = it.pickImageRequestId + 1,
            )
        }
    }

    fun requestAddAudio() {
        _uiState.update {
            it.copy(
                pickImageBlockIndex = -1,
                pickAudioRequestId = it.pickAudioRequestId + 1,
            )
        }
    }

    private suspend fun insertMediaBlock(index: Int, mediaId: String, kind: MediaKind) {
        val paths = mediaOps.resolvePaths(listOf(mediaId))
        val path = paths[mediaId] ?: return
        val block = WriteMediaOps.newMediaBlock(mediaId, kind)
        updateBlocksSync(recordHistory = true) { blocks ->
            val next = blocks.insertMediaAfter(index, block)
            blocks.clear()
            blocks.addAll(next)
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
            documentOps.restoreBlocks(sceneId, blocks)
        } finally {
            applyingHistory = false
        }
    }

    private fun updateBlocks(recordHistory: Boolean = false, mutator: (MutableList<Block>) -> Unit) {
        if (recordHistory) flushTypingHistory()
        var before: List<Block> = emptyList()
        var after: List<Block> = emptyList()
        _uiState.update { state ->
            before = state.blocks.toList()
            val blocks = state.blocks.toMutableList()
            mutator(blocks)
            after = blocks.toList()
            val doc = Document(blocks)
            persistScene(doc)
            state.copy(
                blocks = blocks,
                wordCount = doc.wordCount(),
                canUndo = workspaceHistory.state.value.canUndo,
                canRedo = workspaceHistory.state.value.canRedo,
            )
        }
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
        viewModelScope.launch {
            val base = documentOps.getScene(sceneId) ?: return@launch
            val saved = documentOps.persist(base, doc)
            if (loadedScene?.id == sceneId) {
                loadedScene = saved
                refreshContextMeter()
            }
        }
    }

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
