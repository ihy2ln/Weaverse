package com.ihy2ln.weaverse.feature.roleplay.chat

import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdventureDiceTest {
    @Test
    fun dndAndPathfinderUseD20() {
        assertEquals("1d20", simulateAdventureRoll("Rules system: D&D 5e", Random(1)).notation)
        assertEquals("1d20", simulateAdventureRoll("Rules system: Pathfinder 2e", Random(2)).notation)
    }

    @Test
    fun routineMovementAndConversationDoNotRoll() {
        assertTrue(!decideAdventureCheck("I walk across the room and open the door.").requiresRoll)
        assertTrue(!decideAdventureCheck("I greet the innkeeper and ask for a room.").requiresRoll)
        assertTrue(!decideAdventureCheck("I pick up my backpack and follow the road.").requiresRoll)
    }

    @Test
    fun combatDefenseAndAbilityChecksActivateRolls() {
        val attack = decideAdventureCheck("I attack the orc with my sword.")
        assertTrue(attack.requiresRoll)
        assertEquals(AdventureAbility.Strength, attack.ability)
        assertTrue(attack.addProficiency)

        assertEquals(
            AdventureAbility.Dexterity,
            decideAdventureCheck("I dodge the falling rocks.").ability,
        )
        assertEquals(
            AdventureAbility.Charisma,
            decideAdventureCheck("I try to persuade the guard.").ability,
        )
    }

    @Test
    fun dmNarrationDoesNotRollUnlessItCallsForPartyActionOrARoll() {
        assertTrue(!decideAdventureCheck("The storm begins and the tavern closes.", true).requiresRoll)
        assertTrue(decideAdventureCheck("The party attacks the owlbear.", true).requiresRoll)
        assertTrue(decideAdventureCheck("Make a Wisdom check for the party.", true).requiresRoll)
    }

    @Test
    fun d20RollAppliesCharacterSheetModifierOnce() {
        val roll = simulateAdventureRoll(
            campaignRules = "Rules system: D&D 5e",
            random = Random(7),
            modifier = 5,
            checkLabel = "Melee attack",
        )
        assertEquals(roll.rawTotal + 5, roll.total)
        assertEquals("1d20+5", roll.notation)
        assertEquals("Melee attack", roll.checkLabel)
        assertTrue("Do not apply it twice" in roll.asHiddenDmInstruction())
        assertTrue("baseline DC of 20" in roll.asHiddenDmInstruction("Very Hard", 20))
    }

    @Test
    fun pbtaUsesTwoD6() {
        val roll = simulateAdventureRoll("Powered by the Apocalypse", Random(3))
        assertEquals("2d6", roll.notation)
        assertTrue(roll.total in 2..12)
    }

    @Test
    fun fateUsesFourFudgeDice() {
        val roll = simulateAdventureRoll("Fate Core", Random(4))
        assertEquals("4dF", roll.notation)
        assertTrue(roll.total in -4..4)
    }

    @Test
    fun outcomeMarkerShowsResultButNotThePrivateRoll() {
        val raw = "[[ACTION_RESULT: Mixed success]] You cross the ledge, but lose your pack."
        assertEquals("Mixed success", adventureOutcomeFrom(raw))
        assertEquals("You cross the ledge, but lose your pack.", adventureProseFrom(raw))
        assertEquals("", adventureOutcomeFrom("[[ACTION_RESULT: No roll]] The door is already open."))
        assertEquals("The door is already open.", adventureProseFrom("[[ACTION_RESULT: No roll]] The door is already open."))
        assertTrue("1d20" !in adventureProseFrom(raw))
    }

    @Test
    fun partialStreamingMarkerDoesNotFlashAsStoryText() {
        assertEquals("", adventureProseFrom("[[ACTION_RES"))
        assertEquals("", adventureProseFrom("[[ACTION_RESULT: Suc"))
    }

    @Test
    fun dungeonMasterModeReversesAiAndUserAuthority() {
        val setup = "Player role: Dungeon Master\nMain character(s): Mira, Bran"
        assertTrue(userIsDungeonMaster(setup))
        val directive = adventureRoleDirective(setup, 4)
        assertTrue("human user is the Dungeon Master" in directive)
        assertTrue("You play the selected player-character party" in directive)
        assertTrue("scene 4" in directive)
    }

    @Test
    fun normalModeKeepsAiAsGameMaster() {
        val directive = adventureRoleDirective("Player role: Adventurer", 2)
        assertTrue(!userIsDungeonMaster("Player role: Adventurer"))
        assertTrue("You are the AI game master" in directive)
        assertTrue("scene 2" in directive)
    }
}
