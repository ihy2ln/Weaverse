package com.ihy2ln.weaverse.feature.roleplay.characters

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Groups
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.media.rememberMediaPickerActions
import com.ihy2ln.weaverse.core.media.ui.AvatarImage
import com.ihy2ln.weaverse.core.ui.EmptyState
import com.ihy2ln.weaverse.core.ui.InkCard
import com.ihy2ln.weaverse.core.ui.InkModalBottomSheet
import com.ihy2ln.weaverse.core.ui.NameEntryDialog
import com.ihy2ln.weaverse.core.ui.Spacing
import com.ihy2ln.weaverse.data.db.entity.RpCharacterEntity
import com.ihy2ln.weaverse.data.repo.MediaRepository
import kotlinx.coroutines.launch

/** Characters screen (spec §9/§11): character cards, including PNG card import/export. */
@Composable
fun CharactersScreen(modifier: Modifier = Modifier, viewModel: CharactersViewModel = hiltViewModel()) {
    val characters by viewModel.characters.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var newDialogOpen by remember { mutableStateOf(false) }
    var editingCharacter by remember { mutableStateOf<RpCharacterEntity?>(null) }
    var exportTarget by remember { mutableStateOf<RpCharacterEntity?>(null) }

    val importCardLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { picked -> scope.launch { viewModel.importCard(picked) } }
    }
    val exportCardLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val character = exportTarget
        exportTarget = null
        if (uri != null && character != null) {
            scope.launch {
                val bytes = viewModel.exportCard(character)
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(Spacing.lg)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Characters", style = MaterialTheme.typography.headlineSmall)
            Row {
                TextButton(onClick = { importCardLauncher.launch("image/png") }) {
                    Icon(Icons.Filled.FileUpload, contentDescription = null)
                    Text("Import", modifier = Modifier.padding(start = Spacing.xs))
                }
                TextButton(onClick = { newDialogOpen = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Character", modifier = Modifier.padding(start = Spacing.xs))
                }
            }
        }

        if (characters.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Groups,
                title = "No characters yet",
                subtitle = "Create one, or import a PNG character card.",
                actionLabel = "New character",
                onAction = { newDialogOpen = true },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(top = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(items = characters, key = { it.id }) { character ->
                    CharacterRow(
                        character = character,
                        mediaRepository = viewModel.mediaRepository,
                        onClick = { editingCharacter = character },
                        onExport = { exportTarget = character; exportCardLauncher.launch("${character.name.ifBlank { "character" }}.png") },
                        onDelete = { viewModel.delete(character) },
                    )
                }
            }
        }
    }

    if (newDialogOpen) {
        NameEntryDialog(title = "New character", onDismiss = { newDialogOpen = false }, onCreate = { name -> viewModel.createCharacter(name); newDialogOpen = false })
    }

    editingCharacter?.let { character ->
        CharacterEditorSheet(character = character, viewModel = viewModel, onDismiss = { editingCharacter = null })
    }
}

@Composable
private fun CharacterRow(
    character: RpCharacterEntity,
    mediaRepository: MediaRepository,
    onClick: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    InkCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AvatarImage(
                mediaId = character.avatarMediaId,
                mediaRepository = mediaRepository,
                modifier = Modifier.size(40.dp),
                placeholder = { Icon(Icons.Filled.Groups, contentDescription = null) },
            )
            Column(modifier = Modifier.padding(start = Spacing.sm).weight(1f)) {
                Text(character.name, style = MaterialTheme.typography.titleMedium)
                if (character.description.isNotBlank()) {
                    Text(
                        character.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            IconButton(onClick = onExport) {
                Icon(Icons.Filled.FileUpload, contentDescription = "Export ${character.name} as a card")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete ${character.name}")
            }
        }
    }
}

@Composable
private fun CharacterEditorSheet(character: RpCharacterEntity, viewModel: CharactersViewModel, onDismiss: () -> Unit) {
    var name by remember(character.id) { mutableStateOf(character.name) }
    var description by remember(character.id) { mutableStateOf(character.description) }
    var personality by remember(character.id) { mutableStateOf(character.personality) }
    var scenario by remember(character.id) { mutableStateOf(character.scenario) }
    var firstMes by remember(character.id) { mutableStateOf(character.firstMes) }
    var mesExample by remember(character.id) { mutableStateOf(character.mesExample) }
    val scope = rememberCoroutineScope()
    val mediaPickerActions = rememberMediaPickerActions(onPicked = { uris ->
        scope.launch {
            uris.firstOrNull()?.let { uri ->
                val media = viewModel.importAvatar(uri)
                viewModel.setAvatar(character, media.id)
            }
        }
    })

    fun save() {
        viewModel.update(
            character.copy(
                name = name.ifBlank { character.name },
                description = description,
                personality = personality,
                scenario = scenario,
                firstMes = firstMes,
                mesExample = mesExample,
            ),
        )
    }

    InkModalBottomSheet(onDismiss = { save(); onDismiss() }, title = "Edit character") {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarImage(
                    mediaId = character.avatarMediaId,
                    mediaRepository = viewModel.mediaRepository,
                    modifier = Modifier.size(64.dp),
                    onClick = mediaPickerActions.pickImage,
                    placeholder = { Icon(Icons.Filled.Groups, contentDescription = null) },
                )
                TextButton(onClick = mediaPickerActions.pickImage, modifier = Modifier.padding(start = Spacing.sm)) {
                    Text("Change avatar")
                }
            }
            Spacer(modifier = Modifier.size(Spacing.md))
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.size(Spacing.sm))
            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.size(Spacing.sm))
            OutlinedTextField(value = personality, onValueChange = { personality = it }, label = { Text("Personality") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.size(Spacing.sm))
            OutlinedTextField(value = scenario, onValueChange = { scenario = it }, label = { Text("Scenario") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.size(Spacing.sm))
            OutlinedTextField(value = firstMes, onValueChange = { firstMes = it }, label = { Text("First message") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.size(Spacing.sm))
            OutlinedTextField(value = mesExample, onValueChange = { mesExample = it }, label = { Text("Example messages") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.size(Spacing.lg))
        }
    }
}
