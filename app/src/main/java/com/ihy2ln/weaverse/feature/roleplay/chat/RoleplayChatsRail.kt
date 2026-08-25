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
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.util.alwaysScrollEndSpacer

private const val GeneralGroupKey = ""

/** Roleplay's tool-rail chat list — same "select character -> sub chats" organization as the rail's Novel Chats tab. */
@Composable
fun RoleplayChatsRail(
    selectedChatId: String?,
    onChatClick: (String) -> Unit,
    viewModel: RoleplayChatsViewModel = hiltViewModel(),
) {
    val chats by viewModel.chats.collectAsState()
    val characters by viewModel.characters.collectAsState()
    var selectedGroupKey by rememberSaveable { mutableStateOf<String?>(null) }
    val groups = chats.groupBy { it.characterId ?: GeneralGroupKey }
    val activeGroupKey = selectedGroupKey?.takeIf { groups.containsKey(it) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (activeGroupKey != null) {
            val character = characters.find { it.id == activeGroupKey }
            val groupLabel = character?.name ?: "General"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = InkSpacing.md, vertical = InkSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InkTextButton(label = "←", onClick = { selectedGroupKey = null })
                Text(groupLabel, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                InkTextButton(
                    label = "+",
                    onClick = {
                        val characterId = activeGroupKey.takeIf { it != GeneralGroupKey }
                        viewModel.createChat(characterId, groupLabel, "messenger", onChatClick)
                    },
                )
            }
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(groups[activeGroupKey].orEmpty(), key = { it.id }) { chat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChatClick(chat.id) }
                            .padding(horizontal = InkSpacing.md, vertical = InkSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            chat.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (chat.id == selectedChatId) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.weight(1f),
                        )
                        InkTextButton(label = "−", onClick = { viewModel.deleteChat(chat.id) })
                    }
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
                    onClick = { viewModel.createChat(null, "New chat", "messenger", onChatClick) },
                )
            }
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(groups.keys.toList(), key = { it }) { key ->
                    val character = characters.find { it.id == key }
                    val label = character?.name ?: "General"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedGroupKey = key }
                            .padding(horizontal = InkSpacing.md, vertical = InkSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.titleSmall)
                            val count = groups[key].orEmpty().size
                            Text(
                                "$count chat${if (count == 1) "" else "s"}",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
                alwaysScrollEndSpacer()
            }
        }
    }
}
