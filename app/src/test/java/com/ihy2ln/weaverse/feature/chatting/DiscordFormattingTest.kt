package com.ihy2ln.weaverse.feature.chatting

import com.ihy2ln.weaverse.feature.chatting.social.compactCount
import com.ihy2ln.weaverse.feature.chatting.social.handleFor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiscordFormattingTest {
    @Test
    fun `code fences and quotes split out of prose`() {
        val blocks = parseDiscordBlocks("hey\n```kotlin\nval x = 1\n```\n> quoted line\nafter")
        assertEquals(
            listOf(
                DiscordBlock.Prose("hey"),
                DiscordBlock.Code("val x = 1", "kotlin"),
                DiscordBlock.Quote("quoted line"),
                DiscordBlock.Prose("after"),
            ),
            blocks,
        )
    }

    @Test
    fun `only short emoji-only messages are jumbo`() {
        assertTrue(isJumboEmoji("😂"))
        assertTrue(isJumboEmoji("🔥🔥"))
        assertFalse(isJumboEmoji("lol 😂"))
        assertFalse(isJumboEmoji(""))
    }

    @Test
    fun `writer mentions match persona, you, everyone`() {
        assertTrue(mentionsWriter("hey @Mara look", "Mara"))
        assertTrue(mentionsWriter("@everyone meeting now", "Mara"))
        assertTrue(mentionsWriter("@you there?", "Mara"))
        assertFalse(mentionsWriter("@Youssef hi", "Mara"))
        assertFalse(mentionsWriter("Mara is here", "Mara"))
    }

    @Test
    fun `reactions round-trip and drop zero counts`() {
        val json = encodeReactions(mapOf("👍" to 2, "🔥" to 0))
        assertEquals(mapOf("👍" to 2), decodeReactions(json))
        assertEquals(emptyMap<String, Int>(), decodeReactions("not json"))
    }

    @Test
    fun `social counts and handles`() {
        assertEquals("999", compactCount(999))
        assertEquals("1.2K", compactCount(1_234))
        assertEquals("2K", compactCount(2_000))
        assertEquals("48K", compactCount(48_000))
        assertEquals("2.5M", compactCount(2_500_000))
        assertEquals("elaravance", handleFor("Elara Vance"))
    }

    @Test
    fun `social parser splits quoted-nickname posters and skips unknowns`() {
        val cast = listOf(
            com.ihy2ln.weaverse.data.db.entities.RpCharacterEntity(id = "a", name = "Dr. Elena Valera", createdAt = 0),
            com.ihy2ln.weaverse.data.db.entities.RpCharacterEntity(id = "b", name = "Theresa \"Tessa\" Rivera", createdAt = 0),
        )
        val raw = "Dr. Elena Valera: Coffee is my only friend.\n\nTheresa \"Tessa\" Rivera: Quiet tonight.\nStill quiet.\nNobody: ignored"
        val lines = com.ihy2ln.weaverse.feature.chatting.social.parseSocialLines(raw, cast)
        assertEquals(listOf("a", "b"), lines.map { it.character?.id })
        assertEquals("Quiet tonight.\nStill quiet.\nNobody: ignored", lines[1].text)
    }
}
