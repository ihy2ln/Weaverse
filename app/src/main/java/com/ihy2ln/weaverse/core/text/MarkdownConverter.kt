package com.ihy2ln.weaverse.core.text

import com.ihy2ln.weaverse.core.util.newId
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * Document ⇄ Markdown conversion for export/import (spec §6): media as
 * `![caption](media://<id>)`, scene breaks as `***`. This is a best-effort
 * converter over the common formatting subset, not a full CommonMark
 * implementation — genuinely lossless round-tripping isn't achievable for
 * [Span.colorHex]/[Span.highlightHex]/[Align] (plain Markdown has no syntax
 * for text color, highlight, or paragraph alignment at all), and inline
 * mark parsing doesn't handle deeply nested/overlapping marks. See
 * BUILD_NOTES.md "Markdown conversion" for the exact scope.
 */
object MarkdownConverter {
    /** Beat blocks are excluded from exports by default (spec §6) — `fromMarkdown`'s own
     * `> **Scene Beat:**` case still parses one back in on import, for round-tripping a document
     * that was exported before this exclusion, or hand-authored Markdown that uses the marker. */
    fun toMarkdown(document: Document): String =
        document.blocks.filterNot { it is SceneBeatBlock }.joinToString(separator = "\n\n") { it.toMarkdown() }

    fun fromMarkdown(markdown: String): Document {
        val chunks = markdown.replace("\r\n", "\n").split(Regex("\n\\s*\n"))
        val blocks = chunks.flatMap { parseChunk(it.trim()) }
        return Document(blocks)
    }

    private fun Block.toMarkdown(): String = when (this) {
        is Paragraph -> "  ".repeat(indentLevel) + spans.toMarkdown()
        is Heading -> "#".repeat(level.coerceIn(1, 6)) + " " + spans.toMarkdown()
        is Quote -> "> " + spans.toMarkdown()
        is ListItem -> "  ".repeat(depth) + (if (ordered) "1. " else "- ") + spans.toMarkdown()
        is Divider -> if (style == DividerStyle.SceneBreak) "***" else "---"
        is CodeBlock -> "```" + (language.orEmpty()) + "\n" + text + "\n```"
        is MediaBlock -> "![" + caption.toMarkdown() + "](media://" + mediaId + ")"
        is SceneBeatBlock -> "> **Scene Beat:** " + prompt
        is MediaStack -> "$MEDIA_STACK_MARKER${DocumentJson.encodeToString(this)}-->"
        is MediaGrid -> "$MEDIA_GRID_MARKER${DocumentJson.encodeToString(this)}-->"
    }

    private fun List<Span>.toMarkdown(): String = joinToString(separator = "") { it.toMarkdown() }

    private fun Span.toMarkdown(): String {
        if (Mark.Code in marks) return "`$text`"
        var result = text
        if (Mark.Bold in marks) result = "**$result**"
        if (Mark.Italic in marks) result = "*$result*"
        if (Mark.Strikethrough in marks) result = "~~$result~~"
        if (Mark.Underline in marks) result = "__${result}__"
        if (Mark.Superscript in marks) result = "^$result^"
        if (Mark.Subscript in marks) result = "~$result~"
        return result
    }

    private fun parseChunk(chunk: String): List<Block> {
        if (chunk.isBlank()) return emptyList()
        val lines = chunk.lines()
        return when {
            chunk == "***" || chunk == "* * *" -> listOf(Divider(newId(), DividerStyle.SceneBreak))
            chunk == "---" -> listOf(Divider(newId(), DividerStyle.HorizontalRule))
            chunk.startsWith("```") -> listOf(parseCodeBlock(chunk))
            chunk.startsWith("![") -> listOfNotNull(parseMediaBlock(chunk))
                .ifEmpty { listOf(Paragraph(newId(), parseSpans(chunk))) }
            chunk.startsWith(MEDIA_STACK_MARKER) -> listOfNotNull(
                runCatching { DocumentJson.decodeFromString<MediaStack>(chunk.removePrefix(MEDIA_STACK_MARKER).removeSuffix("-->")) }.getOrNull(),
            )
            chunk.startsWith(MEDIA_GRID_MARKER) -> listOfNotNull(
                runCatching { DocumentJson.decodeFromString<MediaGrid>(chunk.removePrefix(MEDIA_GRID_MARKER).removeSuffix("-->")) }.getOrNull(),
            )
            chunk.startsWith("> **Scene Beat:**") ->
                listOf(SceneBeatBlock(newId(), chunk.removePrefix("> **Scene Beat:**").trim()))
            chunk.startsWith(">") -> listOf(
                Quote(newId(), parseSpans(lines.joinToString(" ") { it.removePrefix(">").trim() })),
            )
            chunk.startsWith("#") -> listOf(parseHeading(chunk))
            lines.isNotEmpty() && lines.all(::isListLine) -> lines.map(::parseListItemLine)
            else -> listOf(Paragraph(newId(), parseSpans(chunk.replace("\n", " "))))
        }
    }

    private fun isListLine(line: String): Boolean {
        val trimmed = line.trimStart()
        return trimmed.startsWith("- ") || trimmed.startsWith("* ") || listItemOrderedPrefix.containsMatchIn(trimmed)
    }

    private val listItemOrderedPrefix = Regex("^\\d+\\.\\s")

    private fun parseListItemLine(line: String): ListItem {
        val depth = line.takeWhile { it == ' ' }.length / 2
        val trimmed = line.trimStart()
        val ordered = listItemOrderedPrefix.containsMatchIn(trimmed)
        val content = when {
            ordered -> trimmed.replaceFirst(listItemOrderedPrefix, "")
            else -> trimmed.removePrefix("- ").removePrefix("* ")
        }
        return ListItem(newId(), ordered = ordered, depth = depth, spans = parseSpans(content))
    }

    private fun parseHeading(chunk: String): Heading {
        val level = chunk.takeWhile { it == '#' }.length.coerceIn(1, 6)
        return Heading(newId(), level = level, spans = parseSpans(chunk.drop(level).trim()))
    }

    private fun parseCodeBlock(chunk: String): CodeBlock {
        val lines = chunk.lines()
        val language = lines.firstOrNull().orEmpty().removePrefix("```").trim().ifBlank { null }
        val body = lines.drop(1).let { if (it.isNotEmpty() && it.last().trim() == "```") it.dropLast(1) else it }
        return CodeBlock(newId(), text = body.joinToString("\n"), language = language)
    }

    /** Spec §8: "serialized... as an HTML comment carrying the layout JSON, so round-trips are
     * lossless" — plain Markdown has no native syntax for a multi-item stack or a grid template,
     * so the whole block is carried verbatim inside a comment rather than approximated. */
    private const val MEDIA_STACK_MARKER = "<!--weaverse:mediastack:"
    private const val MEDIA_GRID_MARKER = "<!--weaverse:mediagrid:"

    private val mediaRegex = Regex("^!\\[(.*)]\\(media://([^)]+)\\)$")

    private fun parseMediaBlock(chunk: String): MediaBlock? {
        val match = mediaRegex.find(chunk.trim()) ?: return null
        val (captionText, mediaId) = match.destructured
        return MediaBlock(
            id = newId(),
            mediaId = mediaId,
            // Plain Markdown can't distinguish image vs video; Image is the
            // safe default since Markdown-imported media almost always is one.
            // The real kind lives in the `media` table (Phase 6/7).
            kind = MediaKind.Image,
            caption = if (captionText.isBlank()) emptyList() else listOf(Span(captionText)),
        )
    }

    private val inlinePatterns: List<Pair<Regex, Mark>> = listOf(
        Regex("\\*\\*(.+?)\\*\\*") to Mark.Bold,
        Regex("__(.+?)__") to Mark.Underline,
        Regex("~~(.+?)~~") to Mark.Strikethrough,
        Regex("`(.+?)`") to Mark.Code,
        Regex("\\*(.+?)\\*") to Mark.Italic,
    )

    /** Greedy left-to-right inline scan — see the class doc for what this deliberately doesn't handle. */
    fun parseSpans(text: String): List<Span> {
        if (text.isEmpty()) return emptyList()

        var earliestMatch: MatchResult? = null
        var earliestMark: Mark? = null
        for ((regex, mark) in inlinePatterns) {
            val match = regex.find(text) ?: continue
            if (earliestMatch == null || match.range.first < earliestMatch.range.first) {
                earliestMatch = match
                earliestMark = mark
            }
        }

        val match = earliestMatch ?: return listOf(Span(text))
        val mark = earliestMark ?: return listOf(Span(text))

        val before = text.substring(0, match.range.first)
        val inner = match.groupValues[1]
        val after = text.substring(match.range.last + 1)

        val result = mutableListOf<Span>()
        if (before.isNotEmpty()) result.add(Span(before))
        result.add(Span(inner, marks = setOf(mark)))
        result.addAll(parseSpans(after))
        return result
    }
}
