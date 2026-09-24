package com.ihy2ln.weaverse.ai.context

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContextMeterTest {
    @Test
    fun formatCount_usesKForThousands() {
        assertEquals("12.4k", ContextMeter.formatCount(12_400))
        assertEquals("1.0k", ContextMeter.formatCount(1_000))
        assertEquals("32.8k", ContextMeter.formatCount(32_768))
        assertEquals("42", ContextMeter.formatCount(42))
    }

    @Test
    fun estimateTokens_isRoughlyCharsOverFour() {
        assertEquals(0, ContextMeter.estimateTokens(""))
        assertEquals(25, ContextMeter.estimateTokens("a".repeat(100)))
    }

    @Test
    fun reading_labelShowsUsedAndLimit() {
        val assembled = AssembledPrompt(
            systemBlocks = listOf("system ".repeat(100)),
            messages = listOf("user" to "hello"),
            usedEntries = emptyList(),
            tokenBreakdown = emptyList(),
        )
        val reading = ContextMeter.reading(assembled, extraUser = "more", limitTokens = 32_768)
        assertTrue(reading.usedTokens > 0)
        assertEquals(32_768, reading.limitTokens)
        assertTrue(reading.label.startsWith("context: "))
        assertTrue(reading.label.contains("/ 32.8k"))
    }
}
