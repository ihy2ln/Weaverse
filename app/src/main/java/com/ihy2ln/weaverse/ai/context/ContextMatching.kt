package com.ihy2ln.weaverse.ai.context

import com.ihy2ln.weaverse.core.text.TermMatching
import com.ihy2ln.weaverse.data.db.entity.SelectiveLogic

/**
 * Keyword matching against scan text (spec §8.3 steps 2-3): entry name/
 * aliases gate inclusion when present in the text (unless [CodexEntryContext.trackByNameAlias]
 * turns that off — spec's "Track this entry by name/alias", for names/aliases that are
 * deliberately common words); lore `keys` + `secondaryKeys` + [SelectiveLogic] layer on top of
 * that for entries that define World Info-style keys.
 */
internal object ContextMatching {
    fun matches(entry: CodexEntryContext, scanText: String): Boolean {
        if (entry.trackByNameAlias && containsAny(scanText, listOf(entry.name) + entry.aliases, entry.caseSensitive, entry.matchWholeWords)) {
            return true
        }
        if (entry.keys.isEmpty()) return false
        if (!containsAny(scanText, entry.keys, entry.caseSensitive, entry.matchWholeWords)) return false
        if (entry.secondaryKeys.isEmpty()) return true

        return when (entry.selectiveLogic) {
            SelectiveLogic.AndAny -> containsAny(scanText, entry.secondaryKeys, entry.caseSensitive, entry.matchWholeWords)
            SelectiveLogic.AndAll -> entry.secondaryKeys.all { key -> containsAny(scanText, listOf(key), entry.caseSensitive, entry.matchWholeWords) }
            SelectiveLogic.NotAny -> !containsAny(scanText, entry.secondaryKeys, entry.caseSensitive, entry.matchWholeWords)
            SelectiveLogic.NotAll -> !entry.secondaryKeys.all { key -> containsAny(scanText, listOf(key), entry.caseSensitive, entry.matchWholeWords) }
        }
    }

    private fun containsAny(text: String, terms: List<String>, caseSensitive: Boolean, wholeWords: Boolean): Boolean =
        terms.any { term -> term.isNotBlank() && TermMatching.containsTerm(text, term, caseSensitive, wholeWords) }
}
