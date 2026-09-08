package com.ihy2ln.weaverse.feature.roleplay.campaign

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ihy2ln.weaverse.core.ui.components.InkOutlinedButton
import com.ihy2ln.weaverse.core.ui.components.InkTextButton

@Composable
fun RpgAdventureMapScreen(
    state: RpgCampaignState,
    onSelectNode: (RpgSceneNode) -> Unit,
    onExploreFreely: () -> Unit,
    onReturnToChapter: () -> Unit,
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Adventure map", style = MaterialTheme.typography.headlineSmall)
        Text("Chapter ${state.chapter} · ${state.map.nodes.firstOrNull { it.id == state.map.currentNodeId }?.title ?: "Unknown location"}")
        if (state.exploration.active) {
            Text("Freeform exploration · ${state.exploration.location}", color = MaterialTheme.colorScheme.primary)
            InkOutlinedButton("Return to chapter", onReturnToChapter, Modifier.fillMaxWidth())
        } else {
            InkOutlinedButton("Explore freely", onExploreFreely, Modifier.fillMaxWidth())
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.map.nodes) { node ->
                val completed = node.id in state.map.completedNodeIds
                val available = node in availableRpgSceneNodes(state)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(if (completed) "✓ ${node.title}" else node.title, style = MaterialTheme.typography.titleMedium)
                        Text(node.summary, style = MaterialTheme.typography.bodySmall)
                    }
                    InkTextButton(label = if (available) "Enter" else if (completed) "Done" else "Locked", onClick = { if (available) onSelectNode(node) }, enabled = available)
                }
            }
        }
    }
}
