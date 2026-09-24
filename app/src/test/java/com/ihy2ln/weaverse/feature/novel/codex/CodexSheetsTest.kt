package com.ihy2ln.weaverse.feature.novel.codex

import com.ihy2ln.weaverse.feature.roleplay.party.InventoryFilter
import com.ihy2ln.weaverse.feature.roleplay.party.InventoryVocabulary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CodexSheetsTest {
    @Test
    fun theSeededCategoriesEachPickATemplate() {
        // The ten categories every book starts with, from BookRepository/DatabaseSeeder.
        val expected = mapOf(
            "Characters" to CodexEntryKind.Character,
            "Locations" to CodexEntryKind.Location,
            "Objects/Items" to CodexEntryKind.Item,
            "Lore" to CodexEntryKind.Lore,
            "Magic/Tech Systems" to CodexEntryKind.Lore,
            "Events/Timeline" to CodexEntryKind.Lore,
            "Factions" to CodexEntryKind.Other,
            "Subplots" to CodexEntryKind.Other,
            "Organizations" to CodexEntryKind.Other,
            "Notes" to CodexEntryKind.Other,
        )
        expected.forEach { (category, kind) ->
            assertEquals(kind, CodexEntryKind.forCategory(category), category)
        }
    }

    @Test
    fun aPinnedKindBeatsWhateverTheCategorySays() {
        val pinned = CodexSheetData(kind = CodexEntryKind.Location.name)
        assertEquals(CodexEntryKind.Location, pinned.kindOr("Notes"))
        assertEquals(CodexEntryKind.Other, CodexSheetData().kindOr("Notes"))
    }

    @Test
    fun preV1345CharacterJsonDecodesToAnEmptySheetInsteadOfThrowing() {
        // v1.3.43 stored a bare RpgCharacterSheet in the same column.
        val legacy = """{"characterClass":"Fighter","level":3,"currentHp":22,"maxHp":30}"""
        val sheet = decodeCodexSheet(legacy)
        assertEquals("", sheet.kind)
        assertEquals(LocationSheet(), sheet.location)
        assertEquals(CodexEntryKind.Location, sheet.kindOr("Locations"))
    }

    @Test
    fun sheetsSurviveARoundTrip() {
        val sheet = CodexSheetData(
            kind = CodexEntryKind.Location.name,
            location = LocationSheet(
                locationType = "Free city",
                population = 41000,
                census = "Two thirds human, a quarter dwarf, the rest counted badly.",
                history = "Founded on the ash of the second siege.",
            ),
        )
        val restored = decodeCodexSheet(encodeCodexSheet(sheet))
        assertEquals(sheet, restored)
        assertEquals(41000, restored.location.population)
    }

    @Test
    fun anItemCountsAsStattedOnceAnyMechanicalFieldIsFilledIn() {
        assertFalse(ItemSheet(description = "A plain iron key.").statsFilledIn)
        assertTrue(ItemSheet(damage = "1d8 slashing").statsFilledIn)
        assertTrue(ItemSheet(hasStats = true).statsFilledIn)
    }

    @Test
    fun oldEntriesOpenWithTheirProseInTheRightField() {
        // Entries written before templates (or filed by the AI sorter) only have plainText.
        val seeded = CodexSheetData().seededFrom(CodexEntryKind.Lore, "The Sundering began at dusk.")
        assertEquals("The Sundering began at dusk.", seeded.lore.explanation)
        // A template that already has a body is left alone.
        val existing = CodexSheetData(location = LocationSheet(description = "Kept."))
        assertEquals("Kept.", existing.seededFrom(CodexEntryKind.Location, "Ignored.").location.description)
    }

    @Test
    fun eachKindFeedsItsOwnBodyTextToTheAiContext() {
        val sheet = CodexSheetData(
            location = LocationSheet(description = "A drowned harbour."),
            item = ItemSheet(description = "A key of black iron."),
            lore = LoreSheet(summary = "The Sundering.", explanation = "It began at dusk."),
            other = OtherSheet(summary = "The Ninefold Court.", description = "Nine judges."),
        )
        assertEquals("A drowned harbour.", sheet.entryTextFor(CodexEntryKind.Location))
        assertEquals("A key of black iron.", sheet.entryTextFor(CodexEntryKind.Item))
        assertEquals("The Sundering.\n\nIt began at dusk.", sheet.entryTextFor(CodexEntryKind.Lore))
        assertEquals("The Ninefold Court.\n\nNine judges.", sheet.entryTextFor(CodexEntryKind.Other))
        // A character's prose lives on the roster sheet, not here.
        assertEquals("", sheet.entryTextFor(CodexEntryKind.Character))
    }

    @Test
    fun onlyAPersonHasAnInventory() {
        // A place stores contents, an object is made of parts, and a legend holds nothing.
        assertEquals("Inventory", CodexEntryKind.Character.ledgerVocabulary()?.tabLabel)
        assertEquals("Contents", CodexEntryKind.Location.ledgerVocabulary()?.tabLabel)
        assertEquals("Components", CodexEntryKind.Item.ledgerVocabulary()?.tabLabel)
        assertNull(CodexEntryKind.Lore.ledgerVocabulary())
        assertNull(CodexEntryKind.Other.ledgerVocabulary())
    }

    @Test
    fun equipmentSlotsAndBackpacksStayOnBodies() {
        listOf(InventoryVocabulary.Stored, InventoryVocabulary.PartsOf).forEach { vocabulary ->
            assertFalse(vocabulary.showEquipment, vocabulary.tabLabel)
            assertFalse(vocabulary.showBackpack, vocabulary.tabLabel)
            // The equipment/backpack filters would filter on something that cannot exist.
            assertFalse(InventoryFilter.Equipment in vocabulary.filters, vocabulary.tabLabel)
            assertFalse(InventoryFilter.Backpack in vocabulary.filters, vocabulary.tabLabel)
        }
        assertTrue(InventoryVocabulary.Carried.showEquipment)
        assertEquals(InventoryFilter.entries, InventoryVocabulary.Carried.filters)
    }
}
