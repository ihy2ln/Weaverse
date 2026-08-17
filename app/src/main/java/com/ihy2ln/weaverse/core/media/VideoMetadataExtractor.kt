package com.ihy2ln.weaverse.core.media

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import java.io.File

/**
 * Video dimensions/duration and frame-0 extraction for thumbnails (spec §7).
 * Uses the platform `android.media.MediaMetadataRetriever` — Media3/ExoPlayer
 * itself is a playback library and doesn't ship its own metadata retriever;
 * the retriever is the same regardless of which player renders the video.
 */
object VideoMetadataExtractor {
    data class Metadata(val width: Int?, val height: Int?, val durationMs: Long?)

    fun extract(file: File): Metadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            Metadata(width, height, durationMs)
        } finally {
            retriever.release()
        }
    }

    fun extractFrameZero(file: File): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } finally {
            retriever.release()
        }
    }
}
