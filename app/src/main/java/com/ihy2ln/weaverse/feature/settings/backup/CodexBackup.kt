package com.ihy2ln.weaverse.feature.settings.backup

import com.ihy2ln.weaverse.core.export.ExportNode
import com.ihy2ln.weaverse.core.export.ExportOutline
import kotlinx.serialization.Serializable

/** Standalone codex export — reuses [CodexCategoryBackup]/[CodexEntryBackup] from
 * `BookBackup.kt` (same shape a book's bundled codex export uses). */
@Serializable
data class CodexBackup(
    val formatVersion: Int = 1,
    val title: String,
    val categories: List<CodexCategoryBackup>,
)

fun CodexBackup.toOutline(): ExportOutline = ExportOutline(
    title = title,
    nodes = categories.flatMap { category ->
        buildList {
            add(ExportNode.Heading(1, category.name))
            category.entries.forEach { entry ->
                add(ExportNode.Heading(2, entry.name))
                if (entry.aliases.isNotEmpty()) add(ExportNode.Paragraph("Aliases: ${entry.aliases.joinToString(", ")}"))
                if (entry.plainText.isNotBlank()) add(ExportNode.Paragraph(entry.plainText))
            }
        }
    },
)

/** Level-1 headings become categories, level-2+ become entries; an "Aliases: a, b, c" paragraph
 * right after an entry heading is parsed back into that entry's alias list, any other paragraph
 * becomes (part of) the entry's body. Color/always-include don't survive this direction. */
fun ExportOutline.toCodexBackup(): CodexBackup {
    class EntryAcc(val name: String) {
        var aliases: List<String> = emptyList()
        val bodyLines = mutableListOf<String>()
    }
    class CategoryAcc(val name: String) {
        val entries = mutableListOf<EntryAcc>()
    }

    val categories = mutableListOf<CategoryAcc>()
    var currentCategory: CategoryAcc? = null
    var currentEntry: EntryAcc? = null

    fun ensureCategory(): CategoryAcc = currentCategory ?: CategoryAcc("Codex").also { categories.add(it); currentCategory = it }

    nodes.forEach { node ->
        when (node) {
            is ExportNode.Heading -> if (node.level <= 1) {
                currentCategory = CategoryAcc(node.text).also { categories.add(it) }
                currentEntry = null
            } else {
                currentEntry = EntryAcc(node.text).also { ensureCategory().entries.add(it) }
            }
            is ExportNode.Paragraph -> {
                val entry = currentEntry
                if (entry != null) {
                    val aliasPrefix = "Aliases: "
                    if (node.text.startsWith(aliasPrefix)) {
                        entry.aliases = node.text.removePrefix(aliasPrefix).split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    } else {
                        entry.bodyLines.add(node.text)
                    }
                }
            }
        }
    }

    return CodexBackup(
        title = title,
        categories = categories.map { category ->
            CodexCategoryBackup(
                name = category.name,
                colorHex = "#8B6FD1",
                entries = category.entries.map { entry ->
                    CodexEntryBackup(
                        name = entry.name,
                        aliases = entry.aliases,
                        plainText = entry.bodyLines.joinToString("\n"),
                        colorHex = null,
                        alwaysInclude = false,
                    )
                },
            )
        },
    )
}
