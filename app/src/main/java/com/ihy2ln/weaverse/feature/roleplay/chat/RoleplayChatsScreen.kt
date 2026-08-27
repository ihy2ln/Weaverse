package com.ihy2ln.weaverse.feature.roleplay.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.components.InkCard
import com.ihy2ln.weaverse.core.ui.components.InkLongPressMenuBox
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.core.ui.components.LongPressMenuItem
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.util.alwaysScrollEndSpacer
import com.ihy2ln.weaverse.data.db.entities.RpChatEntity
import com.ihy2ln.weaverse.feature.library.HomeModeRouting

@Composable
fun RoleplayChatsScreen(
    onChatClick: (String) -> Unit,
    selectedChatId: String? = null,
    compact: Boolean = false,
    viewModel: RoleplayChatsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.pendingOpenId) {
        val id = state.pendingOpenId ?: return@LaunchedEffect
        viewModel.consumePendingOpen()
        onChatClick(id)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(if (compact) InkSpacing.sm else InkSpacing.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Roleplay Chats",
                style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (compact) {
                IconButton(onClick = { viewModel.createChat() }) {
                    Icon(Icons.Default.Add, contentDescription = "New chat")
                }
            } else {
                InkTextButton(label = "New", onClick = { viewModel.createChat() }, compact = true)
            }
        }
        if (state.selectionMode) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.chats, key = { it.id }) { chat ->
                RoleplayChatRow(
                    chat = chat,
                    selected = chat.id == selectedChatId,
                    markedForRemoval = state.selectedForRemoval.contains(chat.id),
                    selectionMode = state.selectionMode,
                    compact = compact,
                    onClick = {
                        if (state.selectionMode) {
                            viewModel.toggleSelectedForRemoval(chat.id)
                        } else {
                            onChatClick(chat.id)
                        }
                    },
                    onRemove = { viewModel.deleteChat(chat.id) },
                    onEnterSelectMode = viewModel::enterSelectionMode,
                )
            }
            alwaysScrollEndSpacer()
        }
    }
}

@Composable
private fun RoleplayChatRow(
    chat: RpChatEntity,
    selected: Boolean,
    markedForRemoval: Boolean,
    selectionMode: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onEnterSelectMode: () -> Unit,
) {
    val tokens = inkTokens()
    val modeLabel = when (HomeModeRouting.normalizeDisplayMode(chat.displayMode)) {
        HomeModeRouting.DUNGEON_MASTER -> "DM"
        HomeModeRouting.STORYBOARD -> "Storyboard"
        else -> "Messenger"
    }
    InkLongPressMenuBox(
        onClick = onClick,
        onRemove = onRemove,
        onEnterSelectMode = onEnterSelectMode,
        selectionMode = selectionMode,
        extraItems = listOf(LongPressMenuItem("Open", onClick)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) InkSpacing.xxs else InkSpacing.sm),
    ) {
        if (compact) {
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
                    chat.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(modeLabel, style = MaterialTheme.typography.labelSmall, color = tokens.secondaryText)
            }
        } else {
            InkCard(
                modifier = Modifier.fillMaxWidth(),
                background = if (markedForRemoval) {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                } else {
                    tokens.panel
                },
            ) {
                Text(chat.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    modeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.secondaryText,
                    modifier = Modifier.padding(top = InkSpacing.xxs),
                )
            }
        }
    }
}
