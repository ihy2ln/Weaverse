package com.ihy2ln.weaverse.feature.novel.start

import com.ihy2ln.weaverse.core.story.StoryCompanionMode
import com.ihy2ln.weaverse.feature.roleplay.campaign.RpgAdventurePlan
import com.ihy2ln.weaverse.feature.roleplay.campaign.RpgChapterOutline
import com.ihy2ln.weaverse.feature.roleplay.campaign.RpgOpeningSceneGuideline
import com.ihy2ln.weaverse.feature.roleplay.campaign.RpgPlanAnswer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NovelStartPlannerTest {

    private val solo = NovelSetupSnapshot(
        title = "The Long Walk Home",
        genre = "Literary fantasy",
        setting = "A drowned coast",
        companions = StoryCompanionMode.Solo.id,
    )

    private val plan = RpgAdventurePlan(
        listOf(
            RpgPlanAnswer("plot", "A difficult homecoming"),
            RpgPlanAnswer("tone", "Grim and close"),
            RpgPlanAnswer("goal", "", skipped = true),
        ),
    )

    @Test
    fun `the six questions match the campaign's, with cast in place of party`() {
        assertEquals(
            listOf("plot", "goal", "scene", "cast", "tone", "complication"),
            novelStartQuestions().map { it.id },
        )
    }

    @Test
    fun `every prompt leads with the company rule`() {
        listOf(
            novelSuggestionPrompt(solo),
            novelChapterPlanPrompt(solo, plan),
            novelOpeningScenePrompt(solo, plan, RpgChapterOutline(), RpgOpeningSceneGuideline()),
        ).forEach { prompt ->
            assertTrue(prompt.startsWith("PARTY SIZE: SOLO"), "prompt should open with the company rule")
        }
    }

    @Test
    fun `the scene prompt repeats the company rule at the end`() {
        val prompt = novelOpeningScenePrompt(solo, plan, RpgChapterOutline(), RpgOpeningSceneGuideline())
        assertTrue(prompt.trimEnd().endsWith("stay where they are."))
    }

    @Test
    fun `no table-only wording reaches a novel prompt`() {
        listOf(
            novelSuggestionPrompt(solo),
            novelChapterPlanPrompt(solo, plan),
            novelOpeningScenePrompt(solo, plan, RpgChapterOutline(), RpgOpeningSceneGuideline()),
        ).forEach { prompt ->
            listOf("Rule system", "house rule", "Play as", "Player role", "Dungeon Master", "d20").forEach { term ->
                assertFalse(prompt.contains(term, ignoreCase = true), "a novel prompt should not mention $term")
            }
        }
    }

    @Test
    fun `answers are carried into the chapter plan prompt, and a skip falls back`() {
        val prompt = novelChapterPlanPrompt(solo, plan)
        assertTrue(prompt.contains("Plot: A difficult homecoming"))
        assertTrue(prompt.contains("Tone: Grim and close"))
        assertTrue(prompt.contains("First goal: Choose a fitting first objective"))
    }

    @Test
    fun `suggestions parse only when all six questions have three answers`() {
        val full = novelStartQuestions().joinToString(",") { "\"${it.id}\":[\"a\",\"b\",\"c\"]" }
        val parsed = parseNovelSuggestions("noise before {$full} noise after")
        assertNotNull(parsed)
        assertEquals(6, parsed!!.size)
        assertEquals(listOf("a", "b", "c"), parsed["cast"])

        assertNull(parseNovelSuggestions("""{"plot":["a","b","c"]}"""))
        assertNull(parseNovelSuggestions("not json at all"))
    }

    @Test
    fun `the fallback plan fills every field so a failed generation is never a dead end`() {
        val payload = fallbackNovelChapterPlan(solo, plan)
        assertTrue(payload.outline.premise.isNotBlank())
        assertTrue(payload.outline.beats.size >= 3)
        assertTrue(payload.openingScene.startingCast.isNotBlank())
        assertTrue(payload.openingScene.conflictAndStakes.contains("A difficult homecoming"))
    }

    @Test
    fun `the scene fallback offers no choices, because a book has none`() {
        val draft = fallbackNovelSceneDraft(plan, RpgOpeningSceneGuideline(startingCast = "Mira"))
        assertTrue(draft.choices.isEmpty())
        assertTrue(draft.prose.contains("Mira"))
    }
}
