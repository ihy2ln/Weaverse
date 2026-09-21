package com.ihy2ln.weaverse.feature.novel.start

import com.ihy2ln.weaverse.core.story.StoryCompanionMode
import com.ihy2ln.weaverse.feature.roleplay.campaign.RpgChapterOutline
import com.ihy2ln.weaverse.feature.roleplay.campaign.RpgOpeningSceneGuideline
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NovelStartPlannerTest {

    private val solo = NovelStartSetup(
        title = "The Long Walk Home",
        genre = "Literary fantasy",
        companions = StoryCompanionMode.Solo,
    )

    @Test
    fun `every prompt leads with the company rule`() {
        listOf(
            novelSuggestionPrompt(solo),
            novelOutlinePrompt(solo, mapOf("plot" to "A hidden mystery")),
            novelOpeningPrompt(solo, RpgChapterOutline(), RpgOpeningSceneGuideline()),
        ).forEach { prompt ->
            assertTrue(prompt.startsWith("PARTY SIZE: SOLO"), "prompt should open with the company rule")
        }
    }

    @Test
    fun `the opening prompt repeats the rule at the end`() {
        val prompt = novelOpeningPrompt(solo, RpgChapterOutline(), RpgOpeningSceneGuideline())
        assertTrue(prompt.trimEnd().endsWith("stay where they are."))
    }

    @Test
    fun `no table-only wording reaches a novel prompt`() {
        val prompt = novelOutlinePrompt(solo, mapOf("goal" to "Find a way home"))
        listOf("Rule system", "house rules", "Play as", "GM", "dice").forEach { term ->
            assertFalse(prompt.contains(term, ignoreCase = true), "novel prompt should not mention $term")
        }
    }

    @Test
    fun `answers are carried into the outline prompt`() {
        val prompt = novelOutlinePrompt(solo, mapOf("plot" to "A difficult homecoming", "tone" to "Grim survival"))
        assertTrue(prompt.contains("A difficult homecoming"))
        assertTrue(prompt.contains("Grim survival"))
    }
}
