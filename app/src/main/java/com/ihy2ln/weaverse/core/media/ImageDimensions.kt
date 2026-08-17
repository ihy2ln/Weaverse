package com.ihy2ln.weaverse.core.media

import android.graphics.BitmapFactory
import java.io.File

/** Reads width/height without decoding pixel data (`inJustDecodeBounds`) — cheap even for huge images. */
fun readImageDimensions(file: File): Pair<Int, Int>? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, options)
    return if (options.outWidth > 0 && options.outHeight > 0) options.outWidth to options.outHeight else null
}
