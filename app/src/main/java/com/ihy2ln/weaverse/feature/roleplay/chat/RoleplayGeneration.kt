package com.ihy2ln.weaverse.feature.roleplay.chat

import com.ihy2ln.weaverse.ai.context.AssembledPrompt
import com.ihy2ln.weaverse.ai.context.ContextMeter
import com.ihy2ln.weaverse.ai.context.ContextMeterReading
import com.ihy2ln.weaverse.ai.prompt.RoleplayPromptBuilder
import com.ihy2ln.weaverse.data.db.entities.RpCharacterEntity
import com.ihy2ln.weaverse.data.db.entities.RpPersonaEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoleplayGeneration @Inject constructor() {
    /**
     * [contextTokens] is the model's context window and [reserveTokens] the room kept for the
     * reply. When the chat no longer fits, the oldest turns are left out (the newest always
     * stay) and the model is told so, instead of the request failing at the provider.
     */
    fun assemble(
        character: RpCharacterEntity?,
        persona: RpPersonaEntity?,
        history: List<Pair<String, String>>,
        outputWords: Int,
        difficultyDirective: String?,
        extraSystem: List<String> = emptyList(),
        contextTokens: Int? = null,
        reserveTokens: Int = 0,
    ): AssembledPrompt {
        val system = RoleplayPromptBuilder.systemBlocks(
            character = character,
            persona = persona,
            outputWords = outputWords,
        ) + listOfNotNull(difficultyDirective) + extraSystem
        val budget = contextTokens?.let { it - reserveTokens - system.sumOf(ContextMeter::estimateTokens) - TRIM_NOTE_TOKENS }
        val kept = if (budget == null) history else fitHistory(history, budget)
        val dropped = history.size - kept.size
        return AssembledPrompt(
            systemBlocks = if (dropped > 0) system + "The $dropped oldest messages of this roleplay are left out to fit the " +
                "model's context. Stay consistent with the character, the lorebook and the recent messages below." else system,
            messages = kept,
            usedEntries = emptyList(),
            tokenBreakdown = emptyList(),
        )
    }

    fun meter(
        assembled: AssembledPrompt,
        extraUser: String,
        limitTokens: Int,
    ): ContextMeterReading = ContextMeter.reading(assembled, extraUser, limitTokens)

    companion object {
        private const val TRIM_NOTE_TOKENS = 60
        /** The latest exchanges always go along, even past the budget. */
        const val MIN_KEPT_MESSAGES = 4

        /** The newest messages whose estimated tokens fit [budgetTokens], oldest dropped first. */
        fun fitHistory(history: List<Pair<String, String>>, budgetTokens: Int): List<Pair<String, String>> {
            var used = 0
            var keep = 0
            for (i in history.indices.reversed()) {
                val cost = ContextMeter.estimateTokens(history[i].second) + 4
                if (keep >= MIN_KEPT_MESSAGES && used + cost > budgetTokens) break
                used += cost
                keep++
            }
            return history.takeLast(keep)
        }
    }
}
