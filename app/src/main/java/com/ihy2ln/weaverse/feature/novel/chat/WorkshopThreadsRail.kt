package com.ihy2ln.weaverse.feature.novel.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.components.InkLongPressMenuBox
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.core.ui.components.LongPressMenuItem
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.util.alwaysScrollEndSpacer
import com.ihy2ln.weaverse.data.db.entities.ChatThreadEntity

@Composable
fun WorkshopThreadsRail(
    selectedThreadId: String?,
    onThreadClick: (String) -> Unit,
    expanded: Boolean = true,
    onToggleExpanded: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: WorkshopThreadsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val tokens = inkTokens()

    if (!expanded) {
        Box(
            modifier = modifier
                .width(40.dp)
                .fillMaxHeight()
                .background(tokens.panel)
                .clickable { onToggleExpanded?.invoke() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Expand chats")
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.panel)
            .padding(InkSpacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Chats",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = viewModel::createThread) {
                Icon(Icons.Default.Add, contentDescription = "New chat")
            }
            if (onToggleExpanded != null) {
                IconButton(onClick = onToggleExpanded) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Collapse chats")
                }
            }
        }
        if (state.selectionMode) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
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
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.threads, key = { it.id }) { thread ->
                ThreadRow(
                    thread = thread,
                    selected = thread.id == selectedThreadId,
                    markedForRemoval = state.selectedForRemoval.contains(thread.id),
                    selectionMode = state.selectionMode,
                    onClick = {
                        if (state.selectionMode) {
                            viewModel.toggleSelectedForRemoval(thread.id)
                        } else {
                            onThreadClick(thread.id)
                        }
                    },
                    onRemove = { viewModel.deleteThread(thread.id) },
                    onEnterSelectMode = viewModel::enterSelectionMode,
                )
            }
            alwaysScrollEndSpacer()
        }
    }
}

@Composable
private fun ThreadRow(
    thread: ChatThreadEntity,
    selected: Boolean,
    markedForRemoval: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onEnterSelectMode: () -> Unit,
) {
    val tokens = inkTokens()
    InkLongPressMenuBox(
        onClick = onClick,
        onRemove = onRemove,
        onEnterSelectMode = onEnterSelectMode,
        selectionMode = selectionMode,
        extraItems = listOf(LongPressMenuItem("Open", onClick)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = InkSpacing.xxs),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    when {
                        markedForRemoval -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        selected -> tokens.hover
                        else -> tokens.panel
                    },
                )
                .padding(horizontal = InkSpacing.sm, vertical = InkSpacing.sm),
        ) {
            Text(
                thread.name,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (thread.pinned) {
                Text("Pinned", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
