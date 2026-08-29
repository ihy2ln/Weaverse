package com.ihy2ln.weaverse.feature.roleplay.chat

import com.ihy2ln.weaverse.ai.AiGenerationService
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.RpCharacterEntity
import com.ihy2ln.weaverse.data.db.entities.RpItem
import com.ihy2ln.weaverse.data.db.entities.decodeItems
import com.ihy2ln.weaverse.data.db.entities.encodeItems
import com.ihy2ln.weaverse.feature.roleplay.characters.RpgCharacterSheet
import com.ihy2ln.weaverse.feature.roleplay.characters.decodeRpgSheet
import com.ihy2ln.weaverse.feature.roleplay.characters.encodeRpgSheet
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Scene bookkeeping for the RPG adventure. A cheap secondary model call pulls
 * character and item facts out of the prose and files them: characters merge
 * into the roster (creating sheets for new ones), items route to the carrier's
 * inventory — "party" items land in the persona's (or a party member's) pack.
 * The ➕👤 / ➕🎒 composer buttons run the same extraction on demand and fall
 * back to blank editable entries when nothing is found.
 */
@Singleton
class AdventureCapture @Inject constructor(
    private val aiGeneration: AiGenerationService,
    private val db: WeaverseDatabase,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Serializable
    data class ExtractedCharacter(
        val name: String = "",
        val inParty: Boolean = false,
        val characterClass: String = "",
        val species: String = "",
        val level: Int = 0,
        val currentHp: Int = 0,
        val maxHp: Int = 0,
        val armorClass: Int = 0,
        val appearance: String = "",
        val notes: String = "",
    )

    @Serializable
    data class ExtractedItem(
        val name: String = "",
        val quantity: Int = 1,
        val notes: String = "",
        val carrier: String = "",
    )

    @Serializable
    data class Extraction(
        val characters: List<ExtractedCharacter> = emptyList(),
        val items: List<ExtractedItem> = emptyList(),
    )

    /** Extract + apply in one step; never throws into the chat flow. */
    suspend fun captureAndApply(replyText: String, chatId: String) {
        runCatching {
            val extraction = extract(replyText) ?: return
            apply(extraction, chatId)
        }
    }

    /** Runs the secondary extraction call; null when there is nothing usable. */
    suspend fun extract(text: String): Extraction? {
        if (!aiGeneration.hasApiKey()) return null
        val scene = text.trim().take(6000)
        if (scene.isBlank()) return null
        val instruction = buildString {
            append("You are the campaign bookkeeper. From the scene text below, list every ")
            append("named character with concrete details and every item that is gained, ")
            append("lost, carried, or used. Reply with ONLY a JSON object, no prose:\n")
            append("{\"characters\":[{\"name\":\"\",\"inParty\":false,\"characterClass\":\"\",")
            append("\"species\":\"\",\"level\":0,\"currentHp\":0,\"maxHp\":0,\"armorClass\":0,")
            append("\"appearance\":\"\",\"notes\":\"\"}],")
            append("\"items\":[{\"name\":\"\",\"quantity\":1,\"notes\":\"\",\"carrier\":\"\"}]}\n")
            append("Omit fields you do not know. Use empty lists when nothing applies. ")
            append("Mark party members (the player's team) with inParty=true. For items, ")
            append("set carrier to the character who carries it, or \"party\" if shared.\n\n")
            append("Scene:\n<scene>\n$scene\n</scene>")
        }
        val result = aiGeneration.complete(
            userMessage = instruction,
            maxTokens = 800,
            temperature = 0.2,
        )
        return parse(result.text)
    }

    /** Models wrap JSON in prose or code fences; take the outermost braces. */
    fun parse(raw: String): Extraction? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching {
            json.decodeFromString<Extraction>(raw.substring(start, end + 1))
        }.getOrNull()
    }

    suspend fun apply(extraction: Extraction, chatId: String) {
        applyCharacters(extraction.characters)
        applyItems(extraction.items, chatId)
    }

    /** Merges extracted characters into the roster; returns the names processed. */
    suspend fun applyCharacters(chars: List<ExtractedCharacter>): List<String> {
        val dao = db.roleplayDao()
        val applied = mutableListOf<String>()
        chars.filter { it.name.isNotBlank() }.forEach { extracted ->
            val name = extracted.name.trim()
            val existing = dao.getCharacters().firstOrNull {
                it.name.equals(name, ignoreCase = true)
            }
            if (existing == null) {
                val now = System.currentTimeMillis()
                val entity = RpCharacterEntity(
                    id = "rpc-${UUID.randomUUID()}",
                    name = name,
                    description = extracted.notes,
                    inParty = extracted.inParty,
                    createdAt = now,
                    updatedAt = now,
                )
                val sheet = RpgCharacterSheet(
                    characterClass = extracted.characterClass.ifBlank { "Adventurer" },
                    species = extracted.species,
                    level = extracted.level.coerceAtLeast(1),
                    currentHp = extracted.currentHp.coerceAtLeast(0),
                    maxHp = extracted.maxHp.coerceAtLeast(0),
                    armorClass = extracted.armorClass.coerceAtLeast(0),
                    appearance = extracted.appearance,
                )
                dao.upsertCharacter(
                    entity.copy(extensionsJson = encodeRpgSheet(entity.extensionsJson, sheet)),
                )
            } else {
                val sheet = decodeRpgSheet(existing.extensionsJson)
                val merged = sheet.copy(
                    species = sheet.species.ifBlank { extracted.species },
                    level = if (extracted.level > 0 && sheet.level <= 1) extracted.level else sheet.level,
                    currentHp = if (extracted.currentHp > 0 && sheet.currentHp <= 10) extracted.currentHp else sheet.currentHp,
                    maxHp = if (extracted.maxHp > 0 && sheet.maxHp <= 10) extracted.maxHp else sheet.maxHp,
                    armorClass = if (extracted.armorClass > 0 && sheet.armorClass <= 10) extracted.armorClass else sheet.armorClass,
                    appearance = sheet.appearance.ifBlank { extracted.appearance },
                )
                dao.upsertCharacter(
                    existing.copy(
                        description = existing.description.ifBlank { extracted.notes },
                        inParty = existing.inParty || extracted.inParty,
                        extensionsJson = encodeRpgSheet(existing.extensionsJson, merged),
                    ),
                )
            }
            applied += name
        }
        return applied
    }

    /**
     * Files extracted items. Items with a [ExtractedItem.carrier] that names a
     * roster character go into that character's inventory; the rest fall back to
     * the persona, then the chat's main character, then any party member.
     * Returns a human-readable summary of where things landed.
     */
    suspend fun applyItems(items: List<ExtractedItem>, chatId: String): String {
        val dao = db.roleplayDao()
        val chat = dao.getChat(chatId)
        val persona = chat?.personaId?.let { dao.getPersona(it) }
        val mainCharacter = chat?.characterId?.let { dao.getCharacter(it) }
        val destinations = mutableListOf<String>()
        items.filter { it.name.isNotBlank() }.forEach { extracted ->
            val name = extracted.name.trim()
            val qty = extracted.quantity.coerceAtLeast(1)
            val carrierName = extracted.carrier.trim()
            val carrier = carrierName.takeIf { it.isNotBlank() && !it.equals("party", ignoreCase = true) }
                ?.let { carrierName ->
                    dao.getCharacters().firstOrNull { it.name.equals(carrierName, ignoreCase = true) }
                }
            when {
                carrier != null -> {
                    val next = mergeItem(decodeItems(carrier.inventoryJson), name, qty, extracted.notes)
                    dao.upsertCharacter(carrier.copy(inventoryJson = encodeItems(next)))
                    destinations += "$name → ${carrier.name}"
                }
                persona != null -> {
                    val next = mergeItem(decodeItems(persona.inventoryJson), name, qty, extracted.notes)
                    dao.upsertPersona(persona.copy(inventoryJson = encodeItems(next)))
                    destinations += "$name → ${persona.name.ifBlank { "You" }}"
                }
                mainCharacter != null -> {
                    val next = mergeItem(decodeItems(mainCharacter.inventoryJson), name, qty, extracted.notes)
                    dao.upsertCharacter(mainCharacter.copy(inventoryJson = encodeItems(next)))
                    destinations += "$name → ${mainCharacter.name}"
                }
                else -> {
                    val partyMember = dao.getCharacters().firstOrNull { it.inParty }
                    if (partyMember != null) {
                        val next = mergeItem(decodeItems(partyMember.inventoryJson), name, qty, extracted.notes)
                        dao.upsertCharacter(partyMember.copy(inventoryJson = encodeItems(next)))
                        destinations += "$name → ${partyMember.name}"
                    } else {
                        destinations += "$name (no carrier found)"
                    }
                }
            }
        }
        return destinations.joinToString(", ")
    }

    private fun mergeItem(items: List<RpItem>, name: String, qty: Int, notes: String): List<RpItem> {
        val existing = items.firstOrNull { it.name.equals(name, ignoreCase = true) }
        return if (existing != null) {
            items.map {
                if (it.id == existing.id) {
                    it.copy(
                        quantity = it.quantity + qty,
                        notes = it.notes.ifBlank { notes },
                    )
                } else {
                    it
                }
            }
        } else {
            items + RpItem(
                id = "item-${UUID.randomUUID()}",
                name = name,
                quantity = qty,
                notes = notes,
            )
        }
    }

    /**
     * Manual ➕👤 fallback: create a blank party character the user can flesh
     * out in Roster.
     */
    suspend fun addBlankCharacter(): String? {
        val dao = db.roleplayDao()
        val now = System.currentTimeMillis()
        val entity = RpCharacterEntity(
            id = "rpc-${UUID.randomUUID()}",
            name = "New Character",
            inParty = true,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsertCharacter(entity)
        return entity.id
    }

    /**
     * Manual ➕🎒 fallback: drop a blank item into the persona's (or a party
     * member's) inventory. Returns the carrier name, or null when no carrier exists.
     */
    suspend fun addBlankItem(chatId: String): String? {
        val dao = db.roleplayDao()
        val chat = dao.getChat(chatId)
        val persona = chat?.personaId?.let { dao.getPersona(it) }
        val character = chat?.characterId?.let { dao.getCharacter(it) }
            ?: dao.getCharacters().firstOrNull { it.inParty }
            ?: dao.getCharacters().firstOrNull()
        val item = RpItem(id = "item-${UUID.randomUUID()}", name = "New Item")
        return when {
            persona != null -> {
                dao.upsertPersona(
                    persona.copy(inventoryJson = encodeItems(decodeItems(persona.inventoryJson) + item)),
                )
                persona.name.ifBlank { "You" }
            }
            character != null -> {
                dao.upsertCharacter(
                    character.copy(inventoryJson = encodeItems(decodeItems(character.inventoryJson) + item)),
                )
                character.name
            }
            else -> null
        }
    }
}
