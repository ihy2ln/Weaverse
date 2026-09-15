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

    /**
     * A page holding one white bubble on dark artwork, with a small block of lettering
     * inside it. The frame used for typesetting should open out to the bubble and stop on
     * its ink border, never crossing onto the artwork behind it.
     */
    @Test
    fun textBoxGrowsOutToItsBubbleAndStopsAtTheBorder() {
        val width = 100
        val height = 100
        val dark = 0xff101010.toInt()
        val paper = 0xffffffff.toInt()
        val pixels = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            val insideBubble = x in 20 until 80 && y in 20 until 80
            val isLettering = x in 42 until 58 && y in 47 until 53
            if (insideBubble && !isLettering) paper else dark
        }

        val frame = bubbleFrameArgb(
            width = width,
            height = height,
            pixels = pixels,
            box = NormalizedPanelBox(0.40f, 0.45f, 0.60f, 0.55f),
        )

        // Sideways the walk meets the bubble outline at x=20 and x=80.
        assertEquals(0.20f, frame.left, 0.01f)
        assertEquals(0.80f, frame.right, 0.01f)
        // Vertically it runs out of allowance first, well inside the bubble.
        assertTrue(frame.top < 0.45f, "frame should reach above the lettering")
        assertTrue(frame.bottom > 0.55f, "frame should reach below the lettering")
        assertTrue(frame.top >= 0.20f && frame.bottom <= 0.80f, "frame must stay inside the bubble")
        assertTrue(frame.width > 0.20f, "bubble frame should be wider than the text box")
    }

    @Test
    fun letteringOnOpenArtworkKeepsItsOwnBox() {
        val width = 60
        val height = 60
        // No bubble anywhere: every neighbouring column and row is artwork.
        val pixels = IntArray(width * height) { 0xff101010.toInt() }

        val box = NormalizedPanelBox(0.40f, 0.40f, 0.60f, 0.60f)
        val frame = bubbleFrameArgb(width = width, height = height, pixels = pixels, box = box)

        assertEquals(box.left, frame.left, 0.01f)
        assertEquals(box.right, frame.right, 0.01f)
        assertEquals(box.top, frame.top, 0.01f)
        assertEquals(box.bottom, frame.bottom, 0.01f)
    }

    @Test
    fun typesetInsetLeavesRoomInsideCurvedBubbleBounds() {
        val safe = ImageOps.insetNormalizedBox(
            NormalizedPanelBox(0.10f, 0.20f, 0.90f, 0.80f),
        )

        assertEquals(0.164f, safe.left, 0.001f)
        assertEquals(0.266f, safe.top, 0.001f)
        assertEquals(0.836f, safe.right, 0.001f)
        assertEquals(0.734f, safe.bottom, 0.001f)
    }

    @Test
    fun typesetInsetClampsExpandedFrameToPage() {
        val safe = ImageOps.insetNormalizedBox(
            NormalizedPanelBox(-0.10f, -0.10f, 1.10f, 1.10f),
        )

        assertTrue(safe.left >= 0f && safe.top >= 0f)
        assertTrue(safe.right <= 1f && safe.bottom <= 1f)
        assertTrue(safe.width() < 1f && safe.height() < 1f)
    }
}
