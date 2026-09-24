package com.ihy2ln.weaverse.feature.roleplay.chat

import kotlin.math.sqrt
import kotlin.math.abs

/** Geometric constraints applied after vision: one passage, one local placement area. */
object MangaLetteringPlacement {
    fun preferredSize(region: PanelTextRegion, pageAspect: Float): Float {
        if (region.fontSizePx > 0f) return region.fontSizePx
        val w = region.cleanupW.takeIf { it > 0f } ?: region.w
        val h = region.cleanupH.takeIf { it > 0f } ?: region.h
        val characters = region.original.count { !it.isWhitespace() }.coerceAtLeast(4)
        return sqrt(w * h * 1_000_000f / pageAspect.coerceAtLeast(.1f) / characters).coerceIn(18f, 38f)
    }

    fun constrain(regions: List<PanelTextRegion>, pageAspect: Float): List<PanelTextRegion> {
        val placed = regions.map { r ->
            if (r.edited) r else {
                val sx = r.cleanupX.takeIf { it >= 0f } ?: r.x
                val sy = r.cleanupY.takeIf { it >= 0f } ?: r.y
                val sw = r.cleanupW.takeIf { it > 0f } ?: r.w
                val sh = r.cleanupH.takeIf { it > 0f } ?: r.h
                val cx = sx + sw / 2f; val cy = sy + sh / 2f
                // Open white backgrounds are not giant speech bubbles. Stay near the source.
                val w = minOf(r.w, maxOf(sw * 1.6f, .16f)).coerceIn(.01f, 1f)
                val h = minOf(r.h, maxOf(sh * 1.35f, .07f)).coerceIn(.01f, 1f)
                val x = (cx - w / 2f).coerceIn(r.x.coerceIn(0f, 1f - w), (r.x + r.w - w).coerceIn(r.x.coerceIn(0f, 1f - w), 1f - w))
                val y = (cy - h / 2f).coerceIn(r.y.coerceIn(0f, 1f - h), (r.y + r.h - h).coerceIn(r.y.coerceIn(0f, 1f - h), 1f - h))
                r.copy(x = x, y = y, w = w, h = h, fontSizePx = preferredSize(r, pageAspect), autoFit = true,
                    cleanupX = sx, cleanupY = sy, cleanupW = sw, cleanupH = sh)
            }
        }.toMutableList()
        for (i in placed.indices) for (j in i + 1 until placed.size) {
            var a = placed[i]; var b = placed[j]
            if (a.edited || b.edited || !overlap(a, b)) continue
            val ax = a.cleanupX + a.cleanupW / 2; val bx = b.cleanupX + b.cleanupW / 2
            val ay = a.cleanupY + a.cleanupH / 2; val by = b.cleanupY + b.cleanupH / 2
            if (abs(ax - bx) > abs(ay - by)) {
                val cut = (ax + bx) / 2
                fun trim(r: PanelTextRegion, left: Boolean): PanelTextRegion {
                    val x = if (left) r.x else maxOf(r.x, cut + .004f)
                    val right = if (left) minOf(r.x + r.w, cut - .004f) else r.x + r.w
                    return if (right - x < .025f) r.copy(reviewRequired = true) else r.copy(x = x, w = right - x)
                }
                a = trim(a, ax < bx); b = trim(b, bx < ax)
            } else if (abs(ay - by) > .01f) {
                val cut = (ay + by) / 2
                fun trim(r: PanelTextRegion, above: Boolean): PanelTextRegion {
                    val y = if (above) r.y else maxOf(r.y, cut + .003f)
                    val bottom = if (above) minOf(r.y + r.h, cut - .003f) else r.y + r.h
                    return if (bottom - y < .02f) r.copy(reviewRequired = true) else r.copy(y = y, h = bottom - y)
                }
                a = trim(a, ay < by); b = trim(b, by < ay)
            } else { a = a.copy(reviewRequired = true); b = b.copy(reviewRequired = true) }
            placed[i] = a; placed[j] = b
        }
        return placed
    }

    private fun overlap(a: PanelTextRegion, b: PanelTextRegion) =
        a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h
}
