package com.ihy2ln.weaverse.core.media

import kotlin.math.roundToInt

object MangaColorPolicy {
    const val DEFAULT_GUIDE = "Flat, restrained colors. Follow existing shading and screentones. No added lighting or painterly effects."

    fun prompt(guide: String): String = """
        COLORIZATION ONLY. The attached manga page is the authoritative drawing, not inspiration.
        Preserve exactly the composition, framing, aspect ratio, panel borders, faces, eyes, anatomy,
        expressions, poses, clothing, objects, line thickness, hatching, original shadows, screentones,
        speech bubbles, blank gutters and every letter. Do not redraw, beautify, simplify, translate,
        add or remove content. Preserve already-colored areas. Do not impose a studio or artist style,
        Ghibli-like shading, watercolor, painterly texture, cinematic lighting, bloom or new shadows.
        Apply color to existing shapes only. Return one complete page, with unchanged geometry.
        Palette and color-treatment preferences (subordinate to preserving the source drawing):
        ${guide.take(2000).ifBlank { DEFAULT_GUIDE }}
    """.trimIndent()

    /** Transfer chroma only; original luminance/alpha and already-colored pixels are retained. */
    fun colorPixel(original: Int, generated: Int): Int {
        val r = original ushr 16 and 255; val g = original ushr 8 and 255; val b = original and 255
        if (maxOf(r, g, b) - minOf(r, g, b) > 16) return original
        val y = .299 * r + .587 * g + .114 * b
        val cr = generated ushr 16 and 255; val cg = generated ushr 8 and 255; val cb = generated and 255
        val cy = .299 * cr + .587 * cg + .114 * cb
        val delta = doubleArrayOf(cr - cy, cg - cy, cb - cy)
        var scale = 1.0
        for (d in delta) {
            if (d > 0) scale = minOf(scale, (255 - y) / d)
            if (d < 0) scale = minOf(scale, -y / d)
        }
        fun channel(d: Double) = (y + d * scale).roundToInt().coerceIn(0, 255)
        return (original and -0x1000000) or (channel(delta[0]) shl 16) or
            (channel(delta[1]) shl 8) or channel(delta[2])
    }
}
