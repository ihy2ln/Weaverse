package com.ihy2ln.weaverse.feature.prompt

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PromptWordLimitTest {
    @Test
    fun `range instruction clamps and includes both targets`() {
        assertEquals(
            "Target 500–750 words. Do not exceed 750 words.",
            PromptWordLimit.instruction(500, 750),
        )
        assertEquals(
            "Target 200–200 words. Do not exceed 200 words.",
            PromptWordLimit.instruction(500, 200),
        )
    }
    @Test
    fun trimsGeneratedTextToExactSelectedMaximum() {
        val source = (1..120).joinToString(" ") { "word$it" }
        val result = PromptWordLimit.trim(source, 100)
        assertEquals(100, PromptWordLimit.count(result))
        assertTrue(result.endsWith("word100"))
    }

    @Test
    fun keepsShortTextAndParagraphFormatting() {
        val source = "First paragraph.\n\nSecond paragraph."
        assertEquals(source, PromptWordLimit.trim(source, 100))
    }

    @Test
    fun presetsCoverShortAndLongGenerations() {
        assertEquals(100, PromptWordLimit.presets.first())
        assertEquals(4000, PromptWordLimit.presets.last())
    }
}
