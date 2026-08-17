package com.ihy2ln.weaverse.core.media

import com.ihy2ln.weaverse.data.db.entity.MediaType

/** Classifies an imported file's MIME type for the `media` table (spec §7). */
object MimeTypes {
    fun mediaTypeFor(mimeType: String): MediaType = when {
        mimeType.startsWith("video/") -> MediaType.Video
        mimeType.startsWith("audio/") -> MediaType.Audio
        else -> MediaType.Image
    }

    /** File extension for the copy under `filesDir/media/` — falls back to the MIME subtype. */
    fun extensionFor(mimeType: String): String = when (mimeType) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "image/heic" -> "heic"
        "image/heif" -> "heif"
        "video/mp4" -> "mp4"
        "video/webm" -> "webm"
        "video/3gpp" -> "3gp"
        "video/quicktime" -> "mov"
        else -> mimeType.substringAfterLast('/', "bin").substringBefore('+')
    }
}
