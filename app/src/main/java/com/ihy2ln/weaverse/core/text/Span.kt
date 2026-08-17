package com.ihy2ln.weaverse.core.text

import kotlinx.serialization.Serializable

enum class Mark { Bold, Italic, Underline, Strikethrough, Code, Superscript, Subscript }

@Serializable
data class Span(
    val text: String,
    val marks: Set<Mark> = emptySet(),
    val colorHex: String? = null,
    val highlightHex: String? = null,
    /** When set, this span is a tappable mention of a codex entry (spec §6/§9). */
    val codexEntryId: String? = null,
)
