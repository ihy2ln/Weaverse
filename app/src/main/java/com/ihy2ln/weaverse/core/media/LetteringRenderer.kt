package com.ihy2ln.weaverse.core.media

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.text.BreakIterator
import kotlin.math.floor

/** Shared by live previews and export. Returns true when manual lettering overflows. */
object LetteringRenderer {
    data class Metrics(val bounds: RectF, val fontSize: Float, val overflow: Boolean)
    fun draw(canvas: Canvas, pageWidth: Float, pageHeight: Float, layer: TypesetLayer, report: ((Metrics) -> Unit)? = null): Boolean {
        if (!layer.visible || layer.text.isBlank()) return false
        val box = RectF(layer.normalized.left * pageWidth, layer.normalized.top * pageHeight,
            layer.normalized.right * pageWidth, layer.normalized.bottom * pageHeight)
        if (box.width() <= 0f || box.height() <= 0f) return true
        val pad = minOf(box.width(), box.height()) * layer.paddingFraction.coerceIn(0f, .45f)
        val width = (box.width() - pad * 2).coerceAtLeast(1f)
        val height = (box.height() - pad * 2).coerceAtLeast(1f)
        val text = if (layer.uppercase) layer.text.uppercase() else layer.text
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = layer.fillColor
            val family = layer.fontFamily.takeIf { it in setOf("sans-serif", "serif", "monospace", "cursive") } ?: "sans-serif"
            typeface = Typeface.create(family, (if (layer.bold) Typeface.BOLD else Typeface.NORMAL) or
                (if (layer.italic) Typeface.ITALIC else Typeface.NORMAL))
        }
        val spacing = layer.lineSpacing.coerceIn(.5f, 3f)
        fun horizontal(): StaticLayout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
            .setAlignment(when (layer.alignment) {
                TypesetAlign.Start -> Layout.Alignment.ALIGN_NORMAL
                TypesetAlign.End -> Layout.Alignment.ALIGN_OPPOSITE
                TypesetAlign.Center -> Layout.Alignment.ALIGN_CENTER
            }).setIncludePad(false).setLineSpacing(0f, spacing)
            .setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED).build()
        // Grapheme boundaries preserve surrogate pairs and combining marks as upright glyphs.
        val iterator = BreakIterator.getCharacterInstance().apply { setText(text) }
        val glyphs = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            glyphs += text.substring(start, end)
            start = end; end = iterator.next()
        }
        fun columns(): List<List<String>> {
            val rows = floor(height / (paint.fontSpacing * spacing)).toInt().coerceAtLeast(1)
            val result = mutableListOf<MutableList<String>>(mutableListOf())
            glyphs.forEach { glyph ->
                if (glyph == "\n") result += mutableListOf<String>()
                else {
                    if (result.last().size >= rows) result += mutableListOf<String>()
                    result.last() += glyph
                }
            }
            return result
        }
        fun fits(): Boolean = if (layer.writing == TypesetWriting.Vertical) {
            val cell = maxOf(paint.textSize, glyphs.maxOfOrNull { paint.measureText(it) } ?: 0f) * spacing
            columns().size * cell <= width && paint.fontSpacing * spacing <= height
        } else {
            val layout = horizontal()
            layout.height <= height && (0 until layout.lineCount).all { layout.getLineWidth(it) <= width }
        }
        val scale = if (layer.referenceWidth > 0f) pageWidth / layer.referenceWidth else 1f
        paint.textSize = (layer.fontSizePx * scale).coerceAtLeast(1f)
        if (layer.autoFit || layer.fontSizePx <= 0f) {
            var low = .1f
            // Fit is shrink-only, not a request to inflate short passages to fill a balloon.
            var high = if (layer.fontSizePx > 0f) layer.fontSizePx * scale else 28f * pageWidth / 1000f
            repeat(18) {
                paint.textSize = (low + high) / 2f
                if (fits()) low = paint.textSize else high = paint.textSize
            }
            paint.textSize = low
        }
        val overflow = !fits()
        val textBounds = if (layer.writing == TypesetWriting.Vertical) RectF(box) else {
            val layout = horizontal()
            val left = (0 until layout.lineCount).minOfOrNull { layout.getLineLeft(it) } ?: 0f
            val right = (0 until layout.lineCount).maxOfOrNull { layout.getLineRight(it) } ?: 0f
            RectF(box.left + pad + left, box.centerY() - layout.height / 2f,
                box.left + pad + right, box.centerY() + layout.height / 2f)
        }
        android.graphics.Matrix().apply { setRotate(layer.rotationDeg, box.centerX(), box.centerY()) }.mapRect(textBounds)
        report?.invoke(Metrics(textBounds, paint.textSize, overflow))
        val saved = canvas.save()
        canvas.rotate(layer.rotationDeg, box.centerX(), box.centerY())
        canvas.clipRect(box)
        if (layer.writing == TypesetWriting.Vertical) {
            val columns = columns()
            val cell = maxOf(paint.textSize, glyphs.maxOfOrNull { paint.measureText(it) } ?: 0f) * spacing
            val x0 = box.centerX() + (columns.size - 1) * cell / 2f
            paint.textAlign = Paint.Align.CENTER
            columns.forEachIndexed { col, glyphsInColumn ->
                val blockHeight = glyphsInColumn.size * paint.fontSpacing * spacing
                val top = when (layer.alignment) {
                    TypesetAlign.Start -> box.top + pad
                    TypesetAlign.End -> box.bottom - pad - blockHeight
                    TypesetAlign.Center -> box.centerY() - blockHeight / 2f
                }
                glyphsInColumn.forEachIndexed { row, glyph ->
                    val y = top - paint.ascent() + row * paint.fontSpacing * spacing
                    if (layer.strokeWidthPx > 0f) {
                        paint.style = Paint.Style.STROKE; paint.color = layer.strokeColor
                        paint.strokeWidth = layer.strokeWidthPx * scale
                        canvas.drawText(glyph, x0 - col * cell, y, paint)
                    }
                    paint.style = Paint.Style.FILL; paint.color = layer.fillColor
                    canvas.drawText(glyph, x0 - col * cell, y, paint)
                }
            }
        } else {
            val layout = horizontal()
            canvas.translate(box.left + pad, box.centerY() - layout.height / 2f)
            if (layer.strokeWidthPx > 0f) {
                paint.style = Paint.Style.STROKE; paint.color = layer.strokeColor
                paint.strokeWidth = layer.strokeWidthPx * scale
                layout.draw(canvas)
            }
            paint.style = Paint.Style.FILL; paint.color = layer.fillColor
            layout.draw(canvas)
        }
        canvas.restoreToCount(saved)
        return overflow
    }
}
