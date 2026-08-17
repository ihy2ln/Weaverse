package com.ihy2ln.weaverse.feature.novel.prompts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
import com.ihy2ln.weaverse.ai.prompt.PromptAdvancedSettings
import com.ihy2ln.weaverse.ai.prompt.PromptInstructionMessage
import com.ihy2ln.weaverse.ai.prompt.PromptInstructions
import com.ihy2ln.weaverse.ai.prompt.PromptMessageRole
import com.ihy2ln.weaverse.ai.prompt.PromptVariables
import com.ihy2ln.weaverse.ai.prompt.toJson
import com.ihy2ln.weaverse.ai.prompt.toPromptAdvancedSettings
import com.ihy2ln.weaverse.ai.prompt.toPromptInstructions
import com.ihy2ln.weaverse.core.ui.InkModalBottomSheet
import com.ihy2ln.weaverse.core.ui.Spacing
import com.ihy2ln.weaverse.data.db.entity.PromptEntity
import com.ihy2ln.weaverse.data.db.entity.PromptFolderEntity

/** Prompt library UI (spec §8.2) — not yet linked from navigation; accessible from the eventual Prompts entry point (Phase 10's bottom rail strip). */
@Composable
fun PromptLibraryScreen(
    modifier: Modifier = Modifier,
    viewModel: PromptLibraryViewModel = hiltViewModel(),
) {
    val folders by viewModel.folders.collectAsState()
    var editingPromptId by remember { mutableStateOf<String?>(null) }

    LazyColumn(modifier = modifier.fillMaxWidth().padding(Spacing.lg)) {
        items(items = folders, key = { it.id }) { folder ->
            PromptFolderSection(
                folder = folder,
                viewModel = viewModel,
                onPromptClick = { editingPromptId = it.id },
            )
            Spacer(modifier = Modifier.height(Spacing.lg))
        }
    }

    editingPromptId?.let { promptId ->
        PromptEditorSheet(promptId = promptId, viewModel = viewModel, onDismiss = { editingPromptId = null })
    }
}

@Composable
private fun PromptFolderSection(folder: PromptFolderEntity, viewModel: PromptLibraryViewModel, onPromptClick: (PromptEntity) -> Unit) {
    val prompts by remember(folder.id) { viewModel.promptsInFolder(folder.id) }.collectAsState(initial = emptyList())

    Text(folder.name, style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(Spacing.xs))
    prompts.forEach { prompt ->
        Surface(onClick = { onPromptClick(prompt) }, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(prompt.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                if (prompt.isSystem) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = "System prompt",
                        modifier = Modifier.padding(end = Spacing.sm),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { viewModel.duplicatePrompt(prompt) }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate ${prompt.name}")
                }
                if (!prompt.isSystem) {
                    IconButton(onClick = { viewModel.deletePrompt(prompt) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete ${prompt.name}")
                    }
                }
            }
        }
    }
}

@Composable
private fun PromptEditorSheet(promptId: String, viewModel: PromptLibraryViewModel, onDismiss: () -> Unit) {
    val prompt by remember(promptId) { viewModel.observePrompt(promptId) }.collectAsState(initial = null)
    val current = prompt ?: return
    var selectedTab by remember(promptId) { mutableStateOf(0) }

    InkModalBottomSheet(onDismiss = onDismiss, title = current.name) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Instructions") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Advanced") })
        }
        Spacer(modifier = Modifier.height(Spacing.md))
        if (selectedTab == 0) {
            InstructionsTab(prompt = current, viewModel = viewModel)
        } else {
            AdvancedTab(prompt = current, viewModel = viewModel)
        }
    }
}

@Composable
private fun InstructionsTab(prompt: PromptEntity, viewModel: PromptLibraryViewModel) {
    var messages by remember(prompt.id) { mutableStateOf(prompt.instructionsJson.toPromptInstructions().messages) }

    fun save(updated: List<PromptInstructionMessage>) {
        messages = updated
        viewModel.updatePrompt(prompt.copy(instructionsJson = PromptInstructions(updated).toJson()))
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)) {
        Text("Variables", style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.sm)) {
            items(PromptVariables.all) { variable ->
                AssistChip(onClick = {}, label = { Text(variable, style = MaterialTheme.typography.labelSmall) })
            }
        }

        messages.forEachIndexed { index, message ->
            Column(modifier = Modifier.padding(bottom = Spacing.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(message.role.name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { save(messages.toMutableList().also { it.removeAt(index) }) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove message")
                    }
                }
                OutlinedTextField(
                    value = message.template,
                    onValueChange = { text ->
                        save(messages.toMutableList().also { it[index] = message.copy(template = text) })
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
        }

        Row {
            PromptMessageRole.entries.forEach { role ->
                AssistChip(
                    onClick = { save(messages + PromptInstructionMessage(role, "")) },
                    label = { Text("+ ${role.name}") },
                    modifier = Modifier.padding(end = Spacing.xs),
                )
            }
        }
        Spacer(modifier = Modifier.height(Spacing.lg))
    }
}

@Composable
private fun AdvancedTab(prompt: PromptEntity, viewModel: PromptLibraryViewModel) {
    var settings by remember(prompt.id) { mutableStateOf(prompt.advancedJson.toPromptAdvancedSettings()) }

    fun save(updated: PromptAdvancedSettings) {
        settings = updated
        viewModel.updatePrompt(prompt.copy(advancedJson = updated.toJson()))
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)) {
        OutlinedTextField(
            value = settings.temperature?.toString().orEmpty(),
            onValueChange = { save(settings.copy(temperature = it.toFloatOrNull())) },
            label = { Text("Temperature") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = settings.topP?.toString().orEmpty(),
            onValueChange = { save(settings.copy(topP = it.toFloatOrNull())) },
            label = { Text("Top P") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        )
        OutlinedTextField(
            value = settings.maxTokens?.toString().orEmpty(),
            onValueChange = { save(settings.copy(maxTokens = it.toIntOrNull())) },
            label = { Text("Max tokens") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        )
        Spacer(modifier = Modifier.height(Spacing.lg))
    }
}
