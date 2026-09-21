package com.ihy2ln.weaverse.feature.chatting

import com.ihy2ln.weaverse.core.text.CodexMentionTarget
import com.ihy2ln.weaverse.core.text.findCodexMentions
import com.ihy2ln.weaverse.core.text.plainText
import com.ihy2ln.weaverse.core.text.documentFromJson
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.BookEntity
import com.ihy2ln.weaverse.data.db.entities.CodexEntryEntity
import com.ihy2ln.weaverse.data.db.entities.RpCharacterEntity
import com.ihy2ln.weaverse.data.db.entities.RpRoomMemberEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves who can live in a work's Chatting rooms — the campaign roster plus every
 * codex character tied to the work — and tracks who is actually seated in each room.
 * Codex entries without a character card get a lightweight [RpCharacterEntity] so they
 * can speak and carry a color, materialized once behind a deterministic id.
 */
private val aliasJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

@Singleton
class ChatCastResolver @Inject constructor(
    private val db: WeaverseDatabase,
) {
    /** Every character eligible to live in [book]'s rooms: roster + codex cast. */
    suspend fun castForBook(book: BookEntity): List<RpCharacterEntity> {
        val characterCategory = characterCategoryEntries(book.id)
        val materialized = characterCategory.map { entry -> materializeCharacter(entry) }
        val campaignSetup = db.roleplayDao().getChats()
            .firstOrNull { it.bookId == book.id && it.displayMode == "dungeonMaster" }
            ?.authorsNote
            .orEmpty()
        val rosterIds = rosterCharacterIds(campaignSetup)
        val roster = rosterIds.mapNotNull { db.roleplayDao().getCharacter(it) }
        return (roster + materialized).distinctBy { it.id }
    }

    /** Codex entries in the shared Characters category relevant to [bookId]. */
    private suspend fun characterCategoryEntries(bookId: String): List<CodexEntryEntity> {
        val categories = db.codexDao().getAllCategories()
            .filter { it.name.equals(CHARACTER_CATEGORY_NAME, ignoreCase = true) }
        if (categories.isEmpty()) return emptyList()
        val entries = db.codexDao().getAllEntries()
        val categoryIds = categories.map { it.id }.toSet()
        val inCategory = entries.filter { it.categoryId in categoryIds }
        val scoped = inCategory.filter { it.scopeId == bookId || it.sheetJson.contains(bookId) }
        return scoped.ifEmpty { inCategory }
    }

    /** Finds or creates the character card backing a codex entry, idempotent by a deterministic id. */
    private suspend fun materializeCharacter(entry: CodexEntryEntity): RpCharacterEntity {
        val linked = db.roleplayDao().getCharacters().firstOrNull { it.defaultCodexId == entry.id }
        if (linked != null) return linked
        val deterministicId = "char-codex-${entry.id}"
        db.roleplayDao().getCharacter(deterministicId)?.let { return it }
        val character = RpCharacterEntity(
            id = deterministicId,
            name = entry.name,
            description = documentFromJson(entry.docJson).plainText().take(1200)
                .ifBlank { entry.plainText.take(1200) },
            colorHex = entry.colorHex,
            defaultCodexId = entry.id,
            createdAt = System.currentTimeMillis(),
        )
        db.roleplayDao().upsertCharacter(character)
        return character
    }

    /**
     * Codex entries whose name or an alias appears in [text] — the people and things the
     * message is actually talking about, whether or not they were @mentioned.
     */
    suspend fun codexMatchesIn(text: String, bookId: String?): List<CodexEntryEntity> {
        if (text.isBlank()) return emptyList()
        val entries = db.codexDao().getAllEntries().filterNot { it.disabled }
        if (entries.isEmpty()) return emptyList()
        val scoped = bookId?.let { id ->
            entries.filter { it.scopeId == id || it.sheetJson.contains(id) }.ifEmpty { entries }
        } ?: entries
        val byId = scoped.associateBy { it.id }
        val targets = scoped.map { entry ->
            CodexMentionTarget(
                entryId = entry.id,
                name = entry.name,
                aliases = decodeAliases(entry.aliasesJson),
                caseSensitive = entry.caseSensitiveMatching,
            )
        }
        return findCodexMentions(text, targets)
            .mapNotNull { byId[it.entryId] }
            .distinctBy { it.id }
    }

    /**
     * Codex context for a room, assembled on every send so chat is never running blind:
     * the work's always-include entries, the seated cast's own entries, and anything the
     * message named. Deduplicated, message matches first, capped for the prompt budget.
     */
    suspend fun codexContextFor(
        text: String,
        bookId: String?,
        members: List<RpCharacterEntity>,
        limit: Int = 8,
        /** Whoever is being spoken to — their entry leads, so their own rules bind first. */
        speakers: List<RpCharacterEntity> = emptyList(),
    ): List<CodexEntryEntity> {
        val speakerEntries = speakers.mapNotNull { it.defaultCodexId }
            .let { ids -> if (ids.isEmpty()) emptyList() else entriesByIds(ids) }
        val named = codexMatchesIn(text, bookId)
        val memberEntries = members.mapNotNull { it.defaultCodexId }
            .let { ids -> if (ids.isEmpty()) emptyList() else entriesByIds(ids) }
        val always = alwaysIncludeEntries(bookId)
        return (speakerEntries + named + memberEntries + always).distinctBy { it.id }.take(limit)
    }

    /** Entries the writer flagged as always-include, scoped to the work when possible. */
    suspend fun alwaysIncludeEntries(bookId: String?): List<CodexEntryEntity> {
        val entries = db.codexDao().getAllEntries().filterNot { it.disabled }.filter { it.alwaysInclude }
        if (bookId == null) return entries
        val scoped = entries.filter { it.scopeId == bookId || it.sheetJson.contains(bookId) }
        return scoped.ifEmpty { entries }
    }

    private suspend fun entriesByIds(ids: List<String>): List<CodexEntryEntity> {
        val wanted = ids.toSet()
        return db.codexDao().getAllEntries().filter { it.id in wanted && !it.disabled }
    }

    /** The character card for a codex entry, creating the lightweight one if needed. */
    suspend fun characterForEntry(entry: CodexEntryEntity): RpCharacterEntity = materializeCharacter(entry)

    /** A codex entry prepared for the prompt: what it is, and which category governs it. */
    data class CodexRef(val name: String, val category: String, val text: String)

    /** Names the category each entry belongs to, so rules read as rules and people as people. */
    suspend fun describe(entries: List<CodexEntryEntity>): List<CodexRef> {
        if (entries.isEmpty()) return emptyList()
        val categories = db.codexDao().getAllCategories().associate { it.id to it.name }
        return entries.map { entry ->
            CodexRef(
                name = entry.name,
                category = categories[entry.categoryId].orEmpty(),
                text = entryText(entry),
            )
        }
    }

    /** Readable body text for a codex entry, for the prompt's reference block. */
    fun entryText(entry: CodexEntryEntity): String =
        documentFromJson(entry.docJson).plainText().ifBlank { entry.plainText }.trim()

    private fun decodeAliases(json: String): List<String> =
        runCatching { aliasJson.decodeFromString<List<String>>(json) }.getOrDefault(emptyList())

    suspend fun membersOf(roomId: String): List<RpCharacterEntity> =
        db.roleplayDao().getMembers(roomId).mapNotNull { member -> db.roleplayDao().getCharacter(member.characterId) }

    suspend fun addMember(roomId: String, character: RpCharacterEntity, seeded: Boolean) {
        db.roleplayDao().upsertMember(
            RpRoomMemberEntity(
                roomId = roomId,
                characterId = character.id,
                codexEntryId = character.defaultCodexId,
                seeded = seeded,
                addedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun removeMember(roomId: String, characterId: String) {
        db.roleplayDao().deleteMember(roomId, characterId)
    }

    /** Campaign setup notes list main characters as `roster:<id>` entries. */
    fun rosterCharacterIds(setup: String): List<String> =
        Regex("roster:([A-Za-z0-9_\\-]+)").findAll(setup)
            .map { it.groupValues[1] }
            .distinct()
            .toList()

    companion object {
        private const val CHARACTER_CATEGORY_NAME = "Characters"
    }
}
