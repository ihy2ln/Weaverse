package com.ihy2ln.weaverse.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusSm
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.toHexString
import kotlin.math.roundToInt

/** Hue of each swatch column, sweeping red → yellow → green → blue → magenta. */
private val HUE_COLUMNS = listOf(
    0f, 15f, 30f, 45f, 60f, 75f, 95f, 120f, 150f, 180f, 210f, 240f, 270f, 300f, 330f,
)

/** Shade of each swatch row: light tints on top, deep darks on the bottom. */
private val SHADE_ROWS = listOf(
    0.45f to 1.00f,
    0.70f to 0.95f,
    0.90f to 0.80f,
    0.85f to 0.55f,
)

/** Neutral column, light to dark, matching the shade rows. */
private val GRAY_COLUMN = listOf(
    Color(0xFFFFFFFF),
    Color(0xFFC4C7CC),
    Color(0xFF7A7F87),
    Color(0xFF2B2F36),
)

private val swatchShape = RoundedCornerShape(3.dp)

/**
 * Swatch-grid color picker: every color is a fixed tile, so selection is
 * stateless — what you tap is exactly what gets reported, with no internal
 * HSV state to fall out of sync. Tap a tile to pick it; the outlined tile is
 * the active color. The optional opacity slider tints the chosen tile.
 */
@Composable
fun InkSwatchColorPicker(
    selected: Color,
    onSelect: (Color) -> Unit,
    modifier: Modifier = Modifier,
    opacityPercent: Int = 100,
    onOpacityChange: ((Int) -> Unit)? = null,
) {
    val tiles = remember { buildSwatchTiles() }
    val alpha = opacityPercent.coerceIn(0, 100) / 100f

    Column(modifier = modifier) {
        tiles.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 1.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                row.forEach { tile ->
                    val isSelected = sameRgb(tile, selected)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(0.92f)
                            .clip(swatchShape)
                            .background(tile)
                            .border(
                                width = if (isSelected) 2.dp else 0.5.dp,
                                color = if (isSelected) {
                                    Color.White
                                } else {
                                    Color.Black.copy(alpha = 0.35f)
                                },
                                shape = swatchShape,
                            )
                            .clickable { onSelect(tile.copy(alpha = alpha)) },
                    )
                }
            }
        }
        if (onOpacityChange != null) {
            Text("Opacity: $opacityPercent%", modifier = Modifier.padding(top = InkSpacing.sm))
            Slider(
                value = opacityPercent.toFloat(),
                onValueChange = { onOpacityChange(it.toInt().coerceIn(0, 100)) },
                valueRange = 0f..100f,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = InkSpacing.sm)
                .height(40.dp)
                .clip(RoundedCornerShape(inkRadiusSm()))
                .background(selected.copy(alpha = 1f))
                .border(1.dp, Color.Gray.copy(alpha = 0.4f), RoundedCornerShape(inkRadiusSm())),
            contentAlignment = Alignment.Center,
        ) {
            Text(selected.toHexString())
        }
    }
}

private fun buildSwatchTiles(): List<List<Color>> = SHADE_ROWS.indices.map { row ->
    val (saturation, value) = SHADE_ROWS[row]
    HUE_COLUMNS.map { hue -> hsvToColor(hue, saturation, value, 100) } + GRAY_COLUMN[row]
}

private fun sameRgb(a: Color, b: Color): Boolean =
    (a.red * 255f).roundToInt() == (b.red * 255f).roundToInt() &&
        (a.green * 255f).roundToInt() == (b.green * 255f).roundToInt() &&
        (a.blue * 255f).roundToInt() == (b.blue * 255f).roundToInt()

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

enum class AppearanceSection(val label: String, val storageKey: String) {
    Chrome("Chrome / header", "chrome"),
    Rail("Left rail", "rail"),
    Content("Content area", "content"),
    Page("Manuscript page", "page"),
    ChatBubble("Chat bubbles", "chat_bubble"),
}
