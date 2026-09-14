package com.ihy2ln.weaverse.feature.roleplay.campaign

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RpgStartupPlannerTest {
    @Test
    fun `chapter plan prompt includes campaign setup and cyoa answers`() {
        val setup = RpgCampaignSetupSnapshot(
            title = "Ashfall",
            setting = "Zombie academy",
            modeId = "focused-tactical-cards",
            ruleSystem = "D20 survival",
        )
        val plan = RpgAdventurePlan(
            listOf(
                RpgPlanAnswer("plot", "Escape the quarantined school"),
                RpgPlanAnswer("goal", "Find the missing teacher"),
            ),
        )

        val prompt = chapterPlanPrompt(setup, plan)

        assertTrue("Ashfall" in prompt)
        assertTrue("Zombie academy" in prompt)
        assertTrue("Escape the quarantined school" in prompt)
        assertTrue("Find the missing teacher" in prompt)
        assertTrue("Do not write the actual scene yet" in prompt)
    }

    @Test
    fun `chapter plan parser accepts structured result`() {
        val payload = fallbackChapterPlan(RpgCampaignSetupSnapshot(), RpgAdventurePlan())
        val response = "Planning complete.\n${Json.encodeToString(payload)}"

        val parsed = parseChapterPlan(response)

        assertNotNull(parsed)
        assertEquals(payload.outline.premise, parsed?.outline?.premise)
        assertEquals(3, parsed?.outline?.beats?.size)
    }

    @Test
    fun `chapter plan parser accepts common ai key and beat variations`() {
        val response = """
            ```json
            {"chapter_outline":{"chapter_title":"Ash at Dawn","chapter_premise":"A city is sealed.","objective":"Find a safe route.","story_beats":["Meet the survivor","Cross the market","Reach the gate"]},"opening_scene":{"scene_title":"Locked In","location_time_and_atmosphere":"School roof at sunset","cast":"JD","first_decision_hook":"Choose a route"}}
            ```
        """.trimIndent()

        val parsed = parseChapterPlan(response)

        assertEquals("Ash at Dawn", parsed?.outline?.workingTitle)
        assertEquals(3, parsed?.outline?.beats?.size)
        assertEquals("School roof at sunset", parsed?.openingScene?.locationAndAtmosphere)
    }

    @Test
    fun `missing ai fields are completed locally instead of blocking progress`() {
        val fallback = fallbackChapterPlan(RpgCampaignSetupSnapshot(setting = "Adams Haven"), RpgAdventurePlan())
        val partial = parseChapterPlan("""{"outline":{"title":"AI title"}}""")

        val completed = completeChapterPlan(partial, fallback)

        assertEquals("AI title", completed.outline.workingTitle)
        assertTrue(completed.outline.premise.isNotBlank())
        assertTrue(completed.openingScene.locationAndAtmosphere.isNotBlank())
        assertTrue(completed.outline.beats.size >= 3)
    }

    @Test
    fun `campaign suggestion parser requires three answers for every question`() {
        val response = """{"plot":["p1","p2","p3"],"goal":["g1","g2","g3"],"scene":["s1","s2","s3"],"party":["a","b","c"],"tone":["t1","t2","t3"],"complication":["c1","c2","c3"]}"""

        val suggestions = parseCyoaSuggestions(response)

        assertNotNull(suggestions)
        assertEquals(3, suggestions?.get("plot")?.size)
        assertEquals(null, parseCyoaSuggestions("""{"plot":["only one"]}"""))
    }

    @Test
    fun `scene parser plus completion always provides three actions`() {
        val valid = """{"prose":"The gate opens.","choices":["Enter","Wait","Flee"],"sceneArtTags":"gate,night"}"""
        val partial = """{"narration":"The gate opens.","actions":["Enter"],"art_tags":["gate","night"]}"""

        assertNotNull(parseSceneDraft(valid))
        val completed = completeSceneDraft(parseSceneDraft(partial), fallbackSceneDraft(RpgAdventurePlan(), RpgOpeningSceneGuideline()))
        assertEquals(3, completed.choices.size)
        assertEquals("The gate opens.", completed.prose)
    }

    @Test
    fun `skipped answers remain blank and use a safe fallback`() {
        val plan = RpgAdventurePlan(listOf(RpgPlanAnswer("plot", skipped = true)))

        assertEquals("Fallback plot", plan.answer("plot", "Fallback plot"))
        assertFalse(plan.answers.single().value.isNotBlank())
    }

    @Test
    fun `startup schema two persists wizard state`() {
        val state = createRpgCampaign("campaign").copy(
            startup = RpgStartupState(
                step = RpgStartupStep.Verification,
                plan = RpgAdventurePlan(listOf(RpgPlanAnswer("plot", "A living storm"))),
            ),
        )

        val restored = Json.decodeFromString<RpgCampaignState>(Json.encodeToString(state))

        assertEquals(2, restored.schemaVersion)
        assertEquals(RpgStartupStep.Verification, restored.startup.step)
        assertEquals("A living storm", restored.startup.plan.answers.single().value)
    }
}
