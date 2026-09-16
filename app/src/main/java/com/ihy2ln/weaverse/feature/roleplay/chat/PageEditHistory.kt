package com.ihy2ln.weaverse.feature.roleplay.chat

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** One chronological history for paint, cleanup masks and editable lettering. */
internal class PageEditHistory(private val image: Bitmap, private val mask: Bitmap) {
    private data class Entry(val image: Bitmap, val mask: Bitmap, val regions: List<PanelTextRegion>) {
        fun dispose() { image.recycle(); mask.recycle() }
    }
    private val past = ArrayDeque<Entry>()
    private val future = ArrayDeque<Entry>()
    private var revision by mutableIntStateOf(0)
    val canUndo: Boolean get() { revision; return past.isNotEmpty() }
    val canRedo: Boolean get() { revision; return future.isNotEmpty() }
    // Avoid holding a dozen full-resolution manga pages in memory.
    private val capacity = (48L * 1024 * 1024 / (image.allocationByteCount.toLong() + mask.allocationByteCount))
        .toInt().coerceIn(1, 12)
    private fun capture(regions: List<PanelTextRegion>) = Entry(
        image.copy(Bitmap.Config.ARGB_8888, false), mask.copy(Bitmap.Config.ARGB_8888, false), regions.toList())
    fun checkpoint(regions: List<PanelTextRegion>) {
        past.addLast(capture(regions))
        while (past.size > capacity) past.removeFirst().dispose()
        future.forEach { it.dispose() }; future.clear(); revision++
    }
    private fun restore(entry: Entry): List<PanelTextRegion> {
        val paint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) }
        Canvas(image).drawBitmap(entry.image, 0f, 0f, paint)
        Canvas(mask).drawBitmap(entry.mask, 0f, 0f, paint)
        entry.dispose(); revision++
        return entry.regions
    }
    fun undo(regions: List<PanelTextRegion>): List<PanelTextRegion>? {
        if (past.isEmpty()) return null
        future.addLast(capture(regions))
        return restore(past.removeLast())
    }
    fun redo(regions: List<PanelTextRegion>): List<PanelTextRegion>? {
        if (future.isEmpty()) return null
        past.addLast(capture(regions))
        return restore(future.removeLast())
    }
    fun dispose() { past.forEach { it.dispose() }; future.forEach { it.dispose() }; past.clear(); future.clear() }
}
