package com.ihy2ln.weaverse.feature.brainstorm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.components.InkCard
import com.ihy2ln.weaverse.core.ui.components.InkChip
import com.ihy2ln.weaverse.core.ui.components.InkClearIconButton
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.feature.prompt.UnifiedPromptBar
import com.ihy2ln.weaverse.core.ui.components.mergeSpokenText
import com.ihy2ln.weaverse.core.ui.components.rememberSpeechToText
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.ihy2ln.weaverse.core.ui.theme.InkAccentBlue
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusSm
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.util.alwaysScrollEndSpacer
import com.ihy2ln.weaverse.core.ui.util.parseHexColor
import com.ihy2ln.weaverse.feature.novel.codex.AddTextDialog
import com.ihy2ln.weaverse.feature.prompt.PromptModelPickerDialog
import com.ihy2ln.weaverse.feature.prompt.PromptModelSelection
import com.ihy2ln.weaverse.feature.prompt.PromptWordLimit
import androidx.compose.foundation.text.selection.SelectionContainer

/**
 * The Brainstorm (Notes) workspace: a NovelCrafter-Chat-style conversation with
 * the AI — threads on the left, the transcript in the middle, and a composer
 * with model, word range, and codex context at the bottom.
 */
@Composable
fun BrainstormChatScreen(
    viewModel: BrainstormChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val tokens = inkTokens()
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var threadsOpen by rememberSaveable { mutableStateOf(true) }
    var modelsOpen by remember { mutableStateOf(false) }
    var modelSearch by rememberSaveable { mutableStateOf("") }
    var showAddText by remember { mutableStateOf(false) }

    var minimumWordsText by rememberSaveable { mutableStateOf(state.minimumWords.toString()) }
    var maximumWordsText by rememberSaveable { mutableStateOf(state.maximumWords.toString()) }
    LaunchedEffect(state.minimumWords) {
        if (minimumWordsText.toIntOrNull() != state.minimumWords) {
            minimumWordsText = state.minimumWords.toString()
        }
    }
    LaunchedEffect(state.maximumWords) {
        if (maximumWordsText.toIntOrNull() != state.maximumWords) {
            maximumWordsText = state.maximumWords.toString()
        }
    }
    val minimumWordsValue = minimumWordsText.toIntOrNull()
    val maximumWordsValue = maximumWordsText.toIntOrNull()
    val wordRangeValid = minimumWordsValue != null && maximumWordsValue != null &&
        minimumWordsValue in PromptWordLimit.Minimum..PromptWordLimit.Maximum &&
        maximumWordsValue in PromptWordLimit.Minimum..PromptWordLimit.Maximum &&
        minimumWordsValue <= maximumWordsValue
    val canSend = (state.input.isNotBlank() || state.hasPendingMedia) && !state.isStreaming && wordRangeValid

    var deleting by remember { mutableStateOf(emptySet<String>()) }
    var promptCollapsed by rememberSaveable { mutableStateOf(false) }
    val startDictate = rememberSpeechToText { spoken ->
        viewModel.onInputChange(mergeSpokenText(viewModel.currentInput(), spoken))
    }
    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.attachMedia(uris)
    }
    LaunchedEffect(state.mediaPickRequestId) {
        if (state.mediaPickRequestId > 0L) {
            mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        if (threadsOpen) {
            BrainstormThreadsRail(
                threads = state.threads,
                selectedThreadId = state.threadId,
                onThreadClick = viewModel::selectThread,
                onCreate = { viewModel.createThread() },
                onCreateSub = viewModel::createSubThread,
                onDelete = { deleting = setOf(it) },
                modifier = Modifier
                    .widthIn(min = 170.dp, max = 250.dp)
                    .fillMaxHeight(),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(InkSpacing.lg),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InkTextButton(
                    label = if (threadsOpen) "Hide chats" else "Chats",
                    onClick = { threadsOpen = !threadsOpen },
                    compact = true,
                )
                Text(
                    "Brainstorm",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = InkSpacing.sm),
                )
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                if (state.messages.isEmpty() && state.streamingText.isBlank()) {
                    item("empty") {
                        Text(
                            "Ideas start here. Ask anything — plots, worldbuilding, " +
                                "research, names, structure. Include codex entries with + Codex.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.secondaryText,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = InkSpacing.xxl),
                        )
                    }
                }
                items(state.messages, key = { it.id }) { message ->
                    BrainstormMessageRow(message)
                }
                if (state.isStreaming) {
                    item("streaming") {
                        BrainstormMessageRow(
                            BrainstormMessageUi(
                                id = "streaming",
                                role = "assistant",
                                text = state.streamingText,
                            ),
                            streaming = true,
                        )
                    }
                }
                alwaysScrollEndSpacer()
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = InkSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
            ) {
                state.contextChips.forEach { chip ->
                    InkChip(
                        label = chip.name,
                        color = parseHexColor(chip.colorHex, MaterialTheme.colorScheme.primary),
                        onRemove = { viewModel.removeChip(chip.entryId) },
                    )
                }
                InkTextButton(label = "+ Codex", onClick = viewModel::openCodexPicker, compact = true)
                InkTextButton(
                    label = if (state.showPreview) "Hide preview" else "Preview",
                    onClick = viewModel::togglePreview,
                    compact = true,
                )
                InkTextButton(
                    label = "＋ Add text to…",
                    onClick = { showAddText = true },
                    compact = true,
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

            if (state.showPreview && state.previewPrompt.isNotBlank()) {
                InkCard(modifier = Modifier.padding(bottom = InkSpacing.xs)) {
                    Text(
                        state.previewPrompt,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .heightIn(max = 160.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }

            if (state.errorMessage.isNotBlank()) {
                Text(
                    state.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = InkSpacing.xs),
                )
            }

            if (state.lastUsage.isNotBlank()) {
                Text(
                    state.lastUsage,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.secondaryText,
                    modifier = Modifier.padding(bottom = InkSpacing.xxs),
                )
            }

            // The same prompt window the RPG adventure and Novel editor use.
            UnifiedPromptBar(
                value = state.input,
                onValueChange = viewModel::onInputChange,
                placeholder = "Brainstorm with the AI…",
                collapsed = promptCollapsed,
                onCollapsedChange = { promptCollapsed = it },
                contextLabel = state.contextMeter?.label.orEmpty(),
                minimumWords = minimumWordsText,
                maximumWords = maximumWordsText,
                onMinimumWordsChange = { value ->
                    minimumWordsText = value.filter(Char::isDigit).take(4)
                    minimumWordsText.toIntOrNull()?.let(viewModel::updateMinimumWords)
                },
                onMaximumWordsChange = { value ->
                    maximumWordsText = value.filter(Char::isDigit).take(4)
                    maximumWordsText.toIntOrNull()?.let(viewModel::updateMaximumWords)
                },
                wordRangeValid = wordRangeValid,
                modelLabel = PromptModelSelection.shortLabel(state.activeModelRef, state.models),
                onModelClick = { modelsOpen = true },
                aiMode = state.aiMode,
                onToggleMode = viewModel::toggleAiMode,
                streaming = state.isStreaming,
                canSubmit = canSend,
                canClear = state.input.isNotBlank() && !state.isStreaming,
                onSubmit = viewModel::send,
                onCancel = viewModel::cancelGeneration,
                onClear = viewModel::clearInput,
                onUndoClear = viewModel::undoClearInput,
                onRetry = viewModel::retry,
                onContinue = viewModel::continueConversation,
                onMicTap = { if (!state.isStreaming) startDictate() },
                onRoll = viewModel::rollDice,
                onAdd = viewModel::requestMediaPick,
                onSpoken = { spoken ->
                    viewModel.onInputChange(mergeSpokenText(viewModel.currentInput(), spoken))
                },
                compactSingleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (modelsOpen) {
        PromptModelPickerDialog(
            models = state.models,
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

    if (state.showCodexPicker) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCodexPicker,
            title = { Text("Include codex entry") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (state.codexEntries.isEmpty()) {
                        Text("No codex entries yet.")
                    }
                    state.codexEntries.forEach { entry ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            androidx.compose.material3.Checkbox(
                                checked = entry.included,
                                onCheckedChange = { checked ->
                                    viewModel.toggleCodexEntry(entry.id, checked)
                                },
                            )
                            TextButton(onClick = {
                                viewModel.toggleCodexEntry(entry.id, !entry.included)
                            }) {
                                Text(entry.name, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissCodexPicker) { Text("Close") }
            },
        )
    }

    if (deleting.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { deleting = emptySet() },
            title = { Text("Delete chat?") },
            text = { Text("This chat history will be permanently removed.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteThreads(deleting)
                    deleting = emptySet()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = emptySet() }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun BrainstormMessageRow(
    message: BrainstormMessageUi,
    streaming: Boolean = false,
) {
    val tokens = inkTokens()
    val isUser = message.role == "user"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = InkSpacing.xs),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 12.dp,
                        topEnd = 12.dp,
                        bottomEnd = if (isUser) 2.dp else 12.dp,
                        bottomStart = if (isUser) 12.dp else 2.dp,
                    ),
                )
                .background(
                    when {
                        isUser -> tokens.activePill.copy(alpha = 0.12f)
                        streaming -> tokens.hover
                        else -> tokens.panel
                    },
                )
                .padding(horizontal = InkSpacing.md, vertical = InkSpacing.sm),
        ) {
            Text(
                when {
                    isUser -> "YOU"
                    streaming -> "AI · streaming"
                    else -> "AI"
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = tokens.secondaryText,
            )
            if (message.text.isNotBlank()) {
                // Selectable so any span can be copied, not just whole messages.
                SelectionContainer {
                    Text(
                        message.text.ifBlank { "…" },
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                        modifier = Modifier.padding(top = InkSpacing.xxs),
                    )
                }
            }
            message.mediaPaths.take(4).forEach { path ->
                coil3.compose.AsyncImage(
                    model = java.io.File(path),
                    contentDescription = "Attached image",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .padding(top = InkSpacing.xs)
                        .size(width = 180.dp, height = 120.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
            }
            if (message.usageText.isNotBlank()) {
                Text(
                    message.usageText,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.secondaryText,
                    modifier = Modifier.padding(top = InkSpacing.xxs),
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun BrainstormThreadsRail(
    threads: List<BrainstormThreadUi>,
    selectedThreadId: String?,
    onThreadClick: (String) -> Unit,
    onCreate: () -> Unit,
    onCreateSub: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .22f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = InkSpacing.sm, vertical = InkSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Chats", style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = onCreate) { Text("+ Add") }
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(threads, key = { it.id }) { thread ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (thread.id == selectedThreadId) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                            },
                        )
                        .combinedClickable(
                            onClick = { onThreadClick(thread.id) },
                            onLongClick = { onDelete(thread.id) },
                        )
                        .padding(
                            start = InkSpacing.md + (InkSpacing.lg * thread.depth),
                            end = InkSpacing.xs,
                            top = InkSpacing.xs,
                            bottom = InkSpacing.xs,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        (if (thread.depth > 0) "└ " else "") + thread.name,
                        style = if (thread.depth > 0) {
                            MaterialTheme.typography.bodyMedium
                        } else {
                            MaterialTheme.typography.titleSmall
                        },
                        color = if (thread.id == selectedThreadId) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    // + next to delete: add a sub-category under this chat.
                    if (thread.depth == 0) {
                        Text(
                            "+",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onCreateSub(thread.id) }
                                .padding(horizontal = 5.dp),
                        )
                    }
                    // Tap the backspace glyph to delete the chat (with confirmation).
                    InkClearIconButton(
                        onClick = { onDelete(thread.id) },
                        contentDescription = "Delete chat",
                        modifier = Modifier.width(22.dp),
                    )
                }
            }
            alwaysScrollEndSpacer()
        }
    }
}
