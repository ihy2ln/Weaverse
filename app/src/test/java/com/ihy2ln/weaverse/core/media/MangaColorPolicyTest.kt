package com.ihy2ln.weaverse.core.media

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MangaColorPolicyTest {
    private fun luminance(c: Int): Double = .299 * (c ushr 16 and 255) + .587 * (c ushr 8 and 255) + .114 * (c and 255)

    @Test fun keepsBlackWhiteAndExistingColor() {
        for (color in listOf(0xff000000.toInt(), 0xffffffff.toInt(), 0xff1020ee.toInt())) {
            assertEquals(color, MangaColorPolicy.colorPixel(color, 0xffee2211.toInt()))
        }
    }
    @Test fun transfersColorWithoutInventingShadowsOrChangingAlpha() {
        for (gray in 0..255) for (color in listOf(0xffff0000.toInt(), 0xff00ff00.toInt(), 0xff0000ff.toInt())) {
            val original = 0x7f000000 or (gray shl 16) or (gray shl 8) or gray
            val result = MangaColorPolicy.colorPixel(original, color)
            assertEquals(luminance(original), luminance(result), .6)
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
