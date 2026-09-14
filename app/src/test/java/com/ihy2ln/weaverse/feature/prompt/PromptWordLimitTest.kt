package com.ihy2ln.weaverse.feature.prompt

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PromptWordLimitTest {
    @Test
    fun `range instruction clamps and includes both targets`() {
        assertEquals(
            "Target 500–750 words. Always complete the final sentence before stopping. " +
                "Aim to stay at or below 750 words, but you may use up to 75 extra words " +
                "only when needed to finish that sentence. Never end with a cut-off sentence or fragment.",
            PromptWordLimit.instruction(500, 750),
        )
        assertEquals(
            "Target 200–200 words. Always complete the final sentence before stopping. " +
                "Aim to stay at or below 200 words, but you may use up to 20 extra words " +
                "only when needed to finish that sentence. Never end with a cut-off sentence or fragment.",
            PromptWordLimit.instruction(500, 200),
        )
    }

    @Test
    fun `allows a small overrun to finish the current sentence`() {
        val first = (1..95).joinToString(" ") { "first$it" } + "."
        val second = (1..10).joinToString(" ") { "second$it" } + "."
        val source = "$first $second trailing words that should not remain."
        val result = PromptWordLimit.trim(source, 100)
        assertEquals(105, PromptWordLimit.count(result))
        assertTrue(result.endsWith("second10."))
    }

    @Test
    fun `uses the previous sentence when the next ending exceeds the allowance`() {
        val first = (1..90).joinToString(" ") { "first$it" } + "."
        val second = (1..50).joinToString(" ") { "second$it" } + "."
        val result = PromptWordLimit.trim("$first $second", 100)
        assertEquals(90, PromptWordLimit.count(result))
        assertTrue(result.endsWith("first90."))
    }

    @Test
    fun `does not create a fragment when unpunctuated text exceeds the target`() {
        val source = (1..120).joinToString(" ") { "word$it" }
        assertEquals(source, PromptWordLimit.trim(source, 100))
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
