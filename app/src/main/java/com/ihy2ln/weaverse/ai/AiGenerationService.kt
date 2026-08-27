package com.ihy2ln.weaverse.ai

import com.ihy2ln.weaverse.ai.context.AssembledPrompt
import com.ihy2ln.weaverse.ai.openrouter.OpenRouterRepository
import com.ihy2ln.weaverse.data.settings.SecureKeyStore
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiGenerationService @Inject constructor(
    private val registry: AIProviderRegistry,
    private val openRouterRepository: OpenRouterRepository,
    private val settings: SettingsRepository,
) {
    suspend fun resolveModelRef(override: String? = null): String {
        if (!override.isNullOrBlank()) return override
        return settings.preferences.first().defaultModelRef
    }

    suspend fun stream(
        userMessage: String,
        assembled: AssembledPrompt? = null,
        modelRef: String? = null,
        maxTokens: Int? = null,
        temperature: Double? = null,
        topP: Double? = null,
        imageAttachments: List<ImageAttachment> = emptyList(),
    ): Flow<AIChunk> {
        val model = resolveModelRef(modelRef)
        val provider = registry.resolve(model)
        val system = assembled?.systemBlocks?.joinToString("\n\n").orEmpty()
        val history = assembled?.messages.orEmpty()
        val request = AIRequest(
            modelId = registry.stripProviderPrefix(model),
            systemPrompt = system,
            messages = history + listOf("user" to userMessage),
            maxTokens = maxTokens,
            temperature = temperature,
            topP = topP,
            stream = true,
            imageAttachments = imageAttachments,
        )
        WeaverseAiLog.i("stream via ${provider.name} model=$model images=${imageAttachments.size}")
        return flow {
            var attempt = 0
            while (true) {
                try {
                    provider.stream(request).collect { chunk ->
                        if (chunk is AIChunk.Usage) {
                            settings.recordUsage(chunk.promptTokens, chunk.completionTokens, chunk.cost)
                        }
                        emit(chunk)
                    }
                    return@flow
                } catch (e: AIError.RateLimited) {
                    if (attempt >= AiRetry.MAX_ATTEMPTS) throw e
                    val wait = AiRetry.waitSeconds(e.retryAfterSeconds, attempt)
                    var left = wait.toInt()
                    while (left > 0) {
                        emit(AIChunk.RetryWait(left))
                        delay(1_000)
                        left--
                    }
                    attempt++
                }
            }
        }
    }

    suspend fun complete(
        userMessage: String,
        assembled: AssembledPrompt? = null,
        modelRef: String? = null,
        maxTokens: Int? = null,
        temperature: Double? = null,
        imageAttachments: List<ImageAttachment> = emptyList(),
    ): AIResult {
        val model = resolveModelRef(modelRef)
        val provider = registry.resolve(model)
        val system = assembled?.systemBlocks?.joinToString("\n\n").orEmpty()
        val history = assembled?.messages.orEmpty()
        val request = AIRequest(
            modelId = registry.stripProviderPrefix(model),
            systemPrompt = system,
            messages = history + listOf("user" to userMessage),
            maxTokens = maxTokens,
            temperature = temperature,
            stream = false,
            imageAttachments = imageAttachments,
        )
        WeaverseAiLog.i("complete via ${provider.name} model=$model")
        var attempt = 0
        while (true) {
            try {
                val result = provider.complete(request)
                settings.recordUsage(result.promptTokens, result.completionTokens, result.cost)
                return result
            } catch (e: AIError.RateLimited) {
                if (attempt >= AiRetry.MAX_ATTEMPTS) throw e
                delay(AiRetry.waitSeconds(e.retryAfterSeconds, attempt) * 1000)
                attempt++
            }
        }
    }

    suspend fun modelSupportsImages(modelRef: String? = null): Boolean {
        val model = resolveModelRef(modelRef)
        if (!model.startsWith("openrouter/") && !model.contains("/")) return false
        return openRouterRepository.modelSupportsImages(model)
    }

    suspend fun synthesizeSpeech(text: String, modelId: String, outputFile: File): File =
        openRouterRepository.synthesizeSpeech(text, modelId, outputFile)

    fun hasApiKey(modelRef: String? = null): Boolean {
        val ref = modelRef.orEmpty()
        return when {
            ref.startsWith("openai/") -> !settings.apiKey(SecureKeyStore.OPENAI).isNullOrBlank()
            ref.startsWith("anthropic/") -> !settings.apiKey(SecureKeyStore.ANTHROPIC).isNullOrBlank()
            ref.startsWith("gemini/") -> !settings.apiKey(SecureKeyStore.GEMINI).isNullOrBlank()
            ref.startsWith("openrouter/") -> !openRouterRepository.storedApiKey().isNullOrBlank()
            else -> listOf(
                SecureKeyStore.OPENROUTER,
                SecureKeyStore.OPENAI,
                SecureKeyStore.ANTHROPIC,
                SecureKeyStore.GEMINI,
            ).any { !settings.apiKey(it).isNullOrBlank() }
        }
    }
}
