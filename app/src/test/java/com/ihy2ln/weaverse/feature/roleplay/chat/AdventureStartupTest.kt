package com.ihy2ln.weaverse.feature.roleplay.chat

import kotlin.random.Random
import com.ihy2ln.weaverse.core.ui.components.CampaignSettingDetailTemplates
import com.ihy2ln.weaverse.core.ui.components.CampaignSettingTemplates
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdventureStartupTest {
    @Test
    fun noSelectedCharacterStartsGuidedSheetCreation() {
        val stored = adventureStartupPrompt(userIsDungeonMaster = false, needsCharacter = true)
        assertEquals(AdventureStartupPhase.Character, adventureStartupPhase(stored))
        val visible = adventureStartupProseFrom(stored)
        assertTrue("Standard Array" in visible)
        assertTrue("complete editable roster sheet" in visible)
        assertEquals(
            AdventureStartupPhase.Choose,
            nextAdventureStartupPhase(AdventureStartupPhase.Character, "surprise me"),
        )
        assertTrue("[[ROSTER_CHARACTER" in adventureStartupDirective(AdventureStartupPhase.Character, "surprise me"))
    }

    @Test
    fun initialDmPromptOffersAllThreeStartupPaths() {
        val stored = adventureStartupPrompt(userIsDungeonMaster = false)
        assertEquals(AdventureStartupPhase.Choose, adventureStartupPhase(stored))
        val visible = adventureStartupProseFrom(stored)
        assertTrue("1 · AI Startup" in visible)
        assertTrue("2 · Character Selector" in visible)
        assertTrue("3 · Quick random start" in visible)
        assertTrue("AI Dungeon Master" in visible)
        assertFalse("[[ADVENTURE_STARTUP" in visible)
    }

    @Test
    fun aiStartupRemainsInSetupUntilAnswersAreSubmitted() {
        assertEquals(AdventureStartupChoice.Ai, adventureStartupChoice("1"))
        assertEquals(
            AdventureStartupPhase.Questions,
            nextAdventureStartupPhase(AdventureStartupPhase.Choose, "1"),
        )
        assertEquals(
            AdventureStartupPhase.Complete,
            nextAdventureStartupPhase(AdventureStartupPhase.Questions, "At sunset in Waterdeep"),
        )
        val directive = adventureStartupDirective(AdventureStartupPhase.Choose, "1", Random(1))
        listOf("Character backstory", "Current situation", "Future goals").forEach {
            assertTrue(it in directive)
        }
        assertTrue("Do not roll dice during setup" in directive)
    }

    @Test
    fun classicAndRandomBothMakeAiDmFrameTheQuest() {
        val classic = adventureStartupDirective(AdventureStartupPhase.Choose, "classic", Random(2))
        val random = adventureStartupDirective(AdventureStartupPhase.Choose, "3", Random(2))
        assertTrue("AI DM—not the player—must begin the quest chain" in classic)
        assertTrue("AI DM—not the player—must begin the quest chain" in random)
        assertTrue("Random opening selected" in random)
        assertEquals(
            AdventureStartupPhase.Complete,
            nextAdventureStartupPhase(AdventureStartupPhase.Choose, "3"),
        )
    }

    @Test
    fun curatedStartsAreOneTapCommandsThatUseCampaignContext() {
        val presets = adventureStartupPresets()
        assertTrue(presets.size >= 5)
        assertTrue(presets.all { it.title.isNotBlank() && it.description.isNotBlank() })
        assertTrue(presets.any { it.id == "isekai-arrival" })
        val selected = presets.first()
        assertEquals(selected, adventureStartupPreset(selected.command))
        assertEquals(AdventureStartupChoice.Curated, adventureStartupChoice(selected.command))
        assertEquals(
            AdventureStartupPhase.CuratedQuestions,
            nextAdventureStartupPhase(AdventureStartupPhase.Choose, selected.command),
        )
        val directive = adventureStartupDirective(AdventureStartupPhase.Choose, selected.command, Random(4))
        assertTrue("saved campaign setting details" in directive)
        assertTrue("ask for two short setup details" in directive)
        assertTrue("Do not begin the adventure yet" in directive)
        assertEquals(
            AdventureStartupPhase.Complete,
            nextAdventureStartupPhase(AdventureStartupPhase.CuratedQuestions, "randomize"),
        )
        val opening = adventureStartupDirective(AdventureStartupPhase.CuratedQuestions, "randomize", Random(4))
        assertTrue("AI DM—not the player—must begin the quest chain" in opening)
        assertTrue("invent fitting details" in opening)
    }

    @Test
    fun settingDetailsCatalogOffersMultipleCuratedWorlds() {
        assertTrue(CampaignSettingDetailTemplates.size >= 12)
        assertTrue(CampaignSettingDetailTemplates.any { it.id == "coastal" })
        assertTrue(CampaignSettingDetailTemplates.any { it.id == "fae" })
        assertTrue(CampaignSettingDetailTemplates.any { it.id == "clockwork" })
        assertTrue(CampaignSettingDetailTemplates.any { it.id == "adult-plot" && "18+" in it.label })
        assertTrue(CampaignSettingDetailTemplates.any { it.id == "adult-smut" && "consent" in it.details.lowercase() })
        assertTrue(CampaignSettingDetailTemplates.any { it.id == "adult-hentai" && "adult" in it.details.lowercase() })
        assertTrue(CampaignSettingDetailTemplates.any { it.id == "adult-ecchi" && "no minors" in it.details.lowercase() })
        assertTrue(CampaignSettingDetailTemplates.any { it.id == "custom" })
        assertTrue(CampaignSettingTemplates.any { it.id == "highschool-of-the-dead" })
        assertTrue(CampaignSettingTemplates.any { it.id == "walking-dead" })
        assertTrue(CampaignSettingTemplates.any { it.id == "world-war-z" })
    }

    @Test
    fun detectsThePreviousPassiveOpeningForMigration() {
        assertTrue(
            isLegacyPassiveAdventureOpening(
                "Mira and Bran stand at the threshold of the first scene. " +
                    "Describe what they do in the action box below.",
            ),
        )
        assertFalse(isLegacyPassiveAdventureOpening("The party attacks the gate."))
    }
}
