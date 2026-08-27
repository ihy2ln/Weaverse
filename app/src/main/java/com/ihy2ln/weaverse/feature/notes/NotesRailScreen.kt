package com.ihy2ln.weaverse.feature.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.components.InkConfirmButton
import com.ihy2ln.weaverse.core.ui.components.InkLongPressMenuBox
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.core.ui.components.LongPressMenuItem
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.util.alwaysScrollEndSpacer
import com.ihy2ln.weaverse.data.db.entities.SnippetEntity

/**
 * Modular Notes list for the shared shell left rail (same collapse / expand / slide
 * chrome as Novel Manuscript and Roleplay Codex rails).
 */
@Composable
fun NotesRailScreen(
    viewModel: NotesViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val tokens = inkTokens()

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = InkSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
        ) {
            Text(
                "Notes",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            InkConfirmButton(
                onClick = viewModel::createNote,
                label = "New",
                contentDescription = "New note",
            )
        }
        Text(
            "Personal notes — not tied to a book. Speak, type, attach media.",
            style = MaterialTheme.typography.bodySmall,
            color = tokens.secondaryText,
            modifier = Modifier.padding(bottom = InkSpacing.sm),
        )
        if (state.selectionMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = InkSpacing.xs),
                horizontalArrangement = Arrangement.End,
            ) {
                InkTextButton(label = "Cancel", onClick = viewModel::exitSelectionMode, compact = true)
                InkTextButton(
                    label = "Remove (${state.selectedForRemoval.size})",
                    onClick = viewModel::removeSelected,
                    enabled = state.selectedForRemoval.isNotEmpty(),
                    compact = true,
                )
            }
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(InkSpacing.xs),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            items(state.notes, key = { it.id }) { note ->
                NoteRailRow(
                    note = note,
                    selected = note.id == state.selectedId,
                    markedForRemoval = state.selectedForRemoval.contains(note.id),
                    selectionMode = state.selectionMode,
                    onClick = {
                        if (state.selectionMode) {
                            viewModel.toggleSelectedForRemoval(note.id)
                        } else {
                            viewModel.selectNote(note.id)
                        }
                    },
                    onRemove = { viewModel.deleteNote(note.id) },
                    onEnterSelectMode = viewModel::enterSelectionMode,
                )
            }
            alwaysScrollEndSpacer()
        }
    }
}

@Composable
private fun NoteRailRow(
    note: SnippetEntity,
    selected: Boolean,
    markedForRemoval: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onEnterSelectMode: () -> Unit,
) {
    val shape = RoundedCornerShape(InkSpacing.radiusSm)
    InkLongPressMenuBox(
        onClick = onClick,
        onRemove = onRemove,
        onEnterSelectMode = onEnterSelectMode,
        selectionMode = selectionMode,
        extraItems = listOf(LongPressMenuItem("Open", onClick)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(
                    when {
                        markedForRemoval -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    },
                )
                .border(
                    width = if (selected && !markedForRemoval) 1.5.dp else 0.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = shape,
                )
                .padding(InkSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                note.title.ifBlank { "Untitled" },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
