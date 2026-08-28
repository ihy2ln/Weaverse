package com.ihy2ln.weaverse.feature.roleplay.town

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TownMapTest {
    @Test
    fun theTownHasTheExpectedBuildings() {
        val kinds = TownMap.locations.map { it.kind }
        assertTrue(TownLocationKind.Commerce in kinds, "no shop")
        assertTrue(TownLocationKind.Blacksmith in kinds, "no blacksmith")
        assertTrue(TownLocationKind.Chief in kinds, "no chief's house")
    }

    @Test
    fun everyBuildingSitsOnTheStripAndHasAUniqueId() {
        val ids = TownMap.locations.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicate location id")
        TownMap.locations.forEach {
            assertTrue(it.xPercent in 0f..100f, "${it.id} is off the map")
            assertTrue(it.name.isNotBlank(), "${it.id} has no name")
            assertTrue(it.actions.isNotEmpty(), "${it.id} has nothing to do")
        }
    }

    @Test
    fun buildingsDoNotOverlapEachOthersDoorways() {
        // Two doors closer than the reach radius would make "nearest" ambiguous.
        val sorted = TownMap.locations.map { it.xPercent }.sorted()
        sorted.zipWithNext().forEach { (a, b) ->
            assertTrue(b - a > TownMap.REACH_PERCENT, "doors at $a and $b are too close")
        }
    }

    @Test
    fun youCanOnlyEnterWhenStandingAtADoor() {
        val store = TownMap.locations.first { it.id == "store" }
        assertEquals(store.id, TownMap.nearest(store.xPercent)?.id)
        assertEquals(store.id, TownMap.nearest(store.xPercent + TownMap.REACH_PERCENT - 0.1f)?.id)
        assertNull(TownMap.nearest(store.xPercent + TownMap.REACH_PERCENT + 1f))
    }

    @Test
    fun shopsSellSomethingAndTheChiefSellsNothing() {
        listOf(TownLocationKind.Commerce, TownLocationKind.Blacksmith).forEach {
            assertTrue(TownMap.goodsFor(it).isNotEmpty(), "$it has no stock")
        }
        assertTrue(TownMap.goodsFor(TownLocationKind.Chief).isEmpty())
        // Every kind must be handled, so no building opens to an empty sheet by accident.
        TownLocationKind.entries.forEach { kind ->
            assertNotNull(TownMap.goodsFor(kind))
            TownMap.goodsFor(kind).forEach { good -> assertTrue(good.priceGold > 0) }
        }
    }

    @Test
    fun shopQuantityFallbacksAreUsefulAndBounded() {
        assertEquals(7, suggestedShopQuantity("Rations", 0))
        assertEquals(2, suggestedShopQuantity("Feed sack", 20))
        assertEquals(1, suggestedShopQuantity("Short sword", 0))
        assertTrue(shopGoodKey("store", "Rope").startsWith("store:"))
    }
}
