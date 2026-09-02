package com.ihy2ln.weaverse.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ihy2ln.weaverse.core.text.TextOverlay
import com.ihy2ln.weaverse.core.text.TextOverlayStyle
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
    onResize: (id: String, widthPercent: Float) -> Unit,
    onTap: (id: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (overlays.isEmpty()) return
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val panelWpx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val panelHpx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        overlays.forEach { overlay ->
            OverlayItem(
                overlay = overlay,
                editable = editable,
                panelWpx = panelWpx,
                panelHpx = panelHpx,
                onMove = { x, y -> onMove(overlay.id, x, y) },
                onResize = { w -> onResize(overlay.id, w) },
                onTap = { onTap(overlay.id) },
            )
        }
    }
}

@Composable
private fun OverlayItem(
    overlay: TextOverlay,
    editable: Boolean,
    panelWpx: Float,
    panelHpx: Float,
    onMove: (Float, Float) -> Unit,
    onResize: (Float) -> Unit,
    onTap: () -> Unit,
) {
    val density = LocalDensity.current
    var dragXPx by remember(overlay.id) { mutableFloatStateOf(0f) }
    var dragYPx by remember(overlay.id) { mutableFloatStateOf(0f) }
    var resizeDxPx by remember(overlay.id) { mutableFloatStateOf(0f) }
    val widthPx = (overlay.widthPercent / 100f * panelWpx) + resizeDxPx
    val centerXPx = overlay.xPercent / 100f * panelWpx + dragXPx
    val centerYPx = overlay.yPercent / 100f * panelHpx + dragYPx
    val bg = parseHexColor(overlay.backgroundHex, Color.Black).copy(alpha = overlay.backgroundAlpha)
    val fg = parseHexColor(overlay.colorHex, Color.White)
    val widthDp = with(density) { widthPx.toDp() }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (centerXPx - widthPx / 2f).roundToInt(),
                    centerYPx.roundToInt(),
                )
            }
            .width(widthDp)
            .rotate(overlay.rotationDeg)
            .then(
                if (editable) {
                    Modifier
                        .pointerInput(overlay.id, panelWpx, panelHpx) {
                            detectDragGestures(
                                onDragEnd = {
                                    onMove(
                                        centerXPx / panelWpx * 100f,
                                        centerYPx / panelHpx * 100f,
                                    )
                                    dragXPx = 0f
                                    dragYPx = 0f
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragXPx += amount.x
                                    dragYPx += amount.y
                                },
                            )
                        }
                        .pointerInput(overlay.id) {
                            detectTapGestures(onTap = { onTap() })
                        }
                } else {
                    Modifier
                },
            ),
    ) {
        val bubble = overlay.style == TextOverlayStyle.SpeechBubble
        if (bubble) {
            // matchParentSize keeps the canvas out of measurement, so the Text sizes the box.
            SpeechBubbleBackground(bg, overlay.tailAngleDeg, Modifier.matchParentSize())
        }
        Text(
            text = overlay.text,
            color = fg,
            fontSize = overlay.fontSizeSp.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .then(
                    if (bubble) {
                        // Room around the text for the bubble outline and its tail.
                        Modifier.padding(BubbleTailMargin)
                    } else {
                        Modifier.background(bg, RoundedCornerShape(6.dp))
                    },
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
        if (editable) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(14.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), RoundedCornerShape(3.dp))
                    .pointerInput(overlay.id, panelWpx) {
                        detectDragGestures(
                            onDragEnd = {
                                val newWidthPercent =
                                    ((overlay.widthPercent / 100f * panelWpx + resizeDxPx) / panelWpx * 100f)
                                        .coerceIn(10f, 100f)
                                onResize(newWidthPercent)
                                resizeDxPx = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                resizeDxPx += amount.x
                            },
                        )
                    },
            )
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
