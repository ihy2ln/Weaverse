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
import androidx.compose.runtime.LaunchedEffect
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
import com.ihy2ln.weaverse.core.text.Mark
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
    Underline,
    Strikethrough,
    Superscript,
    Subscript,
    Color,
    Highlight,
    FontFamily,
    FontSize,
    AddToCodex,
    Shorten,
    Extend,
    Replace,
    Undo,
    Redo,
    Speak,
    /** Speech-to-text: insert dictated words at the caret / selection. */
    Dictate,
    /** Bounce out to the host screen's AI Prompting window (Extend/Summarize/etc). */
    OpenPrompting,
}

data class EditTextPopupConfig(
    val showFormatting: Boolean = true,
    val showWritingAi: Boolean = true,
    val showHistory: Boolean = true,
    val showMessageEdit: Boolean = false,
    val showSpeak: Boolean = true,
    /** Whether a "Prompting" category is offered — only meaningful where a Prompting window exists. */
    val showPrompting: Boolean = true,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val hasSelection: Boolean = false,
    /** Marks shared by the whole selection — shown as a ✓ next to their format item. */
    val activeMarks: Set<Mark> = emptySet(),
)

private enum class EditPopupPage { Categories, Edit, Format, WritingAi, History }

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

    // Only worth a category-picker step when there's more than one destination —
    // a config with just Edit enabled (e.g. chat messages) goes straight there.
    val showCategories = config.showFormatting || config.showPrompting || config.showWritingAi || config.showHistory
    var page by remember { mutableStateOf(EditPopupPage.Categories) }
    LaunchedEffect(expanded) {
        if (expanded) {
            page = if (showCategories) EditPopupPage.Categories else EditPopupPage.Edit
        }
    }
    val onBackToCategories: (() -> Unit)? = if (showCategories) {
        { page = EditPopupPage.Categories }
    } else {
        null
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = offset,
        modifier = modifier,
    ) {
        when (page) {
            EditPopupPage.Categories -> CategoryMenuItems(
                config = config,
                labelColor = labelColor,
                muted = muted,
                onAction = onAction,
                onDismiss = onDismiss,
                onNavigate = { page = it },
            )
            EditPopupPage.Edit -> EditActionMenuItems(
                config = config,
                labelColor = labelColor,
                muted = muted,
                onAction = onAction,
                onDismiss = onDismiss,
                onBack = onBackToCategories,
            )
            EditPopupPage.Format -> FormatMenuItems(
                config = config,
                labelColor = labelColor,
                muted = muted,
                onAction = onAction,
                onDismiss = onDismiss,
                onBack = { page = EditPopupPage.Categories },
            )
            EditPopupPage.WritingAi -> WritingAiMenuItems(
                config = config,
                labelColor = labelColor,
                muted = muted,
                onAction = onAction,
                onDismiss = onDismiss,
                onBack = { page = EditPopupPage.Categories },
            )
            EditPopupPage.History -> HistoryMenuItems(
                config = config,
                labelColor = labelColor,
                muted = muted,
                onAction = onAction,
                onDismiss = onDismiss,
                onBack = { page = EditPopupPage.Categories },
            )
        }
    }
}

/** Condensed landing popup: a chip per category instead of one long flat list. */
@Composable
private fun CategoryMenuItems(
    config: EditTextPopupConfig,
    labelColor: Color,
    muted: Color,
    onAction: (EditTextAction) -> Unit,
    onDismiss: () -> Unit,
    onNavigate: (EditPopupPage) -> Unit,
) {
    MenuHeader("Menu", muted)
    if (config.showFormatting) {
        Item("Aa · Format", labelColor, onClick = { onNavigate(EditPopupPage.Format) })
    }
    if (config.showPrompting) {
        Item("Prompting", labelColor) { onAction(EditTextAction.OpenPrompting); onDismiss() }
    }
    Item("Edit", labelColor, onClick = { onNavigate(EditPopupPage.Edit) })
    if (config.showWritingAi) {
        Item("Writing AI", labelColor, onClick = { onNavigate(EditPopupPage.WritingAi) })
    }
    if (config.showHistory) {
        Item("History", labelColor, onClick = { onNavigate(EditPopupPage.History) })
    }
}

@Composable
private fun EditActionMenuItems(
    config: EditTextPopupConfig,
    labelColor: Color,
    muted: Color,
    onAction: (EditTextAction) -> Unit,
    onDismiss: () -> Unit,
    onBack: (() -> Unit)?,
) {
    MenuHeader("Edit", muted)
    if (onBack != null) {
        Item("← Back", labelColor, onClick = onBack)
    }
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
}

@Composable
private fun WritingAiMenuItems(
    config: EditTextPopupConfig,
    labelColor: Color,
    muted: Color,
    onAction: (EditTextAction) -> Unit,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
) {
    MenuHeader("Writing AI", muted)
    Item("← Back", labelColor, onClick = onBack)
    Item("Add to Codex", labelColor, enabled = config.hasSelection) {
        onAction(EditTextAction.AddToCodex); onDismiss()
    }
    Item("Shorten", labelColor) { onAction(EditTextAction.Shorten); onDismiss() }
    Item("Extend", labelColor) { onAction(EditTextAction.Extend); onDismiss() }
    Item("Replace", labelColor) { onAction(EditTextAction.Replace); onDismiss() }
}

@Composable
private fun HistoryMenuItems(
    config: EditTextPopupConfig,
    labelColor: Color,
    muted: Color,
    onAction: (EditTextAction) -> Unit,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
) {
    MenuHeader("History", muted)
    Item("← Back", labelColor, onClick = onBack)
    Item("Undo", labelColor, enabled = config.canUndo) {
        onAction(EditTextAction.Undo); onDismiss()
    }
    Item("Redo", labelColor, enabled = config.canRedo) {
        onAction(EditTextAction.Redo); onDismiss()
    }
}

@Composable
private fun FormatMenuItems(
    config: EditTextPopupConfig,
    labelColor: Color,
    muted: Color,
    onAction: (EditTextAction) -> Unit,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
) {
    fun markLabel(base: String, mark: Mark) = if (mark in config.activeMarks) "$base  ✓" else base

    MenuHeader("Format", muted)
    Item("← Back", labelColor, onClick = onBack)
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    // Close after each toggle. Span changes make Compose call showMenu() again;
    // EditMenuGate keeps the popup from coming back for this same selection.
    Item(markLabel("Bold", Mark.Bold), labelColor, enabled = config.hasSelection) {
        onAction(EditTextAction.Bold); onDismiss()
    }
    Item(markLabel("Italic", Mark.Italic), labelColor, enabled = config.hasSelection) {
        onAction(EditTextAction.Italic); onDismiss()
    }
    Item(markLabel("Underline", Mark.Underline), labelColor, enabled = config.hasSelection) {
        onAction(EditTextAction.Underline); onDismiss()
    }
    Item(markLabel("Strikethrough", Mark.Strikethrough), labelColor, enabled = config.hasSelection) {
        onAction(EditTextAction.Strikethrough); onDismiss()
    }
    Item(markLabel("Superscript", Mark.Superscript), labelColor, enabled = config.hasSelection) {
        onAction(EditTextAction.Superscript); onDismiss()
    }
    Item(markLabel("Subscript", Mark.Subscript), labelColor, enabled = config.hasSelection) {
        onAction(EditTextAction.Subscript); onDismiss()
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    Item("Text color…", labelColor, enabled = config.hasSelection) {
        onAction(EditTextAction.Color); onDismiss()
    }
    Item("Highlight…", labelColor, enabled = config.hasSelection) {
        onAction(EditTextAction.Highlight); onDismiss()
    }
    Item("Font…", labelColor, enabled = config.hasSelection) {
        onAction(EditTextAction.FontFamily); onDismiss()
    }
    Item("Size…", labelColor, enabled = config.hasSelection) {
        onAction(EditTextAction.FontSize); onDismiss()
    }
}

@Composable
fun TextColorPickerDialog(
    initial: Color,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit,
    title: String = "Text color",
) {
    var color by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                InkHsvColorWheel(
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
