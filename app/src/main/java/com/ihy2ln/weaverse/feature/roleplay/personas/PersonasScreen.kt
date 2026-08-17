package com.ihy2ln.weaverse.feature.roleplay.personas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.components.InkDeleteButton
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.core.ui.theme.InkHover
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens

@Composable
fun PersonasScreen(
    onPersonaClick: (String) -> Unit = {},
    viewModel: PersonasViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.pendingOpenId) {
        val id = state.pendingOpenId ?: return@LaunchedEffect
        viewModel.consumePendingOpen()
        onPersonaClick(id)
    }

    Column(modifier = Modifier.fillMaxSize().padding(InkSpacing.lg)) {
        Text("Personas", style = MaterialTheme.typography.titleLarge)
        Text(
            "Writer identities for roleplay chats",
            color = inkTokens().secondaryText,
            modifier = Modifier.padding(bottom = InkSpacing.sm),
        )
        LazyColumn {
            items(state.groups, key = { it.name }) { group ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.toggleCategory(group.name) }
                        .padding(horizontal = InkSpacing.md, vertical = InkSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (group.expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (group.expanded) "Collapse" else "Expand",
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "${group.name} · ${group.entries.size}",
                        modifier = Modifier.weight(1f).padding(start = InkSpacing.xs),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    InkTextButton(label = "+", onClick = viewModel::addPersona)
                }
                AnimatedVisibility(
                    visible = group.expanded,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Column {
                        group.entries.forEach { persona ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPersonaClick(persona.id) }
                                    .background(InkHover.copy(alpha = 0.3f))
                                    .padding(horizontal = InkSpacing.md, vertical = InkSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        persona.name + if (persona.isDefault) " (default)" else "",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        persona.description,
                                        color = inkTokens().secondaryText,
                                    )
                                }
                                InkDeleteButton(
                                    itemName = persona.name,
                                    onConfirmedDelete = { viewModel.removePersona(persona.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
