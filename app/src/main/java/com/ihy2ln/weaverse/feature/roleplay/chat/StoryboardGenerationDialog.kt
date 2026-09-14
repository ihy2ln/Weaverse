package com.ihy2ln.weaverse.feature.roleplay.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens

@Composable
fun StoryboardGenerationDialog(
    state: RoleplayChatUiState,
    onSource: (String) -> Unit,
    onModel: (String) -> Unit,
    onGenerateMissingArt: (Boolean) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onOffline: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    val generation = state.storyboardGeneration
    val tokens = inkTokens()
    val isBusy = generation.phase == StoryboardGenerationPhase.Generating
    val canApply = generation.phase == StoryboardGenerationPhase.ReadyToApply ||
        generation.phase == StoryboardGenerationPhase.OfflineFallback

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create storyboard page") },
        text = {
            Column {
                Text(
                    "Turn a scene or story beat into editable panels. Existing artwork is reused first; offline mode always creates a usable page.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.secondaryText,
                )
                OutlinedTextField(
                    value = generation.prompt,
                    onValueChange = onSource,
                    enabled = !isBusy,
                    label = { Text("Scene or story beat") },
                    minLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = InkSpacing.sm),
                )
                Text(
                    "${if (generation.rightToLeft) "Manga · right to left" else "Comic · left to right"} model",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = InkSpacing.md),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = InkSpacing.xs),
                ) {
                    Checkbox(
                        checked = generation.generateMissingArt,
                        onCheckedChange = onGenerateMissingArt,
                        enabled = !isBusy,
                    )
                    Text(
                        "Generate missing artwork with AI",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                if (state.writingModels.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 120.dp),
                    ) {
                        items(state.writingModels, key = { it.id }) { model ->
                            val selected = state.selectedModelRef.endsWith(model.id)
                            Text(
                                model.displayName,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.primary else tokens.primaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isBusy) { onModel(model.id) }
                                    .padding(vertical = InkSpacing.xs),
                            )
                        }
                    }
                }
                if (isBusy) {
                    Text(
                        "${generation.progress}% · ${generation.status}",
                        style = MaterialTheme.typography.labelMedium,
                        color = tokens.secondaryText,
                        modifier = Modifier.padding(top = InkSpacing.sm),
                    )
                } else if (generation.status.isNotBlank()) {
                    Text(
                        generation.status,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (generation.phase == StoryboardGenerationPhase.Failed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.padding(top = InkSpacing.sm),
                    )
                }
                generation.draft?.let { draft ->
                    Text(
                        "${draft.title} · ${draft.panels.size} panel(s) · ${draft.templateId}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = InkSpacing.sm),
                    )
                    draft.panels.take(3).forEachIndexed { index, panel ->
                        Text(
                            "${index + 1}. ${panel.description.ifBlank { panel.dialogue }.take(100)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.secondaryText,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row {
                if (isBusy) {
                    TextButton(onClick = onStop) { Text("Stop") }
                } else if (canApply) {
                    TextButton(onClick = onApply) { Text("Apply page") }
                } else {
                    TextButton(onClick = if (generation.phase == StoryboardGenerationPhase.Failed || generation.phase == StoryboardGenerationPhase.Stopped) onRetry else onStart) {
                        Text(if (generation.phase == StoryboardGenerationPhase.Failed || generation.phase == StoryboardGenerationPhase.Stopped) "Retry" else "Create")
                    }
                }
            }
        },
        dismissButton = {
            Row {
                if (!isBusy && !canApply) {
                    TextButton(onClick = onOffline) { Text("Continue offline") }
                }
                TextButton(onClick = onDismiss) { Text(if (isBusy) "Close" else "Cancel") }
            }
        },
    )
}
