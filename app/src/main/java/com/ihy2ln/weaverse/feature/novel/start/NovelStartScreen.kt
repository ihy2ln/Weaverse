package com.ihy2ln.weaverse.feature.novel.start

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.story.StoryCompanionMode
import com.ihy2ln.weaverse.core.ui.components.InkOutlinedButton
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusMd
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.feature.roleplay.campaign.RpgChapterBeat
import com.ihy2ln.weaverse.feature.roleplay.campaign.RpgGenerationStatus

/**
 * The novel's four-step start, the campaign's own wizard worded for a book:
 * Create Your Own Story, the Chapter One plan, Verify Your Story, and Chapter One
 * itself. Nothing table-only appears here — no play-as role, game mode, rule system
 * or house rules — because none of it means anything in a novel.
 */
@Composable
fun NovelStartScreen(
    bookId: String,
    onClose: () -> Unit,
    viewModel: NovelStartViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val tokens = inkTokens()
    LaunchedEffect(bookId) { viewModel.load(bookId) }
    LaunchedEffect(state.step, state.suggestionStatus, state.bookId) {
        if (
            state.bookId.isNotBlank() &&
            state.step == NovelStartStep.Cyoa &&
            state.suggestionStatus == RpgGenerationStatus.Idle &&
            state.suggestions.isEmpty()
        ) {
            viewModel.generateSuggestions()
        }
    }

    Box(Modifier.fillMaxSize().background(tokens.background).padding(InkSpacing.xs)) {
        when (state.step) {
            NovelStartStep.Cyoa -> StartSplit(
                summary = { StepSummary(state, "Create Your Own Story", "Answer in the boxes. Presets fill a box and remain editable; Skip leaves it blank.") },
                controls = { CyoaControls(state, viewModel, onClose) },
            )

            NovelStartStep.GeneratingChapterPlan -> StartSplit(
                summary = { StepSummary(state, "Chapter One Plan", "Your answers are being turned into a flexible chapter outline and an opening-scene guideline.") },
                controls = {
                    GenerationPanel(
                        state = state,
                        label = "Creating Chapter One",
                        working = "The AI is working. This screen advances only after the result is checked and saved.",
                        onRetry = viewModel::generateChapterPlan,
                        onFallback = viewModel::useAuthoredChapterPlan,
                        onCancel = viewModel::cancelGeneration,
                    )
                },
            )

            NovelStartStep.ChapterPlan -> StartSplit(
                summary = { StepSummary(state, "Chapter One Plan", "Edit the AI's rough outline. These beats guide the book; they never lock the writing in.") },
                controls = { ChapterPlanControls(state, viewModel) },
            )

            NovelStartStep.Verification -> StartSplit(
                summary = { StepSummary(state, "Verify Your Story", "Review the book, your answers, the outline, and the opening scene before Chapter One is written.") },
                controls = { VerificationControls(state, viewModel) },
            )

            NovelStartStep.GeneratingScene -> StartSplit(
                summary = { StepSummary(state, "Chapter One", "The verified plan is being written as the book's opening scene.") },
                controls = {
                    GenerationPanel(
                        state = state,
                        label = "Writing Chapter One",
                        working = "The AI is writing the opening scene. It lands in the book's first scene once it is saved.",
                        onRetry = viewModel::verifyAndWriteOpeningScene,
                        onFallback = viewModel::useAuthoredOpeningScene,
                        onCancel = viewModel::cancelGeneration,
                    )
                },
            )

            NovelStartStep.Started -> StartSplit(
                summary = { StepSummary(state, "Chapter One", "The opening scene is saved into the book's first scene. Edit it here, or open the book and keep writing.") },
                controls = { OpeningProseControls(state, viewModel, onClose) },
            )
        }
    }
}

@Composable
private fun StartPane(modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .padding(InkSpacing.xs)
            .clip(RoundedCornerShape(inkRadiusMd()))
            .background(inkTokens().panel)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), RoundedCornerShape(inkRadiusMd()))
            .padding(InkSpacing.sm),
    ) { content() }
}

/** Side by side in landscape, stacked in portrait — the campaign start's own layout. */
@Composable
private fun StartSplit(summary: @Composable () -> Unit, controls: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize()) {
                StartPane(Modifier.weight(1f).fillMaxSize(), summary)
                StartPane(Modifier.weight(1f).fillMaxSize(), controls)
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                StartPane(Modifier.weight(1f).fillMaxSize(), summary)
                StartPane(Modifier.weight(1f).fillMaxSize(), controls)
            }
        }
    }
}

@Composable
private fun StepSummary(state: NovelStartUiState, heading: String, detail: String) {
    val tokens = inkTokens()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(InkSpacing.xs),
    ) {
        Text(heading, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = tokens.secondaryText)
        Text("Book", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text(state.setup.title, style = MaterialTheme.typography.bodyLarge)
        if (state.setup.genre.isNotBlank()) {
            Text(state.setup.genre, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            "${state.setup.pointOfView} · ${state.setup.tense}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (state.setup.characters.isNotBlank()) {
            Text("Cast: ${state.setup.characters}", style = MaterialTheme.typography.bodySmall)
        }
        Text(
            "Company: ${StoryCompanionMode.fromId(state.setup.companions).label}",
            style = MaterialTheme.typography.bodySmall,
        )
        Text("Step ${state.step.number()} of 4", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun GenerationPanel(
    state: NovelStartUiState,
    label: String,
    working: String,
    onRetry: () -> Unit,
    onFallback: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(InkSpacing.sm),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        LinearProgressIndicator(
            progress = { state.generationProgress.coerceIn(0, 100) / 100f },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "${state.generationProgress.coerceIn(0, 100)}%",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        if (state.generationStatus == RpgGenerationStatus.Failed) {
            Text(state.generationError.ifBlank { "Generation failed." }, color = MaterialTheme.colorScheme.error)
            InkOutlinedButton("Retry", onRetry, Modifier.fillMaxWidth())
            InkTextButton("Continue with the written fallback", onFallback)
        } else {
            Text(working, style = MaterialTheme.typography.bodyMedium)
            InkOutlinedButton("Stop AI generation", onCancel, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun StepColumn(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(InkSpacing.sm),
    ) { content() }
}

@Composable
private fun CyoaControls(state: NovelStartUiState, viewModel: NovelStartViewModel, onClose: () -> Unit) {
    val tokens = inkTokens()
    StepColumn {
        when (state.suggestionStatus) {
            RpgGenerationStatus.Generating -> {
                Text("Creating suggestions for this book…", style = MaterialTheme.typography.labelLarge)
                LinearProgressIndicator(
                    progress = { state.suggestionProgress.coerceIn(0, 100) / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                InkOutlinedButton("Stop AI suggestions", viewModel::cancelGeneration, Modifier.fillMaxWidth())
            }

            RpgGenerationStatus.Complete -> Text(
                "AI suggestions for this book are mixed with the built-in choices below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )

            RpgGenerationStatus.Failed -> {
                Text(state.suggestionError, style = MaterialTheme.typography.bodySmall, color = tokens.secondaryText)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
                    InkOutlinedButton("Retry AI suggestions", viewModel::generateSuggestions, Modifier.weight(1f))
                    InkTextButton("Use local suggestions", viewModel::useLocalSuggestions)
                }
            }

            RpgGenerationStatus.Idle -> Unit
        }
        CompanyPicker(StoryCompanionMode.fromId(state.setup.companions), viewModel::setCompanions)
        viewModel.questions().forEachIndexed { index, question ->
            val answer = state.plan.answers.firstOrNull { it.questionId == question.id }
            val choices = (state.suggestions[question.id].orEmpty() + question.presets).distinct()
            Column(verticalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
                OutlinedTextField(
                    value = answer?.value.orEmpty(),
                    onValueChange = { viewModel.saveAnswer(question.id, it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("${index + 1}. ${question.prompt}") },
                    singleLine = false,
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
                ) {
                    items(choices) { preset ->
                        InkTextButton(label = preset, onClick = { viewModel.selectPreset(question.id, preset) }, compact = true)
                    }
                    item {
                        InkTextButton(label = "Skip", onClick = { viewModel.skipQuestion(question.id) }, compact = true)
                    }
                }
            }
        }
        InkOutlinedButton("Randomize unanswered", viewModel::randomizeUnanswered, Modifier.fillMaxWidth())
        InkTextButton("Refresh AI suggestions", viewModel::generateSuggestions)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
            InkTextButton("Close", onClose)
            InkOutlinedButton("Create chapter plan", viewModel::generateChapterPlan, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ChapterPlanControls(state: NovelStartUiState, viewModel: NovelStartViewModel) {
    val outline = state.chapterOutline
    val scene = state.openingScene
    fun update(next: com.ihy2ln.weaverse.feature.roleplay.campaign.RpgChapterOutline) = viewModel.updateChapterOutline(next)
    fun updateScene(next: com.ihy2ln.weaverse.feature.roleplay.campaign.RpgOpeningSceneGuideline) = viewModel.updateOpeningScene(next)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(InkSpacing.xs),
    ) {
        if (state.generationError.isNotBlank()) {
            Text(state.generationError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        Text("Chapter outline", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(outline.workingTitle, { update(outline.copy(workingTitle = it)) }, Modifier.fillMaxWidth(), label = { Text("Working title") })
        OutlinedTextField(outline.premise, { update(outline.copy(premise = it)) }, Modifier.fillMaxWidth(), label = { Text("Premise") }, minLines = 2)
        OutlinedTextField(outline.primaryObjective, { update(outline.copy(primaryObjective = it)) }, Modifier.fillMaxWidth(), label = { Text("What the chapter drives at") })
        OutlinedTextField(outline.antagonist, { update(outline.copy(antagonist = it)) }, Modifier.fillMaxWidth(), label = { Text("Opposition") })
        OutlinedTextField(outline.importantLocations, { update(outline.copy(importantLocations = it)) }, Modifier.fillMaxWidth(), label = { Text("Important places") })
        OutlinedTextField(
            value = outline.beats.joinToString("\n") { "${it.title}: ${it.summary}" },
            onValueChange = { value ->
                update(
                    outline.copy(
                        beats = value.lines().filter { it.isNotBlank() }.take(5).mapIndexed { i, line ->
                            RpgChapterBeat(
                                "beat-${i + 1}",
                                line.substringBefore(':').trim(),
                                line.substringAfter(':', "").trim(),
                                outline.beats.getOrNull(i)?.completed == true,
                            )
                        },
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Three to five story beats") },
            minLines = 4,
        )
        OutlinedTextField(outline.optionalBeat, { update(outline.copy(optionalBeat = it)) }, Modifier.fillMaxWidth(), label = { Text("Optional quieter beat") })
        OutlinedTextField(outline.majorChallenge, { update(outline.copy(majorChallenge = it)) }, Modifier.fillMaxWidth(), label = { Text("The chapter's hardest moment") })
        OutlinedTextField(outline.climax, { update(outline.copy(climax = it)) }, Modifier.fillMaxWidth(), label = { Text("Where the chapter peaks") })
        OutlinedTextField(outline.possibleOutcomes, { update(outline.copy(possibleOutcomes = it)) }, Modifier.fillMaxWidth(), label = { Text("Possible outcomes") }, minLines = 2)
        Text("Opening-scene guideline", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(scene.title, { updateScene(scene.copy(title = it)) }, Modifier.fillMaxWidth(), label = { Text("Scene title") })
        OutlinedTextField(scene.locationAndAtmosphere, { updateScene(scene.copy(locationAndAtmosphere = it)) }, Modifier.fillMaxWidth(), label = { Text("Place, time, atmosphere") }, minLines = 2)
        OutlinedTextField(scene.startingCast, { updateScene(scene.copy(startingCast = it)) }, Modifier.fillMaxWidth(), label = { Text("Who is present") })
        OutlinedTextField(scene.immediateObjective, { updateScene(scene.copy(immediateObjective = it)) }, Modifier.fillMaxWidth(), label = { Text("Immediate objective") })
        OutlinedTextField(scene.conflictAndStakes, { updateScene(scene.copy(conflictAndStakes = it)) }, Modifier.fillMaxWidth(), label = { Text("Conflict and stakes") }, minLines = 2)
        OutlinedTextField(scene.complication, { updateScene(scene.copy(complication = it)) }, Modifier.fillMaxWidth(), label = { Text("Opening complication") })
        OutlinedTextField(scene.firstDecisionHook, { updateScene(scene.copy(firstDecisionHook = it)) }, Modifier.fillMaxWidth(), label = { Text("Closing hook") })
        OutlinedTextField(scene.sceneArtTags, { updateScene(scene.copy(sceneArtTags = it)) }, Modifier.fillMaxWidth(), label = { Text("Scene-art tags") })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
            InkTextButton("Back to the story", viewModel::editCyoa)
            InkOutlinedButton("Regenerate", viewModel::generateChapterPlan, Modifier.weight(1f))
        }
        InkOutlinedButton("Continue to verification", viewModel::openVerification, Modifier.fillMaxWidth())
    }
}

@Composable
private fun VerificationControls(state: NovelStartUiState, viewModel: NovelStartViewModel) {
    StepColumn {
        Text("Book", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            buildString {
                appendLine("Title: ${state.setup.title}")
                if (state.setup.genre.isNotBlank()) appendLine("Genre: ${state.setup.genre}")
                if (state.setup.setting.isNotBlank()) appendLine("Setting: ${state.setup.setting}")
                if (state.setup.characters.isNotBlank()) appendLine("Cast: ${state.setup.characters}")
                appendLine("POV: ${state.setup.pointOfView}; ${state.setup.tense}")
                append("Company: ${StoryCompanionMode.fromId(state.setup.companions).label}")
            },
        )
        Text("Your answers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        viewModel.questions().forEach { question ->
            val answer = state.plan.answers.firstOrNull { it.questionId == question.id }
            Text(
                "${question.id.replaceFirstChar(Char::uppercase)}: " +
                    when {
                        answer == null || (answer.value.isBlank() && !answer.skipped) -> "Not answered"
                        answer.skipped -> "Skipped"
                        else -> answer.value
                    },
            )
        }
        InkTextButton("Edit the story", viewModel::editCyoa)
        Text("Chapter One outline", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            buildString {
                appendLine(state.chapterOutline.workingTitle)
                appendLine(state.chapterOutline.premise)
                appendLine("Drives at: ${state.chapterOutline.primaryObjective}")
                appendLine("Opposition: ${state.chapterOutline.antagonist}")
                appendLine("Places: ${state.chapterOutline.importantLocations}")
                state.chapterOutline.beats.forEachIndexed { index, beat ->
                    appendLine("${index + 1}. ${beat.title}: ${beat.summary}")
                }
                appendLine("Optional beat: ${state.chapterOutline.optionalBeat}")
                appendLine("Hardest moment: ${state.chapterOutline.majorChallenge}")
                appendLine("Peak: ${state.chapterOutline.climax}")
                append("Outcomes: ${state.chapterOutline.possibleOutcomes}")
            },
        )
        Text("Opening-scene guideline", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            buildString {
                appendLine(state.openingScene.title)
                appendLine("Place: ${state.openingScene.locationAndAtmosphere}")
                appendLine("Present: ${state.openingScene.startingCast}")
                appendLine("Objective: ${state.openingScene.immediateObjective}")
                appendLine("Conflict: ${state.openingScene.conflictAndStakes}")
                appendLine("Complication: ${state.openingScene.complication}")
                appendLine("Hook: ${state.openingScene.firstDecisionHook}")
                append("Art tags: ${state.openingScene.sceneArtTags}")
            },
        )
        InkTextButton("Edit the chapter plan", viewModel::editChapterPlan)
        InkOutlinedButton("Verify and write Chapter One", viewModel::verifyAndWriteOpeningScene, Modifier.fillMaxWidth())
    }
}

@Composable
private fun OpeningProseControls(state: NovelStartUiState, viewModel: NovelStartViewModel, onClose: () -> Unit) {
    StepColumn {
        if (state.saved) {
            Text(
                "Saved into the book's first scene.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        OutlinedTextField(
            value = state.sceneDraft?.prose.orEmpty(),
            onValueChange = viewModel::editSceneProse,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Chapter One") },
            minLines = 12,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
            InkTextButton("Back to verification", viewModel::openVerification)
            InkTextButton("Rewrite", viewModel::verifyAndWriteOpeningScene)
        }
        InkOutlinedButton("Save changes", viewModel::saveSceneProse, Modifier.fillMaxWidth())
        InkOutlinedButton("Open the book", onClose, Modifier.fillMaxWidth())
    }
}

/** The company clicker: the one constraint models reliably ignore, so it is explicit. */
@Composable
private fun CompanyPicker(selected: StoryCompanionMode, onSelect: (StoryCompanionMode) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = InkSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(InkSpacing.xxs),
    ) {
        Text("Who travels with the protagonist?", style = MaterialTheme.typography.labelLarge)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
        ) {
            StoryCompanionMode.entries.forEach { mode ->
                FilterChip(
                    selected = mode == selected,
                    onClick = { onSelect(mode) },
                    label = { Text(mode.label, maxLines = 1) },
                )
            }
        }
        Text(selected.blurb, style = MaterialTheme.typography.bodySmall, color = inkTokens().secondaryText)
    }
}
