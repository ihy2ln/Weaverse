package com.ihy2ln.weaverse.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.EmptyState
import com.ihy2ln.weaverse.core.ui.InkCard
import com.ihy2ln.weaverse.core.ui.InkChip
import com.ihy2ln.weaverse.core.ui.Spacing

/** Full-screen global search overlay (spec §4/§9), shown from either mode's top bar. */
@Composable
fun GlobalSearchScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GlobalSearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(Spacing.lg)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Close search")
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.weight(1f).padding(start = Spacing.sm),
                    placeholder = { Text("Search everything…") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                )
            }
            Spacer(modifier = Modifier.height(Spacing.md))

            when {
                query.isBlank() -> EmptyState(
                    icon = Icons.Filled.Search,
                    title = "Search your whole library",
                    subtitle = "Scenes, codex entries, chats, and snippets — all at once.",
                    modifier = Modifier.fillMaxSize(),
                )
                results.isEmpty() && !isSearching -> EmptyState(
                    icon = Icons.Filled.Search,
                    title = "No results",
                    subtitle = "Try a different search term.",
                    modifier = Modifier.fillMaxSize(),
                )
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    items(items = results, key = { it.category.name + it.id }) { row -> SearchResultCard(row) }
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(row: SearchResultRow) {
    InkCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            InkChip(label = row.category.name, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(row.title, style = MaterialTheme.typography.titleSmall)
            if (row.snippet.isNotBlank()) {
                Text(
                    row.snippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}
