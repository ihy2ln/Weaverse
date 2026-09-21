package com.ihy2ln.weaverse.feature.chatting

import com.ihy2ln.weaverse.data.db.entities.RpCharacterEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatMentionParsingTest {
    private fun character(id: String, name: String) = RpCharacterEntity(id = id, name = name, createdAt = 0L)

    private val jd = character("char-jd", "JD Marrow")
    private val mira = character("char-mira", "Mira")

    // ------------------------------------------------------------ matchMentionedCharacters

    @Test
    fun matchMentionedCharacters_fullName() {
        val hits = matchMentionedCharacters("hey @JD Marrow what do you think?", listOf(jd, mira))
        assertEquals(listOf(jd), hits)
    }

    @Test
    fun matchMentionedCharacters_firstName() {
        val kellan = character("char-kellan", "Kellan Voss")
        val hits = matchMentionedCharacters("hey @Kellan what do you think?", listOf(kellan, mira))
        assertEquals(listOf(kellan), hits)
    }

    @Test
    fun matchMentionedCharacters_nonMemberCodexName() {
        // A candidate list can include characters materialized from codex entries that
        // aren't yet seated in the room — matching doesn't care about membership.
        val codexOnly = character("char-codex-9", "Wren")
        val hits = matchMentionedCharacters("@Wren, any thoughts?", listOf(jd, codexOnly))
        assertEquals(listOf(codexOnly), hits)
    }

    @Test
    fun matchMentionedCharacters_nameNotInTheWork() {
        val hits = matchMentionedCharacters("@Gandalf show yourself", listOf(jd, mira))
        assertTrue(hits.isEmpty())
    }

    // ------------------------------------------------------------ parseSpeakerLines

    @Test
    fun parseSpeakerLines_singleNarratorBlob() {
        val lines = parseSpeakerLines("Just a plain reply with no named speaker.", listOf(jd, mira))
        assertEquals(1, lines.size)
        assertNull(lines[0].character)
        assertEquals("Just a plain reply with no named speaker.", lines[0].text)
    }

    @Test
    fun parseSpeakerLines_twoSpeakers() {
        val raw = "JD: I think we should go north.\nMira: Agreed, it's safer."
        val lines = parseSpeakerLines(raw, listOf(jd, mira))
        assertEquals(2, lines.size)
        assertEquals(jd, lines[0].character)
        assertEquals("I think we should go north.", lines[0].text)
        assertEquals(mira, lines[1].character)
        assertEquals("Agreed, it's safer.", lines[1].text)
    }

    @Test
    fun parseSpeakerLines_continuationLines() {
        val raw = "JD: I think we should go north.\nIt's colder but safer.\nMira: I'm not so sure."
        val lines = parseSpeakerLines(raw, listOf(jd, mira))
        assertEquals(2, lines.size)
        assertEquals(jd, lines[0].character)
        assertEquals("I think we should go north.\nIt's colder but safer.", lines[0].text)
        assertEquals(mira, lines[1].character)
    }

    @Test
    fun parseSpeakerLines_markdownBoldNames() {
        // Models often wrap the name in markdown emphasis despite being told not to —
        // this must still split into per-speaker rows instead of falling back to one blob.
        val raw = "**JD:** I think we should go north.\n**Mira:** Agreed, it's safer."
        val lines = parseSpeakerLines(raw, listOf(jd, mira))
        assertEquals(2, lines.size)
        assertEquals(jd, lines[0].character)
        assertEquals("I think we should go north.", lines[0].text)
        assertEquals(mira, lines[1].character)
        assertEquals("Agreed, it's safer.", lines[1].text)
    }

    @Test
    fun parseSpeakerLines_unknownName() {
        val raw = "JD: Let's move.\nStranger: Wait for me!"
        val lines = parseSpeakerLines(raw, listOf(jd, mira))
        assertEquals(2, lines.size)
        assertEquals(jd, lines[0].character)
        assertNull(lines[1].character)
        assertEquals("Stranger", lines[1].displayName)
        assertEquals("Wait for me!", lines[1].text)
    }
}
