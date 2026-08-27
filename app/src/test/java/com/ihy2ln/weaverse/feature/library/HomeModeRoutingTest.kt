package com.ihy2ln.weaverse.feature.library

import com.ihy2ln.weaverse.data.db.entities.RpChatEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HomeModeRoutingTest {
    @Test
    fun homeModesMapToRoleplayCanvases() {
        assertEquals("dungeonMaster", HomeModeRouting.displayModeForHome("Roleplay"))
        assertEquals("messenger", HomeModeRouting.displayModeForHome("Chatting"))
        assertEquals("roleplay", HomeModeRouting.displayModeForHome("Storyboard"))
        assertNull(HomeModeRouting.displayModeForHome("Novel"))
        assertNull(HomeModeRouting.displayModeForHome("Notes"))
    }

    @Test
    fun latestChatPrefersMatchingDisplayModeThenFallsBack() {
        val messenger = chat("m", "messenger", updatedAt = 10)
        val manga = chat("s", "roleplay", updatedAt = 5)
        val dm = chat("d", "dungeonMaster", updatedAt = 8)
        val chats = listOf(messenger, manga, dm)

        assertEquals("s", HomeModeRouting.latestChatForHomeMode("Storyboard", chats)?.id)
        assertEquals("m", HomeModeRouting.latestChatForHomeMode("Chatting", chats)?.id)
        assertEquals("d", HomeModeRouting.latestChatForHomeMode("Roleplay", chats)?.id)
        assertEquals("m", HomeModeRouting.latestChatForHomeMode("Storyboard", listOf(messenger))?.id)
        assertNull(HomeModeRouting.latestChatForHomeMode("Storyboard", emptyList()))
    }

    @Test
    fun normalizeUnknownDisplayModeToMessenger() {
        assertEquals("messenger", HomeModeRouting.normalizeDisplayMode(""))
        assertEquals("roleplay", HomeModeRouting.normalizeDisplayMode("roleplay"))
    }

    private fun chat(id: String, displayMode: String, updatedAt: Long) = RpChatEntity(
        id = id,
        characterId = null,
        personaId = "persona-1",
        title = id,
        displayMode = displayMode,
        createdAt = 0L,
        updatedAt = updatedAt,
    )
}
