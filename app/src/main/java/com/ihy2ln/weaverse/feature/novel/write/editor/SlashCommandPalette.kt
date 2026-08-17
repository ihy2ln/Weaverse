package com.ihy2ln.weaverse.feature.novel.write.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ihy2ln.weaverse.core.ui.CornerRadius
import com.ihy2ln.weaverse.core.ui.Spacing

/**
 * The `/` command palette (spec §6): a searchable list grouped into AI/Codex/Formatting
 * sections, rendered directly below the triggering block rather than a pixel-anchored popup at
 * the literal caret x/y — no caret-position measurement exists in this editor yet (see
 * `BlockEditor`'s documented focus-tracking scope cut), and anchoring to the block itself reads
 * the same on a touch UI where blocks are one line each. A single soft shadow (spec §1.1: "a
 * single soft shadow only on floating overlays") via `Surface`'s tonal elevation.
 */
@Composable
fun SlashCommandPalette(
    query: String,
    onCommandSelected: (SlashCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    val grouped = SlashCommands.filter(query).groupBy { it.group }

    Surface(
        modifier = modifier.fillMaxWidth().heightIn(max = 320.dp),
        shape = RoundedCornerShape(CornerRadius.card),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        if (grouped.isEmpty()) {
            Text(
                "No matching commands",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(Spacing.md),
            )
        } else {
            LazyColumn(modifier = Modifier.padding(vertical = Spacing.xs)) {
                SlashCommandGroup.entries.forEach { group ->
                    val commands = grouped[group] ?: return@forEach
                    item(key = "header_${group.name}") {
                        Text(
                            group.label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                        )
                    }
                    items(items = commands, key = { it.id }) { command ->
                        SlashCommandRow(command = command, onClick = { onCommandSelected(command) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SlashCommandRow(command: SlashCommand, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = command.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = Spacing.sm),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(command.label, style = MaterialTheme.typography.bodyMedium)
            Text(
                if (command.readiness == SlashCommandReadiness.NeedsSelection) {
                    "${command.description} (select text first)"
                } else {
                    command.description
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
