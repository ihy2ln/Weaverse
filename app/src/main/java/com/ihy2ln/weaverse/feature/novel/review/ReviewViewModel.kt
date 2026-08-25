package com.ihy2ln.weaverse.feature.novel.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.ai.AIError
import com.ihy2ln.weaverse.ai.AiGenerationService
import com.ihy2ln.weaverse.ai.context.AssembledPrompt
import com.ihy2ln.weaverse.ai.context.ContextBuildRequest
import com.ihy2ln.weaverse.ai.context.ContextBuilder
import com.ihy2ln.weaverse.core.ui.util.UsageFormat
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.settings.ActionModelKeys
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReviewUiState(
    val sceneId: String = "",
    val chapterScope: Boolean = false,
    val title: String = "",
    val notes: String = "",
    val contextMeter: String = "",
    val usageLog: String = "",
    val errorMessage: String = "",
    val isRunning: Boolean = false,
)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val db: WeaverseDatabase,
    private val settings: SettingsRepository,
    private val aiGeneration: AiGenerationService,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()
    private val contextBuilder = ContextBuilder()

    fun start(sceneId: String, chapterScope: Boolean) {
        if (_uiState.value.sceneId == sceneId &&
            _uiState.value.chapterScope == chapterScope &&
            (_uiState.value.notes.isNotBlank() || _uiState.value.isRunning)
        ) {
            return
        }
        _uiState.value = ReviewUiState(sceneId = sceneId, chapterScope = chapterScope)
        runReview()
    }

    fun runReview() {
        val sceneId = _uiState.value.sceneId
        if (sceneId.isBlank() || _uiState.value.isRunning) return
        viewModelScope.launch {
            if (!aiGeneration.hasApiKey()) {
                _uiState.update {
                    it.copy(errorMessage = AIError.NoApiKey().message.orEmpty(), isRunning = false)
                }
                return@launch
            }
            _uiState.update {
                it.copy(isRunning = true, errorMessage = "", notes = "", usageLog = "", contextMeter = "")
            }
            runCatching {
                val scene = db.manuscriptDao().getScene(sceneId) ?: error("Scene not found")
                val chapter = db.manuscriptDao().getChapter(scene.chapterId)
                val chapterScenes = if (_uiState.value.chapterScope) {
                    db.manuscriptDao().getScenes(scene.chapterId)
                } else {
                    listOf(scene)
                }
                val reviewBody = if (_uiState.value.chapterScope) {
                    chapterScenes.joinToString("\n\n") { s ->
                        "### ${s.title.ifBlank { "Untitled" }}\n${s.plainText}"
                    }
                } else {
                    scene.plainText
                }
                val title = if (_uiState.value.chapterScope) {
                    chapter?.title?.ifBlank { "Chapter" } ?: "Chapter"
                } else {
                    scene.title.ifBlank { "Scene" }
                }
                val bookId = settings.preferences.first().selectedBookId
                val entries = db.codexDao().observeEntries(bookId).first()
                val userMessage = buildString {
                    append("Review the following ")
                    append(if (_uiState.value.chapterScope) "chapter" else "scene")
                    append(" for continuity, voice, pacing, and Codex consistency. ")
                    append("Return concise editorial notes with concrete fix suggestions.\n\n")
                    append(reviewBody.take(12_000))
                }
                val assembled = contextBuilder.build(
                    entries,
                    ContextBuildRequest(
                        scanText = reviewBody,
                        sceneText = reviewBody,
                        userMessage = userMessage,
                    ),
                )
                val prefs = settings.preferences.first()
                val modelRef = settings.modelRefForAction(prefs, ActionModelKeys.REVIEW)
                val systemBlocks = assembled.systemBlocks + listOf(
                    "You are an editorial continuity reviewer for a novel manuscript. " +
                        "Be specific, cite moments from the text, and keep notes actionable.",
                )
                _uiState.update {
                    it.copy(
                        title = title,
                        contextMeter = UsageFormat.formatBreakdown(assembled.tokenBreakdown),
                    )
                }
                val result = aiGeneration.complete(
                    userMessage = userMessage,
                    assembled = AssembledPrompt(
                        systemBlocks = systemBlocks,
                        messages = emptyList(),
                        usedEntries = assembled.usedEntries,
                        tokenBreakdown = assembled.tokenBreakdown,
                        droppedEntryIds = assembled.droppedEntryIds,
                        codexBlock = assembled.codexBlock,
                    ),
                    modelRef = modelRef,
                    maxTokens = 2048,
                )
                val usage = UsageFormat.formatUsage(
                    promptTokens = result.promptTokens,
                    completionTokens = result.completionTokens,
                    totalTokens = result.totalTokens,
                    cost = result.cost,
                )
                _uiState.update {
                    it.copy(
                        notes = result.text.trim(),
                        usageLog = usage,
                        isRunning = false,
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isRunning = false,
                        errorMessage = when (err) {
                            is AIError.HttpFailure -> "HTTP ${err.statusCode}: ${err.message}"
                            is AIError -> err.message.orEmpty()
                            else -> err.message ?: err.toString()
                        },
                    )
                }
            }
        }
    }
}
