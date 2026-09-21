package com.ihy2ln.weaverse.feature.chatting

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
