package com.ihy2ln.weaverse.ai.openrouter

import com.ihy2ln.weaverse.ai.AIError
import com.ihy2ln.weaverse.ai.ImageAttachment
import com.ihy2ln.weaverse.ai.ModelInfo
import kotlinx.serialization.json.*
import java.util.Base64

/** Dedicated Image API, deliberately separate from the chat model catalog. */
object OpenRouterImageApi {
    private val json = Json { ignoreUnknownKeys = true }

    fun editingModels(body: String): List<ModelInfo> {
        val root = json.parseToJsonElement(body).jsonObject
        root["error"]?.takeUnless { it is JsonNull }?.let { throw AIError.EmbeddedError(it.toString().take(300)) }
        return root["data"]?.jsonArray.orEmpty().mapNotNull { entry ->
            runCatching {
                val row = entry.jsonObject
                val id = row.getValue("id").jsonPrimitive.content
                val arch = row.getValue("architecture").jsonObject
                val inputs = arch["input_modalities"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }
                val outputs = arch["output_modalities"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }
                val params = row["supported_parameters"]?.jsonObject
                val maxRefs = params?.get("input_references")?.jsonObject?.get("max")?.jsonPrimitive?.intOrNull ?: 0
                if ("text" !in inputs || "image" !in inputs || "image" !in outputs || maxRefs < 1 || id.contains(":batch") ||
                    id.contains("vector", true)) return@runCatching null
                ModelInfo(id = id, displayName = row["name"]?.jsonPrimitive?.content ?: id,
                    supportsImages = true, generatesImages = true, tags = listOf("Image API", "Reference editing") +
                        params?.get("aspect_ratio")?.jsonObject?.get("values")?.jsonArray.orEmpty()
                            .map { "ratio:${it.jsonPrimitive.content}" })
            }.getOrNull()
        }.distinctBy { it.id }.sortedBy { it.displayName.lowercase() }
    }

    fun request(model: String, prompt: String, images: List<ImageAttachment>, aspectRatio: String? = null): String = buildJsonObject {
        put("model", model)
        put("prompt", prompt)
        if (aspectRatio != null) put("aspect_ratio", aspectRatio)
        if (images.isNotEmpty()) put("input_references", buildJsonArray {
            images.forEach { image -> add(buildJsonObject {
                put("type", "image_url")
                put("image_url", buildJsonObject { put("url", "data:${image.mimeType};base64,${image.base64Data}") })
            }) }
        })
    }.toString()

    fun decode(body: String): Pair<ByteArray, String> {
        val root = json.parseToJsonElement(body).jsonObject
        root["error"]?.takeUnless { it is JsonNull }?.let { throw AIError.EmbeddedError(it.toString().take(300)) }
        val item = root["data"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: throw AIError.EmbeddedError("The Image API returned no image. The provider may have declined the request.")
        val bytes = Base64.getDecoder().decode(item.getValue("b64_json").jsonPrimitive.content)
        // Sniff raster formats instead of trusting a missing or incorrect media_type.
        val mime = when {
            bytes.size >= 8 && bytes.take(8) == listOf(137,80,78,71,13,10,26,10).map { it.toByte() } -> "image/png"
            bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte() -> "image/jpeg"
            bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
            else -> throw AIError.EmbeddedError("The model returned an unsupported or invalid image format.")
        }
        return bytes to mime
    }
}
