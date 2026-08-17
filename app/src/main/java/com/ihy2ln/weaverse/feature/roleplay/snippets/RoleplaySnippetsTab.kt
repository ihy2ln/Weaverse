package com.ihy2ln.weaverse.feature.roleplay.snippets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.EmptyState
import com.ihy2ln.weaverse.core.ui.InkCard
import com.ihy2ln.weaverse.core.ui.InkModalBottomSheet
import com.ihy2ln.weaverse.core.ui.NameEntryDialog
import com.ihy2ln.weaverse.core.ui.Spacing
import com.ihy2ln.weaverse.data.db.entity.RpCharacterEntity
import com.ihy2ln.weaverse.data.db.entity.SnippetEntity

/** The rail's Snippets tab in roleplay mode (Revision 02 §1.4): reusable bits of prose (a
 * character's voice notes, recurring stage directions) scoped to whichever character is picked. */
@Composable
fun RoleplaySnippetsTab(modifier: Modifier = Modifier, viewModel: RoleplaySnippetsViewModel = hiltViewModel()) {
    val characters by viewModel.characters.collectAsState()
    val selectedCharacterId by viewModel.selectedCharacterId.collectAsState()
    val snippets by viewModel.snippets.collectAsState()
    val sortedSnippets = remember(snippets) { snippets.sortedWith(compareByDescending<SnippetEntity> { it.pinned }.thenBy { it.title }) }

    var newDialogOpen by remember { mutableStateOf(false) }
    var editingSnippet by remember { mutableStateOf<SnippetEntity?>(null) }

    Column(modifier = modifier.fillMaxWidth().padding(Spacing.md)) {
        if (characters.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Bookmark,
                title = "No characters yet",
                subtitle = "Create a character first, then give them snippets.",
            )
            return@Column
        }

        val selectedCharacter = characters.firstOrNull { it.id == selectedCharacterId } ?: characters.first()
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            CharacterPicker(characters = characters, current = selectedCharacter, onSelect = viewModel::selectCharacter)
            TextButton(onClick = { newDialogOpen = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("New", modifier = Modifier.padding(start = Spacing.xs))
            }
        }
        Spacer(modifier = Modifier.height(Spacing.sm))

        if (sortedSnippets.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Bookmark,
                title = "No snippets yet",
                subtitle = "Save reusable bits of prose for this character.",
                actionLabel = "New snippet",
                onAction = { newDialogOpen = true },
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(items = sortedSnippets, key = { it.id }) { snippet ->
                    SnippetRow(
                        snippet = snippet,
                        onClick = { editingSnippet = snippet },
                        onTogglePin = { viewModel.togglePinned(snippet) },
                        onDelete = { viewModel.deleteSnippet(snippet) },
                    )
                }
            }
        }
    }

    if (newDialogOpen) {
        NameEntryDialog(title = "New snippet", onDismiss = { newDialogOpen = false }, onCreate = { name -> viewModel.createSnippet(name); newDialogOpen = false })
    }

    editingSnippet?.let { snippet ->
        SnippetEditorSheet(snippet = snippet, onSave = viewModel::updateSnippet, onDismiss = { editingSnippet = null })
    }
}

@Composable
private fun CharacterPicker(characters: List<RpCharacterEntity>, current: RpCharacterEntity, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        TextButton(onClick = { expanded = true }) {
            Text(current.name, style = MaterialTheme.typography.titleMedium)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            characters.forEach { character ->
                DropdownMenuItem(text = { Text(character.name) }, onClick = { onSelect(character.id); expanded = false })
            }
        }
    }
}

@Composable
private fun SnippetRow(snippet: SnippetEntity, onClick: () -> Unit, onTogglePin: () -> Unit, onDelete: () -> Unit) {
    InkCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(snippet.title, style = MaterialTheme.typography.titleSmall)
                if (snippet.body.isNotBlank()) {
                    Text(
                        snippet.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            IconButton(onClick = onTogglePin) {
                Icon(
                    imageVector = if (snippet.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = if (snippet.pinned) "Unpin ${snippet.title}" else "Pin ${snippet.title}",
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete ${snippet.title}")
            }
        }
    }
}

@Composable
private fun SnippetEditorSheet(snippet: SnippetEntity, onSave: (SnippetEntity) -> Unit, onDismiss: () -> Unit) {
    var title by remember(snippet.id) { mutableStateOf(snippet.title) }
    var body by remember(snippet.id) { mutableStateOf(snippet.body) }

    InkModalBottomSheet(
        onDismiss = {
            onSave(snippet.copy(title = title.ifBlank { snippet.title }, body = body))
            onDismiss()
        },
        title = "Edit snippet",
    ) {
        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(Spacing.sm))
        OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Body") }, minLines = 4, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(Spacing.lg))
    }
}
