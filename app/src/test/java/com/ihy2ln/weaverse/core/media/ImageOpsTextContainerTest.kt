package com.ihy2ln.weaverse.core.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Container resolution decides where English is allowed to sit. Getting it wrong is what puts
 * black lettering on a black caption, or pushes a line out of its balloon and onto artwork.
 */
class ImageOpsTextContainerTest {

    private val white = 0xffffffff.toInt()
    private val black = 0xff000000.toInt()
    private val art = 0xff7a3b12.toInt()

    /** Paints [box] in [color] over an [outside]-coloured page. */
    private fun page(
        width: Int,
        height: Int,
        outside: Int,
        box: IntArray,
        color: Int,
    ): IntArray {
        val pixels = IntArray(width * height) { outside }
        val (x0, y0, x1, y1) = box
        for (y in y0 until y1) for (x in x0 until x1) pixels[y * width + x] = color
        return pixels
    }

    private operator fun IntArray.component4(): Int = this[3]

    @Test
    fun whiteLetteringOnADarkCaptionResolvesToTheBlackBox() {
        val width = 60
        val height = 60
        // A black caption box with the OCR hit on a narrow column of white glyphs inside it.
        val pixels = page(width, height, white, intArrayOf(10, 10, 50, 50), black)
        for (row in 0 until 7) {
            val top = 16 + row * 4
            for (x in 26 until 34) pixels[top * width + x] = white
            pixels[(top + 1) * width + 29] = white
            pixels[(top + 1) * width + 30] = white
        }

        val frame = textContainerArgb(
            width,
            height,
            pixels,
            NormalizedPanelBox(26f / width, 16f / height, 34f / width, 44f / height),
        )

        assertTrue(frame.isDark, "a black caption box must be reported as dark")
        assertTrue(frame.box.left * width <= 11f, "container should reach the caption's left edge")
        assertTrue(frame.box.right * width >= 49f, "container should reach the caption's right edge")
        assertEquals(android.graphics.Color.WHITE, ImageOps.contrastingFillColor(frame.backgroundLuminance))
    }

    @Test
    fun darkLetteringOnALightBubbleResolvesToTheWhiteBubble() {
        val width = 60
        val height = 60
        val pixels = page(width, height, art, intArrayOf(10, 10, 50, 50), white)
        // Two lines of lettering with paper around and between them.
        for (x in 20 until 40 step 3) {
            pixels[23 * width + x] = black
            pixels[24 * width + x] = black
            pixels[27 * width + x] = black
            pixels[28 * width + x] = black
        }

        val frame = textContainerArgb(
            width,
            height,
            pixels,
            NormalizedPanelBox(20f / width, 22f / height, 40f / width, 30f / height),
        )

        assertFalse(frame.isDark, "a white bubble must be reported as light")
        assertTrue(frame.box.top * height <= 11f, "container should reach the bubble's top edge")
        // Growth is capped per direction so a tall balloon never drags lettering onto art; what
        // matters is that it opens up beyond the glyph box and stops inside the bubble.
        assertTrue(frame.box.bottom * height > 30f, "container should open below the glyph box")
        assertTrue(frame.box.bottom * height <= 50f, "container must not cross the bubble edge onto artwork")
        assertEquals(android.graphics.Color.BLACK, ImageOps.contrastingFillColor(frame.backgroundLuminance))
    }

    @Test
    fun letteringOverArtworkDoesNotExpandAcrossThePage() {
        val width = 60
        val height = 60
        // A sound effect drawn straight onto busy art: there is no container to grow into.
        val pixels = IntArray(width * height) { index ->
            if ((index / width + index % width) % 2 == 0) art else 0xff2b1405.toInt()
        }
        for (y in 28 until 34) for (x in 24 until 36) pixels[y * width + x] = white

        val frame = textContainerArgb(
            width,
            height,
            pixels,
            NormalizedPanelBox(24f / width, 28f / height, 36f / width, 34f / height),
        )

        val grownWidth = (frame.box.right - frame.box.left) * width
        assertTrue(grownWidth < width * 0.75f, "artwork-backed text must not claim the whole page, got $grownWidth")
    }

    @Test
    fun contrastingStrokeIsAlwaysTheOppositeOfTheFill() {
        assertEquals(android.graphics.Color.BLACK, ImageOps.contrastingStrokeColor(20))
        assertEquals(android.graphics.Color.WHITE, ImageOps.contrastingStrokeColor(240))
    }

    @Test
    fun colorHexRoundTripsThroughTheOverlayFormat() {
        assertEquals("#FFFFFF", ImageOps.colorIntToHex(android.graphics.Color.WHITE))
        assertEquals("#000000", ImageOps.colorIntToHex(android.graphics.Color.BLACK))
        assertEquals(
            android.graphics.Color.WHITE,
            hexToColorInt(ImageOps.colorIntToHex(android.graphics.Color.WHITE)),
        )
    }

    @Test
    fun whiteGlyphsOnBlackAreDetectedAsGlyphsNotAsBackground() {
        val width = 40
        val height = 40
        val pixels = IntArray(width * height) { black }
        for (y in 18 until 22) for (x in 16 until 24) pixels[y * width + x] = white

        val mask = textGlyphMaskArgb(
            width,
            height,
            pixels,
            listOf(NormalizedPanelBox(10f / width, 10f / height, 30f / width, 30f / height)),
        )

        assertTrue(mask[20 * width + 20], "light lettering on a dark caption must be masked for removal")
        assertFalse(mask[12 * width + 12], "the caption background itself must survive")
    }

    @Test
    fun antialiasedGlyphEdgesAreMaskedWithTheGlyph() {
        val width = 40
        val height = 40
        val pixels = IntArray(width * height) { white }
        for (y in 18 until 22) for (x in 16 until 24) pixels[y * width + x] = black
        // A soft grey fringe, as a scanned page produces around printed lettering.
        for (x in 15 until 25) pixels[17 * width + x] = 0xff9a9a9a.toInt()

        val mask = textGlyphMaskArgb(
            width,
            height,
            pixels,
            listOf(NormalizedPanelBox(10f / width, 10f / height, 30f / width, 30f / height)),
        )

        assertTrue(mask[17 * width + 20], "the antialiased fringe must be removed with the glyph")
    }

    @Test
    fun aFlatDarkCaptionIsRepaintedInItsOwnColourNotReconstructed() {
        val width = 60
        val height = 60
        val pixels = page(width, height, white, intArrayOf(10, 10, 50, 50), black)
        // Stroke-shaped white glyphs down a column, with caption showing between them.
        for (row in 0 until 7) {
            val top = 16 + row * 4
            for (x in 26 until 34) pixels[top * width + x] = white
            pixels[(top + 1) * width + 29] = white
            pixels[(top + 1) * width + 30] = white
        }
        val box = NormalizedPanelBox(26f / width, 16f / height, 34f / width, 44f / height)
        val mask = textGlyphMaskArgb(width, height, pixels, listOf(box))
        val container = textContainerArgb(width, height, pixels, box).box

        assertTrue(mask.any { it }, "white strokes on a black caption must be detected")
        val fill = flatContainerColorArgb(width, height, pixels, container, box, mask)

        assertEquals(black, fill, "a flat black caption must be repainted black")
    }

    @Test
    fun aFlatWhiteBubbleIsRepaintedWhite() {
        val width = 60
        val height = 60
        val pixels = page(width, height, art, intArrayOf(10, 10, 50, 50), white)
        // Two lines of black lettering with paper showing around and between them.
        for (x in 20 until 40 step 3) {
            pixels[23 * width + x] = black
            pixels[24 * width + x] = black
            pixels[27 * width + x] = black
            pixels[28 * width + x] = black
        }
        val box = NormalizedPanelBox(20f / width, 22f / height, 40f / width, 30f / height)
        val mask = textGlyphMaskArgb(width, height, pixels, listOf(box))
        val container = textContainerArgb(width, height, pixels, box).box

        assertTrue(mask.any { it }, "dark lettering on a white bubble must be detected")
        assertEquals(white, flatContainerColorArgb(width, height, pixels, container, box, mask))
    }

    @Test
    fun letteringOverArtworkFallsBackToInpaintingInsteadOfAFlatFill() {
        val width = 60
        val height = 60
        // Screentone-ish artwork: no single colour could stand in for it.
        val pixels = IntArray(width * height) { index ->
            if ((index / width + index % width) % 2 == 0) art else 0xff2b1405.toInt()
        }
        // A sound effect drawn straight onto the art: strokes, with artwork showing between.
        for (x in 22 until 38 step 4) {
            for (y in 26 until 36) pixels[y * width + x] = white
        }
        val box = NormalizedPanelBox(20f / width, 24f / height, 40f / width, 38f / height)
        val mask = textGlyphMaskArgb(width, height, pixels, listOf(box))
        val container = textContainerArgb(width, height, pixels, box).box

        assertNull(
            flatContainerColorArgb(width, height, pixels, container, box, mask),
            "textured artwork must not be flattened to one colour",
        )
    }

    @Test
    fun theGlyphPixelsThemselvesNeverSkewTheMeasuredBackground() {
        val width = 60
        val height = 60
        // Dense lettering: a naive average over the box would land mid-grey.
        val pixels = page(width, height, white, intArrayOf(10, 10, 50, 50), black)
        for (y in 14 until 46 step 3) for (x in 20 until 40) pixels[y * width + x] = white
        val box = NormalizedPanelBox(20f / width, 14f / height, 40f / width, 46f / height)
        val mask = textGlyphMaskArgb(width, height, pixels, listOf(box))
        val container = textContainerArgb(width, height, pixels, box).box

        val fill = flatContainerColorArgb(width, height, pixels, container, box, mask)

        assertEquals(black, fill, "masked glyph pixels must be excluded from the sample")
    }

}
