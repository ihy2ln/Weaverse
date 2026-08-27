package com.ihy2ln.weaverse.feature.prompt

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.ai.ModelInfo
import com.ihy2ln.weaverse.core.ui.components.InkCheckIconButton
import com.ihy2ln.weaverse.core.ui.components.InkClearIconButton
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.core.ui.components.VoiceInputButton
import com.ihy2ln.weaverse.core.ui.components.mergeSpokenText
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusMd
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusSm
import com.ihy2ln.weaverse.core.ui.theme.InkAccentBlue
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlobalPromptOverlay(
    context: PromptInsertContext,
    novelDest: String? = null,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    viewModel: GlobalPromptViewModel = hiltViewModel(),
) {
    if (!active) return
    if (!PromptSurface.usesGlobalOverlay(context.mode, novelDest)) return
    val state by viewModel.uiState.collectAsState()
    val tokens = inkTokens()
    LaunchedEffect(context) { viewModel.updateContext(context) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) viewModel.importImage(uri) }
    LaunchedEffect(state.pickImageRequestId) {
        if (state.pickImageRequestId > 0) {
            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    val kind = state.kind
    val placeholder = when (kind) {
        PromptEntryKind.Manual -> "Write ideas / brainstorm (no AI)…"
        PromptEntryKind.Ai -> "Describe the beat…"
        null -> "Continue…  / AI · \\ manual"
    }
    val acceptDescription = if (kind == PromptEntryKind.Ai) "Generate" else "Accept"
    val canClear = !state.isStreaming && (state.text.isNotBlank() || state.streamingText.isNotBlank())
    var modelsOpen by remember { mutableStateOf(false) }
    var modelSearch by rememberSaveable { mutableStateOf("") }
    // Collapsed keeps the dock to a single header line, so it stops covering the
    // page while still being one tap from writing.
    var collapsed by rememberSaveable { mutableStateOf(false) }
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
    val minimumWordsValue = minimumWordsText.toIntOrNull()
    val maximumWordsValue = maximumWordsText.toIntOrNull()
    val wordRangeValid = minimumWordsValue != null && maximumWordsValue != null &&
        minimumWordsValue in PromptWordLimit.Minimum..PromptWordLimit.Maximum &&
        maximumWordsValue in PromptWordLimit.Minimum..PromptWordLimit.Maximum &&
        minimumWordsValue <= maximumWordsValue
    val canSubmit = (state.text.isNotBlank() || state.imagePath != null) && wordRangeValid
    val activeModelRef = PromptModelSelection.effectiveModelRef(
        state.selectedModelRef,
        state.defaultModelRef,
    )
    val shape = RoundedCornerShape(inkRadiusMd())
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent,
        disabledBorderColor = Color.Transparent,
        focusedTextColor = tokens.primaryText,
        unfocusedTextColor = tokens.primaryText,
        disabledTextColor = tokens.primaryText.copy(alpha = 0.7f),
        cursorColor = tokens.primaryText,
        focusedPlaceholderColor = tokens.secondaryText,
        unfocusedPlaceholderColor = tokens.secondaryText,
        disabledPlaceholderColor = tokens.secondaryText,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = InkSpacing.sm, vertical = InkSpacing.xxs)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f))
            .border(1.dp, InkAccentBlue, shape)
            .padding(horizontal = InkSpacing.xs, vertical = InkSpacing.xxs),
    ) {
        Text(
            "PROMPT ${if (collapsed) "▴" else "▾"}",
            modifier = Modifier
                .clickable { collapsed = !collapsed }
                .padding(horizontal = 2.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 8.sp,
            color = InkAccentBlue,
            maxLines = 1,
        )
        if (!collapsed) {
            OutlinedTextField(
                value = state.text,
                onValueChange = viewModel::onTextChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp, max = 92.dp),
                placeholder = { Text(state.errorMessage.ifBlank { placeholder }) },
                enabled = !state.isStreaming,
                minLines = 1,
                maxLines = 3,
                colors = fieldColors,
            )
            if (state.contextMeterLabel.isNotBlank()) {
                Text(
                    state.contextMeterLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.secondaryText,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("W", style = MaterialTheme.typography.labelSmall, color = tokens.secondaryText)
                CompactNumberField(
                    minimumWordsText,
                    { value ->
                        minimumWordsText = value.filter(Char::isDigit).take(4)
                        minimumWordsText.toIntOrNull()?.let(viewModel::updateMinimumOutputWords)
                    },
                    "Minimum words",
                    !state.isStreaming,
                    wordRangeValid,
                )
                Text("–", style = MaterialTheme.typography.labelSmall, color = tokens.secondaryText)
                CompactNumberField(
                    maximumWordsText,
                    { value ->
                        maximumWordsText = value.filter(Char::isDigit).take(4)
                        maximumWordsText.toIntOrNull()?.let(viewModel::updateOutputWords)
                    },
                    "Maximum words",
                    !state.isStreaming,
                    wordRangeValid,
                )
            }
            Row(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(inkRadiusSm()))
                    .clickable(enabled = !state.isStreaming) { modelsOpen = true }
                    .semantics {
                        contentDescription = "Model: ${PromptModelSelection.shortLabel(activeModelRef, state.writingModels)}"
                    }
                    .padding(horizontal = 4.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Model · ", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(
                    PromptModelSelection.shortLabel(activeModelRef, state.writingModels),
                    modifier = Modifier.weight(1f).basicMarquee(iterations = Int.MAX_VALUE),
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.secondaryText,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            if (kind == PromptEntryKind.Ai) {
                Text(
                    if (state.imagePath != null) "▣✓" else "▣",
                    modifier = Modifier.clickable(enabled = !state.isStreaming) { viewModel.requestImage() }
                        .padding(horizontal = 3.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = tokens.secondaryText,
                )
            }
            PromptKindSelector(
                selectedKind = kind ?: PromptEntryKind.Manual,
                enabled = !state.isStreaming,
                onSelect = viewModel::selectEntryKind,
            )
            if (state.isStreaming) {
                InkTextButton(label = "Cancel", onClick = viewModel::cancelGeneration)
            } else {
                InkCheckIconButton(
                    onClick = viewModel::submit,
                    enabled = canSubmit,
                    contentDescription = acceptDescription,
                    modifier = Modifier.size(26.dp),
                )
            }
            InkClearIconButton(
                onClick = viewModel::clearText,
                enabled = canClear,
                modifier = Modifier.size(26.dp),
            )
            VoiceInputButton(
                onSpoken = { viewModel.onTextChange(mergeSpokenText(state.text, it)) },
                enabled = !state.isStreaming,
                compact = true,
                modifier = Modifier.size(26.dp),
            )
        }
    }
    }
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

@Composable
private fun PromptKindSelector(
    selectedKind: PromptEntryKind,
    enabled: Boolean,
    onSelect: (PromptEntryKind) -> Unit,
) {
    val tokens = inkTokens()
    val aiSelected = selectedKind == PromptEntryKind.Ai
    Text(
        text = if (aiSelected) "/A" else "\\M",
        modifier = Modifier
            .clip(RoundedCornerShape(inkRadiusSm()))
            .background(InkAccentBlue.copy(alpha = 0.14f))
            .clickable(enabled = enabled) {
                onSelect(if (aiSelected) PromptEntryKind.Manual else PromptEntryKind.Ai)
            }
            .semantics {
                contentDescription = if (aiSelected) {
                    "AI mode; tap for manual mode"
                } else {
                    "Manual mode; tap for AI mode"
                }
            }
            .padding(horizontal = 4.dp, vertical = 7.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = if (enabled) InkAccentBlue else tokens.secondaryText.copy(alpha = 0.5f),
        maxLines = 1,
    )
}

@Composable
private fun CompactNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    description: String,
    enabled: Boolean,
    valid: Boolean,
) {
    val tokens = inkTokens()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.labelSmall.copy(
            color = if (enabled) tokens.primaryText else tokens.secondaryText,
            textAlign = TextAlign.Center,
        ),
        modifier = Modifier.width(30.dp).semantics { contentDescription = description },
        decorationBox = { inner ->
            Box(
                Modifier.border(
                    1.dp,
                    if (valid) tokens.hairline else MaterialTheme.colorScheme.error,
                    RoundedCornerShape(6.dp),
                ).padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) { inner() }
        },
    )
}

@Composable
private fun PromptModelPickerDialog(
    models: List<ModelInfo>,
    search: String,
    onSearchChange: (String) -> Unit,
    selectedRef: String,
    defaultRef: String,
    onSelect: (String) -> Unit,
    onUseDefault: () -> Unit,
    onDismiss: () -> Unit,
) {
    val filtered = PromptModelSelection.filter(models, search)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Models") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = search,
                    onValueChange = onSearchChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Search models") },
                )
                Text(
                    "Per generation · Settings default stays unless you change it there",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = InkSpacing.xs, bottom = InkSpacing.xs),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onUseDefault)
                        .padding(vertical = InkSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val usingDefault = PromptModelSelection.followsDefault(selectedRef)
                    Text(
                        "Settings default",
                        modifier = Modifier.weight(1f),
                        fontWeight = if (usingDefault) FontWeight.Bold else FontWeight.Normal,
                    )
                    Text(
                        PromptModelSelection.shortLabel(defaultRef, models),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (filtered.isEmpty()) {
                    Text(
                        "Refresh models in Settings → Writing",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = InkSpacing.sm),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .padding(top = InkSpacing.xs),
                    ) {
                        items(filtered, key = { it.id }) { model ->
                            val selected = PromptModelSelection.isSelected(
                                model,
                                selectedRef,
                                defaultRef,
                            )
                            val muted = !model.available
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = model.available) { onSelect(model.id) }
                                    .padding(vertical = InkSpacing.xs),
                            ) {
                                Text(
                                    model.displayName,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (muted) {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    buildString {
                                        append(model.id)
                                        if (muted) append(" · unavailable")
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                        alpha = if (muted) 0.4f else 1f,
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
