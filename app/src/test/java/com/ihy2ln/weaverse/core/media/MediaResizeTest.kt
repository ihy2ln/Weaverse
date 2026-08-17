package com.ihy2ln.weaverse.core.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MediaResizeTest {
    @Test
    fun `values within the threshold snap to the nearest snap point`() {
        assertEquals(50f, MediaResize.resolve(48f))
        assertEquals(50f, MediaResize.resolve(52f))
        assertEquals(100f, MediaResize.resolve(97f))
    }

    @Test
    fun `values far from any snap point pass through unchanged`() {
        assertEquals(60f, MediaResize.resolve(60f))
        assertEquals(40f, MediaResize.resolve(40f))
    }

    @Test
    fun `values are clamped to the draggable range`() {
        assertEquals(MediaResize.MIN_WIDTH_PERCENT, MediaResize.resolve(5f))
        assertEquals(MediaResize.MAX_WIDTH_PERCENT, MediaResize.resolve(150f))
    }

    @Test
    fun `crossedSnapPoint fires only when landing on a new snap point`() {
        assertTrue(MediaResize.crossedSnapPoint(previousResolved = 48f, newResolved = 50f))
        assertFalse(MediaResize.crossedSnapPoint(previousResolved = 50f, newResolved = 50f))
        assertFalse(MediaResize.crossedSnapPoint(previousResolved = 60f, newResolved = 61f))
    }
}
