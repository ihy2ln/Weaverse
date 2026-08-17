package com.ihy2ln.weaverse.core.text

/** One detected occurrence of a codex entry's name/alias inside a block of prose — the shared
 * primitive behind clickable inline codex mentions (Scene Beat output, chat messages, the
 * `+ Context` chip strip) and behind auto-linking a scene to the codex entries it mentions. */
data class CodexMention(val range: IntRange, val entryId: String)

/**
 * A trackable codex entry as far as mention-scanning cares — deliberately decoupled from Room's
 * `CodexEntryEntity`/`CodexEntryLoreEntity` the same way `ai/context/CodexEntryContext` decouples
 * `ContextBuilder` from them, so this stays a plain, framework-free, unit-testable object.
 * [tracked] mirrors `CodexEntryLoreEntity.trackByNameAlias`: when false, this entry's name/aliases
 * are a deliberately common word/phrase the user doesn't want auto-detected in prose (spec:
 * "Track this entry by name/alias").
 */
data class MentionCandidate(
    val entryId: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val tracked: Boolean = true,
    val caseSensitive: Boolean = false,
    val matchWholeWords: Boolean = true,
)

/**
 * Scans [text] for every tracked candidate's name/alias — "type the name/alias/'activating
 * words' for a codex entry and it gets recognized in your prose." Overlapping matches (e.g. one
 * candidate's alias is a substring of another's longer name) resolve to the longest match, then
 * the earliest start, left to right — same greedy-scan spirit as `ProseStyling`'s marker parser.
 */
object MentionScanner {
    fun findMentions(text: String, candidates: List<MentionCandidate>): List<CodexMention> {
        if (text.isBlank() || candidates.isEmpty()) return emptyList()

        data class RawMatch(val range: IntRange, val entryId: String)

        val rawMatches = buildList {
            candidates.filter { it.tracked }.forEach { candidate ->
                val terms = (listOf(candidate.name) + candidate.aliases).filter { it.isNotBlank() }
                terms.forEach { term ->
                    TermMatching.findRanges(text, term, candidate.caseSensitive, candidate.matchWholeWords)
                        .forEach { range -> add(RawMatch(range, candidate.entryId)) }
                }
            }
        }

        val ordered = rawMatches.sortedWith(
            compareByDescending<RawMatch> { it.range.last - it.range.first }.thenBy { it.range.first },
        )
        val chosen = mutableListOf<RawMatch>()
        for (match in ordered) {
            val overlaps = chosen.any { it.range.first <= match.range.last && match.range.first <= it.range.last }
            if (!overlaps) chosen.add(match)
        }
        return chosen.sortedBy { it.range.first }.map { CodexMention(it.range, it.entryId) }
    }
}
