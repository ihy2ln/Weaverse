package com.ihy2ln.weaverse.feature.roleplay.lorebook

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.util.alwaysScrollEndSpacer
import com.ihy2ln.weaverse.feature.novel.codex.CodexEntryPlate

/**
 * What this adventure knows so far — its own characters, places and lore only.
 * A new adventure starts empty; the full library lives under Extra → Codex.
 */
@Composable
fun LorebookScreen(
    onEntryClick: (String) -> Unit = {},
    viewModel: AdventureLoreViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val tokens = inkTokens()

    if (!state.loading && state.entries.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(InkSpacing.xl),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "This adventure has no lore yet.\n\n" +
                    "Entries you create while playing show up here. " +
                    "Your full codex is still under Extra → Codex.",
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.secondaryText,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(state.entries, key = { it.id }) { entry ->
            CodexEntryPlate(
                entry = entry,
                selected = false,
                onClick = { onEntryClick(entry.id) },
            )
        }
        alwaysScrollEndSpacer()
    }
}
