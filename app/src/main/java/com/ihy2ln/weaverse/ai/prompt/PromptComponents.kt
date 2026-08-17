package com.ihy2ln.weaverse.ai.prompt

import com.ihy2ln.weaverse.data.db.entities.BookEntity
import com.ihy2ln.weaverse.data.repo.PromptRepository
import kotlinx.coroutines.flow.first

/** The `type` folder/prompts of this kind live under — seeded, editable Prompt Components. */
const val PromptComponentType: String = "component"

/**
 * Resolves `{include("Weaverse/X")}` component bodies. `Codex` and `Personas`
 * pull real, already-computed data (not stored prose); everything else is a
 * regular single-message [com.ihy2ln.weaverse.data.db.entities.PromptEntity]
 * in the "Prompt Components" folder, editable like any other prompt.
 */
object PromptComponents {
    suspend fun build(
        promptRepository: PromptRepository,
        codexBlock: String,
        book: BookEntity?,
    ): Map<String, String> {
        val stored = promptRepository.observeByType(PromptComponentType).first()
            .associate { it.name to firstMessageContent(it) }
        return stored + mapOf(
            "Codex" to codexBlock,
            "Personas" to personasBlock(book),
        )
    }

    private fun personasBlock(book: BookEntity?): String {
        val styleGuide = book?.styleGuide?.trim().orEmpty()
        return if (styleGuide.isBlank()) "" else "Style guide:\n$styleGuide"
    }

    private fun firstMessageContent(prompt: com.ihy2ln.weaverse.data.db.entities.PromptEntity): String =
        decodePromptMessages(prompt.instructionsJson).firstOrNull()?.content.orEmpty()
}
