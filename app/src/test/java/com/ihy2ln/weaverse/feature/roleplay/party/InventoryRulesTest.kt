package com.ihy2ln.weaverse.feature.roleplay.party

import com.ihy2ln.weaverse.data.db.entities.RpEquipSlot
import com.ihy2ln.weaverse.data.db.entities.RpItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InventoryRulesTest {
    private val backpack = RpItem(
        id = "bag",
        name = "Explorer pack",
        template = InventoryItemTemplate.Backpack.label,
        backpackCapacity = 12,
    )
    private val sword = RpItem(
        id = "sword",
        name = "Longsword",
        template = InventoryItemTemplate.Weapon.label,
        slotSize = 2,
        weightLb = 3.0,
        costGp = 15.0,
        tags = "Combat, Martial",
    )
    private val rations = RpItem(
        id = "food",
        name = "Rations",
        quantity = 3,
        slotSize = 1,
        weightLb = 2.0,
        tags = "Consumable, Survival",
    )

    @Test
    fun equippedBackpackControlsCapacity() {
        val items = listOf(backpack, sword, rations)
        assertEquals(0, backpackCapacity(items, emptyMap()))
        assertEquals(
            12,
            backpackCapacity(items, mapOf(RpEquipSlot.Backpack.name to backpack.name)),
        )
    }

    @Test
    fun equippedGearDoesNotConsumeBackpackSpace() {
        val equipment = mapOf(
            RpEquipSlot.Backpack.name to backpack.name,
            RpEquipSlot.Weapon.name to sword.name,
        )
        assertEquals(3, backpackUsedSlots(listOf(backpack, sword, rations), equipment))
        assertFalse(backpackContents(listOf(backpack, sword, rations), equipment).contains(sword))
    }

    @Test
    fun templatesMapToEveryFunctionalEquipmentSlot() {
        RpEquipSlot.entries.forEach { slot ->
            assertEquals(slot, inventoryTemplateFor(slot).equipmentSlot)
        }
        assertTrue(InventoryItemTemplate.PackItem.equipmentSlot == null)
    }

    @Test
    fun weightUsesQuantityAndFiltersSearchAcrossTags() {
        assertEquals(9.0, inventoryWeight(listOf(sword, rations)))
        assertEquals(listOf(sword), filteredInventory(listOf(sword, rations), "martial", InventoryFilter.All))
        assertEquals(listOf(rations), filteredInventory(listOf(sword, rations), "", InventoryFilter.Backpack))
    }

    @Test
    fun attunementFilterOnlyShowsAttunedItems() {
        val ring = RpItem(id = "ring", name = "Ring", attuned = true)
        assertEquals(listOf(ring), filteredInventory(listOf(sword, ring), "", InventoryFilter.Attunement))
    }
}
