package com.ihy2ln.weaverse.ai.openrouter

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class OpenRouterKeyResponse(
    val data: OpenRouterKeyData? = null,
    val error: OpenRouterErrorBody? = null,
)

@Serializable
data class OpenRouterKeyData(
    val label: String? = null,
    val usage: Double? = null,
    @SerialName("limit") val limit: Double? = null,
    @SerialName("limit_remaining") val limitRemaining: Double? = null,
    @SerialName("rate_limit") val rateLimit: OpenRouterRateLimit? = null,
    @SerialName("is_free_tier") val isFreeTier: Boolean? = null,
)

@Serializable
data class OpenRouterRateLimit(
    val requests: Int? = null,
    val interval: String? = null,
)

@Serializable
data class OpenRouterModelsResponse(
    val data: List<OpenRouterModelDto> = emptyList(),
    val error: OpenRouterErrorBody? = null,
)

@Serializable
data class OpenRouterModelDto(
    val id: String,
    val name: String? = null,
    @SerialName("context_length") val contextLength: Int? = null,
    val pricing: OpenRouterPricing? = null,
    val architecture: OpenRouterArchitecture? = null,
)

@Serializable
data class OpenRouterArchitecture(
    val modality: String? = null,
    @SerialName("input_modalities") val inputModalities: List<String> = emptyList(),
    @SerialName("output_modalities") val outputModalities: List<String> = emptyList(),
)

@Serializable
data class OpenRouterPricing(
    val prompt: String? = null,
    val completion: String? = null,
)

@Serializable
data class OpenRouterChatRequest(
    val model: String,
    val messages: List<OpenRouterChatMessage>,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val temperature: Double? = null,
    @SerialName("top_p") val topP: Double? = null,
    val stream: Boolean = false,
    val reasoning: OpenRouterReasoning? = null,
    /** Ask image-output models to actually return a picture. */
    val modalities: List<String>? = null,
)

@Serializable
data class OpenRouterImage(
    val type: String = "image_url",
    @SerialName("image_url") val imageUrl: OpenRouterImageUrl? = null,
)

@Serializable
data class OpenRouterImageUrl(
    val url: String = "",
)

@Serializable
data class OpenRouterReasoning(
    val effort: String = "minimal",
    val exclude: Boolean = true,
)

@Serializable
data class OpenRouterChatMessage(
    val role: String = "",
    /** Plain string or multimodal content array (JsonArray of parts). */
    val content: JsonElement = kotlinx.serialization.json.JsonPrimitive(""),
    /** Image-output models return generated pictures here as data URLs. */
    val images: List<OpenRouterImage> = emptyList(),
)

@Serializable
data class OpenRouterChatResponse(
    val choices: List<OpenRouterChoice> = emptyList(),
    val usage: OpenRouterUsage? = null,
    val error: OpenRouterErrorBody? = null,
)

@Serializable
data class OpenRouterChoice(
    val message: OpenRouterChatMessage? = null,
    val delta: OpenRouterChatMessage? = null,
)

@Serializable
data class OpenRouterUsage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0,
    val cost: Double? = null,
)

@Serializable
data class OpenRouterStreamChunk(
    val choices: List<OpenRouterChoice> = emptyList(),
    val usage: OpenRouterUsage? = null,
    val error: OpenRouterErrorBody? = null,
)

@Serializable
data class OpenRouterErrorBody(
    val message: String? = null,
    val code: JsonElement? = null,
)

@Serializable
data class OpenRouterSpeechRequest(
    val model: String,
    val input: String,
    val voice: String = "alloy",
    @SerialName("response_format") val responseFormat: String = "mp3",
)

fun OpenRouterModelDto.supportsImageInput(): Boolean {
    if (architecture?.inputModalities?.any { it.equals("image", ignoreCase = true) } == true) return true
    val modality = architecture?.modality.orEmpty().lowercase()
    return modality.contains("image")
}

/** True when the model generates images (text-to-image, e.g. Nano Banana, Flux). */
fun OpenRouterModelDto.generatesImages(): Boolean {
    val outputs = architecture?.outputModalities.orEmpty()
    if (outputs.any { it.equals("image", ignoreCase = true) }) return true
    val id = id.lowercase()
    val modality = architecture?.modality.orEmpty().lowercase()
    // "text->image" style modality strings.
    if (modality.contains("->image") || modality.endsWith("-> image")) return true
    val imageOnlyIds = listOf("flux", "dall-e", "dalle", "stable-diffusion", "sdxl", "imagen", "seedream", "recraft", "ideogram", "gpt-image")
    return imageOnlyIds.any { id.contains(it) } && !modality.contains("text->text")
}

fun OpenRouterModelDto.isSpeechOutput(): Boolean {
    if (architecture?.outputModalities?.any {
            it.equals("speech", ignoreCase = true) || it.equals("audio", ignoreCase = true)
        } == true
    ) {
        return true
    }
    val id = id.lowercase()
    val name = name.orEmpty().lowercase()
    val modality = architecture?.modality.orEmpty().lowercase()
    return id.contains("tts") ||
        id.contains("speech") ||
        name.contains("tts") ||
        name.contains("text-to-speech") ||
        modality.contains("speech") ||
        (modality.contains("audio") && !modality.contains("text->text"))
}

/** True when the model can generate writing text (not TTS-only). */
fun OpenRouterModelDto.isTextGeneration(): Boolean {
    val outputs = architecture?.outputModalities.orEmpty()
    if (outputs.isNotEmpty() && outputs.none { it.equals("text", ignoreCase = true) }) {
        return false
    }
    val modality = architecture?.modality.orEmpty().lowercase()
    if (modality.contains("->") && !modality.contains("->text") && modality.contains("speech")) {
        return false
    }
    if (isSpeechOutput() && outputs.none { it.equals("text", ignoreCase = true) } &&
        !modality.contains("text->text")
    ) {
        return false
    }
    return true
}

fun OpenRouterChatMessage.textContent(): String = when (content) {
    is kotlinx.serialization.json.JsonPrimitive -> content.content
    else -> content.toString()
}
