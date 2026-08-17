package com.ihy2ln.weaverse.core.text

/**
 * Shared literal-term-in-text matching (word-boundary + case-sensitivity aware), the one regex-
 * building implementation behind both AI context-injection matching
 * ([com.ihy2ln.weaverse.ai.context.ContextMatching]) and inline mention detection ([MentionScanner])
 * — kept here, dependency-free, so both call sites agree on what "the entry's name/alias appears
 * in this text" means instead of two regex implementations silently drifting apart.
 */
object TermMatching {
    /** Every non-overlapping occurrence of [term] in [text], as character ranges. */
    fun findRanges(text: String, term: String, caseSensitive: Boolean, wholeWords: Boolean): List<IntRange> {
        if (term.isBlank()) return emptyList()
        val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
        val escaped = Regex.escape(term)
        val pattern = if (wholeWords) "\\b$escaped\\b" else escaped
        return Regex(pattern, options).findAll(text).map { it.range }.toList()
    }

    fun containsTerm(text: String, term: String, caseSensitive: Boolean, wholeWords: Boolean): Boolean =
        findRanges(text, term, caseSensitive, wholeWords).isNotEmpty()
}
