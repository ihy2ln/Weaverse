package com.ihy2ln.weaverse.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ihy2ln.weaverse.core.text.TextOverlay
import com.ihy2ln.weaverse.core.text.TextOverlayStyle
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.util.parseHexColor

private val OverlayTextSwatches = listOf("#FFFFFF", "#000000", "#FFE066", "#FF6B6B", "#6BCB77", "#4A90D9")
private val OverlayBgSwatches = listOf("#000000", "#FFFFFF", "#1A1A2E", "#4A90D9", "#D94A4A")

/** Editor dialog for one [TextOverlay]: text, Plain/Speech-bubble style, colors, delete. */
@Composable
fun TextOverlayEditSheet(
    overlay: TextOverlay,
    onDismiss: () -> Unit,
    onSave: (TextOverlay) -> Unit,
    onDelete: () -> Unit,
) {
    var text by remember(overlay.id) { mutableStateOf(overlay.text) }
    var style by remember(overlay.id) { mutableStateOf(overlay.style) }
    var colorHex by remember(overlay.id) { mutableStateOf(overlay.colorHex) }
    var backgroundHex by remember(overlay.id) { mutableStateOf(overlay.backgroundHex ?: "#000000") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Text overlay") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(InkSpacing.sm),
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Text") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm)) {
                    FilterChip(
                        selected = style == TextOverlayStyle.Plain,
                        onClick = { style = TextOverlayStyle.Plain },
                        label = { Text("Plain") },
                    )
                    FilterChip(
                        selected = style == TextOverlayStyle.SpeechBubble,
                        onClick = { style = TextOverlayStyle.SpeechBubble },
                        label = { Text("Speech bubble") },
                    )
                }
                Text("Text color", style = MaterialTheme.typography.labelSmall)
                SwatchRow(OverlayTextSwatches, colorHex) { colorHex = it }
                Text("Background", style = MaterialTheme.typography.labelSmall)
                SwatchRow(OverlayBgSwatches, backgroundHex) { backgroundHex = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    overlay.copy(
                        text = text,
                        style = style,
                        colorHex = colorHex,
                        backgroundHex = backgroundHex,
                    ),
                )
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onDelete(); onDismiss() }) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun SwatchRow(hexes: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
        hexes.forEach { hex ->
            val color = parseHexColor(hex, Color.Gray)
            val borderColor = if (hex.equals(selected, ignoreCase = true)) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(color, CircleShape)
                    .border(2.dp, borderColor, CircleShape)
                    .clickable { onSelect(hex) },
            )
        }
    }
}
