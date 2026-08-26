package com.ihy2ln.weaverse.feature.roleplay.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.util.alwaysScrollEndSpacer
import com.ihy2ln.weaverse.feature.roleplay.friends.CharacterAvatar

/**
 * Open conversations, newest first — the same row treatment as the friends
 * list, with a preview and a relative timestamp instead of a bare title.
 */
@Composable
fun RoleplayChatsScreen(
    onChatClick: (String) -> Unit,
    viewModel: RoleplayChatsViewModel = hiltViewModel(),
) {
    val chats by viewModel.chats.collectAsState()
    val tokens = inkTokens()

    if (chats.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(InkSpacing.lg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "No conversations yet. Open Friends and pick someone to talk to.",
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.secondaryText,
            )
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(chats, key = { it.chatId }) { chat ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onChatClick(chat.chatId) }
                    .padding(horizontal = InkSpacing.lg, vertical = InkSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm),
            ) {
                CharacterAvatar(name = chat.title, colorHex = chat.avatarColorHex)
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            chat.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        val stamp = relativeStamp(chat.updatedAt)
                        if (stamp.isNotBlank()) {
                            Text(
                                stamp,
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.secondaryText,
                                modifier = Modifier.padding(start = InkSpacing.xs),
                            )
                        }
                    }
                    if (chat.preview.isNotBlank()) {
                        Text(
                            chat.preview,
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        alwaysScrollEndSpacer()
    }
}
