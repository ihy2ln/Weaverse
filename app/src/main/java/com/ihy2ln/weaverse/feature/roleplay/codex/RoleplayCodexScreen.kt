package com.ihy2ln.weaverse.feature.roleplay.codex

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.ihy2ln.weaverse.data.db.entity.CodexEntryEntity
import com.ihy2ln.weaverse.data.db.entity.CodexEntryLoreEntity
import com.ihy2ln.weaverse.data.db.entity.RpCharacterEntity

/** Roleplay mode's Codex tab (Revision 02 §2 rename — this used to be named after the classic
 * SillyTavern feature this replicates, per this app's ground rule that there's only one shared
 * Codex entity): World Info entries scoped to one character, over the shared Codex. */
@Composable
fun RoleplayCodexScreen(modifier: Modifier = Modifier, viewModel: RoleplayCodexViewModel = hiltViewModel()) {
    val characters by viewModel.characters.collectAsState()
    val selectedCharacterId by viewModel.selectedCharacterId.collectAsState()
    val entries by viewModel.entries.collectAsState()
    var newDialogOpen by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<CodexEntryEntity?>(null) }

    Column(modifier = modifier.fillMaxSize().padding(Spacing.lg)) {
        Text("Codex", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(Spacing.md))

        if (characters.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.MenuBook,
                title = "No characters yet",
                subtitle = "Create a character first, then give them a Codex.",
                modifier = Modifier.fillMaxSize(),
            )
            return@Column
        }

        val selectedCharacter = characters.firstOrNull { it.id == selectedCharacterId } ?: characters.first()
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            CharacterPicker(characters = characters, current = selectedCharacter, onSelect = viewModel::selectCharacter)
            TextButton(onClick = { newDialogOpen = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Entry", modifier = Modifier.padding(start = Spacing.xs))
            }
        }
        Spacer(modifier = Modifier.height(Spacing.md))

        if (entries.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.MenuBook,
                title = "No codex entries yet",
                subtitle = "Add World Info entries that fire when their keys are mentioned.",
                actionLabel = "New entry",
                onAction = { newDialogOpen = true },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(items = entries, key = { it.id }) { entry ->
                    LoreEntryRow(entry = entry, onClick = { editingEntry = entry }, onDelete = { viewModel.deleteEntry(entry) })
                }
            }
        }
    }

    if (newDialogOpen) {
        NameEntryDialog(title = "New codex entry", onDismiss = { newDialogOpen = false }, onCreate = { name -> viewModel.createEntry(name); newDialogOpen = false })
    }

    editingEntry?.let { entry ->
        LoreEntryEditorSheet(entryId = entry.id, viewModel = viewModel, onDismiss = { editingEntry = null })
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
private fun LoreEntryRow(entry: CodexEntryEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    InkCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.name, style = MaterialTheme.typography.titleMedium)
                if (entry.plainText.isNotBlank()) {
                    Text(
                        entry.plainText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete ${entry.name}")
            }
        }
    }
}

@Composable
private fun LoreEntryEditorSheet(entryId: String, viewModel: RoleplayCodexViewModel, onDismiss: () -> Unit) {
    val entry by remember(entryId) { viewModel.observeEntry(entryId) }.collectAsState(initial = null)
    val current = entry ?: return

    var name by remember(entryId) { mutableStateOf(current.name) }
    var body by remember(entryId) { mutableStateOf(current.plainText) }
    var lore by remember(entryId) { mutableStateOf<CodexEntryLoreEntity?>(null) }
    var keysText by remember(entryId) { mutableStateOf("") }

    LaunchedEffect(entryId) {
        val loaded = viewModel.getLore(entryId) ?: CodexEntryLoreEntity(entryId = entryId)
        lore = loaded
        keysText = loaded.keys.joinToString(", ")
    }

    fun save() {
        viewModel.updateEntry(current.copy(name = name, docJson = "", plainText = body))
    }

    InkModalBottomSheet(onDismiss = { save(); onDismiss() }, title = "Edit codex entry") {
        Column {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(Spacing.sm))
            OutlinedTextField(
                value = keysText,
                onValueChange = { text ->
                    keysText = text
                    lore?.let { current -> viewModel.upsertLore(current.copy(keys = text.split(",").map { it.trim() }.filter { it.isNotEmpty() })) }
                },
                label = { Text("Trigger words (comma-separated)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Body") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(Spacing.md))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Always include", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = current.alwaysInclude, onCheckedChange = { viewModel.updateEntry(current.copy(alwaysInclude = it)) })
            }
            lore?.let { loreEntry ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Constant", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = loreEntry.isConstant, onCheckedChange = { checked -> lore = loreEntry.copy(isConstant = checked); viewModel.upsertLore(loreEntry.copy(isConstant = checked)) })
                }
            }
            Spacer(modifier = Modifier.height(Spacing.lg))
        }
    }
}
