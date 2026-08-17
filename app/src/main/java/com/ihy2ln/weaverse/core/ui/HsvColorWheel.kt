package com.ihy2ln.weaverse.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * An interactive HSV wheel (spec Revision 02 §4: "an HSV wheel with
 * saturation/value sliders"): angle around the disc is hue, distance from
 * center is saturation, and a separate slider below controls value/
 * brightness — the wheel itself always renders at full brightness (redrawing
 * a radial hue/saturation gradient on every value change is a lot of extra
 * Canvas work for a difference that shows up as a simple darken/lighten of
 * whatever's already selected), then the picked color is scaled by value on
 * the way out. [onColorChanged] fires continuously while dragging.
 */
@Composable
fun HsvColorWheel(
    color: Color,
    onColorChanged: (Color) -> Unit,
    modifier: Modifier = Modifier,
    wheelSize: Dp = 200.dp,
) {
    val hsv = remember(color) { color.toHsv() }
    var hue by remember(color) { mutableFloatStateOf(hsv[0]) }
    var saturation by remember(color) { mutableFloatStateOf(hsv[1]) }
    var value by remember(color) { mutableFloatStateOf(hsv[2]) }

    fun emit() {
        onColorChanged(Color.hsv(hue, saturation, value))
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(wheelSize)
                .clip(CircleShape)
                .pointerInput(Unit) {
                    fun updateFromOffset(offset: Offset) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val radius = min(size.width, size.height) / 2f
                        val dx = offset.x - center.x
                        val dy = offset.y - center.y
                        val distance = sqrt(dx * dx + dy * dy).coerceAtMost(radius)
                        val angle = (Math.toDegrees(atan2(dy, dx).toDouble()) + 360.0) % 360.0
                        hue = angle.toFloat()
                        saturation = (distance / radius).coerceIn(0f, 1f)
                        emit()
                    }
                    detectDragGestures(
                        onDragStart = { offset -> updateFromOffset(offset) },
                        onDrag = { change, _ -> updateFromOffset(change.position) },
                    )
                },
        ) {
            Canvas(modifier = Modifier.size(wheelSize)) {
                val radius = size.minDimension / 2f
                val center = Offset(size.width / 2f, size.height / 2f)
                val hueColors = (0..360 step 15).map { Color.hsv(it.toFloat() % 360f, 1f, 1f) }
                drawCircle(brush = Brush.sweepGradient(hueColors, center), radius = radius, center = center)
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color.White, Color.White.copy(alpha = 0f)),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
                // Selection marker at the current hue/saturation position.
                val markerAngleRad = Math.toRadians(hue.toDouble())
                val markerDistance = saturation * radius
                val markerX = center.x + (cos(markerAngleRad) * markerDistance).toFloat()
                val markerY = center.y + (sin(markerAngleRad) * markerDistance).toFloat()
                drawCircle(color = Color.White, radius = 8.dp.toPx(), center = Offset(markerX, markerY), style = Stroke(width = 2.dp.toPx()))
                drawCircle(color = Color.Black.copy(alpha = 0.4f), radius = 9.dp.toPx(), center = Offset(markerX, markerY), style = Stroke(width = 1.dp.toPx()))
            }
        }
        Text("Value", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = Spacing.sm))
        Slider(
            value = value,
            onValueChange = { value = it; emit() },
            modifier = Modifier.fillMaxWidth().height(32.dp),
        )
    }
}

/** Converts to `[hue(0-360), saturation(0-1), value(0-1)]`, the inverse of [Color.hsv]. */
private fun Color.toHsv(): FloatArray {
    val r = red
    val g = green
    val b = blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min

    val hue = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }

    val saturation = if (max == 0f) 0f else delta / max
    return floatArrayOf(hue, saturation, max)
}
