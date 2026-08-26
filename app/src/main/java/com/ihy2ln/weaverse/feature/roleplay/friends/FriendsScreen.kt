package com.ihy2ln.weaverse.feature.roleplay.friends

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.util.alwaysScrollEndSpacer
import com.ihy2ln.weaverse.core.ui.util.parseHexColor

/**
 * Messenger-style contact list: who you're already talking to on top, everyone
 * from the character codex below. Tapping anyone opens (or starts) their chat.
 */
@Composable
fun FriendsScreen(
    onOpenChat: (String) -> Unit,
    viewModel: FriendsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val tokens = inkTokens()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = InkSpacing.lg, vertical = InkSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                singleLine = true,
                placeholder = { Text("Search friends") },
                modifier = Modifier.weight(1f),
            )
            InkTextButton(
                label = if (state.generating) "…" else "Meet someone",
                onClick = viewModel::generateNewPersonNow,
            )
        }

        if (state.status.isNotBlank()) {
            Text(
                state.status,
                style = MaterialTheme.typography.labelMedium,
                color = tokens.secondaryText,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.clearStatus() }
                    .padding(horizontal = InkSpacing.lg, vertical = InkSpacing.xs),
            )
        }

        if (!state.loading && state.isEmpty) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(InkSpacing.lg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (state.query.isBlank()) {
                        "No friends yet. Add characters under Characters, import a card, " +
                            "or tap Meet someone to have one written for you."
                    } else {
                        "No one matches \"${state.query}\"."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.secondaryText,
                )
            }
            return@Column
        }

        var dmOpen by rememberSaveable { mutableStateOf(true) }
        var allOpen by rememberSaveable { mutableStateOf(true) }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (state.directMessages.isNotEmpty()) {
                item(key = "hdr-dm") {
                    SectionHeader(
                        label = "Direct messages",
                        count = state.directMessages.size,
                        expanded = dmOpen,
                        onToggle = { dmOpen = !dmOpen },
                    )
                }
                if (dmOpen) {
                    items(state.directMessages, key = { it.characterId }) { friend ->
                        FriendRow(friend) { viewModel.openChatWith(friend.characterId, onOpenChat) }
                    }
                }
            }
            if (state.everyoneElse.isNotEmpty()) {
                item(key = "hdr-all") {
                    SectionHeader(
                        label = "Everyone else",
                        count = state.everyoneElse.size,
                        expanded = allOpen,
                        onToggle = { allOpen = !allOpen },
                    )
                }
                if (allOpen) {
                    items(state.everyoneElse, key = { it.characterId }) { friend ->
                        FriendRow(friend) { viewModel.openChatWith(friend.characterId, onOpenChat) }
                    }
                }
            }
            alwaysScrollEndSpacer()
        }
    }
}

/** Collapsible uppercase category header, the way a channel list groups things. */
@Composable
private fun SectionHeader(
    label: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(
                start = InkSpacing.lg,
                end = InkSpacing.lg,
                top = InkSpacing.md,
                bottom = InkSpacing.xs,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (expanded) "⌄" else "›",
            style = MaterialTheme.typography.labelSmall,
            color = inkTokens().secondaryText,
            modifier = Modifier.padding(end = InkSpacing.xs),
        )
        Text(
            "${label.uppercase()} — $count",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = inkTokens().secondaryText,
        )
    }
}

@Composable
private fun FriendRow(friend: FriendUi, onClick: () -> Unit) {
    val tokens = inkTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = InkSpacing.lg, vertical = InkSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm),
    ) {
        // Anyone you have an open conversation with reads as "around".
        CharacterAvatar(
            name = friend.name,
            colorHex = friend.avatarColorHex,
            size = 40.dp,
            present = friend.hasChat,
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
            ) {
                Text(
                    friend.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (friend.isNew) {
                    Text(
                        "NEW",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
            if (friend.subtitle.isNotBlank()) {
                Text(
                    friend.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MonogramAvatar(monogram: String, colorHex: String, size: androidx.compose.ui.unit.Dp = 40.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(parseHexColor(colorHex, MaterialTheme.colorScheme.primary)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            monogram,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

/**
 * Shared with the messenger transcript and the chats list so a character looks
 * the same everywhere. [present] draws the small status dot a contact list uses.
 */
@Composable
fun CharacterAvatar(
    name: String,
    colorHex: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 36.dp,
    present: Boolean = false,
) {
    Box(modifier = modifier) {
        MonogramAvatar(monogramOf(name), colorHex, size)
        if (present) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.32f)
                    .clip(CircleShape)
                    .background(inkTokens().panel)
                    .padding(1.5.dp)
                    .clip(CircleShape)
                    .background(PresenceGreen),
            )
        }
    }
}

private val PresenceGreen = Color(0xFF3BA55D)
