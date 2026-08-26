package com.ihy2ln.weaverse.feature.roleplay.lorebook

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusSm
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.util.alwaysScrollEndSpacer

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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = InkSpacing.lg, vertical = InkSpacing.xs)
                    .clip(RoundedCornerShape(inkRadiusSm()))
                    .background(tokens.panel)
                    .border(1.dp, tokens.hairline, RoundedCornerShape(inkRadiusSm()))
                    .clickable { onEntryClick(entry.id) }
                    .padding(InkSpacing.sm),
            ) {
                if (entry.category.isNotBlank()) {
                    Text(
                        entry.category.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 1.5.sp,
                        color = tokens.secondaryText,
                    )
                }
                Text(
                    entry.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.summary.isNotBlank()) {
                    Text(
                        entry.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.secondaryText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        alwaysScrollEndSpacer()
    }
}
