package com.ihy2ln.weaverse.ai.context

import com.ihy2ln.weaverse.ai.AIMessage
import com.ihy2ln.weaverse.data.db.entity.LorePosition
import com.ihy2ln.weaverse.data.db.entity.SelectiveLogic

/**
 * Everything [ContextBuilder] needs to know about one codex entry to decide
 * whether it fires — deliberately decoupled from the Room entities
 * ([com.ihy2ln.weaverse.data.db.entity.CodexEntryEntity] /
 * `CodexEntryLoreEntity`) so the matching/budget algorithm is unit-testable
 * without a database. The caller (Phase 10/11's chat ViewModels) maps the
 * two entities into this.
 */
data class CodexEntryContext(
    val id: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    /** Plain text of the entry's body — both the text injected into the prompt and the source re-scanned during recursion. */
    val bodyText: String,
    val alwaysInclude: Boolean = false,
    val disabled: Boolean = false,
    /** Spec's "Track this entry by name/alias" — when false, the name/aliases below are excluded
     * from auto-detection (a deliberately common word/phrase); `keys`/`secondaryKeys` still work. */
    val trackByNameAlias: Boolean = true,
    val tokenBudgetWeight: Float = 1f,
    val keys: List<String> = emptyList(),
    val secondaryKeys: List<String> = emptyList(),
    val selectiveLogic: SelectiveLogic = SelectiveLogic.AndAny,
    val insertionOrder: Int = 100,
    val position: LorePosition = LorePosition.AfterChar,
    val depth: Int = 4,
    val probability: Int = 100,
    val isConstant: Boolean = false,
    val caseSensitive: Boolean = false,
    val matchWholeWords: Boolean = true,
    val recursionAllowed: Boolean = true,
)

/**
 * Series-level context (Revision 02 §3): "when a book/session belongs to a series, include the
 * series premise, the summaries of previous members (most recent first, trimmed to a configurable
 * token budget), and series-scoped constant codex entries — before book-level content in priority
 * order." The trimming and most-recent-first ordering are the caller's job (it already knows the
 * member order and the budget it wants to spend before calling [ContextBuilder]); series-scoped
 * *codex entries* aren't modeled here at all — the caller merges them into the same
 * `codexEntries` list [ContextBuilder.build] already takes, so no separate matching path is
 * needed for those.
 */
data class SeriesContext(
    val premise: String = "",
    val previousMemberSummaries: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = premise.isBlank() && previousMemberSummaries.isEmpty()
}

/** The scan-text + project-block inputs, per mode (spec §8.3 step 1 and step 7). */
sealed interface ContextScope {
    val seriesContext: SeriesContext?

    data class Novel(
        val currentSceneText: String,
        /** Already limited to the default N=3 (or caller-chosen) previous scenes, in reading order. */
        val previousScenesText: List<String> = emptyList(),
        val sceneBeats: String = "",
        val styleGuide: String = "",
        val pov: String = "",
        val tense: String = "",
        val genre: String = "",
        override val seriesContext: SeriesContext? = null,
    ) : ContextScope

    data class Roleplay(
        val characterCard: String,
        val personaText: String = "",
        val scenario: String = "",
        /** Already limited to the last `scanDepth` messages. */
        val chatHistory: List<AIMessage> = emptyList(),
        val authorsNote: String? = null,
        override val seriesContext: SeriesContext? = null,
    ) : ContextScope
}

data class ContextTrigger(val userInput: String)

data class TokenBudget(val contextLimit: Int = 8000, val reserveForResponse: Int = 1024) {
    val available: Int get() = (contextLimit - reserveForResponse).coerceAtLeast(0)
}

data class ContextSection(
    val label: String,
    val text: String,
    val tokenCount: Int,
    val included: Boolean,
)

data class AssembledPrompt(
    val systemBlocks: List<String>,
    val messages: List<AIMessage>,
    val usedEntryIds: List<String>,
    val tokenBreakdown: List<ContextSection>,
) {
    val droppedSectionLabels: List<String> get() = tokenBreakdown.filterNot { it.included }.map { it.label }
}
