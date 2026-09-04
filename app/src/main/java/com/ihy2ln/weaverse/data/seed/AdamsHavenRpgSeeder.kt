package com.ihy2ln.weaverse.data.seed

import com.ihy2ln.weaverse.ai.prompt.DefaultAiGuides
import com.ihy2ln.weaverse.core.text.Document
import com.ihy2ln.weaverse.core.text.plainText
import com.ihy2ln.weaverse.core.text.toJson
import com.ihy2ln.weaverse.core.ui.theme.CodexCategoryColors
import com.ihy2ln.weaverse.core.ui.theme.toHexString
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.CodexCategoryEntity
import com.ihy2ln.weaverse.data.db.entities.CodexEntryEntity
import com.ihy2ln.weaverse.data.db.entities.CodexEntryLoreEntity
import com.ihy2ln.weaverse.data.db.entities.RpCharacterEntity
import com.ihy2ln.weaverse.data.db.entities.RpChatEntity
import com.ihy2ln.weaverse.data.db.entities.RpMessageEntity
import com.ihy2ln.weaverse.data.db.entities.RpPersonaEntity
import com.ihy2ln.weaverse.data.db.entities.SnippetEntity
import com.ihy2ln.weaverse.data.repo.CodexScopes
import com.ihy2ln.weaverse.sync.adams.AdamsHavenRpgCatalog
import com.ihy2ln.weaverse.sync.adams.RpgCard
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Inserts the Adams Haven game pack into Roleplay (cards, lorebook, scene chats)
 * without overwriting anything the user has already edited.
 */
@Singleton
class AdamsHavenRpgSeeder @Inject constructor(
    private val db: WeaverseDatabase,
) {
    suspend fun seedIfMissing() {
        val now = System.currentTimeMillis()
        val personaId = ensurePersona()
        val characterIds = ensureCards(now)
        ensureLore(now)
        ensureSnippet(now)
        ensureScenes(personaId, characterIds, now)
    }

    private suspend fun ensurePersona(): String {
        val existingDefault = db.roleplayDao().getPersonas().firstOrNull { it.isDefault }
        if (existingDefault != null) return existingDefault.id
        val named = db.roleplayDao().getPersonas()
            .firstOrNull { it.name.equals(AdamsHavenRpgCatalog.PERSONA_NAME, ignoreCase = true) }
        if (named != null) return named.id
        val persona = AdamsHavenRpgCatalog.persona
        db.roleplayDao().upsertPersona(
            RpPersonaEntity(
                id = persona.id,
                name = persona.name,
                description = persona.description,
                isDefault = true,
            ),
        )
        return persona.id
    }

    private suspend fun ensureCards(now: Long): Map<String, String> {
        val byId = db.roleplayDao().getCharacters().associateBy { it.id }
        val byName = db.roleplayDao().getCharacters().associateBy { it.name.lowercase() }
        val resolved = linkedMapOf<String, String>()
        AdamsHavenRpgCatalog.cards.forEach { card ->
            val existingId = byId[card.id]?.id
                ?: byName[card.name.lowercase()]?.id
            if (existingId != null) {
                resolved[card.id] = existingId
                return@forEach
            }
            db.roleplayDao().upsertCharacter(toEntity(card, now))
            resolved[card.id] = card.id
        }
        return resolved
    }

    private fun toEntity(card: RpgCard, now: Long): RpCharacterEntity = RpCharacterEntity(
        id = card.id,
        name = card.name,
        description = card.description,
        personality = card.personality,
        scenario = card.scenario,
        firstMes = card.firstMes,
        creatorNotes = card.creatorNotes,
        systemPrompt = DefaultAiGuides.characterSystemPrompt(
            name = card.name,
            description = card.description,
            personality = card.personality,
            scenario = card.scenario,
        ),
        tagsJson = AdamsHavenRpgCatalog.tagsJsonFor(card),
        extensionsJson = AdamsHavenRpgCatalog.extensionsJsonFor(card),
        colorHex = card.colorHex,
        createdAt = now,
    )

    private suspend fun ensureLore(now: Long) {
        val categories = ensureCategories(now)
        val existing = db.codexDao().getAllEntries().associateBy { it.id }
        AdamsHavenRpgCatalog.lore.forEach { entry ->
            if (existing.containsKey(entry.id)) return@forEach
            val categoryId = categories[entry.category] ?: return@forEach
            val doc = Document.fromPlainText(entry.body)
            db.codexDao().upsertEntry(
                CodexEntryEntity(
                    id = entry.id,
                    categoryId = categoryId,
                    scopeType = CodexScopes.TYPE,
                    scopeId = CodexScopes.ID,
                    name = entry.name,
                    aliasesJson = AdamsHavenRpgCatalog.aliasesJsonFor(entry),
                    docJson = doc.toJson(),
                    plainText = doc.plainText(),
                    colorHex = entry.colorHex,
                    alwaysInclude = entry.alwaysInclude,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            db.codexDao().upsertLore(
                CodexEntryLoreEntity(
                    entryId = entry.id,
                    keysJson = AdamsHavenRpgCatalog.keysJsonFor(entry),
                    insertionOrder = entry.insertionOrder,
                    isConstant = entry.isConstant,
                    groupName = "Adams Haven RPG",
                ),
            )
        }
    }

    private suspend fun ensureCategories(now: Long): Map<String, String> {
        val wanted = AdamsHavenRpgCatalog.lore.map { it.category }.distinct()
        val existing = db.codexDao().getAllCategories()
        val byName = existing.associateBy { it.name.trim().lowercase() }
        val resolved = linkedMapOf<String, String>()
        wanted.forEachIndexed { index, name ->
            val hit = byName[name.lowercase()]
            if (hit != null) {
                resolved[name] = hit.id
                return@forEachIndexed
            }
            val id = "ah-rpg-cat-${name.lowercase().replace(Regex("[^a-z0-9]+"), "-")}"
            val color = CodexCategoryColors[index % CodexCategoryColors.size].toHexString()
            db.codexDao().upsertCategory(
                CodexCategoryEntity(
                    id = id,
                    scopeType = CodexScopes.TYPE,
                    scopeId = CodexScopes.ID,
                    name = name,
                    colorHex = color,
                    sortOrder = 80 + index,
                    isSystem = false,
                    isBuiltIn = true,
                ),
            )
            resolved[name] = id
        }
        return resolved
    }

    private suspend fun ensureSnippet(now: Long) {
        if (db.snippetDao().getById(AdamsHavenRpgCatalog.SNIPPET_ID) != null) return
        val snippet = AdamsHavenRpgCatalog.gmBrief
        db.snippetDao().upsert(
            SnippetEntity(
                id = snippet.id,
                scopeType = "app",
                scopeId = "global",
                title = snippet.title,
                body = snippet.body,
                category = "notes",
                pinned = true,
                createdAt = now,
            ),
        )
    }

    private suspend fun ensureScenes(
        personaId: String,
        characterIds: Map<String, String>,
        now: Long,
    ) {
        val existingChats = db.roleplayDao().getChats().associateBy { it.id }
        AdamsHavenRpgCatalog.scenes.forEach { scene ->
            if (existingChats.containsKey(scene.id)) return@forEach
            val speaker = characterIds[scene.characterId]
            db.roleplayDao().upsertChat(
                RpChatEntity(
                    id = scene.id,
                    characterId = speaker,
                    personaId = personaId,
                    title = scene.title,
                    authorsNote = scene.authorsNote,
                    authorsNoteDepth = 6,
                    displayMode = scene.displayMode,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            db.roleplayDao().upsertMessage(
                RpMessageEntity(
                    id = "${scene.id}-m0",
                    chatId = scene.id,
                    swipeGroupId = "${scene.id}-swipe",
                    swipeIndex = 0,
                    isActiveSwipe = true,
                    role = "char",
                    speakerCharacterId = speaker,
                    contentJson = Document.fromPlainText(scene.opening).toJson(),
                    createdAt = now,
                    displayMode = scene.displayMode,
                ),
            )
        }
    }
}
