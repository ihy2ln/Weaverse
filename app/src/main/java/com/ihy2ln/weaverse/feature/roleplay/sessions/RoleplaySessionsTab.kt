package com.ihy2ln.weaverse.feature.roleplay.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.EmptyState
import com.ihy2ln.weaverse.core.ui.Spacing
import com.ihy2ln.weaverse.data.db.entity.RpChatEntity

/** The rail's Sessions tab (Revision 02 §1.4): every roleplay chat, newest first, so it can be
 * jumped into from any destination — distinct from the primary Chats destination's own in-screen
 * chat list, which only shows once you've already navigated there. */
@Composable
fun RoleplaySessionsTab(
    modifier: Modifier = Modifier,
    onOpenChat: (String) -> Unit,
    viewModel: RoleplaySessionsViewModel = hiltViewModel(),
) {
    val chats by viewModel.chats.collectAsState()
    val characters by viewModel.characters.collectAsState()

    if (chats.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Forum,
            title = "No sessions yet",
            subtitle = "Start a chat from the Chats destination to see it here.",
            modifier = modifier,
        )
        return
    }

    val sortedChats = remember(chats) { chats.sortedByDescending { it.updatedAt } }

    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(items = sortedChats, key = { it.id }) { chat ->
            val character = characters.firstOrNull { it.id == chat.characterId }
            SessionRow(chat = chat, characterName = character?.name, onClick = { onOpenChat(chat.id) })
        }
    }
}

@Composable
private fun SessionRow(chat: RpChatEntity, characterName: String?, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(Spacing.xxl)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(modifier = Modifier.padding(start = Spacing.sm)) {
                Text(chat.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (characterName != null) {
                    Text(
                        characterName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
