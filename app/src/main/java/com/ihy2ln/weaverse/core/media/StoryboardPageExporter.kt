package com.ihy2ln.weaverse.core.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.TextPaint
import com.ihy2ln.weaverse.core.text.TextOverlay
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

data class StoryboardExportPanel(
    val path: String,
    val col: Int,
    val row: Int,
    val colSpan: Int,
    val rowSpan: Int,
    val rotationDeg: Float = 0f,
    val overlays: List<TextOverlay> = emptyList(),
)

/** Renders one saved storyboard page into a portable PNG in app-local storage. */
@Singleton
class StoryboardPageExporter @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
) {
    suspend fun export(
        pageId: String,
        title: String,
        templateId: String,
        panels: List<StoryboardExportPanel>,
    ): File {
        val width = 1200
        val height = 1800
        val grid = 12f
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(30, 30, 38)
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        panels.forEach { panel ->
            val left = panel.col / grid * width
            val top = panel.row / grid * height
            val right = (panel.col + panel.colSpan) / grid * width
            val bottom = (panel.row + panel.rowSpan) / grid * height
            val rect = RectF(left, top, right, bottom)
            val source = BitmapFactory.decodeFile(panel.path) ?: return@forEach
            canvas.save()
            canvas.rotate(panel.rotationDeg, rect.centerX(), rect.centerY())
            drawCenterCrop(canvas, source, rect)
            canvas.drawRect(rect, border)
            drawOverlays(canvas, rect, panel.overlays)
            canvas.restore()
            source.recycle()
        }
        val exportDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "exports/storyboard")
            .also { it.mkdirs() }
        val safeTitle = title.replace(Regex("[^a-zA-Z0-9._-]+"), "_").trim('_').ifBlank { "page" }
        val output = File(exportDir, "$safeTitle-$pageId.png")
        FileOutputStream(output).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        bitmap.recycle()
        return output
    }

    private fun drawCenterCrop(canvas: Canvas, source: Bitmap, target: RectF) {
        val sourceRatio = source.width.toFloat() / max(1, source.height)
        val targetRatio = target.width() / max(1f, target.height())
        val src = if (sourceRatio > targetRatio) {
            val cropWidth = (source.height * targetRatio).toInt().coerceAtLeast(1)
            val left = (source.width - cropWidth) / 2
            Rect(left, 0, left + cropWidth, source.height)
        } else {
            val cropHeight = (source.width / targetRatio).toInt().coerceAtLeast(1)
            val top = (source.height - cropHeight) / 2
            Rect(0, top, source.width, top + cropHeight)
        }
        canvas.drawBitmap(source, src, target, Paint(Paint.ANTI_ALIAS_FLAG))
    }

    private fun drawOverlays(canvas: Canvas, panel: RectF, overlays: List<TextOverlay>) {
        overlays.forEach { overlay ->
            if (overlay.text.isBlank()) return@forEach
            val centerX = panel.left + panel.width() * overlay.xPercent / 100f
            val centerY = panel.top + panel.height() * overlay.yPercent / 100f
            val boxWidth = panel.width() * overlay.widthPercent / 100f
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = parseColor(overlay.colorHex, android.graphics.Color.WHITE)
                textSize = overlay.fontSizeSp * 2.5f
            }
            val lines = wrap(overlay.text, textPaint, boxWidth - 24f)
            val lineHeight = textPaint.fontMetrics.descent - textPaint.fontMetrics.ascent
            val boxHeight = max(lineHeight + 20f, lines.size * lineHeight + 24f)
            val box = RectF(
                centerX - boxWidth / 2f,
                centerY - boxHeight / 2f,
                centerX + boxWidth / 2f,
                centerY + boxHeight / 2f,
            )
            val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = parseColor(overlay.backgroundHex, android.graphics.Color.TRANSPARENT)
                alpha = (overlay.backgroundAlpha.coerceIn(0f, 1f) * 255).toInt()
            }
            canvas.drawRoundRect(box, 22f, 22f, background)
            var y = box.top + 12f - textPaint.fontMetrics.ascent
            lines.forEach { line ->
                canvas.drawText(line, box.left + 12f, y, textPaint)
                y += lineHeight
            }
        }
    }

    private fun wrap(text: String, paint: TextPaint, maxWidth: Float): List<String> {
        val words = text.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        if (words.isEmpty()) return emptyList()
        val lines = mutableListOf<String>()
        var line = ""
        words.forEach { word ->
            val next = if (line.isBlank()) word else "$line $word"
            if (paint.measureText(next) <= maxWidth || line.isBlank()) {
                line = next
            } else {
                lines += line
                line = word
            }
        }
        if (line.isNotBlank()) lines += line
        return lines
    }

    private fun parseColor(value: String?, fallback: Int): Int = runCatching {
        android.graphics.Color.parseColor(value ?: return@runCatching fallback)
    }.getOrDefault(fallback)
}
