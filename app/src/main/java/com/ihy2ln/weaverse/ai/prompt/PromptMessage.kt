package com.ihy2ln.weaverse.ai.prompt

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One message box in a prompt's editor — System, User, or AI (assistant). */
enum class PromptRole { System, User, Ai }

@Serializable
data class PromptMessage(
    val role: String,
    val content: String,
)

private val promptMessagesJson = Json { ignoreUnknownKeys = true }

/**
 * Decodes [PromptEntity.instructionsJson]. Falls back to treating legacy data
 * (a JSON array of plain paragraph strings, joined blank-line separated) as a
 * single System message, so prompts saved before the multi-message format
 * still open cleanly in the editor instead of showing empty boxes.
 */
fun decodePromptMessages(json: String): List<PromptMessage> {
    runCatching {
        val messages = promptMessagesJson.decodeFromString<List<PromptMessage>>(json)
        if (messages.isNotEmpty() && messages.all { it.role.isNotBlank() }) return messages
    }
    val legacyParagraphs = runCatching {
        promptMessagesJson.decodeFromString<List<String>>(json)
    }.getOrDefault(emptyList())
    val joined = legacyParagraphs.filter { it.isNotBlank() }.joinToString("\n\n")
    return if (joined.isBlank()) emptyList() else listOf(PromptMessage(PromptRole.System.name.lowercase(), joined))
}

fun encodePromptMessages(messages: List<PromptMessage>): String =
    promptMessagesJson.encodeToString(messages.filter { it.content.isNotBlank() })
