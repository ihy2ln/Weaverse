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

/**
 * What the item ledger calls things. A person carries an inventory; a place
 * stores contents; an object is made of parts. The table underneath is the
 * same — only the words, and the body-shaped bits (equipment slots, a
 * backpack), change.
 */
data class InventoryVocabulary(
    val tabLabel: String,
    val addLabel: String,
    val addDialogTitle: String,
    val weightLabel: String,
    val countNoun: String,
    val searchLabel: String,
    val emptyText: String,
    val preparingText: String,
    /** Head/torso/weapon slots only make sense on a body. */
    val showEquipment: Boolean,
    /** So does an equipped backpack and its capacity. */
    val showBackpack: Boolean,
    val filters: List<InventoryFilter>,
) {
    companion object {
        /** A character's own pack — the original inventory, unchanged. */
        val Carried = InventoryVocabulary(
            tabLabel = "Inventory",
            addLabel = "+ Item",
            addDialogTitle = "Add item",
            weightLabel = "WEIGHT CARRIED",
            countNoun = "items",
            searchLabel = "Search items, types, rarities, or tags",
            emptyText = "No items yet. Use + Item to add equipment or supplies.",
            preparingText = "Preparing this pack…",
            showEquipment = true,
            showBackpack = true,
            filters = InventoryFilter.entries,
        )

        /** What a place holds: stock, supplies, and whatever is found there. */
        val Stored = InventoryVocabulary(
            tabLabel = "Contents",
            addLabel = "+ Item",
            addDialogTitle = "Add to contents",
            weightLabel = "WEIGHT STORED",
            countNoun = "items",
            searchLabel = "Search contents, types, or tags",
            emptyText = "Nothing stored here yet. Use + Item for stock, supplies " +
                "or anything kept in this place.",
            preparingText = "Preparing this place's contents…",
            showEquipment = false,
            showBackpack = false,
            filters = listOf(InventoryFilter.All, InventoryFilter.Attunement),
        )

        /** What an object is made of, or holds: pieces, materials, contents. */
        val PartsOf = InventoryVocabulary(
            tabLabel = "Components",
            addLabel = "+ Part",
            addDialogTitle = "Add component",
            weightLabel = "TOTAL WEIGHT",
            countNoun = "parts",
            searchLabel = "Search parts, materials, or tags",
            emptyText = "No components yet. Use + Part for the pieces, materials " +
                "or contents of this object.",
            preparingText = "Preparing this object's components…",
            showEquipment = false,
            showBackpack = false,
            filters = listOf(InventoryFilter.All, InventoryFilter.Attunement),
        )
    }
}
