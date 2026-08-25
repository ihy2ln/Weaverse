package com.ihy2ln.weaverse.core.ui.util

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

fun parseHexColor(hex: String?, fallback: Color = Color.Gray): Color {
    if (hex.isNullOrBlank()) return fallback
    val cleaned = hex.trim().removePrefix("#")
    val value = cleaned.toLongOrNull(16) ?: return fallback
    return when (cleaned.length) {
        3 -> {
            val r = ((value shr 8) and 0xF).toInt() * 0x11
            val g = ((value shr 4) and 0xF).toInt() * 0x11
            val b = (value and 0xF).toInt() * 0x11
            Color(r, g, b)
        }
        6 -> argbColor(
            red = ((value shr 16) and 0xFF).toInt(),
            green = ((value shr 8) and 0xFF).toInt(),
            blue = (value and 0xFF).toInt(),
        )
        8 -> argbColor(
            red = ((value shr 16) and 0xFF).toInt(),
            green = ((value shr 8) and 0xFF).toInt(),
            blue = (value and 0xFF).toInt(),
            alpha = ((value shr 24) and 0xFF).toInt(),
        )
        else -> fallback
    }
}

/** sRGB hex from Compose color components — never [Color.value], which is not ARGB. */
fun Color.toRgbHexString(): String {
    val r = (red * 255f).roundToInt().coerceIn(0, 255)
    val g = (green * 255f).roundToInt().coerceIn(0, 255)
    val b = (blue * 255f).roundToInt().coerceIn(0, 255)
    return "#%02X%02X%02X".format(r, g, b)
}

private fun argbColor(red: Int, green: Int, blue: Int, alpha: Int = 255): Color =
    Color(red, green, blue, alpha)
