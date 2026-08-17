package com.ihy2ln.weaverse.feature.novel.snippets

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

@Composable
fun SnippetsRailScreen(viewModel: SnippetsViewModel = hiltViewModel()) {
    val snippets by viewModel.snippets.collectAsState()
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(snippets, key = { it.id }) { snippet ->
            Column(
                modifier = Modifier
                    .clickable { }
                    .padding(horizontal = InkSpacing.md, vertical = InkSpacing.sm),
            ) {
                Text(snippet.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    snippet.body,
                    maxLines = 2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
