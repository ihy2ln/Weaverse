package com.ihy2ln.weaverse.ai.openrouter

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

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

fun OpenRouterArchitecture?.inputSide(): String {
    val modality = this?.modality.orEmpty().lowercase()
    return modality.substringBefore("->", modality)
}

fun OpenRouterArchitecture?.outputSide(): String {
    val modality = this?.modality.orEmpty().lowercase()
    return if (modality.contains("->")) modality.substringAfter("->") else ""
}

fun OpenRouterModelDto.supportsImageInput(): Boolean {
    if (architecture?.inputModalities?.any { it.equals("image", ignoreCase = true) } == true) return true
    // "modality" is a "in->out" string, e.g. "text->image" or "text+image->text". A bare
    // .contains("image") also matches text->image (image-only OUTPUT, e.g. Flux/DALL-E),
    // which falsely marks a pure text-to-image model as accepting an image to edit. Only the
    // side left of "->" describes what the model accepts.
    if (architecture.inputSide().contains("image")) return true
    if (architecture != null) return false
    val haystack = "$id ${name.orEmpty()}".lowercase()
    return VISION_ID_HINTS.any { haystack.contains(it) }
}

/** True when the model generates images (text-to-image, e.g. Nano Banana, Flux). */
fun OpenRouterModelDto.generatesImages(): Boolean {
    val outputs = architecture?.outputModalities.orEmpty()
    if (outputs.any { it.equals("image", ignoreCase = true) }) return true
    if (architecture.outputSide().contains("image")) return true
    val id = id.lowercase()
    val modality = architecture?.modality.orEmpty().lowercase()
    val imageOnlyIds = listOf(
        "flux", "dall-e", "dalle", "stable-diffusion", "sdxl", "imagen",
        "seedream", "recraft", "ideogram", "gpt-image", "nano-banana",
    )
    return imageOnlyIds.any { id.contains(it) } && !architecture.outputSide().contains("text") &&
        !modality.contains("text->text")
}

fun OpenRouterModelDto.isSpeechOutput(): Boolean {
    if (architecture?.outputModalities?.any {
            it.equals("speech", ignoreCase = true) || it.equals("audio", ignoreCase = true)
        } == true
    ) {
        return true
    }
    if (architecture.outputSide().contains("speech") || architecture.outputSide().contains("audio")) {
        return true
    }
    val id = id.lowercase()
    val name = name.orEmpty().lowercase()
    val modality = architecture?.modality.orEmpty().lowercase()
    return id.contains("tts") ||
        id.contains("speech") ||
        name.contains("tts") ||
        name.contains("text-to-speech") ||
        (modality.contains("speech") && architecture.outputSide().isNotEmpty()) ||
        (modality.contains("audio") && !architecture.outputSide().contains("text"))
}

/** True when the model can generate writing text (not TTS-only / image-only). */
fun OpenRouterModelDto.isTextGeneration(): Boolean {
    val outputs = architecture?.outputModalities.orEmpty()
    if (outputs.isNotEmpty() && outputs.none { it.equals("text", ignoreCase = true) }) {
        return false
    }
    val outputSide = architecture.outputSide()
    if (outputSide.isNotEmpty() && !outputSide.contains("text")) {
        return false
    }
    if (isSpeechOutput() && outputs.none { it.equals("text", ignoreCase = true) } &&
        !outputSide.contains("text")
    ) {
        return false
    }
    return true
}

/**
 * OpenRouter's GET /models defaults to text output only. Parse the body per-model so one
 * malformed catalog entry cannot empty Vision / Image generation.
 */
fun parseOpenRouterModelsBody(json: Json, body: String): OpenRouterModelsResponse {
    val root = json.parseToJsonElement(body)
    val obj = root as? JsonObject
    val error = obj?.get("error")?.let { el ->
        runCatching { json.decodeFromJsonElement(OpenRouterErrorBody.serializer(), el) }.getOrNull()
    }
    val dataElement = obj?.get("data") ?: root
    val array = dataElement as? JsonArray ?: return OpenRouterModelsResponse(error = error)
    val models = array.mapNotNull { el ->
        runCatching { json.decodeFromJsonElement(OpenRouterModelDto.serializer(), el) }.getOrNull()
    }
    return OpenRouterModelsResponse(data = models, error = error)
}

private val VISION_ID_HINTS = listOf(
    "gpt-5.6-luna",
    "gpt-luna",
    "-vision",
    "vision-",
    "pixtral",
    "qwen-vl",
    "qwen2-vl",
    "qwen2.5-vl",
    "gpt-4o",
    "grok-2-vision",
)

fun OpenRouterChatMessage.textContent(): String = when (content) {
    is kotlinx.serialization.json.JsonPrimitive -> content.content
    else -> content.toString()
}
