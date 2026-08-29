package com.ihy2ln.weaverse.feature.roleplay.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.ihy2ln.weaverse.core.ui.components.CollapsibleUsageStrip
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.core.ui.components.mergeSpokenText
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusMd
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.feature.prompt.PromptModelPickerDialog
import com.ihy2ln.weaverse.feature.prompt.PromptModelSelection
import com.ihy2ln.weaverse.feature.prompt.PromptWordLimit
import com.ihy2ln.weaverse.feature.prompt.UnifiedPromptBar
import java.io.File

/**
 * The RPG session reads as an illustrated adventure page rather than a chat.
 * Messages remain the persistence/generation engine, but are rendered as story
 * paragraphs and clearly separated player actions.
 */
@Composable
fun AdventurePlayScreen(
    chatId: String,
    onChromeChange: (RoleplayChatChrome?) -> Unit = {},
    viewModel: RoleplayChatViewModel = hiltViewModel(),
) {
    LaunchedEffect(chatId) {
        viewModel.bindChat(chatId)
        viewModel.setDisplayMode("dungeonMaster")
    }
    val state by viewModel.uiState.collectAsState()
    val tokens = inkTokens()
    val storyState = rememberLazyListState()
    var promptCollapsed by rememberSaveable { mutableStateOf(false) }
    var modelsOpen by remember { mutableStateOf(false) }
    var modelSearch by rememberSaveable { mutableStateOf("") }
    var minimumWordsText by rememberSaveable { mutableStateOf(state.minimumOutputWords.toString()) }
    var maximumWordsText by rememberSaveable { mutableStateOf(state.outputWords.toString()) }
    LaunchedEffect(state.minimumOutputWords) {
        if (minimumWordsText.toIntOrNull() != state.minimumOutputWords) {
            minimumWordsText = state.minimumOutputWords.toString()
        }
    }
    LaunchedEffect(state.outputWords) {
        if (maximumWordsText.toIntOrNull() != state.outputWords) {
            maximumWordsText = state.outputWords.toString()
        }
    }
    val minWords = minimumWordsText.toIntOrNull()
    val maxWords = maximumWordsText.toIntOrNull()
    val wordRangeValid = minWords != null && maxWords != null &&
        minWords in PromptWordLimit.Minimum..PromptWordLimit.Maximum &&
        maxWords in PromptWordLimit.Minimum..PromptWordLimit.Maximum && minWords <= maxWords

    LaunchedEffect(state.title) {
        onChromeChange(
            RoleplayChatChrome(
                title = state.title.ifBlank { "Adventure" },
                displayMode = "dungeonMaster",
                onDisplayMode = {},
                showSwitcher = false,
            ),
        )
    }
    DisposableEffect(Unit) { onDispose { onChromeChange(null) } }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.attachMedia(uris) else viewModel.clearMediaPickRequest()
    }
    LaunchedEffect(state.mediaPickRequestId) {
        if (state.mediaPickRequestId > 0L) {
            mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
    LaunchedEffect(state.messages.size, state.streamingText) {
        val target = state.messages.size + if (state.streamingText.isNotBlank()) 1 else 0
        if (target > 0) runCatching { storyState.animateScrollToItem(target - 1) }
    }

    val sceneArt = state.mediaPanels.lastOrNull { it.path.isNotBlank() && !it.isAudio }
    val startupPending = state.adventureStartupPhase in setOf(
        AdventureStartupPhase.Choose,
        AdventureStartupPhase.Questions,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.background),
    ) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.42f)
                .heightIn(min = 190.dp, max = 360.dp)
                .padding(horizontal = InkSpacing.md, vertical = InkSpacing.sm)
                .clip(RoundedCornerShape(inkRadiusMd()))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                            tokens.panel,
                        ),
                    ),
                )
                .clickable(onClickLabel = "Choose scene art") { viewModel.requestMediaPick() },
            contentAlignment = Alignment.Center,
        ) {
            if (sceneArt != null) {
                AsyncImage(
                    model = File(sceneArt.path),
                    contentDescription = "Current adventure scene",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))),
                        )
                        .padding(InkSpacing.md),
                ) {
                    Text(
                        "Current scene · tap to change art",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("◈", style = MaterialTheme.typography.displaySmall, color = tokens.activePill)
                    Text("Add scene art", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "A single illustration anchors the current adventure scene.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.secondaryText,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(0.58f)
                .fillMaxWidth()
                .padding(horizontal = InkSpacing.lg),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        if (startupPending) "Adventure setup" else "Scene ${state.sceneNumber}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (state.userIsDungeonMaster) "DM mode · You run the world" else "Adventure story",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.secondaryText,
                    )
                }
                if (!startupPending) Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.canGoToPreviousScene) {
                        InkTextButton(
                            label = "‹ Previous",
                            onClick = viewModel::previousScene,
                            compact = true,
                        )
                    }
                    if (state.canUndoSceneAdvance) {
                        InkTextButton(
                            label = "Stay here",
                            onClick = viewModel::undoLastSceneAdvance,
                            compact = true,
                        )
                    }
                    InkTextButton(
                        label = if (state.viewingCurrentScene) "Next scene ›" else "Next ›",
                        onClick = viewModel::advanceScene,
                        compact = true,
                    )
                }
            }
            LazyColumn(
                state = storyState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(InkSpacing.sm),
            ) {
                if (state.messages.isEmpty() && state.streamingText.isBlank()) {
                    item("empty") {
                        Text(
                            "The scene is waiting for its first action.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = tokens.secondaryText,
                        )
                    }
                }
                items(state.messages, key = { it.id }) { message ->
                    if (message.role == "user") {
                        Text(
                            if (message.isAdventureSetup) {
                                "Setup answer — ${message.text}"
                            } else if (state.userIsDungeonMaster) {
                                "Your DM prompt — ${message.text}"
                            } else {
                                "Your action — ${message.text}"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = tokens.secondaryText,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(tokens.hover, RoundedCornerShape(inkRadiusMd()))
                                .padding(InkSpacing.sm),
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (message.isAdventureSetup) {
                                Text(
                                    "ADVENTURE SETUP · AI DM",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = tokens.activePill,
                                    modifier = Modifier.padding(bottom = InkSpacing.xs),
                                )
                            }
                            if (message.actionResult.isNotBlank()) {
                                Text(
                                    "ACTION RESULT · ${message.actionResult.uppercase()}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = tokens.activePill,
                                    modifier = Modifier
                                        .background(tokens.hover, RoundedCornerShape(inkRadiusMd()))
                                        .padding(horizontal = InkSpacing.sm, vertical = InkSpacing.xs),
                                )
                            }
                            Text(
                                message.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = tokens.primaryText,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                if (state.streamingText.isNotBlank()) {
                    item("streaming") {
                        Text(
                            state.streamingText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = tokens.primaryText,
                        )
                    }
                }
            }
        }

        if (state.errorMessage.isNotBlank()) {
            Text(
                state.errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = InkSpacing.lg),
            )
        }
        CollapsibleUsageStrip(state.lastUsage, Modifier.padding(horizontal = InkSpacing.lg))
        UnifiedPromptBar(
            value = state.input,
            onValueChange = viewModel::onInputChange,
            placeholder = if (state.adventureStartupPhase == AdventureStartupPhase.Choose) {
                "Choose 1, 2, or 3…"
            } else if (state.adventureStartupPhase == AdventureStartupPhase.Questions) {
                "Answer the AI DM's setup questions…"
            } else if (state.userIsDungeonMaster) {
                "What happens next? · Describe the scene, NPC response, or ruling…"
            } else {
                "What do you do? · Describe your action…"
            },
            collapsed = promptCollapsed,
            onCollapsedChange = { promptCollapsed = it },
            contextLabel = state.contextMeter?.label.orEmpty(),
            minimumWords = minimumWordsText,
            maximumWords = maximumWordsText,
            onMinimumWordsChange = { value ->
                minimumWordsText = value.filter(Char::isDigit).take(4)
                minimumWordsText.toIntOrNull()?.let(viewModel::updateMinimumOutputWords)
            },
            onMaximumWordsChange = { value ->
                maximumWordsText = value.filter(Char::isDigit).take(4)
                maximumWordsText.toIntOrNull()?.let(viewModel::updateOutputWords)
            },
            wordRangeValid = wordRangeValid,
            modelLabel = PromptModelSelection.shortLabel(
                PromptModelSelection.effectiveModelRef(
                    state.selectedModelRef,
                    state.defaultModelRef,
                ),
                state.writingModels,
            ),
            onModelClick = { modelsOpen = true },
            aiMode = startupPending || state.entryMode != "nai",
            streaming = state.isStreaming,
            onToggleMode = {
                if (!startupPending) {
                    viewModel.setEntryMode(if (state.entryMode == "nai") "ai" else "nai")
                }
            },
            canSubmit = state.input.isNotBlank() && wordRangeValid,
            canClear = state.input.isNotBlank(),
            onSubmit = viewModel::send,
            onCancel = viewModel::cancelGeneration,
            onClear = { viewModel.onInputChange("") },
            onSpoken = { spoken ->
                viewModel.onInputChange(mergeSpokenText(state.input, spoken))
            },
            onAdd = viewModel::requestMediaPick,
            addSelected = sceneArt != null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = InkSpacing.sm, vertical = InkSpacing.xs),
        )
    }
    PixelDiceRollOverlay(
        roll = state.activeRoll,
        sequence = state.rollAnimationId,
        modifier = Modifier.align(Alignment.Center),
    )
    if (modelsOpen) {
        PromptModelPickerDialog(
            models = state.writingModels,
            search = modelSearch,
            onSearchChange = { modelSearch = it },
            selectedRef = state.selectedModelRef,
            defaultRef = state.defaultModelRef,
            onSelect = { id ->
                viewModel.selectModel(id)
                modelsOpen = false
            },
            onUseDefault = {
                viewModel.useDefaultModel()
                modelsOpen = false
            },
            onDismiss = { modelsOpen = false },
        )
    }
    }
}
