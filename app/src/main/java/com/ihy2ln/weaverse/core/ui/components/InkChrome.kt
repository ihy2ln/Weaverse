package com.ihy2ln.weaverse.core.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusSm
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import kotlinx.coroutines.delay

@Composable
fun InkTextTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = inkTokens()
    val underline = tokens.activePill
    Text(
        text = label,
        modifier = modifier
            .clickable(onClick = onClick)
            .drawBehind {
                if (selected) {
                    val y = size.height - 2.dp.toPx()
                    drawLine(underline, Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
                }
            }
            .padding(horizontal = InkSpacing.md, vertical = InkSpacing.sm),
        color = if (selected) tokens.activePill else tokens.secondaryText,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        fontSize = 14.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InkMenuChip(
    label: String,
    options: List<SegmentedOption>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    val tokens = inkTokens()
    val selectedLabel = options.firstOrNull { it.id == selectedId }?.label ?: label
    Box(modifier = modifier) {
        Text(
            text = "$selectedLabel ▾",
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(tokens.hover)
                .border(InkSpacing.hairline, tokens.hairline, RoundedCornerShape(999.dp))
                .combinedClickable(
                    onClick = { open = true },
                    onLongClick = onLongPress,
                )
                .padding(horizontal = InkSpacing.md, vertical = InkSpacing.sm),
            color = tokens.primaryText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            option.label,
                            fontWeight = if (option.id == selectedId) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            softWrap = false,
                        )
                    },
                    onClick = {
                        onSelect(option.id)
                        open = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkspaceChrome(
    bookTitle: String,
    seriesTitle: String,
    workspaceOptions: List<SegmentedOption>,
    workspaceId: String,
    modeOptions: List<SegmentedOption>,
    modeId: String,
    focusOptions: List<SegmentedOption>,
    focusId: String,
    toolOptions: List<SegmentedOption>,
    activeToolId: String?,
    onLibrary: () -> Unit,
    onSettings: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    onTool: (String?) -> Unit,
    onWorkspace: (String) -> Unit,
    onMode: (String) -> Unit,
    onFocus: (String) -> Unit,
    onWorkspaceOrderChange: (List<String>) -> Unit = {},
    onModeOrderChange: (List<String>) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val tokens = inkTokens()
    var arrangeMenu by remember { mutableStateOf<ArrangeMenu?>(null) }
    // The bar rests at half opacity and condenses; any touch lights it up to
    // full opacity for a moment, and open arrange menus keep it lit too.
    var touchedAt by remember { mutableStateOf(0L) }
    val chromeActive = arrangeMenu != null || touchedAt > 0
    val chromeAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (chromeActive) 1f else 0.5f,
        label = "chromeAlpha",
    )
    LaunchedEffect(touchedAt) {
        if (touchedAt > 0) {
            delay(2_500)
            touchedAt = 0L
        }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(tokens.panel)
            .graphicsLayer { alpha = chromeAlpha }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    touchedAt = System.currentTimeMillis()
                }
            }
            .padding(top = InkSpacing.xxs)
            .border(width = InkSpacing.hairline, color = tokens.hairline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = InkSpacing.xs, vertical = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(InkSpacing.xxs),
        ) {
            IconButton(onClick = onLibrary, modifier = Modifier.size(34.dp)) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Library", modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onSettings, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(20.dp))
            }
            InkMenuChip(
                label = "Workspace",
                options = workspaceOptions,
                selectedId = workspaceId,
                onSelect = onWorkspace,
                onLongPress = { arrangeMenu = ArrangeMenu.Workspaces },
            )
            InkMenuChip(
                label = "Mode",
                options = modeOptions,
                selectedId = modeId,
                onSelect = onMode,
                onLongPress = { arrangeMenu = ArrangeMenu.Modes },
            )
            // Focus chip removed: "Story" was the default view anyway and Pictures
            // is reachable under Extra. Kept as a no-op parameter so callers that
            // still drive focus state (the Pictures gallery) keep working.
            if (focusOptions.isNotEmpty()) {
                InkMenuChip(
                    label = "Focus",
                    options = focusOptions,
                    selectedId = focusId,
                    onSelect = onFocus,
                )
            }
            // App-wide tools, grouped behind one "Extra" chip rather than spilling
            // six tabs across the row.
            if (toolOptions.isNotEmpty()) {
                InkMenuChip(
                    label = "Extra",
                    options = toolOptions,
                    selectedId = activeToolId.orEmpty(),
                    onSelect = { onTool(if (activeToolId == it) null else it) },
                )
            }
            InkTextButton(label = "Import", onClick = onImport)
            InkTextButton(label = "Export", onClick = onExport)
            InkTextButton(label = "Undo", onClick = onUndo, enabled = canUndo)
            InkTextButton(label = "Redo", onClick = onRedo, enabled = canRedo)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = InkSpacing.md, end = InkSpacing.md, bottom = InkSpacing.xxs),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = listOf(bookTitle, seriesTitle)
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(" · "),
                modifier = Modifier.basicMarquee(
                    iterations = Int.MAX_VALUE,
                    repeatDelayMillis = 1_200,
                ),
                color = tokens.primaryText,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                softWrap = false,
            )
        }
    }
    arrangeMenu?.let { target ->
        val options = if (target == ArrangeMenu.Workspaces) workspaceOptions else modeOptions
        ReorderOptionsDialog(
            title = if (target == ArrangeMenu.Workspaces) "Arrange main modes" else "Arrange ${workspaceOptions.firstOrNull { it.id == workspaceId }?.label ?: "menu"}",
            options = options,
            onSave = { ids ->
                if (target == ArrangeMenu.Workspaces) onWorkspaceOrderChange(ids)
                else onModeOrderChange(ids)
                arrangeMenu = null
            },
            onDismiss = { arrangeMenu = null },
        )
    }
}

private enum class ArrangeMenu { Workspaces, Modes }

@Composable
private fun ReorderOptionsDialog(
    title: String,
    options: List<SegmentedOption>,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val ordered = remember(options.map { it.id }) { options.toMutableStateList() }
    val tokens = inkTokens()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    "Drag the handles to choose the button order.",
                    color = tokens.secondaryText,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = InkSpacing.sm),
                )
                LazyColumn {
                    itemsIndexed(ordered, key = { _, option -> option.id }) { _, option ->
                        var dragTotal by remember(option.id) { mutableFloatStateOf(0f) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = InkSpacing.xxs)
                                .clip(RoundedCornerShape(inkRadiusSm()))
                                .background(tokens.hover)
                                .padding(start = InkSpacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(option.label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            Text(
                                "≡",
                                modifier = Modifier
                                    .pointerInput(option.id) {
                                        detectDragGestures(
                                            onDragEnd = { dragTotal = 0f },
                                            onDragCancel = { dragTotal = 0f },
                                        ) { change, amount ->
                                            change.consume()
                                            dragTotal += amount.y
                                            val threshold = 34.dp.toPx()
                                            val current = ordered.indexOfFirst { it.id == option.id }
                                            when {
                                                dragTotal > threshold && current < ordered.lastIndex -> {
                                                    val moved = ordered.removeAt(current)
                                                    ordered.add(current + 1, moved)
                                                    dragTotal = 0f
                                                }
                                                dragTotal < -threshold && current > 0 -> {
                                                    val moved = ordered.removeAt(current)
                                                    ordered.add(current - 1, moved)
                                                    dragTotal = 0f
                                                }
                                            }
                                        }
                                    }
                                    .padding(horizontal = InkSpacing.lg, vertical = InkSpacing.md),
                                color = tokens.activePill,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(ordered.map { it.id }) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Dark capsule matching the Novel / Write / Story chrome — use for Clear Text and mode chips. */
@Composable
fun InkModeCapsule(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    val tokens = inkTokens()
    val shape = RoundedCornerShape(999.dp)
    Text(
        text = label,
        modifier = modifier
            .clip(shape)
            .background(tokens.hover)
            .border(
                InkSpacing.hairline,
                if (selected) tokens.activePill else tokens.hairline,
                shape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = InkSpacing.lg, vertical = InkSpacing.sm),
        color = if (enabled) tokens.primaryText else tokens.secondaryText,
        fontSize = 14.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
    )
}
