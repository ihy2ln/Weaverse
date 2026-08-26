package com.ihy2ln.weaverse.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusMd
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusSm
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.toHexString
import kotlin.math.roundToInt

/**
 * Color square bar: hue strip + saturation×value square, plus opacity.
 * Per-section HSV brightness sliders are replaced by the SV square; overall app
 * brightness is controlled separately in Settings.
 */
@Composable
fun InkHsvColorWheel(
    selected: Color,
    onSelect: (Color) -> Unit,
    modifier: Modifier = Modifier,
    opacityPercent: Int = 100,
    onOpacityChange: ((Int) -> Unit)? = null,
) {
    var hue by remember(selected) { mutableFloatStateOf(colorToHue(selected)) }
    var saturation by remember(selected) { mutableFloatStateOf(colorToSaturation(selected)) }
    var value by remember(selected) { mutableFloatStateOf(colorToValue(selected).coerceAtLeast(0.05f)) }
    val preview = hsvToColor(hue, saturation, value, opacityPercent)

    Column(modifier = modifier) {
        Text("Hue")
        HueStrip(
            hue = hue,
            onHueChange = {
                hue = it
                onSelect(hsvToColor(it, saturation, value, opacityPercent))
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = InkSpacing.xs)
                .height(28.dp),
        )
        Text("Color", modifier = Modifier.padding(top = InkSpacing.sm))
        SatValueSquare(
            hue = hue,
            saturation = saturation,
            value = value,
            onChange = { s, v ->
                saturation = s
                value = v
                onSelect(hsvToColor(hue, s, v, opacityPercent))
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = InkSpacing.xs)
                .height(160.dp),
        )
        if (onOpacityChange != null) {
            Text("Opacity: $opacityPercent%", modifier = Modifier.padding(top = InkSpacing.sm))
            Slider(
                value = opacityPercent.toFloat(),
                onValueChange = {
                    val pct = it.toInt().coerceIn(0, 100)
                    onOpacityChange(pct)
                    onSelect(hsvToColor(hue, saturation, value, pct))
                },
                valueRange = 0f..100f,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = InkSpacing.sm)
                .height(40.dp)
                .clip(RoundedCornerShape(inkRadiusSm()))
                .background(preview.copy(alpha = 1f))
                .border(1.dp, Color.Gray.copy(alpha = 0.4f), RoundedCornerShape(inkRadiusSm())),
            contentAlignment = Alignment.Center,
        ) {
            Text(preview.toHexString())
        }
    }
}

@Composable
private fun HueStrip(
    hue: Float,
    onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var size by remember { mutableFloatStateOf(1f) }
    val hues = remember {
        listOf(
            Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red,
        )
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(inkRadiusSm()))
            .onSizeChanged { size = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(size) {
                detectTapGestures { pos ->
                    onHueChange((pos.x / size * 359f).coerceIn(0f, 359f))
                }
            }
            .pointerInput(size) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onHueChange((change.position.x / size * 359f).coerceIn(0f, 359f))
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(brush = Brush.horizontalGradient(hues))
        }
        val thumbX = (hue / 359f * size).roundToInt()
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset(thumbX - 6, 0) }
                .size(12.dp, 28.dp)
                .border(2.dp, Color.White, RoundedCornerShape(2.dp))
                .border(1.dp, Color.Black.copy(alpha = 0.5f), RoundedCornerShape(2.dp)),
        )
    }
}

@Composable
private fun SatValueSquare(
    hue: Float,
    saturation: Float,
    value: Float,
    onChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val pure = hsvToColor(hue, 1f, 1f, 100)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(inkRadiusSm()))
            .onSizeChanged { boxSize = it }
            .pointerInput(boxSize, hue) {
                if (boxSize.width == 0 || boxSize.height == 0) return@pointerInput
                detectTapGestures { pos ->
                    val s = (pos.x / boxSize.width).coerceIn(0f, 1f)
                    val v = (1f - pos.y / boxSize.height).coerceIn(0.05f, 1f)
                    onChange(s, v)
                }
            }
            .pointerInput(boxSize, hue) {
                if (boxSize.width == 0 || boxSize.height == 0) return@pointerInput
                detectDragGestures { change, _ ->
                    change.consume()
                    val s = (change.position.x / boxSize.width).coerceIn(0f, 1f)
                    val v = (1f - change.position.y / boxSize.height).coerceIn(0.05f, 1f)
                    onChange(s, v)
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(brush = Brush.horizontalGradient(listOf(Color.White, pure)))
            drawRect(brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        }
        if (boxSize.width > 0 && boxSize.height > 0) {
            val x = (saturation * boxSize.width).roundToInt()
            val y = ((1f - value) * boxSize.height).roundToInt()
            Box(
                modifier = Modifier
                    .offset { IntOffset(x - 8, y - 8) }
                    .size(16.dp)
                    .border(2.dp, Color.White, CircleShape)
                    .border(1.dp, Color.Black.copy(alpha = 0.45f), CircleShape),
            )
        }
    }
}

fun hsvToColor(h: Float, s: Float, v: Float, opacityPercent: Int): Color {
    val c = v * s
    val x = c * (1 - kotlin.math.abs((h / 60f) % 2 - 1))
    val m = v - c
    val (r1, g1, b1) = when {
        h < 60 -> Triple(c, x, 0f)
        h < 120 -> Triple(x, c, 0f)
        h < 180 -> Triple(0f, c, x)
        h < 240 -> Triple(0f, x, c)
        h < 300 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val alpha = opacityPercent.coerceIn(0, 100) / 100f
    return Color(r1 + m, g1 + m, b1 + m, alpha)
}

private fun colorToHue(color: Color): Float {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    if (delta == 0f) return 0f
    val raw = when (max) {
        r -> 60f * (((g - b) / delta) % 6)
        g -> 60f * (((b - r) / delta) + 2)
        else -> 60f * (((r - g) / delta) + 4)
    }
    return if (raw < 0) raw + 360f else raw
}

private fun colorToSaturation(color: Color): Float {
    val max = maxOf(color.red, color.green, color.blue)
    val min = minOf(color.red, color.green, color.blue)
    return if (max == 0f) 0f else (max - min) / max
}

private fun colorToValue(color: Color): Float = maxOf(color.red, color.green, color.blue)

enum class AppearanceSection(val label: String, val storageKey: String) {
    Chrome("Chrome / header", "chrome"),
    Rail("Left rail", "rail"),
    Content("Content area", "content"),
    Page("Manuscript page", "page"),
    ChatBubble("Chat bubbles", "chat_bubble"),
}
