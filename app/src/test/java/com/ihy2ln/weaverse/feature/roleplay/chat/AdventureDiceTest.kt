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
}
