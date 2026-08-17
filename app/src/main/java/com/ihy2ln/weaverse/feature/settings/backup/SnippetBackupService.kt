package com.ihy2ln.weaverse.feature.settings.backup

import com.ihy2ln.weaverse.core.export.DocxCodec
import com.ihy2ln.weaverse.core.export.ExportFormat
import com.ihy2ln.weaverse.core.export.parseHtmlOutline
import com.ihy2ln.weaverse.core.export.parseMarkdownOutline
import com.ihy2ln.weaverse.core.export.toHtml
import com.ihy2ln.weaverse.core.export.toMarkdown
import com.ihy2ln.weaverse.data.db.entity.ScopeType
import com.ihy2ln.weaverse.data.db.entity.SnippetEntity
import com.ihy2ln.weaverse.data.repo.LibraryRepository
import com.ihy2ln.weaverse.data.repo.SnippetLabelRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Snippet export/import (spec follow-up: same 4 formats as Book/Codex/Chat). Import adds new
 * snippets alongside the book's existing ones rather than replacing them. */
@Singleton
class SnippetBackupService @Inject constructor(
    private val snippetLabelRepository: SnippetLabelRepository,
    private val libraryRepository: LibraryRepository,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    suspend fun export(bookId: String, format: ExportFormat): ByteArray {
        val book = libraryRepository.getBook(bookId)
        val items = snippetLabelRepository.observeSnippets(ScopeType.Book, bookId).first().map {
            SnippetBackupItem(title = it.title, body = it.body, category = it.category, pinned = it.pinned)
        }
        val backup = SnippetBackup(bookTitle = book?.title.orEmpty(), snippets = items)

        return when (format) {
            ExportFormat.Json -> json.encodeToString(backup).toByteArray(Charsets.UTF_8)
            ExportFormat.Markdown -> backup.toOutline().toMarkdown().toByteArray(Charsets.UTF_8)
            ExportFormat.Html -> backup.toOutline().toHtml().toByteArray(Charsets.UTF_8)
            ExportFormat.Docx -> DocxCodec.encode(backup.toOutline())
        }
    }

    /** Returns how many snippets were imported. */
    suspend fun import(bytes: ByteArray, format: ExportFormat, bookId: String): Int {
        val backup = when (format) {
            ExportFormat.Json -> json.decodeFromString<SnippetBackup>(bytes.toString(Charsets.UTF_8))
            ExportFormat.Markdown -> bytes.toString(Charsets.UTF_8).parseMarkdownOutline("Snippets").toSnippetBackup()
            ExportFormat.Html -> bytes.toString(Charsets.UTF_8).parseHtmlOutline("Snippets").toSnippetBackup()
            ExportFormat.Docx -> DocxCodec.decode(bytes).toSnippetBackup()
        }

        backup.snippets.forEach { item ->
            snippetLabelRepository.upsertSnippet(
                SnippetEntity(scopeType = ScopeType.Book, scopeId = bookId, title = item.title, body = item.body, category = item.category, pinned = item.pinned),
            )
        }
        return backup.snippets.size
    }
}
