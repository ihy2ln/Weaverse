package com.ihy2ln.weaverse.feature.novel.codex

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CodexBangTest {
    @Test
    fun theKeywordPicksTheTemplateAndTheRestIsTheBrief() {
        val command = CodexBang.parse("!location a drowned port city on the Marrow")
        assertEquals(CodexEntryKind.Location, command?.kind)
        assertEquals("!location", command?.keyword)
        assertEquals("a drowned port city on the Marrow", command?.brief)
    }

    @Test
    fun theUsualSpellingsAllWork() {
        listOf("!Character", "!char ", "!NPC scarred guard", "!roster").forEach { input ->
            assertEquals(CodexEntryKind.Character, CodexBang.parse(input)?.kind, input)
        }
        assertEquals(CodexEntryKind.Item, CodexBang.parse("!object a black iron key")?.kind)
        assertEquals(CodexEntryKind.Item, CodexBang.parse("!item a black iron key")?.kind)
        assertEquals(CodexEntryKind.Lore, CodexBang.parse("!lore the Sundering")?.kind)
        assertEquals(CodexEntryKind.Other, CodexBang.parse("!other the Ninefold Court")?.kind)
    }

    @Test
    fun separatorsAfterTheKeywordAreNotPartOfTheBrief() {
        assertEquals("Blackreach", CodexBang.parse("!location: Blackreach")?.brief)
        assertEquals("Blackreach", CodexBang.parse("!location — Blackreach")?.brief)
        assertEquals("", CodexBang.parse("!lore")?.brief)
    }

    @Test
    fun ordinaryProseIsNeverHijacked() {
        // Only a leading, known keyword counts — exclamation marks are just punctuation.
        assertNull(CodexBang.parse("Stop right there!"))
        assertNull(CodexBang.parse("!!!"))
        assertNull(CodexBang.parse("!banana a fruit"))
        assertNull(CodexBang.parse("He shouted !character at nobody"))
        assertNull(CodexBang.parse(""))
    }

    @Test
    fun leadingWhitespaceStillCounts() {
        assertEquals(CodexEntryKind.Location, CodexBang.parse("   !place the old mill")?.kind)
    }

    @Test
    fun suggestionsNarrowAsTheKeywordIsTyped() {
        val suggestions = CodexBang.suggestions("!lo")
        assertTrue(suggestions.contains("!location"), suggestions.toString())
        assertTrue(suggestions.contains("!loc"), suggestions.toString())
        assertTrue(suggestions.none { it == "!character" }, suggestions.toString())
        assertTrue(CodexBang.suggestions("!").size > 4)
    }
}
