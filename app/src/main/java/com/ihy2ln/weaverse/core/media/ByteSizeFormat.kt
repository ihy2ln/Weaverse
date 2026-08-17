package com.ihy2ln.weaverse.core.media

import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

/** Human-readable byte size for the media library total (spec §7: "Show total media size in Settings"). */
fun formatByteSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    val exponent = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(1, units.size)
    val value = bytes / 1024.0.pow(exponent.toDouble())
    return String.format(Locale.ROOT, "%.1f %s", value, units[exponent - 1])
}
