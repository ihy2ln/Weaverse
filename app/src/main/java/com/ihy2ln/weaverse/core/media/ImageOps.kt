package com.ihy2ln.weaverse.core.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Typeface
import java.io.ByteArrayOutputStream
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * First bitmap toolkit in the app: decode, crop, erase and re-encode pictures
 * for the Storyboard picture editor and AI panel separation. Everything here
 * is plain android.graphics — outputs flow through MediaRepository.
 */
object ImageOps {

    /** Decodes a bitmap, downsampling so the long edge is at most [maxDim]. */
    fun loadBitmap(path: String, maxDim: Int = 2200): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
        var sample = 1
        val longEdge = max(opts.outWidth, opts.outHeight)
        while (longEdge / (sample * 2) >= maxDim / 2) sample *= 2
        val decode = BitmapFactory.Options().apply {
            inSampleSize = sample
            inMutable = true
        }
        return BitmapFactory.decodeFile(path, decode)
    }

    /** True when nearly every opaque sampled pixel has no meaningful chroma. */
    fun isMostlyGrayscale(target: Bitmap): Boolean {
        val sample = scaleDown(target, 180)
        val pixels = IntArray(sample.width * sample.height)
        return try {
            sample.getPixels(pixels, 0, sample.width, 0, 0, sample.width, sample.height)
            isMostlyGrayscaleArgb(pixels)
        } finally {
            if (sample !== target) sample.recycle()
        }
    }

    /**
     * Gives monochrome manga a deterministic multi-hue base while retaining
     * black ink, screentone values and white speech bubbles. This is the local,
     * zero-cost fallback; users can refine the derived result with the brush.
     */
    fun applyMangaColorization(target: Bitmap) {
        val pixels = IntArray(target.width * target.height)
        target.getPixels(pixels, 0, target.width, 0, 0, target.width, target.height)
        val colored = colorizeMangaArgb(target.width, target.height, pixels)
        target.setPixels(colored, 0, target.width, 0, 0, target.width, target.height)
    }

    /**
     * Types translations into the derived page. Lettering should already have
     * been removed with [inpaintTextGlyphsInRects]; this draws glyphs only, the
     * same way a manga typesetter paints over cleaned bubbles.
     */
    fun typesetTranslations(target: Bitmap, regions: List<Pair<RectF, String>>) {
        typesetLayers(
            target,
            regions.map { (normalized, text) ->
                TypesetLayer(normalized = normalized, text = text)
            },
        )
    }

    fun typesetLayers(target: Bitmap, layers: List<TypesetLayer>) {
        val canvas = Canvas(target)
        layers.forEach { layer ->
            if (!layer.visible) return@forEach
            val text = layer.text.trim()
            if (text.isBlank()) return@forEach
            LetteringRenderer.draw(canvas, target.width.toFloat(), target.height.toFloat(), layer)
        }
    }

    /**
     * Flattens editable layers into an arbitrary destination frame. Export is the only place
     * the English stops being movable, so this is deliberately separate from the page render.
     */
    fun typesetLayersOnCanvas(canvas: Canvas, frame: RectF, layers: List<TypesetLayer>) {
        if (frame.width() < 1f || frame.height() < 1f) return
        layers.forEach { layer ->
            if (!layer.visible || layer.text.isBlank()) return@forEach
            val saved = canvas.save()
            canvas.translate(frame.left, frame.top)
            LetteringRenderer.draw(canvas, frame.width(), frame.height(), layer)
            canvas.restoreToCount(saved)
        }
    }

    /** Lettering ink that stays readable on the container the source text sat in. */
    fun contrastingFillColor(backgroundLuminance: Int): Int =
        if (backgroundLuminance < 128) Color.WHITE else Color.BLACK

    /** The opposite ink, used as a thin outline so lettering survives busy artwork. */
    fun contrastingStrokeColor(backgroundLuminance: Int): Int =
        if (backgroundLuminance < 128) Color.BLACK else Color.WHITE

    fun colorIntToHex(color: Int): String = "#%06X".format(color and 0xffffff)

    /**
     * Widens each detected text box to the bubble that encloses it, so translations are
     * fitted to the shape a letterer would use. Returns frames in the order given.
     */
    fun bubbleFrames(source: Bitmap, rects: List<RectF>): List<RectF> {
        return textContainers(source, rects).map { it.box.toRectF() }
    }

    /** Resolves both light speech bubbles and reversed, dark caption boxes. */
    fun textContainers(source: Bitmap, rects: List<RectF>): List<TextContainerFrame> {
        if (rects.isEmpty()) return emptyList()
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        return rects.map { rect ->
            textContainerArgb(
                source.width,
                source.height,
                pixels,
                NormalizedPanelBox(rect.left, rect.top, rect.right, rect.bottom),
            )
        }
    }

    /**
     * Reconstructs artwork under normalized boxes. The boxes are inset so
     * bubble borders and panel frames stay intact.
     */
    fun inpaintNormalizedRects(target: Bitmap, rects: List<RectF>, insetFraction: Float = 0.08f) {
        if (rects.isEmpty()) return
        val mask = BooleanArray(target.width * target.height)
        rects.forEach { normalized ->
            val left = (normalized.left * target.width).roundToInt()
            val top = (normalized.top * target.height).roundToInt()
            val right = (normalized.right * target.width).roundToInt()
            val bottom = (normalized.bottom * target.height).roundToInt()
            val insetX = ((right - left) * insetFraction).roundToInt().coerceAtLeast(1)
            val insetY = ((bottom - top) * insetFraction).roundToInt().coerceAtLeast(1)
            markRectMask(
                mask,
                target.width,
                target.height,
                (left + insetX).coerceAtMost(right),
                (top + insetY).coerceAtMost(bottom),
                (right - insetX).coerceAtLeast(left),
                (bottom - insetY).coerceAtLeast(top),
            )
        }
        inpaintMasked(target, mask)
    }

    /**
     * Removes lettering inside detected text regions without blanking the whole
     * region. The surrounding edge pixels estimate the paper/background tone;
     * only contrasting, glyph-like pixels are masked and lightly dilated.
     *
     * This is the safe local fallback used before editable translation layers
     * are added. A configured generative image editor can still be used for
     * difficult artwork-backed captions.
     */
    /**
     * Removes source lettering one region at a time. A speech balloon or a caption box is a
     * flat colour, so the honest repair is to repaint the glyphs in that colour — the general
     * inpaint reconstructs from surrounding pixels and on a black caption it drags the light
     * page in and eats the box. Textured backgrounds (lettering over artwork) still inpaint.
     */
    fun inpaintTextGlyphsInRects(target: Bitmap, rects: List<RectF>) {
        if (rects.isEmpty()) return
        val width = target.width
        val height = target.height
        val pixels = IntArray(width * height)
        target.getPixels(pixels, 0, width, 0, 0, width, height)
        val remaining = BooleanArray(width * height)
        var filled = false
        rects.forEach { rect ->
            val box = NormalizedPanelBox(rect.left, rect.top, rect.right, rect.bottom)
            val mask = textGlyphMaskArgb(width, height, pixels, listOf(box))
            if (mask.none { it }) return@forEach
            val container = textContainerArgb(width, height, pixels, box).box
            val flat = flatContainerColorArgb(width, height, pixels, container, box, mask)
            val x0 = (container.left * width).roundToInt().coerceIn(0, width)
            val x1 = (container.right * width).roundToInt().coerceIn(x0, width)
            val y0 = (container.top * height).roundToInt().coerceIn(0, height)
            val y1 = (container.bottom * height).roundToInt().coerceIn(y0, height)
            // Cleanup never reaches outside the container it resolved. The search box is padded
            // and routinely overshoots the balloon or caption; repainting or reconstructing out
            // there drags the container colour into the artwork and notches the box edge. Source
            // text genuinely outside the container is left for verification and Whiteout.
            for (y in y0 until y1) {
                for (x in x0 until x1) {
                    val index = y * width + x
                    if (!mask[index]) continue
                    if (flat == null) {
                        remaining[index] = true
                    } else {
                        pixels[index] = flat
                        filled = true
                    }
                }
            }
        }
        if (filled) target.setPixels(pixels, 0, width, 0, 0, width, height)
        if (remaining.any { it }) inpaintMasked(target, remaining)
    }

    /**
     * Removes source lettering before a replacement is painted. Only the glyph mask
     * may be repaired: reconstructing the whole OCR rectangle destroys bubble borders
     * and nearby artwork. Residual lettering must be corrected with a targeted mask.
     */
    fun replaceTextRegions(target: Bitmap, rects: List<RectF>) {
        if (rects.isEmpty()) return
        val padded = rects.map { rect ->
            val padX = maxOf(0.006f, rect.width() * 0.10f).coerceAtMost(0.025f)
            val padY = maxOf(0.006f, rect.height() * 0.08f).coerceAtMost(0.025f)
            RectF(
                (rect.left - padX).coerceIn(0f, 1f),
                (rect.top - padY).coerceIn(0f, 1f),
                (rect.right + padX).coerceIn(0f, 1f),
                (rect.bottom + padY).coerceIn(0f, 1f),
            )
        }
        inpaintTextGlyphsInRects(target, padded)
    }

    fun inpaintMasked(target: Bitmap, mask: BooleanArray) {
        if (mask.size < target.width * target.height) return
        val pixels = IntArray(target.width * target.height)
        target.getPixels(pixels, 0, target.width, 0, 0, target.width, target.height)
        val filled = inpaintArgb(target.width, target.height, pixels, mask)
        target.setPixels(filled, 0, target.width, 0, 0, target.width, target.height)
    }

    fun markCircleMask(
        mask: BooleanArray,
        width: Int,
        height: Int,
        cx: Float,
        cy: Float,
        radius: Float,
    ) {
        val radiusSq = radius * radius
        val minX = (cx - radius).toInt().coerceAtLeast(0)
        val maxX = (cx + radius).toInt().coerceAtMost(width - 1)
        val minY = (cy - radius).toInt().coerceAtLeast(0)
        val maxY = (cy + radius).toInt().coerceAtMost(height - 1)
        for (y in minY..maxY) {
            for (x in minX..maxX) {
                val dx = x - cx
                val dy = y - cy
                if (dx * dx + dy * dy <= radiusSq) {
                    mask[y * width + x] = true
                }
            }
        }
    }

    private fun markRectMask(
        mask: BooleanArray,
        width: Int,
        height: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        val x0 = left.coerceIn(0, width)
        val y0 = top.coerceIn(0, height)
        val x1 = right.coerceIn(0, width)
        val y1 = bottom.coerceIn(0, height)
        if (x1 <= x0 || y1 <= y0) return
        for (y in y0 until y1) {
            val row = y * width
            for (x in x0 until x1) mask[row + x] = true
        }
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.replace('\n', ' ').split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        val lines = mutableListOf<String>()
        var current = ""
        words.forEach { word ->
            val candidate = if (current.isBlank()) word else "$current $word"
            if (current.isNotBlank() && paint.measureText(candidate) > maxWidth) {
                lines += current
                current = ""
            }
            if (current.isBlank() && paint.measureText(word) > maxWidth) {
                // A single long token must not overflow the bubble. Break it at glyph
                // boundaries as a final fallback; ordinary words still keep their spaces.
                var remainder = word
                while (remainder.isNotEmpty()) {
                    val count = paint.breakText(remainder, true, maxWidth, null).coerceAtLeast(1)
                    val chunk = remainder.take(count)
                    remainder = remainder.drop(count)
                    if (remainder.isNotEmpty()) lines += chunk else current = chunk
                }
            } else if (current.isBlank()) {
                current = word
            } else if (paint.measureText(current) <= maxWidth) {
                // The candidate was accepted above, so keep it as the active line.
                current = candidate
            }
        }
        if (current.isNotBlank()) lines += current
        return lines
    }

    private fun drawTypesetLayer(canvas: Canvas, pageWidth: Int, pageHeight: Int, layer: TypesetLayer) {
        val trimmed = layer.text.trim()
        if (trimmed.isBlank()) return
        // Comic lettering is set in caps; it also reads better than mixed case at the small
        // sizes a balloon forces.
        val text = if (layer.uppercase) trimmed.uppercase() else trimmed
        val rect = RectF(
            layer.normalized.left * pageWidth,
            layer.normalized.top * pageHeight,
            layer.normalized.right * pageWidth,
            layer.normalized.bottom * pageHeight,
        )
        // Placement frames are resolved from the actual light/dark container before this
        // method is called. Blindly widening a vertical OCR box can move English onto art.
        val pad = (minOf(rect.width(), rect.height()) * 0.08f).coerceAtLeast(2f)
        rect.left = rect.left.coerceAtLeast(0f)
        rect.top = rect.top.coerceAtLeast(0f)
        rect.right = rect.right.coerceAtMost(pageWidth.toFloat())
        rect.bottom = rect.bottom.coerceAtMost(pageHeight.toFloat())
        if (rect.width() < 8f || rect.height() < 8f) return

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = layer.fillColor
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = when (layer.alignment) {
                TypesetAlign.Start -> Paint.Align.LEFT
                TypesetAlign.End -> Paint.Align.RIGHT
                TypesetAlign.Center -> Paint.Align.CENTER
            }
        }
        val strokePaint = if (layer.strokeWidthPx > 0f) {
            Paint(fillPaint).apply {
                color = layer.strokeColor
                style = Paint.Style.STROKE
                strokeWidth = layer.strokeWidthPx
                strokeJoin = Paint.Join.ROUND
            }
        } else {
            null
        }

        if (layer.writing == TypesetWriting.Vertical) {
            drawVerticalTypeset(canvas, rect, pad, text, layer, fillPaint, strokePaint)
            return
        }

        val usableWidth = (rect.width() - pad * 2).coerceAtLeast(8f)
        val usableHeight = (rect.height() - pad * 2).coerceAtLeast(8f)
        var size = if (layer.autoFit || layer.fontSizePx <= 0f) {
            (rect.height() * 0.34f).coerceIn(10f, 72f)
        } else {
            layer.fontSizePx.coerceIn(8f, 96f)
        }
        var lines: List<String>
        do {
            fillPaint.textSize = size
            strokePaint?.textSize = size
            lines = wrapText(text, fillPaint, usableWidth)
            size -= 1f
        } while (
            layer.autoFit &&
                size > 8f &&
                lines.size * (fillPaint.fontSpacing * 0.92f) > usableHeight
        )
        val lineHeight = fillPaint.fontSpacing * 0.92f
        val blockHeight = lines.size * lineHeight
        var baseline = rect.centerY() - blockHeight / 2f - fillPaint.ascent()
        val x = when (layer.alignment) {
            TypesetAlign.Start -> rect.left + pad
            TypesetAlign.End -> rect.right - pad
            TypesetAlign.Center -> rect.centerX()
        }
        lines.forEach { line ->
            strokePaint?.let { canvas.drawText(line, x, baseline, it) }
            canvas.drawText(line, x, baseline, fillPaint)
            baseline += lineHeight
        }
    }

    private fun drawVerticalTypeset(
        canvas: Canvas,
        rect: RectF,
        pad: Float,
        text: String,
        layer: TypesetLayer,
        fillPaint: Paint,
        strokePaint: Paint?,
    ) {
        val glyphs = text.replace("\n", "").map { it.toString() }.filter { it.isNotBlank() }
        if (glyphs.isEmpty()) return
        fillPaint.textAlign = Paint.Align.CENTER
        strokePaint?.textAlign = Paint.Align.CENTER
        val usableHeight = (rect.height() - pad * 2).coerceAtLeast(8f)
        var size = if (layer.autoFit || layer.fontSizePx <= 0f) {
            (usableHeight / glyphs.size.coerceAtLeast(1)).coerceIn(10f, 64f)
        } else {
            layer.fontSizePx.coerceIn(8f, 96f)
        }
        do {
            fillPaint.textSize = size
            strokePaint?.textSize = size
            size -= 1f
        } while (layer.autoFit && size > 8f && glyphs.size * fillPaint.fontSpacing > usableHeight)
        val lineHeight = fillPaint.fontSpacing
        val blockHeight = glyphs.size * lineHeight
        val startY = rect.centerY() - blockHeight / 2f - fillPaint.ascent()
        val x = rect.centerX()
        glyphs.forEachIndexed { index, glyph ->
            val y = startY + index * lineHeight
            strokePaint?.let { canvas.drawText(glyph, x, y, it) }
            canvas.drawText(glyph, x, y, fillPaint)
        }
    }

    fun crop(src: Bitmap, rect: RectF): Bitmap {
        val left = (rect.left * src.width).roundToInt().coerceIn(0, src.width - 1)
        val top = (rect.top * src.height).roundToInt().coerceIn(0, src.height - 1)
        val width = max(1, (rect.width() * src.width).roundToInt())
            .coerceAtMost(src.width - left)
        val height = max(1, (rect.height() * src.height).roundToInt())
            .coerceAtMost(src.height - top)
        return Bitmap.createBitmap(src, left, top, width, height)
    }

    /** Erases (fills) a circle in bitmap coordinates with [color]. */
    fun eraseCircle(target: Bitmap, cx: Float, cy: Float, radius: Float, color: Int) {
        val canvas = Canvas(target)
        canvas.drawCircle(cx, cy, radius, Paint().apply { this.color = color })
    }

    /** Erases (fills) a rectangle in bitmap coordinates with [color]. */
    fun eraseRect(target: Bitmap, rect: RectF, color: Int) {
        val canvas = Canvas(target)
        canvas.drawRect(rect, Paint().apply { this.color = color })
    }

    /** Paints a translucent color mark without changing the source media. */
    fun paintCircle(
        target: Bitmap,
        cx: Float,
        cy: Float,
        radius: Float,
        color: Int,
        alpha: Int = 150,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.alpha = alpha.coerceIn(0, 255)
            style = Paint.Style.FILL
        }
        Canvas(target).drawCircle(cx, cy, radius, paint)
    }

    /**
     * Applies a restrained color wash to a derived bitmap. This is an
     * intentionally local fallback for devices without an image-to-image AI
     * provider; the original bitmap remains untouched and the result can be
     * discarded or edited with the color brush.
     */
    fun applyColorWash(target: Bitmap, color: Int, alpha: Int = 42) {
        Canvas(target).drawColor(
            android.graphics.Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color)),
            PorterDuff.Mode.SRC_ATOP,
        )
    }

    fun toPngBytes(bitmap: Bitmap): ByteArray =
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

    fun toJpegBytes(bitmap: Bitmap, quality: Int = 88): ByteArray =
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()

    /** Backwards-compatible box-only view of [detectPanelsByGuttersDetailed]. */
    fun detectPanelsByGutters(src: Bitmap): List<RectF> =
        detectPanelsByGuttersDetailed(src).boxes.map(NormalizedPanelBox::toRectF)

    /**
     * Offline separator diagnostics. This detector only looks for near-white,
     * page-spanning gutter runs; dark or irregular gutters are not promised.
     */
    fun detectPanelsByGuttersDetailed(src: Bitmap): OfflinePanelDetection {
        val small = scaleDown(src, 200)
        val w = small.width
        val h = small.height
        val pixels = IntArray(w * h)
        return try {
            small.getPixels(pixels, 0, w, 0, 0, w, h)
            detectPanelsFromArgb(w, h, pixels)
        } finally {
            if (small !== src) small.recycle()
        }
    }

    private fun scaleDown(src: Bitmap, targetWidth: Int): Bitmap {
        if (src.width <= targetWidth) return src
        val ratio = targetWidth.toFloat() / src.width
        return Bitmap.createScaledBitmap(
            src,
            targetWidth,
            max(1, (src.height * ratio).roundToInt()),
            true,
        )
    }
}

/** Pure glyph-mask builder so cleanup behavior can be tested without Android bitmaps. */
internal fun textGlyphMaskArgb(
    width: Int,
    height: Int,
    pixels: IntArray,
    rects: List<NormalizedPanelBox>,
): BooleanArray {
    val result = BooleanArray(width * height)
    if (width <= 0 || height <= 0 || pixels.size < width * height) return result
    fun luminance(pixel: Int): Int {
        val r = pixel ushr 16 and 0xff
        val g = pixel ushr 8 and 0xff
        val b = pixel and 0xff
        return (r * 30 + g * 59 + b * 11) / 100
    }
    rects.forEach { normalized ->
        val x0 = (normalized.left * width).roundToInt().coerceIn(0, width - 1)
        val y0 = (normalized.top * height).roundToInt().coerceIn(0, height - 1)
        val x1 = (normalized.right * width).roundToInt().coerceIn(x0 + 1, width)
        val y1 = (normalized.bottom * height).roundToInt().coerceIn(y0 + 1, height)
        val regionW = x1 - x0
        val regionH = y1 - y0
        if (regionW < 3 || regionH < 3) return@forEach

        // Background is the majority colour INSIDE the box: in any text region the lettering is
        // the minority of the pixels. Sampling the box border instead fails on a box drawn
        // tightly around a glyph column (the border is lettering), and sampling a band outside
        // the box fails when the box already covers the whole caption (the band is the page) —
        // both of which flip the test and make the caption itself read as lettering.
        val inside = ArrayList<Int>(regionW * regionH)
        for (y in y0 until y1) {
            for (x in x0 until x1) inside += luminance(pixels[y * width + x])
        }
        inside.sort()
        val background = inside[inside.size / 2]
        val threshold = if (background >= 176 || background <= 78) 48 else 66
        val edgeGuardX = (regionW * 0.035f).roundToInt().coerceAtLeast(1)
        val edgeGuardY = (regionH * 0.035f).roundToInt().coerceAtLeast(1)
        val local = BooleanArray(width * height)
        for (y in y0 + edgeGuardY until y1 - edgeGuardY) {
            for (x in x0 + edgeGuardX until x1 - edgeGuardX) {
                val value = luminance(pixels[y * width + x])
                val glyph = when {
                    background >= 176 -> background - value >= threshold
                    background <= 78 -> value - background >= threshold
                    else -> abs(value - background) >= threshold
                }
                if (glyph) local[y * width + x] = true
            }
        }
        // A small dilation catches antialiasing around glyphs but does not reach
        // the guarded bubble/panel border.
        val radius = (minOf(regionW, regionH) / 70).coerceIn(1, 3)
        for (y in y0 + edgeGuardY until y1 - edgeGuardY) {
            for (x in x0 + edgeGuardX until x1 - edgeGuardX) {
                if (!local[y * width + x]) continue
                for (dy in -radius..radius) for (dx in -radius..radius) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx in x0 + edgeGuardX until x1 - edgeGuardX &&
                        ny in y0 + edgeGuardY until y1 - edgeGuardY
                    ) result[ny * width + nx] = true
                }
            }
        }
    }
    return result
}

enum class TypesetAlign { Start, Center, End }

enum class TypesetWriting { Horizontal, Vertical }

data class TypesetLayer(
    val normalized: RectF,
    val text: String,
    val fillColor: Int = Color.BLACK,
    val strokeColor: Int = Color.WHITE,
    val strokeWidthPx: Float = 0f,
    val autoFit: Boolean = true,
    val fontSizePx: Float = 0f,
    val bold: Boolean = true,
    val italic: Boolean = false,
    val fontFamily: String = "sans-serif",
    val alignment: TypesetAlign = TypesetAlign.Center,
    val writing: TypesetWriting = TypesetWriting.Horizontal,
    val visible: Boolean = true,
    val lineSpacing: Float = 1f,
    val paddingFraction: Float = .08f,
    val rotationDeg: Float = 0f,
    /** Reference page width for manual font/stroke sizes; zero preserves legacy pixel units. */
    val referenceWidth: Float = 0f,
    /** Comic lettering convention; also the more legible choice at balloon sizes. */
    val uppercase: Boolean = true,
)

fun hexToColorInt(hex: String, fallback: Int = 0xff000000.toInt()): Int {
    val cleaned = hex.removePrefix("#")
    return when (cleaned.length) {
        6 -> {
            val value = cleaned.toIntOrNull(16) ?: return fallback
            (0xff shl 24) or value
        }
        8 -> cleaned.toLongOrNull(16)?.toInt() ?: fallback
        else -> fallback
    }
}

/**
 * Boundary-inward fill: masked pixels take the average of already-known
 * neighbors, so speech-bubble interiors reconstruct from the surrounding paper.
 */
internal fun inpaintArgb(
    width: Int,
    height: Int,
    pixels: IntArray,
    mask: BooleanArray,
): IntArray {
    require(width > 0 && height > 0)
    if (pixels.size < width * height || mask.size < width * height) return pixels.copyOf()
    val out = pixels.copyOf()
    val filled = BooleanArray(width * height) { index -> !mask[index] }
    val queued = BooleanArray(width * height)
    val queue = ArrayDeque<Int>()
    fun tryEnqueue(x: Int, y: Int) {
        if (x !in 0 until width || y !in 0 until height) return
        val index = y * width + x
        if (!mask[index] || filled[index] || queued[index]) return
        queued[index] = true
        queue.add(index)
    }
    for (y in 0 until height) {
        for (x in 0 until width) {
            val index = y * width + x
            if (!mask[index] || filled[index]) continue
            var touchesKnown = false
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx
                val ny = y + dy
                if (nx in 0 until width && ny in 0 until height && filled[ny * width + nx]) {
                    touchesKnown = true
                }
            }
            if (touchesKnown) {
                queued[index] = true
                queue.add(index)
            }
        }
    }
    while (queue.isNotEmpty()) {
        val index = queue.removeFirst()
        if (filled[index]) continue
        val x = index % width
        val y = index / width
        var red = 0
        var green = 0
        var blue = 0
        var alpha = 0
        var count = 0
        for (dy in -1..1) for (dx in -1..1) {
            if (dx == 0 && dy == 0) continue
            val nx = x + dx
            val ny = y + dy
            if (nx !in 0 until width || ny !in 0 until height) continue
            val neighbor = ny * width + nx
            if (!filled[neighbor]) continue
            val pixel = out[neighbor]
            alpha += pixel ushr 24 and 0xff
            red += pixel ushr 16 and 0xff
            green += pixel ushr 8 and 0xff
            blue += pixel and 0xff
            count++
        }
        if (count == 0) continue
        out[index] = packArgb(alpha / count, red / count, green / count, blue / count)
        filled[index] = true
        for (dy in -1..1) for (dx in -1..1) {
            if (dx == 0 && dy == 0) continue
            tryEnqueue(x + dx, y + dy)
        }
    }
    return out
}

internal fun isMostlyGrayscaleArgb(pixels: IntArray): Boolean {
    var opaque = 0
    var grayscale = 0
    pixels.forEach { pixel ->
        if ((pixel ushr 24 and 0xff) < 24) return@forEach
        opaque++
        val red = pixel ushr 16 and 0xff
        val green = pixel ushr 8 and 0xff
        val blue = pixel and 0xff
        val maxChannel = maxOf(red, green, blue)
        val minChannel = minOf(red, green, blue)
        if (maxChannel - minChannel <= 14) grayscale++
    }
    return opaque > 0 && grayscale.toFloat() / opaque >= 0.94f
}

/** Pure counterpart used by JVM tests and the bitmap wrapper above. */
internal fun colorizeMangaArgb(width: Int, height: Int, pixels: IntArray): IntArray {
    require(width > 0 && height > 0 && pixels.size >= width * height)
    val palette = arrayOf(
        intArrayOf(219, 150, 127), // warm skin/foreground
        intArrayOf(91, 145, 207),  // sky/cool shadow
        intArrayOf(91, 164, 118),  // foliage
        intArrayOf(177, 112, 185), // fabric/accent
        intArrayOf(219, 171, 76),  // light/warm accent
    )
    return IntArray(width * height) { index ->
        val source = pixels[index]
        val alpha = source ushr 24 and 0xff
        if (alpha == 0) return@IntArray source
        val red = source ushr 16 and 0xff
        val green = source ushr 8 and 0xff
        val blue = source and 0xff
        val gray = ((red * 30 + green * 59 + blue * 11) / 100)
        // Keep line art neutral and speech bubbles/page gutters almost white.
        if (gray <= 28 || gray >= 247) {
            packArgb(alpha, gray, gray, gray)
        } else {
            val x = index % width
            val y = index / width
            val paletteIndex = ((x * 3 / width) + (y * 2 / height)) % palette.size
            val tint = palette[paletteIndex]
            val strength = when {
                gray < 80 -> 0.18f
                gray > 225 -> 0.10f
                else -> 0.34f
            }
            fun channel(value: Int): Int = (gray * (1f - strength) + value * strength)
                .roundToInt().coerceIn(0, 255)
            packArgb(alpha, channel(tint[0]), channel(tint[1]), channel(tint[2]))
        }
    }
}

private fun packArgb(alpha: Int, red: Int, green: Int, blue: Int): Int =
    (alpha.coerceIn(0, 255) shl 24) or
        (red.coerceIn(0, 255) shl 16) or
        (green.coerceIn(0, 255) shl 8) or
        blue.coerceIn(0, 255)

data class NormalizedPanelBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    fun toRectF(): RectF = RectF(left, top, right, bottom)
}

data class TextContainerFrame(
    val box: NormalizedPanelBox,
    val backgroundLuminance: Int,
) {
    val isDark: Boolean get() = backgroundLuminance < 128
}

/**
 * The colour to repaint lettering in, when the container behind it is a flat one — a white
 * balloon, a black caption box. Returns null when the area is textured (lettering sitting on
 * artwork), where copying one colour over the glyphs would blank out the drawing.
 *
 * Only pixels the glyph mask did NOT claim, and that lie outside [glyphs], are sampled: the
 * lettering must not drag the measured background toward its own colour, and a "container"
 * that turned out to be no bigger than the lettering is not a container at all.
 */
internal fun flatContainerColorArgb(
    width: Int,
    height: Int,
    pixels: IntArray,
    container: NormalizedPanelBox,
    glyphs: NormalizedPanelBox,
    mask: BooleanArray,
): Int? {
    if (width <= 0 || height <= 0 || pixels.size < width * height) return null
    val x0 = (container.left * width).roundToInt().coerceIn(0, width)
    val x1 = (container.right * width).roundToInt().coerceIn(x0, width)
    val y0 = (container.top * height).roundToInt().coerceIn(0, height)
    val y1 = (container.bottom * height).roundToInt().coerceIn(y0, height)
    if (x1 - x0 < 3 || y1 - y0 < 3) return null

    val reds = ArrayList<Int>()
    val greens = ArrayList<Int>()
    val blues = ArrayList<Int>()
    val lums = ArrayList<Int>()
    val gx0 = (glyphs.left * width).roundToInt().coerceIn(0, width)
    val gx1 = (glyphs.right * width).roundToInt().coerceIn(gx0, width)
    val gy0 = (glyphs.top * height).roundToInt().coerceIn(0, height)
    val gy1 = (glyphs.bottom * height).roundToInt().coerceIn(gy0, height)
    // Sampled strictly from the container AROUND the lettering. Falling back to the pixels
    // between the glyphs when that ring is empty was tried and is much worse: on a caption the
    // page colour wins the sample and the box gets painted out. No ring means no trustworthy
    // container colour, so the region falls through to the inpaint instead.
    for (y in y0 until y1) {
        for (x in x0 until x1) {
            val index = y * width + x
            if (mask[index]) continue
            if (x in gx0 until gx1 && y in gy0 until gy1) continue
            val pixel = pixels[index]
            val r = pixel ushr 16 and 0xff
            val g = pixel ushr 8 and 0xff
            val b = pixel and 0xff
            reds += r
            greens += g
            blues += b
            lums += (r * 30 + g * 59 + b * 11) / 100
        }
    }
    if (lums.size < 24) return null
    lums.sort()
    // A flat container has almost all of its background within a narrow band. Screentone,
    // gradients and artwork spread far wider than this and fall through to the inpaint.
    val low = lums[lums.size / 10]
    val high = lums[lums.size * 9 / 10]
    if (high - low > 30) return null
    reds.sort(); greens.sort(); blues.sort()
    val mid = lums.size / 2
    return (0xff shl 24) or (reds[mid] shl 16) or (greens[mid] shl 8) or blues[mid]
}

/**
 * Expands a tight source-glyph box through a similarly colored container. Unlike the legacy
 * light-only bubble walk, this also finds black caption boxes containing white lettering.
 */
internal fun textContainerArgb(
    width: Int,
    height: Int,
    pixels: IntArray,
    source: NormalizedPanelBox,
): TextContainerFrame {
    if (width <= 0 || height <= 0 || pixels.size < width * height) {
        return TextContainerFrame(source, 255)
    }
    fun luminance(x: Int, y: Int): Int {
        val pixel = pixels[y * width + x]
        val r = pixel ushr 16 and 0xff
        val g = pixel ushr 8 and 0xff
        val b = pixel and 0xff
        return (r * 30 + g * 59 + b * 11) / 100
    }

    var left = (source.left * width).roundToInt().coerceIn(0, width - 1)
    var right = (source.right * width).roundToInt().coerceIn(left + 1, width)
    var top = (source.top * height).roundToInt().coerceIn(0, height - 1)
    var bottom = (source.bottom * height).roundToInt().coerceIn(top + 1, height)
    // The container's colour is the majority colour inside the box, for the same reason the
    // glyph mask uses it: lettering is the minority of a text region, while a band sampled
    // outside the box is the page whenever the box already covers the whole caption — which
    // would grow the container out over the artwork and take the caption's edge with it.
    val ring = ArrayList<Int>((right - left) * (bottom - top))
    for (y in top until bottom) {
        for (x in left until right) ring += luminance(x, y)
    }
    ring.sort()
    val background = ring.getOrElse(ring.size / 2) { 255 }
    val tolerance = if (background <= 70 || background >= 185) 52 else 38
    fun matches(x: Int, y: Int): Boolean = abs(luminance(x, y) - background) <= tolerance
    fun columnMatches(x: Int): Boolean {
        var count = 0
        for (y in top until bottom) if (matches(x, y)) count++
        return count.toFloat() / (bottom - top).coerceAtLeast(1) >= 0.72f
    }
    fun rowMatches(y: Int): Boolean {
        var count = 0
        for (x in left until right) if (matches(x, y)) count++
        return count.toFloat() / (right - left).coerceAtLeast(1) >= 0.72f
    }

    val maxGrowX = ((right - left) * 2.5f).roundToInt().coerceAtLeast(10)
    val maxGrowY = ((bottom - top) * 1.5f).roundToInt().coerceAtLeast(10)
    var grown = 0
    while (left > 0 && grown < maxGrowX && columnMatches(left - 1)) { left--; grown++ }
    grown = 0
    while (right < width && grown < maxGrowX && columnMatches(right)) { right++; grown++ }
    grown = 0
    while (top > 0 && grown < maxGrowY && rowMatches(top - 1)) { top--; grown++ }
    grown = 0
    while (bottom < height && grown < maxGrowY && rowMatches(bottom)) { bottom++; grown++ }

    return TextContainerFrame(
        box = NormalizedPanelBox(
            left.toFloat() / width,
            top.toFloat() / height,
            right.toFloat() / width,
            bottom.toFloat() / height,
        ),
        backgroundLuminance = background,
    )
}

/**
 * Grows a detected text box out to the speech bubble around it.
 *
 * A typesetter fits the translation to the *bubble*, not to the tight box the source
 * glyphs happened to fill. The difference decides whether a page reads: vertical Japanese
 * occupies a tall, narrow column, and forcing horizontal English into that same column
 * gives either microscopic type or an overflowing line. Each edge walks outward while the
 * rows/columns it crosses stay bubble-white and stops at the bubble's ink border, so the
 * frame never spills onto artwork.
 */
internal fun bubbleFrameArgb(
    width: Int,
    height: Int,
    pixels: IntArray,
    box: NormalizedPanelBox,
    lightThreshold: Int = 190,
    lightFraction: Float = 0.82f,
): NormalizedPanelBox {
    if (width <= 0 || height <= 0 || pixels.size < width * height) return box
    fun isLight(x: Int, y: Int): Boolean {
        val pixel = pixels[y * width + x]
        val red = pixel ushr 16 and 0xff
        val green = pixel ushr 8 and 0xff
        val blue = pixel and 0xff
        return (red * 30 + green * 59 + blue * 11) / 100 >= lightThreshold
    }

    var left = (box.left * width).roundToInt().coerceIn(0, width - 1)
    var right = (box.right * width).roundToInt().coerceIn(left + 1, width)
    var top = (box.top * height).roundToInt().coerceIn(0, height - 1)
    var bottom = (box.bottom * height).roundToInt().coerceIn(top + 1, height)

    // A bubble is rarely more than a few times the lettering it holds; the caps stop a
    // borderless caption from growing across the whole page.
    val maxGrowX = ((right - left) * 1.5f).roundToInt().coerceAtLeast(8)
    val maxGrowY = ((bottom - top) * 1.5f).roundToInt().coerceAtLeast(8)

    fun columnIsLight(x: Int): Boolean {
        var light = 0
        for (y in top until bottom) if (isLight(x, y)) light++
        return light.toFloat() / (bottom - top).coerceAtLeast(1) >= lightFraction
    }

    fun rowIsLight(y: Int): Boolean {
        var light = 0
        for (x in left until right) if (isLight(x, y)) light++
        return light.toFloat() / (right - left).coerceAtLeast(1) >= lightFraction
    }

    var grown = 0
    while (left > 0 && grown < maxGrowX && columnIsLight(left - 1)) {
        left--
        grown++
    }
    grown = 0
    while (right < width && grown < maxGrowX && columnIsLight(right.coerceAtMost(width - 1))) {
        right++
        grown++
    }
    grown = 0
    while (top > 0 && grown < maxGrowY && rowIsLight(top - 1)) {
        top--
        grown++
    }
    grown = 0
    while (bottom < height && grown < maxGrowY && rowIsLight(bottom.coerceAtMost(height - 1))) {
        bottom++
        grown++
    }

    return NormalizedPanelBox(
        left = left.toFloat() / width,
        top = top.toFloat() / height,
        right = right.toFloat() / width,
        bottom = bottom.toFloat() / height,
    )
}

enum class OfflinePanelDetectionKind { Multiple, Single, Failed }

data class OfflinePanelDetection(
    val kind: OfflinePanelDetectionKind,
    val boxes: List<NormalizedPanelBox>,
    val message: String,
)

/** Pure ARGB detector so separator behavior can be covered by JVM tests. */
internal fun detectPanelsFromArgb(
    width: Int,
    height: Int,
    pixels: IntArray,
): OfflinePanelDetection {
    if (width < 8 || height < 8 || pixels.size < width * height) {
        return OfflinePanelDetection(
            kind = OfflinePanelDetectionKind.Failed,
            boxes = emptyList(),
            message = "Image pixels could not be read for offline detection.",
        )
    }
    return runCatching {
        fun isWhite(px: Int): Boolean {
            val r = px ushr 16 and 0xff
            val g = px ushr 8 and 0xff
            val b = px and 0xff
            return r > 225 && g > 225 && b > 225 && abs(r - b) < 26
        }
        val boxes = mutableListOf<NormalizedPanelBox>()
        val minW = 0.16f
        val minH = 0.10f

        fun split(x0: Int, y0: Int, x1: Int, y1: Int, depth: Int) {
            if (depth > 5) return
            val regionW = x1 - x0
            val regionH = y1 - y0
            if (regionW < 8 || regionH < 8) return
            // Measure gutters inside the current candidate, rather than only
            // accepting lines that span the entire source page. Real manga
            // layouts often have overlapping or inset panels.
            fun rowIsGutter(row: Int): Boolean {
                var white = 0
                for (x in x0 until x1) if (isWhite(pixels[row * width + x])) white++
                return white.toFloat() / regionW > 0.90f
            }
            fun colIsGutter(col: Int): Boolean {
                var white = 0
                for (y in y0 until y1) if (isWhite(pixels[y * width + col])) white++
                return white.toFloat() / regionH > 0.90f
            }

            fun longestRun(start: Int, end: Int, isGutter: (Int) -> Boolean): Pair<Int, Int> {
                var best = start to start
                var runStart = start
                for (value in start until end) {
                    if (!isGutter(value)) {
                        if (value - runStart > best.second - best.first) best = runStart to value
                        runStart = value + 1
                    }
                }
                if (end - runStart > best.second - best.first) best = runStart to end
                return best
            }

            val bestRowRun = longestRun(y0, y1, ::rowIsGutter)
            val bestColRun = longestRun(x0, x1, ::colIsGutter)
            val rowRunLen = bestRowRun.second - bestRowRun.first
            val colRunLen = bestColRun.second - bestColRun.first
            val canSplitH = rowRunLen >= 3 && rowRunLen < regionH * 0.85f
            val canSplitV = colRunLen >= 3 && colRunLen < regionW * 0.85f
            when {
                canSplitH && (!canSplitV || rowRunLen >= colRunLen) -> {
                    val mid = (bestRowRun.first + bestRowRun.second) / 2
                    split(x0, y0, x1, mid, depth + 1)
                    split(x0, mid, x1, y1, depth + 1)
                }
                canSplitV -> {
                    val mid = (bestColRun.first + bestColRun.second) / 2
                    split(x0, y0, mid, y1, depth + 1)
                    split(mid, y0, x1, y1, depth + 1)
                }
                else -> {
                    val fw = regionW.toFloat() / width
                    val fh = regionH.toFloat() / height
                    if (fw >= minW && fh >= minH) {
                        boxes.add(
                            NormalizedPanelBox(
                                x0.toFloat() / width,
                                y0.toFloat() / height,
                                (x0 + regionW).toFloat() / width,
                                (y0 + regionH).toFloat() / height,
                            ),
                        )
                    }
                }
            }
        }
        split(0, 0, width, height, 0)
        if (boxes.isEmpty()) {
            boxes.add(NormalizedPanelBox(0f, 0f, 1f, 1f))
        }
        val ordered = boxes.sortedWith(compareBy<NormalizedPanelBox> { it.top }.thenBy { it.left })
        OfflinePanelDetection(
            kind = if (ordered.size > 1) {
                OfflinePanelDetectionKind.Multiple
            } else {
                OfflinePanelDetectionKind.Single
            },
            boxes = ordered,
            message = if (ordered.size > 1) {
                "Offline white-gutter detection found ${ordered.size} panels."
            } else {
                "Offline white-gutter detection found one panel; no split was created."
            },
        )
    }.getOrElse { error ->
        OfflinePanelDetection(
            kind = OfflinePanelDetectionKind.Failed,
            boxes = emptyList(),
            message = "Offline white-gutter detection failed: ${error.message ?: "unknown image error"}",
        )
    }
}
