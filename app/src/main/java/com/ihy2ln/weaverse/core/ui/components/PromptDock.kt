package com.ihy2ln.weaverse.core.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ihy2ln.weaverse.ai.ModelInfo
import com.ihy2ln.weaverse.core.ui.theme.inkTokens

/**
 * The prompt dock shared by Novel writing and RPG play: a resizable panel pinned to
 * the bottom of the screen with a drag handle, a scrolling body, and a small set of
 * controls sized for it. Both modes build their rows out of the pieces below so the
 * two composers stay one design instead of drifting apart.
 */

/** Smallest dock that still shows the handle and one row of controls. */
private const val MinDockHeight = 84f

@Composable
fun PromptDockShell(
    /** Height the user dragged to, in dp. 0 means size to the content. */
    pinnedHeightDp: Float,
    onPinnedHeightChange: (Float) -> Unit,
    /** Ceiling for both the dragged and the automatic height. */
    autoMaxHeight: Dp,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    // Remembered so a drag starting from the auto-sized dock knows where it began.
    var measuredHeight by remember { mutableStateOf(0f) }
    val maxDock = autoMaxHeight.value
    val pinned = pinnedHeightDp.takeIf { it > 0f }?.coerceIn(MinDockHeight, maxDock)
    Surface(tonalElevation = 5.dp, modifier = modifier) {
        Column(
            Modifier.fillMaxWidth()
                .onSizeChanged { measuredHeight = with(density) { it.height.toDp().value } }
                .then(
                    if (pinned != null) Modifier.height(pinned.dp) else Modifier.heightIn(max = autoMaxHeight),
                ),
        ) {
            PromptDockDragHandle(
                onDrag = { deltaPx ->
                    val base = pinned ?: measuredHeight
                    val deltaDp = with(density) { deltaPx.toDp().value }
                    // The dock is anchored to the bottom: dragging up must make it taller.
                    onPinnedHeightChange((base - deltaDp).coerceIn(MinDockHeight, maxDock))
                },
                onReset = { onPinnedHeightChange(0f) },
            )
            Column(
                Modifier.fillMaxWidth()
                    .then(if (pinned != null) Modifier.weight(1f) else Modifier)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                content = content,
            )
        }
    }
}

/** How many lines the main input may use at a given dock height. */
fun promptDockFieldLines(pinnedHeightDp: Float, fallback: Int): Int =
    if (pinnedHeightDp > 0f) ((pinnedHeightDp - 108f) / 22f).toInt().coerceIn(1, 24) else fallback

/**
 * The grab bar along the top of the dock. Drag it up or down to resize; the dock keeps
 * the height it was left at. A double tap hands the dock back to automatic sizing.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PromptDockDragHandle(onDrag: (Float) -> Unit, onReset: () -> Unit) {
    val tokens = inkTokens()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp)
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState(onDelta = onDrag),
            )
            .combinedClickable(
                onClick = {},
                onDoubleClick = onReset,
                onClickLabel = "Resize the prompt dock",
            )
            .semantics { contentDescription = "Drag to resize the prompt box" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(tokens.hairline),
        )
    }
}

/** A text button sized for the dock — no 48dp Material padding around four characters. */
@Composable
fun PromptDockChip(
    label: String,
    enabled: Boolean = true,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val tokens = inkTokens()
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        modifier = Modifier
            .heightIn(min = 30.dp)
            .then(
                if (selected) {
                    Modifier.clip(RoundedCornerShape(6.dp)).background(tokens.activePill.copy(alpha = 0.16f))
                } else {
                    Modifier
                },
            ),
    ) { Text(label, fontSize = 12.sp, maxLines = 1, softWrap = false) }
}

/** A one-line bordered field; the Material default is 56dp tall and dwarfs this dock. */
@Composable
fun PromptDockTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    maxLines: Int = 1,
) {
    val tokens = inkTokens()
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .clip(shape)
            .border(1.dp, tokens.hairline, shape)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        if (value.isEmpty()) {
            Text(placeholder, style = MaterialTheme.typography.bodySmall, color = tokens.secondaryText,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            textStyle = MaterialTheme.typography.bodySmall.copy(color = tokens.primaryText),
            cursorBrush = SolidColor(tokens.primaryText),
            maxLines = maxLines,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Four digits is the whole range anyone generates, so the box is four digits wide. */
@Composable
fun PromptDockWordsField(
    words: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit,
    contentDescription: String = "Output words",
) {
    val tokens = inkTokens()
    var text by remember { mutableStateOf(words.toString()) }
    // Only re-sync from the state when it really diverges, so clearing the box to
    // retype does not snap a number back under the caret.
    LaunchedEffect(words) { if (text.toIntOrNull() != words) text = words.toString() }
    BasicTextField(
        value = text,
        onValueChange = { raw ->
            val digits = raw.filter(Char::isDigit).take(4)
            text = digits
            digits.toIntOrNull()?.let(onChange)
        },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.labelMedium.copy(
            color = if (enabled) tokens.primaryText else tokens.secondaryText,
            textAlign = TextAlign.Center,
        ),
        modifier = Modifier.width(46.dp).semantics { this.contentDescription = contentDescription },
        decorationBox = { inner ->
            Box(
                Modifier.border(1.dp, tokens.hairline, RoundedCornerShape(6.dp))
                    .padding(horizontal = 2.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (text.isEmpty()) {
                    Text("0000", style = MaterialTheme.typography.labelMedium, color = tokens.secondaryText)
                }
                inner()
            }
        },
    )
}

/**
 * One line naming the model, opening a scrollable list of the cached text-generation
 * models. [current] is shown as-is; an empty value means the connected default.
 */
@Composable
fun PromptDockModelRow(
    current: String,
    models: List<ModelInfo>,
    enabled: Boolean,
    onPick: (String) -> Unit,
    onUseDefault: () -> Unit,
) {
    val tokens = inkTokens()
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable(enabled = enabled) { open = true }
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Model ▾", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(
                " " + current.ifBlank { "Connected default" },
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = tokens.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem(
                text = { Text("Connected default", style = MaterialTheme.typography.bodySmall) },
                onClick = { open = false; onUseDefault() },
            )
            HorizontalDivider(color = tokens.hairline)
            if (models.isEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text("No cached models — refresh them in AI settings",
                            style = MaterialTheme.typography.bodySmall, color = tokens.secondaryText)
                    },
                    onClick = { open = false },
                )
            } else {
                // Fixed height keeps the list lazy inside the menu's own scroll container.
                LazyColumn(Modifier.width(280.dp).height(260.dp)) {
                    items(models, key = { it.id }) { item ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(item.displayName, style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(item.id, style = MaterialTheme.typography.labelSmall,
                                        color = tokens.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            },
                            onClick = { open = false; onPick(item.id) },
                        )
                    }
                }
            }
        }
    }
}

/** One cell of [PromptDockGrid]. */
data class PromptDockOption(val id: String, val label: String)

/**
 * Two columns split by rules. Novel uses it for prompt templates, where several can be
 * ticked at once and [selectedIds] numbers them in the order they were picked; RPG uses
 * it for action presets, where a tap simply fills the composer.
 */
@Composable
fun PromptDockGrid(
    options: List<PromptDockOption>,
    selectedIds: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 168.dp,
    emptyText: String = "Nothing to choose from.",
    numbered: Boolean = true,
) {
    val tokens = inkTokens()
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .border(1.dp, tokens.hairline, RoundedCornerShape(6.dp))
            .verticalScroll(rememberScrollState()),
    ) {
        if (options.isEmpty()) {
            Text(emptyText, style = MaterialTheme.typography.labelSmall, color = tokens.secondaryText,
                modifier = Modifier.padding(8.dp))
        }
        options.chunked(2).forEachIndexed { row, pair ->
            if (row > 0) HorizontalDivider(color = tokens.hairline)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                PromptDockCell(pair[0], selectedIds, onSelect, numbered, Modifier.weight(1f))
                VerticalDivider(color = tokens.hairline)
                val second = pair.getOrNull(1)
                if (second == null) {
                    Spacer(Modifier.weight(1f))
                } else {
                    PromptDockCell(second, selectedIds, onSelect, numbered, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PromptDockCell(
    option: PromptDockOption,
    selectedIds: List<String>,
    onSelect: (String) -> Unit,
    numbered: Boolean,
    modifier: Modifier = Modifier,
) {
    val tokens = inkTokens()
    val selected = option.id in selectedIds
    Row(
        modifier
            .fillMaxHeight()
            .background(if (selected) tokens.activePill.copy(alpha = 0.14f) else Color.Transparent)
            .clickable { onSelect(option.id) }
            .padding(horizontal = 6.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (numbered) {
            Text(
                if (selected) "${selectedIds.indexOf(option.id) + 1}." else "○",
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) tokens.activePill else tokens.secondaryText,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            if (numbered) " " + option.label else option.label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) tokens.primaryText else tokens.secondaryText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
