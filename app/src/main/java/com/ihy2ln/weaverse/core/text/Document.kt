package com.ihy2ln.weaverse.core.text

import kotlinx.serialization.Serializable

@Serializable
data class Document(val blocks: List<Block> = emptyList())

private fun List<Span>.plainText(): String = joinToString(separator = "") { it.text }

private fun Block.plainText(): String = when (this) {
    is Paragraph -> spans.plainText()
    is Heading -> spans.plainText()
    is Quote -> spans.plainText()
    is ListItem -> spans.plainText()
    is CodeBlock -> text
    // Beat blocks are excluded from word counts and exports by default (spec §6).
    is SceneBeatBlock -> ""
    is Divider -> ""
    is MediaBlock -> caption.plainText()
    is MediaStack -> items.joinToString(separator = " ") { it.caption.plainText() }
    is MediaGrid -> items.joinToString(separator = " ") { it.caption.plainText() }
}

/** Flattens every block's text into one string (spec §6), one block per line. Used for word
 * count, FTS indexing (see `Scene`/`CodexEntry`/etc.`.plainText` in the Phase 3 data layer), and
 * codex-mention scanning (Phase 9's ContextBuilder). */
fun Document.toPlainText(): String = blocks.joinToString(separator = "\n") { it.plainText() }

private val wordSplitRegex = Regex("\\s+")

/** Word count used everywhere the app shows a count (top bar, scene cards, Plan aggregates). */
fun Document.wordCount(): Int {
    val text = toPlainText().trim()
    return if (text.isEmpty()) 0 else text.split(wordSplitRegex).size
}

fun String.wordCount(): Int {
    val trimmed = trim()
    return if (trimmed.isEmpty()) 0 else trimmed.split(wordSplitRegex).size
}
