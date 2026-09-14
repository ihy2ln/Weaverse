package com.ihy2ln.weaverse.feature.roleplay.textgame

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TownSimTest {
    @Test
    fun townStartsAsAShackAndOneEmptyLot() {
        val flags = emptyList<String>()
        assertEquals(TownLotStatus.Built, TownRules.status(TownRules.def("house")!!, flags, 1))
        assertEquals(TownLotStatus.Empty, TownRules.status(TownRules.def("deck_hall")!!, flags, 1))
        assertTrue(TownRules.isVisible(TownRules.def("house")!!, flags, 1))
        assertTrue(TownRules.isVisible(TownRules.def("deck_hall")!!, flags, 1))
        assertFalse(TownRules.isVisible(TownRules.def("market")!!, flags, 1))
        assertFalse(TownRules.isVisible(TownRules.def("frosted_mug")!!, flags, 1))
        assertFalse(TownRules.isVisible(TownRules.def("plaza")!!, flags, 1))
        assertEquals("Shack", TownRules.displayName(TownRules.def("house")!!, homeLevel = 1))
    }

    @Test
    fun deckHallStartsEmptyThenPaysOnceBuilt() {
        val flags = emptyList<String>()
        val def = TownRules.def("deck_hall")!!
        assertEquals(TownLotStatus.Empty, TownRules.status(def, flags, townLevel = 1))
        val builtFlags = listOf("deck_hall_built", "town_l2")
        assertEquals(TownLotStatus.Built, TownRules.status(def, builtFlags, townLevel = 2))
        assertTrue(TownRules.isVisible(TownRules.def("market")!!, builtFlags, 2))
        assertEquals(TownLotStatus.Empty, TownRules.status(TownRules.def("market")!!, builtFlags, 2))
        val town = TownRules.sync(TownState(), builtFlags, 2)
        assertTrue(TownRules.canCollect(town, def, builtFlags, 2, battlesWon = 0))
        val (after, result) = TownRules.collect(town, def, builtFlags, 2, battlesWon = 0)!!
        assertEquals("deck_hall", result.buildingId)
        assertTrue(result.coins >= 3)
        assertFalse(TownRules.canCollect(after, def, builtFlags, 2, battlesWon = 0))
        assertTrue(TownRules.canCollect(after, def, builtFlags, 2, battlesWon = 1))
    }

    @Test
    fun guildStaysLockedUntilTownExpands() {
        val def = TownRules.def("guild_hall")!!
        assertEquals(TownLotStatus.Locked, TownRules.status(def, emptyList(), townLevel = 1))
        assertEquals(TownLotStatus.Empty, TownRules.status(def, listOf("deck_hall_built", "town_l2"), townLevel = 2))
        assertEquals(TownLotStatus.Built, TownRules.status(def, listOf("town_l3"), townLevel = 3))
    }

    @Test
    fun emptyPadCostsMatchMafiaBuildPrices() {
        assertEquals(5, TownRules.def("deck_hall")!!.coinCost)
        assertEquals(2, TownRules.def("deck_hall")!!.materialCost)
        assertEquals("5c · 2 ore", TownRules.costLabel(TownRules.def("deck_hall")!!))
        assertFalse(TownRules.canAfford(TownRules.def("deck_hall")!!, coins = 4, materials = 2))
        assertTrue(TownRules.canAfford(TownRules.def("deck_hall")!!, coins = 5, materials = 2))
        assertEquals(StreetSide.Left, TownRules.def("deck_hall")!!.streetSlot.side)
        assertTrue(TownRules.def("house")!!.streetSlot.depth > TownRules.def("forest_gate")!!.streetSlot.depth)
    }

    @Test
    fun farmKitchenIsAnEmptyLotAfterTheFieldIsCleared() {
        val kitchen = FarmBuildings.def("kitchen")!!
        assertEquals(TownLotStatus.Locked, FarmBuildings.status(kitchen, emptyList(), farmLevel = 1, cleared = false))
        assertEquals(TownLotStatus.Empty, FarmBuildings.status(kitchen, emptyList(), farmLevel = 1, cleared = true))
        assertEquals(TownLotStatus.Built, FarmBuildings.status(kitchen, listOf("farm_l2"), farmLevel = 2, cleared = true))
        assertFalse(FarmBuildings.isVisible(FarmBuildings.def("barn")!!, emptyList(), 1, true))
    }

    @Test
    fun shackStartsOnItsOwnPlotAndCanSwapWithDeckHall() {
        val house = TownRules.def("house")!!
        val hall = TownRules.def("deck_hall")!!
        val emptyFlags = emptyList<String>()
        val emptyTown = TownRules.sync(TownState(), emptyFlags, 1)
        assertEquals("p_house", TownRules.plotId(emptyTown, house))
        assertTrue(TownRules.spritePath(house, 1).contains("painterly-cottage"))
        assertEquals("p_w", TownRules.emptyPadPlot(emptyTown, hall, emptyFlags, 1))
        val relocated = TownRules.move(emptyTown, "house", "p_w", emptyFlags, 1)!!
        assertEquals("p_w", TownRules.plotId(relocated, house))
        val pad = TownRules.emptyPadPlot(relocated, hall, emptyFlags, 1)
        assertTrue(pad != "p_w")
        assertNotNull(TownYard.plot(pad))
        val builtFlags = listOf("deck_hall_built")
        val both = TownRules.sync(TownState(), builtFlags, 1)
        assertEquals("p_house", TownRules.plotId(both, house))
        assertEquals("p_w", TownRules.plotId(both, hall))
        val swapped = TownRules.move(both, "house", "p_w", builtFlags, 1)!!
        assertEquals("p_w", TownRules.plotId(swapped, house))
        assertEquals("p_house", TownRules.plotId(swapped, hall))
    }

    @Test
    fun collectingTributeKeepsTheBuildingOnItsMovedPlot() {
        val flags = listOf("deck_hall_built")
        val hall = TownRules.def("deck_hall")!!
        var town = TownRules.sync(TownState(), flags, 1)
        town = TownRules.move(town, "deck_hall", "p_ne", flags, 1)!!
        assertEquals("p_ne", TownRules.plotId(town, hall))
        val (after, result) = TownRules.collect(town, hall, flags, 1, battlesWon = 0)!!
        assertEquals("deck_hall", result.buildingId)
        assertEquals("p_ne", TownRules.plotId(after, hall))
    }
}
