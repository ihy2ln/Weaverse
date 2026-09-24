package com.ihy2ln.weaverse.core.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.max
import kotlin.math.min

/**
 * Wallpaper behind the app. [Profile] draws the appearance profile's own art; the
 * rest are desktop-style wallpapers in the spirit of Windows and macOS, drawn with
 * brushes so they cost no bundled images and scale to any screen or thumbnail.
 */
enum class BackdropStyle(val label: String, val family: String) {
    Profile("Theme art", "Weaverse"),
    Bloom("Bloom", "Windows"),
    BloomDark("Bloom Dark", "Windows"),
    Glow("Glow", "Windows"),
    Bliss("Bliss", "Windows"),
    BigSur("Big Sur", "Apple"),
    Monterey("Monterey", "Apple"),
    Ventura("Ventura", "Apple"),
    Sonoma("Sonoma", "Apple"),
    Sequoia("Sequoia", "Apple"),
    ;

    companion object {
        fun fromName(name: String?): BackdropStyle = entries.firstOrNull { it.name == name } ?: Profile
    }
}

/** The wallpaper for [style]; [Profile] falls through to the profile's own art. */
@Composable
fun AppBackdrop(style: BackdropStyle, profile: AppearanceProfile, modifier: Modifier = Modifier) {
    if (style == BackdropStyle.Profile) {
        ProfileBackgroundArt(profile, modifier)
        return
    }
    Canvas(modifier) {
        when (style) {
            BackdropStyle.Profile -> Unit
            BackdropStyle.Bloom -> bloom(
                sky = listOf(Color(0xFFEAF2FC), Color(0xFFC7DCF5), Color(0xFF9CC0EB)),
                inner = Color(0xFF0A3FB0), tip = Color(0xFF8CC4FA), glow = Color(0x66FFFFFF),
            )
            BackdropStyle.BloomDark -> bloom(
                sky = listOf(Color(0xFF02060F), Color(0xFF071430), Color(0xFF0B1D45)),
                inner = Color(0xFF0F3BD8), tip = Color(0xFF5CCBFF), glow = Color(0x553D8BFF),
            )
            BackdropStyle.Glow -> glow()
            BackdropStyle.Bliss -> bliss()
            BackdropStyle.BigSur -> layeredWaves(
                sky = listOf(Color(0xFFFFC38A), Color(0xFFF08A7E), Color(0xFF9C5CC4)),
                layers = listOf(Color(0xFFE8637B), Color(0xFFB34FA8), Color(0xFF6A4BC4), Color(0xFF3B3FA8), Color(0xFF1B2470)),
                start = 0.34f,
            )
            BackdropStyle.Monterey -> layeredWaves(
                sky = listOf(Color(0xFF2A1466), Color(0xFF4B22A6), Color(0xFF7A3FD0)),
                layers = listOf(Color(0xFFFF9EC0), Color(0xFFE067D8), Color(0xFF9B59F0), Color(0xFF5B3BD6), Color(0xFF2B1A8A)),
                start = 0.28f,
                flip = true,
            )
            BackdropStyle.Ventura -> ventura()
            BackdropStyle.Sonoma -> layeredWaves(
                sky = listOf(Color(0xFFAFDCEB), Color(0xFFDDEBD9), Color(0xFFF5E6C4)),
                layers = listOf(Color(0xFFA9CF93), Color(0xFF79B07C), Color(0xFF4F8C6A), Color(0xFF2F6653), Color(0xFF1C4238)),
                start = 0.42f,
                amplitude = 0.05f,
            )
            BackdropStyle.Sequoia -> sequoia()
        }
    }
}

private fun DrawScope.fillSky(colors: List<Color>) {
    drawRect(Brush.verticalGradient(colors))
}

private fun DrawScope.softGlow(color: Color, center: Offset, radius: Float) {
    drawRect(
        Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center = center, radius = max(radius, 1f)),
    )
}

/** One petal lying along +x from the origin: a blade that swells then tapers to a point. */
private fun petal(length: Float, width: Float): Path = Path().apply {
    moveTo(0f, 0f)
    cubicTo(length * 0.3f, -width, length * 0.8f, -width * 0.7f, length, 0f)
    cubicTo(length * 0.8f, width * 0.35f, length * 0.35f, width * 0.45f, 0f, 0f)
    close()
}

private fun DrawScope.bloom(sky: List<Color>, inner: Color, tip: Color, glow: Color) {
    fillSky(sky)
    val center = Offset(size.width * 0.5f, size.height * 0.56f)
    val reach = max(size.width, size.height) * 0.42f
    softGlow(glow, center, reach * 1.3f)
    val blade = petal(reach, reach * 0.34f)
    val count = 8
    repeat(count) { i ->
        val angle = -100f + i * (360f / count)
        translate(center.x, center.y) {
            rotate(angle, pivot = Offset.Zero) {
                drawPath(
                    blade,
                    Brush.linearGradient(listOf(inner, tip), start = Offset.Zero, end = Offset(reach, 0f)),
                    alpha = 0.62f,
                )
            }
        }
    }
    // A second, smaller ring twisted between the first gives the folded-ribbon depth.
    val inside = petal(reach * 0.62f, reach * 0.24f)
    repeat(count) { i ->
        val angle = -100f + (i + 0.5f) * (360f / count)
        translate(center.x, center.y) {
            rotate(angle, pivot = Offset.Zero) {
                drawPath(
                    inside,
                    Brush.linearGradient(listOf(tip, inner), start = Offset.Zero, end = Offset(reach * 0.62f, 0f)),
                    alpha = 0.5f,
                )
            }
        }
    }
    softGlow(tip.copy(alpha = 0.45f), center, reach * 0.18f)
}

private fun DrawScope.glow() {
    fillSky(listOf(Color(0xFF07041A), Color(0xFF120A33), Color(0xFF0A0620)))
    val w = size.width
    val h = size.height
    softGlow(Color(0x557B3FF2), Offset(w * 0.3f, h * 0.45f), max(w, h) * 0.55f)
    softGlow(Color(0x44D946EF), Offset(w * 0.8f, h * 0.7f), max(w, h) * 0.45f)
    val ribbons = listOf(
        Triple(Color(0xFF7B3FF2), 0.30f, 0.20f),
        Triple(Color(0xFFD946EF), 0.45f, 0.14f),
        Triple(Color(0xFF3B82F6), 0.58f, 0.12f),
        Triple(Color(0xFFA78BFA), 0.70f, 0.08f),
        Triple(Color(0xFFF0ABFC), 0.80f, 0.05f),
    )
    ribbons.forEachIndexed { i, (color, y, thickness) ->
        val path = Path().apply {
            moveTo(-w * 0.2f, h * (y + 0.12f))
            cubicTo(w * 0.25f, h * (y - 0.22f - i * 0.02f), w * 0.7f, h * (y + 0.25f), w * 1.2f, h * (y - 0.08f))
        }
        drawPath(
            path,
            Brush.linearGradient(listOf(color.copy(alpha = 0f), color, color.copy(alpha = 0.1f))),
            alpha = 0.55f,
            style = Stroke(width = min(w, h) * thickness, cap = StrokeCap.Round),
        )
    }
}

private fun DrawScope.bliss() {
    val w = size.width
    val h = size.height
    fillSky(listOf(Color(0xFF1F5FC9), Color(0xFF4F8EE0), Color(0xFFA9CFF3)))
    // Clouds: clusters of soft white ovals.
    listOf(Offset(0.22f, 0.16f), Offset(0.7f, 0.1f), Offset(0.55f, 0.3f), Offset(0.1f, 0.36f)).forEach { c ->
        val cx = w * c.x
        val cy = h * c.y
        val s = min(w, h) * 0.12f
        listOf(Offset(-1.1f, 0.1f), Offset(0f, -0.25f), Offset(1.0f, 0.05f), Offset(0.3f, 0.3f)).forEach { o ->
            drawOval(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0f)),
                    center = Offset(cx + o.x * s, cy + o.y * s), radius = s * 1.1f,
                ),
                topLeft = Offset(cx + o.x * s - s * 1.2f, cy + o.y * s - s * 0.7f),
                size = Size(s * 2.4f, s * 1.4f),
            )
        }
    }
    val back = Path().apply {
        moveTo(0f, h * 0.66f)
        quadraticTo(w * 0.7f, h * 0.52f, w, h * 0.6f)
        lineTo(w, h); lineTo(0f, h); close()
    }
    drawPath(back, Brush.verticalGradient(listOf(Color(0xFF7CC04A), Color(0xFF3F8A1E)), startY = h * 0.52f, endY = h))
    val front = Path().apply {
        moveTo(0f, h * 0.62f)
        quadraticTo(w * 0.42f, h * 0.5f, w, h * 0.7f)
        lineTo(w, h); lineTo(0f, h); close()
    }
    drawPath(front, Brush.verticalGradient(listOf(Color(0xFF8AD14F), Color(0xFF4E9A22), Color(0xFF2C6B12)), startY = h * 0.5f, endY = h))
}

/**
 * Stacked wave bands, back to front, over a sky gradient — the shape of the Big Sur,
 * Monterey and Sonoma wallpapers, differing only in palette and swell.
 */
private fun DrawScope.layeredWaves(
    sky: List<Color>,
    layers: List<Color>,
    start: Float,
    amplitude: Float = 0.08f,
    flip: Boolean = false,
) {
    val w = size.width
    val h = size.height
    fillSky(sky)
    softGlow(Color.White.copy(alpha = 0.25f), Offset(w * if (flip) 0.8f else 0.2f, h * 0.12f), max(w, h) * 0.5f)
    val step = (1f - start) / (layers.size + 0.5f)
    layers.forEachIndexed { i, color ->
        val y = h * (start + i * step)
        val a = h * amplitude * (1f - i * 0.1f)
        val dir = if ((i % 2 == 0) xor flip) 1f else -1f
        val path = Path().apply {
            moveTo(0f, y + a * 0.4f * dir)
            cubicTo(w * 0.3f, y - a * dir, w * 0.62f, y + a * dir, w, y - a * 0.5f * dir)
            lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(
            path,
            Brush.verticalGradient(
                listOf(lerpToWhite(color, 0.18f), color, lerpToBlack(color, 0.25f)),
                startY = y - a, endY = h,
            ),
        )
    }
}

private fun DrawScope.ventura() {
    val w = size.width
    val h = size.height
    drawRect(
        Brush.radialGradient(
            listOf(Color(0xFFFFC46B), Color(0xFFF26A2E), Color(0xFFB0213A), Color(0xFF4A0B24)),
            center = Offset(w * 0.35f, h * 0.7f), radius = max(w, h) * 0.95f,
        ),
    )
    val center = Offset(w * 0.3f, h * 0.78f)
    val reach = max(w, h) * 0.7f
    val blade = petal(reach, reach * 0.3f)
    repeat(5) { i ->
        translate(center.x, center.y) {
            rotate(-95f + i * 18f, pivot = Offset.Zero) {
                drawPath(
                    blade,
                    Brush.linearGradient(listOf(Color(0xFFFFE0A3), Color(0xFFFF7A18), Color(0xFFC2185B)), start = Offset.Zero, end = Offset(reach, 0f)),
                    alpha = 0.42f,
                )
            }
        }
    }
    softGlow(Color(0x66FFE8B0), center, reach * 0.3f)
}

private fun DrawScope.sequoia() {
    val w = size.width
    val h = size.height
    fillSky(listOf(Color(0xFF01030D), Color(0xFF061338), Color(0xFF0B2A6B)))
    softGlow(Color(0x663B6CF6), Offset(w * 0.6f, h * 0.55f), max(w, h) * 0.55f)
    val bands = listOf(
        Triple(Color(0xFF1E40AF), 0.38f, 0.26f),
        Triple(Color(0xFF3B82F6), 0.5f, 0.16f),
        Triple(Color(0xFF93C5FD), 0.58f, 0.06f),
        Triple(Color(0xFFE11D48), 0.66f, 0.03f),
    )
    bands.forEach { (color, y, thickness) ->
        val path = Path().apply {
            moveTo(-w * 0.1f, h * (y + 0.2f))
            cubicTo(w * 0.35f, h * (y - 0.1f), w * 0.55f, h * (y + 0.12f), w * 1.1f, h * (y - 0.2f))
        }
        drawPath(
            path,
            Brush.linearGradient(listOf(color.copy(alpha = 0.2f), color, color.copy(alpha = 0.3f))),
            alpha = 0.7f,
            style = Stroke(width = min(w, h) * thickness, cap = StrokeCap.Round),
        )
    }
}

private fun lerpToWhite(c: Color, t: Float) = Color(c.red + (1 - c.red) * t, c.green + (1 - c.green) * t, c.blue + (1 - c.blue) * t, c.alpha)
private fun lerpToBlack(c: Color, t: Float) = Color(c.red * (1 - t), c.green * (1 - t), c.blue * (1 - t), c.alpha)
