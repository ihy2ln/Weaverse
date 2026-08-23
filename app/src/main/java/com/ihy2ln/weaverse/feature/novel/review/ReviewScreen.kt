package com.ihy2ln.weaverse.feature.novel.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.components.InkCard
import com.ihy2ln.weaverse.core.ui.components.InkFilledButton
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing

@Composable
fun ReviewScreen(
    sceneId: String,
    chapterScope: Boolean,
    onClose: () -> Unit,
    viewModel: ReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(sceneId, chapterScope) {
        viewModel.start(sceneId, chapterScope)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(InkSpacing.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Review", style = MaterialTheme.typography.titleLarge)
                Text(
                    state.title.ifBlank { if (chapterScope) "Chapter" else "Scene" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            InkTextButton(label = "Close", onClick = onClose, compact = true)
        }
        if (state.contextMeter.isNotBlank()) {
            Text(
                "Context · ${state.contextMeter}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = InkSpacing.xs),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = InkSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm),
        ) {
            InkFilledButton(
                label = if (state.isRunning) "Reviewing…" else "Run review",
                onClick = viewModel::runReview,
                enabled = !state.isRunning,
            )
        }
        if (state.errorMessage.isNotBlank()) {
            Text(
                state.errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = InkSpacing.sm),
            )
        }
        InkCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                if (state.isRunning && state.notes.isBlank()) {
                    Text(
                        "Generating editorial notes…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (state.notes.isBlank()) {
                    Text(
                        "No notes yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(state.notes, style = MaterialTheme.typography.bodyMedium)
                }
                if (state.usageLog.isNotBlank()) {
                    Text(
                        state.usageLog,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = InkSpacing.md),
                    )
                }
            }
        }
    }
}
