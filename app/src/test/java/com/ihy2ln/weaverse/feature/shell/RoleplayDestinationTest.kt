package com.ihy2ln.weaverse.feature.shell

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RoleplayDestinationTest {
    @Test
    fun rpg_isFirstRoleplayWorkspace() {
        assertEquals(RoleplayDestination.Rpg, RoleplayDestination.entries.first())
        assertEquals("RPG", RoleplayDestination.Rpg.label)
        assertTrue(RoleplayDestination.entries.any { it == RoleplayDestination.Chats })
    }
}
