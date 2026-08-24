package com.ihy2ln.weaverse.data.export

import android.content.Context
import android.net.Uri
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.ActEntity
import com.ihy2ln.weaverse.data.db.entities.BookEntity
import com.ihy2ln.weaverse.data.db.entities.ChapterEntity
import com.ihy2ln.weaverse.data.db.entities.ChatMessageEntity
import com.ihy2ln.weaverse.data.db.entities.ChatThreadEntity
import com.ihy2ln.weaverse.data.db.entities.CodexCategoryEntity
import com.ihy2ln.weaverse.data.db.entities.CodexEntryEntity
import com.ihy2ln.weaverse.data.db.entities.PromptEntity
import com.ihy2ln.weaverse.data.db.entities.PromptFolderEntity
import com.ihy2ln.weaverse.data.db.entities.RpCharacterEntity
import com.ihy2ln.weaverse.data.db.entities.RpChatEntity
import com.ihy2ln.weaverse.data.db.entities.RpMessageEntity
import com.ihy2ln.weaverse.data.db.entities.RpPersonaEntity
import com.ihy2ln.weaverse.data.db.entities.SceneEntity
import com.ihy2ln.weaverse.data.db.entities.SnippetEntity
import com.ihy2ln.weaverse.data.export.novelcrafter.NovelcrafterImporter
import com.ihy2ln.weaverse.data.export.novelcrafter.NovelcrafterZipParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class ImportOutcome(
    val message: String,
    val newBookId: String? = null,
)

@Singleton
class ProjectExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: WeaverseDatabase,
    private val novelcrafterImporter: NovelcrafterImporter,
    private val manuscriptFormatImporter: ManuscriptFormatImporter,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val exportDir get() = File(context.filesDir, "exports").also { it.mkdirs() }

    suspend fun loadSceneTree(bookId: String): List<ExportSceneNode> = withContext(Dispatchers.IO) {
        val acts = db.manuscriptDao().getActs(bookId)
        buildList {
            for (act in acts) {
                val chapters = db.manuscriptDao().getChapters(act.id)
                for (chapter in chapters) {
                    val scenes = db.manuscriptDao().getScenes(chapter.id)
                    for (scene in scenes) {
                        add(
                            ExportSceneNode(
                                actId = act.id,
                                actTitle = act.title,
                                chapterId = chapter.id,
                                chapterTitle = chapter.title,
                                sceneId = scene.id,
                                sceneTitle = scene.title,
                                selected = true,
                            ),
                        )
                    }
                }
            }
        }
    }

    suspend fun exportNovel(
        bookId: String,
        format: ExportFormat,
        selectedSceneIds: Set<String>,
        options: ExportOptions,
    ): String = withContext(Dispatchers.IO) {
        val bundle = buildNovelBundle(bookId, selectedSceneIds, options)
        val safeTitle = (bundle.book?.title ?: "novel")
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(48)
        val timestamp = System.currentTimeMillis()
        when (format) {
            ExportFormat.Json -> {
                val file = File(exportDir, "$safeTitle-$timestamp.json")
                file.writeText(json.encodeToString(bundle))
                file.absolutePath
            }
            ExportFormat.ProjectZip -> {
                val file = File(exportDir, "$safeTitle-$timestamp.zip")
                ZipOutputStream(FileOutputStream(file)).use { zip ->
                    zip.putNextEntry(ZipEntry("project.json"))
                    zip.write(json.encodeToString(bundle).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                    zip.putNextEntry(ZipEntry("manuscript.md"))
                    zip.write(renderManuscript(bundle, options, forMarkdown = true).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
                file.absolutePath
            }
            ExportFormat.Markdown -> {
                val file = File(exportDir, "$safeTitle-$timestamp.md")
                file.writeText(renderManuscript(bundle, options, forMarkdown = true))
                file.absolutePath
            }
            ExportFormat.Html -> {
                val file = File(exportDir, "$safeTitle-$timestamp.html")
                file.writeText(renderHtml(bundle, options))
                file.absolutePath
            }
            ExportFormat.PlainText -> {
                val file = File(exportDir, "$safeTitle-$timestamp.txt")
                file.writeText(renderManuscript(bundle, options, forMarkdown = false))
                file.absolutePath
            }
            ExportFormat.Docx -> {
                val file = File(exportDir, "$safeTitle-$timestamp.docx")
                writeMinimalDocx(file, renderManuscript(bundle, options, forMarkdown = false))
                file.absolutePath
            }
        }
    }

    suspend fun exportRoleplay(options: ExportOptions = ExportOptions(includeRoleplay = true)): String =
        withContext(Dispatchers.IO) {
            val bundle = buildRoleplayBundle()
            val file = File(exportDir, "roleplay-${System.currentTimeMillis()}.json")
            file.writeText(json.encodeToString(bundle))
            file.absolutePath
        }

    suspend fun exportNotes(): String = withContext(Dispatchers.IO) {
        val notes = db.snippetDao().getByCategory("notes")
        val bundle = ProjectBundle(
            version = 1,
            kind = "notes",
            exportedAt = System.currentTimeMillis(),
            snippets = notes.map { it.toDto() },
        )
        val file = File(exportDir, "notes-${System.currentTimeMillis()}.json")
        file.writeText(json.encodeToString(bundle))
        file.absolutePath
    }

    suspend fun importFromUri(uri: Uri): ImportOutcome = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Could not open selected file")
        val mime = context.contentResolver.getType(uri).orEmpty().lowercase()
        val name = (uri.lastPathSegment ?: "").lowercase()
        val hint = when {
            mime.contains("wordprocessingml") ->
                if (name.endsWith(".docx")) name else "$name.docx"
            mime == "application/msword" || (name.endsWith(".doc") && !name.endsWith(".docx")) ->
                if (name.endsWith(".doc")) name else "$name.doc"
            mime.contains("html") -> if (name.endsWith(".html") || name.endsWith(".htm")) name else "$name.html"
            mime.contains("markdown") || mime == "text/markdown" ->
                if (name.endsWith(".md")) name else "$name.md"
            mime.contains("json") -> if (name.endsWith(".json")) name else "$name.json"
            mime.contains("zip") -> if (name.endsWith(".zip")) name else "$name.zip"
            else -> name
        }
        importBytes(bytes, displayName = hint)
    }

    suspend fun importFromFile(file: File): ImportOutcome = withContext(Dispatchers.IO) {
        importBytes(file.readBytes(), displayName = file.name.lowercase())
    }

    private suspend fun importBytes(bytes: ByteArray, displayName: String): ImportOutcome {
        val lower = displayName.lowercase()
        return when {
            lower.endsWith(".doc") && !lower.endsWith(".docx") ->
                error("Legacy Word .doc is not supported. Export as .docx, Markdown, HTML, or JSON.")
            lower.endsWith(".docx") || (looksLikeZip(bytes) && lower.contains("docx")) -> {
                val result = manuscriptFormatImporter.importDocx(bytes, suggestedTitle = titleFromName(displayName))
                ImportOutcome(
                    "Imported Word (.docx) as new book “${result.bookTitle}” (${result.sceneCount} scenes)",
                    result.bookId,
                )
            }
            lower.endsWith(".html") || lower.endsWith(".htm") -> {
                val result = manuscriptFormatImporter.importHtml(bytes, suggestedTitle = titleFromName(displayName))
                ImportOutcome(
                    "Imported HTML as new book “${result.bookTitle}” (${result.sceneCount} scenes)",
                    result.bookId,
                )
            }
            lower.endsWith(".md") || lower.endsWith(".markdown") -> {
                val result = manuscriptFormatImporter.importMarkdown(bytes, suggestedTitle = titleFromName(displayName))
                ImportOutcome(
                    "Imported Markdown as new book “${result.bookTitle}” (${result.sceneCount} scenes)",
                    result.bookId,
                )
            }
            lower.endsWith(".zip") || looksLikeZip(bytes) -> importZipBytes(bytes)
            lower.endsWith(".json") || looksLikeJson(bytes) -> {
                val text = bytes.toString(Charsets.UTF_8)
                val bundle = json.decodeFromString<ProjectBundle>(text)
                importBundle(bundle)
                ImportOutcome("Imported JSON project (${bundle.kind})", bundle.book?.id)
            }
            else -> {
                // Probe: zip / json / markdown
                when {
                    looksLikeZip(bytes) -> importZipBytes(bytes)
                    looksLikeJson(bytes) -> {
                        val bundle = json.decodeFromString<ProjectBundle>(bytes.toString(Charsets.UTF_8))
                        importBundle(bundle)
                        ImportOutcome("Imported JSON project (${bundle.kind})", bundle.book?.id)
                    }
                    else -> {
                        val result = manuscriptFormatImporter.importMarkdown(
                            bytes,
                            suggestedTitle = titleFromName(displayName),
                        )
                        ImportOutcome(
                            "Imported text/Markdown as new book “${result.bookTitle}”",
                            result.bookId,
                        )
                    }
                }
            }
        }
    }

    private fun titleFromName(name: String): String =
        name.substringAfterLast('/')
            .substringAfterLast(':')
            .substringBeforeLast('.')
            .ifBlank { "Imported" }

    private fun looksLikeZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()

    private fun looksLikeJson(bytes: ByteArray): Boolean {
        val start = bytes.take(64).toByteArray().toString(Charsets.UTF_8).trimStart()
        return start.startsWith("{") || start.startsWith("[")
    }

    private suspend fun importZipBytes(bytes: ByteArray): ImportOutcome {
        if (NovelcrafterZipParser.looksLikeNovelcrafterZipBytes(bytes)) {
            val parsed = NovelcrafterZipParser.parse(bytes)
            val result = novelcrafterImporter.import(parsed)
            return ImportOutcome(
                "Imported Novelcrafter ZIP as new book “${result.bookTitle}” — " +
                    "${result.sceneCount} scenes, ${result.codexCount} codex, " +
                    "${result.chatCount} chats, ${result.rpCharacterCount} RP characters, " +
                    "${result.rpChatCount} RP chats, ${result.mediaCount} pictures",
                result.bookId,
            )
        }
        var projectJson: String? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val entryName = entry.name.replace('\\', '/')
                    if (entryName == "project.json" || entryName.endsWith("/project.json")) {
                        projectJson = zip.readBytes().toString(Charsets.UTF_8)
                        break
                    }
                    if (entryName.endsWith(".json")) {
                        val text = zip.readBytes().toString(Charsets.UTF_8)
                        if (text.contains("\"kind\"") || text.contains("\"scenes\"") || text.contains("\"rpChats\"")) {
                            projectJson = text
                            break
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val text = projectJson
            ?: error(
                "ZIP not recognized. Supported: Weaverse project.zip (project.json), " +
                    "Novelcrafter full export (novel.md or novel.docx + characters/…).",
            )
        val bundle = json.decodeFromString<ProjectBundle>(text)
        importBundle(bundle)
        return ImportOutcome("Imported Weaverse project ZIP (${bundle.kind})", bundle.book?.id)
    }

    private suspend fun buildNovelBundle(
        bookId: String,
        selectedSceneIds: Set<String>,
        options: ExportOptions,
    ): ProjectBundle {
        val book = db.bookDao().getById(bookId) ?: error("Book not found")
        val acts = db.manuscriptDao().getActs(bookId)
        val chapters = acts.flatMap { db.manuscriptDao().getChapters(it.id) }
        val scenes = chapters
            .flatMap { db.manuscriptDao().getScenes(it.id) }
            .filter { it.id in selectedSceneIds }
        val usedChapterIds = scenes.map { it.chapterId }.toSet()
        val usedActIds = chapters.filter { it.id in usedChapterIds }.map { it.actId }.toSet()

        val codexCategories = if (options.includeCodex) db.codexDao().getAllCategories() else emptyList()
        val codexEntries = if (options.includeCodex) db.codexDao().getAllEntries() else emptyList()
        val snippets = if (options.includeSnippets) db.snippetDao().get(bookId) else emptyList()
        val threads = if (options.includeChats) db.workshopChatDao().getThreads(bookId) else emptyList()
        val messages = if (options.includeChats) {
            threads.flatMap { db.workshopChatDao().getMessages(it.id) }
        } else {
            emptyList()
        }
        val promptFolders = if (options.includePrompts) db.promptDao().getFolders() else emptyList()
        val prompts = if (options.includePrompts) db.promptDao().getAll() else emptyList()

        val rpBundle = if (options.includeRoleplay) buildRoleplayBundle() else ProjectBundle(kind = "roleplay")

        return ProjectBundle(
            version = 1,
            kind = if (options.includeRoleplay) "full" else "novel",
            exportedAt = System.currentTimeMillis(),
            book = book.toDto(),
            acts = acts.filter { it.id in usedActIds }.map { it.toDto() },
            chapters = chapters.filter { it.id in usedChapterIds }.map { it.toDto() },
            scenes = scenes.map { it.toDto() },
            codexCategories = codexCategories.map { it.toDto() },
            codexEntries = codexEntries.map { it.toDto() },
            snippets = snippets.map { it.toDto() },
            chatThreads = threads.map { it.toDto() },
            chatMessages = messages.map { it.toDto() },
            promptFolders = promptFolders.map { it.toDto() },
            prompts = prompts.map { it.toDto() },
            rpCharacters = rpBundle.rpCharacters,
            rpPersonas = rpBundle.rpPersonas,
            rpChats = rpBundle.rpChats,
            rpMessages = rpBundle.rpMessages,
        )
    }

    private suspend fun buildRoleplayBundle(): ProjectBundle {
        val characters = db.roleplayDao().getCharacters()
        val personas = db.roleplayDao().getPersonas()
        val chats = db.roleplayDao().getChats()
        val messages = chats.flatMap { db.roleplayDao().getMessages(it.id) }
        return ProjectBundle(
            version = 1,
            kind = "roleplay",
            exportedAt = System.currentTimeMillis(),
            rpCharacters = characters.map { it.toDto() },
            rpPersonas = personas.map { it.toDto() },
            rpChats = chats.map { it.toDto() },
            rpMessages = messages.map { it.toDto() },
        )
    }

    private suspend fun importBundle(bundle: ProjectBundle) {
        bundle.book?.let { db.bookDao().upsert(it.toEntity()) }
        bundle.acts.forEach { db.manuscriptDao().upsertAct(it.toEntity()) }
        bundle.chapters.forEach { db.manuscriptDao().upsertChapter(it.toEntity()) }
        bundle.scenes.forEach { db.manuscriptDao().upsertScene(it.toEntity()) }
        bundle.codexCategories.forEach { db.codexDao().upsertCategory(it.toEntity()) }
        bundle.codexEntries.forEach { db.codexDao().upsertEntry(it.toEntity()) }
        bundle.snippets.forEach { db.snippetDao().upsert(it.toEntity()) }
        bundle.chatThreads.forEach { db.workshopChatDao().upsertThread(it.toEntity()) }
        bundle.chatMessages.forEach { db.workshopChatDao().upsertMessage(it.toEntity()) }
        bundle.promptFolders.forEach { db.promptDao().upsertFolder(it.toEntity()) }
        bundle.prompts.forEach { db.promptDao().upsert(it.toEntity()) }
        bundle.rpCharacters.forEach { db.roleplayDao().upsertCharacter(it.toEntity()) }
        bundle.rpPersonas.forEach { db.roleplayDao().upsertPersona(it.toEntity()) }
        bundle.rpChats.forEach { db.roleplayDao().upsertChat(it.toEntity()) }
        bundle.rpMessages.forEach { db.roleplayDao().upsertMessage(it.toEntity()) }
    }

    private fun renderManuscript(
        bundle: ProjectBundle,
        options: ExportOptions,
        forMarkdown: Boolean,
    ): String = buildString {
        val bookTitle = bundle.book?.title.orEmpty()
        if (bookTitle.isNotBlank()) {
            if (forMarkdown) append("# ").append(bookTitle).append("\n\n")
            else append(bookTitle).append("\n\n")
        }
        val acts = bundle.acts.sortedBy { it.sortOrder }
        val chaptersByAct = bundle.chapters.groupBy { it.actId }
        val scenesByChapter = bundle.scenes.groupBy { it.chapterId }
        var firstScene = true
        for (act in acts) {
            if (options.includeActTitles) {
                if (forMarkdown) append("## ").append(act.title).append("\n\n")
                else append(act.title).append("\n\n")
            }
            val chapters = chaptersByAct[act.id].orEmpty().sortedBy { it.sortOrder }
            for (chapter in chapters) {
                if (forMarkdown) append("### ").append(chapter.title).append("\n\n")
                else append(chapter.title).append("\n\n")
                if (options.exportSummaries && chapter.summary.isNotBlank()) {
                    append(chapter.summary).append("\n\n")
                }
                val scenes = scenesByChapter[chapter.id].orEmpty().sortedBy { it.sortOrder }
                for (scene in scenes) {
                    if (!firstScene && options.sceneDivider != SceneDivider.None) {
                        val div = options.sceneDivider.value
                        if (div.isBlank()) append("\n") else append(div).append("\n\n")
                    }
                    firstScene = false
                    if (options.includeSceneSubtitles) {
                        if (forMarkdown) append("#### ").append(scene.title).append("\n\n")
                        else append(scene.title).append("\n\n")
                    }
                    if (options.exportSummaries && scene.summary.isNotBlank()) {
                        append(scene.summary).append("\n\n")
                    }
                    if (options.exportProse && scene.plainText.isNotBlank()) {
                        append(scene.plainText.trim()).append("\n\n")
                    }
                }
            }
        }
        if (options.includeCodex && bundle.codexEntries.isNotEmpty()) {
            append(if (forMarkdown) "## Codex\n\n" else "Codex\n\n")
            bundle.codexEntries.sortedBy { it.name }.forEach { entry ->
                append(if (forMarkdown) "### " else "").append(entry.name).append("\n\n")
                append(entry.plainText.trim()).append("\n\n")
            }
        }
    }

    private fun renderHtml(bundle: ProjectBundle, options: ExportOptions): String {
        val body = renderManuscript(bundle, options, forMarkdown = false)
            .split("\n\n")
            .joinToString("\n") { para ->
                val escaped = para
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                "<p>${escaped.replace("\n", "<br/>")}</p>"
            }
        val title = bundle.book?.title?.replace("<", "&lt;").orEmpty()
        return """
            <!DOCTYPE html>
            <html><head><meta charset="utf-8"/><title>$title</title></head>
            <body>
            <h1>$title</h1>
            $body
            </body></html>
        """.trimIndent()
    }

    private fun writeMinimalDocx(file: File, plainText: String) {
        val paragraphs = plainText.split("\n\n").filter { it.isNotBlank() }
        val documentXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">""")
            append("<w:body>")
            paragraphs.forEach { para ->
                append("<w:p><w:r><w:t xml:space=\"preserve\">")
                append(
                    para.replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                        .replace("\n", " "),
                )
                append("</w:t></w:r></w:p>")
            }
            append("<w:sectPr/></w:body></w:document>")
        }
        val contentTypes = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
            </Types>
        """.trimIndent()
        val rels = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
            </Relationships>
        """.trimIndent()
        ZipOutputStream(BufferedOutputStream(FileOutputStream(file))).use { zip ->
            fun putStored(name: String, data: ByteArray) {
                val entry = ZipEntry(name)
                entry.method = ZipEntry.DEFLATED
                zip.putNextEntry(entry)
                zip.write(data)
                zip.closeEntry()
            }
            putStored("[Content_Types].xml", contentTypes.toByteArray(Charsets.UTF_8))
            putStored("_rels/.rels", rels.toByteArray(Charsets.UTF_8))
            putStored("word/document.xml", documentXml.toByteArray(Charsets.UTF_8))
        }
    }
}

// --- Entity ↔ DTO mappers ---

private fun BookEntity.toDto() = BookDto(
    id = id, seriesId = seriesId, title = title, genre = genre, pov = pov,
    povCharacterName = povCharacterName, premise = premise, tense = tense, styleGuide = styleGuide,
    targetWordCount = targetWordCount, coverMediaId = coverMediaId, createdAt = createdAt, updatedAt = updatedAt,
)
private fun BookDto.toEntity() = BookEntity(
    id = id, seriesId = seriesId, title = title, genre = genre, pov = pov,
    povCharacterName = povCharacterName, premise = premise, tense = tense, styleGuide = styleGuide,
    targetWordCount = targetWordCount, coverMediaId = coverMediaId, createdAt = createdAt, updatedAt = updatedAt,
)
private fun ActEntity.toDto() = ActDto(id, bookId, title, sortOrder)
private fun ActDto.toEntity() = ActEntity(id, bookId, title, sortOrder)
private fun ChapterEntity.toDto() = ChapterDto(id, actId, title, sortOrder, summary)
private fun ChapterDto.toEntity() = ChapterEntity(id, actId, title, sortOrder, summary)
private fun SceneEntity.toDto() = SceneDto(
    id, chapterId, title, sortOrder, docJson, plainText, summary, beatsJson, wordCount, status,
    pov, povCharacterId, inWorldDate, labelsJson, colorHex, createdAt, updatedAt,
)
private fun SceneDto.toEntity() = SceneEntity(
    id, chapterId, title, sortOrder, docJson, plainText, summary, beatsJson, wordCount, status,
    pov, povCharacterId, inWorldDate, labelsJson, colorHex, createdAt, updatedAt,
)
private fun CodexCategoryEntity.toDto() = CodexCategoryDto(
    id, scopeType, scopeId, name, colorHex, icon, glyph, sortOrder, isSystem, isBuiltIn,
)
private fun CodexCategoryDto.toEntity() = CodexCategoryEntity(
    id, scopeType, scopeId, name, colorHex, icon, glyph, sortOrder, isSystem, isBuiltIn,
)
private fun CodexEntryEntity.toDto() = CodexEntryDto(
    id, categoryId, scopeType, scopeId, name, aliasesJson, docJson, plainText, colorHex,
    alwaysInclude, disabled, imageMediaId, isAiGenerated, trackMentions, caseSensitiveMatching,
    createdAt, updatedAt,
)
private fun CodexEntryDto.toEntity() = CodexEntryEntity(
    id, categoryId, scopeType, scopeId, name, aliasesJson, docJson, plainText, colorHex,
    alwaysInclude, disabled, imageMediaId, isAiGenerated, trackMentions, caseSensitiveMatching,
    createdAt, updatedAt,
)
private fun SnippetEntity.toDto() = SnippetDto(id, scopeType, scopeId, title, body, category, pinned, createdAt)
private fun SnippetDto.toEntity() = SnippetEntity(id, scopeType, scopeId, title, body, category, pinned, createdAt)
private fun ChatThreadEntity.toDto() = ChatThreadDto(
    id, scopeId, name, pinned, promptId, modelRef, sceneId, createdAt, updatedAt,
)
private fun ChatThreadDto.toEntity() = ChatThreadEntity(
    id, scopeId, name, pinned, promptId, modelRef, sceneId, createdAt, updatedAt,
)
private fun ChatMessageEntity.toDto() = ChatMessageDto(
    id, threadId, role, contentJson, contextUsedJson, tokenCount, wordCount, createdAt,
)
private fun ChatMessageDto.toEntity() = ChatMessageEntity(
    id, threadId, role, contentJson, contextUsedJson, tokenCount, wordCount, createdAt,
)
private fun PromptFolderEntity.toDto() = PromptFolderDto(id, name, type, isSystem)
private fun PromptFolderDto.toEntity() = PromptFolderEntity(id, name, type, isSystem)
private fun PromptEntity.toDto() = PromptDto(
    id, folderId, name, type, description, instructionsJson, advancedJson, isSystem, isDefault, createdAt,
)
private fun PromptDto.toEntity() = PromptEntity(
    id, folderId, name, type, description, instructionsJson, advancedJson, isSystem, isDefault, createdAt,
)
private fun RpCharacterEntity.toDto() = RpCharacterDto(
    id, name, avatarMediaId, description, personality, scenario, firstMes, mesExample, creatorNotes,
    systemPrompt, postHistoryInstructions, alternateGreetingsJson, tagsJson, characterVersion,
    extensionsJson, defaultCodexId, colorHex, createdAt,
)
private fun RpCharacterDto.toEntity() = RpCharacterEntity(
    id, name, avatarMediaId, description, personality, scenario, firstMes, mesExample, creatorNotes,
    systemPrompt, postHistoryInstructions, alternateGreetingsJson, tagsJson, characterVersion,
    extensionsJson, defaultCodexId, colorHex, createdAt,
)
private fun RpPersonaEntity.toDto() = RpPersonaDto(id, name, avatarMediaId, description, isDefault)
private fun RpPersonaDto.toEntity() = RpPersonaEntity(id, name, avatarMediaId, description, isDefault)
private fun RpChatEntity.toDto() = RpChatDto(
    id, characterId, groupId, personaId, title, backgroundMediaId, authorsNote, authorsNoteDepth,
    presetId, promptTemplateId, branchOfChatId, displayMode, narrationColorHex, speechColorHex,
    oocColorHex, createdAt, updatedAt,
)
private fun RpChatDto.toEntity() = RpChatEntity(
    id, characterId, groupId, personaId, title, backgroundMediaId, authorsNote, authorsNoteDepth,
    presetId, promptTemplateId, branchOfChatId, displayMode, narrationColorHex, speechColorHex,
    oocColorHex, createdAt, updatedAt,
)
private fun RpMessageEntity.toDto() = RpMessageDto(
    id, chatId, swipeGroupId, swipeIndex, isActiveSwipe, role, speakerCharacterId, contentJson,
    tokenCount, isEdited, createdAt, displayMode.ifBlank { "messenger" },
)
private fun RpMessageDto.toEntity() = RpMessageEntity(
    id, chatId, swipeGroupId, swipeIndex, isActiveSwipe, role, speakerCharacterId, contentJson,
    tokenCount, isEdited, createdAt, displayMode.ifBlank { "messenger" },
)
