package com.ihy2ln.weaverse.ai

import com.ihy2ln.weaverse.ai.context.AssembledPrompt
import com.ihy2ln.weaverse.ai.openrouter.OpenRouterRepository
import com.ihy2ln.weaverse.ai.prompt.PromptAddOns
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
    private val pcBridge: com.ihy2ln.weaverse.ai.pc.PcBridgeClient,
    private val fal: com.ihy2ln.weaverse.ai.fal.FalClient,
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
        val system = PromptAddOns.applyTo(assembled?.systemBlocks.orEmpty()).joinToString("\n\n")
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
            var wroteText = false
            while (true) {
                try {
                    provider.stream(request).collect { chunk ->
                        if (chunk is AIChunk.Usage) {
                            settings.recordUsage(chunk.promptTokens, chunk.completionTokens, chunk.cost)
                        }
                        if (chunk is AIChunk.Delta) wroteText = true
                        emit(chunk)
                    }
                    return@flow
                } catch (e: AIError) {
                    // Once text has streamed, starting over would repeat it on screen; only
                    // failures before the first word are retried.
                    if (!AiRetry.isRetryable(e) || wroteText || attempt >= AiRetry.MAX_ATTEMPTS) throw e
                    val wait = AiRetry.waitSecondsFor(e, attempt)
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
        val system = PromptAddOns.applyTo(assembled?.systemBlocks.orEmpty()).joinToString("\n\n")
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
            } catch (e: AIError) {
                if (!AiRetry.isRetryable(e) || attempt >= AiRetry.MAX_ATTEMPTS) throw e
                delay(AiRetry.waitSecondsFor(e, attempt) * 1000)
                attempt++
            }
        }
    }

    suspend fun modelSupportsImages(modelRef: String? = null): Boolean {
        val model = resolveModelRef(modelRef)
        if (com.ihy2ln.weaverse.ai.pc.PcHarness.isPcRef(model)) {
            return com.ihy2ln.weaverse.ai.pc.PcHarness.supportsImages(model, pcBridge.capabilities.value)
        }
        if (com.ihy2ln.weaverse.ai.fal.Fal.isFalRef(model)) return false
        if (!model.startsWith("openrouter/") && !model.contains("/")) return false
        return openRouterRepository.modelSupportsImages(model)
    }

    suspend fun synthesizeSpeech(text: String, modelId: String, outputFile: File): File =
        openRouterRepository.synthesizeSpeech(text, modelId, outputFile)

    /**
     * Image generation: an OpenRouter image-output model (Nano Banana, Flux, GPT-Image…),
     * a fal.ai endpoint, or a ComfyUI workflow on the PC. Returns picture bytes + mime type.
     */
    suspend fun generateImage(
        prompt: String,
        modelRef: String?,
        imageAttachments: List<ImageAttachment> = emptyList(),
        aspectRatio: String? = null,
    ): Pair<ByteArray, String> {
        val model = resolveModelRef(modelRef)
        if (model.startsWith(com.ihy2ln.weaverse.ai.pc.PcHarness.COMFY)) {
            // ComfyUI on the user's PC: the first attached picture is the one edited.
            val source = imageAttachments.firstOrNull()
                ?: throw AIError.BadRequest("ComfyUI workflows here edit a picture; attach one.")
            return pcBridge.editImage(model.removePrefix(com.ihy2ln.weaverse.ai.pc.PcHarness.COMFY), prompt, source)
        }
        if (model.startsWith(com.ihy2ln.weaverse.ai.fal.Fal.IMAGE)) {
            return fal.generateImage(model.removePrefix(com.ihy2ln.weaverse.ai.fal.Fal.IMAGE), prompt, imageAttachments, aspectRatio)
        }
        if (!model.startsWith("openrouter/")) {
            throw AIError.HttpFailure(
                statusCode = 400,
                message = "Image generation needs an image model (see Settings → Models → Image generation).",
            )
        }
        if (!hasApiKey(model)) {
            throw AIError.NoApiKey()
        }
        return openRouterRepository.generateImage(
            modelId = model.removePrefix("openrouter/"),
            prompt = prompt,
            imageAttachments = imageAttachments,
            aspectRatio = aspectRatio,
        )
    }

    /** Catalog tags of an OpenRouter image-editing model; empty when unknown. */
    suspend fun imageModelTags(modelRef: String?): List<String> {
        val model = resolveModelRef(modelRef)
        if (model.startsWith(com.ihy2ln.weaverse.ai.fal.Fal.IMAGE)) return fal.ratioTags(model.removePrefix(com.ihy2ln.weaverse.ai.fal.Fal.IMAGE))
        if (!model.startsWith("openrouter/")) return emptyList()
        return runCatching { openRouterRepository.imageEditingModelTags(model.removePrefix("openrouter/")) }
            .getOrDefault(emptyList())
    }

    fun hasApiKey(modelRef: String? = null): Boolean {
        val ref = modelRef.orEmpty()
        return when {
            com.ihy2ln.weaverse.ai.pc.PcHarness.isPcRef(ref) -> pcBridge.isConfigured()
            com.ihy2ln.weaverse.ai.fal.Fal.isFalRef(ref) -> fal.hasKey()
            ref.startsWith("openai/") -> !settings.apiKey(SecureKeyStore.OPENAI).isNullOrBlank()
            ref.startsWith("anthropic/") -> !settings.apiKey(SecureKeyStore.ANTHROPIC).isNullOrBlank()
            ref.startsWith("gemini/") -> !settings.apiKey(SecureKeyStore.GEMINI).isNullOrBlank()
            ref.startsWith("openrouter/") -> !openRouterRepository.storedApiKey().isNullOrBlank()
            else -> listOf(
                SecureKeyStore.OPENROUTER,
                SecureKeyStore.OPENAI,
                SecureKeyStore.ANTHROPIC,
                SecureKeyStore.GEMINI,
                SecureKeyStore.FAL,
            ).any { !settings.apiKey(it).isNullOrBlank() }
        }
    }
}
