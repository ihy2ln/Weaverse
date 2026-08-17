package com.ihy2ln.weaverse.ai

import com.ihy2ln.weaverse.ai.context.AssembledPrompt
import com.ihy2ln.weaverse.ai.openrouter.OpenRouterRepository
import com.ihy2ln.weaverse.ai.providers.OpenRouterProvider
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiGenerationService @Inject constructor(
    private val openRouterProvider: OpenRouterProvider,
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
        val system = assembled?.systemBlocks?.joinToString("\n\n").orEmpty()
        val history = assembled?.messages.orEmpty()
        val request = AIRequest(
            modelId = model.removePrefix("openrouter/"),
            systemPrompt = system,
            messages = history + listOf("user" to userMessage),
            maxTokens = maxTokens,
            temperature = temperature,
            topP = topP,
            stream = true,
            imageAttachments = imageAttachments,
        )
        WeaverseAiLog.i(
            "stream via OpenRouter model=$model endpoint=$ENDPOINT images=${imageAttachments.size}",
        )
        return openRouterProvider.stream(request)
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
        val system = assembled?.systemBlocks?.joinToString("\n\n").orEmpty()
        val history = assembled?.messages.orEmpty()
        val request = AIRequest(
            modelId = model.removePrefix("openrouter/"),
            systemPrompt = system,
            messages = history + listOf("user" to userMessage),
            maxTokens = maxTokens,
            temperature = temperature,
            stream = false,
            imageAttachments = imageAttachments,
        )
        WeaverseAiLog.i("complete via OpenRouter model=$model endpoint=$ENDPOINT")
        return openRouterProvider.complete(request)
    }

    suspend fun modelSupportsImages(modelRef: String? = null): Boolean {
        val model = resolveModelRef(modelRef)
        return openRouterRepository.modelSupportsImages(model)
    }

    suspend fun synthesizeSpeech(text: String, modelId: String, outputFile: File): File =
        openRouterRepository.synthesizeSpeech(text, modelId, outputFile)

    fun hasApiKey(): Boolean = !openRouterRepository.storedApiKey().isNullOrBlank()

    companion object {
        private const val ENDPOINT = "https://openrouter.ai/api/v1"
    }
}
