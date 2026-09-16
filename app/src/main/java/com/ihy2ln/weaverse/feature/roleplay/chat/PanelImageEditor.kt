package com.ihy2ln.weaverse.feature.roleplay.chat

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.FormatColorReset
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.widthIn
import com.ihy2ln.weaverse.core.media.ImageOps
import com.ihy2ln.weaverse.ai.ModelInfo
import com.ihy2ln.weaverse.core.ui.components.InkFilledButton
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.core.ui.components.OverlayLettering
import com.ihy2ln.weaverse.core.ui.components.TextOverlayLayer
import com.ihy2ln.weaverse.core.ui.components.TextOverlayEditSheet
import com.ihy2ln.weaverse.core.text.TextOverlay
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.util.parseHexColor
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val EditorBg = Color(0xFF101010)
private val EditorPanel = Color(0xFF181818)
private val EditorCanvas = Color(0xFF202020)
private val EditorAccent = Color(0xFFE8C872)
private val EditorMuted = Color(0xFF9AA0B4)
/** Regions the verifier still found source lettering in. */
private val EditorReview = Color(0xFFFF6B6B)

/** Drawn size of the resize grip, and the reach of its touch target. */
private val ResizeHandleSize = 28.dp
private val RemoveMask = Color(0x88C45CFF)

@Composable
internal fun ModelChoice(
    label: String,
    models: List<ModelInfo>,
    selectedRef: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelected: (String) -> Unit,
) {
    val selectedId = selectedRef.removePrefix("openrouter/")
    val selected = models.firstOrNull { it.id == selectedId }
    Box {
        InkTextButton(
            label = "$label: ${selected?.displayName ?: "Auto"}",
            onClick = { onExpandedChange(true) },
            enabled = models.isNotEmpty(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.heightIn(max = 360.dp),
        ) {
            models.forEach { model ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(model.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(model.id, style = MaterialTheme.typography.labelSmall, color = EditorMuted)
                        }
                    },
                    onClick = {
                        onSelected(model.id)
                        onExpandedChange(false)
                    },
                )
            }
        }
    }
}

/**
 * Mobile page editor inspired by Koharu's translation workflow.
 * Tools are labeled for touch; there are no keyboard shortcuts.
 * Save always writes a new picture file and leaves the original on disk.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PanelImageEditor(
    editor: PanelEditorUi,
    visionModels: List<ModelInfo>,
    textModels: List<ModelInfo>,
    visionModelRef: String,
    textModelRef: String,
    onVisionModelSelected: (String) -> Unit,
    onTextModelSelected: (String) -> Unit,
    onSave: (Bitmap) -> Unit,
    onClose: () -> Unit,
    onRunPipeline: (Set<MangaProcessStage>) -> Unit,
    onSetLanguage: (String) -> Unit,
    onUpdateRegions: (List<PanelTextRegion>) -> Unit,
    onSelectRegion: (String?) -> Unit,
    onConsumeCleanup: () -> Unit,
    startWithCleanup: Boolean = false,
) {
    val bitmap = remember(editor.path) { ImageOps.loadBitmap(editor.path) }
    var optionsOpen by remember(editor.path) { mutableStateOf(false) }
    var aiOptions by remember(editor.path) { mutableStateOf(false) }
    var pickingCleanupColor by remember { mutableStateOf(false) }
    var customColorOpen by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    val initialRegions = remember(editor.path) { editor.regions }
    var version by remember(editor.path) { mutableIntStateOf(0) }
    fun closeEditor() {
        if (editor.regions != initialRegions || version > 0) confirmClose = true else onClose()
    }
    androidx.activity.compose.BackHandler {
        if (optionsOpen) optionsOpen = false else closeEditor()
    }
    if (confirmClose) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirmClose = false },
        title = { Text("Unsaved page edits") },
        text = { Text("Save a copy before leaving, or discard this editing session. The original is unchanged.") },
        confirmButton = { TextButton(onClick = { confirmClose = false; onClose() }) { Text("Discard edits") } },
        dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("Keep editing") } },
    )

    var editingTextId by remember(editor.path) { mutableStateOf<String?>(null) }
    var tool by remember { mutableStateOf(if (startWithCleanup) MangaEditorTool.Remove else MangaEditorTool.Select) }
    var brushSize by remember { mutableFloatStateOf(3f) }
    var eraseColor by remember { mutableStateOf(android.graphics.Color.WHITE) }
    var paintColor by remember { mutableStateOf(if (startWithCleanup) android.graphics.Color.WHITE else android.graphics.Color.rgb(210, 72, 64)) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var languageDraft by remember(editor.targetLanguage) { mutableStateOf(editor.targetLanguage) }
    var stages by remember {
        mutableStateOf(
            setOf(
                MangaProcessStage.Detection,
                MangaProcessStage.Ocr,
                MangaProcessStage.Translation,
                MangaProcessStage.Proofreading,
                MangaProcessStage.Cleanup,
            ),
        )
    }
    // The Type panel is tall. Opening on the compact Layers list keeps the page visible on a
    // phone, which matters because a translated page arrives with a layer already selected.
    var inspectorTab by remember { mutableStateOf("layers") }
    var frameStart by remember { mutableStateOf<Offset?>(null) }
    var frameCurrent by remember { mutableStateOf<Offset?>(null) }
    var removePreview by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var visionMenuOpen by remember { mutableStateOf(false) }
    var textMenuOpen by remember { mutableStateOf(false) }

    if (bitmap == null) {
        Column(
            modifier = Modifier.fillMaxSize().background(EditorBg).padding(InkSpacing.xl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Could not load that picture.", color = Color.White)
            TextButton(onClick = onClose) { Text("Close") }
        }
        return
    }

    fun bitmapOffset(pos: Offset): Offset {
        if (viewSize.width == 0 || viewSize.height == 0) return Offset.Zero
        return Offset(
            (pos.x / viewSize.width * bitmap.width).coerceIn(0f, bitmap.width.toFloat()),
            (pos.y / viewSize.height * bitmap.height).coerceIn(0f, bitmap.height.toFloat()),
        )
    }

    val cleanupDraft = remember(editor.path, editor.originalPath) { CleanupDraft(bitmap, editor.originalPath) }

    val history = remember(editor.path) { PageEditHistory(bitmap, cleanupDraft.mask) }
    androidx.compose.runtime.DisposableEffect(history) { onDispose { history.dispose() } }
    fun pushUndo() { history.checkpoint(editor.regions); version++ }
    fun undoEdit() { history.undo(editor.regions)?.let { onUpdateRegions(it); cleanupDraft.revision++; version++ } }
    fun redoEdit() { history.redo(editor.regions)?.let { onUpdateRegions(it); cleanupDraft.revision++; version++ } }


    fun updateLettering(id: String, update: (TextOverlay) -> TextOverlay) {
        pushUndo()
        onUpdateRegions(editor.regions.map { region ->
            if (region.id != id) region else update(region.toEditableOverlay()).toPanelTextRegion()
                .copy(original = region.original, visible = region.visible, reviewRequired = region.reviewRequired, edited = true)
        })
    }

    editor.regions.firstOrNull { it.id == editingTextId }?.let { region ->
        TextOverlayEditSheet(region.toEditableOverlay(), onDismiss = { editingTextId = null },
            onSave = { value -> updateLettering(region.id) { value } },
            onDelete = { pushUndo(); onUpdateRegions(editor.regions.filterNot { it.id == region.id }) })
    }

    fun brushRadius(): Float = min(bitmap.width, bitmap.height) * (brushSize / 100f) / 2f

    LaunchedEffect(editor.pendingCleanup, editor.busy, editor.regions) {
        if (!editor.pendingCleanup || editor.busy) return@LaunchedEffect
        pushUndo()
        ImageOps.replaceTextRegions(bitmap, editor.regions.cleanableRects())
        version++
        onConsumeCleanup()
    }

    val selected = editor.regions.firstOrNull { it.id == editor.selectedRegionId }

    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = EditorAccent, onSurface = Color.White,
        onSurfaceVariant = Color.LightGray, surface = EditorPanel)) {
    Box(modifier = Modifier.fillMaxSize().background(EditorBg)) {
    Column(modifier = Modifier.fillMaxSize()) {
        androidx.compose.material3.Surface(color = Color.White, contentColor = Color.Black) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = ::closeEditor) { Text("Read", color = Color.Black) }
                Text("EDIT", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = ::undoEdit, enabled = history.canUndo && !editor.busy,
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = Color.Black, disabledContentColor = Color.Gray)) { Text("Undo") }
                TextButton(onClick = ::redoEdit, enabled = history.canRedo && !editor.busy,
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = Color.Black, disabledContentColor = Color.Gray)) { Text("Redo") }
                TextButton(onClick = { onSave(bitmap) }, enabled = !editor.busy,
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = Color.Black)) { Text("Save") }
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                // Whiteout, brush and drag all happen here, so the page keeps a workable
                // height even when the inspector below is expanded.
                .heightIn(min = 180.dp)
                .fillMaxWidth()
                .padding(InkSpacing.sm)
                .clip(RoundedCornerShape(8.dp))
                .background(EditorCanvas),
            contentAlignment = Alignment.Center,
        ) {
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = pan.x
                            translationY = pan.y
                            scaleX = zoom
                            scaleY = zoom
                        }
                        .width(minOf(maxWidth, maxHeight * (bitmap.width.toFloat() / bitmap.height)))
                        .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                        .onSizeChanged { viewSize = it },
                ) {
                    key(version) {
                        androidx.compose.foundation.Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Page being edited",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (tool == MangaEditorTool.Select) {
                        TextOverlayLayer(editor.regions.filter { it.visible }.map { it.toEditableOverlay() }, true,
                            onMove = { id, x, y -> updateLettering(id) { it.copy(xPercent = x, yPercent = y) } },
                            onResize = { id, x, y, w, h -> updateLettering(id) {
                                it.copy(xPercent = x, yPercent = y, widthPercent = w, heightPercent = h)
                            } },
                            onTap = { editingTextId = it }, onSelected = onSelectRegion,
                            externalSelectedId = editor.selectedRegionId, onSelectionCleared = { onSelectRegion(null) })
                    }
                    editor.regions.forEach { region ->
                        if (tool == MangaEditorTool.Select) return@forEach
                        if (!region.visible) return@forEach
                        val selectedLayer = region.id == editor.selectedRegionId
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(region.w.coerceAtLeast(0.02f))
                                .fillMaxHeight(region.h.coerceAtLeast(0.02f))
                                .offset {
                                    IntOffset(
                                        (region.x * viewSize.width).roundToInt(),
                                        (region.y * viewSize.height).roundToInt(),
                                    )
                                }
                                .border(
                                    // An unresolved region is the one thing the user must act
                                    // on, so it outranks selection in the outline.
                                    if (region.reviewRequired || selectedLayer) 2.dp else 1.dp,
                                    when {
                                        region.reviewRequired -> EditorReview
                                        selectedLayer -> EditorAccent
                                        else -> Color(0x66FFFFFF)
                                    },
                                    RoundedCornerShape(3.dp),
                                )
                                .then(
                                    if (region.reviewRequired && tool != MangaEditorTool.Select) {
                                        Modifier.background(EditorReview.copy(alpha = 0.14f), RoundedCornerShape(3.dp))
                                    } else {
                                        Modifier
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            val preview = region.translation.trim()
                            if (preview.isNotBlank()) {
                                OverlayLettering(region.toEditableOverlay(), viewSize.width.toFloat(),
                                    Modifier.fillMaxSize(), showOverflow = selectedLayer)
                            }
                            if (selectedLayer && tool == MangaEditorTool.Select) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(ResizeHandleSize)
                                        .background(EditorAccent, RoundedCornerShape(6.dp)),
                                )
                            }
                        }
                    }
                    val start = frameStart
                    val current = frameCurrent
                    if ((tool == MangaEditorTool.Text) && start != null && current != null) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val left = min(start.x, current.x)
                            val top = min(start.y, current.y)
                            drawRect(
                                color = Color(0x55E8C872),
                                topLeft = Offset(left, top),
                                size = androidx.compose.ui.geometry.Size(
                                    abs(current.x - start.x),
                                    abs(current.y - start.y),
                                ),
                            )
                        }
                    }
                    if (removePreview.isNotEmpty()) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val radius = (min(size.width, size.height) * (brushSize / 100f) / 2f)
                            removePreview.forEach { point ->
                                drawCircle(RemoveMask, radius, point)
                            }
                        }
                    }
                    if (tool == MangaEditorTool.Remove) CleanupMaskLayer(cleanupDraft, viewSize, brushRadius(), beforeStroke = ::pushUndo)
                    if (tool != MangaEditorTool.Select && tool != MangaEditorTool.Remove) EditorGestureLayer(
                        tool = tool,
                        brushSize = brushSize,
                        eraseColor = eraseColor,
                        paintColor = paintColor,
                        bitmap = bitmap,
                        viewSize = viewSize,
                        regions = editor.regions,
                        onPan = { pan += it },
                        onPushUndo = ::pushUndo,
                        onMutated = { version++ },
                        onSelect = onSelectRegion,
                        onUpdateRegions = onUpdateRegions,
                        onFrameStart = { pos -> frameStart = pos; frameCurrent = pos },
                        onFrameDrag = { frameCurrent = it },
                        onFrameEnd = {
                            val s = frameStart
                            val e = frameCurrent
                            frameStart = null
                            frameCurrent = null
                            if (s == null || e == null || viewSize.width == 0) return@EditorGestureLayer
                            val left = min(s.x, e.x) / viewSize.width
                            val top = min(s.y, e.y) / viewSize.height
                            val width = abs(e.x - s.x) / viewSize.width
                            val height = abs(e.y - s.y) / viewSize.height
                            pushUndo()
                            tool = MangaEditorTool.Select
                            if (width < 0.02f || height < 0.02f) {
                                val nx = (s.x / viewSize.width).coerceIn(0.05f, 0.85f)
                                val ny = (s.y / viewSize.height).coerceIn(0.05f, 0.85f)
                                val created = newTextLayer(nx, ny, 0.18f, 0.10f)
                                onUpdateRegions(editor.regions + created)
                                onSelectRegion(created.id)
                            } else {
                                val created = newTextLayer(left, top, width.coerceAtMost(0.9f), height.coerceAtMost(0.9f))
                                onUpdateRegions(editor.regions + created)
                                onSelectRegion(created.id)
                            }
                        },
                        onSampleColor = { color ->
                            paintColor = color
                            if (pickingCleanupColor) {
                                pickingCleanupColor = false
                                tool = MangaEditorTool.Remove
                                optionsOpen = false
                            } else { tool = MangaEditorTool.Brush }
                        },
                        onRemovePreview = { removePreview = it },
                        bitmapOffset = ::bitmapOffset,
                        brushRadius = ::brushRadius,
                    )
                }
            }

        // Tool properties stay next to the canvas, not on a separate screen.
        Column(Modifier.fillMaxWidth().background(EditorPanel)) {
            if (tool in listOf(MangaEditorTool.Brush, MangaEditorTool.Whiteout, MangaEditorTool.Eraser, MangaEditorTool.Remove)) {
                CompactPaintControls(
                    color = if (tool == MangaEditorTool.Eraser) eraseColor else paintColor, size = brushSize,
                    onColor = { if (tool == MangaEditorTool.Eraser) eraseColor = it else paintColor = it },
                    onSize = { brushSize = it }, onCustomColor = { customColorOpen = true })
                if (tool == MangaEditorTool.Remove) CleanupMaskTools(cleanupDraft, paintColor,
                    before = ::pushUndo, changed = { version++ }, cleanupRects = editor.regions.cleanableRects(), unifiedHistory = true)
            } else {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    TextButton(onClick = {
                        pushUndo()
                        val created = newTextLayer(.35f, .4f, .3f, .15f)
                        onUpdateRegions(editor.regions + created); onSelectRegion(created.id)
                        tool = MangaEditorTool.Select; editingTextId = created.id
                    }) { Text("Add text", color = Color.White) }
                    TextButton(enabled = selected != null, onClick = { editingTextId = selected?.id }) { Text("Format text", color = if (selected != null) Color.White else EditorMuted) }
                    TextButton(onClick = { pan = Offset.Zero; zoom = 1f }) { Text("Fit page", color = Color.White) }
                    TextButton(onClick = { zoom = (zoom / 1.15f).coerceIn(.4f, 4f) }) { Text("−", color = Color.White) }
                    TextButton(onClick = { zoom = (zoom * 1.15f).coerceIn(.4f, 4f) }) { Text("+", color = Color.White) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(EditorTool.Select, EditorTool.Brush, EditorTool.Remove, EditorTool.Pan).forEach { item ->
                    val active = tool == item.tool
                    Column(Modifier.weight(1f).heightIn(min = 56.dp)
                        .background(if (active) Color(0x33E8C872) else Color.Transparent)
                        .clickable { tool = item.tool; onSelectRegion(null) }.padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(item.icon, contentDescription = null, tint = if (active) EditorAccent else Color.White, modifier = Modifier.size(22.dp))
                        Text(item.label, color = if (active) EditorAccent else Color.White, fontSize = 12.sp)
                    }
                }
                TextButton(onClick = { aiOptions = false; optionsOpen = true }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                    Text(if (editor.regions.any { it.reviewRequired }) "Layers !" else "Layers", color = Color.White, fontSize = 12.sp)
                }
            }
        }
        }

        if (customColorOpen) androidx.compose.material3.AlertDialog(
            onDismissRequest = { customColorOpen = false },
            containerColor = EditorPanel,
            title = { Text("Brush color & size", color = Color.White) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    CleanupBrushControls(if (tool == MangaEditorTool.Eraser) eraseColor else paintColor, brushSize,
                        onColor = { if (tool == MangaEditorTool.Eraser) eraseColor = it else paintColor = it },
                        onSize = { brushSize = it }, onEyedropper = {
                            pickingCleanupColor = tool == MangaEditorTool.Remove
                            tool = MangaEditorTool.ColorPicker; customColorOpen = false
                        })
                }
            },
            confirmButton = { TextButton(onClick = { customColorOpen = false }) { Text("Done") } })

        if (optionsOpen) {
            androidx.compose.material3.ModalBottomSheet(onDismissRequest = { optionsOpen = false },
                sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = EditorPanel) {
                Column(Modifier.fillMaxWidth().heightIn(max = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp * .65f)
                    .verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Layers & review", color = Color.White, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        TextButton(onClick = ::undoEdit, enabled = history.canUndo) { Text("Undo") }
                        TextButton(onClick = { optionsOpen = false }) { Text("Close") }
                    }
                    if (editor.status.isNotBlank()) Text(editor.status, color = EditorMuted, fontSize = 12.sp)
                    Text("Translated and added text use the same layers. Tap a layer to move it on the page, or Format to change its wording and style.",
                        color = EditorMuted, fontSize = 12.sp)
                    editor.regions.forEachIndexed { index, region ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = {
                                onSelectRegion(region.id); tool = MangaEditorTool.Select; optionsOpen = false
                            }, modifier = Modifier.weight(1f)) {
                                Text((if (region.reviewRequired) "⚠ " else "") + region.translation.ifBlank { region.original.ifBlank { "Text ${index + 1}" } },
                                    color = if (region.reviewRequired) EditorReview else Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            TextButton(onClick = { onSelectRegion(region.id); editingTextId = region.id; optionsOpen = false }) { Text("Format") }
                            if (region.reviewRequired) TextButton(onClick = {
                                pushUndo(); onUpdateRegions(editor.regions.map { if (it.id == region.id) it.copy(reviewRequired = false, edited = true) else it })
                            }) { Text("Reviewed") }
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        TextButton(enabled = selected != null, onClick = {
                            pushUndo()
                            val adjusted = MangaLetteringPlacement.constrain(editor.regions.map {
                                it.copy(edited = it.id != selected?.id)
                            }, bitmap.width.toFloat() / bitmap.height)
                            onUpdateRegions(editor.regions.map { region ->
                                if (region.id == selected?.id) adjusted.first { it.id == region.id }.copy(edited = true) else region
                            })
                        }) { Text("Re-layout selected text") }
                        TextButton(onClick = { tool = MangaEditorTool.Whiteout; optionsOpen = false }) { Text("Whiteout") }
                        TextButton(onClick = { tool = MangaEditorTool.Eraser; optionsOpen = false }) { Text("Erase / fill") }
                        TextButton(onClick = { tool = MangaEditorTool.Text; optionsOpen = false }) { Text("Draw text box") }
                        TextButton(onClick = { aiOptions = !aiOptions }) { Text("AI tools") }
                    }
        if (aiOptions) Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(EditorPanel)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = InkSpacing.md, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
        ) {
            ModelChoice(
                label = "Vision / OCR",
                models = visionModels,
                selectedRef = visionModelRef,
                expanded = visionMenuOpen,
                onExpandedChange = { visionMenuOpen = it },
                onSelected = onVisionModelSelected,
            )
            ModelChoice(
                label = "Translation",
                models = textModels,
                selectedRef = textModelRef,
                expanded = textMenuOpen,
                onExpandedChange = { textMenuOpen = it },
                onSelected = onTextModelSelected,
            )
            MangaProcessStage.entries.forEach { stage ->
                val selectedStage = stage in stages
                FilterChip(
                    selected = selectedStage,
                    onClick = {
                        stages = if (selectedStage) {
                            if (stages.size == 1) stages else stages - stage
                        } else {
                            stages + stage
                        }
                    },
                    label = {
                        Text(
                            when (stage) {
                                MangaProcessStage.Detection -> "Find text"
                                MangaProcessStage.Ocr -> "Read"
                                MangaProcessStage.Translation -> "Translate"
                                MangaProcessStage.Proofreading -> "Proofread"
                                MangaProcessStage.Cleanup -> "Clean art"
                            },
                            fontSize = 13.sp,
                        )
                    },
                )
            }
            OutlinedTextField(
                value = languageDraft,
                onValueChange = { languageDraft = it },
                singleLine = true,
                label = { Text("Language", fontSize = 12.sp) },
                textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                modifier = Modifier.width(128.dp).heightIn(min = 48.dp),
            )
            InkTextButton(
                label = if (editor.busy) "Working" else "Go",
                onClick = {
                    onSetLanguage(languageDraft)
                    onRunPipeline(stages)
                },
                enabled = !editor.busy && stages.isNotEmpty(),
            )
        }
                }
            }
        }

        if (editor.busy) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0x66000000)),
                contentAlignment = Alignment.Center,
            ) {
                Text(editor.status.ifBlank { "Processing the page…" }, color = Color.White)
            }
        }
    }
    }
}

private enum class EditorTool(
    val tool: MangaEditorTool,
    val label: String,
    val icon: ImageVector,
) {
    Select(MangaEditorTool.Select, "Text", Icons.Filled.TextFields),
    Text(MangaEditorTool.Text, "Text", Icons.Filled.TextFields),
    Brush(MangaEditorTool.Brush, "Paint", Icons.Filled.Brush),
    Whiteout(MangaEditorTool.Whiteout, "Whiteout", Icons.Filled.FormatColorReset),
    Eraser(MangaEditorTool.Eraser, "Erase", Icons.Filled.FormatColorReset),
    Picker(MangaEditorTool.ColorPicker, "Color", Icons.Filled.Colorize),
    Remove(MangaEditorTool.Remove, "Cleanup", Icons.Filled.AutoFixHigh),
    Pan(MangaEditorTool.Pan, "Hand", Icons.Filled.PanTool),
}

@Composable
private fun ColorSwatch(color: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color(color))
            .border(if (selected) 3.dp else 1.dp, if (selected) EditorAccent else Color.Gray, CircleShape)
            .clickable(onClick = onClick),
    )
}

@Composable
private fun EditorGestureLayer(
    tool: MangaEditorTool,
    brushSize: Float,
    eraseColor: Int,
    paintColor: Int,
    bitmap: Bitmap,
    viewSize: IntSize,
    regions: List<PanelTextRegion>,
    onPan: (Offset) -> Unit,
    onPushUndo: () -> Unit,
    onMutated: () -> Unit,
    onSelect: (String?) -> Unit,
    onUpdateRegions: (List<PanelTextRegion>) -> Unit,
    onFrameStart: (Offset) -> Unit,
    onFrameDrag: (Offset) -> Unit,
    onFrameEnd: () -> Unit,
    onSampleColor: (Int) -> Unit,
    onRemovePreview: (List<Offset>) -> Unit,
    bitmapOffset: (Offset) -> Offset,
    brushRadius: () -> Float,
) {
    val pushUndoNow by rememberUpdatedState(onPushUndo)
    when (tool) {
        MangaEditorTool.Pan -> {
            Box(
                modifier = Modifier.fillMaxSize().pointerInput(tool) {
                    detectDragGestures { change, amount ->
                        change.consume()
                        onPan(amount)
                    }
                },
            )
        }
        MangaEditorTool.Brush -> {
            Box(
                modifier = Modifier.fillMaxSize().pointerInput(paintColor, brushSize) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            pushUndoNow()
                            val p = bitmapOffset(pos)
                            ImageOps.paintCircle(bitmap, p.x, p.y, brushRadius(), paintColor)
                            onMutated()
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val p = bitmapOffset(change.position)
                            ImageOps.paintCircle(bitmap, p.x, p.y, brushRadius(), paintColor)
                            onMutated()
                        },
                    )
                },
            )
        }
        MangaEditorTool.Whiteout -> {
            Box(
                modifier = Modifier.fillMaxSize().pointerInput(brushSize) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            pushUndoNow()
                            val p = bitmapOffset(pos)
                            ImageOps.eraseCircle(bitmap, p.x, p.y, brushRadius(), android.graphics.Color.WHITE)
                            onMutated()
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val p = bitmapOffset(change.position)
                            ImageOps.eraseCircle(bitmap, p.x, p.y, brushRadius(), android.graphics.Color.WHITE)
                            onMutated()
                        },
                    )
                },
            )
        }
        MangaEditorTool.Eraser -> {
            Box(
                modifier = Modifier.fillMaxSize().pointerInput(eraseColor, brushSize) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            pushUndoNow()
                            val p = bitmapOffset(pos)
                            ImageOps.eraseCircle(bitmap, p.x, p.y, brushRadius(), eraseColor)
                            onMutated()
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val p = bitmapOffset(change.position)
                            ImageOps.eraseCircle(bitmap, p.x, p.y, brushRadius(), eraseColor)
                            onMutated()
                        },
                    )
                },
            )
        }
        MangaEditorTool.Remove -> {
            var mask by remember { mutableStateOf<BooleanArray?>(null) }
            var preview by remember { mutableStateOf(listOf<Offset>()) }
            Box(
                modifier = Modifier.fillMaxSize().pointerInput(brushSize) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            pushUndoNow()
                            mask = BooleanArray(bitmap.width * bitmap.height)
                            preview = listOf(pos)
                            onRemovePreview(preview)
                            val p = bitmapOffset(pos)
                            ImageOps.markCircleMask(mask!!, bitmap.width, bitmap.height, p.x, p.y, brushRadius())
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            preview = preview + change.position
                            onRemovePreview(preview)
                            val p = bitmapOffset(change.position)
                            mask?.let { ImageOps.markCircleMask(it, bitmap.width, bitmap.height, p.x, p.y, brushRadius()) }
                        },
                        onDragEnd = {
                            mask?.let { ImageOps.inpaintMasked(bitmap, it) }
                            mask = null
                            preview = emptyList()
                            onRemovePreview(emptyList())
                            onMutated()
                        },
                    )
                },
            )
        }
        MangaEditorTool.ColorPicker -> {
            Box(
                modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTapGestures { pos ->
                        val p = bitmapOffset(pos)
                        val x = p.x.roundToInt().coerceIn(0, bitmap.width - 1)
                        val y = p.y.roundToInt().coerceIn(0, bitmap.height - 1)
                        onSampleColor(bitmap.getPixel(x, y))
                    }
                },
            )
        }
        MangaEditorTool.Text -> {
            Box(
                modifier = Modifier.fillMaxSize().pointerInput(viewSize) {
                    detectDragGestures(
                        onDragStart = onFrameStart,
                        onDrag = { change, _ ->
                            change.consume()
                            onFrameDrag(change.position)
                        },
                        onDragEnd = onFrameEnd,
                    )
                },
            )
        }
        MangaEditorTool.Select -> {
            val regionsRef = remember { mutableStateOf(regions) }
            regionsRef.value = regions
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(viewSize) {
                        detectTapGestures { pos ->
                            onSelect(hitRegion(pos, viewSize, regionsRef.value)?.id)
                        }
                    }
                    .pointerInput(viewSize) {
                        val handlePx = ResizeHandleSize.toPx()
                        var draggingId: String? = null
                        var resizing = false
                        detectDragGestures(
                            onDragStart = { pos ->
                                val hit = hitRegion(pos, viewSize, regionsRef.value)
                                draggingId = hit?.id
                                resizing = hit != null && isResizeHandle(pos, hit, viewSize, handlePx)
                                onSelect(hit?.id)
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                val id = draggingId ?: return@detectDragGestures
                                if (viewSize.width == 0 || viewSize.height == 0) return@detectDragGestures
                                onUpdateRegions(
                                    regionsRef.value.map { region ->
                                        if (region.id != id) return@map region
                                        if (resizing) {
                                            region.copy(
                                                w = (region.w + amount.x / viewSize.width).coerceIn(0.04f, 0.95f),
                                                h = (region.h + amount.y / viewSize.height).coerceIn(0.03f, 0.95f),
                                            )
                                        } else {
                                            region.copy(
                                                x = (region.x + amount.x / viewSize.width).coerceIn(0f, 0.95f),
                                                y = (region.y + amount.y / viewSize.height).coerceIn(0f, 0.95f),
                                            )
                                        }
                                    },
                                )
                            },
                            onDragEnd = { draggingId = null; resizing = false },
                        )
                    },
            )
        }
    }
}

private fun newTextLayer(x: Float, y: Float, w: Float, h: Float): PanelTextRegion =
    PanelTextRegion(
        id = "t-${UUID.randomUUID()}",
        x = x.coerceIn(0f, 0.95f),
        y = y.coerceIn(0f, 0.95f),
        w = w,
        h = h,
        original = "",
        translation = "Text",
        cleanupEnabled = false,
        edited = true,
        backingOverlay = TextOverlay(id = "", text = "Text", source = "page-editor", backgroundHex = null, backgroundAlpha = 0f, cleanupEnabled = false),
    )

private fun List<PanelTextRegion>.cleanableRects(): List<RectF> =
    filter { region ->
        region.visible && region.cleanupEnabled && (region.original.isNotBlank() ||
            (region.cleanupW > 0f && region.cleanupH > 0f) || (!region.edited && region.translation.isNotBlank()))
    }.map(PanelTextRegion::cleanupRect)

private fun hitRegion(pos: Offset, viewSize: IntSize, regions: List<PanelTextRegion>): PanelTextRegion? {
    if (viewSize.width == 0 || viewSize.height == 0) return null
    val nx = pos.x / viewSize.width
    val ny = pos.y / viewSize.height
    return regions.asReversed().firstOrNull { region ->
        nx >= region.x && nx <= region.x + region.w && ny >= region.y && ny <= region.y + region.h
    }
}

/**
 * The handle is drawn at [ResizeHandleSize]; its touch target has to match, or on a dense phone
 * screen the user grabs what looks like the handle and moves the layer instead of resizing it.
 * On a small box the corner is capped so dragging the middle still moves it.
 */
private fun isResizeHandle(
    pos: Offset,
    region: PanelTextRegion,
    viewSize: IntSize,
    handlePx: Float,
): Boolean {
    val right = (region.x + region.w) * viewSize.width
    val bottom = (region.y + region.h) * viewSize.height
    val reach = minOf(
        handlePx,
        region.w * viewSize.width * 0.45f,
        region.h * viewSize.height * 0.45f,
    ).coerceAtLeast(24f)
    return abs(pos.x - right) < reach && abs(pos.y - bottom) < reach
}

private fun colorToHex(color: Int): String =
    "#%02X%02X%02X".format(
        android.graphics.Color.red(color),
        android.graphics.Color.green(color),
        android.graphics.Color.blue(color),
    )
