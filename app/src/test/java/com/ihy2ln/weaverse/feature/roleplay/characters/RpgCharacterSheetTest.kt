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
        val encoded = encodeRpgSheet("{\"talkativeness\":0.8}", RpgCharacterSheet(characterClass = "Wizard", level = 3))
        assertTrue(encoded.contains("talkativeness"))
        assertEquals("Wizard", decodeRpgSheet(encoded).characterClass)
        assertEquals(3, decodeRpgSheet(encoded).level)
    }
}
