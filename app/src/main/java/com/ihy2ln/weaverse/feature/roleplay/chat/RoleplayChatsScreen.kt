package com.ihy2ln.weaverse.feature.roleplay.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusSm
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.util.alwaysScrollEndSpacer
import com.ihy2ln.weaverse.feature.roleplay.friends.CharacterAvatar

/** Unread badge / new-chat accent, the green a messenger app uses. */
private val MessengerAccent = Color(0xFF25D366)

/**
 * Conversation list in the shape a phone messenger uses: a pill search field,
 * All / Unread / Groups filters, rows with an unread count on the right, and a
 * new-chat button floating over the list.
 *
 * [showFilters] and [onNewChat] are off by default so the Storyboard and RPG
 * pickers can reuse this list without messenger chrome that does not apply.
 */
@Composable
fun RoleplayChatsScreen(
    onChatClick: (String) -> Unit,
    showFilters: Boolean = false,
    onNewChat: (() -> Unit)? = null,
    viewModel: RoleplayChatsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val tokens = inkTokens()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SearchPill(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
            )

            if (showFilters) {
                Row(
                    modifier = Modifier.padding(
                        start = InkSpacing.md,
                        end = InkSpacing.md,
                        bottom = InkSpacing.xs,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
                ) {
                    ChatFilter.entries.forEach { filter ->
                        FilterChip(
                            label = filter.label,
                            selected = state.filter == filter,
                            onClick = { viewModel.onFilterChange(filter) },
                        )
                    }
                }
            }

            if (state.chats.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(InkSpacing.lg),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        when {
                            state.query.isNotBlank() -> "Nothing matches \"${state.query}\"."
                            state.filter == ChatFilter.Unread -> "You're all caught up."
                            state.filter == ChatFilter.Groups -> "No group chats yet."
                            else -> "No conversations yet. Open Contacts and pick someone."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.secondaryText,
                        textAlign = TextAlign.Center,
                    )
                }
                return@Column
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.chats, key = { it.chatId }) { chat ->
                    ChatRow(
                        chat = chat,
                        onClick = {
                            viewModel.markRead(chat.chatId)
                            onChatClick(chat.chatId)
                        },
                    )
                }
                alwaysScrollEndSpacer()
            }
        }

        if (onNewChat != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(InkSpacing.lg)
                    .size(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MessengerAccent)
                    .clickable(onClickLabel = "New chat", onClick = onNewChat),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "✎",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun SearchPill(value: String, onValueChange: (String) -> Unit) {
    val tokens = inkTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(InkSpacing.md)
            .clip(RoundedCornerShape(percent = 50))
            .background(tokens.hover)
            .padding(horizontal = InkSpacing.md, vertical = InkSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⌕", color = tokens.secondaryText, style = MaterialTheme.typography.bodyLarge)
        Box(modifier = Modifier.padding(start = InkSpacing.sm)) {
            if (value.isEmpty()) {
                Text(
                    "Search",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.secondaryText,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = tokens.primaryText),
                cursorBrush = SolidColor(tokens.primaryText),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val tokens = inkTokens()
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) tokens.activePillLabel else tokens.secondaryText,
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(if (selected) tokens.activePill else tokens.hover)
            .clickable(onClick = onClick)
            .padding(horizontal = InkSpacing.md, vertical = InkSpacing.xs),
    )
}

@Composable
private fun ChatRow(chat: RpChatRowUi, onClick: () -> Unit) {
    val tokens = inkTokens()
    val unread = chat.unreadCount > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = InkSpacing.md, vertical = InkSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm),
    ) {
        CharacterAvatar(name = chat.title, colorHex = chat.avatarColorHex, size = 48.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                chat.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (chat.preview.isNotBlank()) {
                Text(
                    chat.preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (unread) tokens.primaryText else tokens.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            val stamp = relativeStamp(chat.updatedAt)
            if (stamp.isNotBlank()) {
                Text(
                    stamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (unread) MessengerAccent else tokens.secondaryText,
                )
            }
            if (unread) {
                Box(
                    modifier = Modifier
                        .padding(top = InkSpacing.xs)
                        .widthIn(min = 20.dp)
                        .clip(CircleShape)
                        .background(MessengerAccent)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (chat.unreadCount > 99) "99+" else "${chat.unreadCount}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
        }
    }
}
