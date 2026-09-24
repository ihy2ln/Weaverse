package com.ihy2ln.weaverse.feature.roleplay.chat

import com.ihy2ln.weaverse.ai.AIResult
import com.ihy2ln.weaverse.ai.AiGenerationService
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.dao.RoleplayDao
import com.ihy2ln.weaverse.data.db.entities.RpCharacterEntity
import com.ihy2ln.weaverse.data.db.entities.RpChatEntity
import com.ihy2ln.weaverse.data.db.entities.RpPersonaEntity
import com.ihy2ln.weaverse.data.db.entities.decodeItems
import com.ihy2ln.weaverse.feature.roleplay.characters.decodeRpgSheet
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Proves the Adventure composer hold-menu buttons work end to end: pressing
 * ➕👤 with Seren Vex in Scene 1 files her into the roster with a sheet, and
 * ➕🎒 files her equipment into HER inventory (not the persona's).
 */
class AdventureCaptureTest {
    private val characters = mutableListOf<RpCharacterEntity>()
    private val personas = mutableListOf<RpPersonaEntity>()

    private val dao = mockk<RoleplayDao>(relaxed = true) {
        coEvery { getCharacters() } answers { characters.toList() }
        coEvery { upsertCharacter(any()) } answers {
            val entity = firstArg<RpCharacterEntity>()
            characters.removeAll { it.id == entity.id }
            characters += entity
        }
        coEvery { getPersona(any()) } answers {
            personas.firstOrNull { it.id == firstArg<String>() }
        }
        coEvery { upsertPersona(any()) } answers {
            val entity = firstArg<RpPersonaEntity>()
            personas.removeAll { it.id == entity.id }
            personas += entity
        }
    }
    private val db = mockk<WeaverseDatabase> { every { roleplayDao() } returns dao }
    private val ai = mockk<AiGenerationService> {
        coEvery { hasApiKey() } returns true
    }
    private val capture = AdventureCapture(ai, db)

    private val chat = RpChatEntity(
        id = "chat-adventure-1",
        characterId = null,
        personaId = "persona-you",
        title = "Adventure",
        createdAt = 0L,
        updatedAt = 0L,
    )

    private val scene1Reply = """
        Seren Vex steps out of the tide pools, her obsidian hexblade humming with
        pact-light. The human hexblade warlock (level 3) wears shadow-woven leather
        armor and keeps a vial of voidsalt and 25 gold pieces in her satchel.
    """.trimIndent()

    private fun stubExtraction() {
        coEvery {
            ai.complete(any(), any(), any(), any(), any(), any())
        } returns AIResult(
            text = """
                Here is what I found:
                ```json
                {"characters":[{"name":"Seren Vex","inParty":true,
                "characterClass":"Hexblade Warlock","species":"Human","level":3,
                "currentHp":22,"maxHp":24,"armorClass":15,
                "appearance":"Sea-green eyes, salt-tangled black hair",
                "notes":"Summoned by the storm"}],
                "items":[
                {"name":"Obsidian hexblade","quantity":1,"notes":"Pact weapon","carrier":"Seren Vex"},
                {"name":"Shadow-woven leather armor","quantity":1,"notes":"Worn","carrier":"Seren Vex"},
                {"name":"Vial of voidsalt","quantity":1,"notes":"Reagent","carrier":"Seren Vex"},
                {"name":"Gold pieces","quantity":25,"notes":"","carrier":"Seren Vex"}]}
                ```
            """,
            providerName = "test",
        )
        coEvery { dao.getChat("chat-adventure-1") } returns chat
    }

    @Test
    fun `add-to-roster button files Seren Vex with her sheet`() = runTest {
        stubExtraction()
        val extraction = capture.extract(scene1Reply)
        assertNotNull(extraction)
        val added = capture.applyCharacters(extraction!!.characters)

        assertEquals(listOf("Seren Vex"), added)
        val seren = characters.single { it.name == "Seren Vex" }
        assertTrue(seren.inParty, "Seren should sit in the party roster")
        val sheet = decodeRpgSheet(seren.extensionsJson)
        assertEquals("Hexblade Warlock", sheet.characterClass)
        assertEquals(3, sheet.level)
        assertEquals(22, sheet.currentHp)
        assertEquals(15, sheet.armorClass)
    }

    @Test
    fun `add-to-inventory button routes Seren Vex equipment to her own inventory`() = runTest {
        stubExtraction()
        val extraction = capture.extract(scene1Reply)!!
        capture.applyCharacters(extraction.characters)

        val summary = capture.applyItems(extraction.items, "chat-adventure-1")

        val seren = characters.single { it.name == "Seren Vex" }
        val items = decodeItems(seren.inventoryJson)
        assertEquals(4, items.size, "All four pieces of her equipment: $summary")
        assertEquals(
            listOf("Obsidian hexblade", "Shadow-woven leather armor", "Vial of voidsalt", "Gold pieces"),
            items.map { it.name },
        )
        assertEquals(25, items.first { it.name == "Gold pieces" }.quantity)
        assertTrue(summary.contains("Seren Vex"), "Every item routed to Seren: $summary")
    }

    @Test
    fun `buttons fall back to blank editable entries when nothing is found`() = runTest {
        coEvery { ai.hasApiKey() } returns false
        assertNull(capture.extract(scene1Reply))

        assertNotNull(capture.addBlankCharacter(), "Roster fallback should still create a character")
        val created = characters.single()
        assertEquals("New Character", created.name)
        assertTrue(created.inParty, "Fallback character shows up in the party roster immediately")

        coEvery { dao.getChat("chat-adventure-1") } returns chat
        personas += RpPersonaEntity(id = "persona-you", name = "You")
        assertEquals("You", capture.addBlankItem("chat-adventure-1"))
        assertEquals("New Item", decodeItems(personas.single().inventoryJson).single().name)
    }
}
