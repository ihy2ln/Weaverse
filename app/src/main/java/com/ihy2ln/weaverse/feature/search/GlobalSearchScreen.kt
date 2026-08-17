package com.ihy2ln.weaverse.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.components.InkCard
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing

@Composable
fun GlobalSearchScreen(
    modifier: Modifier = Modifier,
    onResultClick: (SearchResult) -> Unit = {},
    viewModel: GlobalSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    Column(modifier = modifier.fillMaxSize().padding(InkSpacing.lg)) {
        Text("Search", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(vertical = InkSpacing.md),
            placeholder = { Text("Scenes, codex, snippets, chats…") },
        )
        LazyColumn {
            items(state.results, key = { it.id }) { result ->
                InkCard(
                    modifier = Modifier
                        .padding(vertical = InkSpacing.xs)
                        .clickable { onResultClick(result) },
                ) {
                    Text(result.type.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(result.title, style = MaterialTheme.typography.titleSmall)
                    Text(result.snippet, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
