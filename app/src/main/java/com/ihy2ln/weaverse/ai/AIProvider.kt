package com.ihy2ln.weaverse.ai

import com.ihy2ln.weaverse.data.db.entity.AIProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

enum class AIMessageRole { System, User, Assistant }

data class AIMessage(val role: AIMessageRole, val content: String)

/** The pricing/context fields are null for every provider except [com.ihy2ln.weaverse.ai.providers.OpenRouterProvider]
 * (spec Revision 02 §5: "show context window and per-million input/output pricing on each row") —
 * optional rather than a separate OpenRouter-only model type so every existing model dropdown/list
 * UI keeps working unchanged for the other three providers. */
data class ModelInfo(
    val id: String,
    val displayName: String,
    val contextLength: Int? = null,
    val pricePerMillionInputTokens: Double? = null,
    val pricePerMillionOutputTokens: Double? = null,
    val modality: String? = null,
)

/** Also the shape persisted as [com.ihy2ln.weaverse.data.db.entity.PresetEntity.paramsJson] (Phase 11's Presets screen). */
@Serializable
data class AIRequestParams(
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val maxTokens: Int = 1024,
    val stopSequences: List<String> = emptyList(),
)

data class AIRequest(
    val model: String,
    val systemPrompt: String? = null,
    val messages: List<AIMessage>,
    val params: AIRequestParams = AIRequestParams(),
)

/** One chunk of a streamed generation — spec §8.1 `stream(request: AIRequest): Flow<AIChunk>`. */
sealed interface AIChunk {
    data class Delta(val text: String) : AIChunk
    data class Done(val fullText: String, val inputTokens: Int?, val outputTokens: Int?) : AIChunk
    data class Error(val message: String) : AIChunk
}

data class AIResult(val text: String, val inputTokens: Int?, val outputTokens: Int?)

/**
 * One provider implementation (spec §8.1). [baseUrl]/[apiKey] are call
 * parameters rather than constructor state because a single provider class
 * is shared across every [com.ihy2ln.weaverse.data.db.entity.ConnectionProfileEntity]
 * of that type — e.g. one `OpenAICompatibleProvider` instance serves an
 * OpenRouter profile and a local Ollama profile alike, just pointed at
 * different base URLs.
 */
interface AIProvider {
    val type: AIProviderType

    suspend fun models(baseUrl: String, apiKey: String): Result<List<ModelInfo>>

    fun stream(baseUrl: String, apiKey: String, request: AIRequest): Flow<AIChunk>

    suspend fun complete(baseUrl: String, apiKey: String, request: AIRequest): AIResult
}
