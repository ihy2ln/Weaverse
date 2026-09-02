package com.ihy2ln.weaverse.feature.roleplay.chat

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ihy2ln.weaverse.core.media.ImageOps
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Full-screen picture editor for Storyboard panels: erase speech-bubble text
 * with a brush or rectangle, let the AI find the text regions (with a
 * translation into any language), erase them with one tap and add translated
 * speech-bubble overlays. Saves as new media so every reference updates.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PanelImageEditor(
    editor: PanelEditorUi,
    onSave: (Bitmap) -> Unit,
    onClose: () -> Unit,
    onFindText: () -> Unit,
    onSetLanguage: (String) -> Unit,
    onApplyRegions: (List<PanelTextRegion>) -> Unit,
) {
    val tokens = inkTokens()
    val bitmap = remember(editor.path) { ImageOps.loadBitmap(editor.path) }
    var version by remember(editor.path) { mutableIntStateOf(0) }
    val undoStack = remember(editor.path) { mutableStateListOf<Bitmap>() }
    var tool by remember { mutableStateOf("brush") } // brush | rect | regions
    var brushSize by remember { androidx.compose.runtime.mutableFloatStateOf(28f) }
    var eraseColor by remember { mutableStateOf(android.graphics.Color.WHITE) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var rectStart by remember { mutableStateOf<Offset?>(null) }
    var rectCurrent by remember { mutableStateOf<Offset?>(null) }
    var showRegions by remember { mutableStateOf(false) }
    var selectedRegions by remember(editor.regions) {
        mutableStateOf(editor.regions.map { it.original.isNotBlank() || it.translation.isNotBlank() })
    }
    var languageDraft by remember { mutableStateOf(editor.targetLanguage) }

    if (bitmap == null) {
        Column(
            modifier = Modifier.fillMaxSize().background(Color.Black).padding(InkSpacing.xl),
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

    fun pushUndo() {
        undoStack.add(bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false))
        if (undoStack.size > 8) undoStack.removeAt(0)
    }

    fun eraseAt(pos: Offset) {
        val p = bitmapOffset(pos)
        val radius = min(bitmap.width, bitmap.height) * (brushSize / 100f) / 2f
        ImageOps.eraseCircle(bitmap, p.x, p.y, radius, eraseColor)
        version++
    }

    /** Erases a normalized (0..1) x/y/width/height box on the picture. */
    fun eraseRectNormalized(x: Float, y: Float, w: Float, h: Float) {
        ImageOps.eraseRect(
            bitmap,
            RectF(
                x * bitmap.width,
                y * bitmap.height,
                (x + w) * bitmap.width,
                (y + h) * bitmap.height,
            ),
            eraseColor,
        )
        version++
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF101010))) {
        // ------------------------------------------------------------- top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF181818))
                .padding(horizontal = InkSpacing.md, vertical = InkSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InkTextButton(label = "‹ Back", onClick = onClose, compact = true)
            Column(modifier = Modifier.weight(1f).padding(start = InkSpacing.sm)) {
                Text("Picture editor", style = MaterialTheme.typography.titleSmall, color = Color.White)
                Text(
                    when {
                        editor.busy -> editor.status.ifBlank { "Working…" }
                        else -> editor.status.ifBlank { "Erase text, then add your own with Add text on the panel." }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9AA0B4),
                    maxLines = 1,
                )
            }
            InkTextButton(
                label = "Undo",
                onClick = {
                    undoStack.removeLastOrNull()?.let {
                        // The stored copy fully covers the live bitmap (same size).
                        android.graphics.Canvas(bitmap).drawBitmap(it, 0f, 0f, null)
                        version++
                    }
                },
                compact = true,
            )
            InkTextButton(label = "Save", onClick = { onSave(bitmap) }, compact = true)
        }

        // --------------------------------------------------------- picture box
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(InkSpacing.md)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF202020)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                    .onSizeChanged { viewSize = it }
                    .combinedClickable(onClick = {}, onLongClick = {}),
            ) {
                key(version) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Picture being edited",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize(),
                    )
                }                // Detected region highlights (regions tool).
                if (tool == "regions") {
                    editor.regions.forEachIndexed { index, region ->
                        val selected = selectedRegions.getOrNull(index) ?: true
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
                                    2.dp,
                                    if (selected) Color(0xFFE8C872) else Color(0x66FFFFFF),
                                    RoundedCornerShape(4.dp),
                                )
                                .combinedClickable(
                                    onClick = {
                                        if (selected) {
                                            pushUndo()
                                            eraseRectNormalized(region.x, region.y, region.w, region.h)
                                            selectedRegions = selectedRegions.mapIndexed { i, sel ->
                                                if (i == index) false else sel
                                            }
                                        }
                                    },
                                ),
                        )
                    }
                }
                // Live rect while dragging (rect tool).
                val start = rectStart
                val current = rectCurrent
                if (tool == "rect" && start != null && current != null) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(
                            color = Color(0x55E8C872),
                            topLeft = Offset(min(start.x, current.x), min(start.y, current.y)),
                            size = androidx.compose.ui.geometry.Size(
                                abs(current.x - start.x),
                                abs(current.y - start.y),
                            ),
                        )
                        drawRect(
                            color = Color(0xFFE8C872),
                            topLeft = Offset(min(start.x, current.x), min(start.y, current.y)),
                            size = androidx.compose.ui.geometry.Size(
                                abs(current.x - start.x),
                                abs(current.y - start.y),
                            ),
                            style = Stroke(2.dp.toPx()),
                        )
                    }
                }
                // Gesture layer (brush / rect).
                when (tool) {
                    "brush" -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(brushSize, eraseColor) {
                                    detectDragGestures(
                                        onDragStart = { pos ->
                                            pushUndo()
                                            eraseAt(pos)
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            eraseAt(change.position)
                                        },
                                    )
                                },
                        )
                    }
                    "rect" -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(eraseColor) {
                                    detectDragGestures(
                                        onDragStart = { pos ->
                                            pushUndo()
                                            rectStart = pos
                                            rectCurrent = pos
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            rectCurrent = change.position
                                        },
                                        onDragEnd = {
                                            val s = rectStart
                                            val e = rectCurrent
                                            if (s != null && e != null) {
                                                val a = bitmapOffset(Offset(min(s.x, e.x), min(s.y, e.y)))
                                                val b = bitmapOffset(Offset(max(s.x, e.x), max(s.y, e.y)))
                                                ImageOps.eraseRect(
                                                    bitmap,
                                                    RectF(a.x, a.y, b.x, b.y),
                                                    eraseColor,
                                                )
                                                version++
                                            }
                                            rectStart = null
                                            rectCurrent = null
                                        },
                                    )
                                },
                        )
                    }
                }
            }
        }

        // ------------------------------------------------------------- toolbox
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF181818))
                .padding(horizontal = InkSpacing.md, vertical = InkSpacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf("brush" to "Brush", "rect" to "Rect", "regions" to "AI regions").forEach { (id, label) ->
                    Text(
                        label,
                        color = if (tool == id) Color(0xFFE8C872) else Color(0xFF9AA0B4),
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (tool == id) Color(0x33E8C872) else Color.Transparent)
                            .combinedClickable(onClick = { tool = id; showRegions = id == "regions" })
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
                Spacer(Modifier.width(InkSpacing.sm))
                // Erase color swatches: bubble white, ink black, page cream.
                listOf(
                    android.graphics.Color.WHITE to "White",
                    android.graphics.Color.BLACK to "Black",
                    android.graphics.Color.rgb(242, 230, 204) to "Cream",
                ).forEach { (color, label) ->
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(
                                when (color) {
                                    android.graphics.Color.WHITE -> Color.White
                                    android.graphics.Color.BLACK -> Color.Black
                                    else -> Color(0xFFF2E6CC)
                                },
                            )
                            .border(
                                if (eraseColor == color) 2.dp else 1.dp,
                                if (eraseColor == color) Color(0xFFE8C872) else Color.Gray,
                                CircleShape,
                            )
                            .combinedClickable(onClick = { eraseColor = color }),
                    )
                }
                Spacer(Modifier.width(InkSpacing.sm))
                Text(
                    "Size ${brushSize.roundToInt()}",
                    color = Color(0xFF9AA0B4),
                    fontSize = 12.sp,
                    modifier = Modifier.width(52.dp),
                )
                Slider(
                    value = brushSize,
                    onValueChange = { brushSize = it },
                    valueRange = 8f..90f,
                    modifier = Modifier.width(120.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = languageDraft,
                    onValueChange = { languageDraft = it },
                    singleLine = true,
                    label = { Text("Translate into", fontSize = 11.sp) },
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                    modifier = Modifier.width(150.dp),
                )
                InkTextButton(
                    label = "Read & translate",
                    onClick = {
                        onSetLanguage(languageDraft)
                        onFindText()
                        tool = "regions"
                    },
                    compact = true,
                )
                InkTextButton(
                    label = "Erase found text",
                    onClick = {
                        pushUndo()
                        editor.regions.forEach { region ->
                            eraseRectNormalized(region.x, region.y, region.w, region.h)
                        }
                    },
                    compact = true,
                )
                InkTextButton(
                    label = "Add translations",
                    onClick = {
                        val selected = editor.regions.filterIndexed { i, _ -> selectedRegions.getOrNull(i) ?: true }
                        if (selected.isNotEmpty()) {
                            onApplyRegions(selected)
                            editor.regions.forEach { region ->
                                eraseRectNormalized(region.x, region.y, region.w, region.h)
                            }
                        }
                    },
                    compact = true,
                )
                if (editor.regions.isNotEmpty()) {
                    InkTextButton(
                        label = if (showRegions) "Hide list" else "Region list",
                        onClick = { showRegions = !showRegions },
                        compact = true,
                    )
                }
            }
            if (showRegions && editor.regions.isNotEmpty()) {
                LazyColumn(modifier = Modifier.fillMaxWidth().height(150.dp).padding(top = InkSpacing.xxs)) {
                    items(editor.regions.size) { index ->
                        val region = editor.regions[index]
                        val selected = selectedRegions.getOrNull(index) ?: true
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = selected,
                                onCheckedChange = { checked ->
                                    selectedRegions = selectedRegions.mapIndexed { i, sel ->
                                        if (i == index) checked else sel
                                    }
                                },
                                modifier = Modifier.size(30.dp),
                            )
                            Column {
                                Text(
                                    "Original: ${region.original.ifBlank { "(decorative)" }}",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                )
                                Text(
                                    "Translation: ${region.translation.ifBlank { "-" }}",
                                    color = Color(0xFFE8C872),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (editor.busy) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x66000000)),
                contentAlignment = Alignment.Center,
            ) {
                Text("Reading the picture…", color = Color.White)
            }
        }
    }
}
