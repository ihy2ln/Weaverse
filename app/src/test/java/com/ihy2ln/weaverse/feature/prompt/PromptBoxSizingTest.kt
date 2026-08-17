package com.ihy2ln.weaverse.feature.prompt

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PromptBoxSizingTest {
    @Test
    fun emptyAndSingleLineStayAtOne() {
        assertEquals(1, PromptBoxSizing.lineCount(""))
        assertEquals(1, PromptBoxSizing.lineCount("Continue the scene"))
        assertEquals(1, PromptBoxSizing.fieldMaxLines("Continue the scene"))
    }

    @Test
    fun extraNewlinesGrowTheField() {
        assertEquals(2, PromptBoxSizing.fieldMaxLines("beat one\nbeat two"))
        assertEquals(3, PromptBoxSizing.fieldMaxLines("a\nb\nc"))
    }

    @Test
    fun capsGrowthSoTheBoxCannotEatTheEditor() {
        val long = (1..20).joinToString("\n") { "line $it" }
        assertEquals(20, PromptBoxSizing.lineCount(long))
        assertEquals(PromptBoxSizing.MaxLines, PromptBoxSizing.fieldMaxLines(long))
    }
}
