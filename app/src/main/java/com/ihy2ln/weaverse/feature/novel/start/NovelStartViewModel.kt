package com.ihy2ln.weaverse.feature.novel.start

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.ai.AIChunk
import com.ihy2ln.weaverse.ai.AiGenerationService
import com.ihy2ln.weaverse.core.story.StoryCompanionMode
import com.ihy2ln.weaverse.core.story.StoryStartVocabulary
import com.ihy2ln.weaverse.core.story.storyStartPromptBlock
import com.ihy2ln.weaverse.core.text.Document
import com.ihy2ln.weaverse.core.text.plainText
import com.ihy2ln.weaverse.core.text.toJson
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.NovelWritingSettings
import com.ihy2ln.weaverse.data.repo.BookRepository
import com.ihy2ln.weaverse.data.repo.ManuscriptRepository
import com.ihy2ln.weaverse.feature.roleplay.campaign.RpgAdventurePlan
import com.ihy2ln.weaverse.feature.roleplay.campaign.RpgChapterOutline
import com.ihy2ln.weaverse.feature.roleplay.campaign.RpgChapterPlanPayload
import com.ihy2ln.weaverse.feature.roleplay.campaign.RpgGenerationStatus
import com.ihy2ln.weaverse.feature.roleplay.campaign.RpgOpeningSceneGuideline
import com.ihy2ln.weaverse.feature.roleplay.campaign.RpgPlanAnswer
import com.ihy2ln.weaverse.feature.roleplay.campaign.RpgSceneDraft
import com.ihy2ln.weaverse.feature.roleplay.campaign.completeChapterPlan
import com.ihy2ln.weaverse.feature.roleplay.campaign.completeSceneDraft
import com.ihy2ln.weaverse.feature.roleplay.campaign.parseChapterPlan
import com.ihy2ln.weaverse.feature.roleplay.campaign.parseSceneDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

/**
 * The novel start's four steps, in the same order the campaign uses them: the story
 * questions, the chapter plan, verification, and Scene One.
 */
enum class NovelStartStep { Setup, Cyoa, GeneratingChapterPlan, ChapterPlan, Verification, GeneratingScene, Started }

/** Which of the four the wizard is on, for the "Step n of 4" line. */
fun NovelStartStep.number(): Int = when (this) {
    NovelStartStep.Setup -> 1
    NovelStartStep.Cyoa -> 2
    NovelStartStep.GeneratingChapterPlan, NovelStartStep.ChapterPlan -> 3
    NovelStartStep.Verification, NovelStartStep.GeneratingScene, NovelStartStep.Started -> 4
}

data class NovelStartUiState(
    val bookId: String = "",
    val step: NovelStartStep = NovelStartStep.Setup,
    val setup: NovelSetupSnapshot = NovelSetupSnapshot(),
    val plan: RpgAdventurePlan = RpgAdventurePlan(),
    val chapterOutline: RpgChapterOutline = RpgChapterOutline(),
    val openingScene: RpgOpeningSceneGuideline = RpgOpeningSceneGuideline(),
    val sceneDraft: RpgSceneDraft? = null,
    val generationStatus: RpgGenerationStatus = RpgGenerationStatus.Idle,
    val generationProgress: Int = 0,
    val generationError: String = "",
    val suggestions: Map<String, List<String>> = emptyMap(),
    val suggestionStatus: RpgGenerationStatus = RpgGenerationStatus.Idle,
    val suggestionProgress: Int = 0,
    val suggestionError: String = "",
    val saved: Boolean = false,
    /** A half-finished start found on open; non-null until Continue or Start over. */
    val resumable: NovelStartProgress? = null,
)

/**
 * Drives the novel's four-step start. The AI takes part in three of the four — it
 * suggests answers, drafts the chapter plan, and writes Scene One — and each of those
 * can be retried, stopped, or replaced with an authored fallback, exactly as the
 * campaign's start allows.
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
    private var progressJob: Job? = null

    fun load(bookId: String) {
        if (_uiState.value.bookId == bookId) return
        viewModelScope.launch {
            val book = bookRepository.getBook(bookId) ?: return@launch
            val saved = db.novelWritingDao().settings(bookId)
            val fresh = NovelStartUiState(
                bookId = bookId,
                setup = NovelSetupSnapshot(
                    title = book.title.ifBlank { "Untitled Book" },
                    genre = book.genre,
                    pointOfView = book.pov.ifBlank { "Third-person limited" },
                    tense = book.tense.ifBlank { "Past tense" },
                    styleGuide = book.styleGuide,
                    companions = StoryCompanionMode.fromId(saved?.companions).id,
                ),
            )
            // A half-finished start asks before doing anything, so reopening never
            // throws away work and never silently resumes something forgotten.
            val progress = decodeNovelStartProgress(saved?.startProgress.orEmpty())
            _uiState.value = if (progress == null) fresh else fresh.copy(resumable = progress)
        }
    }

    /** Picks up the saved start exactly where it stopped. */
    fun resume() {
        val progress = _uiState.value.resumable ?: return
        _uiState.update { state ->
            state.copy(
                resumable = null,
                step = runCatching { NovelStartStep.valueOf(progress.stepId) }.getOrDefault(NovelStartStep.Setup)
                    // A step that was mid-generation resumes at the screen before it,
                    // because the generation itself did not survive the close.
                    .let { step ->
                        when (step) {
                            NovelStartStep.GeneratingChapterPlan -> NovelStartStep.Cyoa
                            NovelStartStep.GeneratingScene -> NovelStartStep.Verification
                            else -> step
                        }
                    },
                setup = progress.setup,
                plan = RpgAdventurePlan(
                    progress.answers.map { RpgPlanAnswer(it.questionId, it.value, skipped = it.skipped) },
                ),
                chapterOutline = progress.outline?.toOutline() ?: state.chapterOutline,
                openingScene = progress.outline?.toSceneGuideline() ?: state.openingScene,
                sceneDraft = progress.openingProse.takeIf { it.isNotBlank() }?.let { RpgSceneDraft(prose = it) },
            )
        }
    }

    /** Throws the saved start away and begins again at step one. */
    fun startOver() {
        val state = _uiState.value
        _uiState.value = NovelStartUiState(bookId = state.bookId, setup = state.setup.copy())
        viewModelScope.launch { clearSavedProgress() }
    }

    // ---- Step 1: Book Setup ------------------------------------------------------

    fun updateSetup(block: (NovelSetupSnapshot) -> NovelSetupSnapshot) {
        _uiState.update { it.copy(setup = block(it.setup)) }
        saveProgressSoon()
    }

    /** Picking a Setting Template carries its guidance across, reworded for a book. */
    fun selectSettingTemplate(id: String, label: String, directive: String) = updateSetup {
        it.copy(
            settingId = id,
            setting = label,
            settingGuidance = novelizeGuidance(directive),
        )
    }

    fun selectSettingDetails(id: String, details: String) = updateSetup {
        it.copy(settingDetailId = id, settingDetails = novelizeGuidance(details))
    }

    fun selectPerspective(id: String, label: String, directive: String) = updateSetup {
        it.copy(
            narrativePovId = id,
            pointOfView = label,
            perspectiveGuidance = novelizeGuidance(directive),
        )
    }

    fun openCyoa() = goTo(NovelStartStep.Cyoa)
    fun editSetup() = goTo(NovelStartStep.Setup)

    // ---- Step 2: Create Your Own Story -------------------------------------------

    fun setCompanions(mode: StoryCompanionMode) {
        _uiState.update { it.copy(setup = it.setup.copy(companions = mode.id)) }
        viewModelScope.launch { persistCompanions(mode) }
    }

    fun saveAnswer(questionId: String, value: String) {
        _uiState.update { state -> state.copy(plan = state.plan.withAnswer(RpgPlanAnswer(questionId, value))) }
        saveProgressSoon()
    }

    fun selectPreset(questionId: String, value: String) = saveAnswer(questionId, value)

    fun skipQuestion(questionId: String) {
        _uiState.update { state ->
            state.copy(plan = state.plan.withAnswer(RpgPlanAnswer(questionId, "", skipped = true)))
        }
        saveProgressSoon()
    }

    fun randomizeUnanswered() = _uiState.update { state ->
        val random = Random(state.bookId.hashCode())
        var plan = state.plan
        novelStartQuestions().forEach { question ->
            val current = plan.answers.firstOrNull { it.questionId == question.id }
            if (current == null || (current.value.isBlank() && !current.skipped)) {
                val choices = state.suggestions[question.id].orEmpty() + question.presets
                choices.randomOrNull(random)?.let { plan = plan.withAnswer(RpgPlanAnswer(question.id, it)) }
            }
        }
        state.copy(plan = plan)
    }

    fun generateSuggestions() {
        val state = _uiState.value
        job?.cancel()
        job = viewModelScope.launch {
            _uiState.update {
                it.copy(suggestionStatus = RpgGenerationStatus.Generating, suggestionProgress = 5, suggestionError = "")
            }
            val text = generate(novelSuggestionPrompt(state.setup), maxTokens = 900) { progress ->
                _uiState.update { it.copy(suggestionProgress = progress) }
            }
            val parsed = text?.let { parseNovelSuggestions(it) }
            _uiState.update {
                if (parsed == null) {
                    it.copy(
                        suggestionStatus = RpgGenerationStatus.Failed,
                        suggestionProgress = 0,
                        suggestionError = it.suggestionError.ifBlank { "No suggestions came back. Use the built-in choices, or retry." },
                    )
                } else {
                    it.copy(
                        suggestions = parsed,
                        suggestionStatus = RpgGenerationStatus.Complete,
                        suggestionProgress = 100,
                        suggestionError = "",
                    )
                }
            }
        }
    }

    fun useLocalSuggestions() = _uiState.update {
        it.copy(
            suggestions = fallbackNovelSuggestions(it.setup),
            suggestionStatus = RpgGenerationStatus.Complete,
            suggestionProgress = 100,
            suggestionError = "",
        )
    }

    // ---- Step 2: the Chapter One plan --------------------------------------------

    fun generateChapterPlan() {
        val state = _uiState.value
        job?.cancel()
        job = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    step = NovelStartStep.GeneratingChapterPlan,
                    generationStatus = RpgGenerationStatus.Generating,
                    generationProgress = 5,
                    generationError = "",
                )
            }
            val text = generate(novelChapterPlanPrompt(state.setup, state.plan), maxTokens = 1600) { progress ->
                _uiState.update { it.copy(generationProgress = progress) }
            }
            val parsed = text?.let { parseChapterPlan(it) }
            if (parsed == null) {
                _uiState.update {
                    it.copy(
                        generationStatus = RpgGenerationStatus.Failed,
                        generationError = it.generationError.ifBlank { "The chapter plan did not come back." },
                    )
                }
            } else {
                applyChapterPlan(completeChapterPlan(parsed, fallbackNovelChapterPlan(state.setup, state.plan)))
            }
        }
    }

    /** The offline escape hatch, so a failed generation never blocks the book. */
    fun useAuthoredChapterPlan() {
        val state = _uiState.value
        applyChapterPlan(fallbackNovelChapterPlan(state.setup, state.plan))
    }

    private fun applyChapterPlan(payload: RpgChapterPlanPayload) {
        _uiState.update {
            it.copy(
                step = NovelStartStep.ChapterPlan,
                chapterOutline = payload.outline,
                openingScene = payload.openingScene,
                generationStatus = RpgGenerationStatus.Complete,
                generationProgress = 100,
                generationError = "",
            )
        }
        saveProgressSoon()
    }

    fun updateChapterOutline(next: RpgChapterOutline) {
        _uiState.update { it.copy(chapterOutline = next) }
        saveProgressSoon()
    }

    fun updateOpeningScene(next: RpgOpeningSceneGuideline) {
        _uiState.update { it.copy(openingScene = next) }
        saveProgressSoon()
    }

    // ---- Step 3: verification ----------------------------------------------------

    fun openVerification() = goTo(NovelStartStep.Verification)
    fun editCyoa() = goTo(NovelStartStep.Cyoa)
    fun editChapterPlan() = goTo(NovelStartStep.ChapterPlan)

    private fun goTo(step: NovelStartStep) {
        _uiState.update { it.copy(step = step) }
        saveProgressSoon()
    }

    // ---- Step 4: Scene One -------------------------------------------------------

    fun verifyAndWriteOpeningScene() {
        val state = _uiState.value
        job?.cancel()
        job = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    step = NovelStartStep.GeneratingScene,
                    generationStatus = RpgGenerationStatus.Generating,
                    generationProgress = 5,
                    generationError = "",
                )
            }
            val text = generate(
                novelOpeningScenePrompt(state.setup, state.plan, state.chapterOutline, state.openingScene),
                // A paragraph, not a scene — the budget says so as well as the prompt.
                maxTokens = 400,
            ) { progress -> _uiState.update { it.copy(generationProgress = progress) } }
            val parsed = text?.let { parseSceneDraft(it) }
                ?: text?.takeIf { it.isNotBlank() }?.let { RpgSceneDraft(prose = it.trim()) }
            if (parsed == null || parsed.prose.isBlank()) {
                _uiState.update {
                    it.copy(
                        generationStatus = RpgGenerationStatus.Failed,
                        generationError = it.generationError.ifBlank { "Scene One did not come back." },
                    )
                }
            } else {
                val completed = completeSceneDraft(parsed, fallbackNovelSceneDraft(state.plan, state.openingScene))
                applySceneDraft(
                    completed.copy(
                        // A book offers no choices; drop any the model insisted on.
                        choices = emptyList(),
                        // And one paragraph means one, whatever the model sent back.
                        prose = firstParagraphOnly(completed.prose),
                    ),
                )
            }
        }
    }

    fun useAuthoredOpeningScene() {
        val state = _uiState.value
        applySceneDraft(fallbackNovelSceneDraft(state.plan, state.openingScene))
    }

    private fun applySceneDraft(draft: RpgSceneDraft) {
        _uiState.update {
            it.copy(
                step = NovelStartStep.Started,
                sceneDraft = draft,
                generationStatus = RpgGenerationStatus.Complete,
                generationProgress = 100,
                generationError = "",
            )
        }
        viewModelScope.launch { save(draft.prose) }
    }

    fun editSceneProse(value: String) {
        _uiState.update {
            it.copy(sceneDraft = (it.sceneDraft ?: RpgSceneDraft()).copy(prose = value), saved = false)
        }
        saveProgressSoon()
    }

    fun saveSceneProse() {
        val prose = _uiState.value.sceneDraft?.prose.orEmpty()
        viewModelScope.launch { save(prose) }
    }

    // ---- Shared ------------------------------------------------------------------

    fun retryGeneration() {
        when (_uiState.value.step) {
            NovelStartStep.GeneratingChapterPlan, NovelStartStep.ChapterPlan -> generateChapterPlan()
            NovelStartStep.GeneratingScene, NovelStartStep.Started -> verifyAndWriteOpeningScene()
            else -> generateSuggestions()
        }
    }

    fun cancelGeneration() {
        job?.cancel()
        _uiState.update {
            it.copy(
                generationStatus = if (it.generationStatus == RpgGenerationStatus.Generating) RpgGenerationStatus.Idle else it.generationStatus,
                suggestionStatus = if (it.suggestionStatus == RpgGenerationStatus.Generating) RpgGenerationStatus.Idle else it.suggestionStatus,
                step = when (it.step) {
                    NovelStartStep.GeneratingChapterPlan -> NovelStartStep.Cyoa
                    NovelStartStep.GeneratingScene -> NovelStartStep.Verification
                    else -> it.step
                },
            )
        }
    }

    fun questions() = novelStartQuestions()

    /**
     * Saves the start: the book's own fields, the memory block every later generation
     * reads, and Scene One's prose into the book's first scene.
     */
    private suspend fun save(prose: String) {
        val state = _uiState.value
        bookRepository.getBook(state.bookId)?.let { book ->
            db.bookDao().upsert(
                book.copy(
                    title = state.setup.title.ifBlank { book.title },
                    genre = state.setup.genre,
                    pov = state.setup.pointOfView,
                    tense = state.setup.tense,
                    // The templates' guidance is folded into the book's style guide, the
                    // way a campaign folds its own, so every later generation reads it.
                    styleGuide = listOf(
                        state.setup.settingGuidance.takeIf { it.isNotBlank() }?.let { "Setting guidance: $it" },
                        state.setup.settingDetails.takeIf { it.isNotBlank() }?.let { "Setting details: $it" },
                        state.setup.perspectiveGuidance.takeIf { it.isNotBlank() }?.let { "Perspective guidance: $it" },
                        state.setup.styleGuide.trim().takeIf { it.isNotBlank() },
                    ).filterNotNull().joinToString("\n\n"),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
        val memory = buildString {
            append(
                storyStartPromptBlock(
                    companions = StoryCompanionMode.fromId(state.setup.companions),
                    answers = state.plan.answers.filterNot { it.skipped }.associate { it.questionId to it.value },
                    vocabulary = StoryStartVocabulary.Novel,
                ),
            )
            if (state.chapterOutline.premise.isNotBlank()) {
                appendLine()
                appendLine()
                appendLine("CHAPTER ONE PLAN")
                appendLine("Title: ${state.chapterOutline.workingTitle}")
                appendLine("Premise: ${state.chapterOutline.premise}")
                appendLine("Objective: ${state.chapterOutline.primaryObjective}")
                if (state.chapterOutline.antagonist.isNotBlank()) appendLine("Opposition: ${state.chapterOutline.antagonist}")
                state.chapterOutline.beats.forEach { beat -> appendLine("- ${beat.title}: ${beat.summary}") }
            }
        }
        val existing = db.novelWritingDao().settings(state.bookId)
        db.novelWritingDao().saveSettings(
            (existing ?: NovelWritingSettings(bookId = state.bookId)).copy(
                companions = state.setup.companions,
                memory = memory,
            ),
        )
        val text = prose.trim()
        if (text.isNotBlank()) {
            bookRepository.firstSceneId(state.bookId)?.let { sceneId ->
                manuscriptRepository.getScene(sceneId)?.let { scene ->
                    val document = Document.fromPlainText(text)
                    manuscriptRepository.saveScene(
                        scene.copy(
                            title = state.openingScene.title.ifBlank { scene.title },
                            docJson = document.toJson(),
                            plainText = document.plainText(),
                            wordCount = text.split(Regex("\\s+")).count { it.isNotBlank() },
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                }
            }
        }
        // The start is done, so there is nothing left to come back to.
        clearSavedProgress()
        _uiState.update { it.copy(saved = true) }
    }

    /**
     * Writes the start's progress after each change, debounced so typing does not hit
     * the database on every keystroke. The wizard has no Save button by design: the
     * work should still be there whether it was closed on purpose or not.
     */
    private fun saveProgressSoon() {
        val state = _uiState.value
        if (state.bookId.isBlank() || state.resumable != null) return
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            delay(400)
            writeProgress(
                NovelStartProgress(
                    stepId = state.step.name,
                    setup = state.setup,
                    answers = state.plan.answers.map {
                        NovelSavedAnswer(it.questionId, it.value, it.skipped)
                    },
                    outline = state.chapterOutline.toSaved(state.openingScene),
                    openingProse = state.sceneDraft?.prose.orEmpty(),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    private suspend fun writeProgress(progress: NovelStartProgress) {
        val bookId = _uiState.value.bookId.ifBlank { return }
        val existing = db.novelWritingDao().settings(bookId)
        db.novelWritingDao().saveSettings(
            (existing ?: NovelWritingSettings(bookId = bookId))
                .copy(startProgress = encodeNovelStartProgress(progress)),
        )
    }

    private suspend fun clearSavedProgress() {
        val bookId = _uiState.value.bookId.ifBlank { return }
        progressJob?.cancel()
        val existing = db.novelWritingDao().settings(bookId) ?: return
        db.novelWritingDao().saveSettings(existing.copy(startProgress = ""))
    }

    private suspend fun persistCompanions(mode: StoryCompanionMode) {
        val bookId = _uiState.value.bookId.ifBlank { return }
        val existing = db.novelWritingDao().settings(bookId)
        db.novelWritingDao().saveSettings(
            (existing ?: NovelWritingSettings(bookId = bookId)).copy(companions = mode.id),
        )
    }

    private suspend fun generate(
        prompt: String,
        maxTokens: Int,
        onProgress: (Int) -> Unit,
    ): String? {
        if (!aiGeneration.hasApiKey(null)) {
            _uiState.update {
                it.copy(
                    generationError = "Add an API key in Settings to let the AI help here.",
                    suggestionError = "Add an API key in Settings to let the AI help here.",
                )
            }
            return null
        }
        val builder = StringBuilder()
        runCatching {
            aiGeneration.stream(userMessage = prompt, maxTokens = maxTokens, temperature = 0.8).collect { chunk ->
                if (chunk is AIChunk.Delta) {
                    builder.append(chunk.text)
                    // Length against the budget is the only honest progress signal a
                    // stream gives; it is capped at 95 so it never claims to be done.
                    onProgress((builder.length * 95 / (maxTokens * 3).coerceAtLeast(1)).coerceIn(5, 95))
                }
            }
        }.onFailure { error ->
            _uiState.update {
                it.copy(
                    generationError = error.message ?: "Generation failed.",
                    suggestionError = error.message ?: "Generation failed.",
                )
            }
            return null
        }
        return builder.toString().takeIf { it.isNotBlank() }
    }
}

private fun RpgAdventurePlan.withAnswer(answer: RpgPlanAnswer): RpgAdventurePlan =
    copy(answers = answers.filterNot { it.questionId == answer.questionId } + answer)

private fun RpgChapterOutline.toSaved(scene: RpgOpeningSceneGuideline): NovelSavedOutline? {
    val empty = premise.isBlank() && primaryObjective.isBlank() && beats.isEmpty() &&
        scene.locationAndAtmosphere.isBlank() && scene.startingCast.isBlank()
    if (empty) return null
    return NovelSavedOutline(
        workingTitle = workingTitle,
        premise = premise,
        primaryObjective = primaryObjective,
        antagonist = antagonist,
        importantLocations = importantLocations,
        beats = beats.map { NovelSavedBeat(it.id, it.title, it.summary, it.completed) },
        optionalBeat = optionalBeat,
        majorChallenge = majorChallenge,
        climax = climax,
        possibleOutcomes = possibleOutcomes,
        sceneTitle = scene.title,
        sceneLocation = scene.locationAndAtmosphere,
        sceneCast = scene.startingCast,
        sceneObjective = scene.immediateObjective,
        sceneConflict = scene.conflictAndStakes,
        sceneComplication = scene.complication,
        sceneHook = scene.firstDecisionHook,
        sceneArtTags = scene.sceneArtTags,
    )
}

internal fun NovelSavedOutline.toOutline(): RpgChapterOutline = RpgChapterOutline(
    workingTitle = workingTitle.ifBlank { "Chapter One" },
    premise = premise,
    primaryObjective = primaryObjective,
    antagonist = antagonist,
    importantLocations = importantLocations,
    beats = beats.map { com.ihy2ln.weaverse.feature.roleplay.campaign.RpgChapterBeat(it.id, it.title, it.summary, it.completed) },
    optionalBeat = optionalBeat,
    majorChallenge = majorChallenge,
    climax = climax,
    possibleOutcomes = possibleOutcomes,
)

internal fun NovelSavedOutline.toSceneGuideline(): RpgOpeningSceneGuideline = RpgOpeningSceneGuideline(
    title = sceneTitle.ifBlank { "Chapter One" },
    locationAndAtmosphere = sceneLocation,
    startingCast = sceneCast,
    immediateObjective = sceneObjective,
    conflictAndStakes = sceneConflict,
    complication = sceneComplication,
    firstDecisionHook = sceneHook,
    sceneArtTags = sceneArtTags,
)
