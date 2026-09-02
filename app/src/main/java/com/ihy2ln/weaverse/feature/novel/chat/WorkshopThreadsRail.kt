package com.ihy2ln.weaverse.feature.novel.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.util.alwaysScrollEndSpacer

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkshopThreadsRail(
    selectedThreadId: String?,
    onThreadClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkshopThreadsViewModel = hiltViewModel(),
) {
    val threads by viewModel.threads.collectAsState()
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var pendingDelete by remember { mutableStateOf(emptySet<String>()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .22f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = InkSpacing.sm, vertical = InkSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Chats", style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = { viewModel.createThread(onThreadClick) }) { Text("+ Add") }
        }
        if (selectedIds.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = InkSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("${selectedIds.size} selected", style = MaterialTheme.typography.labelSmall)
                Row {
                    TextButton(onClick = { selectedIds = emptySet() }) { Text("Clear") }
                    TextButton(onClick = { pendingDelete = selectedIds }) { Text("Quick remove") }
                }
            }
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(threads, key = { it.id }) { thread ->
                var menuOpen by remember(thread.id) { mutableStateOf(false) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            when {
                                thread.id in selectedIds -> MaterialTheme.colorScheme.primary.copy(alpha = .16f)
                                thread.id == selectedThreadId -> MaterialTheme.colorScheme.surfaceVariant
                                else -> MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                            },
                        )
                        .combinedClickable(
                            onClick = {
                                if (selectedIds.isEmpty()) onThreadClick(thread.id)
                                else selectedIds = if (thread.id in selectedIds) {
                                    selectedIds - thread.id
                                } else {
                                    selectedIds + thread.id
                                }
                            },
                            onLongClick = { menuOpen = true },
                        )
                        .padding(horizontal = InkSpacing.md, vertical = InkSpacing.sm),
                ) {
                    Text(
                        thread.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (thread.id == selectedThreadId) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    if (thread.pinned) Text("Pinned", style = MaterialTheme.typography.labelSmall)
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (thread.id in selectedIds) "Unselect" else "Select for quick remove") },
                            onClick = {
                                menuOpen = false
                                selectedIds = if (thread.id in selectedIds) {
                                    selectedIds - thread.id
                                } else {
                                    selectedIds + thread.id
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete chat") },
                            onClick = { menuOpen = false; pendingDelete = setOf(thread.id) },
                        )
                    }
                }
            }
            alwaysScrollEndSpacer()
        }
    }

    if (pendingDelete.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { pendingDelete = emptySet() },
            title = { Text(if (pendingDelete.size == 1) "Delete chat?" else "Delete selected chats?") },
            text = { Text("The selected chat history will be permanently removed.") },
            confirmButton = {
                TextButton(onClick = {
                    val deleting = pendingDelete
                    viewModel.deleteThreads(deleting) { next -> next?.let(onThreadClick) }
                    selectedIds = selectedIds - deleting
                    pendingDelete = emptySet()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = emptySet() }) { Text("Cancel") } },
        )
    }
}
