package com.ihy2ln.weaverse.feature.novel.chat



import androidx.activity.compose.rememberLauncherForActivityResult

import androidx.activity.result.PickVisualMediaRequest

import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.fillMaxHeight

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip

import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.lazy.items

import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.verticalScroll

import androidx.compose.material3.AlertDialog

import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.Text

import androidx.compose.material3.TextButton

import androidx.compose.runtime.Composable

import androidx.compose.runtime.LaunchedEffect

import androidx.compose.runtime.collectAsState

import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.saveable.rememberSaveable

import androidx.compose.runtime.setValue

import androidx.compose.ui.Modifier

import androidx.compose.ui.text.style.LineHeightStyle

import androidx.compose.ui.unit.sp

import androidx.compose.ui.unit.dp

import androidx.hilt.navigation.compose.hiltViewModel

import com.ihy2ln.weaverse.feature.prompt.UnifiedPromptBar
import com.ihy2ln.weaverse.core.ui.components.mergeSpokenText
import com.ihy2ln.weaverse.feature.prompt.PromptModelPickerDialog
import com.ihy2ln.weaverse.feature.prompt.PromptModelSelection
import com.ihy2ln.weaverse.feature.prompt.PromptWordLimit

import com.ihy2ln.weaverse.core.ui.components.CollapsibleUsageStrip

import com.ihy2ln.weaverse.core.ui.components.InkCard
import com.ihy2ln.weaverse.core.ui.components.rememberSpeechToText

import com.ihy2ln.weaverse.core.ui.components.InkChip

import com.ihy2ln.weaverse.core.ui.components.InkTextButton

import com.ihy2ln.weaverse.core.ui.theme.CodexCharacters

import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.util.alwaysScrollEndSpacer

import com.ihy2ln.weaverse.core.ui.util.parseHexColor



@Composable

fun WorkshopChatScreen(

    threadId: String? = null,

    viewModel: WorkshopChatViewModel = hiltViewModel(),

) {

    val state by viewModel.uiState.collectAsState()
    var threadsOpen by rememberSaveable { mutableStateOf(true) }
    var promptCollapsed by rememberSaveable { mutableStateOf(false) }
    var modelsOpen by rememberSaveable { mutableStateOf(false) }
    var modelSearch by rememberSaveable { mutableStateOf("") }
    var minimumWordsText by rememberSaveable { mutableStateOf(state.minimumOutputWords.toString()) }
    var maximumWordsText by rememberSaveable { mutableStateOf(state.maximumOutputWords.toString()) }
    LaunchedEffect(state.minimumOutputWords) {
        if (minimumWordsText.toIntOrNull() != state.minimumOutputWords) {
            minimumWordsText = state.minimumOutputWords.toString()
        }
    }
    LaunchedEffect(state.maximumOutputWords) {
        if (maximumWordsText.toIntOrNull() != state.maximumOutputWords) {
            maximumWordsText = state.maximumOutputWords.toString()
        }
    }
    val minWordsValue = minimumWordsText.toIntOrNull()
    val maxWordsValue = maximumWordsText.toIntOrNull()
    val wordRangeValid = minWordsValue != null && maxWordsValue != null &&
        minWordsValue in PromptWordLimit.Minimum..PromptWordLimit.Maximum &&
        maxWordsValue in PromptWordLimit.Minimum..PromptWordLimit.Maximum &&
        minWordsValue <= maxWordsValue
    val canSend = (state.input.isNotBlank() || state.hasPendingMedia) && !state.isStreaming && wordRangeValid
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

    LaunchedEffect(threadId) {

        viewModel.selectThread(threadId ?: state.threadId)

    }

    val compactStyle = MaterialTheme.typography.bodyMedium.copy(

        lineHeight = 20.sp,

        lineHeightStyle = LineHeightStyle(

            alignment = LineHeightStyle.Alignment.Center,

            trim = LineHeightStyle.Trim.None,

        ),

    )

    Row(modifier = Modifier.fillMaxSize()) {
        if (threadsOpen) {
            WorkshopThreadsRail(
                selectedThreadId = state.threadId,
                onThreadClick = viewModel::selectThread,
                modifier = Modifier.widthIn(min = 180.dp, max = 280.dp).fillMaxHeight(),
            )
        }
        Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(InkSpacing.lg)) {

        Row(modifier = Modifier.fillMaxWidth()) {
            InkTextButton(
                label = if (threadsOpen) "Hide chats" else "Chats",
                onClick = { threadsOpen = !threadsOpen },
                compact = true,
            )
            Text("Workshop Chats", style = MaterialTheme.typography.titleLarge)
        }

        LazyColumn(modifier = Modifier.weight(1f)) {

            items(state.messages, key = { it.id }) { message ->

                InkCard(modifier = Modifier.padding(vertical = InkSpacing.xs)) {

                    Text(message.role.uppercase(), style = MaterialTheme.typography.labelMedium)

                    if (message.plainText.isNotBlank()) {
                        Text(message.plainText, style = compactStyle, modifier = Modifier.padding(top = InkSpacing.xxs))
                    }
                    message.mediaPaths.take(4).forEach { path ->
                        coil3.compose.AsyncImage(
                            model = java.io.File(path),
                            contentDescription = "Attached image",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier
                                .padding(top = InkSpacing.xxs)
                                .size(width = 180.dp, height = 120.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                    }
                    if (message.usageText.isNotBlank()) {
                        Text(
                            message.usageText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = InkSpacing.xxs),
                        )
                    }

                }

            }

            if (state.isStreaming && state.streamingText.isNotBlank()) {

                item("streaming") {

                    InkCard(modifier = Modifier.padding(vertical = InkSpacing.xs)) {

                        Text("ASSISTANT · streaming", style = MaterialTheme.typography.labelMedium)

                        Text(state.streamingText, style = compactStyle, modifier = Modifier.padding(top = InkSpacing.xxs))

                    }

                }

            }

            alwaysScrollEndSpacer()

        }

        Row(

            modifier = Modifier

                .fillMaxWidth()

                .padding(vertical = InkSpacing.sm),

        ) {

            state.contextChips.forEach { chip ->

                InkChip(

                    label = chip.name,

                    color = parseHexColor(chip.colorHex, CodexCharacters),

                    onRemove = { viewModel.removeChip(chip.entryId) },

                    modifier = Modifier.padding(end = InkSpacing.xs),

                )

            }

            InkTextButton(label = "+ Codex", onClick = viewModel::openCodexPicker)

        }

        if (state.showPreview && state.previewPrompt.isNotBlank()) {

            InkCard(modifier = Modifier.padding(bottom = InkSpacing.sm)) {

                Text("Preview prompt", style = MaterialTheme.typography.labelLarge)

                Text(state.previewPrompt, style = MaterialTheme.typography.bodySmall)

            }

        }

        if (state.errorMessage.isNotBlank()) {

            Text(

                state.errorMessage,

                color = MaterialTheme.colorScheme.error,

                modifier = Modifier.padding(bottom = InkSpacing.sm),

            )

        }

        CollapsibleUsageStrip(usageText = state.lastUsage)
        state.contextMeter?.let { meter ->
            Text(
                meter.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = InkSpacing.xs),
            )
        }

        if (state.isStreaming) {
            InkTextButton(
                label = "Cancel",
                onClick = viewModel::cancelGeneration,
                modifier = Modifier.padding(top = InkSpacing.xs),
            )
        }

        if (state.showExtraPromptSurfaces) {
            // The shared prompt window — same bar as the RPG adventure.
            UnifiedPromptBar(
                value = state.input,
                onValueChange = viewModel::onInputChange,
                placeholder = "Message the workshop…",
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
                modelLabel = PromptModelSelection.shortLabel(state.modelRef, state.writingModels),
                onModelClick = { modelsOpen = true },
                aiMode = true,
                onToggleMode = {},
                streaming = state.isStreaming,
                canSubmit = canSend,
                canClear = state.input.isNotBlank() && !state.isStreaming,
                onSubmit = viewModel::send,
                onCancel = viewModel::cancelGeneration,
                onClear = viewModel::clearInput,
                onUndoClear = viewModel::undoClearInput,
                onRetry = viewModel::retry,
                onContinue = viewModel::continuePrompt,
                onMicTap = { if (!state.isStreaming) startDictate() },
                onRoll = viewModel::rollDice,
                onAdd = viewModel::requestMediaPick,
                onSpoken = { spoken ->
                    viewModel.onInputChange(mergeSpokenText(viewModel.currentInput(), spoken))
                },
                compactSingleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = InkSpacing.sm),
            )
        }

        InkTextButton(

            label = if (state.showPreview) "Hide preview" else "Preview prompt",

            onClick = viewModel::togglePreview,

        )

        }
    }

    if (modelsOpen) {
        PromptModelPickerDialog(
            models = state.writingModels,
            search = modelSearch,
            onSearchChange = { modelSearch = it },
            selectedRef = state.modelRef,
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

                    state.codexEntries.forEach { entry ->

                        Row(

                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,

                            modifier = Modifier.fillMaxWidth(),

                        ) {

                            androidx.compose.material3.Checkbox(

                                checked = entry.included,

                                onCheckedChange = { checked ->

                                    viewModel.toggleCodexEntry(entry.id, checked)

                                },

                                colors = androidx.compose.material3.CheckboxDefaults.colors(

                                    checkedColor = MaterialTheme.colorScheme.primary,

                                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,

                                    checkmarkColor = MaterialTheme.colorScheme.onPrimary,

                                ),

                            )

                            TextButton(onClick = { viewModel.toggleCodexEntry(entry.id, !entry.included) }) {

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

}


