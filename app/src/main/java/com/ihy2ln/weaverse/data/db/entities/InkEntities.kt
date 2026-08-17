package com.ihy2ln.weaverse.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "series")
data class SeriesEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String = "",
    val premise: String = "",
    val rollingSummary: String = "",
    val summaryUpdatedAt: Long? = null,
    val createdAt: Long,
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
)

@Entity(tableName = "acts", indices = [Index("bookId")])
data class ActEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val title: String,
    val sortOrder: Int,
)

@Entity(tableName = "chapters", indices = [Index("actId")])
data class ChapterEntity(
    @PrimaryKey val id: String,
    val actId: String,
    val title: String,
    val sortOrder: Int,
    val summary: String = "",
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
    val createdAt: Long,
    val updatedAt: Long,
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
)

@Entity(tableName = "rp_personas")
data class RpPersonaEntity(
    @PrimaryKey val id: String,
    val name: String,
    val avatarMediaId: String? = null,
    val description: String = "",
    val isDefault: Boolean = false,
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
)

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
    val instructionsJson: String = "[]",
    val advancedJson: String = "{}",
    val isSystem: Boolean = false,
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
