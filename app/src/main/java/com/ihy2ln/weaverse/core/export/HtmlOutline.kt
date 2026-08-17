package com.ihy2ln.weaverse.core.export

/** [ExportOutline] <-> a plain HTML document. Import is tag-stripping/regex-based (no XML/DOM
 * parser dependency) — reliable against HTML this app itself exported, best-effort against
 * arbitrary HTML from elsewhere (nested tags, `<div>` wrappers, inline styles, etc. aren't
 * specially handled; their text still comes through, just not necessarily as the heading/
 * paragraph structure a fancier document produced it with). */
fun ExportOutline.toHtml(): String = buildString {
    append("<!DOCTYPE html>\n<html><head><meta charset=\"utf-8\"><title>")
    append(escapeHtml(title))
    append("</title></head><body>\n<h1>")
    append(escapeHtml(title))
    append("</h1>\n")
    nodes.forEach { node ->
        when (node) {
            is ExportNode.Heading -> {
                val tag = "h" + (node.level + 1).coerceIn(2, 6)
                append("<").append(tag).append(">").append(escapeHtml(node.text)).append("</").append(tag).append(">\n")
            }
            is ExportNode.Paragraph -> {
                append("<p>").append(escapeHtml(node.text)).append("</p>\n")
            }
        }
    }
    append("</body></html>")
}

private val blockTagPattern = Regex("<(h[1-6]|p)[^>]*>(.*?)</\\1>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
private val anyTagPattern = Regex("<[^>]*>")

fun String.parseHtmlOutline(fallbackTitle: String): ExportOutline {
    var title = fallbackTitle
    var titleConsumed = false
    val nodes = mutableListOf<ExportNode>()

    blockTagPattern.findAll(this).forEach { match ->
        val tag = match.groupValues[1].lowercase()
        val text = stripHtmlTags(match.groupValues[2]).trim()
        if (text.isEmpty()) return@forEach
        if (tag == "h1" && !titleConsumed) {
            title = text
            titleConsumed = true
        } else if (tag.startsWith("h")) {
            nodes.add(ExportNode.Heading(tag.substring(1).toInt() - 1, text))
        } else {
            nodes.add(ExportNode.Paragraph(text))
        }
    }
    return ExportOutline(title, nodes)
}

private fun stripHtmlTags(text: String): String = unescapeHtml(text.replace(anyTagPattern, ""))

private fun escapeHtml(text: String): String = text
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")

private fun unescapeHtml(text: String): String = text
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&amp;", "&")
