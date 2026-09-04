package com.ihy2ln.weaverse.feature.novel.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.ai.AIChunk
import com.ihy2ln.weaverse.ai.AIError
import com.ihy2ln.weaverse.ai.AiGenerationService
import com.ihy2ln.weaverse.ai.context.AssembledPrompt
import com.ihy2ln.weaverse.ai.context.ContextBuilder
import com.ihy2ln.weaverse.ai.context.ContextBuildRequest
import com.ihy2ln.weaverse.ai.context.ContextChip
import com.ihy2ln.weaverse.ai.context.ContextMeter
import com.ihy2ln.weaverse.ai.context.ContextMeterReading
import com.ihy2ln.weaverse.ai.openrouter.OpenRouterModelCache
import com.ihy2ln.weaverse.ai.openrouter.WritingModelSeeds
import com.ihy2ln.weaverse.ai.prompt.PromptComponents
import com.ihy2ln.weaverse.ai.prompt.PromptRenderContext
import com.ihy2ln.weaverse.ai.prompt.PromptRenderer
import com.ihy2ln.weaverse.data.repo.PromptRepository
import com.ihy2ln.weaverse.core.text.Document
import com.ihy2ln.weaverse.core.text.documentFromJson
import com.ihy2ln.weaverse.core.text.plainText
import com.ihy2ln.weaverse.core.text.toJson
import com.ihy2ln.weaverse.core.ui.util.UsageFormat
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.ChatMessageEntity
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import com.ihy2ln.weaverse.feature.prompt.PromptModelSelection
import com.ihy2ln.weaverse.feature.prompt.PromptWordLimit
import com.ihy2ln.weaverse.feature.shell.WorkspaceHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatMessageUi(
    val id: String,
    val role: String,
    val plainText: String,
    val usageText: String = "",
    val mediaPaths: List<String> = emptyList(),
)

data class WorkshopChatUiState(
    val threadId: String = "thread-1",
    val modelRef: String = "openrouter/deepseek/deepseek-v4-flash",
    val writingModels: List<com.ihy2ln.weaverse.ai.ModelInfo> = emptyList(),
    val defaultModelRef: String = WritingModelSeeds.DEFAULT_MODEL_REF,
    val input: String = "",
    val messages: List<ChatMessageUi> = emptyList(),
    val contextChips: List<ContextChip> = emptyList(),
    val previewPrompt: String = "",
    val showPreview: Boolean = false,
    val streamingText: String = "",
    val isStreaming: Boolean = false,
    val errorMessage: String = "",
    val lastUsage: String = "",
    val contextMeter: ContextMeterReading? = null,
    val showCodexPicker: Boolean = false,
    val codexEntries: List<CodexPickerEntry> = emptyList(),
    val showExtraPromptSurfaces: Boolean = false,
    val minimumOutputWords: Int = 50,
    val maximumOutputWords: Int = 200,
    /** >0 asks the screen to open the media picker (+ button in the dock). */
    val mediaPickRequestId: Long = 0,
    /** Shows when the + button has staged media for the next message. */
    val hasPendingMedia: Boolean = false,
)

data class CodexPickerEntry(
    val id: String,
    val name: String,
    val colorHex: String?,
    val included: Boolean = false,
)

@HiltViewModel
class WorkshopChatViewModel @Inject constructor(
    private val db: WeaverseDatabase,
    private val aiGeneration: AiGenerationService,
    private val settings: SettingsRepository,
    private val promptRepository: PromptRepository,
    private val workspaceHistory: WorkspaceHistory,
    private val modelCache: OpenRouterModelCache,
    private val mediaRepository: com.ihy2ln.weaverse.core.media.MediaRepository,
) : ViewModel() {
    private val contextBuilder = ContextBuilder()
    private var bookId = "book-adams-haven-1"
    private val _uiState = MutableStateFlow(WorkshopChatUiState())
    val uiState: StateFlow<WorkshopChatUiState> = _uiState.asStateFlow()
    private var observeJob: Job? = null
    private var generateJob: Job? = null
    private var assembledPrompt: com.ihy2ln.weaverse.ai.context.AssembledPrompt? = null
    private var contextLimit = ContextMeter.DEFAULT_LIMIT
    /** Media attached via the dock's + button, sent with the next message. */
    private var pendingMedia: List<com.ihy2ln.weaverse.data.db.entities.MediaEntity> = emptyList()

    init {
        viewModelScope.launch {
            settings.preferences.collect { prefs ->
                bookId = prefs.selectedBookId
                _uiState.update { it.copy(showExtraPromptSurfaces = prefs.extraPromptSurfaces.chatComposer) }
            }
        }
        viewModelScope.launch {
            combine(settings.preferences, modelCache.models) { prefs, dtos ->
                ContextMeter.limitFor(prefs.defaultModelRef, modelCache.toModelInfo(dtos))
            }.collect { limit ->
                contextLimit = limit
                refreshContext(_uiState.value.input)
            }
        }
        viewModelScope.launch {
            combine(settings.preferences, modelCache.models) { prefs, dtos ->
                prefs.defaultModelRef to modelCache.toModelInfo(dtos)
            }.collect { (defaultRef, models) ->
                _uiState.update { it.copy(defaultModelRef = defaultRef, writingModels = models) }
            }
        }
        selectThread("thread-1")
    }

    fun selectModel(modelId: String) {
        _uiState.update { it.copy(modelRef = PromptModelSelection.modelRef(modelId)) }
        persistThreadModel()
    }

    fun useDefaultModel() {
        _uiState.update { it.copy(modelRef = _uiState.value.defaultModelRef) }
        persistThreadModel()
    }

    private fun persistThreadModel() {
        val state = _uiState.value
        viewModelScope.launch {
            db.workshopChatDao().observeThreads(bookId).first()
                .find { it.id == state.threadId }
                ?.let { thread ->
                    db.workshopChatDao().upsertThread(thread.copy(modelRef = state.modelRef, updatedAt = System.currentTimeMillis()))
                }
        }
    }

    fun selectThread(threadId: String) {
        if (_uiState.value.threadId == threadId && observeJob?.isActive == true) return
        _uiState.update { it.copy(threadId = threadId) }
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            val thread = db.workshopChatDao().observeThreads(bookId).first().find { it.id == threadId }
            if (thread != null) {
                _uiState.update {
                    it.copy(modelRef = thread.modelRef.ifBlank { "openrouter/deepseek/deepseek-v4-flash" })
                }
            }
            db.workshopChatDao().observeMessages(threadId).collect { messages ->
                val mapped = messages.map { m ->
                    ChatMessageUi(
                        m.id,
                        m.role,
                        documentFromJson(m.contentJson).plainText(),
                        usageText = if (m.role != "user" && (m.promptTokens > 0 || m.completionTokens > 0 || m.costUsd > 0.0)) {
                            UsageFormat.formatUsage(m.promptTokens, m.completionTokens, null, m.costUsd.takeIf { it > 0.0 })
                        } else {
                            ""
                        },
                        mediaPaths = mediaPathsOf(m),
                    )
                }
                _uiState.update { it.copy(messages = mapped) }
            }
        }
    }

    /** Resolvable image paths from a message's media blocks, for inline display. */
    private suspend fun mediaPathsOf(message: ChatMessageEntity): List<String> =
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

    fun onInputChange(value: String) {
        _uiState.update { it.copy(input = value, errorMessage = "") }
        refreshContext(value)
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

    /** + dock button: attach pictures to the next message. */
    fun requestMediaPick() {
        _uiState.update { it.copy(mediaPickRequestId = it.mediaPickRequestId + 1) }
    }

    fun attachMedia(uris: List<android.net.Uri>) {
        viewModelScope.launch {
            pendingMedia = runCatching { mediaRepository.importFromUris(uris) }.getOrDefault(emptyList())
            _uiState.update { it.copy(hasPendingMedia = pendingMedia.isNotEmpty()) }
        }
    }

    /** Outgoing draft as a document: text paragraph plus any media blocks. */
    private fun userMessageDocument(text: String): Document = Document(
        blocks = buildList {
            if (text.isNotBlank()) {
                add(com.ihy2ln.weaverse.core.text.Paragraph("p-${System.currentTimeMillis()}", listOf(com.ihy2ln.weaverse.core.text.Span(text))))
            }
            pendingMedia.forEach { item ->
                add(
                    com.ihy2ln.weaverse.core.text.MediaBlock(
                        id = "mb-${java.util.UUID.randomUUID()}",
                        mediaId = item.id,
                        kind = com.ihy2ln.weaverse.core.media.MediaRepository.kindForType(item.type),
                    ),
                )
            }
        },
    )

    /** » hold-menu action: keep the answer going without a new prompt. */
    fun continuePrompt() {
        val state = _uiState.value
        if (state.isStreaming) return
        generateJob?.cancel()
        generateJob = viewModelScope.launch {
            if (!aiGeneration.hasApiKey()) {
                _uiState.update { it.copy(errorMessage = AIError.NoApiKey().message.orEmpty()) }
                return@launch
            }
            _uiState.update { it.copy(isStreaming = true, streamingText = "", errorMessage = "") }
            val builder = StringBuilder()
            runCatching {
                aiGeneration.stream(
                    userMessage = "Continue.",
                    assembled = assembledPrompt,
                    modelRef = state.modelRef,
                ).collect { chunk ->
                    if (chunk is AIChunk.Delta) {
                        builder.append(chunk.text)
                        _uiState.update { it.copy(streamingText = builder.toString()) }
                    }
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isStreaming = false, streamingText = "", errorMessage = formatError(err)) }
                return@launch
            }
            val reply = ChatMessageEntity(
                id = "msg-${System.currentTimeMillis()}",
                threadId = state.threadId,
                role = "assistant",
                contentJson = Document.fromPlainText(builder.toString()).toJson(),
                createdAt = System.currentTimeMillis(),
            )
            db.workshopChatDao().upsertMessage(reply)
            _uiState.update { it.copy(isStreaming = false, streamingText = "") }
        }
    }

    private var lastClearedInput: String = ""

    /** ⌫ tap: delete the draft entry (stashed so hold can undo it). */
    fun clearInput() {
        lastClearedInput = _uiState.value.input
        onInputChange("")
    }

    /** ⌫ press-and-hold: restore the last deleted draft. */
    fun undoClearInput() {
        if (lastClearedInput.isBlank()) return
        val restored = lastClearedInput
        lastClearedInput = ""
        onInputChange(restored)
    }

    private val excludedEntryIds = mutableSetOf<String>()
    private val manualIncludeIds = mutableSetOf<String>()

    fun removeChip(entryId: String) {
        excludedEntryIds.add(entryId)
        manualIncludeIds.remove(entryId)
        refreshContext(_uiState.value.input)
    }

    fun openCodexPicker() {
        viewModelScope.launch {
            val entries = db.codexDao().observeEntries(bookId).first()
            _uiState.update {
                it.copy(
                    showCodexPicker = true,
                    codexEntries = entries.filter { e -> !e.disabled }.map { e ->
                        CodexPickerEntry(
                            id = e.id,
                            name = e.name,
                            colorHex = e.colorHex,
                            included = e.id in manualIncludeIds ||
                                _uiState.value.contextChips.any { chip -> chip.entryId == e.id },
                        )
                    },
                )
            }
        }
    }

    fun dismissCodexPicker() = _uiState.update { it.copy(showCodexPicker = false) }

    fun addCodexEntry(entryId: String) {
        manualIncludeIds.add(entryId)
        excludedEntryIds.remove(entryId)
        _uiState.update { state ->
            state.copy(
                codexEntries = state.codexEntries.map {
                    if (it.id == entryId) it.copy(included = true) else it
                },
            )
        }
        refreshContext(_uiState.value.input)
    }

    fun toggleCodexEntry(entryId: String, include: Boolean) {
        if (include) {
            manualIncludeIds.add(entryId)
            excludedEntryIds.remove(entryId)
        } else {
            manualIncludeIds.remove(entryId)
            excludedEntryIds.add(entryId)
        }
        _uiState.update { state ->
            state.copy(
                codexEntries = state.codexEntries.map {
                    if (it.id == entryId) it.copy(included = include) else it
                },
            )
        }
        refreshContext(_uiState.value.input)
    }

    fun togglePreview() = _uiState.update { it.copy(showPreview = !it.showPreview) }

    fun updateOutputWords(words: Int) {
        if (words !in PromptWordLimit.Minimum..PromptWordLimit.Maximum) return
        _uiState.update { it.copy(maximumOutputWords = words) }
    }

    fun updateMinimumOutputWords(words: Int) {
        if (words !in PromptWordLimit.Minimum..PromptWordLimit.Maximum) return
        _uiState.update { it.copy(minimumOutputWords = words) }
    }

    /** ↻: delete the latest assistant reply and regenerate from the last user message. */
    fun retry() {
        val state = _uiState.value
        if (state.isStreaming) return
        generateJob?.cancel()
        generateJob = viewModelScope.launch {
            val messages = db.workshopChatDao().getMessages(state.threadId)
            val lastReply = messages.lastOrNull { it.role != "user" } ?: return@launch
            val lastUser = messages.lastOrNull { it.role == "user" } ?: return@launch
            db.workshopChatDao().deleteMessage(lastReply.id)
            _uiState.update { it.copy(isStreaming = true, streamingText = "", errorMessage = "") }
            val userText = documentFromJson(lastUser.contentJson).plainText().trim()
            val builder = StringBuilder()
            runCatching {
                aiGeneration.stream(
                    userMessage = userText,
                    assembled = assembledPrompt,
                    modelRef = state.modelRef,
                ).collect { chunk ->
                    if (chunk is AIChunk.Delta) {
                        builder.append(chunk.text)
                        _uiState.update { it.copy(streamingText = builder.toString()) }
                    }
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isStreaming = false, streamingText = "", errorMessage = formatError(err)) }
                return@launch
            }
            val reply = ChatMessageEntity(
                id = "msg-${System.currentTimeMillis()}",
                threadId = state.threadId,
                role = "assistant",
                contentJson = Document.fromPlainText(builder.toString()).toJson(),
                createdAt = System.currentTimeMillis(),
            )
            db.workshopChatDao().upsertMessage(reply)
            _uiState.update { it.copy(isStreaming = false, streamingText = "") }
        }
    }

    fun send() {
        val state = _uiState.value
        if ((state.input.isBlank() && pendingMedia.isEmpty()) || state.isStreaming) return
        generateJob?.cancel()
        generateJob = viewModelScope.launch {
            if (!aiGeneration.hasApiKey()) {
                _uiState.update { it.copy(errorMessage = AIError.NoApiKey().message.orEmpty()) }
                return@launch
            }
            val now = System.currentTimeMillis()
            val userText = state.input
            val userMessage = ChatMessageEntity(
                id = "msg-$now",
                threadId = state.threadId,
                role = "user",
                contentJson = userMessageDocument(userText).toJson(),
                createdAt = now,
            )
            db.workshopChatDao().upsertMessage(userMessage)
            pendingMedia = emptyList()
            _uiState.update { it.copy(hasPendingMedia = false) }
            _uiState.update { it.copy(input = "", isStreaming = true, streamingText = "", errorMessage = "") }
            val builder = StringBuilder()
            var usageText = ""
            var promptTokens = 0
            var completionTokens = 0
            var costUsd = 0.0
            runCatching {
                aiGeneration.stream(
                    userMessage = userText,
                    assembled = assembledPrompt,
                    modelRef = state.modelRef,
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
                    undo = { db.workshopChatDao().deleteMessage(userMessage.id) },
                    redo = { db.workshopChatDao().upsertMessage(userMessage) },
                )
                _uiState.update {
                    it.copy(
                        isStreaming = false,
                        streamingText = "",
                        errorMessage = formatError(err),
                    )
                }
                return@launch
            }
            val assistantMessage = ChatMessageEntity(
                id = "msg-${now + 1}",
                threadId = state.threadId,
                role = "assistant",
                contentJson = Document.fromPlainText(builder.toString()).toJson(),
                createdAt = now + 1,
                promptTokens = promptTokens,
                completionTokens = completionTokens,
                costUsd = costUsd,
            )
            db.workshopChatDao().upsertMessage(assistantMessage)
            val added = listOf(userMessage, assistantMessage)
            workspaceHistory.record(
                undo = { added.forEach { db.workshopChatDao().deleteMessage(it.id) } },
                redo = { added.forEach { db.workshopChatDao().upsertMessage(it) } },
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

    private fun refreshContext(input: String) {
        viewModelScope.launch {
            val entries = db.codexDao().getAllEntries()
            val book = db.bookDao().getById(bookId)
            val series = book?.seriesId?.let { id -> db.seriesDao().observeById(id).first() }
            val assembled = contextBuilder.build(
                entries,
                ContextBuildRequest(
                    scanText = input + " WAHB WAH WAHO AFM Gender Ratio GKOM Celestium",
                    userMessage = input,
                    manualIncludeIds = manualIncludeIds,
                    manualExcludeIds = excludedEntryIds,
                ),
            )
            val renderCtx = PromptRenderContext(
                novelTense = book?.tense?.ifBlank { "past tense" } ?: "past tense",
                novelTitle = book?.title.orEmpty(),
                seriesTitle = series?.title.orEmpty(),
                seriesDescription = listOfNotNull(
                    series?.description?.takeIf { it.isNotBlank() },
                    series?.premise?.takeIf { it.isNotBlank() },
                ).joinToString("\n"),
                message = input,
                componentBlocks = PromptComponents.build(promptRepository, assembled.codexBlock, book),
            )
            val workshop = promptRepository.observeByType("workshop_chat").first()
                .let { prompts -> prompts.firstOrNull { it.isDefault } ?: prompts.firstOrNull() }
            val workshopSystemText = PromptRenderer.render(workshop, renderCtx).systemText
            val withWorkshop = AssembledPrompt(
                systemBlocks = listOfNotNull(workshopSystemText.takeIf { it.isNotBlank() }),
                messages = assembled.messages,
                usedEntries = assembled.usedEntries,
                tokenBreakdown = assembled.tokenBreakdown,
                droppedEntryIds = assembled.droppedEntryIds,
            )
            assembledPrompt = withWorkshop
            val chips = withWorkshop.usedEntries.filter { it.entryId !in excludedEntryIds }
            val meter = ContextMeter.reading(withWorkshop, extraUser = input, limitTokens = contextLimit)
            _uiState.update {
                it.copy(
                    contextChips = chips,
                    previewPrompt = withWorkshop.systemBlocks.joinToString("\n\n"),
                    contextMeter = meter,
                )
            }
        }
    }

    private fun formatError(err: Throwable): String = when (err) {
        is AIError.HttpFailure -> "HTTP ${err.statusCode}: ${err.message}"
        is AIError -> err.message
        else -> err.message ?: err.toString()
    }.orEmpty()
}
