package com.ihy2ln.weaverse.core.media

import android.graphics.BitmapFactory

object MangaColorTransfer {
    fun validateSource(path: String) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0 &&
            bounds.outWidth.toLong() * bounds.outHeight <= 8_000_000) {
            "Preserve drawing supports source pages up to 8 megapixels. Resize or split this page first; nothing was replaced."
        }
    }

    fun preserveDrawing(path: String, generated: ByteArray): ByteArray {
        validateSource(path)
        val source = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inMutable = true })
            ?: error("Could not read the original page.")
        try {
            val generatedBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(generated, 0, generated.size, generatedBounds)
            require(generatedBounds.outWidth > 0 && generatedBounds.outHeight > 0) { "The provider returned an unreadable image." }
            val sourceRatio = source.width.toDouble() / source.height
            val outputRatio = generatedBounds.outWidth.toDouble() / generatedBounds.outHeight
            require(kotlin.math.abs(sourceRatio / outputRatio - 1) < .015) {
                "The model changed the page's aspect ratio. Its output was rejected to protect the drawing."
            }
            var sample = 1
            while (generatedBounds.outWidth.toLong() * generatedBounds.outHeight / sample / sample > 4_000_000) sample *= 2
            val proposal = BitmapFactory.decodeByteArray(generated, 0, generated.size,
                BitmapFactory.Options().apply { inSampleSize = sample }) ?: error("Invalid generated image.")
            try {
                val originalRow = IntArray(source.width)
                val colorRow = IntArray(proposal.width)
                for (y in 0 until source.height) {
                    source.getPixels(originalRow, 0, source.width, 0, y, source.width, 1)
                    proposal.getPixels(colorRow, 0, proposal.width, 0, y * proposal.height / source.height, proposal.width, 1)
                    for (x in originalRow.indices) originalRow[x] = MangaColorPolicy.colorPixel(
                        originalRow[x], colorRow[x * proposal.width / source.width])
                    source.setPixels(originalRow, 0, source.width, 0, y, source.width, 1)
                }
                return ImageOps.toPngBytes(source)
            } finally { proposal.recycle() }
        } finally { source.recycle() }
    }
}
