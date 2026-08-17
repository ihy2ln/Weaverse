package com.ihy2ln.weaverse.core.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ByteSizeFormatTest {
    @Test
    fun `bytes under 1024 render as a plain byte count`() {
        assertEquals("500 B", formatByteSize(500))
        assertEquals("0 B", formatByteSize(0))
    }

    @Test
    fun `kilobytes and megabytes round to one decimal place`() {
        assertEquals("1.5 KB", formatByteSize(1500))
        assertEquals("1.4 GB", formatByteSize(1_500_000_000))
    }
}
