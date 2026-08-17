package com.ihy2ln.weaverse.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "rp_characters")
data class RpCharacterEntity(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val avatarMediaId: String? = null,
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val firstMes: String = "",
    val mesExample: String = "",
    val creatorNotes: String = "",
    val systemPrompt: String = "",
    val postHistoryInstructions: String = "",
    val alternateGreetings: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val characterVersion: String = "1.0",
    /** V2 card "extensions" object, passed through verbatim on import/export. */
    val extensionsJson: String = "{}",
    val defaultCodexCategoryId: String? = null,
    val colorHex: String? = null,
    val createdAt: Long = nowEpochMillis(),
)

@Entity(tableName = "rp_personas")
data class RpPersonaEntity(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val avatarMediaId: String? = null,
    val description: String = "",
    val isDefault: Boolean = false,
)

@Entity(tableName = "rp_groups")
data class RpGroupEntity(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val activationStrategy: ActivationStrategy = ActivationStrategy.Natural,
    val autoModeDelayMs: Long = 3000L,
)

@Entity(
    tableName = "rp_group_members",
    primaryKeys = ["groupId", "characterId"],
    foreignKeys = [
        ForeignKey(
            entity = RpGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RpCharacterEntity::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("characterId")],
)
data class RpGroupMemberEntity(
    val groupId: String,
    val characterId: String,
    val talkativeness: Float = 0.5f,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "rp_chats",
    foreignKeys = [
        ForeignKey(
            entity = RpCharacterEntity::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RpGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RpPersonaEntity::class,
            parentColumns = ["id"],
            childColumns = ["personaId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PresetEntity::class,
            parentColumns = ["id"],
            childColumns = ["presetId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("characterId"), Index("groupId"), Index("personaId"), Index("presetId"), Index("branchOfChatId")],
)
data class RpChatEntity(
    @PrimaryKey val id: String = newId(),
    val characterId: String? = null,
    val groupId: String? = null,
    val personaId: String,
    val title: String,
    val backgroundMediaId: String? = null,
    val authorsNote: String = "",
    val authorsNoteDepth: Int = 4,
    val presetId: String? = null,
    /** Instruct/context template id (ChatML, Llama 3, Mistral, ...) — real registry in Phase 11. */
    val promptTemplateId: String? = null,
    val branchOfChatId: String? = null,
    /** Revision 02 §9: messenger (bubbles) vs dungeon-master (full-width prose) — presentation
     * plus a prompt-template swap, not a data change; switching re-renders existing messages. */
    val displayMode: RpDisplayMode = RpDisplayMode.Messenger,
    /** Only meaningful in [RpDisplayMode.DungeonMaster] — colour for `*asterisked action text*`,
     * `"quoted speech"`, and `[bracketed OOC]` respectively. Null falls back to a theme default. */
    val narrationColorHex: String? = null,
    val speechColorHex: String? = null,
    val oocColorHex: String? = null,
    val createdAt: Long = nowEpochMillis(),
    val updatedAt: Long = nowEpochMillis(),
)

@Entity(
    tableName = "rp_messages",
    foreignKeys = [
        ForeignKey(
            entity = RpChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RpCharacterEntity::class,
            parentColumns = ["id"],
            childColumns = ["speakerCharacterId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("chatId"), Index("swipeGroupId"), Index("speakerCharacterId")],
)
data class RpMessageEntity(
    @PrimaryKey val id: String = newId(),
    val chatId: String,
    /** Groups alternate generations of "the same" reply together for swipe cycling. */
    val swipeGroupId: String,
    val swipeIndex: Int = 0,
    val isActiveSwipe: Boolean = true,
    val role: RpMessageRole,
    val speakerCharacterId: String? = null,
    /** Serialized [com.ihy2ln.weaverse.core.text.Document] JSON (blocks, so images work) — Phase 5. */
    val contentJson: String = "",
    /** Plain-text extraction of [contentJson], indexed by `RpMessageFts` (spec §4 Search). */
    val plainText: String = "",
    val tokenCount: Int = 0,
    val isEdited: Boolean = false,
    val createdAt: Long = nowEpochMillis(),
)

@Entity(
    tableName = "rp_expressions",
    foreignKeys = [
        ForeignKey(
            entity = RpCharacterEntity::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("characterId")],
)
data class RpExpressionEntity(
    @PrimaryKey val id: String = newId(),
    val characterId: String,
    val label: String,
    val mediaId: String,
)
