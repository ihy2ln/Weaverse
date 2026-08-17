package com.ihy2ln.weaverse.feature.roleplay.characters

import com.ihy2ln.weaverse.core.media.PngChunkIO
import com.ihy2ln.weaverse.data.db.entity.RpCharacterEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CharacterCardCodecTest {
    private fun minimalPng(): ByteArray =
        PngChunkIO.writeChunks(listOf(PngChunkIO.Chunk("IHDR", ByteArray(13)), PngChunkIO.Chunk("IEND", ByteArray(0))))

    private val character = RpCharacterEntity(
        name = "Mara Voss",
        description = "A weary starship engineer.",
        personality = "Blunt, resourceful, secretly sentimental.",
        scenario = "Aboard the drifting freighter Halcyon.",
        firstMes = "You good? You look like you saw a ghost.",
        mesExample = "<START>\n{{user}}: Status report.\n{{char}}: Could be worse. Could be on fire.",
        creatorNotes = "Works best with a sarcastic user persona.",
        systemPrompt = "Stay in character as Mara at all times.",
        postHistoryInstructions = "Keep replies under 150 words.",
        alternateGreetings = listOf("Oh, it's you.", "Don't touch that lever."),
        tags = listOf("sci-fi", "engineer"),
        characterVersion = "1.2",
        extensionsJson = """{"talkativeness":0.6}""",
    )

    @Test
    fun `decoding an encoded card recovers every field`() {
        val png = CharacterCardCodec.encode(character, minimalPng())
        val decoded = CharacterCardCodec.decode(png)

        assertEquals(character.name, decoded?.name)
        assertEquals(character.description, decoded?.description)
        assertEquals(character.personality, decoded?.personality)
        assertEquals(character.scenario, decoded?.scenario)
        assertEquals(character.firstMes, decoded?.firstMes)
        assertEquals(character.mesExample, decoded?.mesExample)
        assertEquals(character.creatorNotes, decoded?.creatorNotes)
        assertEquals(character.systemPrompt, decoded?.systemPrompt)
        assertEquals(character.postHistoryInstructions, decoded?.postHistoryInstructions)
        assertEquals(character.alternateGreetings, decoded?.alternateGreetings)
        assertEquals(character.tags, decoded?.tags)
        assertEquals(character.characterVersion, decoded?.characterVersion)
        assertEquals(character.extensionsJson, decoded?.extensionsJson)
    }

    @Test
    fun `re-encoding a card with an existing chara chunk replaces it instead of duplicating`() {
        val once = CharacterCardCodec.encode(character, minimalPng())
        val renamed = character.copy(name = "Renamed")
        val twice = CharacterCardCodec.encode(renamed, once)

        val textChunks = PngChunkIO.readChunks(twice).count { it.type == "tEXt" }
        assertEquals(1, textChunks)
        assertEquals("Renamed", CharacterCardCodec.decode(twice)?.name)
    }

    @Test
    fun `decoding a PNG with no chara chunk returns null`() {
        assertNull(CharacterCardCodec.decode(minimalPng()))
    }

    @Test
    fun `decode preserves the caller-supplied id and avatar rather than minting a new one`() {
        val png = CharacterCardCodec.encode(character, minimalPng())
        val decoded = CharacterCardCodec.decode(png, existingId = "keep-me", avatarMediaId = "avatar-1")

        assertEquals("keep-me", decoded?.id)
        assertEquals("avatar-1", decoded?.avatarMediaId)
    }
}
