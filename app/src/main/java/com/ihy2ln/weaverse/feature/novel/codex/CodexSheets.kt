package com.ihy2ln.weaverse.feature.novel.codex

import com.ihy2ln.weaverse.feature.roleplay.party.InventoryVocabulary
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What a codex entry *is*, which decides the template it gets. Characters use
 * the RPG Roster sheet itself; everything else gets a sheet built the same way
 * — framed header, stat strip, expandable sections — but with the fields that
 * kind actually needs.
 */
enum class CodexEntryKind(val label: String, val symbol: String) {
    Character("Character", "☗"),
    Location("Location", "⌂"),
    Item("Object / Item", "▣"),
    Lore("Lore", "✦"),
    Other("Other", "●"),
    ;

    companion object {
        /** The kind a category name implies, before any per-entry override. */
        fun forCategory(categoryName: String): CodexEntryKind {
            val name = categoryName.lowercase()
            fun has(vararg words: String) = words.any { it in name }
            return when {
                has("character", "cast", "npc", "people", "person", "roster") -> Character
                has("location", "place", "setting", "region", "city", "world", "map", "realm") -> Location
                has("object", "item", "gear", "equipment", "artifact", "weapon", "treasure") -> Item
                has("lore", "history", "myth", "legend", "magic", "system", "event", "timeline", "religion") -> Lore
                else -> Other
            }
        }
    }
}

/**
 * The item ledger a kind gets, or null when it holds nothing. A character
 * carries an inventory, a place stores contents, an object is made of parts —
 * one table underneath, named for what it actually holds.
 */
fun CodexEntryKind.ledgerVocabulary(): InventoryVocabulary? = when (this) {
    CodexEntryKind.Character -> InventoryVocabulary.Carried
    CodexEntryKind.Location -> InventoryVocabulary.Stored
    CodexEntryKind.Item -> InventoryVocabulary.PartsOf
    CodexEntryKind.Lore, CodexEntryKind.Other -> null
}

/** A place: what it looks like, what happened here, and who lives in it. */
@Serializable
data class LocationSheet(
    val locationType: String = "",
    val region: String = "",
    val ruler: String = "",
    val scale: String = "",
    val population: Int = 0,
    val description: String = "",
    val appearance: String = "",
    val census: String = "",
    val notableResidents: String = "",
    val factions: String = "",
    val history: String = "",
    val lore: String = "",
    val pointsOfInterest: String = "",
    val services: String = "",
    val defenses: String = "",
    val hooks: String = "",
    val notes: String = "",
)

/** A thing: what it looks like, where it came from, and what it does in play. */
@Serializable
data class ItemSheet(
    val itemType: String = "",
    val rarity: String = "",
    val requiresAttunement: Boolean = false,
    val value: String = "",
    val weight: String = "",
    val quantity: Int = 1,
    val description: String = "",
    val appearance: String = "",
    /** Off by default; the stat block appears once this entry actually has stats. */
    val hasStats: Boolean = false,
    val damage: String = "",
    val properties: String = "",
    val attackBonus: String = "",
    val armorClassBonus: String = "",
    val range: String = "",
    val charges: String = "",
    val saveDc: String = "",
    val effects: String = "",
    val attunementNotes: String = "",
    val origin: String = "",
    val history: String = "",
    val lore: String = "",
    val owner: String = "",
    val pastOwners: String = "",
    val location: String = "",
    val notes: String = "",
) {
    /** True once any mechanical field is filled in, even if the toggle was never touched. */
    val statsFilledIn: Boolean
        get() = hasStats || listOf(
            damage, properties, attackBonus, armorClassBonus, range, charges, saveDc, effects,
        ).any { it.isNotBlank() }
}

/** An idea: the long-form explanation, and everything it hangs off. */
@Serializable
data class LoreSheet(
    val loreType: String = "",
    val era: String = "",
    val summary: String = "",
    val explanation: String = "",
    val details: String = "",
    val origins: String = "",
    val timeline: String = "",
    val significance: String = "",
    val relatedPeople: String = "",
    val relatedPlaces: String = "",
    val relatedThings: String = "",
    val sources: String = "",
    val beliefs: String = "",
    val secrets: String = "",
    val notes: String = "",
)

@Serializable
data class CodexCustomField(
    val label: String = "",
    val value: String = "",
)

/** Everything else: a free sheet with fields the writer names themselves. */
@Serializable
data class OtherSheet(
    val subtype: String = "",
    val summary: String = "",
    val description: String = "",
    val details: String = "",
    val background: String = "",
    val connections: String = "",
    val notes: String = "",
    val customFields: List<CodexCustomField> = emptyList(),
)

/**
 * Everything a non-character codex entry stores, kept in
 * `codex_entries.sheetJson`. A blank [kind] means "whatever the category
 * implies"; setting it pins the template for this one entry.
 */
@Serializable
data class CodexSheetData(
    val kind: String = "",
    val location: LocationSheet = LocationSheet(),
    val item: ItemSheet = ItemSheet(),
    val lore: LoreSheet = LoreSheet(),
    val other: OtherSheet = OtherSheet(),
) {
    /** The template this entry uses, falling back to what its category implies. */
    fun kindOr(categoryName: String): CodexEntryKind =
        CodexEntryKind.entries.firstOrNull { it.name == kind }
            ?: CodexEntryKind.forCategory(categoryName)

    /**
     * The line that becomes the entry's plain text, so mention matching and AI
     * context keep reading the same prose the sheet shows.
     */
    fun entryTextFor(kind: CodexEntryKind): String = when (kind) {
        CodexEntryKind.Location -> location.description
        CodexEntryKind.Item -> item.description
        CodexEntryKind.Lore -> listOf(lore.summary, lore.explanation)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
        CodexEntryKind.Other -> listOf(other.summary, other.description)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
        CodexEntryKind.Character -> ""
    }

    /**
     * Seeds an empty template from the entry's existing text, so entries written
     * before templates existed — or captured out of play by the AI sorter —
     * open with their prose already in the right field instead of blank.
     */
    fun seededFrom(kind: CodexEntryKind, entryText: String): CodexSheetData {
        if (entryText.isBlank() || entryTextFor(kind).isNotBlank()) return this
        return when (kind) {
            CodexEntryKind.Location -> copy(location = location.copy(description = entryText))
            CodexEntryKind.Item -> copy(item = item.copy(description = entryText))
            CodexEntryKind.Lore -> copy(lore = lore.copy(explanation = entryText))
            CodexEntryKind.Other -> copy(other = other.copy(description = entryText))
            CodexEntryKind.Character -> this
        }
    }
}

private val codexSheetCodec = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * Reads the sheet off an entry. Pre-v1.3.45 entries hold a bare
 * `RpgCharacterSheet` here, which has none of these keys and so decodes to an
 * empty sheet — exactly what a non-character entry should start from.
 */
fun decodeCodexSheet(sheetJson: String): CodexSheetData =
    runCatching { codexSheetCodec.decodeFromString(CodexSheetData.serializer(), sheetJson) }
        .getOrDefault(CodexSheetData())

fun encodeCodexSheet(sheet: CodexSheetData): String =
    codexSheetCodec.encodeToString(CodexSheetData.serializer(), sheet)
