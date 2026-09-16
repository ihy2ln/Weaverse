package com.ihy2ln.weaverse.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ihy2ln.weaverse.core.text.TextOverlay
import com.ihy2ln.weaverse.core.text.TextOverlayStyle
import com.ihy2ln.weaverse.core.text.OverlayFrame
import com.ihy2ln.weaverse.core.text.OverlayGeometry
import com.ihy2ln.weaverse.core.ui.util.parseHexColor
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Renders draggable/resizable [TextOverlay]s on top of a panel's media.
 * Positions/sizes are percent-of-panel so they stay put across panel resizes.
 */
@Composable
fun TextOverlayLayer(
    overlays: List<TextOverlay>,
    editable: Boolean,
    onMove: (id: String, xPercent: Float, yPercent: Float) -> Unit,
    onResize: (id: String, x: Float, y: Float, width: Float, height: Float) -> Unit,
    onTap: (id: String) -> Unit,
    modifier: Modifier = Modifier,
    onSelected: (String) -> Unit = {},
    selectionResetKey: Int = 0,
    onSelectionCleared: () -> Unit = {},
) {
    if (overlays.isEmpty()) return
    var selectedId by remember(editable, selectionResetKey) { mutableStateOf<String?>(null) }
    BoxWithConstraints(modifier = modifier.fillMaxSize().then(
        if (editable) Modifier.pointerInput(Unit) {
            detectTapGestures(onTap = { selectedId = null; onSelectionCleared() })
        } else Modifier,
    )) {
        val panelWpx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val panelHpx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        overlays.forEach { overlay ->
            OverlayItem(
                overlay = overlay,
                editable = editable,
                selected = selectedId == overlay.id,
                onSelect = { selectedId = overlay.id; onSelected(overlay.id) },
                panelWpx = panelWpx,
                panelHpx = panelHpx,
                onMove = { x, y -> onMove(overlay.id, x, y) },
                onResize = { x, y, w, h -> onResize(overlay.id, x, y, w, h) },
                onTap = { onTap(overlay.id) },
            )
        }
    }
}

@Composable
private fun OverlayItem(
    overlay: TextOverlay,
    editable: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
    panelWpx: Float,
    panelHpx: Float,
    onMove: (Float, Float) -> Unit,
    onResize: (Float, Float, Float, Float) -> Unit,
    onTap: () -> Unit,
) {
    val density = LocalDensity.current
    val currentOverlay by rememberUpdatedState(overlay)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnResize by rememberUpdatedState(onResize)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentSelected by rememberUpdatedState(selected)
    var dragXPx by remember(overlay.id) { mutableFloatStateOf(0f) }
    var dragYPx by remember(overlay.id) { mutableFloatStateOf(0f) }
    var resizeDxPx by remember(overlay.id) { mutableFloatStateOf(0f) }
    var resizeDyPx by remember(overlay.id) { mutableFloatStateOf(0f) }
    var resizeCenterX by remember(overlay.id) { mutableFloatStateOf(0f) }
    var resizeCenterY by remember(overlay.id) { mutableFloatStateOf(0f) }
    val widthPx = ((overlay.widthPercent / 100f * panelWpx) + resizeDxPx).coerceAtLeast(1f)
    val baseHeightPx = if (overlay.heightPercent > 0f) {
        overlay.heightPercent / 100f * panelHpx
    } else {
        with(density) { (overlay.fontSizeSp * 2.2f).sp.toPx() }
    }
    val heightPx = (baseHeightPx + resizeDyPx).coerceAtLeast(1f)
    val currentFrame by rememberUpdatedState(OverlayFrame(overlay.xPercent / 100f * panelWpx,
        overlay.yPercent / 100f * panelHpx, overlay.widthPercent / 100f * panelWpx, baseHeightPx))
    val centerXPx = overlay.xPercent / 100f * panelWpx + dragXPx + resizeCenterX
    val centerYPx = overlay.yPercent / 100f * panelHpx + dragYPx + resizeCenterY
    val bg = parseHexColor(overlay.backgroundHex, Color.Black).copy(alpha = overlay.backgroundAlpha)
    val fg = parseHexColor(overlay.colorHex, Color.White)
    val widthDp = with(density) { widthPx.toDp() }
    val heightDp = with(density) { heightPx.toDp() }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (centerXPx - widthPx / 2f).roundToInt(),
                    (centerYPx - heightPx / 2f).roundToInt(),
                )
            }
            .width(widthDp)
            .height(heightDp)
            .rotate(overlay.rotationDeg)
            .testTag("overlay-${overlay.id}")
            .semantics {
                contentDescription = "Text: ${overlay.text}"
                if (editable) customActions = listOf(CustomAccessibilityAction("Edit text") { onTap(); true })
            }
            .then(
                if (editable) {
                    Modifier
                        .pointerInput(overlay.id, panelWpx, panelHpx) {
                            detectDragGestures(
                                onDragStart = { currentOnSelect() },
                                onDragEnd = {
                                    val moved = OverlayGeometry.move(currentFrame, dragXPx, dragYPx,
                                        currentOverlay.rotationDeg, panelWpx, panelHpx)
                                    currentOnMove(
                                        moved.x / panelWpx * 100f,
                                        moved.y / panelHpx * 100f,
                                    )
                                    dragXPx = 0f
                                    dragYPx = 0f
                                },
                                onDragCancel = { dragXPx = 0f; dragYPx = 0f },
                                onDrag = { change, amount ->
                                    change.consume()
                                    val angle = Math.toRadians(currentOverlay.rotationDeg.toDouble())
                                    dragXPx += (amount.x * cos(angle) - amount.y * sin(angle)).toFloat()
                                    dragYPx += (amount.x * sin(angle) + amount.y * cos(angle)).toFloat()
                                },
                            )
                        }
                        .pointerInput(overlay.id) {
                            detectTapGestures(onTap = {
                                if (currentSelected) currentOnTap() else currentOnSelect()
                            })
                        }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        val bubble = overlay.style == TextOverlayStyle.SpeechBubble
        if (bubble) {
            // matchParentSize keeps the canvas out of measurement, so the Text sizes the box.
            SpeechBubbleBackground(bg, overlay.tailAngleDeg, Modifier.matchParentSize())
        }
        OverlayLettering(
            overlay = overlay,
            pageWidthPx = panelWpx,
            showOverflow = editable && selected,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (bubble) {
                        // Room around the text for the bubble outline and its tail.
                        Modifier.padding(BubbleTailMargin)
                    } else {
                        Modifier.background(bg, RoundedCornerShape(6.dp))
                    },
                ),
        )
        if (editable && selected) {
            Box(Modifier.matchParentSize().border(1.dp, MaterialTheme.colorScheme.primary))
            val handles = listOf(
                Triple(Alignment.CenterStart, -1, 0), Triple(Alignment.CenterEnd, 1, 0),
                Triple(Alignment.TopCenter, 0, -1), Triple(Alignment.BottomCenter, 0, 1),
                Triple(Alignment.TopStart, -1, -1), Triple(Alignment.TopEnd, 1, -1),
                Triple(Alignment.BottomStart, -1, 1), Triple(Alignment.BottomEnd, 1, 1),
            )
            handles.forEach { (alignment, axisX, axisY) ->
            Box(
                modifier = Modifier
                    .align(alignment)
                    .testTag("overlay-${overlay.id}-handle-$axisX-$axisY")
                    .size(minOf(20.dp, widthDp / 4, heightDp / 4))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), RoundedCornerShape(3.dp))
                    .pointerInput(overlay.id, panelWpx, panelHpx, axisX, axisY) {
                        detectDragGestures(
                            onDragEnd = {
                                currentOnResize(
                                    currentOverlay.xPercent + resizeCenterX / panelWpx * 100f,
                                    currentOverlay.yPercent + resizeCenterY / panelHpx * 100f,
                                    currentOverlay.widthPercent + resizeDxPx / panelWpx * 100f,
                                    (currentFrame.height + resizeDyPx) / panelHpx * 100f,
                                )
                                resizeDxPx = 0f
                                resizeDyPx = 0f; resizeCenterX = 0f; resizeCenterY = 0f
                            },
                            onDragCancel = { resizeDxPx = 0f; resizeDyPx = 0f; resizeCenterX = 0f; resizeCenterY = 0f },
                            onDrag = { change, amount ->
                                change.consume()
                                val frame = currentFrame
                                val next = OverlayGeometry.resize(frame, resizeDxPx * axisX + amount.x,
                                    resizeDyPx * axisY + amount.y, axisX, axisY, currentOverlay.rotationDeg, panelWpx, panelHpx)
                                resizeDxPx = next.width - frame.width
                                resizeDyPx = next.height - frame.height
                                resizeCenterX = next.x - frame.x
                                resizeCenterY = next.y - frame.y
                            },
                        )
                    },
            )
            }
            // Draw last so the dedicated move target wins hit testing over the
            // expanded touch targets of resize handles on narrow text boxes.
            // No background: this target must never obscure the lettering.
            Box(
                Modifier.align(Alignment.Center)
                    .width(minOf(64.dp, widthDp / 2))
                    .height(minOf(64.dp, heightDp / 2))
                    .testTag("overlay-${overlay.id}-move")
                    .semantics { contentDescription = "Move translated text box" }
                    .pointerInput(overlay.id, panelWpx, panelHpx) {
                        detectDragGestures(
                            onDragEnd = {
                                val moved = OverlayGeometry.move(currentFrame, dragXPx, dragYPx,
                                    currentOverlay.rotationDeg, panelWpx, panelHpx)
                                currentOnMove(moved.x / panelWpx * 100f, moved.y / panelHpx * 100f)
                                dragXPx = 0f
                                dragYPx = 0f
                            },
                            onDragCancel = { dragXPx = 0f; dragYPx = 0f },
                            onDrag = { change, amount ->
                                change.consume()
                                val angle = Math.toRadians(currentOverlay.rotationDeg.toDouble())
                                dragXPx += (amount.x * cos(angle) - amount.y * sin(angle)).toFloat()
                                dragYPx += (amount.x * sin(angle) + amount.y * cos(angle)).toFloat()
                            },
                        )
                    },
            )
        }
    }
}

/** Smallest and largest lettering the auto-fit search will settle on. */
private const val MinOverlaySp = 6f
private const val MaxOverlaySp = 96f

/**
 * The largest font size whose wrapped text still fits the box. Auto-fit layers are placed into
 * whatever balloon the cleanup resolved, so the box is chosen first and the lettering has to be
 * sized to it — drawing at a stored size just overflows the bubble.
 */
@Composable
private fun rememberFittedFontSize(
    text: String,
    autoFit: Boolean,
    requestedSp: Float,
    availableWidthPx: Float,
    availableHeightPx: Float,
    align: TextAlign,
): Float {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val baseStyle = LocalTextStyle.current
    if (!autoFit || text.isBlank() || availableWidthPx < 1f || availableHeightPx < 1f) {
        return requestedSp.coerceIn(MinOverlaySp, MaxOverlaySp)
    }
    return remember(text, availableWidthPx, availableHeightPx, align, density, baseStyle) {
        val maxWidth = availableWidthPx.toInt().coerceAtLeast(1)
        fun fits(sizeSp: Float): Boolean {
            val result = measurer.measure(
                text = AnnotatedString(text),
                style = baseStyle.copy(fontSize = sizeSp.sp, textAlign = align),
                constraints = Constraints(maxWidth = maxWidth),
                softWrap = true,
            )
            return result.size.height <= availableHeightPx && result.size.width <= availableWidthPx
        }
        val ceiling = with(density) { availableHeightPx.toDp().value }
            .coerceIn(MinOverlaySp, MaxOverlaySp)
        if (fits(ceiling)) {
            ceiling
        } else {
            var low = MinOverlaySp
            var high = ceiling
            repeat(7) {
                val mid = (low + high) / 2f
                if (fits(mid)) low = mid else high = mid
            }
            low
        }
    }
}

/**
 * Rounded bubble with a triangular tail pointing toward [tailAngleDeg].
 * The bubble is inset by [BubbleTailMargin] so the tail has room to reach the edge
 * without being clipped.
 */
@Composable
private fun SpeechBubbleBackground(
    color: Color,
    tailAngleDeg: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val inset = BubbleTailMargin.toPx()
        val left = inset
        val top = inset
        val right = (size.width - inset).coerceAtLeast(left + 1f)
        val bottom = (size.height - inset).coerceAtLeast(top + 1f)
        val corner = 10.dp.toPx()
        val bubble = Path().apply {
            addRoundRect(
                RoundRect(
                    left = left,
                    top = top,
                    right = right,
                    bottom = bottom,
                    cornerRadius = CornerRadius(corner, corner),
                ),
            )
        }
        // Tail runs from the bubble's center out to the canvas edge in the tail direction.
        val rad = Math.toRadians(tailAngleDeg.toDouble())
        val dx = cos(rad).toFloat()
        val dy = sin(rad).toFloat()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val tipX = cx + dx * (size.width / 2f)
        val tipY = cy + dy * (size.height / 2f)
        // Base is perpendicular to the tail direction so the triangle stays attached.
        val baseSpread = 7.dp.toPx()
        val px = -dy * baseSpread
        val py = dx * baseSpread
        val tail = Path().apply {
            moveTo(cx + px, cy + py)
            lineTo(cx - px, cy - py)
            lineTo(tipX, tipY)
            close()
        }
        drawPath(tail, color)
        drawPath(bubble, color)
    }
}

/** Space reserved around a speech bubble for its tail. */
private val BubbleTailMargin = 10.dp
