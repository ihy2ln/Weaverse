package com.ihy2ln.weaverse.feature.novel.write.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.HighlightOff
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ihy2ln.weaverse.core.text.Mark
import com.ihy2ln.weaverse.core.ui.CornerRadius
import com.ihy2ln.weaverse.core.ui.Spacing

/**
 * The custom press-and-hold contextual toolbar (spec §7): appears whenever a block's text field
 * has a non-empty selection — any way of creating one (long-press-drag, double-tap-word-select,
 * drag handles) triggers it, not just a literal long-press gesture, since Compose's
 * `BasicTextField` doesn't expose "this was a long-press" as a distinct signal from "the
 * selection changed." A custom overlay rather than replacing the stock Android selection handles,
 * per spec ("so the app's own actions sit alongside the system ones").
 *
 * Select/Select All/Paste as plain text/Edit/Add to Codex from spec's own list aren't here —
 * Select/Select All already work via the platform's own selection handles this toolbar floats
 * above; the rest are tracked as a follow-up (rev02-07b/rev02-08b, see BUILD_NOTES).
 */
@Composable
fun SelectionToolbar(
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onPaste: () -> Unit,
    onDelete: () -> Unit,
    onToggleMark: (Mark) -> Unit,
    onPickTextColor: () -> Unit,
    onPickHighlight: () -> Unit,
    onRemoveHighlight: () -> Unit,
    onMoveBlockUp: () -> Unit,
    onMoveBlockDown: () -> Unit,
    onAskAi: (SlashCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    var askAiExpanded by remember { mutableStateOf(false) }
    var moveExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(CornerRadius.card),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
        ) {
            IconButton(onClick = onCopy) { Icon(Icons.Filled.ContentCopy, contentDescription = "Copy") }
            IconButton(onClick = onCut) { Icon(Icons.Filled.ContentCut, contentDescription = "Cut") }
            IconButton(onClick = onPaste) { Icon(Icons.Filled.ContentPaste, contentDescription = "Paste") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
            IconButton(onClick = { onToggleMark(Mark.Bold) }) { Icon(Icons.Filled.FormatBold, contentDescription = "Bold") }
            IconButton(onClick = { onToggleMark(Mark.Italic) }) { Icon(Icons.Filled.FormatItalic, contentDescription = "Italic") }
            IconButton(onClick = { onToggleMark(Mark.Underline) }) { Icon(Icons.Filled.FormatUnderlined, contentDescription = "Underline") }
            IconButton(onClick = { onToggleMark(Mark.Strikethrough) }) { Icon(Icons.Filled.FormatStrikethrough, contentDescription = "Strikethrough") }
            IconButton(onClick = onPickTextColor) { Icon(Icons.Filled.FormatColorText, contentDescription = "Text colour") }
            IconButton(onClick = onPickHighlight) { Icon(Icons.Filled.FormatColorFill, contentDescription = "Highlight") }
            IconButton(onClick = onRemoveHighlight) { Icon(Icons.Filled.HighlightOff, contentDescription = "Remove highlight") }
            Box {
                IconButton(onClick = { moveExpanded = true }) { Icon(Icons.Filled.SwapVert, contentDescription = "Move block") }
                DropdownMenu(expanded = moveExpanded, onDismissRequest = { moveExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Move block up") },
                        leadingIcon = { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null) },
                        onClick = { moveExpanded = false; onMoveBlockUp() },
                    )
                    DropdownMenuItem(
                        text = { Text("Move block down") },
                        leadingIcon = { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null) },
                        onClick = { moveExpanded = false; onMoveBlockDown() },
                    )
                }
            }
            Box {
                IconButton(onClick = { askAiExpanded = true }) { Icon(Icons.Filled.AutoAwesome, contentDescription = "Ask AI") }
                DropdownMenu(expanded = askAiExpanded, onDismissRequest = { askAiExpanded = false }) {
                    SlashCommands.all.filter { it.id in AskAiCommandIds }.forEach { command ->
                        DropdownMenuItem(
                            text = { Text(command.label) },
                            onClick = { askAiExpanded = false; onAskAi(command) },
                        )
                    }
                }
            }
        }
    }
}

private val AskAiCommandIds = setOf(
    SlashCommands.REWRITE_SELECTION,
    SlashCommands.EXPAND,
    SlashCommands.SHORTEN,
    SlashCommands.DESCRIBE,
    SlashCommands.DIALOGUE_PASS,
)
