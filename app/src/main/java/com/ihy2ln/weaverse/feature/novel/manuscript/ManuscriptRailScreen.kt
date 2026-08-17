package com.ihy2ln.weaverse.feature.novel.manuscript

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
fun ManuscriptRailScreen(
    onSceneClick: (String) -> Unit,
    viewModel: ManuscriptRailViewModel = hiltViewModel(),
) {
    val scenes by viewModel.scenes.collectAsState()
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text("Act I · Chapter 1", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(InkSpacing.md))
        }
        items(scenes, key = { it.id }) { scene ->
            Column(
                modifier = Modifier
                    .clickable { onSceneClick(scene.id) }
                    .padding(horizontal = InkSpacing.md, vertical = InkSpacing.sm),
            ) {
                Text(scene.title, style = MaterialTheme.typography.titleSmall)
                Text("${scene.wordCount} words · ${scene.status}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        alwaysScrollEndSpacer()
    }
}
