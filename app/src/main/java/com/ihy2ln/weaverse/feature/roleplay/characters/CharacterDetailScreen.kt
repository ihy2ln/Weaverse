package com.ihy2ln.weaverse.feature.roleplay.characters

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.components.InkConfirmButton
import com.ihy2ln.weaverse.core.ui.components.InkToolbar
import com.ihy2ln.weaverse.core.ui.components.VoiceToTextField
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.util.AlwaysScrollEndPadding
import com.ihy2ln.weaverse.core.ui.util.adaptiveContentPadding

@Composable
fun CharacterDetailScreen(
    characterId: String,
    onBack: () -> Unit,
    viewModel: CharacterDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(characterId) { viewModel.load(characterId) }
    val state by viewModel.uiState.collectAsState()
    val contentPad = adaptiveContentPadding()

    Column(modifier = Modifier.fillMaxSize()) {
        InkToolbar(
            title = state.name.ifBlank { "Character" },
            subtitle = "Character detail",
            canGoBack = true,
            onBack = onBack,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPad),
        ) {
            VoiceToTextField(
                value = state.name,
                onValueChange = viewModel::onName,
                label = "Name",
                singleLine = true,
            )
            VoiceToTextField(
                value = state.description,
                onValueChange = viewModel::onDescription,
                label = "Description",
                minLines = 3,
                modifier = Modifier.padding(top = InkSpacing.md),
            )
            VoiceToTextField(
                value = state.personality,
                onValueChange = viewModel::onPersonality,
                label = "Personality",
                minLines = 3,
                modifier = Modifier.padding(top = InkSpacing.md),
            )
            VoiceToTextField(
                value = state.scenario,
                onValueChange = viewModel::onScenario,
                label = "Scenario",
                minLines = 3,
                modifier = Modifier.padding(top = InkSpacing.md),
            )
            VoiceToTextField(
                value = state.firstMes,
                onValueChange = viewModel::onFirstMes,
                label = "First message",
                minLines = 3,
                modifier = Modifier.padding(top = InkSpacing.md),
            )
            VoiceToTextField(
                value = state.mesExample,
                onValueChange = viewModel::onMesExample,
                label = "Message examples",
                minLines = 3,
                modifier = Modifier.padding(top = InkSpacing.md),
            )
            VoiceToTextField(
                value = state.systemPrompt,
                onValueChange = viewModel::onSystemPrompt,
                label = "System prompt",
                minLines = 2,
                modifier = Modifier.padding(top = InkSpacing.md),
            )
            VoiceToTextField(
                value = state.postHistoryInstructions,
                onValueChange = viewModel::onPostHistory,
                label = "Post-history instructions",
                minLines = 2,
                modifier = Modifier.padding(top = InkSpacing.md),
            )
            VoiceToTextField(
                value = state.creatorNotes,
                onValueChange = viewModel::onCreatorNotes,
                label = "Creator notes",
                minLines = 2,
                modifier = Modifier.padding(top = InkSpacing.md),
            )
            VoiceToTextField(
                value = state.tags,
                onValueChange = viewModel::onTags,
                label = "Tags (comma-separated)",
                singleLine = true,
                modifier = Modifier.padding(top = InkSpacing.md),
            )
            VoiceToTextField(
                value = state.colorHex,
                onValueChange = viewModel::onColorHex,
                label = "Color hex (optional)",
                singleLine = true,
                modifier = Modifier.padding(top = InkSpacing.md),
            )
            if (state.statusMessage.isNotBlank()) {
                Text(
                    state.statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = InkSpacing.sm),
                )
            }
            InkConfirmButton(
                onClick = viewModel::save,
                label = if (state.saved) "Saved" else "Save",
                contentDescription = if (state.saved) "Saved" else "Save character",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = InkSpacing.lg),
            )
            Spacer(modifier = Modifier.height(AlwaysScrollEndPadding))
        }
    }
}
