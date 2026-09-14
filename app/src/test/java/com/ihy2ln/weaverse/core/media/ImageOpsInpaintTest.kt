package com.ihy2ln.weaverse.core.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImageOpsInpaintTest {
    @Test
    fun maskedCenterPixelTakesNeighborPaperColor() {
        val width = 5
        val height = 5
        val white = 0xffffffff.toInt()
        val black = 0xff101010.toInt()
        val pixels = IntArray(width * height) { white }
        pixels[2 * width + 2] = black
        val mask = BooleanArray(width * height)
        mask[2 * width + 2] = true

        val filled = inpaintArgb(width, height, pixels, mask)

        assertEquals(white, filled.first())
        assertTrue(filled[2 * width + 2] != black, "inpaint should replace the masked glyph")
        val red = filled[2 * width + 2] ushr 16 and 0xff
        val green = filled[2 * width + 2] ushr 8 and 0xff
        val blue = filled[2 * width + 2] and 0xff
        assertTrue(red > 200 && green > 200 && blue > 200, "reconstructed pixel should be near paper white")
    }

    @Test
    fun unmaskedPixelsStayUntouched() {
        val width = 4
        val height = 4
        val ink = 0xff202020.toInt()
        val pixels = IntArray(width * height) { ink }
        val mask = BooleanArray(width * height)
        mask[5] = true

        val filled = inpaintArgb(width, height, pixels, mask)

        filled.forEachIndexed { index, pixel ->
            if (index != 5) assertEquals(ink, pixel)
        }
    }

    @Test
    fun hexToColorIntReadsSixDigitRgb() {
        assertEquals(0xff0000, hexToColorInt("#FF0000") and 0x00ffffff)
        assertEquals(0x111111, hexToColorInt("#111111") and 0x00ffffff)
    }

    @Test
    fun glyphMaskSelectsInkButPreservesRegionBorder() {
        val width = 20
        val height = 20
        val white = 0xffffffff.toInt()
        val black = 0xff101010.toInt()
        val pixels = IntArray(width * height) { white }
        // A bubble/frame edge at the OCR-box perimeter must remain intact.
        for (x in 2 until 18) pixels[2 * width + x] = black
        // Compact source glyphs near the middle should be selected.
        pixels[9 * width + 9] = black
        pixels[9 * width + 10] = black
        pixels[10 * width + 9] = black
        pixels[10 * width + 10] = black

        val mask = textGlyphMaskArgb(width, height, pixels, listOf(NormalizedPanelBox(.1f, .1f, .9f, .9f)))

        assertTrue(mask[9 * width + 9], "source glyph should be masked")
        assertTrue(!mask[2 * width + 9], "bubble or panel border should be preserved")
    }

    @Test
    fun glyphMaskSupportsLightLettersOnDarkBackground() {
        val width = 16
        val height = 16
        val dark = 0xff181818.toInt()
        val white = 0xfff5f5f5.toInt()
        val pixels = IntArray(width * height) { dark }
        pixels[8 * width + 8] = white

        val mask = textGlyphMaskArgb(width, height, pixels, listOf(NormalizedPanelBox(.1f, .1f, .9f, .9f)))

        assertTrue(mask[8 * width + 8], "reversed white lettering should be masked")
        assertTrue(!mask[2 * width + 2], "dark background should remain")
    }
}
