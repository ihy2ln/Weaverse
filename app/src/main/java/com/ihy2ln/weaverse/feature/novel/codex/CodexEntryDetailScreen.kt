package com.ihy2ln.weaverse.feature.novel.codex

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.components.AudioMediaPlayer
import com.ihy2ln.weaverse.core.ui.components.InkChip
import com.ihy2ln.weaverse.core.ui.components.InkConfirmButton
import com.ihy2ln.weaverse.core.ui.components.InkDeleteButton
import com.ihy2ln.weaverse.core.ui.components.InkModeCapsule
import com.ihy2ln.weaverse.core.ui.components.InkOutlinedButton
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.core.ui.components.InkToolbar
import com.ihy2ln.weaverse.core.ui.components.VoiceToTextField
import com.ihy2ln.weaverse.core.ui.components.ZoomableMedia
import com.ihy2ln.weaverse.core.ui.theme.CodexCharacters
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.util.AlwaysScrollEndPadding
import com.ihy2ln.weaverse.core.ui.util.adaptiveContentPadding
import com.ihy2ln.weaverse.data.db.entities.CodexEntryEntity

@Composable
fun CodexEntryDetailScreen(
    entryId: String,
    onBack: () -> Unit,
    onOpenEntry: (String) -> Unit = {},
    viewModel: CodexEntryDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(entryId) { viewModel.load(entryId) }
    val state by viewModel.uiState.collectAsState()

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importMedia(uris)
    }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importMedia(uris)
    }

    LaunchedEffect(state.mediaPickRequestId) {
        if (state.mediaPickRequestId > 0L) {
            mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
        }
    }

    LaunchedEffect(state.audioPickRequestId) {
        if (state.audioPickRequestId > 0L) {
            audioPicker.launch(arrayOf("audio/*", "audio/mpeg", "audio/wav", "audio/x-wav"))
        }
    }

    val contentPad = adaptiveContentPadding()
    val clipboard = LocalClipboardManager.current
    Column(modifier = Modifier.fillMaxSize()) {
        Box {
            InkToolbar(
                title = state.name.ifBlank { "Codex" },
                subtitle = "Codex",
                canGoBack = true,
                onBack = onBack,
                onSettings = { viewModel.onShowSettingsMenuChange(true) },
            )
            CodexEntrySettingsMenu(
                expanded = state.showSettingsMenu,
                trackMentions = state.trackMentions,
                caseSensitiveMatching = state.caseSensitiveMatching,
                onDismiss = { viewModel.onShowSettingsMenuChange(false) },
                onCopy = {
                    clipboard.setText(AnnotatedString("${state.name}\n\n${state.plainText}"))
                },
                onPaste = {
                    clipboard.getText()?.text?.let(viewModel::onPaste)
                },
                onTrackMentionsChange = viewModel::onTrackMentions,
                onCaseSensitiveMatchingChange = viewModel::onCaseSensitiveMatching,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPad),
        ) {
            VoiceToTextField(
                value = state.name,
                onValueChange = viewModel::onName,
                label = "Name",
                singleLine = true,
            )
            VoiceToTextField(
                value = state.aliasesText,
                onValueChange = viewModel::onAliasesText,
                label = "Aliases / nicknames (comma separated)",
                singleLine = true,
                modifier = Modifier.padding(top = InkSpacing.sm),
            )
            VoiceToTextField(
                value = state.plainText,
                onValueChange = viewModel::onBody,
                label = "Entry text",
                minLines = 6,
                modifier = Modifier.padding(top = InkSpacing.md),
            )
            Text(
                "Pictures, videos & audio",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = InkSpacing.lg, bottom = InkSpacing.sm),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                InkOutlinedButton(
                    label = "Add media",
                    onClick = viewModel::requestMediaPick,
                    modifier = Modifier.weight(1f),
                )
                InkOutlinedButton(
                    label = "Add audio",
                    onClick = viewModel::requestAudioPick,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = InkSpacing.sm),
                )
            }
            state.media.forEach { item ->
                Column(modifier = Modifier.padding(top = InkSpacing.sm)) {
                    if (item.isAudio) {
                        AudioMediaPlayer(path = item.path, label = "Audio")
                    } else {
                        ZoomableMedia(
                            path = item.path,
                            isVideo = item.isVideo,
                            contentDescription = "Codex media",
                            contentScale = ContentScale.Fit,
                        )
                    }
                    InkDeleteButton(
                        itemName = "this media",
                        onConfirmedDelete = { viewModel.removeMedia(item.id) },
                    )
                }
            }
            Text(
                "Relationships",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = InkSpacing.lg, bottom = InkSpacing.sm),
            )
            state.relationships.forEach { row ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = InkSpacing.xxs),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onOpenEntry(row.otherEntryId) },
                    ) {
                        Text(
                            if (row.outgoing) row.label else "${row.label} (of you)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "${if (row.outgoing) "→" else "←"} ${row.otherEntryName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    InkDeleteButton(
                        itemName = "this relationship",
                        onConfirmedDelete = { viewModel.removeRelationship(row.id) },
                    )
                }
            }
            InkOutlinedButton(
                label = "+ Add relationship",
                onClick = { viewModel.onShowAddRelationshipChange(true) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = InkSpacing.xs),
            )
            if (state.showAddRelationship) {
                AddRelationshipDialog(
                    candidates = state.otherEntries,
                    onConfirm = viewModel::addRelationship,
                    onDismiss = { viewModel.onShowAddRelationshipChange(false) },
                )
            }
            Text(
                "Used in",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = InkSpacing.lg, bottom = InkSpacing.sm),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
                InkModeCapsule(
                    label = "Everywhere",
                    selected = state.usageMode == "everywhere",
                    onClick = { viewModel.onUsageMode("everywhere") },
                    compact = true,
                )
                InkModeCapsule(
                    label = "Specific books",
                    selected = state.usageMode == "specific",
                    onClick = { viewModel.onUsageMode("specific") },
                    compact = true,
                )
            }
            if (state.usageMode == "specific") {
                if (state.allBooks.isEmpty() && state.allRoleplayChats.isEmpty()) {
                    Text(
                        "No books or roleplay chats yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = InkSpacing.sm),
                    )
                }
                if (state.allBooks.isNotEmpty()) {
                    Text(
                        "Books",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = InkSpacing.sm),
                    )
                    state.allBooks.forEach { book ->
                        UsageCheckRow(
                            label = book.title.ifBlank { "Untitled Book" },
                            checked = book.id in state.usageBookIds,
                            onToggle = { viewModel.onToggleUsageBook(book.id) },
                        )
                    }
                }
                if (state.allRoleplayChats.isNotEmpty()) {
                    Text(
                        "Roleplay chats",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = InkSpacing.sm),
                    )
                    state.allRoleplayChats.forEach { chat ->
                        UsageCheckRow(
                            label = chat.title.ifBlank { "Untitled chat" },
                            checked = chat.id in state.usageRoleplayIds,
                            onToggle = { viewModel.onToggleUsageRoleplay(chat.id) },
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = InkSpacing.md)
                    .clickable { viewModel.onAlwaysInclude(!state.alwaysInclude) },
            ) {
                Checkbox(
                    checked = state.alwaysInclude,
                    onCheckedChange = viewModel::onAlwaysInclude,
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
                Text(
                    "Always include in context",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            if (state.statusMessage.isNotBlank()) {
                Text(
                    state.statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = InkSpacing.sm),
                )
            }
            InkConfirmButton(
                onClick = viewModel::save,
                label = if (state.saved) "Saved" else "Save",
                contentDescription = if (state.saved) "Saved" else "Save entry",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = InkSpacing.lg),
            )
            Spacer(modifier = Modifier.height(AlwaysScrollEndPadding))
        }
    }
}

@Composable
private fun AddRelationshipDialog(
    candidates: List<CodexEntryEntity>,
    onConfirm: (toEntryId: String, label: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedId by remember { mutableStateOf(candidates.firstOrNull()?.id) }
    var label by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add relationship") },
        text = {
            if (candidates.isEmpty()) {
                Text(
                    "No other Codex entries yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Related to",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = InkSpacing.xxs, bottom = InkSpacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
                    ) {
                        candidates.forEach { entry ->
                            InkChip(
                                label = entry.name,
                                color = CodexCharacters,
                                selected = selectedId == entry.id,
                                onClick = { selectedId = entry.id },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Relationship") },
                        placeholder = { Text("e.g. sibling of, rival of, mentor to") },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedId?.let { onConfirm(it, label) } },
                enabled = selectedId != null && label.isNotBlank(),
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun UsageCheckRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        Text(label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CodexEntrySettingsMenu(
    expanded: Boolean,
    trackMentions: Boolean,
    caseSensitiveMatching: Boolean,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onTrackMentionsChange: (Boolean) -> Unit,
    onCaseSensitiveMatchingChange: (Boolean) -> Unit,
) {
    val labelColor = MaterialTheme.colorScheme.onSurface
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Copy entry", color = labelColor) }, onClick = { onCopy(); onDismiss() })
        DropdownMenuItem(text = { Text("Paste into text", color = labelColor) }, onClick = { onPaste(); onDismiss() })
        HorizontalDivider(modifier = Modifier.padding(vertical = InkSpacing.xs))
        Text(
            "Tracking / matching",
            style = MaterialTheme.typography.labelSmall,
            color = mutedColor,
            modifier = Modifier.padding(horizontal = InkSpacing.md, vertical = InkSpacing.xs),
        )
        DropdownMenuItem(
            text = { Text("Track this entry by name/alias", color = labelColor) },
            trailingIcon = { Checkbox(checked = trackMentions, onCheckedChange = null) },
            onClick = { onTrackMentionsChange(!trackMentions) },
        )
        DropdownMenuItem(
            text = { Text("Case-sensitive matching", color = if (trackMentions) labelColor else labelColor.copy(alpha = 0.38f)) },
            trailingIcon = { Checkbox(checked = caseSensitiveMatching, onCheckedChange = null, enabled = trackMentions) },
            onClick = { if (trackMentions) onCaseSensitiveMatchingChange(!caseSensitiveMatching) },
            enabled = trackMentions,
        )
    }
}
