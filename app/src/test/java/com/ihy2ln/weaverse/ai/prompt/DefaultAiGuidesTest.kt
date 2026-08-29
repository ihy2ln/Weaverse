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
        assertTrue(prompts.any { it.id == "prompt-custom-wish-fulfilment" && it.folderId == "folder-custom" })
        // Prompt Components (AdditionalContext/AdditionalInstructions, Chat/*) ship with adult male
        // wish fulfilment defaults the user can edit later.
        prompts.filter { it.type != PromptComponentType }.forEach { prompt ->
            assertTrue(prompt.instructionsJson.length > 80, "${prompt.name} should have prose")
            assertTrue(prompt.description.isNotBlank())
        }
    }

    @Test
    fun seedPrompts_carryWishFulfilmentCanonInEveryTemplate() {
        val canon = listOf("WAHB", "WAH", "WAHO", "AFM", "Gender Ratio", "GKOM", "Celestium")
        DefaultAiGuides.seedPrompts(0L).forEach { prompt ->
            canon.forEach { term ->
                assertTrue(prompt.instructionsJson.contains(term), "${prompt.name} should include $term")
            }
            assertTrue(
                prompt.instructionsJson.contains("wish fulfilment", ignoreCase = true) ||
                    prompt.instructionsJson.contains("wish-fulfilment", ignoreCase = true),
                "${prompt.name} should carry the adult male wish fulfilment frame",
            )
        }
    }

    @Test
    fun adamsHavenMw_isSeededAsAnAdultOnlySceneTemplateWithRequestedCanon() {
        val prompt = DefaultAiGuides.seedPrompts(0L).single { it.id == "prompt-adams-haven-mw" }
        val instructions = prompt.instructionsJson

        assertTrue(prompt.name == "Adams Haven MW")
        assertTrue(prompt.folderId == "folder-adams-haven")
        assertTrue(prompt.type == "scene_beat")
        assertTrue(instructions.contains("adult-themed ecchi mangaka"))
        assertTrue(instructions.contains("18 or older"))
        listOf("WAHB", "WAH", "WAHO", "AFM", "Gender Ratio", "GKOM", "Celestium").forEach { term ->
            assertTrue(instructions.contains(term), "Adams Haven MW should include $term")
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
