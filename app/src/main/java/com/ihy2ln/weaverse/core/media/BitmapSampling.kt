package com.ihy2ln.weaverse.core.media

/**
 * Power-of-two `BitmapFactory.Options.inSampleSize` for decoding an image no
 * larger than [targetLongEdge] on its long edge — the standard
 * decode-at-a-smaller-size trick so downscaling/thumbnailing never fully
 * decodes a huge source bitmap into memory first. Pure so it's unit-testable
 * without `BitmapFactory` itself (Android framework, not available on the
 * unit-test JVM's `testOptions.returnDefaultValues` stub).
 */
fun calculateInSampleSize(width: Int, height: Int, targetLongEdge: Int): Int {
    if (width <= 0 || height <= 0 || targetLongEdge <= 0) return 1
    var sampleSize = 1
    val longEdge = maxOf(width, height)
    while (longEdge / (sampleSize * 2) >= targetLongEdge) {
        sampleSize *= 2
    }
    return sampleSize
}
