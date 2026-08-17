package com.ihy2ln.weaverse.data.export

import kotlinx.serialization.Serializable

enum class ExportFormat(val label: String, val extension: String) {
    Docx("Word (.docx)", "docx"),
    Html("HTML", "html"),
    Json("JSON", "json"),
    Markdown("Markdown (.md)", "md"),
    PlainText("Plain text (.txt)", "txt"),
    ProjectZip("Project ZIP", "zip"),
}

enum class SceneDivider(val label: String, val value: String) {
    Asterisks("Asterisks (* * *)", "* * *"),
    Hash("Hash (###)", "###"),
    Blank("Blank line", ""),
    None("None", "__NONE__"),
}

data class ExportOptions(
    val exportSummaries: Boolean = false,
    val exportProse: Boolean = true,
    val includeActTitles: Boolean = true,
    val includeSceneSubtitles: Boolean = true,
    val sceneDivider: SceneDivider = SceneDivider.Asterisks,
    val includeCodex: Boolean = false,
    val includeSnippets: Boolean = false,
    val includeChats: Boolean = false,
    val includePrompts: Boolean = false,
    val includeRoleplay: Boolean = false,
)

data class ExportSceneNode(
    val actId: String,
    val actTitle: String,
    val chapterId: String,
    val chapterTitle: String,
    val sceneId: String,
    val sceneTitle: String,
    val selected: Boolean = true,
)

@Serializable
data class ProjectBundle(
    val version: Int = 1,
    val kind: String = "novel",
    val exportedAt: Long = 0L,
    val book: BookDto? = null,
    val acts: List<ActDto> = emptyList(),
    val chapters: List<ChapterDto> = emptyList(),
    val scenes: List<SceneDto> = emptyList(),
    val codexCategories: List<CodexCategoryDto> = emptyList(),
    val codexEntries: List<CodexEntryDto> = emptyList(),
    val snippets: List<SnippetDto> = emptyList(),
    val chatThreads: List<ChatThreadDto> = emptyList(),
    val chatMessages: List<ChatMessageDto> = emptyList(),
    val promptFolders: List<PromptFolderDto> = emptyList(),
    val prompts: List<PromptDto> = emptyList(),
    val rpCharacters: List<RpCharacterDto> = emptyList(),
    val rpPersonas: List<RpPersonaDto> = emptyList(),
    val rpChats: List<RpChatDto> = emptyList(),
    val rpMessages: List<RpMessageDto> = emptyList(),
)

@Serializable
data class BookDto(
    val id: String,
    val seriesId: String? = null,
    val title: String,
    val genre: String = "",
    val pov: String = "",
    val tense: String = "",
    val styleGuide: String = "",
    val targetWordCount: Int = 0,
    val coverMediaId: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
data class ActDto(val id: String, val bookId: String, val title: String, val sortOrder: Int)

@Serializable
data class ChapterDto(
    val id: String,
    val actId: String,
    val title: String,
    val sortOrder: Int,
    val summary: String = "",
)

@Serializable
data class SceneDto(
    val id: String,
    val chapterId: String,
    val title: String,
    val sortOrder: Int,
    val docJson: String = "",
    val plainText: String = "",
    val summary: String = "",
    val beatsJson: String = "[]",
    val wordCount: Int = 0,
    val status: String = "draft",
    val pov: String = "",
    val povCharacterId: String? = null,
    val inWorldDate: String = "",
    val labelsJson: String = "[]",
    val colorHex: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
data class CodexCategoryDto(
    val id: String,
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

@Serializable
data class CodexEntryDto(
    val id: String,
    val categoryId: String,
    val scopeType: String,
    val scopeId: String,
    val name: String,
    val aliasesJson: String = "[]",
    val docJson: String = "",
    val plainText: String = "",
    val colorHex: String? = null,
    val alwaysInclude: Boolean = false,
    val disabled: Boolean = false,
    val imageMediaId: String? = null,
    val isAiGenerated: Boolean = false,
    val trackMentions: Boolean = true,
    val caseSensitiveMatching: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
data class SnippetDto(
    val id: String,
    val scopeType: String,
    val scopeId: String,
    val title: String,
    val body: String,
    val category: String = "",
    val pinned: Boolean = false,
    val createdAt: Long = 0,
)

@Serializable
data class ChatThreadDto(
    val id: String,
    val scopeId: String,
    val name: String,
    val pinned: Boolean = false,
    val promptId: String? = null,
    val modelRef: String = "",
    val sceneId: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
data class ChatMessageDto(
    val id: String,
    val threadId: String,
    val role: String,
    val contentJson: String,
    val contextUsedJson: String = "[]",
    val tokenCount: Int = 0,
    val wordCount: Int = 0,
    val createdAt: Long = 0,
)

@Serializable
data class PromptFolderDto(
    val id: String,
    val name: String,
    val type: String,
    val isSystem: Boolean = false,
)

@Serializable
data class PromptDto(
    val id: String,
    val folderId: String,
    val name: String,
    val type: String,
    val description: String = "",
    val instructionsJson: String = "[]",
    val advancedJson: String = "{}",
    val isSystem: Boolean = false,
    val isDefault: Boolean = false,
    val createdAt: Long = 0,
)

@Serializable
data class RpCharacterDto(
    val id: String,
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
    val createdAt: Long = 0,
)

@Serializable
data class RpPersonaDto(
    val id: String,
    val name: String,
    val avatarMediaId: String? = null,
    val description: String = "",
    val isDefault: Boolean = false,
)

@Serializable
data class RpChatDto(
    val id: String,
    val characterId: String? = null,
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
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
data class RpMessageDto(
    val id: String,
    val chatId: String,
    val swipeGroupId: String,
    val swipeIndex: Int,
    val isActiveSwipe: Boolean,
    val role: String,
    val speakerCharacterId: String? = null,
    val contentJson: String,
    val tokenCount: Int = 0,
    val isEdited: Boolean = false,
    val createdAt: Long = 0,
    /** messenger | dungeonMaster | roleplay; missing on older bundles → messenger. */
    val displayMode: String = "messenger",
)
