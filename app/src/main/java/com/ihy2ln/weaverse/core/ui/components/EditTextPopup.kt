package com.ihy2ln.weaverse.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.toHexString

enum class EditTextAction {
    Copy,
    Cut,
    Paste,
    SelectAll,
    Delete,
    Edit,
    Bold,
    Italic,
    Color,
    AddToCodex,
    Shorten,
    Extend,
    Replace,
    Undo,
    Redo,
    Speak,
    /** Speech-to-text: insert dictated words at the caret / selection. */
    Dictate,
}

data class EditTextPopupConfig(
    val showFormatting: Boolean = true,
    val showWritingAi: Boolean = true,
    val showHistory: Boolean = true,
    val showMessageEdit: Boolean = false,
    val showSpeak: Boolean = true,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val hasSelection: Boolean = false,
)

@Composable
fun EditTextPopup(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: (EditTextAction) -> Unit,
    config: EditTextPopupConfig = EditTextPopupConfig(),
    anchorOffset: Offset = Offset.Zero,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val offset = with(density) {
        DpOffset(anchorOffset.x.toDp(), anchorOffset.y.toDp())
    }
    val labelColor = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = offset,
        modifier = modifier,
    ) {
        MenuHeader("Edit", muted)
        Item("Copy", labelColor) { onAction(EditTextAction.Copy); onDismiss() }
        Item("Cut", labelColor, enabled = config.hasSelection) {
            onAction(EditTextAction.Cut); onDismiss()
        }
        Item("Paste", labelColor) { onAction(EditTextAction.Paste); onDismiss() }
        Item("Select all", labelColor) { onAction(EditTextAction.SelectAll); onDismiss() }
        Item("Delete", labelColor, enabled = config.hasSelection || config.showMessageEdit) {
            onAction(EditTextAction.Delete); onDismiss()
        }
        if (config.showMessageEdit) {
            Item("Edit…", labelColor) { onAction(EditTextAction.Edit); onDismiss() }
        }
        Item("Dictate (voice)", labelColor) {
            onAction(EditTextAction.Dictate); onDismiss()
        }
        if (config.showSpeak) {
            Item("Speak", labelColor, enabled = config.hasSelection) {
                onAction(EditTextAction.Speak); onDismiss()
            }
        }

        if (config.showFormatting) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            MenuHeader("Format", muted)
            Item("Bold", labelColor, enabled = config.hasSelection) {
                onAction(EditTextAction.Bold); onDismiss()
            }
            Item("Italicize", labelColor, enabled = config.hasSelection) {
                onAction(EditTextAction.Italic); onDismiss()
            }
            Item("Add color…", labelColor, enabled = config.hasSelection) {
                onAction(EditTextAction.Color)
            }
        }

        if (config.showWritingAi) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            MenuHeader("Writing AI", muted)
            Item("Add to Codex", labelColor, enabled = config.hasSelection) {
                onAction(EditTextAction.AddToCodex); onDismiss()
            }
            Item("Shorten", labelColor) { onAction(EditTextAction.Shorten); onDismiss() }
            Item("Extend", labelColor) { onAction(EditTextAction.Extend); onDismiss() }
            Item("Replace", labelColor) { onAction(EditTextAction.Replace); onDismiss() }
        }

        if (config.showHistory) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            MenuHeader("History", muted)
            Item("Undo", labelColor, enabled = config.canUndo) {
                onAction(EditTextAction.Undo); onDismiss()
            }
            Item("Redo", labelColor, enabled = config.canRedo) {
                onAction(EditTextAction.Redo); onDismiss()
            }
        }
    }
}

@Composable
fun TextColorPickerDialog(
    initial: Color,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit,
) {
    var color by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Text color") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                InkSwatchColorPicker(
                    selected = color,
                    onSelect = { color = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(color); onDismiss() }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun MenuHeader(label: String, color: Color) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier.padding(horizontal = InkSpacing.md, vertical = InkSpacing.xs),
    )
}

@Composable
private fun Item(
    label: String,
    color: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                label,
                color = if (enabled) color else color.copy(alpha = 0.38f),
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        onClick = onClick,
        enabled = enabled,
    )
}

fun Color.toSpanHex(): String = toHexString()
