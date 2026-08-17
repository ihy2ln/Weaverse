package com.ihy2ln.weaverse.core.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BitmapSamplingTest {
    @Test
    fun `returns 1 when the image already fits`() {
        assertEquals(1, calculateInSampleSize(2000, 1000, 4096))
        assertEquals(1, calculateInSampleSize(4096, 2000, 4096))
    }

    @Test
    fun `doubles until the long edge fits within the target`() {
        // long edge 8192 -> /2 = 4096, still >= 4096 target, so it must go one more step
        assertEquals(4, calculateInSampleSize(8192, 4000, 2048))
    }

    @Test
    fun `uses the longer of width or height`() {
        assertEquals(calculateInSampleSize(8000, 100, 4096), calculateInSampleSize(100, 8000, 4096))
    }

    @Test
    fun `degenerate inputs fall back to 1`() {
        assertEquals(1, calculateInSampleSize(0, 0, 4096))
        assertEquals(1, calculateInSampleSize(-1, 100, 4096))
        assertEquals(1, calculateInSampleSize(100, 100, 0))
    }
}
