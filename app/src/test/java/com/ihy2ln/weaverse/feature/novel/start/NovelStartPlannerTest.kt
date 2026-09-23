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
    fun `template guidance loses its table words on the way into a book`() {
        val rewritten = novelizeGuidance(
            "Run a campaign in the wilds. The party follows the player character, and the " +
                "Dungeon Master withholds secrets when revealing them would undermine play.",
        )
        listOf("campaign", "the party", "player character", "Dungeon Master", "undermine play").forEach { term ->
            assertFalse(rewritten.contains(term, ignoreCase = true), "rewritten guidance still says $term")
        }
        assertTrue(rewritten.startsWith("Set the book in the wilds"))
        assertTrue(rewritten.contains("the cast"))
        assertTrue(rewritten.contains("viewpoint character"))
    }

    @Test
    fun `the every shipped setting template survives the rewrite without table words`() {
        com.ihy2ln.weaverse.core.ui.components.CampaignSettingTemplates.forEach { template ->
            val rewritten = novelizeGuidance(template.directive)
            listOf("Dungeon Master", "the party", "player character").forEach { term ->
                assertFalse(
                    rewritten.contains(term, ignoreCase = true),
                    "${template.label} still says $term after the rewrite",
                )
            }
        }
    }

    @Test
    fun `setting and perspective guidance reach the prompt header`() {
        val setup = solo.copy(
            settingGuidance = "Set the book in a drowned coast.",
            perspectiveGuidance = "Stay close to one viewpoint character.",
        )
        val prompt = novelChapterPlanPrompt(setup, plan)
        assertTrue(prompt.contains("Setting guidance: Set the book in a drowned coast."))
        assertTrue(prompt.contains("Perspective guidance: Stay close to one viewpoint character."))
    }

    @Test
    fun `the genre templates are distinct and short enough to read on a chip`() {
        val genres = novelGenreTemplates()
        assertTrue(genres.size >= 15, "too few genre templates to be worth a row")
        assertEquals(genres.size, genres.distinct().size, "a genre template is duplicated")
        genres.forEach { genre ->
            assertTrue(genre.isNotBlank())
            assertTrue(genre.length <= 24, "\"$genre\" is too long for a chip")
        }
    }

    @Test
    fun `every style-guide template has a unique id, a label, and real guidance`() {
        val templates = novelStyleGuideTemplates()
        assertTrue(templates.size >= 8)
        assertEquals(templates.size, templates.map { it.id }.distinct().size, "a style template id is reused")
        templates.forEach { template ->
            assertTrue(template.label.isNotBlank())
            assertTrue(template.guidance.length >= 80, "${template.label} guidance is too thin to steer prose")
        }
    }

    @Test
    fun `no style-guide template drags table words into a book`() {
        novelStyleGuideTemplates().forEach { template ->
            listOf("player", "the party", "Dungeon Master", "dice", "campaign").forEach { term ->
                assertFalse(
                    template.guidance.contains(term, ignoreCase = true),
                    "${template.label} mentions $term",
                )
            }
        }
    }

    @Test
    fun `the opening prompt asks for a set-up paragraph, not a scene`() {
        val prompt = novelOpeningScenePrompt(solo, plan, RpgChapterOutline(), RpgOpeningSceneGuideline())
        assertTrue(prompt.contains("ONE PARAGRAPH, MAXIMUM"))
        assertTrue(prompt.contains("not the scene itself"))
        assertFalse(prompt.contains("the full scene"))
        assertFalse(prompt.contains("finished prose"))
    }

    @Test
    fun `a model that ignores the limit is cut back to one paragraph`() {
        val overrun = """
            The rain had not stopped in three days.

            "You are late," she said, and did not look up.

            He sat down anyway.
        """.trimIndent()
        val cut = firstParagraphOnly(overrun)
        assertEquals("The rain had not stopped in three days.", cut)
        assertFalse(cut.contains("You are late"))
    }

    @Test
    fun `a paragraph wrapped over several lines survives intact`() {
        val wrapped = "The rain had not stopped\nin three days, and the road\nwas gone."
        assertEquals("The rain had not stopped in three days, and the road was gone.", firstParagraphOnly(wrapped))
    }

    @Test
    fun `trimming an empty or blank draft does not crash`() {
        assertEquals("", firstParagraphOnly(""))
        assertEquals("", firstParagraphOnly("   \n\n  "))
    }

    @Test
    fun `the scene fallback is a single paragraph`() {
        val draft = fallbackNovelSceneDraft(plan, RpgOpeningSceneGuideline(startingCast = "Mira"))
        assertFalse(draft.prose.contains("\n"), "the fallback set-up should be one paragraph")
        assertEquals(draft.prose, firstParagraphOnly(draft.prose))
    }

    @Test
    fun `saved progress survives a round trip`() {
        val progress = NovelStartProgress(
            stepId = "ChapterPlan",
            setup = solo.copy(title = "The Long Walk Home", settingId = "high-fantasy"),
            answers = listOf(
                NovelSavedAnswer("plot", "A difficult homecoming"),
                NovelSavedAnswer("goal", skipped = true),
            ),
            outline = NovelSavedOutline(
                workingTitle = "The First Sign",
                premise = "Something surfaces",
                beats = listOf(NovelSavedBeat("beat-1", "The Opening", "It lands")),
                sceneCast = "Mira",
            ),
            openingProse = "The rain had not stopped.",
            updatedAt = 42L,
        )
        val restored = decodeNovelStartProgress(encodeNovelStartProgress(progress))
        assertEquals(progress, restored)
    }

    @Test
    fun `a blank or broken saved start decodes to nothing rather than throwing`() {
        assertNull(decodeNovelStartProgress(""))
        assertNull(decodeNovelStartProgress("   "))
        assertNull(decodeNovelStartProgress("{ not json"))
        assertNull(decodeNovelStartProgress("[]"))
    }

    @Test
    fun `the resume summary names the step that was reached`() {
        fun summary(step: String, answers: List<NovelSavedAnswer> = emptyList()) =
            novelStartProgressSummary(NovelStartProgress(stepId = step, answers = answers))

        assertTrue(summary("Setup").contains("setup", ignoreCase = true))
        assertTrue(
            summary("Cyoa", listOf(NovelSavedAnswer("plot", "x"), NovelSavedAnswer("goal", skipped = true)))
                .contains("2 of 6"),
        )
        assertTrue(summary("ChapterPlan").contains("Chapter One"))
        assertTrue(summary("Verification").contains("verify", ignoreCase = true))
        assertTrue(summary("Started").contains("set-up", ignoreCase = true))
    }

    @Test
    fun `a saved outline converts back to the shapes the wizard edits`() {
        val saved = NovelSavedOutline(
            workingTitle = "The First Sign",
            premise = "Something surfaces",
            beats = listOf(NovelSavedBeat("beat-1", "The Opening", "It lands", completed = true)),
            sceneTitle = "Chapter One",
            sceneCast = "Mira",
            sceneComplication = "A deadline",
        )
        val outline = saved.toOutline()
        assertEquals("The First Sign", outline.workingTitle)
        assertEquals(1, outline.beats.size)
        assertTrue(outline.beats.first().completed)

        val scene = saved.toSceneGuideline()
        assertEquals("Mira", scene.startingCast)
        assertEquals("A deadline", scene.complication)
    }

    @Test
    fun `an outline with no title still restores a usable chapter name`() {
        assertEquals("Chapter One", NovelSavedOutline().toOutline().workingTitle)
        assertEquals("Chapter One", NovelSavedOutline().toSceneGuideline().title)
    }

    @Test
    fun `the scene fallback offers no choices, because a book has none`() {
        val draft = fallbackNovelSceneDraft(plan, RpgOpeningSceneGuideline(startingCast = "Mira"))
        assertTrue(draft.choices.isEmpty())
        assertTrue(draft.prose.contains("Mira"))
    }
}
