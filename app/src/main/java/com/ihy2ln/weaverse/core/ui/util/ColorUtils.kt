package com.ihy2ln.weaverse.core.ui.util

import androidx.compose.ui.graphics.Color

fun parseHexColor(hex: String?, fallback: Color = Color.Gray): Color {
    if (hex.isNullOrBlank()) return fallback
    val cleaned = hex.removePrefix("#")
    val value = cleaned.toLongOrNull(16) ?: return fallback
    return when (cleaned.length) {
        6 -> Color(0xFF000000 or value)
        8 -> Color(value)
        else -> fallback
    }
}
