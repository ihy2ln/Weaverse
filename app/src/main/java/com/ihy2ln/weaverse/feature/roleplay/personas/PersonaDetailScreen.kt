package com.ihy2ln.weaverse.feature.roleplay.personas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.components.InkConfirmButton
import com.ihy2ln.weaverse.core.ui.components.InkToolbar
import com.ihy2ln.weaverse.core.ui.components.VoiceToTextField
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.util.AlwaysScrollEndPadding
import com.ihy2ln.weaverse.core.ui.util.adaptiveContentPadding

@Composable
fun PersonaDetailScreen(
    personaId: String,
    onBack: () -> Unit,
    viewModel: PersonaDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(personaId) { viewModel.load(personaId) }
    val state by viewModel.uiState.collectAsState()
    val contentPad = adaptiveContentPadding()

    Column(modifier = Modifier.fillMaxSize()) {
        InkToolbar(
            title = state.name.ifBlank { "Persona" },
            subtitle = "Persona detail",
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
                minLines = 4,
                modifier = Modifier.padding(top = InkSpacing.md),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = InkSpacing.md)
                    .clickable { viewModel.onDefault(!state.isDefault) },
            ) {
                Checkbox(
                    checked = state.isDefault,
                    onCheckedChange = viewModel::onDefault,
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
                Text(
                    "Default persona",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
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
                contentDescription = if (state.saved) "Saved" else "Save persona",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = InkSpacing.lg),
            )
            Spacer(modifier = Modifier.height(AlwaysScrollEndPadding))
        }
    }
}
