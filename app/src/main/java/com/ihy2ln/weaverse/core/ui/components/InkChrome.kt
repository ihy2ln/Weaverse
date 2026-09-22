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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
    onHome: () -> Unit = {},
    onSearch: () -> Unit = {},
    isHome: Boolean = false,
    browsing: Boolean = false,
    onLibrary: () -> Unit,
    onSettings: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    canGoBack: Boolean = false,
    onBack: () -> Unit = {},
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
    var menuOpen by remember { mutableStateOf(false) }
    val accent = com.ihy2ln.weaverse.feature.shell.HomeAccent
    Surface(color = tokens.panel, tonalElevation = 2.dp, modifier = modifier.fillMaxWidth()) {
        if (browsing) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            if (canGoBack) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = tokens.primaryText) }
            Text(if (androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f) "Weaver\nVerse" else "WeaverVerse", Modifier.weight(1f).padding(start = 8.dp), color = tokens.primaryText, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            TextButton(onClick = { menuOpen = true }) { Icon(Icons.Default.Menu, "Open navigation", tint = accent); Spacer(Modifier.width(6.dp)); Text("Modes", color = tokens.primaryText, fontFamily = FontFamily.SansSerif) }
        } else Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.Menu, "Open navigation", tint = tokens.primaryText) }
            if (canGoBack) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = tokens.primaryText) }
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(if (isHome) "WEAVERSE" else workspaceOptions.firstOrNull { it.id == workspaceId }?.label.orEmpty(), color = accent, fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.SansSerif)
                Text(if (isHome) "Home" else bookTitle.ifBlank { modeOptions.firstOrNull { it.id == modeId }?.label.orEmpty() }, color = tokens.primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, fontFamily = FontFamily.SansSerif)
            }
            IconButton(onClick = onSearch) { Icon(Icons.Default.Search, "Search", tint = tokens.primaryText) }
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings", tint = tokens.primaryText) }
        }
    }
    if (menuOpen) Dialog(onDismissRequest = { menuOpen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Compose owns this transition. A second platform fade/dim animation
        // can flash the underlying activity as the dialog surface is attached.
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(dialogWindow) {
            val previousAnimations = dialogWindow?.attributes?.windowAnimations
            val previousDim = dialogWindow?.attributes?.dimAmount
            dialogWindow?.setWindowAnimations(0)
            dialogWindow?.setDimAmount(0f)
            onDispose {
                previousAnimations?.let { dialogWindow?.setWindowAnimations(it) }
                previousDim?.let { dialogWindow?.setDimAmount(it) }
            }
        }
        var entered by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { entered = true }
        val entrance by animateFloatAsState(if (entered) 1f else 0f, tween(180), label = "navigationEntrance")
        Box(Modifier.fillMaxSize().drawBehind { drawRect(androidx.compose.ui.graphics.Color.Black.copy(alpha = .4f * entrance)) }.clickable { menuOpen = false }) {
            Surface(modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(.9f).fillMaxHeight().graphicsLayer { translationX = -size.width * (1f - entrance) }.pointerInput(Unit) { detectTapGestures {} }, color = tokens.panel, shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("WEAVERSE", color = accent, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.weight(1f), fontFamily = FontFamily.SansSerif)
                        TextButton(onClick = { menuOpen = false }) { Text("Close", fontFamily = FontFamily.SansSerif) }
                    }
                    Spacer(Modifier.height(20.dp))
                    @Composable fun entry(label: String, selected: Boolean = false, enabled: Boolean = true, action: () -> Unit) {
                        Surface(onClick = { menuOpen = false; action() }, enabled = enabled, color = if (selected) accent.copy(alpha = .14f) else androidx.compose.ui.graphics.Color.Transparent, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                val icon = when (label) {
                                    "Home" -> Icons.Default.Home
                                    "Novel", "Library", "Read" -> Icons.AutoMirrored.Filled.MenuBook
                                    "RPG", "Games" -> Icons.Default.SportsEsports
                                    "Chatting", "Chat", "Chats", "Brainstorm/Notes", "Brainstorm" -> Icons.Default.ChatBubbleOutline
                                    "Storyboard", "Pictures" -> Icons.Default.Collections
                                    "Settings" -> Icons.Default.Settings
                                    else -> Icons.Default.ChevronRight
                                }
                                Icon(icon, null, tint = if (selected) accent else tokens.secondaryText, modifier = Modifier.size(20.dp))
                                Text(label, color = if (!enabled) tokens.secondaryText else if (selected) accent else tokens.primaryText, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.padding(start = 14.dp), fontFamily = FontFamily.SansSerif)
                            }
                        }
                    }
                    entry("Home", isHome, action = onHome)
                    Text("YOUR MODES", color = tokens.secondaryText, fontSize = 10.sp, letterSpacing = 2.sp, modifier = Modifier.padding(16.dp), fontFamily = FontFamily.SansSerif)
                    workspaceOptions.forEach { option -> entry(option.label, !isHome && option.id == workspaceId) { onWorkspace(option.id) } }
                    TextButton(onClick = { menuOpen = false; arrangeMenu = ArrangeMenu.Workspaces }) { Text("Arrange modes", color = accent, fontFamily = FontFamily.SansSerif) }
                    if (!isHome) {
                        HorizontalDivider(color = tokens.hairline)
                        Text("IN THIS MODE", color = tokens.secondaryText, fontSize = 10.sp, letterSpacing = 2.sp, modifier = Modifier.padding(16.dp), fontFamily = FontFamily.SansSerif)
                        modeOptions.forEach { option -> entry(option.label, option.id == modeId && activeToolId == null) { onMode(option.id) } }
                        TextButton(onClick = { menuOpen = false; arrangeMenu = ArrangeMenu.Modes }) { Text("Arrange sections", color = accent, fontFamily = FontFamily.SansSerif) }
                    }
                    HorizontalDivider(color = tokens.hairline)
                    Text("TOOLS", color = tokens.secondaryText, fontSize = 10.sp, letterSpacing = 2.sp, modifier = Modifier.padding(16.dp), fontFamily = FontFamily.SansSerif)
                    toolOptions.forEach { option -> entry(option.label, option.id == activeToolId) { onTool(if (activeToolId == option.id) null else option.id) } }
                    entry("Library", action = onLibrary)
                    entry("Import", action = onImport)
                    entry("Export", action = onExport)
                    entry("Undo", enabled = canUndo, action = onUndo)
                    entry("Redo", enabled = canRedo, action = onRedo)
                    entry("Settings", action = onSettings)
                    // Which build this is, so a bug report can name it without digging
                    // through Android's app info.
                    Text(
                        "v${com.ihy2ln.weaverse.BuildConfig.VERSION_NAME}",
                        color = tokens.secondaryText,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.SansSerif,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                    )
                }
            }
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
