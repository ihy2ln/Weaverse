package com.ihy2ln.weaverse.sync.novelcrafter

/**
 * Parsed Novelcrafter full-export ZIP (folder-per-codex-entry + novel.md **or** novel.docx
 * + chats/ + snippets/). Always imported as a **new** book.
 */
data class NovelcrafterParsedExport(
    val bookTitle: String,
    val author: String = "",
    val acts: List<NcAct> = emptyList(),
    val codexEntries: List<NcCodexEntry> = emptyList(),
    val chats: List<NcChat> = emptyList(),
    val snippets: List<NcSnippet> = emptyList(),
    val manuscriptSource: String = "novel.md",
)

data class NcAct(
    val title: String,
    val chapters: List<NcChapter> = emptyList(),
)

data class NcChapter(
    val title: String,
    val scenes: List<NcScene> = emptyList(),
)

data class NcScene(
    val title: String,
    val summary: String = "",
    val prose: String = "",
)

data class NcCodexEntry(
    val id: String,
    val categoryFolder: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val color: String? = null,
    val alwaysInclude: Boolean = false,
    val body: String = "",
    val notes: String = "",
)

data class NcChat(
    val id: String,
    val title: String,
    val favourite: Boolean = false,
    val messages: List<NcChatMessage> = emptyList(),
)

data class NcChatMessage(
    val role: String, // user | assistant
    val content: String,
)

data class NcSnippet(
    val id: String,
    val title: String,
    val body: String,
)

data class NovelcrafterImportCounts(
    val bookId: String,
    val bookTitle: String,
    val actCount: Int,
    val chapterCount: Int,
    val sceneCount: Int,
    val codexCount: Int,
    val chatCount: Int,
    val snippetCount: Int,
    val rpCharacterCount: Int = 0,
    val rpChatCount: Int = 0,
    val mediaCount: Int = 0,
)
