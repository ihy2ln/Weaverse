package com.ihy2ln.weaverse.core.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

object Thumbnailer {
    const val THUMB_MAX_LONG_EDGE = 512

    fun generateImageThumbnail(sourceFile: File, destFile: File): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(sourceFile.absolutePath, bounds)
        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, THUMB_MAX_LONG_EDGE)
        val bitmap = BitmapFactory.decodeFile(
            sourceFile.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: return false
        destFile.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out) }
        bitmap.recycle()
        return true
    }

    /** Frame 0, per spec §7 ("Media3 MediaMetadataRetriever for video frame 0"). */
    fun generateVideoThumbnail(sourceFile: File, destFile: File): Boolean {
        val frame = VideoMetadataExtractor.extractFrameZero(sourceFile) ?: return false
        destFile.outputStream().use { out -> frame.compress(Bitmap.CompressFormat.JPEG, 80, out) }
        frame.recycle()
        return true
    }
}
