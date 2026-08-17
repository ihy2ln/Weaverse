package com.ihy2ln.weaverse.core.export

/** [ExportOutline] <-> plain Markdown text. The most reliable of the three readable formats to
 * round-trip, since Markdown headings (`#`/`##`/...) map onto [ExportNode.Heading] unambiguously
 * — no tag-stripping or XML parsing involved, unlike [toHtml]/[DocxCodec]. */
fun ExportOutline.toMarkdown(): String = buildString {
    appendLine("# $title")
    appendLine()
    nodes.forEach { node ->
        when (node) {
            is ExportNode.Heading -> {
                appendLine("#".repeat(node.level.coerceIn(1, 6)) + " " + node.text)
                appendLine()
            }
            is ExportNode.Paragraph -> {
                appendLine(node.text)
                appendLine()
            }
        }
    }
}

private val headingPattern = Regex("^(#{1,6})\\s+(.*)$")

fun String.parseMarkdownOutline(fallbackTitle: String): ExportOutline {
    var title = fallbackTitle
    var titleConsumed = false
    val nodes = mutableListOf<ExportNode>()

    lineSequence().forEach { raw ->
        val line = raw.trim()
        if (line.isEmpty()) return@forEach
        val match = headingPattern.find(line)
        if (match != null) {
            val level = match.groupValues[1].length
            val text = match.groupValues[2].trim()
            if (level == 1 && !titleConsumed) {
                title = text
                titleConsumed = true
            } else {
                nodes.add(ExportNode.Heading(level, text))
            }
        } else {
            nodes.add(ExportNode.Paragraph(line))
        }
    }
    return ExportOutline(title, nodes)
}
