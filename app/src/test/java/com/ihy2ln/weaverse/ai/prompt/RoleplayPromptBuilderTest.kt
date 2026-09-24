package com.ihy2ln.weaverse.ai.prompt

import com.ihy2ln.weaverse.data.db.entities.RpCharacterEntity
import com.ihy2ln.weaverse.data.db.entities.RpPersonaEntity
import com.ihy2ln.weaverse.feature.shell.AppMode
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RoleplayPromptBuilderTest {
    @Test
    fun systemBlocks_includeCraftAndCharacterCard() {
        val character = RpCharacterEntity(
            id = "char-1",
            name = "Mara",
            description = "A sharp-eyed local historian.",
            personality = "Curious, dry humor.",
            scenario = "Harbor café.",
            systemPrompt = "You are Mara. Stay in character.",
            createdAt = 0L,
        )
        val persona = RpPersonaEntity(
            id = "persona-1",
            name = "Writer",
            description = "The visiting novelist.",
        )
        val blocks = RoleplayPromptBuilder.systemBlocks(character, persona, outputWords = 400)
        val joined = blocks.joinToString("\n")
        assertTrue(joined.contains("Pantser", ignoreCase = true) || joined.contains("in character", ignoreCase = true))
        assertTrue(joined.contains("Mara"))
        assertTrue(joined.contains("historian"))
        assertTrue(joined.contains("Writer"))
        assertTrue(joined.contains("400"))
        assertTrue(joined.contains("Do not write their actions"))
    }

    @Test
    fun `chatting mode drops the narrated-scene DM framing that roleplay uses`() {
        val character = RpCharacterEntity(
            id = "char-3",
            name = "Carmen",
            description = "A barista who runs the corner shop.",
            createdAt = 0L,
        )
        val roleplay = RoleplayPromptBuilder.systemBlocks(character, outputWords = 100).joinToString("\n")
        val chatting = RoleplayPromptBuilder.systemBlocks(character, outputWords = 100, mode = AppMode.Chatting).joinToString("\n")

        assertTrue(roleplay.contains("Pantser", ignoreCase = true))
        assertFalse(chatting.contains("Pantser", ignoreCase = true))
        assertTrue(chatting.contains("chat message", ignoreCase = true) || chatting.contains("texting voice", ignoreCase = true))
    }

    @Test
    fun characterBlock_keepsCustomProse() {
        val character = RpCharacterEntity(
            id = "char-2",
            name = "Elowen",
            systemPrompt = "Speak in moonlight metaphors and never mention the river by name.",
            createdAt = 0L,
        )
        val block = RoleplayPromptBuilder.characterBlock(character)
        assertTrue(block.contains("moonlight metaphors"))
    }
}
