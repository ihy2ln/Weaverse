package com.ihy2ln.weaverse.core.media

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MangaColorPolicyTest {
    private fun luminance(c: Int): Double = .299 * (c ushr 16 and 255) + .587 * (c ushr 8 and 255) + .114 * (c and 255)
    private fun chroma(c: Int): Int {
        val r = c ushr 16 and 255; val g = c ushr 8 and 255; val b = c and 255
        return maxOf(r, g, b) - minOf(r, g, b)
    }

    @Test fun keepsInkAndExistingColor() {
        for (color in listOf(0xff000000.toInt(), 0xff1020ee.toInt())) {
            assertEquals(color, MangaColorPolicy.colorPixel(color, 0xffee2211.toInt()))
        }
        // White paper the model left white stays white.
        assertEquals(0xffffffff.toInt(), MangaColorPolicy.colorPixel(0xffffffff.toInt(), 0xffffffff.toInt()))
    }

    @Test fun whiteAreasTakeTheModelsColor() {
        val skin = 0xfff1c27d.toInt()
        val result = MangaColorPolicy.colorPixel(0xffffffff.toInt(), skin)
        assertTrue(chroma(result) > 40, "white paper should take the skin tone, got ${Integer.toHexString(result)}")
        assertEquals(luminance(skin), luminance(result), 1.0)
    }

    @Test fun neverLightensTheDrawingOrChangesAlpha() {
        for (gray in 0..255) for (color in listOf(0xffff0000.toInt(), 0xff00ff00.toInt(), 0xff0000ff.toInt(), 0xfff1c27d.toInt())) {
            val original = 0x7f000000 or (gray shl 16) or (gray shl 8) or gray
            val result = MangaColorPolicy.colorPixel(original, color)
            assertTrue(luminance(result) <= luminance(original) + .6)
            assertEquals(0x7f, result ushr 24)
        }
        assertNotEquals(0xff808080.toInt(), MangaColorPolicy.colorPixel(0xff808080.toInt(), 0xffff0000.toInt()))
    }

    @Test fun guideCannotRemovePreservationInstructions() {
        val prompt = MangaColorPolicy.prompt("Muted teal coat and warm skin tones.")
        assertTrue(prompt.contains("Muted teal coat"))
        assertTrue(prompt.contains("Do not redraw"))
        assertTrue(prompt.contains("subordinate"))
    }
}
