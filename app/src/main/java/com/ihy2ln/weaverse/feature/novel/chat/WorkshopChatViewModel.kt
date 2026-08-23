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
import com.ihy2ln.weaverse.data.settings.ActionModelKeys
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import com.ihy2ln.weaverse.feature.shell.WorkspaceHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatMessageUi(
    val id: String,
    val role: String,
    val plainText: String,
)

data class WorkshopChatUiState(
    val threadId: String = "thread-1",
    val modelRef: String = "openrouter/deepseek/deepseek-v4-flash",
    val input: String = "",
    val messages: List<ChatMessageUi> = emptyList(),
    val contextChips: List<ContextChip> = emptyList(),
    val previewPrompt: String = "",
    val showPreview: Boolean = false,
    val streamingText: String = "",
    val isStreaming: Boolean = false,
    val errorMessage: String = "",
    val lastUsage: String = "",
    val showCodexPicker: Boolean = false,
    val codexEntries: List<CodexPickerEntry> = emptyList(),
    val showExtraPromptSurfaces: Boolean = false,
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
) : ViewModel() {
    private val contextBuilder = ContextBuilder()
    private var bookId = "book-adams-haven-1"
    private val _uiState = MutableStateFlow(WorkshopChatUiState())
    val uiState: StateFlow<WorkshopChatUiState> = _uiState.asStateFlow()
    private var observeJob: Job? = null
    private var assembledPrompt: com.ihy2ln.weaverse.ai.context.AssembledPrompt? = null

    init {
        viewModelScope.launch {
            settings.preferences.collect { prefs ->
                bookId = prefs.selectedBookId
                _uiState.update {
                    it.copy(
                        showExtraPromptSurfaces = prefs.extraPromptSurfaces.chatComposer,
                        modelRef = settings.modelRefForAction(prefs, ActionModelKeys.WORKSHOP),
                    )
                }
            }
        }
        selectThread("thread-1")
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
                _uiState.update {
                    it.copy(messages = messages.map { m ->
                        ChatMessageUi(m.id, m.role, documentFromJson(m.contentJson).plainText())
                    })
                }
            }
        }
    }

    fun onInputChange(value: String) {
        _uiState.update { it.copy(input = value, errorMessage = "") }
        refreshContext(value)
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

    fun send() {
        val state = _uiState.value
        if (state.input.isBlank() || state.isStreaming) return
        viewModelScope.launch {
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
                contentJson = Document.fromPlainText(userText).toJson(),
                createdAt = now,
            )
            db.workshopChatDao().upsertMessage(userMessage)
            _uiState.update { it.copy(input = "", isStreaming = true, streamingText = "", errorMessage = "") }
            val builder = StringBuilder()
            var usageText = ""
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
                            usageText = UsageFormat.formatUsage(
                                promptTokens = chunk.promptTokens,
                                completionTokens = chunk.completionTokens,
                                totalTokens = chunk.totalTokens,
                                cost = chunk.cost,
                            )
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

    private fun refreshContext(input: String) {
        viewModelScope.launch {
            val entries = db.codexDao().getAllEntries()
            val book = db.bookDao().getById(bookId)
            val series = book?.seriesId?.let { id -> db.seriesDao().observeById(id).first() }
            val assembled = contextBuilder.build(
                entries,
                ContextBuildRequest(
                    scanText = input + " WAHM WAHB WAHO WAH-MEN GR GKOM WAH",
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
            _uiState.update {
                it.copy(
                    contextChips = chips,
                    previewPrompt = withWorkshop.systemBlocks.joinToString("\n\n"),
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
