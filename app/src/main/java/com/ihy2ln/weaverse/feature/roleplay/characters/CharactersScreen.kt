package com.ihy2ln.weaverse.feature.roleplay.characters

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.components.InkOutlinedButton
import com.ihy2ln.weaverse.core.ui.components.InkDeleteButton
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.core.ui.theme.InkHover
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens

@Composable
fun CharactersScreen(
    onCharacterClick: (String) -> Unit = {},
    viewModel: CharactersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importCard(uri)
    }

    LaunchedEffect(state.pendingOpenId) {
        val id = state.pendingOpenId ?: return@LaunchedEffect
        viewModel.consumePendingOpen()
        onCharacterClick(id)
    }

    Column(modifier = Modifier.fillMaxSize().padding(InkSpacing.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Characters", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            InkOutlinedButton(
                label = "Import PNG/JSON",
                onClick = { picker.launch(arrayOf("image/png", "application/json", "*/*")) },
            )
        }
        if (state.importStatus.isNotBlank()) {
            Text(
                state.importStatus,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = InkSpacing.sm),
            )
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = InkSpacing.sm)) {
            if (state.groups.isEmpty()) {
                item("empty-cat") {
                    CategoryHeader(
                        name = "Characters",
                        count = 0,
                        expanded = true,
                        onToggle = {},
                        onAdd = { viewModel.addCharacter("Characters") },
                    )
                }
            }
            items(state.groups, key = { it.name }) { group ->
                CategoryHeader(
                    name = group.name,
                    count = group.entries.size,
                    expanded = group.expanded,
                    onToggle = { viewModel.toggleCategory(group.name) },
                    onAdd = { viewModel.addCharacter(group.name) },
                )
                AnimatedVisibility(
                    visible = group.expanded,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Column {
                        group.entries.forEach { character ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onCharacterClick(character.id) }
                                    .background(InkHover.copy(alpha = 0.3f))
                                    .padding(horizontal = InkSpacing.md, vertical = InkSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(character.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        character.description.take(100),
                                        maxLines = 2,
                                        color = inkTokens().secondaryText,
                                        fontSize = 12.sp,
                                    )
                                }
                                InkDeleteButton(
                                    itemName = character.name,
                                    onConfirmedDelete = { viewModel.removeCharacter(character.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryHeader(
    name: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = InkSpacing.md, vertical = InkSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (expanded) "Collapse" else "Expand",
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = "$name · $count",
            modifier = Modifier.weight(1f).padding(start = InkSpacing.xs),
            style = MaterialTheme.typography.labelLarge,
        )
        InkTextButton(label = "+", onClick = onAdd)
    }
}
