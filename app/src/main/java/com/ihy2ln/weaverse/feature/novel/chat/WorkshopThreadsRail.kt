package com.ihy2ln.weaverse.feature.novel.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.util.alwaysScrollEndSpacer
import com.ihy2ln.weaverse.feature.chats.ChatsHubViewModel

/**
 * The Write/Plan tool-rail's "Chats" tab — every book's Workshop chat threads, grouped by book
 * (select a book to drill into its sub chats), plus book-less mini chats. Same organization and
 * data as the standalone Chats hub, just in the compact rail shape.
 */
@Composable
fun WorkshopThreadsRail(
    selectedThreadId: String?,
    onThreadClick: (String) -> Unit,
    viewModel: ChatsHubViewModel = hiltViewModel(),
) {
    val bookGroups by viewModel.bookGroups.collectAsState()
    val miniChats by viewModel.miniChats.collectAsState()
    var selectedBookId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedGroup = bookGroups.find { it.book.id == selectedBookId }

    Column(modifier = Modifier.fillMaxSize()) {
        if (selectedGroup != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = InkSpacing.md, vertical = InkSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InkTextButton(label = "←", onClick = { selectedBookId = null })
                Text(
                    selectedGroup.book.title,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                InkTextButton(
                    label = "+",
                    onClick = { viewModel.createThreadForBook(selectedGroup.book.id, onThreadClick) },
                )
            }
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(selectedGroup.threads, key = { it.id }) { thread ->
                    ThreadRow(
                        name = thread.name,
                        selected = thread.id == selectedThreadId,
                        onClick = { onThreadClick(thread.id) },
                        onDelete = { viewModel.deleteThread(thread.id) },
                    )
                }
                alwaysScrollEndSpacer()
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = InkSpacing.md, vertical = InkSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Chats", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                InkTextButton(
                    label = "+",
                    onClick = { viewModel.createMiniChat(onThreadClick) },
                )
            }
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(bookGroups, key = { it.book.id }) { group ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedBookId = group.book.id }
                            .padding(horizontal = InkSpacing.md, vertical = InkSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(group.book.title, style = MaterialTheme.typography.titleSmall)
                            val count = group.threads.size
                            Text(
                                "$count chat${if (count == 1) "" else "s"}",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
                if (miniChats.isNotEmpty()) {
                    item {
                        Text(
                            "Mini chats",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = InkSpacing.md, vertical = InkSpacing.xs),
                        )
                    }
                    items(miniChats, key = { it.id }) { thread ->
                        ThreadRow(
                            name = thread.name,
                            selected = thread.id == selectedThreadId,
                            onClick = { onThreadClick(thread.id) },
                            onDelete = { viewModel.deleteThread(thread.id) },
                        )
                    }
                }
                alwaysScrollEndSpacer()
            }
        }
    }
}

@Composable
private fun ThreadRow(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = InkSpacing.md, vertical = InkSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        InkTextButton(label = "−", onClick = onDelete)
    }
}
