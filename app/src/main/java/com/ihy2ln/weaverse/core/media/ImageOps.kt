package com.ihy2ln.weaverse.core.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
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
