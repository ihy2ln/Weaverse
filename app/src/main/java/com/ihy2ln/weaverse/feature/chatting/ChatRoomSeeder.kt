package com.ihy2ln.weaverse.feature.chatting

import com.ihy2ln.weaverse.core.roleplay.avatarColorHexFor
import com.ihy2ln.weaverse.core.text.Document
import com.ihy2ln.weaverse.core.text.toJson
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.BookEntity
import com.ihy2ln.weaverse.data.db.entities.RpCharacterEntity
import com.ihy2ln.weaverse.data.db.entities.RpChatEntity
import com.ihy2ln.weaverse.data.db.entities.RpMessageEntity
import com.ihy2ln.weaverse.data.db.entities.RpPersonaEntity
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Seeds the Discord-style room layout for a work: the three starter text
 * channels plus one live room per character tied to the work (campaign roster
 * members via the setup note; novel characters via defaultCodexId codex
 * links). Called when a work is created and defensively whenever a server is
 * opened, so legacy works catch up too.
 */
@Singleton
class ChatRoomSeeder @Inject constructor(
    private val db: WeaverseDatabase,
) {
    /** Books already seeded this session — keeps repeat scans cheap. */
    private val ensured = mutableSetOf<String>()

    suspend fun ensureRoomsForBook(book: BookEntity) {
        if (book.workType !in SERVER_WORK_TYPES) return
        if (!ensured.add(book.id)) return
        val existing = db.roleplayDao().observeRoomsForBook(book.id).first()
        if (existing.none { it.roomKind == ROOM_KIND_CHANNEL }) {
            listOf(
                "general" to "General chat about ${book.title}.",
                "lore" to "Deep-dive the world, canon, and lore of ${book.title}.",
                "brainstorm" to "Pitch ideas, outlines, and what-ifs for ${book.title}.",
            ).forEach { (name, topic) ->
                createRoom(book, name, ROOM_KIND_CHANNEL, null, topic, null)
            }
        }
        if (existing.none { it.roomKind == ROOM_KIND_CHARACTER }) {
            // Campaign roster characters live in the adventure chat's setup note;
            // novel characters are linked through the book's codex entries.
            val campaignSetup = db.roleplayDao().getChats()
                .firstOrNull { it.bookId == book.id && it.displayMode == "dungeonMaster" }
                ?.authorsNote
                .orEmpty()
            val characterIds = rosterCharacterIds(campaignSetup) + codexLinkedCharacterIds(book.id)
            characterIds.distinct().forEach { id ->
                db.roleplayDao().getCharacter(id)?.let { character ->
                    createRoom(
                        book = book,
                        name = character.name,
                        kind = ROOM_KIND_CHARACTER,
                        characterId = character.id,
                        topic = "A private room where ${character.name} hangs out.",
                        character = character,
                    )
                }
            }
        }
    }

    /** Creates a room row and optionally seeds the character's greeting. */
    suspend fun createRoom(
        book: BookEntity,
        name: String,
        kind: String,
        characterId: String?,
        topic: String,
        character: RpCharacterEntity?,
    ): RpChatEntity {
        val now = System.currentTimeMillis()
        val chat = RpChatEntity(
            id = "room-${UUID.randomUUID()}",
            characterId = characterId,
            personaId = defaultPersona().id,
            title = name,
            authorsNote = topic,
            displayMode = "messenger",
            createdAt = now,
            updatedAt = now,
            bookId = book.id,
            roomKind = kind,
        )
        db.roleplayDao().upsertChat(chat)
        if (character != null) seedGreeting(chat.id, character, now)
        return chat
    }

    suspend fun defaultPersona(): RpPersonaEntity =
        db.roleplayDao().getPersonas().firstOrNull { it.isDefault }
            ?: db.roleplayDao().getPersonas().firstOrNull()
            ?: RpPersonaEntity(id = "persona-default", name = "You", isDefault = true)

    private suspend fun seedGreeting(chatId: String, character: RpCharacterEntity, now: Long) {
        val greeting = character.firstMes.trim()
        if (greeting.isBlank()) return
        db.roleplayDao().upsertMessage(
            RpMessageEntity(
                id = "rpm-${UUID.randomUUID()}",
                chatId = chatId,
                swipeGroupId = "sw-${UUID.randomUUID()}",
                swipeIndex = 0,
                isActiveSwipe = true,
                role = "char",
                speakerCharacterId = character.id,
                contentJson = Document.fromPlainText(greeting).toJson(),
                createdAt = now,
                displayMode = "messenger",
            ),
        )
    }

    /** Campaign setup notes list main characters as `roster:<id>` entries. */
    private fun rosterCharacterIds(setup: String): List<String> =
        Regex("roster:([A-Za-z0-9_\\-]+)").findAll(setup)
            .map { it.groupValues[1] }
            .distinct()
            .toList()

    private suspend fun codexLinkedCharacterIds(bookId: String): List<String> =
        db.roleplayDao().getCharacters().filter { character ->
            val codexId = character.defaultCodexId ?: return@filter false
            val entry = db.codexDao().observeEntry(codexId).first()
            entry != null && entry.scopeId == bookId
        }.map { it.id }

    companion object {
        private val SERVER_WORK_TYPES = setOf("novel", "campaign")
    }
}
