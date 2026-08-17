package com.ihy2ln.weaverse.ai.context

import com.ihy2ln.weaverse.ai.AIMessage
import com.ihy2ln.weaverse.ai.AIMessageRole
import com.ihy2ln.weaverse.ai.token.TokenEstimator
import kotlin.random.Random

/**
 * The core context-assembly algorithm shared by both modes (spec §8.3).
 * Stateless — every input is a parameter, so it's fully unit-testable
 * without a database or a running app (see `ContextBuilderTest`).
 */
object ContextBuilder {
    const val DEFAULT_RECURSION_DEPTH = 2

    fun build(
        scope: ContextScope,
        trigger: ContextTrigger,
        codexEntries: List<CodexEntryContext>,
        manualIncludeIds: Set<String> = emptySet(),
        manualExcludeIds: Set<String> = emptySet(),
        tokenBudget: TokenBudget = TokenBudget(),
        maxRecursionDepth: Int = DEFAULT_RECURSION_DEPTH,
        random: Random = Random.Default,
    ): AssembledPrompt {
        val scanText = buildScanText(scope, trigger)
        val detectedIds = detectEntries(codexEntries, scanText, manualIncludeIds, manualExcludeIds, maxRecursionDepth, random)
        val detectedEntries = codexEntries
            .filter { it.id in detectedIds }
            .sortedBy { it.insertionOrder }

        val candidateSections = buildCandidateSections(scope, trigger, detectedEntries)
        val resolvedSections = allocateBudget(candidateSections, tokenBudget)

        val systemBlocks = resolvedSections
            .filter {
                it.included &&
                    (it.label == LABEL_SERIES || it.label == LABEL_SYSTEM || it.label == LABEL_CURRENT_SCENE || it.label.startsWith(LABEL_ENTRY_PREFIX))
            }
            .map { it.text }

        val messages = buildList {
            resolvedSections
                .filter { it.included && it.label == LABEL_CHAT_HISTORY }
                .forEach { add(AIMessage(AIMessageRole.User, it.text)) }
            add(AIMessage(AIMessageRole.User, trigger.userInput))
        }

        val includedEntryIds = detectedEntries
            .filter { entry -> resolvedSections.any { it.included && it.label == entry.sectionLabel() } }
            .map { it.id }

        return AssembledPrompt(
            systemBlocks = systemBlocks,
            messages = messages,
            usedEntryIds = includedEntryIds,
            tokenBreakdown = resolvedSections,
        )
    }

    private fun buildScanText(scope: ContextScope, trigger: ContextTrigger): String = when (scope) {
        is ContextScope.Novel -> buildString {
            scope.previousScenesText.forEach { appendLine(it) }
            appendLine(scope.currentSceneText)
            appendLine(scope.sceneBeats)
            append(trigger.userInput)
        }
        is ContextScope.Roleplay -> buildString {
            scope.chatHistory.forEach { appendLine(it.content) }
            append(trigger.userInput)
        }
    }

    private fun detectEntries(
        entries: List<CodexEntryContext>,
        scanText: String,
        manualIncludeIds: Set<String>,
        manualExcludeIds: Set<String>,
        maxRecursionDepth: Int,
        random: Random,
    ): Set<String> {
        val active = entries.filterNot { it.disabled || it.id in manualExcludeIds }
        val byId = active.associateBy { it.id }
        val matched = linkedSetOf<String>()

        fun tryInclude(entry: CodexEntryContext, bypassProbability: Boolean): Boolean {
            if (entry.id in matched) return false
            if (!bypassProbability && !rollProbability(entry, random)) return false
            matched.add(entry.id)
            return true
        }

        // Constant / always-include entries bypass keyword matching and probability entirely.
        active.filter { it.alwaysInclude || it.isConstant }.forEach { tryInclude(it, bypassProbability = true) }

        // Manual force-includes bypass matching and probability too (but not the manualExclude filter above).
        manualIncludeIds.forEach { id -> byId[id]?.let { tryInclude(it, bypassProbability = true) } }

        var scanTexts = listOf(scanText)
        var pass = 0
        while (pass <= maxRecursionDepth && scanTexts.isNotEmpty()) {
            val combined = scanTexts.joinToString("\n")
            val newlyMatched = mutableListOf<CodexEntryContext>()
            for (entry in active) {
                if (entry.id in matched) continue
                if (ContextMatching.matches(entry, combined) && tryInclude(entry, bypassProbability = false)) {
                    newlyMatched.add(entry)
                }
            }
            if (newlyMatched.isEmpty()) break
            pass++
            scanTexts = newlyMatched.filter { it.recursionAllowed }.map { it.bodyText }
        }

        return matched
    }

    private fun rollProbability(entry: CodexEntryContext, random: Random): Boolean =
        entry.probability >= 100 || random.nextInt(100) < entry.probability

    private fun buildCandidateSections(
        scope: ContextScope,
        trigger: ContextTrigger,
        detectedEntries: List<CodexEntryContext>,
    ): List<ContextSection> = buildList {
        // 0. Series block (Revision 02 §3): premise + prior-member summaries, ahead of every
        //    book-level section including the system/style block below it.
        val series = scope.seriesContext
        if (series != null && !series.isEmpty) {
            val seriesText = buildString {
                if (series.premise.isNotBlank()) appendLine("Series premise: ${series.premise}")
                series.previousMemberSummaries.forEach { appendLine("Previously: $it") }
            }.trim()
            if (seriesText.isNotBlank()) add(section(LABEL_SERIES, seriesText))
        }

        // 1. System/style block (spec §8.3 step 7 — the "project block").
        val systemText = when (scope) {
            is ContextScope.Novel -> listOfNotNull(
                scope.styleGuide.takeIf { it.isNotBlank() }?.let { "Style guide: $it" },
                scope.pov.takeIf { it.isNotBlank() }?.let { "POV: $it" },
                scope.tense.takeIf { it.isNotBlank() }?.let { "Tense: $it" },
                scope.genre.takeIf { it.isNotBlank() }?.let { "Genre: $it" },
            ).joinToString("\n")
            is ContextScope.Roleplay -> listOfNotNull(
                scope.characterCard.takeIf { it.isNotBlank() },
                scope.personaText.takeIf { it.isNotBlank() }?.let { "Persona: $it" },
                scope.scenario.takeIf { it.isNotBlank() }?.let { "Scenario: $it" },
                scope.authorsNote?.takeIf { it.isNotBlank() }?.let { "Author's note: $it" },
            ).joinToString("\n")
        }
        if (systemText.isNotBlank()) add(section(LABEL_SYSTEM, systemText))

        // 2. Constant entries first, then the rest by insertionOrder (already sorted by the caller).
        val (constant, rest) = detectedEntries.partition { it.isConstant || it.alwaysInclude }
        constant.forEach { add(section(it.sectionLabel(), it.bodyText)) }
        rest.forEach { add(section(it.sectionLabel(), it.bodyText)) }

        // 3. Chat history newest-first (roleplay only — novel mode's "history" is previous scenes,
        //    already folded into the scan text, not a separately-budgeted section).
        if (scope is ContextScope.Roleplay) {
            scope.chatHistory.asReversed().forEach { message -> add(section(LABEL_CHAT_HISTORY, message.content)) }
        }

        // 4. Current scene / final turn.
        if (scope is ContextScope.Novel) {
            add(section(LABEL_CURRENT_SCENE, scope.currentSceneText))
        }
    }

    private fun allocateBudget(candidates: List<ContextSection>, budget: TokenBudget): List<ContextSection> {
        var remaining = budget.available
        return candidates.map { candidate ->
            if (candidate.tokenCount <= remaining) {
                remaining -= candidate.tokenCount
                candidate.copy(included = true)
            } else {
                candidate.copy(included = false)
            }
        }
    }

    private fun section(label: String, text: String) = ContextSection(label, text, TokenEstimator.estimate(text), included = false)

    private fun CodexEntryContext.sectionLabel() = "$LABEL_ENTRY_PREFIX$id"

    private const val LABEL_SERIES = "series"
    private const val LABEL_SYSTEM = "system"
    private const val LABEL_CHAT_HISTORY = "chat_history"
    private const val LABEL_CURRENT_SCENE = "current_scene"
    private const val LABEL_ENTRY_PREFIX = "entry:"
}
