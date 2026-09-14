package com.ihy2ln.weaverse.core.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImageOpsPanelDetectionTest {
    @Test
    fun monochromePageIsDetectedAndColorizedWithoutLosingInkOrPaper() {
        val width = 20
        val height = 20
        val pixels = IntArray(width * height) { index ->
            val gray = when {
                index < width -> 12
                index >= width * (height - 1) -> 252
                else -> 128
            }
            (0xff shl 24) or (gray shl 16) or (gray shl 8) or gray
        }

        assertTrue(isMostlyGrayscaleArgb(pixels))
        val colored = colorizeMangaArgb(width, height, pixels)

        assertTrue(colored.slice(width until width * (height - 1)).any { pixel ->
            val red = pixel ushr 16 and 0xff
            val green = pixel ushr 8 and 0xff
            val blue = pixel and 0xff
            maxOf(red, green, blue) - minOf(red, green, blue) > 14
        })
        assertEquals(pixels.first(), colored.first(), "black ink should stay neutral")
        assertEquals(pixels.last(), colored.last(), "white paper should stay neutral")
    }

    @Test
    fun existingColorArtIsNotClassifiedAsBlackAndWhite() {
        val red = 0xffff3030.toInt()
        val gray = 0xff888888.toInt()
        val pixels = IntArray(100) { if (it < 10) red else gray }

        assertTrue(!isMostlyGrayscaleArgb(pixels))
    }

    @Test
    fun cleanPageWithCrossGuttersFindsFourPanels() {
        val width = 80
        val height = 100
        val black = 0xff101010.toInt()
        val white = 0xffffffff.toInt()
        val pixels = IntArray(width * height) { black }
        for (y in 48..52) for (x in 0 until width) pixels[y * width + x] = white
        for (x in 38..42) for (y in 0 until height) pixels[y * width + x] = white

        val result = detectPanelsFromArgb(width, height, pixels)

        assertEquals(OfflinePanelDetectionKind.Multiple, result.kind)
        assertEquals(4, result.boxes.size)
    }

    @Test
    fun pageWithoutNearWhiteGutterIsOnePanel() {
        val result = detectPanelsFromArgb(
            width = 80,
            height = 100,
            pixels = IntArray(80 * 100) { 0xff101010.toInt() },
        )

        assertEquals(OfflinePanelDetectionKind.Single, result.kind)
        assertEquals(1, result.boxes.size)
        assertTrue(result.message.contains("one panel"))
    }

    @Test
    fun unreadablePixelBufferReportsFailure() {
        val result = detectPanelsFromArgb(width = 80, height = 100, pixels = IntArray(4))

        assertEquals(OfflinePanelDetectionKind.Failed, result.kind)
        assertTrue(result.boxes.isEmpty())
        assertTrue(result.message.contains("could not be read"))
    }
}
