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

/**
 * The novel's four-step start, the same shape as the RPG campaign's: book setup, the
 * Create Your Own Story questions, the Chapter One plan, and the opening scene. The
 * table-only controls — play-as role, game mode, rule system, house rules — have no
 * place in a book and are left out.
 */
@Composable
fun NovelStartScreen(
    bookId: String,
    onFinished: () -> Unit,
    viewModel: NovelStartViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(bookId) { viewModel.load(bookId) }
    LaunchedEffect(state.finished) { if (state.finished) onFinished() }

    Box(Modifier.fillMaxSize().background(inkTokens().background).padding(InkSpacing.xs)) {
        when (state.step) {
            NovelStartStep.Setup -> StartSplit(
                summary = { StepSummary(state, "Book Setup", "Name the book and set how it is told. Everything here can be changed later.") },
                controls = { SetupControls(state, viewModel) },
            )

            NovelStartStep.Story -> StartSplit(
                summary = { StepSummary(state, "Create Your Own Story", "Answer in the boxes. Presets fill a box and stay editable; leave one blank to skip it.") },
                controls = { StoryControls(state, viewModel) },
            )

            NovelStartStep.Outline -> StartSplit(
                summary = { StepSummary(state, "Chapter One Plan", "Edit the AI's rough outline. These beats guide the book; they never lock you in.") },
                controls = { OutlineControls(state, viewModel) },
            )

            NovelStartStep.Opening -> StartSplit(
                summary = { StepSummary(state, "Opening Scene", "The first scene of Chapter One, written from the plan. Edit it here before it is saved.") },
                controls = { OpeningControls(state, viewModel) },
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

/** Side by side in landscape, stacked in portrait — the RPG start's own layout. */
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
        Text(state.setup.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.bodyLarge)
        if (state.setup.genre.isNotBlank()) Text(state.setup.genre, style = MaterialTheme.typography.bodyMedium)
        Text(
            "${state.setup.pointOfView} · ${state.setup.tense}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text("Company: ${state.setup.companions.label}", style = MaterialTheme.typography.bodySmall)
        if (state.status.isNotBlank()) {
            Text(state.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (state.error.isNotBlank()) {
            Text(state.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Text(
            "Step ${state.step.ordinal + 1} of 4",
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun StepColumn(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(InkSpacing.xs),
    ) { content() }
}

@Composable
private fun SetupControls(state: NovelStartUiState, viewModel: NovelStartViewModel) {
    val setup = state.setup
    StepColumn {
        OutlinedTextField(setup.title, { v -> viewModel.updateSetup { it.copy(title = v) } }, Modifier.fillMaxWidth(), label = { Text("Title") })
        OutlinedTextField(setup.genre, { v -> viewModel.updateSetup { it.copy(genre = v) } }, Modifier.fillMaxWidth(), label = { Text("Genre") })
        OutlinedTextField(setup.setting, { v -> viewModel.updateSetup { it.copy(setting = v) } }, Modifier.fillMaxWidth(), label = { Text("Setting") }, minLines = 2)
        OutlinedTextField(setup.pointOfView, { v -> viewModel.updateSetup { it.copy(pointOfView = v) } }, Modifier.fillMaxWidth(), label = { Text("Point of view") })
        PresetRow(listOf("First person", "Third-person limited", "Third-person omniscient", "Second person")) { v ->
            viewModel.updateSetup { it.copy(pointOfView = v) }
        }
        OutlinedTextField(setup.tense, { v -> viewModel.updateSetup { it.copy(tense = v) } }, Modifier.fillMaxWidth(), label = { Text("Tense") })
        PresetRow(listOf("Past tense", "Present tense")) { v -> viewModel.updateSetup { it.copy(tense = v) } }
        OutlinedTextField(setup.characters, { v -> viewModel.updateSetup { it.copy(characters = v) } }, Modifier.fillMaxWidth(), label = { Text("Main characters") }, minLines = 2)
        OutlinedTextField(setup.styleGuide, { v -> viewModel.updateSetup { it.copy(styleGuide = v) } }, Modifier.fillMaxWidth(), label = { Text("Style guide") }, minLines = 2)
        CompanyPicker(setup.companions) { mode -> viewModel.updateSetup { it.copy(companions = mode) } }
        InkOutlinedButton("Continue to the story questions", viewModel::startStoryStep, Modifier.fillMaxWidth())
    }
}

@Composable
private fun StoryControls(state: NovelStartUiState, viewModel: NovelStartViewModel) {
    StepColumn {
        if (state.busy) {
            InkOutlinedButton("Stop AI suggestions", viewModel::cancel, Modifier.fillMaxWidth())
        }
        viewModel.questions().forEachIndexed { index, question ->
            val choices = (state.suggestions[question.id].orEmpty() + question.presets).distinct()
            Column(verticalArrangement = Arrangement.spacedBy(InkSpacing.xxs)) {
                OutlinedTextField(
                    value = state.answers[question.id].orEmpty(),
                    onValueChange = { viewModel.setAnswer(question.id, it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("${index + 1}. ${question.prompt}") },
                    singleLine = false,
                )
                PresetRow(choices) { viewModel.setAnswer(question.id, it) }
            }
        }
        InkTextButton("Refresh AI suggestions", viewModel::suggest)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
            InkTextButton("Back to setup", viewModel::back)
            InkOutlinedButton("Plan Chapter One", viewModel::buildOutline, Modifier.weight(1f))
        }
    }
}

@Composable
private fun OutlineControls(state: NovelStartUiState, viewModel: NovelStartViewModel) {
    val outline = state.outline
    fun update(next: com.ihy2ln.weaverse.feature.roleplay.campaign.RpgChapterOutline) = viewModel.editOutline { next }
    StepColumn {
        OutlinedTextField(outline.workingTitle, { update(outline.copy(workingTitle = it)) }, Modifier.fillMaxWidth(), label = { Text("Chapter title") })
        OutlinedTextField(outline.premise, { update(outline.copy(premise = it)) }, Modifier.fillMaxWidth(), label = { Text("Premise") }, minLines = 2)
        OutlinedTextField(outline.primaryObjective, { update(outline.copy(primaryObjective = it)) }, Modifier.fillMaxWidth(), label = { Text("What the chapter is driving at") })
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
            label = { Text("Three to five beats") },
            minLines = 4,
        )
        OutlinedTextField(outline.climax, { update(outline.copy(climax = it)) }, Modifier.fillMaxWidth(), label = { Text("Where the chapter peaks") })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
            InkTextButton("Back to the questions", viewModel::back)
            InkTextButton("Regenerate", viewModel::buildOutline)
        }
        InkOutlinedButton("Write the opening scene", viewModel::writeOpening, Modifier.fillMaxWidth())
    }
}

@Composable
private fun OpeningControls(state: NovelStartUiState, viewModel: NovelStartViewModel) {
    StepColumn {
        OutlinedTextField(
            value = state.openingProse,
            onValueChange = viewModel::editOpening,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Opening scene") },
            minLines = 12,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
            InkTextButton("Back to the plan", viewModel::back)
            InkTextButton("Rewrite", viewModel::writeOpening)
        }
        InkOutlinedButton("Save and open the book", viewModel::finish, Modifier.fillMaxWidth())
    }
}

@Composable
private fun PresetRow(presets: List<String>, onPick: (String) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
    ) {
        items(presets) { preset -> InkTextButton(label = preset, onClick = { onPick(preset) }, compact = true) }
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
