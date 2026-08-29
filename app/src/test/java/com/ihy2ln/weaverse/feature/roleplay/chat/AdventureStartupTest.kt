package com.ihy2ln.weaverse.feature.roleplay.chat

import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdventureStartupTest {
    @Test
    fun initialDmPromptOffersAllThreeStartupPaths() {
        val stored = adventureStartupPrompt(userIsDungeonMaster = false)
        assertEquals(AdventureStartupPhase.Choose, adventureStartupPhase(stored))
        val visible = adventureStartupProseFrom(stored)
        assertTrue("1 · Classic D&D opening" in visible)
        assertTrue("2 · Build it together" in visible)
        assertTrue("3 · Random start" in visible)
        assertTrue("AI Dungeon Master" in visible)
        assertFalse("[[ADVENTURE_STARTUP" in visible)
    }

    @Test
    fun interviewRemainsInSetupUntilAnswersAreSubmitted() {
        assertEquals(AdventureStartupChoice.Interview, adventureStartupChoice("2"))
        assertEquals(
            AdventureStartupPhase.Questions,
            nextAdventureStartupPhase(AdventureStartupPhase.Choose, "2"),
        )
        assertEquals(
            AdventureStartupPhase.Complete,
            nextAdventureStartupPhase(AdventureStartupPhase.Questions, "At sunset in Waterdeep"),
        )
        val directive = adventureStartupDirective(AdventureStartupPhase.Choose, "2", Random(1))
        listOf("where", "when", "who", "what is happening", "main goal").forEach {
            assertTrue(it in directive)
        }
        assertTrue("Do not begin the adventure yet" in directive)
    }

    @Test
    fun classicAndRandomBothMakeAiDmFrameTheQuest() {
        val classic = adventureStartupDirective(AdventureStartupPhase.Choose, "1", Random(2))
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
