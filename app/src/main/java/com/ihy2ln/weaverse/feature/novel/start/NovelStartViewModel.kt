package com.ihy2ln.weaverse.feature.novel.start

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.ai.AIChunk
import com.ihy2ln.weaverse.ai.AiGenerationService
import com.ihy2ln.weaverse.core.story.StoryCompanionMode
import com.ihy2ln.weaverse.core.story.StoryStartVocabulary
import com.ihy2ln.weaverse.core.story.storyStartPromptBlock
import com.ihy2ln.weaverse.core.story.storyStartQuestions
import com.ihy2ln.weaverse.core.text.Document
import com.ihy2ln.weaverse.core.text.plainText
import com.ihy2ln.weaverse.core.text.toJson
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.NovelWritingSettings
import com.ihy2ln.weaverse.data.repo.BookRepository
import com.ihy2ln.weaverse.data.repo.ManuscriptRepository
import com.ihy2ln.weaverse.feature.roleplay.campaign.RpgChapterOutline
import com.ihy2ln.weaverse.feature.roleplay.campaign.RpgOpeningSceneGuideline
import com.ihy2ln.weaverse.feature.roleplay.campaign.parseChapterPlan
import com.ihy2ln.weaverse.feature.roleplay.campaign.parseCyoaSuggestions
import com.ihy2ln.weaverse.feature.roleplay.campaign.parseSceneDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The four steps of a novel's start, in order. */
enum class NovelStartStep { Setup, Story, Outline, Opening }

data class NovelStartUiState(
    val bookId: String = "",
    val step: NovelStartStep = NovelStartStep.Setup,
    val setup: NovelStartSetup = NovelStartSetup(),
    val answers: Map<String, String> = emptyMap(),
    val suggestions: Map<String, List<String>> = emptyMap(),
    val outline: RpgChapterOutline = RpgChapterOutline(),
    val openingGuideline: RpgOpeningSceneGuideline = RpgOpeningSceneGuideline(),
    val openingProse: String = "",
    val busy: Boolean = false,
    val status: String = "",
    val error: String = "",
    val finished: Boolean = false,
)

/**
 * Drives the novel start: setup, the Create Your Own Story questions, the Chapter One
 * plan, and the opening scene. The AI takes part in the last three, and every prompt
 * carries the book's company rule.
 */
@HiltViewModel
class NovelStartViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val manuscriptRepository: ManuscriptRepository,
    private val aiGeneration: AiGenerationService,
    private val db: WeaverseDatabase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NovelStartUiState())
    val uiState: StateFlow<NovelStartUiState> = _uiState.asStateFlow()
    private var job: Job? = null

    fun load(bookId: String) {
        if (_uiState.value.bookId == bookId) return
        viewModelScope.launch {
            val book = bookRepository.getBook(bookId) ?: return@launch
            val saved = db.novelWritingDao().settings(bookId)
            _uiState.value = NovelStartUiState(
                bookId = bookId,
                setup = NovelStartSetup(
                    title = book.title,
                    genre = book.genre,
                    pointOfView = book.pov.ifBlank { "Third-person limited" },
                    tense = book.tense.ifBlank { "Past tense" },
                    styleGuide = book.styleGuide,
                    companions = StoryCompanionMode.fromId(saved?.companions),
                ),
            )
        }
    }

    fun updateSetup(block: (NovelStartSetup) -> NovelStartSetup) {
        _uiState.update { it.copy(setup = block(it.setup), error = "") }
    }

    fun setAnswer(id: String, value: String) {
        _uiState.update { it.copy(answers = it.answers + (id to value)) }
    }

    fun back() {
        _uiState.update {
            val previous = when (it.step) {
                NovelStartStep.Setup -> NovelStartStep.Setup
                NovelStartStep.Story -> NovelStartStep.Setup
                NovelStartStep.Outline -> NovelStartStep.Story
                NovelStartStep.Opening -> NovelStartStep.Outline
            }
            it.copy(step = previous, error = "")
        }
    }

    /** Step 1 → 2, asking the model for per-question ideas for this book. */
    fun startStoryStep() {
        _uiState.update { it.copy(step = NovelStartStep.Story) }
        suggest()
    }

    fun suggest() {
        val state = _uiState.value
        job?.cancel()
        job = viewModelScope.launch {
            _uiState.update { it.copy(busy = true, status = "Thinking up ideas for this book…", error = "") }
            val text = generate(novelSuggestionPrompt(state.setup), maxTokens = 900)
            val parsed = text?.let { parseCyoaSuggestions(it) }
            _uiState.update {
                it.copy(
                    busy = false,
                    status = "",
                    suggestions = parsed.orEmpty(),
                    error = if (parsed == null) "No suggestions came back — write your own, or try again." else "",
                )
            }
        }
    }

    /** Step 2 → 3: turn the answers into a chapter outline and an opening guideline. */
    fun buildOutline() {
        val state = _uiState.value
        job?.cancel()
        job = viewModelScope.launch {
            _uiState.update { it.copy(step = NovelStartStep.Outline, busy = true, status = "Planning Chapter One…", error = "") }
            val text = generate(novelOutlinePrompt(state.setup, state.answers), maxTokens = 1600)
            val plan = text?.let { parseChapterPlan(it) }
            _uiState.update {
                it.copy(
                    busy = false,
                    status = "",
                    outline = plan?.outline ?: it.outline,
                    openingGuideline = plan?.openingScene ?: it.openingGuideline,
                    error = if (plan == null) "The plan did not come back. Try again, or edit it yourself." else "",
                )
            }
        }
    }

    fun editOutline(block: (RpgChapterOutline) -> RpgChapterOutline) {
        _uiState.update { it.copy(outline = block(it.outline)) }
    }

    /** Step 3 → 4: write the opening scene. */
    fun writeOpening() {
        val state = _uiState.value
        job?.cancel()
        job = viewModelScope.launch {
            _uiState.update { it.copy(step = NovelStartStep.Opening, busy = true, status = "Writing the opening…", error = "") }
            val text = generate(
                novelOpeningPrompt(state.setup, state.outline, state.openingGuideline),
                maxTokens = 2400,
            )
            val prose = text?.let { parseSceneDraft(it)?.prose }?.takeIf { it.isNotBlank() }
                ?: text?.takeIf { it.isNotBlank() }
            _uiState.update {
                it.copy(
                    busy = false,
                    status = "",
                    openingProse = prose.orEmpty(),
                    error = if (prose.isNullOrBlank()) "Nothing came back. Try again, or finish without an opening scene." else "",
                )
            }
        }
    }

    fun editOpening(value: String) = _uiState.update { it.copy(openingProse = value) }

    fun cancel() {
        job?.cancel()
        _uiState.update { it.copy(busy = false, status = "") }
    }

    /**
     * Saves the whole start: the book's own fields, the memory block every generation
     * reads, and the opening prose as the first scene.
     */
    fun finish() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, status = "Saving…") }
            bookRepository.getBook(state.bookId)?.let { book ->
                db.bookDao().upsert(
                    book.copy(
                        title = state.setup.title.ifBlank { book.title },
                        genre = state.setup.genre,
                        pov = state.setup.pointOfView,
                        tense = state.setup.tense,
                        styleGuide = state.setup.styleGuide,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
            val memory = buildString {
                append(
                    storyStartPromptBlock(
                        companions = state.setup.companions,
                        answers = state.answers,
                        vocabulary = StoryStartVocabulary.Novel,
                    ),
                )
                if (state.outline.premise.isNotBlank()) {
                    appendLine()
                    appendLine()
                    appendLine("CHAPTER ONE PLAN")
                    appendLine("Title: ${state.outline.workingTitle}")
                    appendLine("Premise: ${state.outline.premise}")
                    appendLine("Objective: ${state.outline.primaryObjective}")
                    if (state.outline.antagonist.isNotBlank()) appendLine("Opposition: ${state.outline.antagonist}")
                    state.outline.beats.forEach { beat -> appendLine("- ${beat.title}: ${beat.summary}") }
                }
            }
            val existing = db.novelWritingDao().settings(state.bookId)
            db.novelWritingDao().saveSettings(
                (existing ?: NovelWritingSettings(bookId = state.bookId)).copy(
                    companions = state.setup.companions.id,
                    memory = memory,
                ),
            )
            val prose = state.openingProse.trim()
            if (prose.isNotBlank()) {
                bookRepository.firstSceneId(state.bookId)?.let { sceneId ->
                    manuscriptRepository.getScene(sceneId)?.let { scene ->
                        val document = Document.fromPlainText(prose)
                        manuscriptRepository.saveScene(
                            scene.copy(
                                title = state.openingGuideline.title.ifBlank { scene.title },
                                docJson = document.toJson(),
                                plainText = document.plainText(),
                                wordCount = prose.split(Regex("\\s+")).count { it.isNotBlank() },
                                updatedAt = System.currentTimeMillis(),
                            ),
                        )
                    }
                }
            }
            _uiState.update { it.copy(busy = false, status = "", finished = true) }
        }
    }

    fun questions() = storyStartQuestions(StoryStartVocabulary.Novel)

    private suspend fun generate(prompt: String, maxTokens: Int): String? {
        if (!aiGeneration.hasApiKey(null)) {
            _uiState.update { it.copy(error = "Add an API key in Settings to let the AI help here.") }
            return null
        }
        val builder = StringBuilder()
        runCatching {
            aiGeneration.stream(userMessage = prompt, maxTokens = maxTokens, temperature = 0.8).collect { chunk ->
                if (chunk is AIChunk.Delta) builder.append(chunk.text)
            }
        }.onFailure { error ->
            _uiState.update { it.copy(error = error.message ?: "Generation failed.") }
            return null
        }
        return builder.toString().takeIf { it.isNotBlank() }
    }
}
