package com.ihy2ln.weaverse.core.story

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StoryStartTemplateTest {
    @Test
    fun soloRuleForbidsTheCompanionModelsKeepAdding() {
        val rule = companionRule(StoryCompanionMode.Solo).lowercase()
        listOf("companion", "sidekick", "guide", "rescuer", "talking animal").forEach {
            assertTrue(rule.contains(it), "solo rule should name $it")
        }
        // It must still allow other people to exist, or scenes become empty rooms.
        assertTrue(rule.contains("other people still exist"))
    }

    @Test
    fun unknownOrMissingIdFallsBackToParty() {
        assertEquals(StoryCompanionMode.Party, StoryCompanionMode.fromId(null))
        assertEquals(StoryCompanionMode.Party, StoryCompanionMode.fromId("nonsense"))
        assertEquals(StoryCompanionMode.Solo, StoryCompanionMode.fromId("SOLO"))
    }

    @Test
    fun promptBlockLeadsWithCompanyAndSkipsBlankAnswers() {
        val block = storyStartPromptBlock(
            companions = StoryCompanionMode.Solo,
            answers = mapOf("plot" to "A hidden mystery", "goal" to "   "),
            vocabulary = StoryStartVocabulary.Novel,
        )
        assertTrue(block.startsWith("PARTY SIZE: SOLO"))
        assertTrue(block.contains("Plot: A hidden mystery"))
        assertTrue(!block.contains("Goal:"))
        assertTrue(block.trimEnd().endsWith("Alone. No companion joins, now or later, unless you ask."))
    }

    @Test
    fun bothModesShareTheSameQuestionIds() {
        assertEquals(
            storyStartQuestions(StoryStartVocabulary.Rpg).map { it.id },
            storyStartQuestions(StoryStartVocabulary.Novel).map { it.id },
        )
    }
}
