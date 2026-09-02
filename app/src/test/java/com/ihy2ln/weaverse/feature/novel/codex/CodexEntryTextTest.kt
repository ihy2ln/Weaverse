package com.ihy2ln.weaverse.feature.novel.codex

import com.ihy2ln.weaverse.feature.roleplay.characters.RpgCharacterSheet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CodexEntryTextTest {
    private val place = CodexSheetData(
        location = LocationSheet(
            locationType = "Free city",
            scale = "City",
            population = 41000,
            ruler = "The Harbour Court",
            description = "A drowned harbour that refused to die.",
            census = "Two thirds human, a quarter dwarf.",
            history = "Founded on the ash of the second siege.",
        ),
    )

    @Test
    fun everyFilledFieldReachesTheProse() {
        val text = CodexEntryText.render(CodexEntryKind.Location, "Blackreach", place)
        listOf(
            "Blackreach",
            "Free city",
            "population 41,000",
            "A drowned harbour that refused to die.",
            "Census",
            "Two thirds human, a quarter dwarf.",
            "History",
            "Founded on the ash of the second siege.",
            "The Harbour Court",
        ).forEach { assertTrue(it in text, "missing \"$it\" in:\n$text") }
    }

    @Test
    fun blankFieldsLeaveNoEmptyHeadings() {
        val text = CodexEntryText.render(CodexEntryKind.Location, "Blackreach", place)
        // Nothing was written for these, so their headings must not appear at all.
        assertFalse("Defenses" in text, text)
        assertFalse("Hooks & secrets" in text, text)
        assertFalse("\n\n\n" in text, "blank field left a hole:\n$text")
    }

    @Test
    fun anObjectWithoutStatsPrintsNoStatLine() {
        val mundane = CodexSheetData(item = ItemSheet(description = "A plain iron key."))
        assertFalse("Stats" in CodexEntryText.render(CodexEntryKind.Item, "Key", mundane))
        val magic = CodexSheetData(
            item = ItemSheet(description = "It hums.", damage = "1d8 slashing", saveDc = "15"),
        )
        val text = CodexEntryText.render(CodexEntryKind.Item, "Hum", magic)
        assertTrue("Damage 1d8 slashing" in text, text)
        assertTrue("Save DC 15" in text, text)
    }

    @Test
    fun aCharacterPrintsItsSheetNumbers() {
        val text = CodexEntryText.renderCharacter(
            name = "Ilse Vance",
            sheet = RpgCharacterSheet(
                characterClass = "Ranger",
                level = 3,
                species = "Human",
                currentHp = 22,
                maxHp = 28,
                armorClass = 15,
                appearance = "Salt-bleached coat.",
            ),
            description = "A caravan guard with a debt.",
            personality = "Quiet until she is not.",
            gear = listOf("Longbow", "Rope, 50ft"),
        )
        assertTrue("Ranger · level 3 · Human · 22/28 HP · AC 15" in text, text)
        assertTrue("Salt-bleached coat." in text, text)
        assertTrue("Longbow, Rope, 50ft" in text, text)
    }

    @Test
    fun theTextAndTheEntryAreTheSameWords() {
        // The one-to-one guarantee: the entry stores what the writer was handed,
        // because the prose is rendered from the entry rather than written twice.
        val rendered = CodexEntryText.render(CodexEntryKind.Lore, "The Sundering", CodexSheetData(
            lore = LoreSheet(summary = "The night the moon split.", explanation = "It began at dusk."),
        ))
        val reloaded = decodeCodexSheet(
            encodeCodexSheet(
                CodexSheetData(
                    kind = CodexEntryKind.Lore.name,
                    lore = LoreSheet(summary = "The night the moon split.", explanation = "It began at dusk."),
                ),
            ),
        )
        assertEquals(rendered, CodexEntryText.render(CodexEntryKind.Lore, "The Sundering", reloaded))
    }
}
