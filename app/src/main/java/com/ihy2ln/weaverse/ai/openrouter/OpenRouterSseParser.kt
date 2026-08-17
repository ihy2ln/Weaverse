package com.ihy2ln.weaverse.ai.openrouter

import com.ihy2ln.weaverse.ai.AIChunk
import com.ihy2ln.weaverse.ai.AIError
import kotlinx.serialization.json.Json

object OpenRouterSseParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    sealed class ParseResult {
        data object Ignore : ParseResult()
        data object Done : ParseResult()
        data class Chunks(val chunks: List<AIChunk>) : ParseResult()
    }

    fun parseLine(line: String): ParseResult {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith(":")) return ParseResult.Ignore
        if (!trimmed.startsWith("data:")) return ParseResult.Ignore
        val payload = trimmed.removePrefix("data:").trim()
        if (payload == "[DONE]") return ParseResult.Done
        val parsed = runCatching {
            json.decodeFromString<OpenRouterStreamChunk>(payload)
        }.getOrElse { return ParseResult.Ignore }
        parsed.error?.let { throw OpenRouterErrorMapper.fromEmbeddedError(it) ?: AIError.EmbeddedError("Unknown API error") }
        val out = mutableListOf<AIChunk>()
        parsed.choices.firstOrNull()?.delta?.textContent()?.takeIf { it.isNotEmpty() }?.let {
            out += AIChunk.Delta(it)
        }
        parsed.usage?.let { usage ->
            out += AIChunk.Usage(
                promptTokens = usage.promptTokens,
                completionTokens = usage.completionTokens,
                totalTokens = usage.totalTokens.takeIf { it > 0 } ?: (usage.promptTokens + usage.completionTokens),
                cost = usage.cost,
            )
        }
        return if (out.isEmpty()) ParseResult.Ignore else ParseResult.Chunks(out)
    }
}
