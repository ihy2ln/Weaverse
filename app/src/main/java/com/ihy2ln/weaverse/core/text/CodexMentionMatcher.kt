package com.ihy2ln.weaverse.core.text

/** A codex entry's name + aliases, indexed for mention matching against prose. */
data class CodexMentionTarget(
    val entryId: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val caseSensitive: Boolean = false,
)

/** A matched range of [text] that names/aliases-matched [entryId], for hyperlinking. */
data class CodexMention(
    val entryId: String,
    val start: Int,
    val end: Int,
)

/**
 * Finds every whole-word occurrence of a codex entry's name or alias in [text].
 * Longer names win overlapping shorter ones (e.g. "Radiance Vampirism" over "Radiance").
 */
fun findCodexMentions(text: String, targets: List<CodexMentionTarget>): List<CodexMention> {
    if (text.isBlank() || targets.isEmpty()) return emptyList()

    data class Candidate(val entryId: String, val term: String, val caseSensitive: Boolean)

    val candidates = targets.flatMap { target ->
        (listOf(target.name) + target.aliases)
            .filter { it.isNotBlank() }
            .map { Candidate(target.entryId, it.trim(), target.caseSensitive) }
    }.sortedByDescending { it.term.length }

    val matches = mutableListOf<CodexMention>()
    val claimed = BooleanArray(text.length)

    for (candidate in candidates) {
        val term = candidate.term
        if (term.isEmpty()) continue
        var searchFrom = 0
        while (searchFrom <= text.length - term.length) {
            val index = text.indexOf(term, searchFrom, ignoreCase = !candidate.caseSensitive)
            if (index < 0) break
            val end = index + term.length
            val boundaryBefore = index == 0 || !text[index - 1].isLetterOrDigit()
            val boundaryAfter = end == text.length || !text[end].isLetterOrDigit()
            val alreadyClaimed = (index until end).any { claimed[it] }
            if (boundaryBefore && boundaryAfter && !alreadyClaimed) {
                matches += CodexMention(candidate.entryId, index, end)
                for (i in index until end) claimed[i] = true
            }
            searchFrom = index + 1
        }
    }
    return matches.sortedBy { it.start }
}
