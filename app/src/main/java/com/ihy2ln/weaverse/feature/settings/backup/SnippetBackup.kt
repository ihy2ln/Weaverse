package com.ihy2ln.weaverse.feature.settings.backup

import com.ihy2ln.weaverse.core.export.ExportNode
import com.ihy2ln.weaverse.core.export.ExportOutline
import kotlinx.serialization.Serializable

@Serializable
data class SnippetBackupItem(val title: String, val body: String, val category: String, val pinned: Boolean)

@Serializable
data class SnippetBackup(val formatVersion: Int = 1, val bookTitle: String, val snippets: List<SnippetBackupItem>)

/** Each snippet becomes a level-2 heading (its title) plus a body paragraph — flat, no
 * categories folded in as a separate heading level (most books only have a handful of
 * snippets; a two-level Category > Snippet hierarchy would round-trip category too, but
 * wasn't judged worth the added ambiguity for how few snippets typically exist). */
fun SnippetBackup.toOutline(): ExportOutline = ExportOutline(
    title = "$bookTitle Snippets",
    nodes = snippets.flatMap { snippet ->
        buildList {
            add(ExportNode.Heading(2, snippet.title))
            if (snippet.body.isNotBlank()) add(ExportNode.Paragraph(snippet.body))
        }
    },
)

fun ExportOutline.toSnippetBackup(): SnippetBackup {
    class SnippetAcc(val title: String) {
        val lines = mutableListOf<String>()
    }

    val snippets = mutableListOf<SnippetAcc>()
    var current: SnippetAcc? = null

    nodes.forEach { node ->
        when (node) {
            is ExportNode.Heading -> current = SnippetAcc(node.text).also { snippets.add(it) }
            is ExportNode.Paragraph -> current?.lines?.add(node.text)
        }
    }

    return SnippetBackup(
        bookTitle = title,
        snippets = snippets.map { SnippetBackupItem(title = it.title, body = it.lines.joinToString("\n"), category = "", pinned = false) },
    )
}
