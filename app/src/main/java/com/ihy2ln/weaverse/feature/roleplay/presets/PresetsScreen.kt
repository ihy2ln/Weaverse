package com.ihy2ln.weaverse.feature.roleplay.presets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Tune
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
import com.ihy2ln.weaverse.ai.AIRequestParams
import com.ihy2ln.weaverse.core.ui.EmptyState
import com.ihy2ln.weaverse.core.ui.InkCard
import com.ihy2ln.weaverse.core.ui.InkModalBottomSheet
import com.ihy2ln.weaverse.core.ui.NameEntryDialog
import com.ihy2ln.weaverse.core.ui.Spacing
import com.ihy2ln.weaverse.data.db.entity.PresetEntity

/** Presets screen (spec §9/§11): sampler presets, shared between Novel Prompts and Roleplay chats. */
@Composable
fun PresetsScreen(modifier: Modifier = Modifier, viewModel: PresetsViewModel = hiltViewModel()) {
    val presets by viewModel.presets.collectAsState()
    var newDialogOpen by remember { mutableStateOf(false) }
    var editingPreset by remember { mutableStateOf<PresetEntity?>(null) }

    Column(modifier = modifier.fillMaxSize().padding(Spacing.lg)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Presets", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = { newDialogOpen = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Preset", modifier = Modifier.padding(start = Spacing.xs))
            }
        }

        if (presets.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Tune,
                title = "No presets yet",
                subtitle = "Create a sampler preset to reuse across connections and chats.",
                actionLabel = "New preset",
                onAction = { newDialogOpen = true },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(top = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(items = presets, key = { it.id }) { preset ->
                    PresetRow(
                        preset = preset,
                        onClick = { editingPreset = preset },
                        onDelete = { viewModel.delete(preset) },
                    )
                }
            }
        }
    }

    if (newDialogOpen) {
        NameEntryDialog(title = "New preset", onDismiss = { newDialogOpen = false }, onCreate = { name -> viewModel.createPreset(name); newDialogOpen = false })
    }

    editingPreset?.let { preset ->
        PresetEditorSheet(
            preset = preset,
            initialParams = viewModel.paramsFor(preset),
            onDismiss = { editingPreset = null },
            onSave = { params -> viewModel.updateParams(preset, params) },
        )
    }
}

@Composable
private fun PresetRow(preset: PresetEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    InkCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(preset.name, style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete ${preset.name}")
            }
        }
    }
}

@Composable
private fun PresetEditorSheet(
    preset: PresetEntity,
    initialParams: AIRequestParams,
    onDismiss: () -> Unit,
    onSave: (AIRequestParams) -> Unit,
) {
    var temperature by remember(preset.id) { mutableStateOf(initialParams.temperature?.toString().orEmpty()) }
    var topP by remember(preset.id) { mutableStateOf(initialParams.topP?.toString().orEmpty()) }
    var topK by remember(preset.id) { mutableStateOf(initialParams.topK?.toString().orEmpty()) }
    var maxTokens by remember(preset.id) { mutableStateOf(initialParams.maxTokens.toString()) }
    var stopSequences by remember(preset.id) { mutableStateOf(initialParams.stopSequences.joinToString(", ")) }

    fun save() {
        onSave(
            AIRequestParams(
                temperature = temperature.toFloatOrNull(),
                topP = topP.toFloatOrNull(),
                topK = topK.toIntOrNull(),
                maxTokens = maxTokens.toIntOrNull() ?: 1024,
                stopSequences = stopSequences.split(",").map { it.trim() }.filter { it.isNotEmpty() },
            ),
        )
    }

    InkModalBottomSheet(onDismiss = { save(); onDismiss() }, title = preset.name) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedTextField(value = temperature, onValueChange = { temperature = it }, label = { Text("Temperature") }, singleLine = true)
            OutlinedTextField(value = topP, onValueChange = { topP = it }, label = { Text("Top P") }, singleLine = true)
            OutlinedTextField(value = topK, onValueChange = { topK = it }, label = { Text("Top K") }, singleLine = true)
            OutlinedTextField(value = maxTokens, onValueChange = { maxTokens = it }, label = { Text("Max tokens") }, singleLine = true)
            OutlinedTextField(
                value = stopSequences,
                onValueChange = { stopSequences = it },
                label = { Text("Stop sequences (comma-separated)") },
                singleLine = true,
            )
        }
    }
}
