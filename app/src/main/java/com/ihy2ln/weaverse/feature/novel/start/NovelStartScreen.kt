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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.story.StoryCompanionMode
import com.ihy2ln.weaverse.core.ui.components.CampaignPerspectiveTemplates
import com.ihy2ln.weaverse.core.ui.components.CampaignPresetBrowserDialog
import com.ihy2ln.weaverse.core.ui.components.CampaignPresetEditorDialog
import com.ihy2ln.weaverse.core.ui.components.CampaignSettingDetailTemplate
import com.ihy2ln.weaverse.core.ui.components.CampaignSettingTemplate
import com.ihy2ln.weaverse.core.ui.components.CampaignSettingTemplates
import com.ihy2ln.weaverse.core.ui.components.CampaignSettingDetailTemplates
import com.ihy2ln.weaverse.core.ui.components.InkChip
import com.ihy2ln.weaverse.core.ui.components.InkOutlinedButton
import com.ihy2ln.weaverse.core.ui.components.InkSegmentedPill
import com.ihy2ln.weaverse.core.ui.components.SegmentedOption
import com.ihy2ln.weaverse.core.ui.components.WorkCharacterOption
import com.ihy2ln.weaverse.core.ui.components.campaignSettingBrowserItems
import com.ihy2ln.weaverse.core.ui.components.campaignSettingDetailBrowserItems
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
    characterOptions: List<WorkCharacterOption> = emptyList(),
    customSettings: List<CampaignSettingTemplate> = emptyList(),
    customSettingDetails: List<CampaignSettingDetailTemplate> = emptyList(),
    favoriteSettingIds: Set<String> = emptySet(),
    favoriteSettingDetailIds: Set<String> = emptySet(),
    onToggleSettingFavorite: (String) -> Unit = {},
    onToggleSettingDetailFavorite: (String) -> Unit = {},
    onAddSetting: ((String, String, String, String) -> Unit)? = null,
    onRemoveSetting: ((String) -> Unit)? = null,
    onAddSettingDetail: ((String, String, String, String) -> Unit)? = null,
    onRemoveSettingDetail: ((String) -> Unit)? = null,
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

    val resumable = state.resumable
    if (resumable != null) {
        Box(Modifier.fillMaxSize().background(tokens.background).padding(InkSpacing.xs)) {
            StartPane(Modifier.fillMaxSize()) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(InkSpacing.sm),
                ) {
                    Text("Pick up where you left off?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "This book has a start you did not finish.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.secondaryText,
                    )
                    Text(
                        novelStartProgressSummary(resumable),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (resumable.setup.title.isNotBlank()) {
                        Text(resumable.setup.title, style = MaterialTheme.typography.bodyMedium)
                    }
                    InkOutlinedButton("Continue", viewModel::resume, Modifier.fillMaxWidth())
                    if (state.hasCyoaCheckpoint) {
                        InkTextButton("Back to CYOA set up", viewModel::restoreCyoaCheckpoint)
                    }
                    InkTextButton("Start from template", viewModel::startFromTemplate)
                    InkTextButton("Start over", viewModel::startOver)
                    InkTextButton("Close", onClose)
                }
            }
        }
        return
    }

    Box(Modifier.fillMaxSize().background(tokens.background).padding(InkSpacing.xs)) {
        when (state.step) {
            NovelStartStep.Setup -> StartSplit(
                summary = { StepSummary(state, "Book Setup", "Name the book and choose its templates. Everything here can be changed later from the editor.") },
                controls = {
                    SetupControls(
                        state = state,
                        viewModel = viewModel,
                        onClose = onClose,
                        characterOptions = characterOptions,
                        customSettings = customSettings,
                        customSettingDetails = customSettingDetails,
                        favoriteSettingIds = favoriteSettingIds,
                        favoriteSettingDetailIds = favoriteSettingDetailIds,
                        onToggleSettingFavorite = onToggleSettingFavorite,
                        onToggleSettingDetailFavorite = onToggleSettingDetailFavorite,
                        onAddSetting = onAddSetting,
                        onRemoveSetting = onRemoveSetting,
                        onAddSettingDetail = onAddSettingDetail,
                        onRemoveSettingDetail = onRemoveSettingDetail,
                    )
                },
            )

            NovelStartStep.Cyoa -> StartSplit(
                summary = { StepSummary(state, "Create Your Own Story", "Answer in the boxes. Presets fill a box and remain editable; Skip leaves it blank.") },
                controls = { CyoaControls(state, viewModel) },
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
                summary = { StepSummary(state, "Verify Your Story", "Review the book, your answers, the outline, and the opening scene before the set-up paragraph is written.") },
                controls = { VerificationControls(state, viewModel) },
            )

            NovelStartStep.GeneratingScene -> StartSplit(
                summary = { StepSummary(state, "Opening Set-Up", "The verified plan is being turned into a single paragraph that sets the opening scene up for you.") },
                controls = {
                    GenerationPanel(
                        state = state,
                        label = "Setting up the opening",
                        working = "The AI is writing the set-up paragraph. It lands in the book's first scene once it is saved.",
                        onRetry = viewModel::verifyAndWriteOpeningScene,
                        onFallback = viewModel::useAuthoredOpeningScene,
                        onCancel = viewModel::cancelGeneration,
                    )
                },
            )

            NovelStartStep.Started -> StartSplit(
                summary = { StepSummary(state, "Opening Set-Up", "One paragraph, saved into the book's first scene. The scene itself is yours to write — edit the set-up here, or open the book and carry on.") },
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

/**
 * Step one: the book's name and its templates. This is the campaign's own Setup
 * screen — the Setting Template and Setting Details browsers with favourites and
 * custom presets, the main-character picker, perspective and tense — minus everything
 * that only exists for a table: play-as role, game mode, rule system, house rules.
 */
@Composable
private fun SetupControls(
    state: NovelStartUiState,
    viewModel: NovelStartViewModel,
    onClose: () -> Unit,
    characterOptions: List<WorkCharacterOption>,
    customSettings: List<CampaignSettingTemplate>,
    customSettingDetails: List<CampaignSettingDetailTemplate>,
    favoriteSettingIds: Set<String>,
    favoriteSettingDetailIds: Set<String>,
    onToggleSettingFavorite: (String) -> Unit,
    onToggleSettingDetailFavorite: (String) -> Unit,
    onAddSetting: ((String, String, String, String) -> Unit)?,
    onRemoveSetting: ((String) -> Unit)?,
    onAddSettingDetail: ((String, String, String, String) -> Unit)?,
    onRemoveSettingDetail: ((String) -> Unit)?,
) {
    val tokens = inkTokens()
    val setup = state.setup
    var settingBrowserOpen by remember { mutableStateOf(false) }
    var settingDetailBrowserOpen by remember { mutableStateOf(false) }
    var perspectiveMenuOpen by remember { mutableStateOf(false) }
    var styleMenuOpen by remember { mutableStateOf(false) }
    var styleTemplateId by remember { mutableStateOf("") }
    var showAddSetting by remember { mutableStateOf(false) }
    var showAddSettingDetail by remember { mutableStateOf(false) }
    var characterQuery by remember { mutableStateOf("") }
    var selectedCharacterIds by remember { mutableStateOf(setOf<String>()) }
    val effectiveSettings = CampaignSettingTemplates + customSettings
    val effectiveSettingDetails = CampaignSettingDetailTemplates + customSettingDetails

    StepColumn {
        OutlinedTextField(
            value = setup.title,
            onValueChange = { value -> viewModel.updateSetup { it.copy(title = value) } },
            label = { Text("Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = setup.genre,
            onValueChange = { value -> viewModel.updateSetup { it.copy(genre = value) } },
            label = { Text("Genre") },
            placeholder = { Text("Type one, or tap a template below") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // The templates only fill the field; a typed genre is never overwritten.
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
        ) {
            items(novelGenreTemplates()) { genre ->
                InkTextButton(
                    label = genre,
                    onClick = { viewModel.updateSetup { it.copy(genre = genre) } },
                    compact = true,
                )
            }
        }

        Text("Setting template", style = MaterialTheme.typography.labelMedium)
        InkOutlinedButton(
            label = (effectiveSettings.firstOrNull { it.id == setup.settingId }?.label ?: "Choose setting") + " ▸",
            onClick = { settingBrowserOpen = true },
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Setting Details preset", style = MaterialTheme.typography.labelMedium)
        InkOutlinedButton(
            label = (effectiveSettingDetails.firstOrNull { it.id == setup.settingDetailId }?.label ?: "Choose details") + " ▸",
            onClick = { settingDetailBrowserOpen = true },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = setup.settingDetails,
            onValueChange = { value -> viewModel.updateSetup { it.copy(settingDetails = value) } },
            label = { Text("Setting details") },
            placeholder = { Text("Place, era, locations, factions, tone…") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Main character(s)", style = MaterialTheme.typography.labelMedium)
        if (characterOptions.isEmpty()) {
            OutlinedTextField(
                value = setup.characters,
                onValueChange = { value -> viewModel.updateSetup { it.copy(characters = value) } },
                label = { Text("Who the book follows") },
                placeholder = { Text("Name them, or add Codex entries first") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            OutlinedTextField(
                value = characterQuery,
                onValueChange = { characterQuery = it },
                label = { Text("Search characters") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            val visible = if (characterQuery.isBlank()) {
                characterOptions
            } else {
                characterOptions.filter { it.name.contains(characterQuery, ignoreCase = true) }
            }
            if (visible.isEmpty()) {
                Text(
                    "No characters match that search.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.secondaryText,
                )
            } else {
                // Two rows of names are visible at most; a longer cast scrolls.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
                        visible.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
                                row.forEach { option ->
                                    val selected = option.id in selectedCharacterIds
                                    InkChip(
                                        label = if (selected) "✓ " + option.name else option.name,
                                        color = MaterialTheme.colorScheme.primary,
                                        selected = selected,
                                        onClick = {
                                            selectedCharacterIds = if (selected) {
                                                selectedCharacterIds - option.id
                                            } else {
                                                selectedCharacterIds + option.id
                                            }
                                            val names = characterOptions
                                                .filter { it.id in selectedCharacterIds }
                                                .joinToString(", ") { it.name }
                                            viewModel.updateSetup { it.copy(characters = names) }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                if (visible.size > 6) {
                    Text(
                        "${visible.size} characters — scroll for the rest.",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.secondaryText,
                    )
                }
            }
            if (setup.characters.isNotBlank()) {
                Text(
                    setup.characters,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.secondaryText,
                    maxLines = 2,
                )
            }
        }

        Text("Point of view", style = MaterialTheme.typography.labelMedium)
        Box(Modifier.fillMaxWidth()) {
            InkOutlinedButton(
                label = (
                    CampaignPerspectiveTemplates.firstOrNull { it.id == setup.narrativePovId }?.label
                        ?: setup.pointOfView
                    ) + " ▾",
                onClick = { perspectiveMenuOpen = true },
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownMenu(expanded = perspectiveMenuOpen, onDismissRequest = { perspectiveMenuOpen = false }) {
                CampaignPerspectiveTemplates.forEach { template ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(template.label)
                                Text(
                                    novelizeGuidance(template.directive),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.secondaryText,
                                    maxLines = 3,
                                )
                            }
                        },
                        onClick = {
                            viewModel.selectPerspective(template.id, template.label, template.directive)
                            perspectiveMenuOpen = false
                        },
                    )
                }
            }
        }
        Text("Tense", style = MaterialTheme.typography.labelMedium)
        InkSegmentedPill(
            options = listOf(
                SegmentedOption("Past tense", "Past"),
                SegmentedOption("Present tense", "Present"),
                SegmentedOption("Future tense", "Future"),
            ),
            selectedId = setup.tense.ifBlank { "Past tense" },
            onSelect = { value -> viewModel.updateSetup { it.copy(tense = value) } },
            compact = true,
        )

        Text("Style guide template", style = MaterialTheme.typography.labelMedium)
        Box(Modifier.fillMaxWidth()) {
            InkOutlinedButton(
                label = (
                    novelStyleGuideTemplates().firstOrNull { it.id == styleTemplateId }?.label
                        ?: "Choose a style"
                    ) + " ▾",
                onClick = { styleMenuOpen = true },
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownMenu(expanded = styleMenuOpen, onDismissRequest = { styleMenuOpen = false }) {
                novelStyleGuideTemplates().forEach { template ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(template.label)
                                Text(
                                    template.guidance,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.secondaryText,
                                    maxLines = 3,
                                )
                            }
                        },
                        onClick = {
                            styleTemplateId = template.id
                            // The template writes the field; it stays editable after.
                            viewModel.updateSetup { it.copy(styleGuide = template.guidance) }
                            styleMenuOpen = false
                        },
                    )
                }
            }
        }
        OutlinedTextField(
            value = setup.styleGuide,
            onValueChange = { value -> viewModel.updateSetup { it.copy(styleGuide = value) } },
            label = { Text("Style guide") },
            placeholder = { Text("Type your own, or pick a template above") },
            minLines = 3,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Voice, pacing, anything the AI should keep to. A template fills this box and stays editable.",
            style = MaterialTheme.typography.labelSmall,
            color = tokens.secondaryText,
        )

        CompanyPicker(StoryCompanionMode.fromId(setup.companions), viewModel::setCompanions)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
            InkTextButton("Close", onClose)
            InkTextButton("Start over", viewModel::startOver)
            InkOutlinedButton("Continue to the story", viewModel::openCyoa, Modifier.weight(1f))
        }
        SaveSlotBar(state, viewModel, offerTemplate = true, offerSave = true)
        Text(
            "Your answers are kept as you go — closing this and coming back offers to continue.",
            style = MaterialTheme.typography.labelSmall,
            color = tokens.secondaryText,
        )
    }

    if (settingBrowserOpen) {
        CampaignPresetBrowserDialog(
            title = "Setting templates",
            items = campaignSettingBrowserItems(customSettings),
            selectedId = setup.settingId,
            favoriteIds = favoriteSettingIds,
            onToggleFavorite = onToggleSettingFavorite,
            onSelect = { preset ->
                val directive = effectiveSettings.firstOrNull { it.id == preset.id }?.directive.orEmpty()
                viewModel.selectSettingTemplate(preset.id, preset.label, directive)
                settingBrowserOpen = false
            },
            onDismiss = { settingBrowserOpen = false },
            onRemove = onRemoveSetting,
            onAdd = onAddSetting?.let { { settingBrowserOpen = false; showAddSetting = true } },
            addLabel = "Add setting template",
        )
    }
    if (settingDetailBrowserOpen) {
        CampaignPresetBrowserDialog(
            title = "Setting details",
            items = campaignSettingDetailBrowserItems(customSettingDetails),
            selectedId = setup.settingDetailId,
            favoriteIds = favoriteSettingDetailIds,
            onToggleFavorite = onToggleSettingDetailFavorite,
            onSelect = { preset ->
                viewModel.selectSettingDetails(preset.id, preset.description)
                settingDetailBrowserOpen = false
            },
            onDismiss = { settingDetailBrowserOpen = false },
            onRemove = onRemoveSettingDetail,
            onAdd = onAddSettingDetail?.let { { settingDetailBrowserOpen = false; showAddSettingDetail = true } },
            addLabel = "Add details preset",
        )
    }
    if (showAddSetting && onAddSetting != null) {
        CampaignPresetEditorDialog(
            title = "Add Setting Template",
            guidanceLabel = "World guidance for the AI",
            defaultSection = "Custom",
            defaultTheme = "Saved templates",
            onDismiss = { showAddSetting = false },
            onSave = onAddSetting,
        )
    }
    if (showAddSettingDetail && onAddSettingDetail != null) {
        CampaignPresetEditorDialog(
            title = "Add Setting Details Preset",
            guidanceLabel = "Setting details for the AI",
            defaultSection = "Custom",
            defaultTheme = "Saved presets",
            onDismiss = { showAddSettingDetail = false },
            onSave = onAddSettingDetail,
        )
    }
}

@Composable
private fun CyoaControls(state: NovelStartUiState, viewModel: NovelStartViewModel) {
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
            InkTextButton("Back to setup", viewModel::editSetup)
            InkOutlinedButton("Create chapter plan", viewModel::generateChapterPlan, Modifier.weight(1f))
        }
        SaveSlotBar(state, viewModel, offerTemplate = false, offerSave = true)
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
        SaveSlotBar(state, viewModel, offerTemplate = false, offerSave = true)
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
        InkOutlinedButton("Verify and set up the opening", viewModel::verifyAndWriteOpeningScene, Modifier.fillMaxWidth())
        SaveSlotBar(state, viewModel, offerTemplate = false, offerSave = true)
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
            label = { Text("Opening set-up") },
            minLines = 5,
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
/**
 * The start's save slots: the "CYOA set up" checkpoint, and the starting template
 * that skips straight to verification.
 */
@Composable
private fun SaveSlotBar(
    state: NovelStartUiState,
    viewModel: NovelStartViewModel,
    offerTemplate: Boolean,
    offerSave: Boolean,
) {
    val tokens = inkTokens()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
        if (offerTemplate) InkTextButton("Start from template", viewModel::startFromTemplate)
        if (state.hasCyoaCheckpoint) InkTextButton("Back to CYOA set up", viewModel::restoreCyoaCheckpoint)
        if (offerSave) InkTextButton("Save as template", viewModel::saveAsTemplate)
    }
    if (state.slotMessage.isNotBlank()) {
        Text(state.slotMessage, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    } else if (offerTemplate) {
        Text(
            if (state.hasSavedTemplate) "Start from template loads your saved template and skips to verification."
            else "Start from template fills every answer and the chapter plan, and skips to verification.",
            style = MaterialTheme.typography.labelSmall,
            color = tokens.secondaryText,
        )
    }
}

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
