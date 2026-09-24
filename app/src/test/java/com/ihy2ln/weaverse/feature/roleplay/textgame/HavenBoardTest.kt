package com.ihy2ln.weaverse.feature.roleplay.textgame

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HavenBoardTest {
    @Test
    fun initialBoardStartsWithShackOnTown() {
        val board = HavenBoardRules.initial()
        assertEquals(1, board.placed.size)
        assertEquals("haven/shack", board.placed.first().cardId)
        assertEquals("town", board.placed.first().board)
    }

    @Test
    fun placeBuildingCardFromHand() {
        var board = HavenBoardRules.initial().copy(hand = listOf("haven/deck_hall"))
        val placed = HavenBoardRules.place(board, "haven/deck_hall", HavenBoardKind.Town, 0.3f, 0.5f)
        assertNotNull(placed)
        assertTrue(placed!!.placed.any { it.cardId == "haven/deck_hall" })
        assertFalse(placed.hand.contains("haven/deck_hall"))
    }

    @Test
    fun stackUpgradeOntoBuilding() {
        var board = HavenBoardRules.initial().copy(
            hand = listOf("haven/upgrade-house-kitchen"),
        )
        val stacked = HavenBoardRules.stack(board, "haven/shack", "haven/upgrade-house-kitchen")
        assertNotNull(stacked)
        assertEquals(listOf("haven/upgrade-house-kitchen"), stacked!!.placed.first().upgradeIds)
    }

    @Test
    fun autoPlaceFromBuildFlags() {
        val board = HavenBoardRules.autoPlaceFromFlags(HavenBoardRules.initial(), listOf("deck_hall_built"))
        assertTrue(board.placed.any { it.cardId == "haven/deck_hall" })
    }

    @Test
    fun grantRewardAddsNextCardToHand() {
        val board = HavenBoardRules.grantReward(HavenBoardRules.initial())
        assertTrue(board.hand.isNotEmpty())
        assertEquals("haven/deck_hall", board.hand.first())
    }

    @Test
    fun tapWorldIsDisconnected() {
        assertFalse(isTapWorldNode("town"))
        assertFalse(isTapWorldNode("farm"))
        assertTrue(isHavenBoardNode("town"))
        assertTrue(isHavenBoardNode("return_farm"))
    }
}
