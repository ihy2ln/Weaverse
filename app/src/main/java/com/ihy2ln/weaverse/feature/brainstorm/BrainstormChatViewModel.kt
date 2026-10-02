package com.ihy2ln.weaverse.feature.brainstorm

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.ai.AIChunk
import com.ihy2ln.weaverse.ai.AIError
import com.ihy2ln.weaverse.ai.AiGenerationService
import com.ihy2ln.weaverse.ai.ImageAttachment
import com.ihy2ln.weaverse.ai.context.AssembledPrompt
import com.ihy2ln.weaverse.ai.context.ContextBuilder
import com.ihy2ln.weaverse.ai.context.ContextBuildRequest
import com.ihy2ln.weaverse.ai.context.ContextChip
import com.ihy2ln.weaverse.ai.context.ContextMeter
import com.ihy2ln.weaverse.ai.context.ContextMeterReading
import com.ihy2ln.weaverse.ai.openrouter.OpenRouterModelCache
import com.ihy2ln.weaverse.core.text.Document
import com.ihy2ln.weaverse.core.text.documentFromJson
import com.ihy2ln.weaverse.core.text.plainText
import com.ihy2ln.weaverse.core.text.toJson
import com.ihy2ln.weaverse.core.ui.util.UsageFormat
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.ChatMessageEntity
import com.ihy2ln.weaverse.data.db.entities.ChatThreadEntity
import com.ihy2ln.weaverse.data.db.entities.BrainstormIdeaEntity
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import com.ihy2ln.weaverse.feature.prompt.PromptModelSelection
import com.ihy2ln.weaverse.feature.prompt.PromptWordLimit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** One brainstorm message row. */
data class BrainstormMessageUi(
    val id: String,
    val role: String,
    val text: String,
    val usageText: String = "",
    val mediaPaths: List<String> = emptyList(),
    val createdAt: Long = 0L,
    val alternatives: List<BrainstormAlternative> = emptyList(),
)

/** One row in the thread rail: a main category and its nested sub-categories. */
data class BrainstormThreadUi(
    val id: String,
    val name: String,
    val parentId: String?,
    val depth: Int,
    val updatedAt: Long,
    val pinned: Boolean = false,
)

data class BrainstormUiState(
    val threads: List<BrainstormThreadUi> = emptyList(),
    val threadId: String = "",
    val messages: List<BrainstormMessageUi> = emptyList(),
    val input: String = "",
    val isStreaming: Boolean = false,
    val streamingText: String = "",
    val errorMessage: String = "",
    val noticeMessage: String = "",
    val lastUsage: String = "",
    val contextChips: List<ContextChip> = emptyList(),
    val showCodexPicker: Boolean = false,
    val codexEntries: List<BrainstormCodexEntry> = emptyList(),
    val showPreview: Boolean = false,
    val previewPrompt: String = "",
    val contextMeter: ContextMeterReading? = null,
    val selectedModelRef: String = "",
    val defaultModelRef: String = "",
    val models: List<com.ihy2ln.weaverse.ai.ModelInfo> = emptyList(),
    val minimumWords: Int = 50,
    val maximumWords: Int = 400,
    /** /A = AI brainstorm reply; \M = file the text without calling the model. */
    val aiMode: Boolean = true,
    /** >0 asks the screen to open the media picker (+ button in the dock). */
    val mediaPickRequestId: Long = 0,
    /** Shows when the + button has staged media for the next message. */
    val hasPendingMedia: Boolean = false,
    val pendingMediaPaths: List<String> = emptyList(),
) {
    val wordRangeValid: Boolean
        get() = minimumWords in PromptWordLimit.Minimum..PromptWordLimit.Maximum &&
            maximumWords in PromptWordLimit.Minimum..PromptWordLimit.Maximum &&
            minimumWords <= maximumWords

    val activeModelRef: String
        get() = PromptModelSelection.effectiveModelRef(selectedModelRef, defaultModelRef)
}

data class BrainstormCodexEntry(
    val id: String,
    val name: String,
    val colorHex: String?,
    val included: Boolean = false,
)

/**
 * The Brainstorm (Notes) workspace: a NovelCrafter-Chat-style AI conversation
 * for ideas and research — plain assistant chat, no characters or personas.
 */
@HiltViewModel
class BrainstormChatViewModel @Inject constructor(
    private val db: WeaverseDatabase,
    private val aiGeneration: AiGenerationService,
    private val settings: SettingsRepository,
    private val modelCache: OpenRouterModelCache,
    private val mediaRepository: com.ihy2ln.weaverse.core.media.MediaRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrainstormUiState())
    val uiState: StateFlow<BrainstormUiState> = _uiState.asStateFlow()

    private val contextBuilder = ContextBuilder()
    private val excludedEntryIds = mutableSetOf<String>()
    private var manualIncludeIds: Set<String> = emptySet()
    private var assembledPrompt: AssembledPrompt? = null
    private var contextLimit = ContextMeter.DEFAULT_LIMIT
    private var generateJob: Job? = null
    private var observeJob: Job? = null
    private var lastClearedInput: String = ""
    /** Media attached via the dock's + button, sent with the next message. */
    private var pendingMedia: List<com.ihy2ln.weaverse.data.db.entities.MediaEntity> = emptyList()

    init {
        viewModelScope.launch {
            db.workshopChatDao().observeThreads(SCOPE).collect { threads ->
                _uiState.update { it.copy(threads = buildThreadTree(threads)) }
                if (_uiState.value.threadId !in threads.map { t -> t.id }) {
                    val next = threads.firstOrNull()?.id ?: createThreadSync()
                    if (next != _uiState.value.threadId) selectThread(next)
                }
            }
        }
        viewModelScope.launch {
            combine(settings.preferences, modelCache.models) { prefs, dtos ->
                prefs.defaultModelRef to modelCache.toModelInfo(dtos)
            }.collect { (defaultRef, models) ->
                _uiState.update { it.copy(defaultModelRef = defaultRef, models = models) }
                contextLimit = ContextMeter.limitFor(_uiState.value.activeModelRef, models)
                refreshContext()
            }
        }
    }

    @Inject lateinit var homeHistory: com.ihy2ln.weaverse.feature.shell.HomeHistory
    fun recordThreadOpen(threadId: String) { viewModelScope.launch { homeHistory.record("Notes", "thread", threadId) } }
    fun selectThread(threadId: String) {
        if (_uiState.value.threadId == threadId && observeJob?.isActive == true) return
        _uiState.update { it.copy(threadId = threadId, messages = emptyList(), streamingText = "") }
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            val thread = db.workshopChatDao().observeThreads(SCOPE).first().find { it.id == threadId }
            if (thread != null) {
                _uiState.update {
                    it.copy(selectedModelRef = thread.modelRef)
                }
            }
            db.workshopChatDao().observeMessages(threadId).collect { messages ->
                val mapped = messages.map { m ->
                    BrainstormMessageUi(
                        id = m.id,
                        role = m.role,
                        text = documentFromJson(m.contentJson).plainText(),
                        usageText = if (m.role != "user" &&
                            (m.promptTokens > 0 || m.completionTokens > 0 || m.costUsd > 0.0)
                        ) {
                            UsageFormat.formatUsage(
                                m.promptTokens,
                                m.completionTokens,
                                null,
                                m.costUsd.takeIf { it > 0.0 },
                            )
                        } else {
                            ""
                        },
                        mediaPaths = mediaPathsOf(m),
                        createdAt = m.createdAt,
                        alternatives = if (m.role == "assistant") {
                            parseBrainstormAlternatives(documentFromJson(m.contentJson).plainText())
                        } else emptyList(),
                    )
                }
                _uiState.update { it.copy(messages = mapped) }
                refreshContext()
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

    fun createThread(onCreated: (String) -> Unit = {}) {
        viewModelScope.launch {
            val id = createThreadSync()
            selectThread(id)
            onCreated(id)
        }
    }

    /** + next to a main category: create a sub-category nested under it. */
    fun createSubThread(parentId: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val id = "thread-${UUID.randomUUID()}"
            val siblings = db.workshopChatDao().getThreads(SCOPE)
                .count { it.parentThreadId == parentId }
            db.workshopChatDao().upsertThread(
                ChatThreadEntity(
                    id = id,
                    scopeId = SCOPE,
                    name = "Sub ${siblings + 1}",
                    parentThreadId = parentId,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            selectThread(id)
        }
    }

    /** Flattens the thread table into parents with their children nested below. */
    private fun buildThreadTree(threads: List<ChatThreadEntity>): List<BrainstormThreadUi> {
        val byParent = threads.groupBy { it.parentThreadId }
        fun childrenOf(parentId: String?): List<ChatThreadEntity> =
            (byParent[parentId] ?: emptyList()).sortedWith(
                compareByDescending<ChatThreadEntity> { it.pinned }.thenByDescending { it.updatedAt },
            )
        val rows = mutableListOf<BrainstormThreadUi>()
        fun emit(parentId: String?, depth: Int) {
            childrenOf(parentId).forEach { thread ->
                rows += BrainstormThreadUi(thread.id, thread.name, thread.parentThreadId, depth, thread.updatedAt, thread.pinned)
                emit(thread.id, depth + 1)
            }
        }
        emit(null, 0)
        // Orphans whose parent vanished still render, one level deep.
        threads.filter { it.parentThreadId != null && threads.none { t -> t.id == it.parentThreadId } }
            .filter { orphan -> rows.none { it.id == orphan.id } }
            .sortedBy { it.createdAt }
            .forEach { rows += BrainstormThreadUi(it.id, it.name, it.parentThreadId, 1, it.updatedAt, it.pinned) }
        return rows
    }

    private suspend fun createThreadSync(): String {
        val now = System.currentTimeMillis()
        val id = "thread-${UUID.randomUUID()}"
        db.workshopChatDao().upsertThread(
            ChatThreadEntity(
                id = id,
                scopeId = SCOPE,
                name = "Chat ${_uiState.value.threads.size + 1}",
                createdAt = now,
                updatedAt = now,
            ),
        )
        return id
    }

    fun deleteThreads(ids: Set<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            // Deleting a main category removes its sub-categories too.
            val doomed = ids.toMutableSet()
            var grew = true
            val all = db.workshopChatDao().getThreads(SCOPE)
            while (grew) {
                grew = false
                all.filter { it.parentThreadId in doomed && it.id !in doomed }.forEach {
                    doomed.add(it.id)
                    grew = true
                }
            }
            doomed.forEach { id ->
                db.workshopChatDao().deleteMessagesForThread(id)
                db.workshopChatDao().deleteThread(id)
            }
        }
    }

    fun renameThread(threadId: String, name: String) {
        viewModelScope.launch {
            val thread = db.workshopChatDao().getThreads(SCOPE).find { it.id == threadId } ?: return@launch
            db.workshopChatDao().upsertThread(thread.copy(name = name.trim().ifBlank { thread.name }, updatedAt = System.currentTimeMillis()))
        }
    }

    fun toggleThreadPin(threadId: String) {
        viewModelScope.launch {
            val thread = db.workshopChatDao().getThreads(SCOPE).find { it.id == threadId } ?: return@launch
            db.workshopChatDao().upsertThread(thread.copy(pinned = !thread.pinned, updatedAt = System.currentTimeMillis()))
        }
    }

    fun onInputChange(value: String) {
        _uiState.update { it.copy(input = value, errorMessage = "") }
        refreshContext()
    }

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

    fun updateMinimumWords(words: Int) {
        if (words !in PromptWordLimit.Minimum..PromptWordLimit.Maximum) return
        _uiState.update { it.copy(minimumWords = words.coerceAtMost(it.maximumWords)) }
        refreshContext()
    }

    fun updateMaximumWords(words: Int) {
        if (words !in PromptWordLimit.Minimum..PromptWordLimit.Maximum) return
        _uiState.update { it.copy(maximumWords = words.coerceAtLeast(it.minimumWords)) }
        refreshContext()
    }

    fun selectModel(modelId: String) {
        _uiState.update { it.copy(selectedModelRef = PromptModelSelection.modelRef(modelId)) }
        contextLimit = ContextMeter.limitFor(_uiState.value.activeModelRef, _uiState.value.models)
        persistModelRef()
        refreshContext()
    }

    fun useDefaultModel() {
        _uiState.update { it.copy(selectedModelRef = "") }
        contextLimit = ContextMeter.limitFor(_uiState.value.activeModelRef, _uiState.value.models)
        persistModelRef()
        refreshContext()
    }

    private fun persistModelRef() {
        val state = _uiState.value
        if (state.threadId.isBlank()) return
        viewModelScope.launch {
            val thread = db.workshopChatDao().getThreads(SCOPE).find { it.id == state.threadId }
                ?: return@launch
            db.workshopChatDao().upsertThread(
                thread.copy(modelRef = state.selectedModelRef, updatedAt = System.currentTimeMillis()),
            )
        }
    }

    fun togglePreview() = _uiState.update { it.copy(showPreview = !it.showPreview) }

    /** /A ↔ \M: AI reply versus filing the text without calling the model. */
    fun toggleAiMode() {
        _uiState.update { it.copy(aiMode = !it.aiMode) }
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
            val result = runCatching { mediaRepository.importFromUris(uris) }
            pendingMedia = result.getOrDefault(emptyList())
                .filter { it.type == "image" }.take(4)
            _uiState.update { it.copy(
                hasPendingMedia = pendingMedia.isNotEmpty(),
                pendingMediaPaths = pendingMedia.map { item -> mediaRepository.resolveFile(item).absolutePath },
                errorMessage = when {
                    result.isFailure -> "Couldn't attach that: ${result.exceptionOrNull()?.message ?: "unsupported file"}"
                    pendingMedia.isEmpty() -> "Choose an image to attach."
                    else -> ""
                },
            ) }
        }
    }

    fun removePendingMedia(mediaPath: String) {
        pendingMedia = pendingMedia.filterNot { mediaRepository.resolveFile(it).absolutePath == mediaPath }
        _uiState.update { it.copy(
            hasPendingMedia = pendingMedia.isNotEmpty(),
            pendingMediaPaths = pendingMedia.map { item -> mediaRepository.resolveFile(item).absolutePath },
        ) }
    }

    /** Outgoing draft as a document: text paragraph plus any media blocks. */
    private fun userMessageDocument(
        text: String, media: List<com.ihy2ln.weaverse.data.db.entities.MediaEntity>,
    ): Document = Document(
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

    fun dismissCodexPicker() = _uiState.update { it.copy(showCodexPicker = false) }

    fun openCodexPicker() {
        viewModelScope.launch {
            val entries = db.codexDao().getAllEntries()
            _uiState.update {
                it.copy(
                    showCodexPicker = true,
                    codexEntries = entries.filter { e -> !e.disabled }.map { e ->
                        BrainstormCodexEntry(
                            id = e.id,
                            name = e.name,
                            colorHex = e.colorHex,
                            included = e.id in manualIncludeIds ||
                                it.contextChips.any { chip -> chip.entryId == e.id },
                        )
                    },
                )
            }
        }
    }

    fun toggleCodexEntry(entryId: String, include: Boolean) {
        manualIncludeIds = if (include) {
            manualIncludeIds + entryId
        } else {
            manualIncludeIds - entryId
        }
        if (!include) excludedEntryIds.add(entryId) else excludedEntryIds.remove(entryId)
        _uiState.update { state ->
            state.copy(
                codexEntries = state.codexEntries.map {
                    if (it.id == entryId) it.copy(included = include) else it
                },
            )
        }
        refreshContext()
    }

    fun removeChip(entryId: String) {
        excludedEntryIds.add(entryId)
        manualIncludeIds = manualIncludeIds - entryId
        refreshContext()
    }

    fun saveAlternative(messageId: String, alternative: BrainstormAlternative) {
        val threadId = _uiState.value.threadId
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            db.brainstormIdeaDao().upsert(BrainstormIdeaEntity(
                id = "idea-${UUID.randomUUID()}", title = alternative.title,
                premise = alternative.premise, strengths = alternative.strengths,
                risks = alternative.risks, nextStep = alternative.nextStep,
                sourceThreadId = threadId, sourceMessageId = messageId,
                createdAt = now, updatedAt = now,
            ))
            _uiState.update { it.copy(noticeMessage = "Saved to Ideas") }
        }
    }

    fun saveMessageAsIdea(messageId: String) {
        val state = _uiState.value
        val message = state.messages.firstOrNull { it.id == messageId } ?: return
        val text = message.text.trim()
        if (text.isBlank()) return
        val title = text.lineSequence().first().take(80).ifBlank { "Brainstorm idea" }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            db.brainstormIdeaDao().upsert(BrainstormIdeaEntity(
                id = "idea-${UUID.randomUUID()}", title = title, premise = text,
                sourceThreadId = state.threadId, sourceMessageId = messageId,
                createdAt = now, updatedAt = now,
            ))
            _uiState.update { it.copy(noticeMessage = "Saved to Ideas") }
        }
    }

    fun creativeAction(action: String, sourceMessageId: String? = null) {
        val state = _uiState.value
        if (state.isStreaming || state.threadId.isBlank()) return
        val source = sourceMessageId?.let { id -> state.messages.firstOrNull { it.id == id }?.text }
            ?: state.input.takeIf { it.isNotBlank() }
            ?: state.messages.lastOrNull { it.role == "assistant" }?.text
        if (source.isNullOrBlank()) {
            _uiState.update { it.copy(errorMessage = "Write an idea or select a message first.") }
            return
        }
        val instruction = when (action) {
            "Explore" -> EXPLORE_FORMAT
            "Develop" -> "Develop this idea into concrete possibilities for characters, world, conflict, and next steps. Ask one useful follow-up question."
            "Challenge" -> "Critique this idea constructively: find weak spots, explain why they matter, and propose practical repairs."
            else -> return
        }
        generateJob?.cancel()
        generateJob = viewModelScope.launch {
            sendAi(state.copy(input = "$action: ${source.trim()}"), instruction, fromDraft = false)
        }
    }

    fun combineAlternatives(options: List<BrainstormAlternative>) {
        val state = _uiState.value
        if (state.isStreaming || options.size < 2) return
        val source = options.joinToString("\n\n") { "${it.title}: ${it.premise}" }
        generateJob?.cancel()
        generateJob = viewModelScope.launch {
            sendAi(state.copy(input = "Combine the strongest parts of these directions:\n$source"),
                "Combine these selected directions into one coherent idea. Preserve what makes each distinct and explain the tradeoffs.",
                fromDraft = false)
        }
    }

    fun send() {
        val state = _uiState.value
        if (state.threadId.isBlank() || state.isStreaming) return
        if (state.input.isBlank() && pendingMedia.isEmpty()) return
        if (state.aiMode) {
            if (!state.wordRangeValid) return
            generateJob?.cancel()
            generateJob = viewModelScope.launch {
                sendAi(state)
            }
        } else {
            // \M manual mode: file the text without calling the model.
            generateJob?.cancel()
            generateJob = viewModelScope.launch {
                val now = System.currentTimeMillis()
                db.workshopChatDao().upsertMessage(
                    ChatMessageEntity(
                        id = "msg-$now",
                        threadId = state.threadId,
                        role = "user",
                        contentJson = userMessageDocument(state.input.trim(), pendingMedia).toJson(),
                        createdAt = now,
                    ),
                )
                pendingMedia = emptyList()
            _uiState.update { it.copy(hasPendingMedia = false, pendingMediaPaths = emptyList()) }
                db.workshopChatDao().getThreads(SCOPE).find { it.id == state.threadId }?.let { thread ->
                    db.workshopChatDao().upsertThread(thread.copy(updatedAt = now))
                }
                _uiState.update { it.copy(input = "", errorMessage = "") }
            }
        }
    }

    private suspend fun sendAi(
        state: BrainstormUiState, extraInstruction: String = "", fromDraft: Boolean = true,
    ) {
        val modelRef = state.activeModelRef
        if (!aiGeneration.hasApiKey(modelRef)) {
            _uiState.update { it.copy(errorMessage = AIError.NoApiKey().message.orEmpty()) }
            return
        }
        val media = if (fromDraft) pendingMedia.toList() else emptyList()
        if (media.isNotEmpty() && !aiGeneration.modelSupportsImages(modelRef)) {
            _uiState.update { it.copy(errorMessage = "Choose a vision model to send attached images.") }
            return
        }
        val attachments = runCatching { imageAttachments(media) }.getOrElse { error ->
            _uiState.update { it.copy(errorMessage = error.message ?: "Could not read an attached image") }
            return
        }
        val now = System.currentTimeMillis()
        val userText = state.input
        val prompt = buildPrompt(state.threadId, userText, extraInstruction = extraInstruction)
        val userMessage = ChatMessageEntity(
            id = "msg-$now",
            threadId = state.threadId,
            role = "user",
            contentJson = userMessageDocument(userText, media).toJson(),
            createdAt = now,
        )
        db.workshopChatDao().upsertMessage(userMessage)
        if (fromDraft) pendingMedia = emptyList()
        _uiState.update { it.copy(
            input = if (fromDraft) "" else it.input,
            hasPendingMedia = if (fromDraft) false else it.hasPendingMedia,
            pendingMediaPaths = if (fromDraft) emptyList() else it.pendingMediaPaths,
            isStreaming = true, streamingText = "", errorMessage = "", noticeMessage = "",
        ) }
            val builder = StringBuilder()
            var usageText = ""
            var promptTokens = 0
            var completionTokens = 0
            var costUsd = 0.0
            runCatching {
                aiGeneration.stream(
                    userMessage = userText,
                    assembled = prompt,
                    modelRef = modelRef,
                    maxTokens = outputTokens(state.maximumWords, extraInstruction),
                    imageAttachments = attachments,
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
            db.workshopChatDao().deleteMessage(userMessage.id)
            if (fromDraft) pendingMedia = media
            _uiState.update {
                it.copy(input = if (fromDraft) userText else it.input,
                    hasPendingMedia = if (fromDraft) media.isNotEmpty() else it.hasPendingMedia,
                    pendingMediaPaths = if (fromDraft) media.map { item -> mediaRepository.resolveFile(item).absolutePath } else it.pendingMediaPaths,
                    isStreaming = false, streamingText = "", errorMessage = formatError(err))
            }
            return
        }
        val replyText = if (extraInstruction == EXPLORE_FORMAT) builder.toString().trim()
            else PromptWordLimit.trim(builder.toString(), state.maximumWords)
        if (replyText.isBlank()) {
            db.workshopChatDao().deleteMessage(userMessage.id)
            if (fromDraft) pendingMedia = media
            _uiState.update {
                it.copy(
                    input = if (fromDraft) userText else it.input,
                    hasPendingMedia = if (fromDraft) media.isNotEmpty() else it.hasPendingMedia,
                    pendingMediaPaths = if (fromDraft) media.map { item -> mediaRepository.resolveFile(item).absolutePath } else it.pendingMediaPaths,
                    isStreaming = false,
                    streamingText = "",
                    errorMessage = "The model returned nothing. Your message was restored — tap Send to retry.",
                )
            }
            return
        }
        val reply = ChatMessageEntity(
            id = "msg-${now + 1}",
            threadId = state.threadId,
            role = "assistant",
            contentJson = Document.fromPlainText(replyText).toJson(),
            createdAt = System.currentTimeMillis(),
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            costUsd = costUsd,
        )
        db.workshopChatDao().upsertMessage(reply)
        db.workshopChatDao().getThreads(SCOPE).find { it.id == state.threadId }?.let { thread ->
            db.workshopChatDao().upsertThread(thread.copy(updatedAt = System.currentTimeMillis()))
        }
        _uiState.update { it.copy(isStreaming = false, streamingText = "", lastUsage = usageText) }
    }

    /** ↻: delete the latest assistant reply and regenerate from the last user message. */
    fun retry() {
        val state = _uiState.value
        if (state.isStreaming || state.threadId.isBlank()) return
        generateJob?.cancel()
        generateJob = viewModelScope.launch {
            val messages = db.workshopChatDao().getMessages(state.threadId)
            val lastReply = messages.lastOrNull { it.role != "user" } ?: return@launch
            val lastUser = messages.lastOrNull { it.role == "user" && it.createdAt <= lastReply.createdAt } ?: return@launch
            _uiState.update { it.copy(isStreaming = true, streamingText = "", errorMessage = "") }
            val userText = documentFromJson(lastUser.contentJson).plainText().trim()
            val images = imageEntitiesOf(lastUser)
            if (images.isNotEmpty() && !aiGeneration.modelSupportsImages(state.activeModelRef)) {
                _uiState.update { it.copy(isStreaming = false,
                    errorMessage = "Choose a vision model to retry this image message.") }
                return@launch
            }
            regenerateLast(state, userText, lastUser.id, lastReply, images)
        }
    }

    private suspend fun regenerateLast(
        state: BrainstormUiState, userText: String, userMessageId: String, previous: ChatMessageEntity,
        images: List<com.ihy2ln.weaverse.data.db.entities.MediaEntity>,
    ) {
        if (!aiGeneration.hasApiKey(state.activeModelRef)) {
            _uiState.update { it.copy(isStreaming = false, errorMessage = AIError.NoApiKey().message.orEmpty()) }
            return
        }
        val previousText = documentFromJson(previous.contentJson).plainText()
        val extraInstruction = if (parseBrainstormAlternatives(previousText).isNotEmpty()) EXPLORE_FORMAT else ""
        val prompt = buildPrompt(state.threadId, userText,
            excludeIds = setOf(userMessageId, previous.id), extraInstruction = extraInstruction)
        val attachments = runCatching { imageAttachments(images) }.getOrElse { err ->
            _uiState.update { it.copy(isStreaming = false, errorMessage = formatError(err)) }
            return
        }
        val builder = StringBuilder()
        runCatching {
            aiGeneration.stream(
                userMessage = userText,
                assembled = prompt,
                modelRef = state.activeModelRef,
                maxTokens = outputTokens(state.maximumWords, extraInstruction),
                imageAttachments = attachments,
            ).collect { chunk ->
                if (chunk is AIChunk.Delta) {
                    builder.append(chunk.text)
                    _uiState.update { it.copy(streamingText = builder.toString()) }
                }
            }
        }.onFailure { err ->
            _uiState.update { it.copy(isStreaming = false, streamingText = "", errorMessage = formatError(err)) }
            return
        }
        val replyText = if (extraInstruction == EXPLORE_FORMAT) builder.toString().trim()
            else PromptWordLimit.trim(builder.toString(), state.maximumWords)
        if (replyText.isBlank()) {
            _uiState.update { it.copy(isStreaming = false, streamingText = "", errorMessage = "The model returned nothing.") }
            return
        }
        db.workshopChatDao().upsertMessage(previous.copy(contentJson = Document.fromPlainText(replyText).toJson()))
        _uiState.update { it.copy(isStreaming = false, streamingText = "") }
    }

    /** » hold-menu action: keep the answer going without a new prompt. */
    fun continueConversation() {
        val state = _uiState.value
        if (state.isStreaming || state.threadId.isBlank()) return
        generateJob?.cancel()
        generateJob = viewModelScope.launch {
            if (!aiGeneration.hasApiKey(state.activeModelRef)) {
                _uiState.update { it.copy(errorMessage = AIError.NoApiKey().message.orEmpty()) }
                return@launch
            }
            _uiState.update { it.copy(isStreaming = true, streamingText = "", errorMessage = "") }
            val builder = StringBuilder()
            val prompt = buildPrompt(state.threadId, "Continue.")
            runCatching {
                aiGeneration.stream(
                    userMessage = "Continue.",
                    assembled = prompt,
                    modelRef = state.activeModelRef,
                    maxTokens = (state.maximumWords * 1.7 + 192).toInt().coerceIn(192, 8192),
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
            val replyText = PromptWordLimit.trim(builder.toString(), state.maximumWords)
            if (replyText.isBlank()) {
                _uiState.update { it.copy(isStreaming = false, streamingText = "", errorMessage = "The model returned nothing.") }
                return@launch
            }
            db.workshopChatDao().upsertMessage(
                ChatMessageEntity(
                    id = "msg-${System.currentTimeMillis()}",
                    threadId = state.threadId,
                    role = "assistant",
                    contentJson = Document.fromPlainText(replyText).toJson(),
                    createdAt = System.currentTimeMillis(),
                ),
            )
            _uiState.update { it.copy(isStreaming = false, streamingText = "") }
        }
    }

    fun cancelGeneration() {
        generateJob?.cancel()
        generateJob = null
        _uiState.update { it.copy(isStreaming = false, streamingText = "", errorMessage = "Cancelled") }
    }

    private suspend fun imageEntitiesOf(message: ChatMessageEntity) =
        documentFromJson(message.contentJson).blocks.mapNotNull { block ->
            (block as? com.ihy2ln.weaverse.core.text.MediaBlock)?.mediaId
                ?.let { mediaRepository.getById(it) }?.takeIf { it.type == "image" }
        }

    private fun imageAttachments(media: List<com.ihy2ln.weaverse.data.db.entities.MediaEntity>): List<ImageAttachment> =
        media.map { item ->
            val file = mediaRepository.resolveFile(item)
            require(file.exists() && file.length() in 1..15_000_000) { "An attached image is missing or too large." }
            val mime = when (file.extension.lowercase()) {
                "png" -> "image/png"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                else -> "image/jpeg"
            }
            ImageAttachment(mime, Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
        }

    private fun outputTokens(maximumWords: Int, extraInstruction: String): Int =
        if (extraInstruction == EXPLORE_FORMAT) 1400
        else (maximumWords * 1.7 + 192).toInt().coerceIn(192, 8192)

    private fun refreshContext() {
        val state = _uiState.value
        if (state.threadId.isBlank()) return
        viewModelScope.launch {
            val prompt = buildPrompt(state.threadId, state.input)
            if (_uiState.value.threadId != state.threadId || _uiState.value.input != state.input) return@launch
            assembledPrompt = prompt
            _uiState.update {
                it.copy(
                    contextChips = prompt.usedEntries.filter { chip -> chip.entryId !in excludedEntryIds },
                    previewPrompt = prompt.systemBlocks.joinToString("\n\n"),
                    contextMeter = ContextMeter.reading(prompt, extraUser = state.input, limitTokens = contextLimit),
                )
            }
        }
    }

    /** Assemble the exact request immediately before generation, with each turn included once. */
    private suspend fun buildPrompt(
        threadId: String, userText: String, excludeIds: Set<String> = emptySet(),
        extraInstruction: String = "",
    ): AssembledPrompt {
        val prior = db.workshopChatDao().getMessages(threadId)
            .filterNot { it.id in excludeIds }
            .mapNotNull { row ->
                val text = documentFromJson(row.contentJson).plainText().trim()
                row.role.takeIf { it == "user" || it == "assistant" }
                    ?.takeIf { text.isNotBlank() }?.let { it to text }
            }
        val state = _uiState.value
        val reserve = (state.maximumWords * 1.7 + 192).toInt().coerceIn(192, 8192)
        val codex = contextBuilder.build(db.codexDao().getAllEntries(), ContextBuildRequest(
            scanText = prior.takeLast(6).joinToString("\n") { it.second } + "\n" + userText,
            userMessage = userText,
            manualIncludeIds = manualIncludeIds,
            manualExcludeIds = excludedEntryIds,
            maxContextTokens = contextLimit,
            reserveResponseTokens = reserve,
        ))
        val system = listOfNotNull(
            BRAIN_STEM,
            extraInstruction.takeIf { it.isNotBlank() },
            codex.codexBlock.takeIf { it.isNotBlank() },
            PromptWordLimit.instruction(state.minimumWords, state.maximumWords)
                .takeUnless { extraInstruction == EXPLORE_FORMAT },
        )
        val historyBudget = (contextLimit - reserve -
            (system.sumOf { it.length } + userText.length + 3) / 4).coerceAtLeast(0)
        return AssembledPrompt(
            systemBlocks = system,
            messages = boundedBrainstormHistory(prior, historyBudget),
            usedEntries = codex.usedEntries,
            tokenBreakdown = codex.tokenBreakdown,
            droppedEntryIds = codex.droppedEntryIds,
            codexBlock = codex.codexBlock,
        )
    }

    private fun formatError(err: Throwable): String = when (err) {
        is AIError.HttpFailure -> "HTTP ${err.statusCode}: ${err.message}"
        is AIError -> err.message
        else -> err.message ?: err.toString()
    }.orEmpty()

    companion object {
        /** App-global scope: brainstorm chats are shared across every book and mode. */
        const val SCOPE = "brainstorm"

        private val BRAIN_STEM = """
            You are a brainstorming partner for a writer: idea generation, worldbuilding,
            research, outlining, and honest feedback. Chat naturally, ask useful questions,
            and keep responses focused and practical. You are not a character or persona —
            never roleplay unless asked, and keep creative work grounded in the writer's
            goals. Reference included codex entries when they are relevant.
        """.trimIndent()
    }
}

/** Retain the newest complete turns that fit, in their original order. */
internal fun boundedBrainstormHistory(
    history: List<Pair<String, String>>, budgetTokens: Int,
): List<Pair<String, String>> {
    var used = 0
    val kept = mutableListOf<Pair<String, String>>()
    for (message in history.asReversed()) {
        val cost = (message.second.length + 3) / 4 + 4
        if (used + cost > budgetTokens) break
        kept += message
        used += cost
    }
    return kept.asReversed().dropWhile { it.first != "user" }
}
