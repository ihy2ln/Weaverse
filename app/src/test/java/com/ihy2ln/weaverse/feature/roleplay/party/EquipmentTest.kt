package com.ihy2ln.weaverse.feature.roleplay.party

import com.ihy2ln.weaverse.data.db.entities.RpEquipSlot
import com.ihy2ln.weaverse.data.db.entities.RpItem
import com.ihy2ln.weaverse.data.db.entities.decodeEquipment
import com.ihy2ln.weaverse.data.db.entities.decodeItems
import com.ihy2ln.weaverse.data.db.entities.encodeEquipment
import com.ihy2ln.weaverse.data.db.entities.encodeItems
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EquipmentTest {
    @Test
    fun everySlotACharacterPlateShows() {
        assertEquals(
            listOf("Head", "Torso", "Arms", "Legs", "Weapon", "Accessory", "Backpack"),
            RpEquipSlot.entries.map { it.label },
        )
    }

    @Test
    fun equipmentRoundTripsAndDropsClearedSlots() {
        val equipped = mapOf(
            RpEquipSlot.Head.name to "Iron helm",
            RpEquipSlot.Weapon.name to "Short sword",
            // A cleared slot must not persist as an empty string.
            RpEquipSlot.Legs.name to "",
        )
        val restored = decodeEquipment(encodeEquipment(equipped))
        assertEquals("Iron helm", restored[RpEquipSlot.Head.name])
        assertEquals("Short sword", restored[RpEquipSlot.Weapon.name])
        assertTrue(RpEquipSlot.Legs.name !in restored)
    }

    @Test
    fun itemsRoundTripAndBadJsonIsSurvivable() {
        val items = listOf(
            RpItem(id = "i1", name = "Rope", quantity = 2, notes = "50ft", imageMediaId = "media-rope"),
            RpItem(id = "i2", name = "Torch"),
        )
        assertEquals(items, decodeItems(encodeItems(items)))
        // Characters predating inventory store "[]"; malformed data must not crash.
        assertTrue(decodeItems("[]").isEmpty())
        assertTrue(decodeItems("not json").isEmpty())
        assertTrue(decodeEquipment("{}").isEmpty())
        assertTrue(decodeEquipment("nonsense").isEmpty())
    }
}
