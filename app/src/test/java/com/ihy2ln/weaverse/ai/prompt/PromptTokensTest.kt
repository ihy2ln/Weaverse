package com.ihy2ln.weaverse.ai.prompt

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PromptTokensTest {
    @Test
    fun apply_fillsNovelAndSeriesTokens() {
        val filled = PromptTokens.apply(
            DefaultAiGuides.workshopChatProse,
            PromptTokenContext(
                tense = "past tense",
                language = "General English",
                bookTitle = "Isekai Gacha",
                seriesTitle = "Adams Haven",
                seriesDescription = "John Doe is isekai'd to many worlds.",
                today = "Thursday, August 13, 2026",
            ),
        )
        assertTrue(filled.contains("Isekai Gacha"))
        assertTrue(filled.contains("Adams Haven"))
        assertTrue(filled.contains("Thursday, August 13, 2026"))
        assertTrue(filled.contains("isekai'd"))
        assertFalse(filled.contains("{book.title}"))
        assertFalse(filled.contains("{series.title}"))
    }

    @Test
    fun apply_fillsTenseOnSceneBeat() {
        val filled = PromptTokens.apply(
            DefaultAiGuides.sceneBeatProse,
            PromptTokenContext(tense = "present tense", language = "General English"),
        )
        assertTrue(filled.contains("present tense"))
        assertTrue(filled.contains("General English"))
        assertEquals(false, filled.contains("{novel.tense}"))
    }
}