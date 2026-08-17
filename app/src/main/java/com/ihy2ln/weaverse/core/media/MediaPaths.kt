package com.ihy2ln.weaverse.core.media

import android.content.Context
import java.io.File

/** `filesDir/media/<id>.<ext>` for originals, `filesDir/media/thumbs/<id>.jpg` for thumbnails (spec §7). */
object MediaPaths {
    fun mediaDir(context: Context): File = File(context.filesDir, "media").apply { mkdirs() }

    fun thumbsDir(context: Context): File = File(mediaDir(context), "thumbs").apply { mkdirs() }

    fun mediaFile(context: Context, id: String, extension: String): File = File(mediaDir(context), "$id.$extension")

    fun thumbFile(context: Context, id: String): File = File(thumbsDir(context), "$id.jpg")

    fun resolve(context: Context, relativePath: String): File = File(mediaDir(context), relativePath)

    fun resolveThumb(context: Context, thumbnailPath: String): File = File(thumbsDir(context), thumbnailPath)
}
