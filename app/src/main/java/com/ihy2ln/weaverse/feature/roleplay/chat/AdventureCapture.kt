package com.ihy2ln.weaverse.feature.roleplay.chat

import com.ihy2ln.weaverse.ai.AiGenerationService
import com.ihy2ln.weaverse.core.text.toJson
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

    /** Free text that belongs in a codex entry rather than a sheet or pack. */
    @Serializable
    data class ExtractedLore(
        val title: String = "",
        val category: String = "Lore",
        val text: String = "",
        val uncertain: Boolean = false,
    )

    @Serializable
    data class Extraction(
        val characters: List<ExtractedCharacter> = emptyList(),
        val items: List<ExtractedItem> = emptyList(),
        val lore: List<ExtractedLore> = emptyList(),
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
            append("\"items\":[{\"name\":\"\",\"quantity\":1,\"notes\":\"\",\"carrier\":\"\"}],")
            append("\"lore\":[]}\n")
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

    /**
     * Full AI routing for the "AI sort into Codex" capture: splits selected
     * text into character-sheet facts, inventory items, and codex lore (with
     * a suggested category). Rows the model is unsure about are flagged with
     * [ExtractedLore.uncertain] so the review dialog can ask the user.
     */
    suspend fun plan(text: String): CapturePlan? {
        if (!aiGeneration.hasApiKey()) return null
        val source = text.trim().take(6000)
        if (source.isBlank()) return null
        val instruction = buildString {
            append("You are the campaign bookkeeper for a writing app. Split the selected text below ")
            append("into the right destination sections. Reply with ONLY a JSON object, no prose:\n")
            append("{\"characters\":[{\"name\":\"\",\"inParty\":false,\"characterClass\":\"\",")
            append("\"species\":\"\",\"level\":0,\"currentHp\":0,\"maxHp\":0,\"armorClass\":0,")
            append("\"appearance\":\"\",\"notes\":\"\"}],")
            append("\"items\":[{\"name\":\"\",\"quantity\":1,\"notes\":\"\",\"carrier\":\"\"}],")
            append("\"lore\":[{\"title\":\"\",\"category\":\"Lore\",\"text\":\"\",\"uncertain\":false}]}\n")
            append("Rules:\n")
            append("- characters: facts that belong on a character sheet (class, species, level, ")
            append("hp, armor class, appearance, backstory). Only include fields the text states.\n")
            append("- items: physical objects with a count (weapons, gear, treasure, supplies).\n")
            append("- lore: everything else worth keeping — worldbuilding, history, locations, ")
            append("rumors. Set category to Characters, Locations, Objects, Lore, Factions, or ")
            append("Events. Set uncertain=true when you cannot tell if the text is lore, a ")
            append("character fact, or an item.\n")
            append("Omit empty sections.\n\n")
            append("Selected text:\n<text>\n$source\n</text>")
        }
        val result = aiGeneration.complete(
            userMessage = instruction,
            maxTokens = 1200,
            temperature = 0.2,
        )
        val parsed = parse(result.text) ?: return null
        val hasContent = parsed.characters.isNotEmpty() || parsed.items.isNotEmpty() ||
            parsed.lore.any { it.text.isNotBlank() || it.title.isNotBlank() }
        return if (hasContent) CapturePlan(
            characters = parsed.characters,
            items = parsed.items,
            lore = parsed.lore,
        ) else null
    }

    data class CapturePlan(
        val characters: List<ExtractedCharacter>,
        val items: List<ExtractedItem>,
        val lore: List<ExtractedLore>,
    )

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
        applyCharacters(extraction.characters, chatId)
        applyItems(extraction.items, chatId)
    }

    /**
     * Applies an AI-routed plan: characters merge into the roster (with a
     * linked codex entry in the Characters category), items file into the
     * right carrier's inventory, and lore blobs become codex entries in the
     * suggested category. Returns a human-readable placement summary.
     */
    suspend fun applyPlan(plan: CapturePlan, chatId: String): String {
        val appliedCharacters = applyCharacters(plan.characters, chatId)
        val appliedItems = applyItems(plan.items, chatId)
        val appliedLore = applyLore(plan.lore, chatId)
        return listOf(appliedCharacters.joinToString(", "), appliedItems, appliedLore)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
    }

    /** Writes lore blobs as codex entries in the suggested category. */
    private suspend fun applyLore(lore: List<ExtractedLore>, chatId: String): String {
        val applied = mutableListOf<String>()
        lore.filter { it.text.isNotBlank() || it.title.isNotBlank() }.forEach { blob ->
            val chat = db.roleplayDao().getChat(chatId)
            val scopeId = chat?.bookId ?: "global"
            val categoryName = blob.category.ifBlank { "Lore" }
            val category = ensureCategory(categoryName, scopeId)
            val entry = ensureCodexEntry(category, scopeId, blob.title.ifBlank { categoryName })
            db.codexDao().upsertEntry(
                entry.copy(
                    plainText = listOf(entry.plainText, blob.text.trim())
                        .filter { it.isNotBlank() }
                        .joinToString("\n\n"),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            applied += "\"${entry.name}\" → Codex · $categoryName"
        }
        return applied.joinToString(", ")
    }

    /** Finds a codex category by name in the scope, creating it when missing. */
    private suspend fun ensureCategory(name: String, scopeId: String): com.ihy2ln.weaverse.data.db.entities.CodexCategoryEntity {
        val existing = db.codexDao().getAllCategories().firstOrNull {
            it.name.equals(name, ignoreCase = true) &&
                (it.scopeId == scopeId || it.scopeId == com.ihy2ln.weaverse.data.repo.CodexScopes.ID)
        }
        if (existing != null) return existing
        val now = System.currentTimeMillis()
        val entity = com.ihy2ln.weaverse.data.db.entities.CodexCategoryEntity(
            id = "rpg-cat-${UUID.randomUUID()}",
            scopeType = "book",
            scopeId = scopeId,
            name = name,
            colorHex = if (name.equals("Characters", ignoreCase = true)) "#3F7A5A" else "#6B5B95",
            sortOrder = 100,
            updatedAt = now,
        )
        db.codexDao().upsertCategory(entity)
        return entity
    }

    /** Finds a codex entry by name in the category, creating it when missing. */
    private suspend fun ensureCodexEntry(
        category: com.ihy2ln.weaverse.data.db.entities.CodexCategoryEntity,
        scopeId: String,
        name: String,
    ): com.ihy2ln.weaverse.data.db.entities.CodexEntryEntity {
        val existing = db.codexDao().getAllEntries().firstOrNull {
            it.categoryId == category.id && it.name.equals(name, ignoreCase = true)
        }
        if (existing != null) return existing
        val now = System.currentTimeMillis()
        val entity = com.ihy2ln.weaverse.data.db.entities.CodexEntryEntity(
            id = "rpg-codex-${UUID.randomUUID()}",
            categoryId = category.id,
            scopeType = "book",
            scopeId = scopeId,
            name = name,
            docJson = com.ihy2ln.weaverse.core.text.Document.fromPlainText("").toJson(),
            plainText = "",
            isAiGenerated = true,
            createdAt = now,
            updatedAt = now,
        )
        db.codexDao().upsertEntry(entity)
        return entity
    }

    /** Merges extracted characters into the roster; returns the names processed. */
    suspend fun applyCharacters(chars: List<ExtractedCharacter>, chatId: String? = null): List<String> {
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
                    characterClass = sheet.characterClass.ifBlank { extracted.characterClass },
                    species = sheet.species.ifBlank { extracted.species },
                    level = if (extracted.level > sheet.level) extracted.level else sheet.level,
                    currentHp = if (extracted.currentHp > 0 && extracted.currentHp > sheet.currentHp) extracted.currentHp else sheet.currentHp,
                    maxHp = if (extracted.maxHp > sheet.maxHp) extracted.maxHp else sheet.maxHp,
                    armorClass = if (extracted.armorClass > sheet.armorClass) extracted.armorClass else sheet.armorClass,
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
            // Codex parity: every rostered character gets a linked entry in the
            // work's Characters category (defaultCodexId → entry).
            if (chatId != null) {
                val bookId = db.roleplayDao().getChat(chatId)?.bookId ?: "global"
                val category = ensureCategory("Characters", bookId)
                val entry = ensureCodexEntry(category, bookId, name)
                if (extracted.notes.isNotBlank()) {
                    val current = db.codexDao().getAllEntries().find { it.id == entry.id }
                    if (current != null && current.plainText.isBlank()) {
                        db.codexDao().upsertEntry(
                            current.copy(plainText = extracted.notes, updatedAt = System.currentTimeMillis()),
                        )
                    }
                }
                dao.getCharacters().firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { character ->
                    dao.upsertCharacter(character.copy(defaultCodexId = entry.id))
                }
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
