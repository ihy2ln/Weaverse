package com.ihy2ln.weaverse.feature.novel.chat

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
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.util.alwaysScrollEndSpacer

@Composable
fun WorkshopThreadsRail(
    selectedThreadId: String?,
    onThreadClick: (String) -> Unit,
    viewModel: WorkshopThreadsViewModel = hiltViewModel(),
) {
    val threads by viewModel.threads.collectAsState()
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(threads, key = { it.id }) { thread ->
            Column(
                modifier = Modifier
                    .clickable { onThreadClick(thread.id) }
                    .padding(horizontal = InkSpacing.md, vertical = InkSpacing.sm),
            ) {
                Text(
                    thread.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (thread.id == selectedThreadId) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                if (thread.pinned) {
                    Text("Pinned", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        alwaysScrollEndSpacer()
    }
}
