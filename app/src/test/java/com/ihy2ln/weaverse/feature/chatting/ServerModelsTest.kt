package com.ihy2ln.weaverse.feature.chatting

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServerModelsTest {
    @Test
    fun serverJsonRoundTrips() {
        val roles = listOf(ServerRole("r1", "Guild Master", "#F1C40F"), ServerRole("r2", "Scout", "#2ECC71", hoist = false))
        assertEquals(roles, ServerJson.roles(ServerJson.roles(roles)))
        assertEquals(listOf("a", "b"), ServerJson.ids(ServerJson.ids(listOf("a", "b", "a"))))
        val map = mapOf("char-1" to listOf("r1"), "char-2" to emptyList())
        assertEquals(mapOf("char-1" to listOf("r1")), ServerJson.memberRoles(ServerJson.memberRoles(map)))
    }

    @Test
    fun brokenJsonFallsBackToEmpty() {
        assertEquals(emptyList<String>(), ServerJson.ids("not json"))
        assertEquals(emptyList<ServerRole>(), ServerJson.roles(""))
        assertEquals(emptyMap<String, List<String>>(), ServerJson.memberRoles("{"))
    }

    @Test
    fun everyonePings() {
        assertTrue(pingsEveryone("@everyone hey"))
        assertTrue(pingsEveryone("ok @here what's up"))
        assertTrue(pingsEveryone("@Everyone!"))
        assertFalse(pingsEveryone("email me@everyone.com"))
        assertFalse(pingsEveryone("@Lyra hi"))
    }

    @Test
    fun highestRoleColorsTheName() {
        val state = DiscordChatUiState(
            roles = listOf(ServerRole("top", "Leader", "#E74C3C"), ServerRole("low", "Member", "#3498DB")),
            memberRoles = mapOf("char-1" to listOf("low", "top"), "char-2" to listOf("low")),
        )
        assertEquals("#E74C3C", state.roleColors["char-1"])
        assertEquals("#3498DB", state.roleColors["char-2"])
    }
}
