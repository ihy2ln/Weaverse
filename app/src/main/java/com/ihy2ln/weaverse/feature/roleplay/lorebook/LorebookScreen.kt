package com.ihy2ln.weaverse.feature.roleplay.lorebook

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.feature.novel.codex.CodexRailScreen

@Composable
fun LorebookScreen(
    onEntryClick: (String) -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Lorebook (World Info)",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(InkSpacing.lg),
        )
        Text(
            "Shared with Novel Codex — keys, aliases, and lore fields drive context in roleplay.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = InkSpacing.lg),
        )
        CodexRailScreen(
            onEntryClick = onEntryClick,
            modifier = Modifier.weight(1f),
        )
    }
}
