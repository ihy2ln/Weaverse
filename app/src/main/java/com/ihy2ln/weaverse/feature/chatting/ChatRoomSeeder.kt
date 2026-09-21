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
import kotlin.random.Random

/**
 * Seeds the Discord-style room layout for a work: the three starter text
 * channels plus one live room per character in [ChatCastResolver]'s cast for
 * the work, each room seeded with a 1-5 person member list. Called when a
 * work is created and defensively whenever a server is opened, so legacy
 * works catch up too.
 */
@Singleton
class ChatRoomSeeder @Inject constructor(
    private val db: WeaverseDatabase,
    private val castResolver: ChatCastResolver,
) {
    /** Books already seeded this session — keeps repeat scans cheap. */
    private val ensured = mutableSetOf<String>()

    suspend fun ensureRoomsForBook(book: BookEntity) {
        if (book.workType !in SERVER_WORK_TYPES) return
        if (!ensured.add(book.id)) return
        val existing = db.roleplayDao().observeRoomsForBook(book.id).first()
        val cast = castResolver.castForBook(book)

        if (existing.none { it.roomKind == ROOM_KIND_CHANNEL }) {
            listOf(
                Triple("general", "General chat about ${book.title}.", ambientGeneral(book.title)),
                Triple("lore", "Deep-dive the world, canon, and lore of ${book.title}.", ambientLore(book.title)),
                Triple("brainstorm", "Pitch ideas, outlines, and what-ifs for ${book.title}.", ambientBrainstorm(book.title)),
            ).forEach { (name, topic, lines) ->
                val room = createRoom(book, name, ROOM_KIND_CHANNEL, null, topic, null)
                val members = channelCast(book, name, cast)
                members.forEach { castResolver.addMember(room.id, it, seeded = true) }
                seedAmbientActivity(room.id, members, lines)
            }
        }
        if (existing.none { it.roomKind == ROOM_KIND_CHARACTER }) {
            cast.forEach { character ->
                val room = createRoom(
                    book = book,
                    name = character.name,
                    kind = ROOM_KIND_CHARACTER,
                    characterId = character.id,
                    topic = "A private room where ${character.name} hangs out.",
                    character = character,
                )
                castResolver.addMember(room.id, character, seeded = true)
                (cast - character).shuffled(Random(book.id.hashCode() xor character.id.hashCode()))
                    .take((cast.size - 1).coerceIn(0, 2))
                    .forEach { castResolver.addMember(room.id, it, seeded = true) }
            }
        }

        // Catch-up: rooms that already exist (from before rooms carried members) get
        // seeded now too, so this isn't limited to newly created works.
        existing.filter { room -> db.roleplayDao().getMembers(room.id).isEmpty() }
            .forEach { room ->
                val members = when (room.roomKind) {
                    ROOM_KIND_CHANNEL -> channelCast(book, room.title, cast)
                    ROOM_KIND_CHARACTER -> room.characterId?.let { id -> cast.filter { it.id == id } }.orEmpty()
                        .ifEmpty { room.characterId?.let { id -> db.roleplayDao().getCharacter(id) }?.let(::listOf).orEmpty() }
                    else -> emptyList()
                }
                members.forEach { castResolver.addMember(room.id, it, seeded = true) }
            }
    }

    /** A stable 1-5 person slice of the cast for one channel, different per channel. */
    private fun channelCast(book: BookEntity, channelName: String, cast: List<RpCharacterEntity>): List<RpCharacterEntity> {
        if (cast.isEmpty()) return emptyList()
        val n = cast.size.coerceIn(1, 5)
        return cast.shuffled(Random(book.id.hashCode() xor channelName.hashCode())).take(n)
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

    /**
     * Drops a handful of pre-existing messages from the server's own cast into a fresh
     * channel, so it reads as a room with people already in it rather than a blank
     * "this is the start of #..." page. They are one-time history, not live chatters —
     * nothing keeps posting after this; the user's own messages pick up from here.
     */
    private suspend fun seedAmbientActivity(roomId: String, characters: List<RpCharacterEntity>, lines: List<String>) {
        if (characters.isEmpty() || lines.isEmpty()) return
        val now = System.currentTimeMillis()
        val speakers = characters.shuffled()
        lines.forEachIndexed { index, line ->
            val speaker = speakers[index % speakers.size]
            db.roleplayDao().upsertMessage(
                RpMessageEntity(
                    id = "rpm-${UUID.randomUUID()}",
                    chatId = roomId,
                    swipeGroupId = "sw-${UUID.randomUUID()}",
                    swipeIndex = 0,
                    isActiveSwipe = true,
                    role = "char",
                    speakerCharacterId = speaker.id,
                    contentJson = Document.fromPlainText(line).toJson(),
                    // Staggered into the recent past, oldest first, so they read top-to-
                    // bottom as a conversation that already happened before the user arrived.
                    createdAt = now - (lines.size - index) * AMBIENT_MESSAGE_SPACING_MS,
                    displayMode = "messenger",
                ),
            )
        }
    }

    private fun ambientGeneral(title: String): List<String> = listOf(
        "settling in over here, this is going to be fun",
        "same, glad this server for \"$title\" is up",
        "count me in whenever things kick off",
    )

    private fun ambientLore(title: String): List<String> = listOf(
        "been meaning to write up some background notes for \"$title\"",
        "yeah, there's a lot we still haven't nailed down",
        "drop anything you find in here, I'll keep it organized",
    )

    private fun ambientBrainstorm(title: String): List<String> = listOf(
        "dropping a few ideas here whenever they come to me",
        "love that direction, keep them coming",
    )

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

    companion object {
        private val SERVER_WORK_TYPES = setOf("novel", "campaign")
        private const val AMBIENT_MESSAGE_SPACING_MS = 15 * 60 * 1000L
    }
}
