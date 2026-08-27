package com.ihy2ln.weaverse.feature.novel.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.components.InkConfirmDeleteDialog
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.core.ui.components.ItemAdminMenu
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.util.alwaysScrollEndSpacer
import com.ihy2ln.weaverse.feature.library.ItemAdminAction
import java.text.DateFormat
import java.util.Date

@Composable
fun WorkshopThreadsRail(
    selectedThreadId: String?,
    onThreadClick: (String) -> Unit,
    viewModel: WorkshopThreadsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    WorkshopThreadListPane(
        state = state,
        selectedThreadId = selectedThreadId,
        onQuery = viewModel::onQuery,
        onThreadClick = onThreadClick,
        onCreate = { viewModel.createThread(onThreadClick) },
        onDelete = { id -> viewModel.deleteThread(id) },
        onCopy = { id -> viewModel.copyThread(id, onThreadClick) },
        onPin = viewModel::togglePin,
        onRename = viewModel::beginRename,
        onSelectToRemove = viewModel::enterSelectToRemove,
        onToggleSelect = viewModel::toggleSelected,
        onExitSelect = viewModel::exitSelectToRemove,
        onDeleteSelected = { viewModel.deleteSelected() },
        modifier = Modifier.fillMaxSize(),
    )
    RenameThreadDialog(viewModel)
}

@Composable
fun WorkshopThreadListPane(
    state: WorkshopThreadsUiState,
    selectedThreadId: String?,
    onQuery: (String) -> Unit,
    onThreadClick: (String) -> Unit,
    onCreate: () -> Unit,
    onDelete: (String) -> Unit,
    onCopy: (String) -> Unit,
    onPin: (String) -> Unit,
    onRename: (String) -> Unit,
    onSelectToRemove: (String?) -> Unit,
    onToggleSelect: (String) -> Unit,
    onExitSelect: () -> Unit,
    onDeleteSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = inkTokens()
    Column(modifier = modifier.padding(InkSpacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQuery,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Search threads...") },
            )
            InkTextButton(label = "+", onClick = onCreate, compact = true)
        }
        if (state.selectingToRemove) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = InkSpacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("${state.selectedToRemove.size} selected", color = tokens.secondaryText, fontSize = 12.sp)
                Row {
                    InkTextButton(label = "Cancel", onClick = onExitSelect, compact = true)
                    InkTextButton(
                        label = "Remove",
                        onClick = onDeleteSelected,
                        compact = true,
                        enabled = state.selectedToRemove.isNotEmpty(),
                    )
                }
            }
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            if (state.pinned.isNotEmpty()) {
                item("pinned-header") {
                    ThreadSectionHeader("Pinned", state.pinned.size)
                }
                items(state.pinned, key = { "pin-${it.id}" }) { thread ->
                    ThreadRow(
                        thread = thread,
                        selected = thread.id == selectedThreadId,
                        selecting = state.selectingToRemove,
                        checked = thread.id in state.selectedToRemove,
                        onClick = { if (state.selectingToRemove) onToggleSelect(thread.id) else onThreadClick(thread.id) },
                        onDelete = { onDelete(thread.id) },
                        onCopy = { onCopy(thread.id) },
                        onPin = { onPin(thread.id) },
                        onRename = { onRename(thread.id) },
                        onSelectToRemove = { onSelectToRemove(thread.id) },
                    )
                }
            }
            item("unpinned-header") {
                ThreadSectionHeader("Unpinned", state.unpinned.size)
            }
            items(state.unpinned, key = { it.id }) { thread ->
                ThreadRow(
                    thread = thread,
                    selected = thread.id == selectedThreadId,
                    selecting = state.selectingToRemove,
                    checked = thread.id in state.selectedToRemove,
                    onClick = { if (state.selectingToRemove) onToggleSelect(thread.id) else onThreadClick(thread.id) },
                    onDelete = { onDelete(thread.id) },
                    onCopy = { onCopy(thread.id) },
                    onPin = { onPin(thread.id) },
                    onRename = { onRename(thread.id) },
                    onSelectToRemove = { onSelectToRemove(thread.id) },
                )
            }
            alwaysScrollEndSpacer()
        }
    }
}

@Composable
fun WorkshopCollapsedChatRail(
    threadCount: Int,
    onExpand: () -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = inkTokens()
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(52.dp)
            .background(tokens.panel)
            .clickable(onClick = onExpand)
            .padding(vertical = InkSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(InkSpacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(tokens.hover),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Forum, contentDescription = "Expand chats")
        }
        Text(
            "$threadCount",
            fontSize = 11.sp,
            color = tokens.secondaryText,
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(tokens.hover)
                .clickable(onClick = onCreate),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Add, contentDescription = "New thread")
        }
    }
}

@Composable
private fun ThreadSectionHeader(label: String, count: Int) {
    val tokens = inkTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = InkSpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = tokens.secondaryText)
        Text("$count threads", fontSize = 12.sp, color = tokens.secondaryText)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThreadRow(
    thread: WorkshopThreadUi,
    selected: Boolean,
    selecting: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onPin: () -> Unit,
    onRename: () -> Unit,
    onSelectToRemove: () -> Unit,
) {
    val tokens = inkTokens()
    var menuOpen by remember(thread.id) { mutableStateOf(false) }
    var confirmDelete by remember(thread.id) { mutableStateOf(false) }
    val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(thread.updatedAt))
    val shape = RoundedCornerShape(InkSpacing.radiusSm)
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .then(
                    if (selected) {
                        Modifier.border(InkSpacing.hairline, tokens.hairline, shape)
                    } else {
                        Modifier
                    },
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuOpen = true },
                )
                .padding(horizontal = InkSpacing.sm, vertical = InkSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selecting) {
                Checkbox(checked = checked, onCheckedChange = { onClick() })
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    thread.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (selected) MaterialTheme.colorScheme.primary else tokens.primaryText,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(date, fontSize = 12.sp, color = tokens.secondaryText)
                    Icon(
                        Icons.Default.ChatBubbleOutline,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = InkSpacing.sm, end = InkSpacing.xxs)
                            .size(12.dp),
                        tint = tokens.secondaryText,
                    )
                    Text("${thread.messageCount}", fontSize = 12.sp, color = tokens.secondaryText)
                }
            }
        }
        ItemAdminMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            onAction = { action ->
                when (action) {
                    ItemAdminAction.Copy -> onCopy()
                    ItemAdminAction.Delete -> confirmDelete = true
                    ItemAdminAction.SelectToRemove -> onSelectToRemove()
                    ItemAdminAction.Rename -> onRename()
                    ItemAdminAction.Pin -> onPin()
                    else -> Unit
                }
            },
            actions = listOf(
                ItemAdminAction.Rename,
                ItemAdminAction.Pin,
                ItemAdminAction.Copy,
                ItemAdminAction.Delete,
                ItemAdminAction.SelectToRemove,
            ),
            title = thread.name,
        )
    }
    if (confirmDelete) {
        InkConfirmDeleteDialog(
            itemName = thread.name,
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
fun RenameThreadDialog(viewModel: WorkshopThreadsViewModel) {
    val state by viewModel.uiState.collectAsState()
    val renameId = state.renameThreadId ?: return
    androidx.compose.material3.AlertDialog(
        onDismissRequest = viewModel::dismissRename,
        title = { Text("Rename thread") },
        text = {
            OutlinedTextField(
                value = state.renameDraft,
                onValueChange = viewModel::onRenameDraft,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            InkTextButton(label = "Save", onClick = viewModel::confirmRename)
        },
        dismissButton = {
            InkTextButton(label = "Cancel", onClick = viewModel::dismissRename)
        },
    )
    // Keep the unused id referenced so the dialog keys off the open thread.
    renameId.let { }
}
