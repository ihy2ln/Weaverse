package com.ihy2ln.weaverse.feature.chats

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
import com.ihy2ln.weaverse.core.ui.components.InkCard
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.util.alwaysScrollEndSpacer

/**
 * Standalone "Chats" hub — every book's Workshop chat threads grouped by book (select a book to
 * drill into its sub chats), plus freestanding mini chats that aren't attached to any novel.
 */
@Composable
fun ChatsHubScreen(
    onThreadClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatsHubViewModel = hiltViewModel(),
) {
    val bookGroups by viewModel.bookGroups.collectAsState()
    val miniChats by viewModel.miniChats.collectAsState()
    var selectedBookId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedGroup = bookGroups.find { it.book.id == selectedBookId }

    Column(modifier = modifier.fillMaxSize().padding(InkSpacing.lg)) {
        if (selectedGroup != null) {
            InkTextButton(label = "← Chats", onClick = { selectedBookId = null })
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = InkSpacing.sm, bottom = InkSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    selectedGroup.book.title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                InkTextButton(
                    label = "+",
                    onClick = { viewModel.createThreadForBook(selectedGroup.book.id, onThreadClick) },
                )
            }
            LazyColumn {
                items(selectedGroup.threads, key = { it.id }) { thread ->
                    ChatThreadRow(
                        name = thread.name,
                        onClick = { onThreadClick(thread.id) },
                        onDelete = { viewModel.deleteThread(thread.id) },
                    )
                }
                alwaysScrollEndSpacer()
            }
        } else {
            Text(
                "Chats",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = InkSpacing.md),
            )
            LazyColumn {
                if (bookGroups.isNotEmpty()) {
                    item {
                        Text(
                            "Books",
                            style = MaterialTheme.typography.labelLarge,
                            color = inkTokens().secondaryText,
                            modifier = Modifier.padding(bottom = InkSpacing.xs),
                        )
                    }
                    items(bookGroups, key = { it.book.id }) { group ->
                        InkCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = InkSpacing.sm)
                                .clickable { selectedBookId = group.book.id },
                        ) {
                            Text(group.book.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${group.threads.size} chat${if (group.threads.size == 1) "" else "s"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = inkTokens().secondaryText,
                            )
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = InkSpacing.sm, bottom = InkSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Mini chats",
                            style = MaterialTheme.typography.labelLarge,
                            color = inkTokens().secondaryText,
                            modifier = Modifier.weight(1f),
                        )
                        InkTextButton(
                            label = "+",
                            onClick = { viewModel.createMiniChat(onThreadClick) },
                        )
                    }
                }
                if (miniChats.isEmpty()) {
                    item {
                        Text(
                            "No mini chats yet — start one that isn't attached to any novel.",
                            style = MaterialTheme.typography.bodySmall,
                            color = inkTokens().secondaryText,
                        )
                    }
                }
                items(miniChats, key = { it.id }) { thread ->
                    ChatThreadRow(
                        name = thread.name,
                        onClick = { onThreadClick(thread.id) },
                        onDelete = { viewModel.deleteThread(thread.id) },
                    )
                }
                alwaysScrollEndSpacer()
            }
        }
    }
}

@Composable
private fun ChatThreadRow(
    name: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = InkSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        InkTextButton(label = "−", onClick = onDelete)
    }
}
