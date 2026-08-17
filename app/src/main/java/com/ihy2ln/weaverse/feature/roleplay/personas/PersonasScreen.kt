package com.ihy2ln.weaverse.feature.roleplay.personas

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.media.rememberMediaPickerActions
import com.ihy2ln.weaverse.core.media.ui.AvatarImage
import com.ihy2ln.weaverse.core.ui.EmptyState
import com.ihy2ln.weaverse.core.ui.InkCard
import com.ihy2ln.weaverse.core.ui.InkModalBottomSheet
import com.ihy2ln.weaverse.core.ui.NameEntryDialog
import com.ihy2ln.weaverse.core.ui.Spacing
import com.ihy2ln.weaverse.data.db.entity.RpPersonaEntity
import com.ihy2ln.weaverse.data.repo.MediaRepository
import kotlinx.coroutines.launch

/** Personas screen (spec §9/§11): the user's own roleplay identities. */
@Composable
fun PersonasScreen(modifier: Modifier = Modifier, viewModel: PersonasViewModel = hiltViewModel()) {
    val personas by viewModel.personas.collectAsState()
    var newDialogOpen by remember { mutableStateOf(false) }
    var editingPersona by remember { mutableStateOf<RpPersonaEntity?>(null) }

    Column(modifier = modifier.fillMaxSize().padding(Spacing.lg)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Personas", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = { newDialogOpen = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Persona", modifier = Modifier.padding(start = Spacing.xs))
            }
        }

        if (personas.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Person,
                title = "No personas yet",
                subtitle = "Create a persona to play as in roleplay chats.",
                actionLabel = "New persona",
                onAction = { newDialogOpen = true },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(top = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(items = personas, key = { it.id }) { persona ->
                    PersonaRow(
                        persona = persona,
                        mediaRepository = viewModel.mediaRepository,
                        onClick = { editingPersona = persona },
                        onSetDefault = { viewModel.setDefault(persona) },
                        onDelete = { viewModel.delete(persona) },
                    )
                }
            }
        }
    }

    if (newDialogOpen) {
        NameEntryDialog(title = "New persona", onDismiss = { newDialogOpen = false }, onCreate = { name -> viewModel.createPersona(name); newDialogOpen = false })
    }

    editingPersona?.let { persona ->
        PersonaEditorSheet(
            persona = persona,
            viewModel = viewModel,
            onDismiss = { editingPersona = null },
        )
    }
}

@Composable
private fun PersonaRow(
    persona: RpPersonaEntity,
    mediaRepository: MediaRepository,
    onClick: () -> Unit,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit,
) {
    InkCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AvatarImage(
                mediaId = persona.avatarMediaId,
                mediaRepository = mediaRepository,
                modifier = Modifier.size(40.dp),
                placeholder = { Icon(Icons.Filled.Person, contentDescription = null) },
            )
            Column(modifier = Modifier.padding(start = Spacing.sm).weight(1f)) {
                Text(persona.name, style = MaterialTheme.typography.titleMedium)
                if (persona.description.isNotBlank()) {
                    Text(
                        persona.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            IconButton(onClick = onSetDefault) {
                Icon(
                    imageVector = if (persona.isDefault) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (persona.isDefault) "Default persona" else "Set as default",
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete ${persona.name}")
            }
        }
    }
}

@Composable
private fun PersonaEditorSheet(persona: RpPersonaEntity, viewModel: PersonasViewModel, onDismiss: () -> Unit) {
    var description by remember(persona.id) { mutableStateOf(persona.description) }
    val scope = rememberCoroutineScope()
    val mediaPickerActions = rememberMediaPickerActions(onPicked = { uris ->
        scope.launch {
            uris.firstOrNull()?.let { uri ->
                val media = viewModel.importAvatar(uri)
                viewModel.setAvatar(persona, media.id)
            }
        }
    })

    InkModalBottomSheet(onDismiss = { viewModel.updateDescription(persona, description); onDismiss() }, title = persona.name) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarImage(
                mediaId = persona.avatarMediaId,
                mediaRepository = viewModel.mediaRepository,
                modifier = Modifier.size(64.dp),
                onClick = mediaPickerActions.pickImage,
                placeholder = { Icon(Icons.Filled.Person, contentDescription = null) },
            )
            TextButton(onClick = mediaPickerActions.pickImage, modifier = Modifier.padding(start = Spacing.sm)) {
                Text("Change avatar")
            }
        }
        Spacer(modifier = Modifier.size(Spacing.md))
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
