package com.ihy2ln.weaverse.feature.roleplay.party

import com.ihy2ln.weaverse.data.db.entities.RpEquipSlot
import com.ihy2ln.weaverse.data.db.entities.RpItem

enum class InventoryItemTemplate(
    val label: String,
    val equipmentSlot: RpEquipSlot? = null,
    val defaultBackpackCapacity: Int = 0,
) {
    PackItem("Pack item"),
    Head("Head gear", RpEquipSlot.Head),
    Torso("Torso gear", RpEquipSlot.Torso),
    Arms("Arm gear", RpEquipSlot.Arms),
    Legs("Leg gear", RpEquipSlot.Legs),
    Weapon("Weapon", RpEquipSlot.Weapon),
    Accessory("Accessory", RpEquipSlot.Accessory),
    Backpack("Backpack", RpEquipSlot.Backpack, defaultBackpackCapacity = 12),
}

enum class InventoryFilter(val label: String) {
    All("All"),
    Equipment("Equipment"),
    Backpack("Backpack"),
    Attunement("Attunement"),
}

fun inventoryWeight(items: List<RpItem>): Double = items.sumOf { item ->
    item.weightLb.coerceAtLeast(0.0) * item.quantity.coerceAtLeast(1)
}

fun filteredInventory(
    items: List<RpItem>,
    query: String,
    filter: InventoryFilter,
): List<RpItem> {
    val needle = query.trim().lowercase()
    return items.filter { item ->
        val matchesFilter = when (filter) {
            InventoryFilter.All -> true
            InventoryFilter.Equipment -> item.template != InventoryItemTemplate.PackItem.label
            InventoryFilter.Backpack -> item.template == InventoryItemTemplate.Backpack.label ||
                item.template == InventoryItemTemplate.PackItem.label
            InventoryFilter.Attunement -> item.attuned
        }
        val searchable = listOf(item.name, item.template, item.tags, item.notes).joinToString(" ").lowercase()
        matchesFilter && (needle.isBlank() || needle in searchable)
    }
}

fun inventoryTemplateFor(slot: RpEquipSlot): InventoryItemTemplate =
    InventoryItemTemplate.entries.first { it.equipmentSlot == slot }

fun equippedBackpack(items: List<RpItem>, equipment: Map<String, String>): RpItem? {
    val name = equipment[RpEquipSlot.Backpack.name].orEmpty()
    return items.firstOrNull { it.name.equals(name, ignoreCase = true) }
}

fun backpackCapacity(items: List<RpItem>, equipment: Map<String, String>): Int =
    equippedBackpack(items, equipment)?.backpackCapacity?.coerceAtLeast(0) ?: 0

fun backpackContents(items: List<RpItem>, equipment: Map<String, String>): List<RpItem> {
    val equippedNames = equipment.values.map(String::lowercase).toSet()
    return items.filterNot { it.name.lowercase() in equippedNames }
}

fun backpackUsedSlots(items: List<RpItem>, equipment: Map<String, String>): Int =
    backpackContents(items, equipment).sumOf { item ->
        item.quantity.coerceAtLeast(1) * item.slotSize.coerceAtLeast(1)
    }
