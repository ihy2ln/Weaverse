package com.ihy2ln.weaverse.ai.prompt

import com.ihy2ln.weaverse.feature.shell.AppMode
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DefaultAiGuidesTest {
    @Test
    fun drafts_areFilledProse() {
        AppMode.entries.forEach { mode ->
            val draft = DefaultAiGuides.draftFor(mode)
            assertTrue(draft.length > 80, "draft for $mode should be guiding prose")
            assertFalse(draft.contains("TODO", ignoreCase = true))
        }
    }

    @Test
    fun seedPrompts_includeRoleplayAndContinue() {
        val prompts = DefaultAiGuides.seedPrompts(0L)
        assertTrue(prompts.any { it.id == "prompt-roleplay-reply" })
        assertTrue(prompts.any { it.id == "prompt-continue" })
        assertTrue(prompts.any { it.id == "prompt-scene-beat" && it.instructionsJson.contains("Pantser") })
        assertTrue(prompts.any { it.id == "prompt-summarize" && it.instructionsJson.contains("summarizer") })
        assertTrue(prompts.any { it.id == "prompt-replace" && it.name == "Scene Text Replacer" })
        assertTrue(prompts.any { it.id == "prompt-workshop-chat" && it.instructionsJson.contains("{book.title}") })
        prompts.forEach { prompt ->
            assertTrue(prompt.instructionsJson.length > 80, "${prompt.name} should have prose")
            assertTrue(prompt.description.isNotBlank())
        }
    }

    @Test
    fun thinSystemPrompt_detectsOldDefaults() {
        assertTrue(DefaultAiGuides.isThinSystemPrompt("Amara", ""))
        assertTrue(DefaultAiGuides.isThinSystemPrompt("Amara", "You are Amara. Stay in character."))
        assertTrue(DefaultAiGuides.isThinSystemPrompt("Mara", "You are Mara, a historian in Adams Haven."))
        assertFalse(
            DefaultAiGuides.isThinSystemPrompt(
                "Mara",
                DefaultAiGuides.characterSystemPrompt("Mara", "A historian.", "Dry humor.", "Harbor café."),
            ),
        )
    }

    @Test
    fun characterSystemPrompt_includesCardFields() {
        val prose = DefaultAiGuides.characterSystemPrompt(
            name = "Mara",
            description = "A sharp-eyed local historian.",
            personality = "Curious, dry humor.",
            scenario = "Harbor café.",
        )
        assertTrue(prose.contains("Mara"))
        assertTrue(prose.contains("historian"))
        assertTrue(prose.contains("Curious"))
        assertTrue(prose.contains("Harbor"))
    }
}
