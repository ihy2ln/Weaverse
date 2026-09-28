package com.ihy2ln.weaverse.core.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Image models answer in a fixed set of aspect ratios. A manga page of any other shape came
 * back reshaped, the colour could no longer be laid under the original ink, and the page was
 * rejected; across a chapter that dropped every page whose shape the model did not offer.
 * The page is now padded with paper-white to the nearest ratio the model offers, and the
 * padding is cut off the answer again, so every page returns at its own shape.
 */
object MangaAspectFit {
    /** Where the source page sits inside the padded canvas sent to the model. */
    data class Plan(
        val ratio: String?,
        val canvasWidth: Int,
        val canvasHeight: Int,
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
    ) {
        val padded: Boolean get() = canvasWidth != width || canvasHeight != height
    }

    /** Numeric ratios from the image catalog's `ratio:W:H` tags; `ratio:auto` is not a shape. */
    fun ratios(tags: List<String>): List<Pair<String, Double>> = tags.mapNotNull { tag ->
        if (!tag.startsWith("ratio:")) return@mapNotNull null
        val value = tag.removePrefix("ratio:")
        val parts = value.split(':')
        val w = parts.getOrNull(0)?.toDoubleOrNull()
        val h = parts.getOrNull(1)?.toDoubleOrNull()
        if (parts.size != 2 || w == null || h == null || w <= 0 || h <= 0) null else value to w / h
    }

    fun plan(width: Int, height: Int, tags: List<String>): Plan {
        require(width > 0 && height > 0)
        val source = width.toDouble() / height
        val choice = ratios(tags).minByOrNull { abs(ln(it.second / source)) }
            ?: return Plan(null, width, height, 0, 0, width, height)
        val target = choice.second
        // Close enough already: padding a few pixels would only shift the art.
        if (abs(target / source - 1) < 0.01) return Plan(choice.first, width, height, 0, 0, width, height)
        return if (target > source) {
            val canvasWidth = (height * target).roundToInt().coerceAtLeast(width)
            Plan(choice.first, canvasWidth, height, (canvasWidth - width) / 2, 0, width, height)
        } else {
            val canvasHeight = (width / target).roundToInt().coerceAtLeast(height)
            Plan(choice.first, width, canvasHeight, 0, (canvasHeight - height) / 2, width, height)
        }
    }

    /**
     * The model's answer scaled onto the padded canvas and cropped back to the source's
     * area. A small ratio drift (rounding in the provider's sizes) is absorbed by the scale;
     * a large one means the model recomposed the page and cannot be mapped back.
     */
    fun cropArea(plan: Plan, outputWidth: Int, outputHeight: Int): Rect {
        val canvasRatio = plan.canvasWidth.toDouble() / plan.canvasHeight
        val outputRatio = outputWidth.toDouble() / outputHeight
        require(abs(outputRatio / canvasRatio - 1) < 0.04) {
            "The model changed the page's aspect ratio. Its output was rejected to protect the drawing."
        }
        val sx = outputWidth.toDouble() / plan.canvasWidth
        val sy = outputHeight.toDouble() / plan.canvasHeight
        val left = (plan.left * sx).roundToInt().coerceIn(0, outputWidth - 1)
        val top = (plan.top * sy).roundToInt().coerceIn(0, outputHeight - 1)
        val right = ((plan.left + plan.width) * sx).roundToInt().coerceIn(left + 1, outputWidth)
        val bottom = ((plan.top + plan.height) * sy).roundToInt().coerceIn(top + 1, outputHeight)
        return Rect(left, top, right, bottom)
    }

    /** The source page centred on a paper-white canvas of the plan's shape. */
    fun pad(source: Bitmap, plan: Plan): Bitmap {
        if (!plan.padded && source.width == plan.canvasWidth && source.height == plan.canvasHeight) return source
        val scale = source.width.toDouble() / plan.width
        val out = Bitmap.createBitmap(
            (plan.canvasWidth * scale).roundToInt().coerceAtLeast(source.width),
            (plan.canvasHeight * scale).roundToInt().coerceAtLeast(source.height),
            Bitmap.Config.ARGB_8888,
        )
        Canvas(out).apply {
            drawColor(Color.WHITE)
            drawBitmap(source, (plan.left * scale).toFloat(), (plan.top * scale).toFloat(), null)
        }
        return out
    }

    /** Cuts the padding back off an encoded answer; returns it untouched when nothing was padded. */
    fun unpad(generated: Pair<ByteArray, String>, plan: Plan): Pair<ByteArray, String> {
        if (!plan.padded) return generated
        val bitmap = BitmapFactory.decodeByteArray(generated.first, 0, generated.first.size)
            ?: error("The provider returned an unreadable image.")
        try {
            val area = cropArea(plan, bitmap.width, bitmap.height)
            val cropped = Bitmap.createBitmap(bitmap, area.left, area.top, area.width(), area.height())
            try {
                return ImageOps.toPngBytes(cropped) to "image/png"
            } finally {
                if (cropped !== bitmap) cropped.recycle()
            }
        } finally {
            bitmap.recycle()
        }
    }
}
