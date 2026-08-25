package com.ihy2ln.weaverse.feature.roleplay.chat

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

/** Sentinel group key for chats with no characterId — mirrors how the Chats hub buckets mini chats. */
private const val GeneralGroupKey = ""

/**
 * Roleplay chats, grouped by character (select a character -> its sub chats), the same
 * "main item -> sub chats" organization the standalone Chats hub uses for books.
 */
@Composable
fun RoleplayChatsScreen(
    displayMode: String,
    onChatClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: RoleplayChatsViewModel = hiltViewModel(),
) {
    val chats by viewModel.chats.collectAsState()
    val characters by viewModel.characters.collectAsState()
    val filtered = chats.filter { it.displayMode.ifBlank { "messenger" } == displayMode }
    var selectedGroupKey by rememberSaveable(displayMode) { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(InkSpacing.lg)) {
        val groups = filtered.groupBy { it.characterId ?: GeneralGroupKey }
        val activeGroupKey = selectedGroupKey?.takeIf { groups.containsKey(it) }

        if (activeGroupKey != null) {
            val character = characters.find { it.id == activeGroupKey }
            val groupLabel = character?.name ?: "General"
            Row(verticalAlignment = Alignment.CenterVertically) {
                InkTextButton(label = "← Characters", onClick = { selectedGroupKey = null })
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = InkSpacing.sm, bottom = InkSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(groupLabel, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                InkTextButton(
                    label = "+",
                    onClick = {
                        val characterId = activeGroupKey.takeIf { it != GeneralGroupKey }
                        viewModel.createChat(characterId, groupLabel, displayMode, onChatClick)
                    },
                )
            }
            LazyColumn {
                items(groups[activeGroupKey].orEmpty(), key = { it.id }) { chat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChatClick(chat.id) }
                            .padding(vertical = InkSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(chat.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        InkTextButton(label = "−", onClick = { viewModel.deleteChat(chat.id) })
                    }
                }
                alwaysScrollEndSpacer()
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InkTextButton(label = "← Modes", onClick = onBack)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = InkSpacing.sm, bottom = InkSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${roleplayModeLabel(displayMode)} chats",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                InkTextButton(
                    label = "+",
                    onClick = { viewModel.createChat(null, "New chat", displayMode, onChatClick) },
                )
            }
            if (groups.isEmpty()) {
                Text(
                    "No chats in this mode yet. Tap + to start one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = inkTokens().secondaryText,
                )
            }
            LazyColumn {
                items(groups.keys.toList(), key = { it }) { key ->
                    val character = characters.find { it.id == key }
                    val label = character?.name ?: "General"
                    InkCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = InkSpacing.sm)
                            .clickable { selectedGroupKey = key },
                    ) {
                        Text(label, style = MaterialTheme.typography.titleMedium)
                        val count = groups[key].orEmpty().size
                        Text(
                            "$count chat${if (count == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = inkTokens().secondaryText,
                        )
                    }
                }
                alwaysScrollEndSpacer()
            }
        }
    }
}
