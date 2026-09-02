package com.ihy2ln.weaverse.feature.prompt

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Base64
import com.ihy2ln.weaverse.ai.AIChunk
import com.ihy2ln.weaverse.ai.AIError
import com.ihy2ln.weaverse.ai.AiGenerationService
import com.ihy2ln.weaverse.ai.ImageAttachment
import com.ihy2ln.weaverse.ai.ModelInfo
import com.ihy2ln.weaverse.ai.context.AssembledPrompt
import com.ihy2ln.weaverse.ai.context.ContextMeter
import com.ihy2ln.weaverse.ai.context.ContextMeterReading
import com.ihy2ln.weaverse.ai.openrouter.OpenRouterModelCache
import com.ihy2ln.weaverse.ai.prompt.DefaultAiGuides
import com.ihy2ln.weaverse.ai.prompt.PromptTokenContext
import com.ihy2ln.weaverse.ai.prompt.RoleplayPromptBuilder
import com.ihy2ln.weaverse.core.media.MediaRepository
import com.ihy2ln.weaverse.core.text.Document
import com.ihy2ln.weaverse.core.text.appendParagraphs
import com.ihy2ln.weaverse.core.text.documentFromJson
import com.ihy2ln.weaverse.core.text.insertProseAt
import com.ihy2ln.weaverse.core.text.plainText
import com.ihy2ln.weaverse.core.text.toJson
import com.ihy2ln.weaverse.core.text.wordCount
import com.ihy2ln.weaverse.core.ui.util.UsageFormat
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.ChatMessageEntity
import com.ihy2ln.weaverse.data.db.entities.RpMessageEntity
import com.ihy2ln.weaverse.data.db.entities.SnippetEntity
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import com.ihy2ln.weaverse.feature.notes.NotesViewModel
import com.ihy2ln.weaverse.feature.novel.codex.CodexBang
import com.ihy2ln.weaverse.feature.novel.codex.CodexQuickAdd
import com.ihy2ln.weaverse.feature.shell.AppMode
import com.ihy2ln.weaverse.feature.shell.NovelDestination
import com.ihy2ln.weaverse.feature.shell.WorkspaceHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class GlobalPromptUiState(
    val kind: PromptEntryKind? = PromptEntryKind.Ai,
    val text: String = "",
    val minimumOutputWords: Int = 50,
    val outputWords: Int = 100,
    /** True inserts generated prose at the tapped editor caret; false appends at scene end. */
    val insertAtCursor: Boolean = false,
    /** Scene block id of the insert anchor when targeting the cursor. */
    val anchorLabel: String = "",
    /** Last submitted prompt text, for the ↻ retry/resubmit action. */
    val lastPrompt: String = "",
    val streamingText: String = "",
    val isStreaming: Boolean = false,
    val errorMessage: String = "",
    val usageText: String = "",
    val imageMediaId: String? = null,
    val imagePath: String? = null,
    val pickImageRequestId: Long = 0L,
    val statusMessage: String = "",
    /** Empty follows Settings default for this generation. */
    val selectedModelRef: String = "",
    val defaultModelRef: String = "",
    val writingModels: List<ModelInfo> = emptyList(),
    val contextMeterLabel: String = "",
)

data class PromptInsertContext(
    val mode: AppMode = AppMode.Novel,
    val sceneId: String? = null,
    val rpChatId: String? = null,
    val noteId: String? = null,
    val bookId: String = "",
    val workshopThreadId: String? = null,
    val novelDest: String? = null,
)

@HiltViewModel
class GlobalPromptViewModel @Inject constructor(
    private val bus: PromptEntryBus,
    private val aiGeneration: AiGenerationService,
    private val db: WeaverseDatabase,
    private val mediaRepository: MediaRepository,
    private val settings: SettingsRepository,
    private val modelCache: OpenRouterModelCache,
    private val workspaceHistory: WorkspaceHistory,
    private val writeStamps: com.ihy2ln.weaverse.data.repo.SceneWriteStamps,
    private val codexQuickAdd: CodexQuickAdd,
) : ViewModel() {
    private val _uiState = MutableStateFlow(GlobalPromptUiState())
    val uiState: StateFlow<GlobalPromptUiState> = _uiState.asStateFlow()
    private var generateJob: Job? = null
    private var cachedSystemTokens: Int = 0

    private var context = PromptInsertContext()
    private var customBangCommands: Map<String, String> = emptyMap()
    private var removedBangKeywords: Set<String> = emptySet()

    init {
        viewModelScope.launch {
            bus.openRequests.collect { kind -> open(kind) }
        }
        viewModelScope.launch {
            // Keep the ⌖ chip's paragraph number live as the user taps the document.
            bus.insertAnchor.collect { anchor ->
                _uiState.update { state ->
                    if (!state.insertAtCursor) {
                        state
                    } else {
                        state.copy(
                            anchorLabel = anchor?.let { "¶${it.blockIndex + 1}" } ?: "",
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            settings.preferences.collect { prefs ->
                customBangCommands = prefs.customBangCommands
                removedBangKeywords = prefs.removedBangKeywords
                _uiState.update { it.copy(defaultModelRef = prefs.defaultModelRef) }
            }
        }
        viewModelScope.launch {
            modelCache.models.collect { dtos ->
                _uiState.update { it.copy(writingModels = modelCache.writingModels(dtos)) }
            }
        }
    }

    fun updateContext(ctx: PromptInsertContext) {
        context = ctx
        refreshContextMeter(reloadSystem = true)
    }

    fun open(kind: PromptEntryKind) {
        _uiState.update {
            it.copy(
                kind = kind,
                text = "",
                streamingText = "",
                errorMessage = "",
                usageText = "",
                statusMessage = "",
                isStreaming = false,
                imageMediaId = null,
                imagePath = null,
                minimumOutputWords = if (kind == PromptEntryKind.Ai) 50 else it.minimumOutputWords,
                outputWords = if (kind == PromptEntryKind.Ai) 100 else it.outputWords,
            )
        }
        refreshContextMeter(reloadSystem = true)
    }

    fun dismiss() {
        generateJob?.cancel()
        // Reset to the default entry option (/A) when the dock closes.
        _uiState.update { it.copy(kind = PromptEntryKind.Ai, isStreaming = false) }
    }

    fun cancelGeneration() {
        generateJob?.cancel()
        generateJob = null
        _uiState.update { it.copy(isStreaming = false, errorMessage = "Cancelled") }
    }

    fun onTextChange(value: String) {
        _uiState.update {
            it.copy(
                text = value,
                errorMessage = "",
                statusMessage = "",
                contextMeterLabel = meterLabel(value),
            )
        }
    }

    fun selectEntryKind(kind: PromptEntryKind) {
        _uiState.update {
            it.copy(
                kind = kind,
                errorMessage = "",
                statusMessage = "",
                minimumOutputWords = if (kind == PromptEntryKind.Ai && it.kind != PromptEntryKind.Ai) {
                    50
                } else {
                    it.minimumOutputWords
                },
                outputWords = if (kind == PromptEntryKind.Ai && it.kind != PromptEntryKind.Ai) {
                    100
                } else {
                    it.outputWords
                },
            )
        }
    }

    private var lastClearedPromptText: String = ""

    /** ⌫ tap: delete the draft entry (stashed so hold can undo it). */
    fun clearText() {
        lastClearedPromptText = _uiState.value.text
        _uiState.update {
            it.copy(text = "", streamingText = "", errorMessage = "", statusMessage = "")
        }
    }

    /** ⌫ press-and-hold: restore the last deleted draft. */
    fun undoClearText() {
        if (lastClearedPromptText.isBlank()) return
        _uiState.update { it.copy(text = lastClearedPromptText, errorMessage = "") }
        lastClearedPromptText = ""
    }

    /** ⇥/⌖ target chip: toggle between appending at scene end and the tapped caret. */
    fun toggleInsertTarget() {
        _uiState.update { state ->
            val next = !state.insertAtCursor
            val label = if (next) {
                "¶${(bus.insertAnchor.value?.blockIndex ?: 0) + 1}"
            } else {
                ""
            }
            state.copy(insertAtCursor = next, anchorLabel = label)
        }
    }

    /** ↻ hold-menu action: resubmit the last prompt (falls back to the current text). */
    fun retryPrompt() {
        val state = _uiState.value
        if (state.isStreaming) return
        val text = state.text.ifBlank { state.lastPrompt }
        if (text.isBlank() && state.imageMediaId == null) return
        generateAi(state.copy(text = text))
    }

    /** » hold-menu action: keep writing from where the document left off. */
    fun continuePrompt() {
        val state = _uiState.value
        if (state.isStreaming) return
        // Blank text falls through to the mode's continue draft in generateAi.
        generateAi(state.copy(text = ""))
    }

    /** 🎲 composer hold-menu action: append a fresh d20 roll to the prompt text. */
    fun rollDice() {
        val roll = (1..20).random()
        _uiState.update {
            val base = it.text.trimEnd()
            it.copy(text = if (base.isBlank()) "[d20: $roll]" else "$base [d20: $roll]")
        }
    }

    /** ↻ action: reset the prompt entry to its defaults (/A, 50–100 words, empty input). */
    fun refreshPrompt() {
        generateJob?.cancel()
        _uiState.update {
            it.copy(
                kind = PromptEntryKind.Ai,
                text = "",
                streamingText = "",
                errorMessage = "",
                statusMessage = "",
                usageText = "",
                isStreaming = false,
                imageMediaId = null,
                imagePath = null,
                minimumOutputWords = 50,
                outputWords = 100,
            )
        }
        refreshContextMeter(reloadSystem = true)
    }

    fun updateOutputWords(words: Int) {
        if (words !in PromptWordLimit.Minimum..PromptWordLimit.Maximum) return
        _uiState.update { it.copy(outputWords = words) }
    }

    fun updateMinimumOutputWords(words: Int) {
        if (words !in PromptWordLimit.Minimum..PromptWordLimit.Maximum) return
        _uiState.update { it.copy(minimumOutputWords = words) }
    }

    fun selectModel(modelId: String) {
        _uiState.update { it.copy(selectedModelRef = PromptModelSelection.modelRef(modelId)) }
        refreshContextMeter(reloadSystem = false)
    }

    fun useDefaultModel() {
        _uiState.update { it.copy(selectedModelRef = "") }
        refreshContextMeter(reloadSystem = false)
    }

    fun requestImage() {
        _uiState.update { it.copy(pickImageRequestId = it.pickImageRequestId + 1) }
    }

    fun clearImage() {
        _uiState.update { it.copy(imageMediaId = null, imagePath = null) }
    }

    fun importImage(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val entity = mediaRepository.importFromUri(uri)
                _uiState.update {
                    it.copy(
                        imageMediaId = entity.id,
                        imagePath = mediaRepository.resolveFile(entity).absolutePath,
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message ?: "Could not import image") }
            }
        }
    }

    fun submit() {
        val state = _uiState.value
        val kind = state.kind ?: PromptEntryKind.Ai
        if (state.text.isBlank() && state.imageMediaId == null) return
        // "!location a drowned port city" writes the entry and the prose in one go,
        // in AI mode or out of it — the bang is the command, not the mode.
        CodexBang.parse(state.text, customBangCommands, removedBangKeywords)?.let { command ->
            runCodexBang(command)
            return
        }
        if (kind == PromptEntryKind.Ai && state.minimumOutputWords > state.outputWords) {
            _uiState.update { it.copy(errorMessage = "Minimum words must not exceed maximum words") }
            return
        }
        when (kind) {
            PromptEntryKind.Manual -> submitManual(state.text)
            PromptEntryKind.Ai -> generateAi(state)
        }
    }

    /**
     * Generates the codex entry, then inserts the prose it rendered — the same
     * words the entry stores, so the page and the codex never disagree.
     */
    private fun runCodexBang(command: com.ihy2ln.weaverse.feature.novel.codex.CodexBangCommand) {
        generateJob?.cancel()
        generateJob = viewModelScope.launch {
            _uiState.update {
                it.copy(isStreaming = true, streamingText = "", errorMessage = "", statusMessage = "")
            }
            runCatching { codexQuickAdd.run(command, sceneContext = insertContextText()) }
                .onSuccess { result ->
                    runCatching { insertText(result.text, asUserInRoleplay = false) }
                        .onFailure { err ->
                            _uiState.update {
                                it.copy(errorMessage = err.message ?: "Could not insert the entry text")
                            }
                        }
                    _uiState.update {
                        it.copy(isStreaming = false, text = "", statusMessage = result.status)
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isStreaming = false, errorMessage = err.message ?: "Could not write that entry")
                    }
                }
        }
    }

    /** Nearby prose so a generated entry matches what is actually on the page. */
    private suspend fun insertContextText(): String = runCatching {
        when {
            context.sceneId != null -> db.manuscriptDao().getScene(context.sceneId!!)
                ?.let { documentFromJson(it.docJson).plainText() }
                .orEmpty()
            context.rpChatId != null -> db.roleplayDao().getMessages(context.rpChatId!!)
                .takeLast(6)
                .joinToString(separator = System.lineSeparator()) { documentFromJson(it.contentJson).plainText() }
            else -> ""
        }
    }.getOrDefault("")

    private fun submitManual(text: String) {
        viewModelScope.launch {
            runCatching { insertText(text, asUserInRoleplay = true) }
                .onSuccess {
                    _uiState.update { it.copy(statusMessage = "Added", text = "") }
                    dismiss()
                }
                .onFailure { err ->
                    _uiState.update { it.copy(errorMessage = err.message ?: "Could not add text") }
                }
        }
    }

    private fun generateAi(state: GlobalPromptUiState) {
        generateJob?.cancel()
        if (state.text.isNotBlank()) {
            _uiState.update { it.copy(lastPrompt = state.text) }
        }
        generateJob = viewModelScope.launch {
            if (!aiGeneration.hasApiKey()) {
                _uiState.update { it.copy(errorMessage = AIError.NoApiKey().message.orEmpty()) }
                return@launch
            }
            _uiState.update {
                it.copy(isStreaming = true, streamingText = "", errorMessage = "", usageText = "")
            }
            val maxTokens = (state.outputWords * 1.5).toInt().coerceIn(64, 8192)
            val userMessage = buildString {
                append(state.text.ifBlank { DefaultAiGuides.draftFor(context.mode) })
                if (state.imageMediaId != null) {
                    append("\n\n(Use the attached image; turn it into vivid scene text.)")
                }
            }
            val imageAttachments = state.imagePath?.let { path ->
                val file = File(path)
                if (!file.exists()) return@let null
                val bytes = file.readBytes()
                val mime = when (file.extension.lowercase()) {
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    "gif" -> "image/gif"
                    else -> "image/jpeg"
                }
                listOf(ImageAttachment(mimeType = mime, base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)))
            }.orEmpty()
            val builder = StringBuilder()
            var usage = ""
            runCatching {
                aiGeneration.stream(
                    userMessage = userMessage,
                    assembled = AssembledPrompt(
                        systemBlocks = assembleSystemBlocks(state.minimumOutputWords, state.outputWords),
                        messages = emptyList(),
                        usedEntries = emptyList(),
                        tokenBreakdown = emptyList(),
                    ),
                    modelRef = state.selectedModelRef.ifBlank { null },
                    maxTokens = maxTokens,
                    temperature = 0.85,
                    imageAttachments = imageAttachments,
                ).collect { chunk ->
                    when (chunk) {
                        is AIChunk.Delta -> {
                            builder.append(chunk.text)
                            _uiState.update {
                                it.copy(streamingText = PromptWordLimit.trim(builder.toString(), state.outputWords))
                            }
                        }
                        is AIChunk.Usage -> {
                            usage = UsageFormat.formatUsage(
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
                    it.copy(isStreaming = false, errorMessage = err.message ?: "Generation failed")
                }
                return@launch
            }
            // Providers treat maxTokens as an approximate ceiling. Enforce the
            // selected word budget locally before anything reaches the document.
            val result = PromptWordLimit.trim(builder.toString(), state.outputWords)
            runCatching {
                // Roleplay: keep user prompt + character reply; others get AI text only.
                if (context.mode == AppMode.Roleplay && !context.rpChatId.isNullOrBlank()) {
                    insertRoleplayExchange(state.text, result)
                } else if (isWorkshopChat()) {
                    val added = buildList {
                        insertWorkshop(state.text, role = "user")?.let { add(it) }
                        insertWorkshop(result, role = "assistant")?.let { add(it) }
                    }
                    recordChatMessages(added)
                } else if (context.mode == AppMode.Novel) {
                    insertNovelAi(result)
                } else {
                    insertText(result, asUserInRoleplay = false)
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isStreaming = false,
                        usageText = usage,
                        errorMessage = err.message ?: "Could not insert result",
                    )
                }
                return@launch
            }
            _uiState.update {
                val target = if (_uiState.value.insertAtCursor) " at cursor" else ""
                it.copy(
                    isStreaming = false,
                    streamingText = result,
                    usageText = usage,
                    statusMessage = "Inserted$target · ${PromptWordLimit.count(result)}/${state.outputWords} words",
                    text = "",
                )
            }
            dismiss()
        }
    }

    private fun refreshContextMeter(reloadSystem: Boolean) {
        viewModelScope.launch {
            val state = _uiState.value
            if (reloadSystem) {
                val blocks = runCatching {
                    assembleSystemBlocks(state.minimumOutputWords, state.outputWords)
                }.getOrDefault(emptyList())
                cachedSystemTokens = blocks.sumOf { ContextMeter.estimateTokens(it) }
            }
            _uiState.update { it.copy(contextMeterLabel = meterLabel(it.text)) }
        }
    }

    private fun meterLabel(text: String): String {
        val state = _uiState.value
        val used = cachedSystemTokens + ContextMeter.estimateTokens(text)
        val modelRef = state.selectedModelRef.ifBlank { state.defaultModelRef }
        val limit = ContextMeter.limitFor(modelRef, state.writingModels)
        return ContextMeterReading(used, limit).label
    }

    private suspend fun assembleSystemBlocks(minimumWords: Int, outputWords: Int): List<String> {
        val chatId = context.rpChatId
        if (context.mode != AppMode.Roleplay || chatId.isNullOrBlank()) {
            val book = context.bookId.takeIf { it.isNotBlank() }?.let { db.bookDao().getById(it) }
            val series = book?.seriesId?.let { id -> db.seriesDao().observeById(id).first() }
            val pictureCategories = if (context.mode == AppMode.Games) {
                db.mediaDao().getImageCategories().filter { it.startsWith("Adams Haven / ") }
            } else emptyList()
            val gameGuide = if (context.mode == AppMode.Games) buildList {
                add("You are optional narration support for a deterministic text/card game. " +
                    "Write vivid scene prose, dialogue, reactions, descriptions, or player-facing flavor in first person " +
                    "from the Summoner/MC's viewpoint and always in present tense. Never choose the Summoner's actions, " +
                    "words, private thoughts, or decisions. " +
                    "At the start of a new Campaign, vary the immediate hook, weather, discovered trace, visitor, rumor, " +
                    "or minor problem; do not repeat a stock opening, but always leave Farm, Town, Home, and Dungeon as player choices. " +
                    "Never change health, resources, card costs, rewards, flags, targets, or combat outcomes; " +
                    "the local game engine remains authoritative. After the prose, write STORY_OPTIONS: followed by " +
                    "exactly three numbered story choices and a fourth numbered option labeled Custom prompt. " +
                    "These are proposals only and must not be narrated as completed actions. " +
                    "Only reference an existing structured action with [ACTION: existing_choice_id] when the app has supplied that ID; " +
                    "the player must confirm it before state changes.")
                if (pictureCategories.isNotEmpty()) {
                    add(
                        "The shared Pictures library exposes these Adams Haven asset categories: " +
                            pictureCategories.joinToString() + ". Match scene-art requests to the most specific category; " +
                            "never substitute a Character Card for a location or a non-crossroads road for the Haven Crossroads.",
                    )
                }
            } else emptyList()
            return DefaultAiGuides.systemBlocks(
                context.mode,
                outputWords,
                PromptTokenContext(
                    tense = book?.tense?.ifBlank { "past tense" } ?: "past tense",
                    bookTitle = book?.title.orEmpty(),
                    seriesTitle = series?.title.orEmpty(),
                    seriesDescription = series?.description.orEmpty(),
                ),
            ) + gameGuide + PromptWordLimit.instruction(minimumWords, outputWords)
        }
        val chat = db.roleplayDao().getChat(chatId)
        val character = chat?.characterId?.let { db.roleplayDao().getCharacter(it) }
        val persona = chat?.personaId?.let { db.roleplayDao().getPersona(it) }
        return RoleplayPromptBuilder.systemBlocks(character, persona, outputWords) +
            PromptWordLimit.instruction(minimumWords, outputWords)
    }

    private suspend fun activeRpDisplayMode(chatId: String): String =
        db.roleplayDao().getChat(chatId)?.displayMode?.ifBlank { "messenger" } ?: "messenger"

    private fun isWorkshopChat(): Boolean {
        val dest = context.novelDest?.let { runCatching { NovelDestination.valueOf(it) }.getOrNull() }
        return context.mode == AppMode.Novel && dest == NovelDestination.Chat
    }

    private suspend fun insertWorkshop(text: String, role: String): ChatMessageEntity? {
        val threadId = context.workshopThreadId ?: error("Open Chat first")
        if (text.isBlank()) return null
        val entity = ChatMessageEntity(
            id = "msg-${UUID.randomUUID()}",
            threadId = threadId,
            role = role,
            contentJson = Document.fromPlainText(text).toJson(),
            createdAt = System.currentTimeMillis(),
        )
        db.workshopChatDao().upsertMessage(entity)
        return entity
    }

    private fun recordChatMessages(entities: List<ChatMessageEntity>) {
        if (entities.isEmpty()) return
        workspaceHistory.record(
            undo = { entities.forEach { db.workshopChatDao().deleteMessage(it.id) } },
            redo = { entities.forEach { db.workshopChatDao().upsertMessage(it) } },
        )
    }

    private suspend fun insertNovelAi(generated: String) {
        val sceneId = context.sceneId ?: error("Open a scene in Write first")
        val scene = db.manuscriptDao().getScene(sceneId) ?: error("Scene not found")
        val doc = documentFromJson(scene.docJson)
        val anchor = bus.insertAnchor.value
        val targeted = _uiState.value.insertAtCursor && anchor != null && anchor.sceneId == sceneId
        val next = if (targeted) {
            doc.insertProseAt(anchor.blockIndex, anchor.caret, generated)
        } else {
            doc.appendParagraphs(generated)
        }
        persistSceneWithHistory(scene, next)
    }

    private suspend fun persistSceneWithHistory(scene: com.ihy2ln.weaverse.data.db.entities.SceneEntity, next: Document) {
        val after = scene.copy(
            docJson = next.toJson(),
            plainText = next.plainText(),
            wordCount = next.wordCount(),
            updatedAt = writeStamps.next(),
        )
        db.manuscriptDao().upsertScene(after)
        workspaceHistory.record(
            undo = { db.manuscriptDao().upsertScene(scene) },
            redo = { db.manuscriptDao().upsertScene(after) },
        )
    }

    private suspend fun insertRoleplayExchange(userPrompt: String, aiText: String) {
        val chatId = context.rpChatId ?: return
        val mode = activeRpDisplayMode(chatId)
        val now = System.currentTimeMillis()
        val groupId = "sw-$now"
        val added = mutableListOf<RpMessageEntity>()
        if (userPrompt.isNotBlank()) {
            val user = RpMessageEntity(
                id = "rpm-$now",
                chatId = chatId,
                swipeGroupId = groupId,
                swipeIndex = 0,
                isActiveSwipe = true,
                role = "user",
                contentJson = Document.fromPlainText(userPrompt).toJson(),
                createdAt = now,
                displayMode = mode,
            )
            db.roleplayDao().upsertMessage(user)
            added += user
        }
        val reply = RpMessageEntity(
            id = "rpm-${now + 1}",
            chatId = chatId,
            swipeGroupId = groupId,
            swipeIndex = 0,
            isActiveSwipe = true,
            role = "char",
            contentJson = Document.fromPlainText(aiText).toJson(),
            createdAt = now + 1,
            displayMode = mode,
        )
        db.roleplayDao().upsertMessage(reply)
        added += reply
        recordRpMessages(added)
    }

    private fun recordRpMessages(entities: List<RpMessageEntity>) {
        if (entities.isEmpty()) return
        workspaceHistory.record(
            undo = { entities.forEach { db.roleplayDao().deleteMessage(it.id) } },
            redo = { entities.forEach { db.roleplayDao().upsertMessage(it) } },
        )
    }

    private suspend fun insertText(text: String, asUserInRoleplay: Boolean) {
        when {
            isWorkshopChat() -> {
                val entity = insertWorkshop(text, role = if (asUserInRoleplay) "user" else "assistant")
                if (entity != null) recordChatMessages(listOf(entity))
            }
            context.mode == AppMode.Roleplay || context.mode == AppMode.Games -> {
                val chatId = context.rpChatId ?: error("Open a session first")
                val mode = activeRpDisplayMode(chatId)
                val now = System.currentTimeMillis()
                val entity = RpMessageEntity(
                    id = "rpm-$now",
                    chatId = chatId,
                    swipeGroupId = "sw-$now",
                    swipeIndex = 0,
                    isActiveSwipe = true,
                    role = if (asUserInRoleplay) "user" else "char",
                    contentJson = Document.fromPlainText(text).toJson(),
                    createdAt = now,
                    displayMode = mode,
                )
                db.roleplayDao().upsertMessage(entity)
                recordRpMessages(listOf(entity))
            }
            context.mode == AppMode.Novel -> {
                val sceneId = context.sceneId ?: error("Open a scene in Write first")
                val scene = db.manuscriptDao().getScene(sceneId) ?: error("Scene not found")
                persistSceneWithHistory(scene, documentFromJson(scene.docJson).appendParagraphs(text))
            }
            context.mode == AppMode.Notes -> {
                val noteId = context.noteId ?: bus.activeNoteId
                if (noteId != null) {
                    val existing = db.snippetDao().getById(noteId)
                        ?: error("Select a note first")
                    val next = documentFromJson(existing.body).appendParagraphs(text)
                    persistNoteWithHistory(
                        existing,
                        existing.copy(
                            body = next.toJson(),
                            scopeType = NotesViewModel.SCOPE_TYPE,
                            scopeId = NotesViewModel.SCOPE_ID,
                            category = NotesViewModel.CATEGORY,
                        ),
                    )
                } else {
                    val now = System.currentTimeMillis()
                    val id = "note-${UUID.randomUUID()}"
                    val created = SnippetEntity(
                        id = id,
                        scopeType = NotesViewModel.SCOPE_TYPE,
                        scopeId = NotesViewModel.SCOPE_ID,
                        title = "Prompt note",
                        body = Document.fromPlainText(text).toJson(),
                        category = NotesViewModel.CATEGORY,
                        pinned = false,
                        createdAt = now,
                    )
                    persistNoteWithHistory(before = null, after = created)
                    bus.activeNoteId = id
                }
            }
        }
    }

    private suspend fun persistNoteWithHistory(before: SnippetEntity?, after: SnippetEntity) {
        db.snippetDao().upsert(after)
        bus.notifyNoteChanged(after.id)
        workspaceHistory.record(
            undo = {
                if (before == null) db.snippetDao().deleteById(after.id)
                else db.snippetDao().upsert(before)
                bus.notifyNoteChanged(after.id)
            },
            redo = {
                db.snippetDao().upsert(after)
                bus.notifyNoteChanged(after.id)
            },
        )
    }
}
