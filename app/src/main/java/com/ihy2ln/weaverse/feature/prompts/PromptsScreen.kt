package com.ihy2ln.weaverse.feature.prompts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.components.InkConfirmButton
import com.ihy2ln.weaverse.core.ui.components.InkSegmentedPill
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.core.ui.components.SegmentedOption
import com.ihy2ln.weaverse.core.ui.components.VoiceToTextField
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens

@Composable
fun PromptsScreen(
    modifier: Modifier = Modifier,
    viewModel: PromptsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val tokens = inkTokens()
    val isNarrow = LocalConfiguration.current.screenWidthDp < 600
    var showEditor by rememberSaveable { mutableStateOf(false) }

    fun openPrompt(id: String) {
        viewModel.selectPrompt(id)
        showEditor = true
    }

    if (isNarrow) {
        Column(modifier = modifier.fillMaxSize().background(tokens.background)) {
            if (showEditor && state.selectedId != null) {
                PromptEditorPane(
                    state = state,
                    viewModel = viewModel,
                    onBackToList = { showEditor = false },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                PromptLibraryPane(
                    state = state,
                    viewModel = viewModel,
                    onOpenPrompt = ::openPrompt,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        return
    }

    Row(modifier = modifier.fillMaxSize().background(tokens.background)) {
        PromptLibraryPane(
            state = state,
            viewModel = viewModel,
            onOpenPrompt = ::openPrompt,
            modifier = Modifier
                .width(260.dp)
                .fillMaxHeight(),
        )
        PromptEditorPane(
            state = state,
            viewModel = viewModel,
            onBackToList = null,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
    }
}

@Composable
private fun PromptLibraryPane(
    state: PromptsUiState,
    viewModel: PromptsViewModel,
    onOpenPrompt: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = inkTokens()
    Column(
        modifier = modifier
            .padding(InkSpacing.md)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Prompt Collection", style = MaterialTheme.typography.titleMedium)
        Text(
            "Active in Novel, Roleplay, and Notes — same library everywhere.",
            style = MaterialTheme.typography.labelSmall,
            color = tokens.secondaryText,
            modifier = Modifier.padding(top = InkSpacing.xxs, bottom = InkSpacing.sm),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.newFolderName,
                onValueChange = viewModel::onNewFolderName,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Category") },
            )
            InkConfirmButton(
                onClick = viewModel::createFolder,
                contentDescription = "Create category",
                modifier = Modifier.padding(start = InkSpacing.xs),
            )
        }
        state.folders.forEach { group ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.toggleFolder(group.folder.id) }
                    .padding(vertical = InkSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (group.expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                )
                Text(
                    "${group.folder.name} (${group.prompts.size})",
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
                InkTextButton(label = "+", onClick = { viewModel.createPrompt(group.folder.id) })
            }
            if (group.expanded) {
                group.prompts.forEach { prompt ->
                    Text(
                        prompt.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenPrompt(prompt.id) }
                            .padding(start = InkSpacing.lg, top = InkSpacing.xs, bottom = InkSpacing.xs),
                        color = if (prompt.id == state.selectedId) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            tokens.primaryText
                        },
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun PromptEditorPane(
    state: PromptsUiState,
    viewModel: PromptsViewModel,
    onBackToList: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val tokens = inkTokens()
    Column(
        modifier = modifier
            .padding(InkSpacing.md)
            .verticalScroll(rememberScrollState()),
    ) {
        if (state.selectedId == null) {
            Text("Select a prompt on the left. Instructions should be full guiding prose.", color = tokens.secondaryText)
            return
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBackToList != null) {
                IconButton(onClick = onBackToList) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Prompt list")
                }
            }
            Text(
                state.name.ifBlank { "Prompt" },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            InkConfirmButton(
                onClick = viewModel::save,
                label = "Save",
                contentDescription = "Save prompt",
            )
            InkTextButton(label = "Delete", onClick = viewModel::deleteSelected)
        }
        InkSegmentedPill(
            options = PromptEditorTab.entries.map { SegmentedOption(it.name, it.name) },
            selectedId = state.editorTab.name,
            onSelect = { viewModel.setEditorTab(PromptEditorTab.valueOf(it)) },
            modifier = Modifier.padding(vertical = InkSpacing.md),
        )
        when (state.editorTab) {
            PromptEditorTab.General -> {
                VoiceToTextField(
                    value = state.name,
                    onValueChange = viewModel::onName,
                    label = "Name",
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.type,
                    onValueChange = viewModel::onType,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = InkSpacing.sm),
                    label = { Text("Type (scene_beat / summarize / replace / workshop_chat)") },
                    singleLine = true,
                )
            }
            PromptEditorTab.Instructions -> {
                VoiceToTextField(
                    value = state.instructionsText,
                    onValueChange = viewModel::onInstructions,
                    label = "System message — guiding prose for the model",
                    minLines = 14,
                )
            }
            PromptEditorTab.Advanced -> {
                VoiceToTextField(
                    value = state.bias,
                    onValueChange = viewModel::onBias,
                    label = "Bias",
                    singleLine = true,
                )
                VoiceToTextField(
                    value = state.guidance,
                    onValueChange = viewModel::onGuidance,
                    label = "In-depth guidance",
                    minLines = 8,
                    modifier = Modifier.padding(top = InkSpacing.sm),
                )
            }
            PromptEditorTab.Description -> {
                VoiceToTextField(
                    value = state.description,
                    onValueChange = viewModel::onDescription,
                    label = "Description",
                    minLines = 5,
                )
            }
        }
    }
}
