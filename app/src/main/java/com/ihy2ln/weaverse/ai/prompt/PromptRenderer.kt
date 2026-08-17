package com.ihy2ln.weaverse.ai.prompt

import com.ihy2ln.weaverse.data.db.entities.PromptEntity

data class RenderedPrompt(
    val systemText: String,
    val messages: List<Pair<String, String>>,
)

/** Turns a [PromptEntity]'s message boxes into what [com.ihy2ln.weaverse.ai.AIRequest] expects. */
object PromptRenderer {
    fun render(prompt: PromptEntity?, ctx: PromptRenderContext): RenderedPrompt {
        val messages = prompt?.let { decodePromptMessages(it.instructionsJson) }.orEmpty()
        val systemParts = mutableListOf<String>()
        val turns = mutableListOf<Pair<String, String>>()

        messages.forEach { message ->
            val resolved = PromptTemplateEngine.render(message.content, ctx)
            if (resolved.isBlank()) return@forEach
            when (message.role.lowercase()) {
                PromptRole.System.name.lowercase() -> systemParts += resolved
                PromptRole.Ai.name.lowercase() -> turns += "assistant" to resolved
                else -> turns += "user" to resolved
            }
        }

        return RenderedPrompt(
            systemText = systemParts.joinToString("\n\n"),
            messages = turns,
        )
    }
}
