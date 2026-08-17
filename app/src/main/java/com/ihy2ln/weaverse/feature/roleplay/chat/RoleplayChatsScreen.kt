package com.ihy2ln.weaverse.feature.roleplay.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.components.InkCard
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.util.alwaysScrollEndSpacer

@Composable
fun RoleplayChatsScreen(
    onChatClick: (String) -> Unit,
    viewModel: RoleplayChatsViewModel = hiltViewModel(),
) {
    val chats by viewModel.chats.collectAsState()
    Column(modifier = Modifier.fillMaxSize().padding(InkSpacing.lg)) {
        Text("Roleplay Chats", style = MaterialTheme.typography.titleLarge)
        LazyColumn {
            items(chats, key = { it.id }) { chat ->
                InkCard(
                    modifier = Modifier
                        .padding(vertical = InkSpacing.sm)
                        .clickable { onChatClick(chat.id) },
                ) {
                    Text(chat.title, style = MaterialTheme.typography.titleMedium)
                }
            }
            alwaysScrollEndSpacer()
        }
    }
}
