package com.ihy2ln.weaverse.core.ui

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.ihy2ln.weaverse.data.db.entity.SceneStatus

/** Public — Codex/Roleplay entry editors (Phase 7+) also need Color -> stored hex string. */
fun Color.toHex(): String =
    String.format(java.util.Locale.ROOT, "#%06X", 0xFFFFFF and this.toArgb())

/** Public — same reason as [toHex]: entry editors need to render a stored hex string as a Color. */
fun parseHex(hex: String): Color? {
    val cleaned = hex.removePrefix("#").trim()
    if (cleaned.length != 6 && cleaned.length != 8) return null
    return try {
        val argb = if (cleaned.length == 6) 0xFF000000.toInt() or cleaned.toLong(16).toInt() else cleaned.toLong(16).toInt()
        Color(argb)
    } catch (e: NumberFormatException) {
        null
    }
}

/**
 * Curated-palette-plus-hex-entry picker (spec §11). Used for category/entry
 * color overrides, labels, and roleplay character accents.
 */
@Composable
fun ColorPickerDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onColorSelected: (Color) -> Unit,
) {
    var selected by remember { mutableStateOf(initialColor) }
    var hexText by remember { mutableStateOf(initialColor.toHex()) }
    var hexError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a color") },
        text = {
            Column {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier.height(120.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(CuratedColorPalette) { color ->
                        ColorSwatch(
                            color = color,
                            selected = color == selected,
                            onClick = {
                                selected = color
                                hexText = color.toHex()
                                hexError = false
                            },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.md))
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { text ->
                        hexText = text
                        val parsed = parseHex(text)
                        hexError = parsed == null
                        if (parsed != null) selected = parsed
                    },
                    label = { Text("Hex") },
                    isError = hexError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onColorSelected(selected) }, enabled = !hexError) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * "Color legend" sheet listing every codex category color and scene status
 * color, plus the app-wide "disable all colorization" toggle (spec §11).
 */
@Composable
fun ColorLegendSheet(
    colorizationEnabled: Boolean,
    onColorizationToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    InkModalBottomSheet(onDismiss = onDismiss, title = "Color legend") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Use color everywhere", style = MaterialTheme.typography.bodyLarge)
            Switch(checked = colorizationEnabled, onCheckedChange = onColorizationToggle)
        }
        Spacer(modifier = Modifier.height(Spacing.lg))
        Text("Codex categories", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(Spacing.sm))
        CodexCategoryKind.entries.forEach { kind ->
            LegendRow(label = kind.label, color = kind.defaultColor)
        }
        Spacer(modifier = Modifier.height(Spacing.lg))
        Text("Scene status", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(Spacing.sm))
        SceneStatus.entries.forEach { status ->
            LegendRow(label = status.label, color = status.color)
        }
    }
}

@Composable
private fun LegendRow(label: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        ColorSwatch(color = color, size = 16.dp)
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}
