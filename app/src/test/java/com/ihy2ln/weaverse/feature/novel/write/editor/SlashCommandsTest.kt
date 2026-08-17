package com.ihy2ln.weaverse.feature.novel.write.editor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SlashCommandsTest {
    @Test
    fun `blank query returns every command`() {
        assertEquals(SlashCommands.all.size, SlashCommands.filter("").size)
    }

    @Test
    fun `filter matches by label substring, case-insensitively`() {
        val results = SlashCommands.filter("scene")
        assertTrue(results.any { it.id == SlashCommands.SCENE_BEAT })
        assertTrue(results.any { it.id == SlashCommands.SCENE_BREAK })
        assertTrue(results.all { it.label.contains("scene", ignoreCase = true) })
    }

    @Test
    fun `filter with no matches returns an empty list`() {
        assertTrue(SlashCommands.filter("zzzznotacommand").isEmpty())
    }

    @Test
    fun `every command id is unique`() {
        val ids = SlashCommands.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `ai generation commands are exactly scene beat and continue writing`() {
        assertEquals(setOf(SlashCommands.SCENE_BEAT, SlashCommands.CONTINUE_WRITING), SlashCommands.aiGenerationCommands)
    }
}
