package com.ihy2ln.weaverse.core.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/** Downscales images over 4096px on the long edge in place (spec §7). */
object ImageDownscaler {
    const val MAX_LONG_EDGE = 4096

    fun downscaleIfNeeded(file: File, maxLongEdge: Int = MAX_LONG_EDGE) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return
        if (maxOf(bounds.outWidth, bounds.outHeight) <= maxLongEdge) return

        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxLongEdge)
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val sampled = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return

        val scale = maxLongEdge.toFloat() / maxOf(sampled.width, sampled.height)
        val finalBitmap = if (scale < 1f) {
            val scaled = Bitmap.createScaledBitmap(
                sampled,
                (sampled.width * scale).toInt().coerceAtLeast(1),
                (sampled.height * scale).toInt().coerceAtLeast(1),
                true,
            )
            if (scaled !== sampled) sampled.recycle()
            scaled
        } else {
            sampled
        }

        file.outputStream().use { out -> finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
        finalBitmap.recycle()
    }
}
