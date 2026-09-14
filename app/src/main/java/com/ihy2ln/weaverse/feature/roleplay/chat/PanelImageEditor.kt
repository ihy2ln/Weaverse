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
) {
    val bitmap = remember(editor.path) { ImageOps.loadBitmap(editor.path) }
    var version by remember(editor.path) { mutableIntStateOf(0) }
    val undoStack = remember(editor.path) { mutableStateListOf<Bitmap>() }
    val redoStack = remember(editor.path) { mutableStateListOf<Bitmap>() }
    var tool by remember { mutableStateOf(MangaEditorTool.Select) }
    var brushSize by remember { mutableFloatStateOf(28f) }
    var eraseColor by remember { mutableStateOf(android.graphics.Color.WHITE) }
    var paintColor by remember { mutableStateOf(android.graphics.Color.rgb(210, 72, 64)) }
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
    var inspectorTab by remember { mutableStateOf("type") }
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

    fun snapshot(): Bitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)

    fun restore(source: Bitmap) {
        android.graphics.Canvas(bitmap).drawBitmap(source, 0f, 0f, null)
        version++
    }

    fun pushUndo() {
        undoStack.add(snapshot())
        if (undoStack.size > 12) undoStack.removeAt(0)
        redoStack.clear()
    }

    fun brushRadius(): Float = min(bitmap.width, bitmap.height) * (brushSize / 100f) / 2f

    LaunchedEffect(editor.pendingCleanup, editor.busy, editor.regions) {
        if (!editor.pendingCleanup || editor.busy) return@LaunchedEffect
        pushUndo()
        ImageOps.inpaintTextGlyphsInRects(
            bitmap,
            editor.regions.map { region -> RectF(region.x, region.y, region.x + region.w, region.y + region.h) },
        )
        version++
        onConsumeCleanup()
    }

    val selected = editor.regions.firstOrNull { it.id == editor.selectedRegionId }

    Box(modifier = Modifier.fillMaxSize().background(EditorBg)) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(EditorPanel)
                .padding(horizontal = InkSpacing.md, vertical = InkSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InkTextButton(label = "Back", onClick = onClose)
            Column(modifier = Modifier.weight(1f).padding(horizontal = InkSpacing.sm)) {
                Text("Page editor", style = MaterialTheme.typography.titleSmall, color = Color.White)
                Text(
                    when {
                        editor.busy -> editor.status.ifBlank { "Working…" }
                        else -> editor.status.ifBlank { "Tap a tool below, then work on the page with your finger." }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = EditorMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            InkFilledButton(
                label = "Save copy",
                onClick = {
                    if (editor.regions.isNotEmpty()) {
                        ImageOps.inpaintTextGlyphsInRects(
                            bitmap,
                            editor.regions.map { region ->
                                RectF(region.x, region.y, region.x + region.w, region.y + region.h)
                            },
                        )
                    }
                    onSave(bitmap)
                },
            )
        }

        Row(
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

        Box(
            modifier = Modifier
                .weight(1f)
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
                        .fillMaxWidth()
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
                    editor.regions.forEach { region ->
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
                                    if (selectedLayer) 2.dp else 1.dp,
                                    if (selectedLayer) EditorAccent else Color(0x66FFFFFF),
                                    RoundedCornerShape(3.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            val preview = region.translation.trim()
                            if (preview.isNotBlank()) {
                                val fill = parseHexColor(region.fillHex, Color.Black)
                                if (region.writingMode == "Vertical") {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        preview.replace("\n", "").forEach { glyph ->
                                            Text(
                                                glyph.toString(),
                                                color = fill,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                } else {
                                    Text(
                                        preview,
                                        color = fill,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = when (region.alignment) {
                                            "Start" -> TextAlign.Start
                                            "End" -> TextAlign.End
                                            else -> TextAlign.Center
                                        },
                                        modifier = Modifier.padding(4.dp),
                                    )
                                }
                            }
                            if (selectedLayer && tool == MangaEditorTool.Select) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(28.dp)
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
                    EditorGestureLayer(
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
                            selected?.let { region ->
                                onUpdateRegions(
                                    editor.regions.map {
                                        if (it.id == region.id) {
                                            it.copy(fillHex = colorToHex(color), edited = true)
                                        } else {
                                            it
                                        }
                                    },
                                )
                            }
                        },
                        onRemovePreview = { removePreview = it },
                        bitmapOffset = ::bitmapOffset,
                        brushRadius = ::brushRadius,
                    )
                }
            }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(EditorPanel)
                .padding(horizontal = InkSpacing.sm, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
        ) {
            InkTextButton(
                label = "Undo",
                onClick = {
                    undoStack.removeLastOrNull()?.let {
                        redoStack.add(snapshot())
                        restore(it)
                    }
                },
            )
            InkTextButton(
                label = "Redo",
                onClick = {
                    redoStack.removeLastOrNull()?.let {
                        undoStack.add(snapshot())
                        restore(it)
                    }
                },
            )
            InkTextButton(
                label = "Fit page",
                onClick = {
                    pan = Offset.Zero
                    zoom = 1f
                },
            )
            Spacer(Modifier.weight(1f))
            InkTextButton(label = "−", onClick = { zoom = (zoom / 1.15f).coerceIn(0.4f, 4f) })
            Text("${(zoom * 100).roundToInt()}%", color = EditorMuted, fontSize = 13.sp)
            InkTextButton(label = "+", onClick = { zoom = (zoom * 1.15f).coerceIn(0.4f, 4f) })
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(EditorPanel)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = InkSpacing.sm, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EditorTool.entries.forEach { item ->
                val active = tool == item.tool
                Column(
                    modifier = Modifier
                        .widthIn(min = 64.dp)
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (active) Color(0x33E8C872) else Color(0xFF2A2A2A))
                        .clickable { tool = item.tool }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        item.icon,
                        contentDescription = item.label,
                        tint = if (active) EditorAccent else Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        item.label,
                        color = if (active) EditorAccent else Color.White,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
            }
        }

        if (tool == MangaEditorTool.Brush || tool == MangaEditorTool.Eraser || tool == MangaEditorTool.Remove) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EditorPanel)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = InkSpacing.md, vertical = InkSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
            ) {
                if (tool == MangaEditorTool.Brush) {
                    listOf(
                        android.graphics.Color.rgb(210, 72, 64),
                        android.graphics.Color.rgb(55, 115, 190),
                        android.graphics.Color.rgb(54, 145, 90),
                        android.graphics.Color.rgb(220, 160, 48),
                        android.graphics.Color.BLACK,
                        android.graphics.Color.WHITE,
                    ).forEach { color ->
                        ColorSwatch(color = color, selected = paintColor == color) { paintColor = color }
                    }
                }
                if (tool == MangaEditorTool.Eraser) {
                    listOf(
                        android.graphics.Color.WHITE to "White",
                        android.graphics.Color.BLACK to "Black",
                        android.graphics.Color.rgb(242, 230, 204) to "Cream",
                    ).forEach { (color, _) ->
                        ColorSwatch(color = color, selected = eraseColor == color) { eraseColor = color }
                    }
                }
                if (tool == MangaEditorTool.Remove) {
                    Text("Paint over the old lettering, then lift your finger to rebuild the art.", color = EditorMuted, fontSize = 13.sp)
                }
                Text("Size ${brushSize.roundToInt()}", color = EditorMuted, fontSize = 13.sp)
                Slider(
                    value = brushSize,
                    onValueChange = { brushSize = it },
                    valueRange = 8f..90f,
                    modifier = Modifier.width(160.dp).heightIn(min = 44.dp),
                )
            }
        } else {
            Text(
                when (tool) {
                    MangaEditorTool.Select -> "Drag a box to move it. The large corner handle resizes."
                    MangaEditorTool.Text -> "Tap or drag on the page to add a text box."
                    MangaEditorTool.ColorPicker -> "Tap the page to pick a color for paint or text."
                    MangaEditorTool.Pan -> "Drag with one finger to move the page. Fit page resets the view."
                    else -> ""
                },
                color = EditorMuted,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EditorPanel)
                    .padding(horizontal = InkSpacing.md, vertical = 6.dp),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp)
                .background(EditorPanel)
                .padding(horizontal = InkSpacing.md, vertical = InkSpacing.xs),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm),
                modifier = Modifier.padding(bottom = 4.dp),
            ) {
                Text(
                    "Type",
                    color = if (inspectorTab == "type") EditorAccent else EditorMuted,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { inspectorTab = "type" }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
                Text(
                    "Layers (${editor.regions.size})",
                    color = if (inspectorTab == "layers") EditorAccent else EditorMuted,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { inspectorTab = "layers" }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
            if (inspectorTab == "layers") {
                if (editor.regions.isEmpty()) {
                    Text("No text layers yet. Run Detection or use the Text tool.", color = EditorMuted, fontSize = 12.sp)
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp)) {
                        itemsIndexed(editor.regions, key = { _, region -> region.id }) { index, region ->
                            val active = region.id == editor.selectedRegionId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (active) Color(0x22E8C872) else Color.Transparent)
                                    .clickable { onSelectRegion(region.id) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    if (region.visible) "●" else "○",
                                    color = if (region.visible) EditorAccent else EditorMuted,
                                    modifier = Modifier
                                        .clickable {
                                            onUpdateRegions(
                                                editor.regions.map {
                                                    if (it.id == region.id) it.copy(visible = !it.visible) else it
                                                },
                                            )
                                        }
                                        .padding(end = 8.dp),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        region.translation.ifBlank { region.original.ifBlank { "Text ${index + 1}" } },
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        if (region.original.isBlank()) "Text" else region.original,
                                        color = EditorMuted,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                val region = selected
                if (region == null) {
                    Text("Select a text layer to edit source, translation, and type.", color = EditorMuted, fontSize = 12.sp)
                } else {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        OutlinedTextField(
                            value = region.original,
                            onValueChange = { value ->
                                onUpdateRegions(
                                    editor.regions.map {
                                        if (it.id == region.id) it.copy(original = value, edited = true) else it
                                    },
                                )
                            },
                            label = { Text("Source", fontSize = 11.sp) },
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 1,
                            maxLines = 3,
                        )
                        OutlinedTextField(
                            value = region.translation,
                            onValueChange = { value ->
                                onUpdateRegions(
                                    editor.regions.map {
                                        if (it.id == region.id) it.copy(translation = value, edited = true) else it
                                    },
                                )
                            },
                            label = { Text("Translation", fontSize = 11.sp) },
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 1,
                            maxLines = 3,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FilterChip(
                                selected = region.autoFit,
                                onClick = {
                                    onUpdateRegions(
                                        editor.regions.map {
                                            if (it.id == region.id) it.copy(autoFit = !it.autoFit, edited = true) else it
                                        },
                                    )
                                },
                                label = { Text("Auto-fit", fontSize = 11.sp) },
                            )
                            listOf("Start", "Center", "End").forEach { align ->
                                FilterChip(
                                    selected = region.alignment == align,
                                    onClick = {
                                        onUpdateRegions(
                                            editor.regions.map {
                                                if (it.id == region.id) it.copy(alignment = align, edited = true) else it
                                            },
                                        )
                                    },
                                    label = { Text(align, fontSize = 11.sp) },
                                )
                            }
                            FilterChip(
                                selected = region.writingMode == "Vertical",
                                onClick = {
                                    val next = if (region.writingMode == "Vertical") "Horizontal" else "Vertical"
                                    onUpdateRegions(
                                        editor.regions.map {
                                            if (it.id == region.id) it.copy(writingMode = next, edited = true) else it
                                        },
                                    )
                                },
                                label = { Text(if (region.writingMode == "Vertical") "Vertical" else "Horizontal", fontSize = 11.sp) },
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Fill", color = EditorMuted, fontSize = 11.sp, modifier = Modifier.padding(end = 6.dp))
                            listOf("#111111", "#FFFFFF", "#C45C5C", "#E8C872").forEach { hex ->
                                ColorSwatch(
                                    color = android.graphics.Color.parseColor(hex),
                                    selected = region.fillHex.equals(hex, ignoreCase = true),
                                ) {
                                    onUpdateRegions(
                                        editor.regions.map {
                                            if (it.id == region.id) it.copy(fillHex = hex, edited = true) else it
                                        },
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("Stroke", color = EditorMuted, fontSize = 11.sp, modifier = Modifier.padding(end = 6.dp))
                            FilterChip(
                                selected = region.strokeWidth > 0f,
                                onClick = {
                                    val next = if (region.strokeWidth > 0f) 0f else 3f
                                    onUpdateRegions(
                                        editor.regions.map {
                                            if (it.id == region.id) it.copy(strokeWidth = next, edited = true) else it
                                        },
                                    )
                                },
                                label = { Text(if (region.strokeWidth > 0f) "On" else "Off", fontSize = 11.sp) },
                            )
                            Spacer(Modifier.weight(1f))
                            InkTextButton(
                                label = "Delete",
                                onClick = {
                                    onUpdateRegions(editor.regions.filterNot { it.id == region.id })
                                    onSelectRegion(null)
                                },
                                compact = true,
                            )
                        }
                    }
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

private enum class EditorTool(
    val tool: MangaEditorTool,
    val label: String,
    val icon: ImageVector,
) {
    Select(MangaEditorTool.Select, "Move", Icons.Filled.NearMe),
    Text(MangaEditorTool.Text, "Text", Icons.Filled.TextFields),
    Brush(MangaEditorTool.Brush, "Paint", Icons.Filled.Brush),
    Eraser(MangaEditorTool.Eraser, "Erase", Icons.Filled.FormatColorReset),
    Picker(MangaEditorTool.ColorPicker, "Color", Icons.Filled.Colorize),
    Remove(MangaEditorTool.Remove, "Clean", Icons.Filled.AutoFixHigh),
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
                            onPushUndo()
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
        MangaEditorTool.Eraser -> {
            Box(
                modifier = Modifier.fillMaxSize().pointerInput(eraseColor, brushSize) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            onPushUndo()
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
                            onPushUndo()
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
                        var draggingId: String? = null
                        var resizing = false
                        detectDragGestures(
                            onDragStart = { pos ->
                                val hit = hitRegion(pos, viewSize, regionsRef.value)
                                draggingId = hit?.id
                                resizing = hit != null && isResizeHandle(pos, hit, viewSize)
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
        edited = true,
    )

private fun hitRegion(pos: Offset, viewSize: IntSize, regions: List<PanelTextRegion>): PanelTextRegion? {
    if (viewSize.width == 0 || viewSize.height == 0) return null
    val nx = pos.x / viewSize.width
    val ny = pos.y / viewSize.height
    return regions.asReversed().firstOrNull { region ->
        nx >= region.x && nx <= region.x + region.w && ny >= region.y && ny <= region.y + region.h
    }
}

private fun isResizeHandle(pos: Offset, region: PanelTextRegion, viewSize: IntSize): Boolean {
    val right = (region.x + region.w) * viewSize.width
    val bottom = (region.y + region.h) * viewSize.height
    return abs(pos.x - right) < 48f && abs(pos.y - bottom) < 48f
}

private fun colorToHex(color: Int): String =
    "#%02X%02X%02X".format(
        android.graphics.Color.red(color),
        android.graphics.Color.green(color),
        android.graphics.Color.blue(color),
    )
