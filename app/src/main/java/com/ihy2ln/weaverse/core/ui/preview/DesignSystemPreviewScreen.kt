package com.ihy2ln.weaverse.core.ui.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.ihy2ln.weaverse.core.ui.components.InkCard
import com.ihy2ln.weaverse.core.ui.components.InkChip
import com.ihy2ln.weaverse.core.ui.components.InkColorPicker
import com.ihy2ln.weaverse.core.ui.components.InkEmptyState
import com.ihy2ln.weaverse.core.ui.components.InkGhostChip
import com.ihy2ln.weaverse.core.ui.components.InkSegmentedPill
import com.ihy2ln.weaverse.core.ui.components.InkToolbar
import com.ihy2ln.weaverse.core.ui.components.SegmentedOption
import com.ihy2ln.weaverse.core.ui.theme.CodexCharacters
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.InkSecondaryText

@Composable
fun DesignSystemPreviewScreen(modifier: Modifier = Modifier) {
    var tab by remember { mutableStateOf("Plan") }
    var chipColor by remember { mutableStateOf(CodexCharacters) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        InkToolbar(
            title = "Adams Haven",
            subtitle = "Book 1 · Design System",
        )
        InkSegmentedPill(
            options = listOf("Plan", "Write", "Chat", "Review").map { SegmentedOption(it, it) },
            selectedId = tab,
            onSelect = { tab = it },
            modifier = Modifier.padding(InkSpacing.lg),
        )
        InkCard(modifier = Modifier.padding(InkSpacing.lg)) {
            Text("Components", color = Color.Black)
            Column(verticalArrangement = Arrangement.spacedBy(InkSpacing.sm)) {
                InkChip(label = "John Z", color = CodexCharacters, selected = true)
                InkGhostChip(label = "+ Codex", onClick = {})
                InkColorPicker(selected = chipColor, onSelect = { chipColor = it })
            }
        }
        InkEmptyState(
            title = "No scenes yet",
            subtitle = "Create your first scene from Plan view.",
            modifier = Modifier.padding(InkSpacing.lg),
        )
        Text(
            text = "Active tab: $tab",
            modifier = Modifier.padding(InkSpacing.lg),
            color = InkSecondaryText,
        )
    }
}
