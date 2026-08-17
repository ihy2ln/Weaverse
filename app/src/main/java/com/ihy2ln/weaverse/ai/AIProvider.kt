package com.ihy2ln.weaverse.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class ModelInfo(
    val id: String,
    val displayName: String,
    val contextLength: Int? = null,
    val promptPricePerMillion: Double? = null,
    val completionPricePerMillion: Double? = null,
    val available: Boolean = true,
    /** True when model architecture lists speech/audio output (TTS). */
    val isTts: Boolean = false,
    /** True when model accepts image inputs (vision). */
    val supportsImages: Boolean = false,
    /** Display tags e.g. TTS, Vision. */
    val tags: List<String> = emptyList(),
)

data class ImageAttachment(
    val mimeType: String,
    val base64Data: String,
)

data class AIRequest(
    val modelId: String,
    val systemPrompt: String = "",
    val messages: List<Pair<String, String>> = emptyList(),
    val maxTokens: Int? = null,
    val temperature: Double? = null,
    val topP: Double? = null,
    val stream: Boolean = true,
    /** Attached to the last user message as multimodal content parts. */
    val imageAttachments: List<ImageAttachment> = emptyList(),
)

sealed class AIChunk {
    data class Delta(val text: String) : AIChunk()
    data class Usage(
        val promptTokens: Int,
        val completionTokens: Int,
        val totalTokens: Int = promptTokens + completionTokens,
        val cost: Double? = null,
    ) : AIChunk()
    data object Done : AIChunk()
}

data class AIResult(
    val text: String,
    val providerName: String,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
    val cost: Double? = null,
)

sealed class AIError : Exception() {
    abstract override val message: String

    class NoApiKey : AIError() {
        override val message: String = "Configure OpenRouter API key in Settings → AI Connections"
    }

    class NoProvider(override val message: String = "No AI provider configured for this model") : AIError()

    class InvalidKey(override val message: String = "Invalid API key") : AIError()

    data object OutOfCredits : AIError() {
        override val message: String = "Out of credits"
    }

    data class RateLimited(val retryAfterSeconds: Long?) : AIError() {
        override val message: String =
            if (retryAfterSeconds != null) "Rate limited — retry after ${retryAfterSeconds}s"
            else "Rate limited"
    }

    class BadRequest(override val message: String) : AIError()

    data object ProviderDown : AIError() {
        override val message: String = "Provider temporarily unavailable"
    }

    class NoNetwork(cause: Throwable? = null) : AIError() {
        override val message: String = "No network connection"
        init { cause?.let { initCause(it) } }
    }

    class HttpFailure(val statusCode: Int, override val message: String) : AIError()

    class EmbeddedError(override val message: String) : AIError()
}

interface AIProvider {
    val name: String
    suspend fun models(): List<ModelInfo>
    fun stream(request: AIRequest): Flow<AIChunk>
    suspend fun complete(request: AIRequest): AIResult
}
