package com.ihy2ln.weaverse.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "series")
data class SeriesEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String = "",
    val premise: String = "",
    val rollingSummary: String = "",
    val summaryUpdatedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long = 0L,
)

@Entity(tableName = "books", indices = [Index("seriesId")])
data class BookEntity(
    @PrimaryKey val id: String,
    val seriesId: String?,
    val title: String,
    val genre: String = "",
    val pov: String = "",
    val tense: String = "",
    val styleGuide: String = "",
    val targetWordCount: Int = 0,
    val coverMediaId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    /** novel | campaign | storyboard — keeps each workspace's library distinct. */
    val workType: String = "novel",
)

@Entity(tableName = "acts", indices = [Index("bookId")])
data class ActEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val title: String,
    val sortOrder: Int,
    val updatedAt: Long = 0L,
)

@Entity(tableName = "chapters", indices = [Index("actId")])
data class ChapterEntity(
    @PrimaryKey val id: String,
    val actId: String,
    val title: String,
    val sortOrder: Int,
    val summary: String = "",
    val updatedAt: Long = 0L,
)

@Entity(tableName = "scenes", indices = [Index("chapterId")])
data class SceneEntity(
    @PrimaryKey val id: String,
    val chapterId: String,
    val title: String,
    val sortOrder: Int,
    val docJson: String,
    val plainText: String,
    val summary: String = "",
    val beatsJson: String = "[]",
    val wordCount: Int = 0,
    val status: String = "draft",
    val pov: String = "",
    val povCharacterId: String? = null,
    val inWorldDate: String = "",
    val labelsJson: String = "[]",
    val colorHex: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "scene_codex_links",
    primaryKeys = ["sceneId", "entryId"],
)
data class SceneCodexLinkEntity(
    val sceneId: String,
    val entryId: String,
    val source: String = "manual",
    val updatedAt: Long = 0L,
)

@Entity(tableName = "scene_revisions", indices = [Index("sceneId")])
data class SceneRevisionEntity(
    @PrimaryKey val id: String,
    val sceneId: String,
    val createdAt: Long,
    val docJson: String,
    val plainText: String,
    val wordCount: Int = 0,
    val kind: String = "hourly",
)

@Entity(tableName = "codex_categories")
data class CodexCategoryEntity(
    @PrimaryKey val id: String,
    val scopeType: String,
    val scopeId: String,
    val name: String,
    val colorHex: String,
    val icon: String = "",
    val glyph: String = "",
    val sortOrder: Int = 0,
    val isSystem: Boolean = false,
    val isBuiltIn: Boolean = false,
    val updatedAt: Long = 0L,
)

@Entity(tableName = "codex_entries", indices = [Index("categoryId"), Index("scopeId")])
data class CodexEntryEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val scopeType: String,
    val scopeId: String,
    val name: String,
    val aliasesJson: String = "[]",
    val docJson: String,
    val plainText: String,
    val colorHex: String? = null,
    val alwaysInclude: Boolean = false,
    val disabled: Boolean = false,
    val imageMediaId: String? = null,
    val isAiGenerated: Boolean = false,
    /** Whether name/alias mentions of this entry are auto-highlighted as links and auto-detected for AI context. */
    val trackMentions: Boolean = true,
    /** Whether name/alias mentions of this entry are auto-highlighted as links and auto-detected for AI context. */
    val caseSensitiveMatching: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    /** RPG roster sheet (RpgCharacterSheet JSON) for Characters-style entries. */
    val sheetJson: String = "{}",
    /** RPG inventory (JSON-encoded List<RpItem>) so any codex entry can carry gear. */
    val inventoryJson: String = "[]",
)

@Entity(tableName = "codex_entries_lore")
data class CodexEntryLoreEntity(
    @PrimaryKey val entryId: String,
    val keysJson: String = "[]",
    val secondaryKeysJson: String = "[]",
    val selectiveLogic: String = "andAny",
    val insertionOrder: Int = 100,
    val position: String = "beforeChar",
    val depth: Int = 0,
    val probability: Int = 100,
    val isConstant: Boolean = false,
    val caseSensitive: Boolean = false,
    val matchWholeWords: Boolean = true,
    val scanDepth: Int = 2,
    val tokenBudgetWeight: Float = 1f,
    val recursionAllowed: Boolean = true,
    val groupName: String = "",
    val updatedAt: Long = 0L,
)

@Entity(tableName = "snippets")
data class SnippetEntity(
    @PrimaryKey val id: String,
    val scopeType: String,
    val scopeId: String,
    val title: String,
    val body: String,
    val category: String = "",
    val pinned: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long = 0L,
)

@Entity(tableName = "chat_threads")
data class ChatThreadEntity(
    @PrimaryKey val id: String,
    val scopeId: String,
    val name: String,
    val pinned: Boolean = false,
    val promptId: String? = null,
    val modelRef: String = "",
    val sceneId: String? = null,
    /** Brainstorm sub-categories: non-null nests this thread under a parent. */
    val parentThreadId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "chat_messages", indices = [Index("threadId")])
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val threadId: String,
    val role: String,
    val contentJson: String,
    val contextUsedJson: String = "[]",
    val tokenCount: Int = 0,
    val wordCount: Int = 0,
    val createdAt: Long,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val costUsd: Double = 0.0,
)

@Entity(tableName = "rp_characters")
data class RpCharacterEntity(
    @PrimaryKey val id: String,
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
    val alternateGreetingsJson: String = "[]",
    val tagsJson: String = "[]",
    val characterVersion: String = "2.0",
    val extensionsJson: String = "{}",
    val defaultCodexId: String? = null,
    val colorHex: String? = null,
    val createdAt: Long,
    /** JSON-encoded List<RpItem> this character is carrying. */
    val inventoryJson: String = "[]",
    /** In the player's immediate team. The wider cast lives in Lore. */
    val inParty: Boolean = false,
    /** Equipped item per slot, keyed by RpEquipSlot.name. */
    val equipmentJson: String = "{}",
    val updatedAt: Long = 0L,
)

/** One carried item. System-agnostic on purpose — no rules engine behind it. */
@Serializable
data class RpItem(
    val id: String,
    val name: String,
    val quantity: Int = 1,
    val notes: String = "",
    /** Per-item weight used by the compact tabletop inventory ledger. */
    val weightLb: Double = 0.0,
    /** Per-item value in gold pieces; currency conversion stays campaign-specific. */
    val costGp: Double = 0.0,
    /** Comma-separated searchable labels such as Utility, Combat, or Consumable. */
    val tags: String = "",
    /** Whether an attunement-style item is currently bonded to the carrier. */
    val attuned: Boolean = false,
    /** Quick active/equipped marker shown in the inventory table. */
    val active: Boolean = false,
    /** Optional imported equipment/accessory illustration. */
    val imageMediaId: String? = null,
    /** Matches a functional equipment slot or Pack item. */
    val template: String = "Pack item",
    /** Space consumed per item while carried in a backpack. */
    val slotSize: Int = 1,
    /** Positive only for backpack items; becomes active when equipped. */
    val backpackCapacity: Int = 0,
)

private val itemsJsonCodec = Json { ignoreUnknownKeys = true }

fun decodeItems(json: String): List<RpItem> =
    runCatching { itemsJsonCodec.decodeFromString<List<RpItem>>(json) }.getOrDefault(emptyList())

fun encodeItems(items: List<RpItem>): String = itemsJsonCodec.encodeToString(items)

/** The equipment slots a character plate shows. */
enum class RpEquipSlot(val label: String) {
    Head("Head"),
    Torso("Torso"),
    Arms("Arms"),
    Legs("Legs"),
    Weapon("Weapon"),
    Accessory("Accessory"),
    Backpack("Backpack"),
}

fun decodeEquipment(json: String): Map<String, String> =
    runCatching { itemsJsonCodec.decodeFromString<Map<String, String>>(json) }
        .getOrDefault(emptyMap())

fun encodeEquipment(equipment: Map<String, String>): String =
    itemsJsonCodec.encodeToString(equipment.filterValues { it.isNotBlank() })

@Entity(tableName = "rp_personas")
data class RpPersonaEntity(
    @PrimaryKey val id: String,
    val name: String,
    val avatarMediaId: String? = null,
    val description: String = "",
    val isDefault: Boolean = false,
    /** You carry gear the same way the rest of the team does. */
    val inventoryJson: String = "[]",
    val equipmentJson: String = "{}",
    val updatedAt: Long = 0L,
)

@Entity(tableName = "rp_chats")
data class RpChatEntity(
    @PrimaryKey val id: String,
    val characterId: String?,
    val groupId: String? = null,
    val personaId: String,
    val title: String,
    val backgroundMediaId: String? = null,
    val authorsNote: String = "",
    val authorsNoteDepth: Int = 4,
    val presetId: String? = null,
    val promptTemplateId: String? = null,
    val branchOfChatId: String? = null,
    val displayMode: String = "messenger",
    val narrationColorHex: String? = null,
    val speechColorHex: String? = null,
    val oocColorHex: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    /** JSON-encoded List<RpPageMeta> — storyboard pages for the DM/Roleplay canvases. */
    val pagesJson: String = "[]",
    /** When the user last opened this chat — drives the unread badge. */
    val lastReadAt: Long = 0L,
    /** Manuscript that owns this campaign/storyboard chat, when applicable. */
    val bookId: String? = null,
    /**
     * Chatting-mode Discord room kind: "" = legacy messenger chat (reads as a DM),
     * "channel" = work text channel, "character" = per-character room inside a
     * work's server, "dm" = direct message conversation.
     */
    val roomKind: String = "",
)

/** One storyboard page within a roleplay chat's DM/Roleplay canvas. */
@Serializable
data class RpPageMeta(
    val id: String,
    val order: Int,
    val title: String? = null,
    /** Layout whose slot outlines this page shows; media drops into the slots. */
    val templateId: String = "classic-6",
)

private val pagesJsonCodec = Json { ignoreUnknownKeys = true }

fun decodePages(json: String): List<RpPageMeta> =
    runCatching { pagesJsonCodec.decodeFromString<List<RpPageMeta>>(json) }
        .getOrDefault(emptyList())
        .sortedBy { it.order }

fun encodePages(pages: List<RpPageMeta>): String = pagesJsonCodec.encodeToString(pages)

@Entity(tableName = "rp_messages", indices = [Index("chatId"), Index(value = ["chatId", "displayMode"])])
data class RpMessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val swipeGroupId: String,
    val swipeIndex: Int,
    val isActiveSwipe: Boolean,
    val role: String,
    val speakerCharacterId: String? = null,
    val contentJson: String,
    val tokenCount: Int = 0,
    val isEdited: Boolean = false,
    val createdAt: Long,
    /** messenger | dungeonMaster | roleplay — content is isolated per mode. */
    val displayMode: String = "messenger",
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val costUsd: Double = 0.0,
)

@Entity(tableName = "media")
data class MediaEntity(
    @PrimaryKey val id: String,
    val type: String,
    val relativePath: String,
    val mimeType: String,
    val byteSize: Long,
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long? = null,
    val thumbnailPath: String? = null,
    val checksum: String = "",
    /** Human-readable Pictures-library title. */
    val displayName: String = "",
    /** User-facing organizational category, for example Adams Haven / Scene / Farm. */
    val category: String = "",
    /** Comma-separated machine-searchable labels used by scene selection and AI context. */
    val tags: String = "",
    val createdAt: Long,
)

@Entity(tableName = "prompt_folders")
data class PromptFolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val isSystem: Boolean = false,
)

@Entity(tableName = "prompts")
data class PromptEntity(
    @PrimaryKey val id: String,
    val folderId: String,
    val name: String,
    val type: String,
    val description: String = "",
    /** JSON-encoded List<PromptMessage> (role + content per message box). */
    val instructionsJson: String = "[]",
    val advancedJson: String = "{}",
    val isSystem: Boolean = false,
    /** Whether this is the active prompt for its type when more than one exists. */
    val isDefault: Boolean = false,
    val createdAt: Long,
)

@Entity(tableName = "ai_profiles")
data class AiProfileEntity(
    @PrimaryKey val id: String,
    val providerType: String,
    val label: String,
    val baseUrl: String = "",
    val favoriteModelsJson: String = "[]",
    val isDefault: Boolean = false,
)
