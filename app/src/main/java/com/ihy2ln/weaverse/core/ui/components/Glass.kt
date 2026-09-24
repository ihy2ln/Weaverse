package com.ihy2ln.weaverse.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.ihy2ln.weaverse.core.ui.theme.LocalGlassClarity
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusMd
import com.ihy2ln.weaverse.core.ui.theme.inkTokens

/** How opaque a glass panel's tint is at the current Glass setting. */
@Composable
fun glassFillAlpha(): Float = (1f - LocalGlassClarity.current * 0.75f).coerceIn(0.25f, 1f)

/**
 * Frosted-glass panel: a translucent tint of [tint] with a soft top sheen and a
 * light-catching edge, so the wallpaper shows through as on the streaming Home.
 * Clarity comes from the Appearance → Glass setting.
 */
@Composable
fun Modifier.glassPanel(
    shape: Shape = RoundedCornerShape(inkRadiusMd() * 1.5f),
    tint: Color = inkTokens().panel,
): Modifier {
    val light = inkTokens().background.luminance() > 0.5f
    val edge = if (light) {
        listOf(Color.White.copy(alpha = 0.75f), inkTokens().hairline.copy(alpha = 0.55f))
    } else {
        listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.04f))
    }
    val sheen = Color.White.copy(alpha = if (light) 0.22f else 0.05f)
    return this
        .clip(shape)
        .background(tint.copy(alpha = tint.alpha * glassFillAlpha()))
        .background(Brush.verticalGradient(listOf(sheen, Color.Transparent)))
        .border(1.dp, Brush.verticalGradient(edge), shape)
}
