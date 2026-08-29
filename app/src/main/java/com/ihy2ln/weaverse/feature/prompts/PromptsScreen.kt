package com.ihy2ln.weaverse.feature.prompts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.ai.prompt.PromptRole
import com.ihy2ln.weaverse.ai.prompt.PromptAddOns
import com.ihy2ln.weaverse.ai.prompt.PromptAgeRating
import com.ihy2ln.weaverse.ai.prompt.PromptingMode
import com.ihy2ln.weaverse.core.ui.components.InkConfirmButton
import com.ihy2ln.weaverse.core.ui.components.InkChip
import com.ihy2ln.weaverse.core.ui.components.InkSegmentedPill
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.core.ui.components.SegmentedOption
import com.ihy2ln.weaverse.core.ui.components.VoiceToTextField
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusMd
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusSm
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens

@Composable
fun PromptsScreen(
    modifier: Modifier = Modifier,
    viewModel: PromptsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val tokens = inkTokens()
    val isNarrow = LocalConfiguration.current.screenWidthDp < 600
    var showEditor by rememberSaveable { mutableStateOf(false) }

    fun openPrompt(id: String) {
        viewModel.selectPrompt(id)
        showEditor = true
    }

    if (isNarrow) {
        Column(modifier = modifier.fillMaxSize().background(tokens.background)) {
            if (showEditor && state.selectedId != null) {
                PromptEditorPane(
                    state = state,
                    viewModel = viewModel,
                    onBackToList = { showEditor = false },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                PromptLibraryPane(
                    state = state,
                    viewModel = viewModel,
                    onOpenPrompt = ::openPrompt,
                    onShowEffectiveInstructions = { showEditor = true },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        return
    }

    Row(modifier = modifier.fillMaxSize().background(tokens.background)) {
        PromptLibraryPane(
            state = state,
            viewModel = viewModel,
            onOpenPrompt = ::openPrompt,
            onShowEffectiveInstructions = { showEditor = true },
            modifier = Modifier
                .width(260.dp)
                .fillMaxHeight(),
        )
        PromptEditorPane(
            state = state,
            viewModel = viewModel,
            onBackToList = null,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
    }
}

@Composable
private fun PromptTemplateHeader(
    state: PromptsUiState,
    viewModel: PromptsViewModel,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = inkTokens()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(inkRadiusMd()))
            .background(tokens.panel)
            .border(1.dp, tokens.hairline, RoundedCornerShape(inkRadiusMd()))
            .padding(InkSpacing.sm),
    ) {
        // TEMPLATE (grey bar) + explicit effective-instruction refresh.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(tokens.hover, RoundedCornerShape(inkRadiusSm()))
                .padding(horizontal = InkSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "TEMPLATE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = tokens.secondaryText,
                modifier = Modifier.weight(1f),
            )
            InkTextButton(
                label = "↻ Refresh instructions",
                onClick = onRefresh,
                compact = true,
            )
        }
        // MODE — the selected base template sits first; all other controls are add-ons.
        Text(
            "MODE TEMPLATE",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = InkSpacing.xs),
        )
        InkSegmentedPill(
            options = PromptingMode.entries.map { SegmentedOption(it.id, it.label) },
            selectedId = state.promptingMode.id,
            onSelect = { id -> viewModel.setPromptingMode(PromptingMode.fromId(id)) },
            modifier = Modifier.fillMaxWidth().padding(top = InkSpacing.xxs),
            scrollable = true,
            compact = true,
        )
        Text(
            "${state.promptingMode.label} is the base; the controls below are layered onto it.",
            style = MaterialTheme.typography.labelSmall,
            color = tokens.secondaryText,
            modifier = Modifier.padding(top = InkSpacing.xxs),
        )
        // ADD-ON — ECCHI MANGAKA OVERLAY toggle
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = InkSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("ADD-ON · ECCHI MANGAKA OVERLAY", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (state.ecchiOverlay) "ON — injected into every mode's role and rules" else "OFF — all ecchi layers stripped from prompts",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.secondaryText,
                )
            }
            Switch(checked = state.ecchiOverlay, onCheckedChange = viewModel::setEcchiOverlay)
        }
        // GENRES — independent multi-select add-ons.
        Text(
            "GENRES · PICK ANY",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = InkSpacing.sm, bottom = InkSpacing.xxs),
        )
        val genreRows = PromptAddOns.GenreOptions.chunked((PromptAddOns.GenreOptions.size + 1) / 2)
        Column(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(InkSpacing.xxs),
        ) {
            genreRows.forEach { rowGenres ->
                Row(horizontalArrangement = Arrangement.spacedBy(InkSpacing.xxs)) {
                    rowGenres.forEach { genre ->
                        InkChip(
                            label = genre,
                            color = MaterialTheme.colorScheme.primary,
                            selected = genre in state.selectedGenres,
                            onClick = { viewModel.toggleGenre(genre) },
                        )
                    }
                }
            }
        }
        // AGE RATING — a persisted add-on ranging from PG through X.
        Text(
            "AGE RATING",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = InkSpacing.sm),
        )
        InkSegmentedPill(
            options = PromptAgeRating.entries.map { SegmentedOption(it.id, it.label) },
            selectedId = state.promptAgeRating.id,
            onSelect = { id -> viewModel.setPromptAgeRating(PromptAgeRating.fromId(id)) },
            modifier = Modifier.fillMaxWidth().padding(top = InkSpacing.xxs),
            scrollable = true,
            compact = true,
        )
        Text(
            state.promptAgeRating.instruction,
            style = MaterialTheme.typography.labelSmall,
            color = tokens.secondaryText,
            modifier = Modifier.padding(top = InkSpacing.xxs),
        )
    }
}

@Composable
private fun PromptLibraryPane(
    state: PromptsUiState,
    viewModel: PromptsViewModel,
    onOpenPrompt: (String) -> Unit,
    onShowEffectiveInstructions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = inkTokens()
    Column(
        modifier = modifier
            .padding(InkSpacing.md)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Prompt Collection", style = MaterialTheme.typography.titleMedium)
        Text(
            "One section per mode — active in Novel, Roleplay, and Notes everywhere.",
            style = MaterialTheme.typography.labelSmall,
            color = tokens.secondaryText,
            modifier = Modifier.padding(top = InkSpacing.xxs, bottom = InkSpacing.sm),
        )
        PromptTemplateHeader(
            state = state,
            viewModel = viewModel,
            onRefresh = {
                viewModel.refreshEffectiveInstructions()
                onShowEffectiveInstructions()
            },
            modifier = Modifier.fillMaxWidth(),
        )
        state.folders
            .sortedBy { group -> if (group.folder.type == state.promptingMode.id) 0 else 1 }
            .forEach { group ->
            val activeModeFolder = group.folder.type == state.promptingMode.id
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.toggleFolder(group.folder.id) }
                    .padding(vertical = InkSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (group.expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                )
                Text(
                    "${group.folder.name} (${group.prompts.size})" +
                        if (activeModeFolder) " · ACTIVE TEMPLATE" else "",
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = if (activeModeFolder) MaterialTheme.colorScheme.primary else tokens.primaryText,
                )
                InkTextButton(label = "+", onClick = { viewModel.createPrompt(group.folder.id) })
            }
            if (group.expanded) {
                group.prompts.forEach { prompt ->
                    Text(
                        prompt.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenPrompt(prompt.id) }
                            .padding(start = InkSpacing.lg, top = InkSpacing.xs, bottom = InkSpacing.xs),
                        color = if (prompt.id == state.selectedId) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            tokens.primaryText
                        },
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun PromptEditorPane(
    state: PromptsUiState,
    viewModel: PromptsViewModel,
    onBackToList: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val tokens = inkTokens()
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = modifier
            .padding(InkSpacing.md)
            .verticalScroll(rememberScrollState()),
    ) {
        if (state.selectedId == null) {
            Text("Select a prompt on the left. Instructions should be full guiding prose.", color = tokens.secondaryText)
            return
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBackToList != null) {
                IconButton(onClick = onBackToList) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Prompt list")
                }
            }
            Text(
                state.name.ifBlank { "Prompt" },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            InkConfirmButton(
                onClick = viewModel::save,
                label = "Save",
                contentDescription = "Save prompt",
            )
            InkTextButton(label = "Delete", onClick = viewModel::deleteSelected)
        }
        InkSegmentedPill(
            options = PromptEditorTab.entries.map { SegmentedOption(it.name, it.name) },
            selectedId = state.editorTab.name,
            onSelect = { viewModel.setEditorTab(PromptEditorTab.valueOf(it)) },
            modifier = Modifier.padding(vertical = InkSpacing.md),
        )
        when (state.editorTab) {
            PromptEditorTab.General -> {
                VoiceToTextField(
                    value = state.name,
                    onValueChange = viewModel::onName,
                    label = "Name",
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.type,
                    onValueChange = viewModel::onType,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = InkSpacing.sm),
                    label = { Text("Type (scene_beat / summarize / replace / workshop_chat)") },
                    singleLine = true,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = InkSpacing.md)
                        .clickable { viewModel.onIsDefault(!state.isDefault) },
                ) {
                    Checkbox(checked = state.isDefault, onCheckedChange = viewModel::onIsDefault)
                    Text("Default for this type", color = tokens.primaryText)
                }
            }
            PromptEditorTab.Instructions -> {
                if (state.effectiveInstructions.isNotBlank()) {
                    Text(
                        "Effective instructions sent to the AI",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "This refreshed preview combines the selected mode, genres, age rating, overlay, " +
                            "and the editable System message below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.secondaryText,
                        modifier = Modifier.padding(top = InkSpacing.xxs),
                    )
                    OutlinedTextField(
                        value = state.effectiveInstructions,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().padding(top = InkSpacing.xs, bottom = InkSpacing.md),
                        minLines = 6,
                        maxLines = 12,
                        label = { Text("Refreshed effective prompt") },
                    )
                } else {
                    Text(
                        "Change TEMPLATE controls, then tap ↻ Refresh instructions to rebuild the " +
                            "effective prompt shown here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.secondaryText,
                        modifier = Modifier.padding(bottom = InkSpacing.sm),
                    )
                }
                Text(
                    "Each prompt needs at least one System message. Add User / AI messages for " +
                        "multi-turn structure — an AI message primes the model's own reply style.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.secondaryText,
                    modifier = Modifier.padding(bottom = InkSpacing.sm),
                )
                state.messages.forEach { message ->
                    PromptMessageEditor(
                        message = message,
                        onRoleChange = { role -> viewModel.onMessageRole(message.localId, role) },
                        onContentChange = { viewModel.onMessageContent(message.localId, it) },
                        onCopy = { clipboard.setText(AnnotatedString(message.content)) },
                        onRemove = { viewModel.removeMessage(message.localId) },
                        modifier = Modifier.padding(bottom = InkSpacing.sm),
                    )
                }
                InkTextButton(label = "+ Add message", onClick = { viewModel.addMessage() })
            }
            PromptEditorTab.Advanced -> {
                VoiceToTextField(
                    value = state.bias,
                    onValueChange = viewModel::onBias,
                    label = "Bias",
                    singleLine = true,
                )
                VoiceToTextField(
                    value = state.guidance,
                    onValueChange = viewModel::onGuidance,
                    label = "In-depth guidance",
                    minLines = 8,
                    modifier = Modifier.padding(top = InkSpacing.sm),
                )
            }
            PromptEditorTab.Description -> {
                VoiceToTextField(
                    value = state.description,
                    onValueChange = viewModel::onDescription,
                    label = "Description",
                    minLines = 5,
                )
            }
        }
    }
}

@Composable
private fun PromptMessageEditor(
    message: PromptMessageUi,
    onRoleChange: (PromptRole) -> Unit,
    onContentChange: (String) -> Unit,
    onCopy: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = inkTokens()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, tokens.hairline, RoundedCornerShape(inkRadiusSm()))
            .padding(InkSpacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            InkSegmentedPill(
                options = PromptRole.entries.map { SegmentedOption(it.name.lowercase(), it.name) },
                selectedId = message.role,
                onSelect = { id -> onRoleChange(PromptRole.entries.first { role -> role.name.lowercase() == id }) },
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onCopy) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy message", tint = tokens.secondaryText)
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Remove message", tint = tokens.secondaryText)
            }
        }
        VoiceToTextField(
            value = message.content,
            onValueChange = onContentChange,
            label = "",
            minLines = 6,
            modifier = Modifier.padding(top = InkSpacing.xs),
        )
    }
}
