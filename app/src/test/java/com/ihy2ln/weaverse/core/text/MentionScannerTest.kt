package com.ihy2ln.weaverse.core.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MentionScannerTest {
    @Test
    fun `finds a name mention`() {
        val mentions = MentionScanner.findMentions(
            text = "John Zhao walked into the room.",
            candidates = listOf(MentionCandidate(entryId = "1", name = "John Zhao")),
        )
        assertEquals(1, mentions.size)
        assertEquals("1", mentions.single().entryId)
        assertEquals(0..8, mentions.single().range)
    }

    @Test
    fun `finds an alias mention`() {
        val mentions = MentionScanner.findMentions(
            text = "Zhao nodded.",
            candidates = listOf(MentionCandidate(entryId = "1", name = "John Zhao", aliases = listOf("Zhao"))),
        )
        assertEquals(listOf("1"), mentions.map { it.entryId })
    }

    @Test
    fun `untracked entries are skipped`() {
        val mentions = MentionScanner.findMentions(
            text = "Grace walked into the room.",
            candidates = listOf(MentionCandidate(entryId = "1", name = "Grace", tracked = false)),
        )
        assertTrue(mentions.isEmpty())
    }

    @Test
    fun `overlapping candidates resolve to the longest match`() {
        val mentions = MentionScanner.findMentions(
            text = "John Zhao walked in.",
            candidates = listOf(
                MentionCandidate(entryId = "long", name = "John Zhao"),
                MentionCandidate(entryId = "short", name = "John"),
            ),
        )
        assertEquals(listOf("long"), mentions.map { it.entryId })
    }

    @Test
    fun `finds multiple non-overlapping mentions in order`() {
        val mentions = MentionScanner.findMentions(
            text = "Zhao saw Mara across the hall.",
            candidates = listOf(
                MentionCandidate(entryId = "zhao", name = "Zhao"),
                MentionCandidate(entryId = "mara", name = "Mara"),
            ),
        )
        assertEquals(listOf("zhao", "mara"), mentions.map { it.entryId })
    }
}
