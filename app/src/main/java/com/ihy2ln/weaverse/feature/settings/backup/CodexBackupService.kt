package com.ihy2ln.weaverse.feature.settings.backup

import com.ihy2ln.weaverse.core.export.DocxCodec
import com.ihy2ln.weaverse.core.export.ExportFormat
import com.ihy2ln.weaverse.core.export.parseHtmlOutline
import com.ihy2ln.weaverse.core.export.parseMarkdownOutline
import com.ihy2ln.weaverse.core.export.toHtml
import com.ihy2ln.weaverse.core.export.toMarkdown
import com.ihy2ln.weaverse.data.db.entity.CodexCategoryEntity
import com.ihy2ln.weaverse.data.db.entity.CodexEntryEntity
import com.ihy2ln.weaverse.data.db.entity.ScopeType
import com.ihy2ln.weaverse.data.repo.CodexRepository
import com.ihy2ln.weaverse.data.repo.LibraryRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Standalone codex export/import (as opposed to [BookBackupService]'s codex-bundled-inside-a-
 * book export) — same four formats. Import always adds *new* categories/entries into [bookId]'s
 * existing codex rather than replacing it, so importing a codex file is closer to "merge in
 * these entries" than "restore a backup". */
@Singleton
class CodexBackupService @Inject constructor(
    private val codexRepository: CodexRepository,
    private val libraryRepository: LibraryRepository,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    suspend fun export(bookId: String, format: ExportFormat): ByteArray {
        val book = libraryRepository.getBook(bookId)
        val categoryBackups = codexRepository.observeCategories(ScopeType.Book, bookId).first().map { category ->
            val entries = codexRepository.observeEntries(category.id).first().map { entry ->
                CodexEntryBackup(
                    name = entry.name,
                    aliases = entry.aliases,
                    plainText = entry.plainText,
                    colorHex = entry.colorHex,
                    alwaysInclude = entry.alwaysInclude,
                )
            }
            CodexCategoryBackup(name = category.name, colorHex = category.colorHex, entries = entries)
        }
        val backup = CodexBackup(title = "${book?.title.orEmpty().ifBlank { "Codex" }} Codex", categories = categoryBackups)

        return when (format) {
            ExportFormat.Json -> json.encodeToString(backup).toByteArray(Charsets.UTF_8)
            ExportFormat.Markdown -> backup.toOutline().toMarkdown().toByteArray(Charsets.UTF_8)
            ExportFormat.Html -> backup.toOutline().toHtml().toByteArray(Charsets.UTF_8)
            ExportFormat.Docx -> DocxCodec.encode(backup.toOutline())
        }
    }

    /** Returns how many entries were imported. */
    suspend fun import(bytes: ByteArray, format: ExportFormat, bookId: String): Int {
        val backup = when (format) {
            ExportFormat.Json -> json.decodeFromString<CodexBackup>(bytes.toString(Charsets.UTF_8))
            ExportFormat.Markdown -> bytes.toString(Charsets.UTF_8).parseMarkdownOutline("Codex").toCodexBackup()
            ExportFormat.Html -> bytes.toString(Charsets.UTF_8).parseHtmlOutline("Codex").toCodexBackup()
            ExportFormat.Docx -> DocxCodec.decode(bytes).toCodexBackup()
        }

        var importedCount = 0
        backup.categories.forEach { categoryBackup ->
            val category = CodexCategoryEntity(scopeType = ScopeType.Book, scopeId = bookId, name = categoryBackup.name, colorHex = categoryBackup.colorHex)
            codexRepository.upsertCategory(category)
            categoryBackup.entries.forEach { entryBackup ->
                codexRepository.upsertEntry(
                    CodexEntryEntity(
                        categoryId = category.id,
                        scopeType = ScopeType.Book,
                        scopeId = bookId,
                        name = entryBackup.name,
                        aliases = entryBackup.aliases,
                        plainText = entryBackup.plainText,
                        colorHex = entryBackup.colorHex,
                        alwaysInclude = entryBackup.alwaysInclude,
                    ),
                )
                importedCount++
            }
        }
        return importedCount
    }
}
