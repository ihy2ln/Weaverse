package com.ihy2ln.weaverse.core.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import java.io.ByteArrayOutputStream
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
        val decode = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(path, decode)
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
        val rowWhite = FloatArray(height) { row ->
            var count = 0
            for (x in 0 until width) if (isWhite(pixels[row * width + x])) count++
            count.toFloat() / width
        }
        val colWhite = FloatArray(width) { col ->
            var count = 0
            for (y in 0 until height) if (isWhite(pixels[y * width + col])) count++
            count.toFloat() / height
        }
        val boxes = mutableListOf<NormalizedPanelBox>()
        val minW = 0.16f
        val minH = 0.10f

        fun split(x0: Int, y0: Int, x1: Int, y1: Int, depth: Int) {
            if (depth > 5) return
            val regionW = x1 - x0
            val regionH = y1 - y0
            if (regionW < 8 || regionH < 8) return
            // Longest horizontal white run inside the region (with margins).
            var bestRowRun = 0 to 0
            var run = 0
            for (y in y0 until y1) {
                if (rowWhite[y] > 0.93f) {
                    run++
                    if (run > bestRowRun.second - bestRowRun.first) bestRowRun = (y - run + 1) to (y + 1)
                } else run = 0
            }
            val rowRunLen = bestRowRun.second - bestRowRun.first
            var bestColRun = 0 to 0
            run = 0
            for (x in x0 until x1) {
                if (colWhite[x] > 0.93f) {
                    run++
                    if (run > bestColRun.second - bestColRun.first) bestColRun = (x - run + 1) to (x + 1)
                } else run = 0
            }
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
