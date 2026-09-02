package com.ihy2ln.weaverse.feature.roleplay.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.ihy2ln.weaverse.core.text.CodexMentionTag
import com.ihy2ln.weaverse.core.text.CodexMentionTarget
import com.ihy2ln.weaverse.core.text.findCodexMentions
import com.ihy2ln.weaverse.core.ui.components.CollapsibleUsageStrip
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.core.ui.components.mergeSpokenText
import com.ihy2ln.weaverse.core.ui.components.rememberSpeechToText
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusMd
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusSm
import com.ihy2ln.weaverse.feature.novel.codex.AddTextDialog
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
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AdventurePlayScreen(
    chatId: String,
    onChromeChange: (RoleplayChatChrome?) -> Unit = {},
    onOpenCodexEntry: (String) -> Unit = {},
    viewModel: RoleplayChatViewModel = hiltViewModel(),
) {
    LaunchedEffect(chatId) {
        viewModel.bindChat(chatId)
        viewModel.setDisplayMode("dungeonMaster")
    }
    val state by viewModel.uiState.collectAsState()
    val tokens = inkTokens()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var captureMenuFor by remember { mutableStateOf<RpMessageUi?>(null) }
    val storyState = rememberLazyListState()
    var promptCollapsed by rememberSaveable { mutableStateOf(false) }
    var modelsOpen by remember { mutableStateOf(false) }
    var showAddText by remember { mutableStateOf(false) }
    var sceneArtMenuOpen by remember { mutableStateOf(false) }
    var showAppPictures by remember { mutableStateOf(false) }
    // 0 normal, 1 collapsed (thin strip), 2 enlarged.
    var sceneArtSize by rememberSaveable { mutableStateOf(0) }
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
    val startDictate = rememberSpeechToText { spoken ->
        viewModel.onInputChange(mergeSpokenText(state.input, spoken))
    }
    LaunchedEffect(state.mediaPickRequestId) {
        if (state.mediaPickRequestId > 0L) {
            mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            // Consume the request so returning to this screen never re-launches
            // the picker on its own.
            viewModel.clearMediaPickRequest()
        }
    }
    LaunchedEffect(state.messages.size, state.streamingText) {
        val target = state.messages.size + if (state.streamingText.isNotBlank()) 1 else 0
        if (target > 0) runCatching { storyState.animateScrollToItem(target - 1) }
    }

    val sceneArt = state.mediaPanels.lastOrNull { it.path.isNotBlank() && !it.isAudio }
    val startupPending = state.adventureStartupPhase in setOf(
        AdventureStartupPhase.Character,
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
                .then(
                    when (sceneArtSize) {
                        1 -> Modifier.height(48.dp)
                        2 -> Modifier.weight(0.62f)
                        else -> Modifier.weight(0.42f)
                    },
                )
                .then(
                    if (sceneArtSize == 1) {
                        Modifier
                    } else {
                        Modifier.heightIn(min = 190.dp, max = 360.dp)
                    },
                )
                .padding(horizontal = InkSpacing.md, vertical = if (sceneArtSize == 1) 2.dp else InkSpacing.sm)
                .clip(RoundedCornerShape(inkRadiusMd()))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                            tokens.panel,
                        ),
                    ),
                ),
            // The whole panel is intentionally NOT clickable: an accidental tap
            // (e.g. back-navigation focus) used to auto-open the gallery. The
            // collapsed "Scene art" chip below is the only trigger.
            contentAlignment = Alignment.Center,
        ) {
            if (sceneArtSize != 1) {
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
                        "Current scene",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("◈", style = MaterialTheme.typography.displaySmall, color = tokens.activePill)
                    Text(
                        "No scene art yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Use the Scene art chip to add one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.secondaryText,
                    )
                }
            }
            }
            // Collapsed trigger — the only way to open a picture source. The menu
            // offers the app Pictures library, the device, and panel sizing.
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                Text(
                    if (sceneArtSize == 1) "▤ Scene art ▸" else "▤ Scene art ▾",
                    modifier = Modifier
                        .padding(6.dp)
                        .clip(RoundedCornerShape(inkRadiusSm()))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable(onClickLabel = "Choose scene art") { sceneArtMenuOpen = true }
                        .padding(horizontal = InkSpacing.sm, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
                DropdownMenu(
                    expanded = sceneArtMenuOpen,
                    onDismissRequest = { sceneArtMenuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("App Pictures…") },
                        onClick = {
                            sceneArtMenuOpen = false
                            showAppPictures = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Device gallery…") },
                        onClick = {
                            sceneArtMenuOpen = false
                            viewModel.requestMediaPick()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (sceneArtSize == 1) "Expand" else "Collapse") },
                        onClick = {
                            sceneArtSize = if (sceneArtSize == 1) 0 else 1
                            sceneArtMenuOpen = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (sceneArtSize == 2) "Shrink" else "Enlarge") },
                        onClick = {
                            sceneArtSize = if (sceneArtSize == 2) 0 else 2
                            sceneArtMenuOpen = false
                        },
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(
                    when (sceneArtSize) {
                        1 -> 1f
                        2 -> 0.38f
                        else -> 0.58f
                    },
                )
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InkTextButton(
                        label = "Setup",
                        onClick = viewModel::beginCampaignSetup,
                        compact = true,
                    )
                    if (!startupPending) {
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
                    val menuExpanded = captureMenuFor?.id == message.id
                    if (message.role == "user") {
                        Box(modifier = Modifier.fillMaxWidth()) {
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
                                    .combinedClickable(
                                        onClick = {},
                                        onLongClick = { captureMenuFor = message },
                                    )
                                    .background(tokens.hover, RoundedCornerShape(inkRadiusMd()))
                                    .padding(InkSpacing.sm),
                            )
                            CaptureMenu(
                                expanded = menuExpanded,
                                onClose = { captureMenuFor = null },
                                onAiSort = {
                                    captureMenuFor = null
                                    viewModel.captureFromText(message.text, "ai")
                                },
                                onRoster = {
                                    captureMenuFor = null
                                    viewModel.captureFromText(message.text, "roster")
                                },
                                onInventory = {
                                    captureMenuFor = null
                                    viewModel.captureFromText(message.text, "inventory")
                                },
                                onCopy = {
                                    clipboard.setText(AnnotatedString(message.text))
                                    captureMenuFor = null
                                },
                            )
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxWidth()) {
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
                                message.rollResult?.let { roll ->
                                    AdventureRollCard(
                                        roll = roll,
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
                                // SelectionContainer enables native copy of any
                                // span the user selects, not just whole messages.
                                SelectionContainer {
                                    CodexMentionText(
                                        text = message.text,
                                        targets = state.codexTargets,
                                        baseColor = tokens.primaryText,
                                        linkColor = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.bodyLarge,
                                        onTap = onOpenCodexEntry,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                            CaptureMenu(
                                expanded = menuExpanded,
                                onClose = { captureMenuFor = null },
                                onAiSort = {
                                    captureMenuFor = null
                                    viewModel.captureFromText(message.text, "ai")
                                },
                                onRoster = {
                                    captureMenuFor = null
                                    viewModel.captureFromText(message.text, "roster")
                                },
                                onInventory = {
                                    captureMenuFor = null
                                    viewModel.captureFromText(message.text, "inventory")
                                },
                                onCopy = {
                                    clipboard.setText(AnnotatedString(message.text))
                                    captureMenuFor = null
                                },
                            )
                        }
                    }
                }
                if (state.streamingText.isNotBlank()) {
                    item("streaming") {
                        SelectionContainer {
                            Text(
                                state.streamingText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = tokens.primaryText,
                            )
                        }
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
        if (state.composerStatus.isNotBlank()) {
            Text(
                state.composerStatus,
                style = MaterialTheme.typography.labelMedium,
                color = tokens.secondaryText,
                modifier = Modifier.padding(horizontal = InkSpacing.lg),
            )
        }
        CollapsibleUsageStrip(state.lastUsage, Modifier.padding(horizontal = InkSpacing.lg))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = InkSpacing.lg),
            horizontalArrangement = Arrangement.End,
        ) {
            InkTextButton(
                label = "＋ Add text to…",
                onClick = { showAddText = true },
            )
        }
        UnifiedPromptBar(
            value = state.input,
            onValueChange = viewModel::onInputChange,
            placeholder = if (state.adventureStartupPhase == AdventureStartupPhase.Character) {
                "Describe your character or say surprise me…"
            } else if (state.adventureStartupPhase == AdventureStartupPhase.Choose) {
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
            onClear = viewModel::clearInput,
            onUndoClear = viewModel::undoClearInput,
            onRetry = viewModel::regenerateLatestReply,
            onContinue = viewModel::continueAdventure,
            onMicTap = { if (!state.isStreaming) startDictate() },
            onExtraAction = viewModel::rollAction,
            onAdd = viewModel::requestMediaPick,
            onAddCharacter = viewModel::addRosterCharacter,
            onAddItem = viewModel::addInventoryItem,
            onSpoken = { spoken ->
                viewModel.onInputChange(mergeSpokenText(state.input, spoken))
            },
            compactSingleLine = true,
            showCommandPopup = true,
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
    if (showAppPictures) {
        com.ihy2ln.weaverse.feature.media.MediaLibraryPickerDialog(
            title = "Choose scene art",
            onSelect = { image ->
                showAppPictures = false
                viewModel.attachExistingMedia(image.id)
            },
            onDismiss = { showAppPictures = false },
        )
    }
    if (showAddText) {
        AddTextDialog(
            initialText = clipboard.getText()?.text.orEmpty(),
            onDismiss = { showAddText = false },
            onStatus = { message ->
                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            },
        )
    }
    state.captureDialog?.let { dialog ->
        AlertDialog(
            onDismissRequest = viewModel::dismissCapture,
            title = {
                Text(
                    when (dialog.kind) {
                        "roster" -> "Add to roster"
                        "ai" -> "AI sorted into sections"
                        else -> "Add to inventory"
                    },
                )
            },
            text = {
                Column {
                    Text(
                        if (dialog.kind == "ai") {
                            "The AI split the text into sections — uncheck anything to skip, then place."
                        } else {
                            "Found in the scene — uncheck anything to skip:"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = tokens.secondaryText,
                        modifier = Modifier.padding(bottom = InkSpacing.xs),
                    )
                    dialog.candidates.forEach { candidate ->
                        if (dialog.kind == "ai") {
                            val section = when {
                                candidate.name.startsWith("[C]") -> "Character sheet"
                                candidate.name.startsWith("[I]") -> "Inventory"
                                else -> "Codex"
                            }
                            Text(
                                section,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = tokens.secondaryText,
                                modifier = Modifier.padding(top = InkSpacing.xs),
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleCaptureCandidate(candidate.name) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = candidate.selected,
                                onCheckedChange = { viewModel.toggleCaptureCandidate(candidate.name) },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    candidate.name.removePrefix("[C] ").removePrefix("[I] ").removePrefix("[L] "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (candidate.summary.isNotBlank()) {
                                    Text(
                                        candidate.summary,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tokens.secondaryText,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                InkTextButton(label = "Place selected", onClick = viewModel::confirmCapture)
            },
            dismissButton = {
                InkTextButton(label = "Cancel", onClick = viewModel::dismissCapture)
            },
        )
    }
    state.campaignSetupInitial?.let { initial ->
        CampaignOptionsDialog(
            initial = initial,
            characterOptions = state.campaignCharacterOptions,
            onDismiss = viewModel::dismissCampaignOptions,
            onApply = viewModel::applyCampaignSetup,
            onRestart = viewModel::restartAdventure,
            customSettings = state.customSettingTemplates,
            onAddSetting = viewModel::addSettingTemplate,
            onRemoveSetting = viewModel::removeSettingTemplate,
        )
    }
    }
}

@Composable
private fun CaptureMenu(
    expanded: Boolean,
    onClose: () -> Unit,
    onRoster: () -> Unit,
    onInventory: () -> Unit,
    onAiSort: () -> Unit,
    onCopy: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onClose) {
        DropdownMenuItem(
            text = { Text("AI sort into Codex / Roster / Inventory…") },
            onClick = onAiSort,
        )
        DropdownMenuItem(text = { Text("Add to roster") }, onClick = onRoster)
        DropdownMenuItem(text = { Text("Add to inventory") }, onClick = onInventory)
        DropdownMenuItem(text = { Text("Copy") }, onClick = onCopy)
    }
}

@Composable
private fun AdventureRollCard(roll: AdventureRoll, modifier: Modifier = Modifier) {
    val tokens = inkTokens()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(inkRadiusMd()))
            .background(Color(0x147341A8))
            .border(1.dp, Color(0xFF7341A8), RoundedCornerShape(inkRadiusMd()))
            .padding(horizontal = InkSpacing.sm, vertical = InkSpacing.xs),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "${roll.checkLabel.uppercase()} · ${roll.system}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF7341A8),
            )
            Text(
                roll.outcome.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = tokens.activePill,
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm)) {
            Text(
                "FOR · ${roll.forCalculation()}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                "AGAINST · ${roll.againstCalculation()}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            "Natural ${roll.rawTotal} · modifier ${if (roll.modifier >= 0) "+${roll.modifier}" else roll.modifier} · ${roll.marginLabel()}",
            style = MaterialTheme.typography.labelSmall,
            color = tokens.secondaryText,
        )
    }
}

/** Story prose with codex entry names/aliases as tappable links. */
@Composable
private fun CodexMentionText(
    text: String,
    targets: List<CodexMentionTarget>,
    baseColor: Color,
    linkColor: Color,
    style: androidx.compose.ui.text.TextStyle,
    onTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (targets.isEmpty()) {
        Text(text, style = style, color = baseColor, modifier = modifier)
        return
    }
    val mentions = remember(text, targets) { findCodexMentions(text, targets) }
    val annotated = remember(text, mentions, linkColor) {
        buildAnnotatedString {
            append(text)
            mentions.forEach { mention ->
                addStyle(
                    SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                    mention.start,
                    mention.end,
                )
                addStringAnnotation(CodexMentionTag, mention.entryId, mention.start, mention.end)
            }
        }
    }
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        annotated,
        style = style,
        color = baseColor,
        onTextLayout = { layoutResult = it },
        modifier = modifier.pointerInput(annotated) {
            awaitEachGesture {
                val down = awaitFirstDown(pass = PointerEventPass.Initial)
                val layout = layoutResult ?: return@awaitEachGesture
                val offset = layout.getOffsetForPosition(down.position)
                val annotation = annotated.getStringAnnotations(CodexMentionTag, offset, offset)
                    .firstOrNull()
                if (annotation != null) {
                    down.consume()
                    onTap(annotation.item)
                }
            }
        },
    )
}