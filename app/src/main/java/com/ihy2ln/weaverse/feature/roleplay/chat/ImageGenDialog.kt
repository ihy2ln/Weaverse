package com.ihy2ln.weaverse.feature.roleplay.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens

/**
 * ☁️ Cloud AI picture generation: describe the panel, pick an image-output
 * model (OpenRouter), and the generated picture attaches to the page like
 * any imported media.
 */
@Composable
fun ImageGenDialog(
    state: RoleplayChatUiState,
    onPrompt: (String) -> Unit,
    onModel: (String) -> Unit,
    onGenerate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = inkTokens()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generate picture (AI)") },
        text = {
            Column {
                Text(
                    "Connects to a cloud image model through OpenRouter. " +
                        "Describe the panel — style, subject, mood.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.secondaryText,
                )
                OutlinedTextField(
                    value = state.imageGenPrompt,
                    onValueChange = onPrompt,
                    label = { Text("Prompt") },
                    placeholder = { Text("A rain-slick harbor street at dusk, neon signs…") },
                    minLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = InkSpacing.sm),
                )
                Text(
                    "Model",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = InkSpacing.md),
                )
                if (state.imageGenModels.isEmpty()) {
                    Text(
                        "No image-generation models cached — add an OpenRouter key and " +
                            "Refresh models in Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.secondaryText,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                    ) {
                        items(state.imageGenModels, key = { it.id }) { model ->
                            val selected = state.imageGenModelRef.endsWith(model.id)
                            Text(
                                model.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    tokens.primaryText
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onModel(model.id) }
                                    .padding(vertical = InkSpacing.xs),
                            )
                        }
                    }
                }
                if (state.imageGenStatus.isNotBlank()) {
                    Text(
                        state.imageGenStatus,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (state.imageGenBusy) {
                            tokens.secondaryText
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.padding(top = InkSpacing.sm),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onGenerate,
                enabled = !state.imageGenBusy && state.imageGenPrompt.isNotBlank(),
            ) { Text(if (state.imageGenBusy) "Generating…" else "Generate") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (state.imageGenBusy) "Close" else "Cancel") }
        },
    )
}
