package com.ihy2ln.weaverse.feature.novel.write

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.text.Mark
import com.ihy2ln.weaverse.core.ui.components.EditTextAction
import com.ihy2ln.weaverse.core.ui.components.EditTextPopupConfig
import com.ihy2ln.weaverse.core.ui.components.InkConfirmButton
import com.ihy2ln.weaverse.core.ui.components.InkFilledButton
import com.ihy2ln.weaverse.core.ui.components.InkModeCapsule
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.core.ui.components.PromptActionMenuButton
import com.ihy2ln.weaverse.core.ui.components.TextColorPickerDialog
import com.ihy2ln.weaverse.core.ui.components.VoiceToTextField
import com.ihy2ln.weaverse.core.ui.components.rememberSpeechToText
import com.ihy2ln.weaverse.core.ui.components.toSpanHex
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.util.adaptiveContentPadding
import com.ihy2ln.weaverse.feature.novel.write.editor.DocumentEditor
import com.ihy2ln.weaverse.feature.novel.write.editor.GeneratedProse
import com.ihy2ln.weaverse.feature.novel.write.editor.SlashCommandOverlay
import com.ihy2ln.weaverse.feature.novel.write.editor.defaultSlashCommands

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun WriteScreen(
    sceneId: String = "scene-1",
    jumpKind: String = "Scene",
    onOpenCodexEntry: (String) -> Unit = {},
    pendingMediaId: String? = null,
    onMediaConsumed: () -> Unit = {},
    onOpenMediaLibrary: () -> Unit = {},
    toolRequest: String? = null,
    onToolConsumed: () -> Unit = {},
    viewModel: WriteViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var toolSheet by rememberSaveable(sceneId) { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingMediaId, state.sceneId) {
        if (pendingMediaId != null && state.sceneId == sceneId) {
            viewModel.insertExistingMedia(pendingMediaId); onMediaConsumed()
        }
    }
    LaunchedEffect(toolRequest) { if (toolRequest != null) { toolSheet = toolRequest; onToolConsumed() } }
    val tokens = inkTokens()
    val clipboard = LocalClipboardManager.current
    val startDictate = rememberSpeechToText { spoken ->
        viewModel.pasteIntoSelection(spoken)
    }

    LaunchedEffect(sceneId, jumpKind) { viewModel.loadScene(sceneId, jumpKind) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importImages(uris)
        } else {
            viewModel.cancelImagePick()
        }
    }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importImages(uris)
        } else {
            viewModel.cancelImagePick()
        }
    }

    val beatImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.attachBeatImage(uri)
    }

    LaunchedEffect(state.pickImageRequestId) {
        if (state.pickImageRequestId > 0L && state.pickImageBlockIndex != null) {
            imagePicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
            )
        }
    }

    LaunchedEffect(state.pickAudioRequestId) {
        if (state.pickAudioRequestId > 0L && state.pickImageBlockIndex != null) {
            audioPicker.launch(arrayOf("audio/*", "audio/mpeg", "audio/wav", "audio/x-wav"))
        }
    }

    LaunchedEffect(state.aiOverlay?.pickBeatImageRequestId) {
        val req = state.aiOverlay?.pickBeatImageRequestId ?: 0L
        if (req > 0L) {
            viewModel.consumeBeatImageRequest()
            beatImagePicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        }
    }

    if (state.showColorPicker) {
        TextColorPickerDialog(
            initial = MaterialTheme.colorScheme.primary,
            onDismiss = viewModel::dismissColorPicker,
            onConfirm = { color -> viewModel.applyColorOnSelection(color.toSpanHex()) },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val contentPad = adaptiveContentPadding()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPad),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${state.wordCount} words · ${state.saveStatus}", style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f))

            }
            if (state.findReplace.visible) {
                FindReplaceBar(
                    state = state.findReplace,
                    onQuery = viewModel::updateFindQuery,
                    onReplacement = viewModel::updateFindReplacement,
                    onPrev = viewModel::findPrev,
                    onNext = viewModel::findNext,
                    onReplace = viewModel::replaceCurrent,
                    onReplaceAll = viewModel::replaceAllInScene,
                    onClose = viewModel::toggleFindReplace,
                )
            }
            if (state.statusMessage.isNotBlank()) {
                Text(
                    state.statusMessage,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(bottom = InkSpacing.xs)
                        .then(
                            if (state.pendingCodexEntryId != null) {
                                Modifier.padding(0.dp) // click target via InkTextButton below
                            } else {
                                Modifier
                            },
                        ),
                )
                if (state.pendingCodexEntryId != null) {
                    InkTextButton(
                        label = "Open entry",
                        onClick = {
                            onOpenCodexEntry(state.pendingCodexEntryId!!)
                            viewModel.clearStatus()
                        },
                    )
                }
            }
            DocumentEditor(
                blocks = state.blocks,
                mediaPaths = state.mediaPaths,
                onParagraphChange = viewModel::updateParagraph,
                onMediaWidthChange = viewModel::updateMediaWidth,
                onMediaSelect = viewModel::selectMediaBlock,
                onMediaRemove = viewModel::removeMediaBlock,
                onMediaMoveBy = viewModel::moveBlock,
                onStackMedia = viewModel::stackMediaWithAdjacent,
                onCycleStack = viewModel::cycleMediaStack,
                onMediaDragRelease = viewModel::onMediaDragRelease,
                canPasteMedia = state.canPasteMedia,
                onMediaEditAction = viewModel::onMediaEditAction,
                selectedMediaBlockIndex = state.selectedMediaBlockIndex,
                onSlashTrigger = viewModel::onSlashTrigger,
                onBackslashTrigger = viewModel::onBackslashTrigger,
                focusedBlockIndex = state.selection.blockIndex,
                editPopupBlockIndex = state.editPopupBlockIndex,
                onSelectionChange = viewModel::onSelectionChange,
                onShowEditPopup = viewModel::setEditPopupBlock,
                popupConfig = EditTextPopupConfig(
                    canUndo = state.canUndo,
                    canRedo = state.canRedo,
                    hasSelection = state.selection.hasSelection,
                ),
                onSceneBeatPromptChange = viewModel::updateSceneBeatPrompt,
                onToggleSceneBeat = viewModel::toggleSceneBeat,
                onGenerateSceneBeat = viewModel::generateFromSceneBeat,
                onClearSceneBeat = viewModel::clearSceneBeat,
                onAcceptSceneBeat = viewModel::acceptAiResult,
                onRetrySceneBeat = viewModel::retryAiGeneration,
                onRequestBeatImage = viewModel::requestBeatImage,
                beatImageAttached = state.aiOverlay?.imagePath != null,
                sceneBeatResultIndex = state.aiOverlay
                    ?.takeIf { it.commandId == "scene_beat" && it.streamingText.isNotBlank() && !it.isStreaming }
                    ?.insertAfterIndex,
                generatingSceneBeatIndex = state.aiOverlay
                    ?.takeIf { it.commandId == "scene_beat" && it.isStreaming }
                    ?.insertAfterIndex,
                codexNames = state.codexNames,
                codexMentionTargets = state.codexMentionTargets,
                onMentionClick = onOpenCodexEntry,
                onContinuationSubmit = viewModel::insertContinuation,
                showInlineWritingPrompt = state.showInlineWritingPrompt,
                showSceneBeatCard = state.showSceneBeatCard,
                showContinuationBox = state.showContinuationBox,
                generatedProse = state.aiOverlay
                    ?.takeIf { it.streamingText.isNotBlank() }
                    // Scene beats already render their own result card in place.
                    ?.takeUnless { it.commandId == "scene_beat" && state.showSceneBeatCard }
                    ?.let { overlay ->
                        GeneratedProse(
                            text = overlay.streamingText,
                            original = overlay.sourceParagraphText.orEmpty(),
                            label = overlay.label,
                            streaming = overlay.isStreaming,
                            replacing = overlay.replaceBlockIndex != null,
                            afterBlockId = overlay.anchorBlockId,
                            earlierCount = overlay.candidates.size,
                        )
                    },
                onAcceptGenerated = viewModel::acceptAiResult,
                onRetryGenerated = viewModel::retryAiGeneration,
                onDiscardGenerated = viewModel::discardAiResult,
                onCopyGenerated = {
                    val text = state.aiOverlay?.streamingText.orEmpty()
                    if (text.isNotBlank()) clipboard.setText(AnnotatedString(text))
                },
                onChooseEarlierGenerated = viewModel::chooseCandidate,
                onEditGenerated = viewModel::updateGeneratedDraft,
                caretRequest = state.caretRequest,
                onPlaceCaret = { index -> viewModel.placeCaret(index) },
                onPlaceCaretAtEnd = viewModel::placeCaretAtEnd,
                onEditAction = { index, action, value ->
                    viewModel.onSelectionChange(index, value.selection)
                    when (action) {
                        EditTextAction.Copy -> {
                            val text = viewModel.selectedText()
                            if (text.isNotBlank()) clipboard.setText(AnnotatedString(text))
                        }
                        EditTextAction.Cut -> {
                            val text = viewModel.cutSelection()
                            if (text.isNotBlank()) clipboard.setText(AnnotatedString(text))
                        }
                        EditTextAction.Paste -> {
                            val clip = clipboard.getText()?.text.orEmpty()
                            viewModel.pasteIntoSelection(clip)
                        }
                        EditTextAction.SelectAll -> viewModel.selectAllInFocusedBlock()
                        EditTextAction.Delete -> viewModel.deleteSelection()
                        EditTextAction.Edit -> Unit
                        EditTextAction.Bold -> viewModel.toggleMarkOnSelection(Mark.Bold)
                        EditTextAction.Italic -> viewModel.toggleMarkOnSelection(Mark.Italic)
                        EditTextAction.Color -> viewModel.requestColorPicker()
                        EditTextAction.AddToCodex -> viewModel.addSelectionToCodex()
                        EditTextAction.Shorten -> viewModel.startSelectionAi("shorten", "Shorten")
                        EditTextAction.Extend -> viewModel.startSelectionAi("extend", "Extend")
                        EditTextAction.Replace -> viewModel.startSelectionAi("replace", "Replace")
                        EditTextAction.Undo -> viewModel.undo()
                        EditTextAction.Redo -> viewModel.redo()
                        EditTextAction.Speak -> {
                            val text = viewModel.selectedText().ifBlank {
                                state.aiOverlay?.streamingText.orEmpty()
                            }
                            viewModel.speakText(text)
                        }
                        EditTextAction.Dictate -> startDictate()
                    }
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
        if (toolSheet != null) androidx.compose.material3.ModalBottomSheet(onDismissRequest = { toolSheet = null }) {
            Column(Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text(toolSheet.orEmpty(), style = MaterialTheme.typography.titleLarge)
                fun closeAction(action: () -> Unit): () -> Unit = { toolSheet = null; action() }
                when (toolSheet) {
                    "Undo/Redo" -> {
                        InkTextButton("Undo", closeAction(viewModel::undo), enabled = state.canUndo)
                        InkTextButton("Redo", closeAction(viewModel::redo), enabled = state.canRedo)
                    }
                    "Format" -> {
                        InkTextButton("Bold", closeAction { viewModel.toggleMarkOnSelection(Mark.Bold) })
                        InkTextButton("Italic", closeAction { viewModel.toggleMarkOnSelection(Mark.Italic) })
                        InkTextButton("Text color", closeAction(viewModel::requestColorPicker))
                        InkTextButton("Add selection to Codex", closeAction(viewModel::addSelectionToCodex))
                        InkTextButton("Dictate", closeAction { startDictate() })
                    }
                    "Insert" -> {
                        InkTextButton("Insert image / video", closeAction(viewModel::requestAddMedia))
                        InkTextButton("Insert audio", closeAction(viewModel::requestAddAudio))
                        InkTextButton("Story media & references", closeAction(onOpenMediaLibrary))
                    }
                    "AI" -> {
                        Text("Choose an action, inspect the prompt, then Generate. Nothing is sent by opening this menu.", style = MaterialTheme.typography.bodySmall)
                        if (state.aiOverlay?.hidden == true) InkTextButton("Resume AI draft", closeAction(viewModel::resumeAiOverlay))
                        listOf("continue" to "Continue", "replace" to "Rewrite", "expand" to "Expand", "shorten" to "Shorten").forEach { (id, label) ->
                            InkTextButton(label, closeAction { viewModel.startSelectionAi(id, label) })
                        }
                        InkTextButton("Scene beat / image-to-prose", closeAction(viewModel::startSceneBeatFromPlan))
                    }
                    else -> {
                        InkTextButton("Find / replace", closeAction(viewModel::toggleFindReplace))
                        InkTextButton("Revision history", closeAction(viewModel::toggleHistory))
                        InkTextButton("Save revision snapshot", closeAction(viewModel::snapshotNow))
                        InkTextButton("Summarize scene (runs AI)", closeAction(viewModel::summarizeScene))
                        Text(state.contextMeter?.label.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (state.slashBlockIndex != null) {
            SlashCommandOverlay(
                commands = defaultSlashCommands,
                filter = state.slashFilter.removePrefix("/"),
                onSelect = viewModel::applySlashCommand,
                onDismiss = viewModel::dismissSlash,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(InkSpacing.lg),
            )
        }
        if (state.showHistory) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = viewModel::toggleHistory,
                title = { Text("Scene history") },
                text = {
                    Column {
                        InkTextButton(label = "Save snapshot now", onClick = viewModel::snapshotNow, compact = true)
                        if (state.revisions.isEmpty()) {
                            Text("No snapshots yet. Hourly copies are kept as you write.", color = tokens.secondaryText)
                        } else {
                            state.revisions.take(24).forEach { rev ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.US)
                                                .format(java.util.Date(rev.createdAt)),
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                        Text(
                                            "${rev.wordCount} words · ${rev.preview}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = tokens.secondaryText,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    InkTextButton(
                                        label = "Restore",
                                        onClick = { viewModel.restoreRevision(rev.id) },
                                        compact = true,
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    InkTextButton(label = "Close", onClick = viewModel::toggleHistory, compact = true)
                },
            )
        }
    }
}

@Composable
private fun FindReplaceBar(
    state: FindReplaceState,
    onQuery: (String) -> Unit,
    onReplacement: (String) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit,
) {
    val tokens = inkTokens()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = InkSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(InkSpacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQuery,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Find") },
                singleLine = true,
            )
            Text(
                state.matchLabel,
                style = MaterialTheme.typography.labelSmall,
                color = tokens.secondaryText,
                modifier = Modifier.padding(horizontal = InkSpacing.sm),
            )
            InkTextButton(label = "Prev", onClick = onPrev, compact = true)
            InkTextButton(label = "Next", onClick = onNext, compact = true)
            InkTextButton(label = "×", onClick = onClose, compact = true)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.replacement,
                onValueChange = onReplacement,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Replace") },
                singleLine = true,
            )
            InkTextButton(label = "Replace", onClick = onReplace, compact = true)
            InkTextButton(label = "All", onClick = onReplaceAll, compact = true)
        }
    }
}
