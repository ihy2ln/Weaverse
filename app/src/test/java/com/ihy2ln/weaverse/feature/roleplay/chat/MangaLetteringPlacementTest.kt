package com.ihy2ln.weaverse.feature.roleplay.chat

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MangaLetteringPlacementTest {
    private fun region(x: Float) = PanelTextRegion(x = .1f, y = .1f, w = .8f, h = .5f,
        original = "図書館", translation = "Wisdom? I see… it's a library.",
        cleanupX = x, cleanupY = .25f, cleanupW = .15f, cleanupH = .1f)

    @Test fun separatePassagesOnSharedWhiteBackground() {
        val original = listOf(region(.2f), region(.55f))
        val result = MangaLetteringPlacement.constrain(original, .7f)
        assertTrue(result[0].x + result[0].w < result[1].x)
        assertTrue(result.all { it.w <= .25f && it.fontSizePx in 18f..38f })
        assertEquals(original.map { it.cleanupX }, result.map { it.cleanupX })
        assertEquals(original.map { it.translation }, result.map { it.translation })
    }
    @Test fun manualEditsRemainUntouchedAndPreferredSizeIsRetained() {
        val manual = region(.2f).copy(edited = true, fontSizePx = 22f)
        assertEquals(manual, MangaLetteringPlacement.constrain(listOf(manual), .7f).single())
        assertEquals(22f, MangaLetteringPlacement.preferredSize(manual, .7f))
    }
    @Test fun coincidentPassagesNeedReviewInsteadOfSilentOverlap() {
        assertTrue(MangaLetteringPlacement.constrain(listOf(region(.2f), region(.2f)), .7f).all { it.reviewRequired })
    }
}
