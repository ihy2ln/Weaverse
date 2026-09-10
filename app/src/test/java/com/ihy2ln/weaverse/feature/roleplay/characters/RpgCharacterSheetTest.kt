package com.ihy2ln.weaverse.feature.roleplay.characters

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RpgCharacterSheetTest {
    @Test fun `ability modifiers round negative values down`() {
        assertEquals(-1, abilityModifier(9))
        assertEquals(-2, abilityModifier(7))
        assertEquals("+3", formatModifier(16))
    }

    @Test fun `hit points stay within maximum`() {
        val sheet = RpgCharacterSheet(currentHp = 5, maxHp = 10)
        assertEquals(10, sheet.withCurrentHp(99).currentHp)
        assertEquals(0, sheet.withCurrentHp(-2).currentHp)
    }

    @Test fun `sheet persistence preserves imported extension fields`() {
        val encoded = encodeRpgSheet(
            "{\"talkativeness\":0.8}",
            RpgCharacterSheet(characterClass = "Wizard", species = "Elf", subclass = "Evoker", level = 3),
        )
        assertTrue(encoded.contains("talkativeness"))
        assertEquals("Wizard", decodeRpgSheet(encoded).characterClass)
        assertEquals(3, decodeRpgSheet(encoded).level)
        assertEquals("Elf", decodeRpgSheet(encoded).species)
        assertEquals("Evoker", decodeRpgSheet(encoded).subclass)
    }

    @Test fun `new sheets contain playable d20 and tactical card statistics`() {
        val sheet = createRpgCharacterSheet(
            name = "Aria",
            characterClass = "Wizard",
            species = "Elf",
            level = 3,
        )

        assertEquals("Arcanist", sheet.tacticalRole)
        assertTrue(sheet.maxHp > 0)
        assertEquals(sheet.maxHp, sheet.currentHp)
        assertTrue(sheet.armorClass > 0)
        assertTrue(sheet.proficiencyBonus > 0)
        assertTrue(sheet.attacksAndActions.isNotBlank())
        assertTrue(sheet.savingThrows.isNotBlank())
        assertTrue(sheet.tacticalAttack > 0)
        assertTrue(sheet.tacticalActionPoints > 0)
        assertTrue(sheet.tacticalEnergyPoints > 0)
        assertTrue(sheet.tacticalSignatureEffect.isNotBlank())
    }

    @Test fun `missing sheet data receives a populated backward compatible sheet`() {
        val sheet = decodeRpgSheet("{\"talkativeness\":0.8}")
        assertEquals("Adventurer", sheet.characterClass)
        assertTrue(sheet.maxHp > 0)
        assertTrue(sheet.skillsAndProficiencies.isNotBlank())
        assertTrue(sheet.tacticalSignature.isNotBlank())
    }
}
