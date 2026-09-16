package com.ihy2ln.weaverse.feature.roleplay.chat

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import com.ihy2ln.weaverse.core.media.ImageOps

/** A draft pixel mask, independent of all translated-text frames. Pixels change only on Apply. */
internal class CleanupDraft(val target: Bitmap, originalPath: String) {
    val mask = Bitmap.createBitmap(target.width, target.height, Bitmap.Config.ARGB_8888)
    val original = originalPath.takeIf { it.isNotBlank() }?.let { ImageOps.loadBitmap(it) }
        ?.takeIf { it.width == target.width && it.height == target.height }
    var revision by mutableIntStateOf(0)
    var subtract by mutableStateOf(false)
    var preview by mutableStateOf(true)
    private val undo = ArrayDeque<Bitmap>()
    private val redo = ArrayDeque<Bitmap>()
    fun push() {
        undo.addLast(mask.copy(Bitmap.Config.ARGB_8888, false))
        if (undo.size > 8) undo.removeFirst().recycle()
        redo.forEach(Bitmap::recycle); redo.clear()
    }
    private fun replace(source: Bitmap) {
        mask.eraseColor(0); Canvas(mask).drawBitmap(source, 0f, 0f, null); revision++
    }
    fun undo() { if (undo.isNotEmpty()) { redo.addLast(mask.copy(Bitmap.Config.ARGB_8888, false)); undo.removeLast().let { replace(it); it.recycle() } } }
    fun redo() { if (redo.isNotEmpty()) { undo.addLast(mask.copy(Bitmap.Config.ARGB_8888, false)); redo.removeLast().let { replace(it); it.recycle() } } }
    fun clear() { mask.eraseColor(0); revision++ }
    fun stroke(from: Offset, to: Offset, radius: Float) {
        val canvas = Canvas(mask)
        val paint = Paint().apply {
            color = 0x88c45cff.toInt(); strokeWidth = radius * 2
            strokeCap = Paint.Cap.ROUND
            if (subtract) xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        canvas.drawLine(from.x, from.y, to.x, to.y, paint)
        canvas.drawCircle(to.x, to.y, radius, paint)
        revision++
    }
    fun selection(): BooleanArray {
        val pixels = IntArray(mask.width * mask.height)
        mask.getPixels(pixels, 0, mask.width, 0, 0, mask.width, mask.height)
        return BooleanArray(pixels.size) { pixels[it] ushr 24 != 0 }
    }
    fun fill(color: Int, restore: Boolean = false) {
        val pixels = IntArray(target.width * target.height)
        target.getPixels(pixels, 0, target.width, 0, 0, target.width, target.height)
        val source = if (restore) IntArray(pixels.size).also {
            original?.getPixels(it, 0, target.width, 0, 0, target.width, target.height)
        } else null
        selection().forEachIndexed { i, selected -> if (selected) pixels[i] = source?.get(i) ?: color }
        target.setPixels(pixels, 0, target.width, 0, 0, target.width, target.height)
    }
}

@Composable
internal fun CleanupMaskTools(draft: CleanupDraft, sampledColor: Int, before: () -> Unit, changed: () -> Unit,
    cleanupRects: List<android.graphics.RectF>) {
    var confirmRebuild by remember { mutableStateOf(false) }
    if (confirmRebuild) AlertDialog(onDismissRequest = { confirmRebuild = false },
        title = { Text("Rebuild cleanup from original?") },
        text = { Text("This replaces the current editor preview with the original image and reapplies only source-lettering cleanup. Translations stay in place. Nothing is saved until Save copy; Undo restores this preview.") },
        confirmButton = { TextButton(onClick = {
            draft.original?.let { before(); Canvas(draft.target).drawBitmap(it, 0f, 0f, null)
                ImageOps.replaceTextRegions(draft.target, cleanupRects); changed() }
            confirmRebuild = false
        }) { Text("Rebuild preview") } }, dismissButton = { TextButton(onClick = { confirmRebuild = false }) { Text("Cancel") } })
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        FilterChip(!draft.subtract, { draft.subtract = false }, label = { Text("Add mask") })
        FilterChip(draft.subtract, { draft.subtract = true }, label = { Text("Subtract mask") })
        FilterChip(draft.preview, { draft.preview = !draft.preview }, label = { Text("Preview mask") })
        TextButton(onClick = { draft.undo() }) { Text("Undo mask") }
        TextButton(onClick = { draft.redo() }) { Text("Redo mask") }
        TextButton(onClick = { before(); draft.fill(sampledColor); changed() }) { Text("Fill sampled color") }
        TextButton(onClick = { before(); ImageOps.inpaintMasked(draft.target, draft.selection()); changed() }) { Text("Repair masked art") }
        TextButton(enabled = draft.original != null, onClick = { before(); draft.fill(0, restore = true); changed() }) { Text("Restore original in mask") }
        TextButton(onClick = { draft.push(); draft.clear() }) { Text("Clear mask") }
        TextButton(enabled = draft.original != null, onClick = { confirmRebuild = true }) { Text("Rebuild from original") }
    }
}

@Composable
internal fun CleanupMaskLayer(draft: CleanupDraft, viewSize: IntSize, radius: Float) {
    val revision = draft.revision
    if (draft.preview) key(revision) {
        Image(draft.mask.asImageBitmap(), "Cleanup mask preview", Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
    }
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().pointerInput(draft, viewSize, radius, draft.subtract) {
        fun point(p: Offset) = Offset(p.x * draft.target.width / viewSize.width.coerceAtLeast(1),
            p.y * draft.target.height / viewSize.height.coerceAtLeast(1))
        var last = Offset.Zero
        detectDragGestures(onDragStart = { p -> draft.push(); last = point(p); draft.stroke(last, last, radius) },
            onDrag = { change, _ -> change.consume(); val next = point(change.position); draft.stroke(last, next, radius); last = next })
    })
}
