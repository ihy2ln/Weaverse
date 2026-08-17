package com.ihy2ln.weaverse.feature.settings.backup

import com.ihy2ln.weaverse.core.export.DocxCodec
import com.ihy2ln.weaverse.core.export.ExportFormat
import com.ihy2ln.weaverse.core.export.parseHtmlOutline
import com.ihy2ln.weaverse.core.export.parseMarkdownOutline
import com.ihy2ln.weaverse.core.export.toHtml
import com.ihy2ln.weaverse.core.export.toMarkdown
import com.ihy2ln.weaverse.core.text.Document
import com.ihy2ln.weaverse.core.text.Paragraph
import com.ihy2ln.weaverse.core.text.Span
import com.ihy2ln.weaverse.core.text.toJson
import com.ihy2ln.weaverse.core.util.newId
import com.ihy2ln.weaverse.data.db.entity.ActEntity
import com.ihy2ln.weaverse.data.db.entity.BookEntity
import com.ihy2ln.weaverse.data.db.entity.ChapterEntity
import com.ihy2ln.weaverse.data.db.entity.CodexCategoryEntity
import com.ihy2ln.weaverse.data.db.entity.CodexEntryEntity
import com.ihy2ln.weaverse.data.db.entity.SceneEntity
import com.ihy2ln.weaverse.data.db.entity.ScopeType
import com.ihy2ln.weaverse.data.repo.CodexRepository
import com.ihy2ln.weaverse.data.repo.LibraryRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds/consumes a book as any of [ExportFormat]'s four formats against Room — the actual file
 * I/O (picking a destination/source Uri) lives in the Settings screen, same split as Phase 11's
 * card codec. JSON round-trips everything this DTO hierarchy captures (structure, word counts,
 * status, codex); Markdown/HTML/DOCX only round-trip the manuscript's headings/paragraphs (see
 * [toOutline]/[toBookBackup] in `BookBackup.kt` for exactly what's lost each direction).
 */
@Singleton
class BookBackupService @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val codexRepository: CodexRepository,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    suspend fun export(bookId: String, format: ExportFormat): ByteArray {
        val backup = buildBackup(bookId)
        return when (format) {
            ExportFormat.Json -> json.encodeToString(backup).toByteArray(Charsets.UTF_8)
            ExportFormat.Markdown -> backup.toOutline().toMarkdown().toByteArray(Charsets.UTF_8)
            ExportFormat.Html -> backup.toOutline().toHtml().toByteArray(Charsets.UTF_8)
            ExportFormat.Docx -> DocxCodec.encode(backup.toOutline())
        }
    }

    /** Always creates a *new* [BookEntity] (fresh ids throughout) rather than merging into an
     * existing one — importing twice is safe (produces two books) instead of silently overwriting. */
    suspend fun import(bytes: ByteArray, format: ExportFormat): BookEntity {
        val backup = when (format) {
            ExportFormat.Json -> json.decodeFromString<BookBackup>(bytes.toString(Charsets.UTF_8))
            ExportFormat.Markdown -> bytes.toString(Charsets.UTF_8).parseMarkdownOutline("Untitled").toBookBackup()
            ExportFormat.Html -> bytes.toString(Charsets.UTF_8).parseHtmlOutline("Untitled").toBookBackup()
            ExportFormat.Docx -> DocxCodec.decode(bytes).toBookBackup()
        }
        return persist(backup)
    }

    private suspend fun buildBackup(bookId: String): BookBackup {
        val book = libraryRepository.getBook(bookId) ?: error("Book not found")

        val actBackups = libraryRepository.observeActs(bookId).first().sortedBy { it.sortOrder }.map { act ->
            val chapterBackups = libraryRepository.observeChapters(act.id).first().sortedBy { it.sortOrder }.map { chapter ->
                val sceneBackups = libraryRepository.observeScenes(chapter.id).first().sortedBy { it.sortOrder }.map { scene ->
                    SceneBackup(
                        title = scene.title,
                        plainText = scene.plainText,
                        summary = scene.summary,
                        wordCount = scene.wordCount,
                        status = scene.status,
                        pov = scene.pov,
                        sortOrder = scene.sortOrder,
                    )
                }
                ChapterBackup(title = chapter.title, summary = chapter.summary, sortOrder = chapter.sortOrder, scenes = sceneBackups)
            }
            ActBackup(title = act.title, sortOrder = act.sortOrder, chapters = chapterBackups)
        }

        val categoryBackups = codexRepository.observeCategories(ScopeType.Book, bookId).first().map { category ->
            val entryBackups = codexRepository.observeEntries(category.id).first().map { entry ->
                CodexEntryBackup(
                    name = entry.name,
                    aliases = entry.aliases,
                    plainText = entry.plainText,
                    colorHex = entry.colorHex,
                    alwaysInclude = entry.alwaysInclude,
                )
            }
            CodexCategoryBackup(name = category.name, colorHex = category.colorHex, entries = entryBackups)
        }

        return BookBackup(
            title = book.title,
            genre = book.genre,
            pov = book.pov,
            tense = book.tense,
            styleGuide = book.styleGuide,
            acts = actBackups,
            codexCategories = categoryBackups,
        )
    }

    private suspend fun persist(backup: BookBackup): BookEntity {
        val book = BookEntity(title = backup.title, genre = backup.genre, pov = backup.pov, tense = backup.tense, styleGuide = backup.styleGuide)
        libraryRepository.upsertBook(book)

        backup.acts.forEach { actBackup ->
            val act = ActEntity(bookId = book.id, title = actBackup.title, sortOrder = actBackup.sortOrder)
            libraryRepository.upsertAct(act)
            actBackup.chapters.forEach { chapterBackup ->
                val chapter = ChapterEntity(actId = act.id, title = chapterBackup.title, summary = chapterBackup.summary, sortOrder = chapterBackup.sortOrder)
                libraryRepository.upsertChapter(chapter)
                chapterBackup.scenes.forEach { sceneBackup ->
                    val document = Document(listOf(Paragraph(newId(), listOf(Span(sceneBackup.plainText)))))
                    libraryRepository.upsertScene(
                        SceneEntity(
                            chapterId = chapter.id,
                            title = sceneBackup.title,
                            sortOrder = sceneBackup.sortOrder,
                            docJson = document.toJson(),
                            plainText = sceneBackup.plainText,
                            summary = sceneBackup.summary,
                            wordCount = sceneBackup.wordCount,
                            status = sceneBackup.status,
                            pov = sceneBackup.pov,
                        ),
                    )
                }
            }
        }

        backup.codexCategories.forEach { categoryBackup ->
            val category = CodexCategoryEntity(scopeType = ScopeType.Book, scopeId = book.id, name = categoryBackup.name, colorHex = categoryBackup.colorHex)
            codexRepository.upsertCategory(category)
            categoryBackup.entries.forEach { entryBackup ->
                codexRepository.upsertEntry(
                    CodexEntryEntity(
                        categoryId = category.id,
                        scopeType = ScopeType.Book,
                        scopeId = book.id,
                        name = entryBackup.name,
                        aliases = entryBackup.aliases,
                        plainText = entryBackup.plainText,
                        colorHex = entryBackup.colorHex,
                        alwaysInclude = entryBackup.alwaysInclude,
                    ),
                )
            }
        }

        return book
    }
}
