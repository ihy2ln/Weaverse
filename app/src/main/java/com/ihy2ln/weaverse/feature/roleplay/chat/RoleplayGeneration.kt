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
    fun assemble(
        character: RpCharacterEntity?,
        persona: RpPersonaEntity?,
        history: List<Pair<String, String>>,
        outputWords: Int,
        difficultyDirective: String?,
        extraSystem: List<String> = emptyList(),
    ): AssembledPrompt {
        val system = RoleplayPromptBuilder.systemBlocks(
            character = character,
            persona = persona,
            outputWords = outputWords,
        ) + listOfNotNull(difficultyDirective) + extraSystem
        return AssembledPrompt(
            systemBlocks = system,
            messages = history,
            usedEntries = emptyList(),
            tokenBreakdown = emptyList(),
        )
    }

    fun meter(
        assembled: AssembledPrompt,
        extraUser: String,
        limitTokens: Int,
    ): ContextMeterReading = ContextMeter.reading(assembled, extraUser, limitTokens)
}
