package com.ihy2ln.weaverse.feature.roleplay.chat

import android.graphics.Canvas
import android.graphics.RectF
import com.ihy2ln.weaverse.core.media.LetteringRenderer

/** Uses exactly the same layout engine as preview/export; no duplicate character-count fit. */
object MangaLetteringValidator {
    /**
     * Indexes (into [regions]) of lines whose English does not fit its balloon at a readable
     * size — the ones worth rewording shorter rather than shrinking further.
     */
    fun overflowing(regions: List<PanelTextRegion>, width: Int, height: Int): List<Int> =
        regions.indices.filter { index ->
            val r = regions[index]
            if (!r.visible || r.translation.isBlank()) return@filter false
            var metric: LetteringRenderer.Metrics? = null
            LetteringRenderer.draw(Canvas(), width.toFloat(), height.toFloat(), r.toTypesetLayer()) { metric = it }
            val m = metric ?: return@filter false
            m.overflow || m.fontSize < width * .012f
        }

    fun problems(regions: List<PanelTextRegion>, width: Int, height: Int): List<String> {
        val problems = mutableListOf<String>()
        val measured = regions.filter { it.visible && it.translation.isNotBlank() }.mapIndexedNotNull { index, r ->
            var metric: LetteringRenderer.Metrics? = null
            LetteringRenderer.draw(Canvas(), width.toFloat(), height.toFloat(), r.toTypesetLayer()) { metric = it }
            val m = metric ?: return@mapIndexedNotNull null
            val safe = RectF(r.x * width, r.y * height, (r.x + r.w) * width, (r.y + r.h) * height)
            if (r.reviewRequired || m.overflow || m.fontSize < width * .012f || !safe.contains(m.bounds))
                problems += "Passage ${index + 1}: text is too small, clipped, or outside its safe area. Resize/reposition this passage."
            index to m.bounds
        }
        for (i in measured.indices) for (j in i + 1 until measured.size) {
            if (RectF.intersects(measured[i].second, measured[j].second))
                problems += "Passages ${measured[i].first + 1} and ${measured[j].first + 1} overlap. Separate their placement."
        }
        return problems
    }
}
