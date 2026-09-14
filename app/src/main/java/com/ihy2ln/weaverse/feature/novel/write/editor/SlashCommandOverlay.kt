package com.ihy2ln.weaverse.feature.novel.write.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusMd
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens

data class SlashCommand(val id: String, val label: String, val description: String)

val defaultSlashCommands = listOf(
    SlashCommand("scene_beat", "Scene Beat", "Generate a pivotal story moment."),
    SlashCommand("continue", "Continue Writing", "Continue from the current scene."),
    SlashCommand("expand", "Expand", "Expand the selected text."),
    SlashCommand("image", "Insert Image", "Add an image block to the scene."),
    SlashCommand("video", "Insert Video", "Add a video block to the scene."),
    SlashCommand("heading", "Heading", "Convert line to heading."),
)

@Composable
fun SlashCommandOverlay(
    commands: List<SlashCommand>,
    filter: String,
    onSelect: (SlashCommand) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val filtered = if (filter.isBlank()) {
        commands
    } else {
        commands.filter { it.label.contains(filter, ignoreCase = true) || it.id.contains(filter, ignoreCase = true) }
    }
    val tokens = inkTokens()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(inkRadiusMd()))
            .clip(RoundedCornerShape(inkRadiusMd()))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, tokens.hairline, RoundedCornerShape(inkRadiusMd()))
            .heightIn(max = 260.dp)
            .verticalScroll(rememberScrollState())
            .padding(vertical = InkSpacing.xxs),
    ) {
        filtered.forEach { cmd ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(cmd); onDismiss() }
                    .padding(horizontal = InkSpacing.md, vertical = InkSpacing.sm),
            ) {
                Text(cmd.label, style = MaterialTheme.typography.titleSmall)
                Text(cmd.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
