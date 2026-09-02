package com.ihy2ln.weaverse.feature.roleplay.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdventureWorldUpdatesTest {
    @Test
    fun parsesAndHidesRosterAndLoreMarkers() {
        val raw = "[[SCENE_SYNOPSIS: Mira discovered the Moonwell beneath the ruins.]]" +
            "[[ROSTER_CHARACTER|name=Mira|species=Elf|class=Ranger|level=2|dexterity=16|role=Team|description=Scout|portrait=Green cloak]]" +
            "[[LORE_UPDATE|category=Locations|name=Moonwell|summary=An old well beneath the ruins]]" +
            "Mira points toward the ruined well."
        val updates = adventureWorldUpdatesFrom(raw)
        assertEquals("Mira", updates.characters.single().name)
        assertEquals(16, updates.characters.single().dexterity)
        assertEquals("Moonwell", updates.lore.single().name)
        assertEquals("Mira discovered the Moonwell beneath the ruins.", updates.sceneSynopsis)
        assertEquals("Mira points toward the ruined well.", updates.prose)
        assertFalse("[[" in updates.prose)
    }

    @Test
    fun partialPrivateMarkerNeverFlashesDuringStreaming() {
        assertEquals("", adventureWorldProseFrom("[[ROSTER_CHARACTER|name=Mi"))
        assertTrue("first created or first met" in adventureWorldUpdateDirective())
    }
}
