package com.ihy2ln.weaverse.core.media

import com.ihy2ln.weaverse.feature.roleplay.chat.PanelAi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Pages that used to drop out of a whole-chapter colorize or translate run. */
class MangaChapterCoverageTest {
    private val tags = listOf("Image API", "ratio:auto", "ratio:1:1", "ratio:2:3", "ratio:3:4", "ratio:9:16")

    @Test
    fun oddlyShapedPageIsPaddedToTheNearestOfferedRatio() {
        val plan = MangaAspectFit.plan(720, 1024, tags)
        assertEquals("2:3", plan.ratio)
        assertEquals(720, plan.canvasWidth)
        assertEquals(1080, plan.canvasHeight)
        assertEquals(28, plan.top)
        assertTrue(plan.padded)
    }

    @Test
    fun pageAlreadyInAnOfferedRatioIsNotPadded() {
        val plan = MangaAspectFit.plan(800, 1200, tags)
        assertEquals("2:3", plan.ratio)
        assertFalse(plan.padded)
    }

    @Test
    fun modelWithoutNumericRatiosLeavesThePageAlone() {
        val plan = MangaAspectFit.plan(720, 1024, listOf("ratio:auto"))
        assertEquals(null, plan.ratio)
        assertFalse(plan.padded)
    }

    @Test
    fun sepiaScanCountsAsBlackAndWhite() {
        // Yellowed paper, grey tone and black ink: one hue, low chroma.
        val paper = 0xfff2e6c8.toInt()
        val tone = 0xff9a9080.toInt()
        val ink = 0xff141210.toInt()
        val pixels = IntArray(300) { when (it % 3) { 0 -> paper; 1 -> tone; else -> ink } }
        assertTrue(isMostlyGrayscaleArgb(pixels))
    }

    @Test
    fun softMultiHueArtIsStillColor() {
        val pixels = IntArray(300) {
            when (it % 3) { 0 -> 0xffc89a8a.toInt(); 1 -> 0xff8aa6c8.toInt(); else -> 0xff9ac88a.toInt() }
        }
        assertFalse(isMostlyGrayscaleArgb(pixels))
    }

    @Test
    fun truncatedVisionAnswerKeepsTheRegionsThatArrivedWhole() {
        val raw = """[{"x":10,"y":10,"w":100,"h":40,"original":"こんにちは","translation":"Hello","language":"ja"},""" +
            """{"x":200,"y":10,"w":100,"h":40,"original":"さよう"""
        val regions = PanelAi.parseTranslatedRegions(raw)
        assertNotNull(regions)
        assertEquals(1, regions!!.size)
        assertEquals("Hello", regions.first().translation)
    }
}
