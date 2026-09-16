package com.ihy2ln.weaverse.core.text

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

data class OverlayFrame(val x: Float, val y: Float, val width: Float, val height: Float)

/** Pixel-space transforms after Compose has removed the parent page zoom/pan. */
object OverlayGeometry {
    private fun extents(frame: OverlayFrame, rotation: Float): Pair<Float, Float> {
        val angle = Math.toRadians(rotation.toDouble())
        return (abs(cos(angle)) * frame.width / 2 + abs(sin(angle)) * frame.height / 2).toFloat() to
            (abs(sin(angle)) * frame.width / 2 + abs(cos(angle)) * frame.height / 2).toFloat()
    }
    fun move(frame: OverlayFrame, dx: Float, dy: Float, rotation: Float, pageWidth: Float, pageHeight: Float): OverlayFrame {
        val (halfW, halfH) = extents(frame, rotation)
        return frame.copy(
            x = if (halfW * 2 > pageWidth) pageWidth / 2 else (frame.x + dx).coerceIn(halfW, pageWidth - halfW),
            y = if (halfH * 2 > pageHeight) pageHeight / 2 else (frame.y + dy).coerceIn(halfH, pageHeight - halfH),
        )
    }
    fun resize(frame: OverlayFrame, dx: Float, dy: Float, axisX: Int, axisY: Int, rotation: Float,
        pageWidth: Float, pageHeight: Float): OverlayFrame {
        val dw = if (axisX == 0) 0f else (dx * axisX).coerceAtLeast(1f - frame.width)
        val dh = if (axisY == 0) 0f else (dy * axisY).coerceAtLeast(1f - frame.height)
        val angle = Math.toRadians(rotation.toDouble())
        fun candidate(t: Float): OverlayFrame {
            val x = dw * axisX * t / 2
            val y = dh * axisY * t / 2
            return OverlayFrame(frame.x + (x * cos(angle) - y * sin(angle)).toFloat(),
                frame.y + (x * sin(angle) + y * cos(angle)).toFloat(), frame.width + dw * t, frame.height + dh * t)
        }
        fun inside(f: OverlayFrame): Boolean {
            val (w, h) = extents(f, rotation)
            return f.x - w >= -.01f && f.y - h >= -.01f && f.x + w <= pageWidth + .01f && f.y + h <= pageHeight + .01f
        }
        val result = candidate(1f)
        if (inside(result)) return result
        // Do not jump a legacy out-of-bounds frame during an otherwise independent resize.
        if (!inside(frame)) return frame
        var low = 0f; var high = 1f
        repeat(20) { val mid = (low + high) / 2; if (inside(candidate(mid))) low = mid else high = mid }
        return candidate(low)
    }
}
