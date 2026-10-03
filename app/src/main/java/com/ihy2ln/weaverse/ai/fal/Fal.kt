package com.ihy2ln.weaverse.ai.fal

import com.ihy2ln.weaverse.ai.AIError
import com.ihy2ln.weaverse.ai.ModelInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * fal.ai: a pay-as-you-go platform hosting hundreds of image models (FLUX, Qwen Image Edit,
 * Seedream, Nano Banana, GPT Image…) plus an OpenAI-compatible LLM router.
 * Model refs: `fal/<endpoint id>` for pictures (e.g. `fal/fal-ai/flux-pro/kontext`) and
 * `fal-llm/<model>` for text through fal's router (e.g. `fal-llm/anthropic/claude-sonnet-5.5`).
 *
 * Every fal endpoint has its own input schema, so requests are built from the endpoint's
 * published OpenAPI schema: which field takes the picture, and which turn the endpoint's own
 * safety filter off (Weaverse adds no filter of its own).
 */
object Fal {
    const val IMAGE = "fal/"
    const val CHAT = "fal-llm/"
    const val TAG = "fal.ai"

    fun isFalRef(ref: String): Boolean = ref.startsWith(IMAGE) || ref.startsWith(CHAT)
    fun isImageRef(ref: String): Boolean = ref.startsWith(IMAGE)

    private val json = Json { ignoreUnknownKeys = true }

    private fun image(endpoint: String, name: String, edits: Boolean) = ModelInfo(
        id = IMAGE + endpoint,
        displayName = "fal · $name",
        supportsImages = edits,
        generatesImages = true,
        tags = buildList {
            add("Image generation")
            if (edits) add("Reference editing")
            add(TAG)
        },
    )

    /** Shown before the live catalog has loaded; the editors that suit manga pages come first. */
    val seedImageModels = listOf(
        image("fal-ai/flux-pro/kontext", "FLUX.1 Kontext [pro]", edits = true),
        image("fal-ai/flux-pro/kontext/max", "FLUX.1 Kontext [max]", edits = true),
        image("fal-ai/qwen-image-edit-2511", "Qwen Image Edit 2511", edits = true),
        image("fal-ai/flux-2-pro/edit", "FLUX 2 Pro Edit", edits = true),
        image("bytedance/seedream/v5/pro/edit", "Seedream 5.0 Pro Edit", edits = true),
        image("fal-ai/nano-banana-pro/edit", "Nano Banana Pro Edit", edits = true),
        image("fal-ai/flux/dev", "FLUX.1 [dev]", edits = false),
        image("fal-ai/flux-2-pro", "FLUX 2 Pro", edits = false),
        image("fal-ai/qwen-image-2512", "Qwen Image 2512", edits = false),
    )

    /** Text through fal's OpenRouter-powered router; any OpenRouter model id works there. */
    val chatModels = listOf(
        "anthropic/claude-sonnet-5.5" to "Claude Sonnet 5.5",
        "google/gemini-3.8-flash" to "Gemini 3.8 Flash",
        "openai/gpt-5.6-luna" to "GPT-5.6 Luna",
        "deepseek/deepseek-v4-pro" to "DeepSeek V4 Pro",
        "x-ai/grok-4.7" to "Grok 4.7",
        "mistralai/mistral-large-2512" to "Mistral Large",
    ).map { (id, name) ->
        ModelInfo(id = CHAT + id, displayName = "fal · $name", tags = listOf("Text output", TAG))
    }

    /** fal endpoints that are tools rather than picture makers (cut-outs, upscalers, masks…). */
    private val utility = Regex(
        "background|rembg|upscal|segment|sam-?\\d|sam2|depth|preprocess|vectori|eraser|extract-frame|ffmpeg|" +
            "birefnet|esrgan|aura-sr|layerize|expand|fill|material|try-?on|face-?swap|lora",
        RegexOption.IGNORE_CASE,
    )

    /** One page of `GET https://api.fal.ai/v1/models` → picture models, utilities left out. */
    fun parseCatalog(body: String): Pair<List<ModelInfo>, String?> {
        val root = json.parseToJsonElement(body).jsonObject
        val models = root["models"]?.jsonArray.orEmpty().mapNotNull { entry ->
            runCatching {
                val row = entry.jsonObject
                val endpoint = row.getValue("endpoint_id").jsonPrimitive.content
                val meta = row["metadata"]?.jsonObject ?: return@runCatching null
                if (meta["status"]?.jsonPrimitive?.contentOrNull == "deprecated") return@runCatching null
                val category = meta["category"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (category != "image-to-image" && category != "text-to-image") return@runCatching null
                val tags = meta["tags"]?.jsonArray.orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }
                if (utility.containsMatchIn(endpoint) || tags.any { "utility" in it || "upscal" in it }) return@runCatching null
                val name = meta["display_name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifBlank { endpoint }
                image(endpoint, name, edits = category == "image-to-image")
            }.getOrNull()
        }
        val next = root["next_cursor"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { root["has_more"]?.jsonPrimitive?.booleanOrNull == true && it.isNotBlank() }
        return models to next
    }

    /** What an endpoint's input accepts, read from its OpenAPI schema. */
    data class InputSchema(
        val fields: Set<String>,
        val aspectRatios: List<String> = emptyList(),
        val safetyTolerances: List<String> = emptyList(),
        val outputFormats: List<String> = emptyList(),
    ) {
        val takesImageList: Boolean get() = "image_urls" in fields
        val takesImage: Boolean get() = "image_url" in fields || takesImageList
    }

    fun parseSchema(body: String): InputSchema {
        val schemas = json.parseToJsonElement(body).jsonObject["components"]?.jsonObject
            ?.get("schemas")?.jsonObject ?: throw AIError.EmbeddedError("fal returned no input schema for this model")
        val input = schemas.entries
            .filter { it.key.endsWith("Input") }
            .map { it.value.jsonObject }
            .firstOrNull { it["properties"]?.jsonObject?.containsKey("prompt") == true }
            ?: schemas.values.map { it.jsonObject }.firstOrNull { it["properties"]?.jsonObject?.containsKey("prompt") == true }
            ?: throw AIError.EmbeddedError("This fal model takes no prompt")
        val props = input["properties"]!!.jsonObject
        fun enumOf(field: String): List<String> = props[field]?.let(::enumValues).orEmpty()
        return InputSchema(
            fields = props.keys,
            aspectRatios = enumOf("aspect_ratio"),
            safetyTolerances = enumOf("safety_tolerance"),
            outputFormats = enumOf("output_format"),
        )
    }

    /** Enum values wherever the schema keeps them (directly, or inside anyOf/allOf). */
    private fun enumValues(element: JsonElement): List<String> {
        val obj = element as? JsonObject ?: return emptyList()
        obj["enum"]?.jsonArray?.let { values -> return values.mapNotNull { it.jsonPrimitive.contentOrNull } }
        return listOf("anyOf", "allOf", "oneOf").flatMap { key -> obj[key]?.jsonArray.orEmpty().flatMap(::enumValues) }
    }

    /** `ratio:W:H` tags (the shapes the manga editor pads a page to) from an endpoint's schema. */
    fun ratioTags(schema: InputSchema): List<String> =
        schema.aspectRatios.filter { Regex("\\d+:\\d+").matches(it) }.map { "ratio:$it" }

    /**
     * The request body: the prompt, the pictures as data URIs in whichever field the endpoint
     * names, the endpoint's own safety filter off or at its most permissive, PNG out.
     */
    fun input(schema: InputSchema, prompt: String, imageDataUris: List<String>, aspectRatio: String?): JsonObject {
        val body = linkedMapOf<String, JsonElement>("prompt" to JsonPrimitive(prompt))
        if (imageDataUris.isNotEmpty()) {
            when {
                schema.takesImageList -> body["image_urls"] = JsonArray(imageDataUris.map(::JsonPrimitive))
                "image_url" in schema.fields -> body["image_url"] = JsonPrimitive(imageDataUris.first())
            }
        }
        if ("enable_safety_checker" in schema.fields) body["enable_safety_checker"] = JsonPrimitive(false)
        if ("safety_tolerance" in schema.fields) {
            val most = schema.safetyTolerances.maxByOrNull { it.toIntOrNull() ?: 0 } ?: "5"
            body["safety_tolerance"] = JsonPrimitive(most)
        }
        if ("output_format" in schema.fields && (schema.outputFormats.isEmpty() || "png" in schema.outputFormats)) {
            body["output_format"] = JsonPrimitive("png")
        }
        if (aspectRatio != null && aspectRatio in schema.aspectRatios) body["aspect_ratio"] = JsonPrimitive(aspectRatio)
        if ("num_images" in schema.fields) body["num_images"] = JsonPrimitive(1)
        return JsonObject(body)
    }

    /** The finished request's first picture: its URL (or data URI) and type. */
    fun resultImage(body: String): Pair<String, String?> {
        val root = json.parseToJsonElement(body).jsonObject
        root["detail"]?.let { detail -> throw AIError.BadRequest("fal: " + detailMessage(detail)) }
        val picture = root["images"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: root["image"]?.jsonObject
            ?: throw AIError.EmbeddedError("fal returned no image")
        val nsfw = root["has_nsfw_concepts"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.booleanOrNull == true
        val url = picture["url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: throw AIError.EmbeddedError(if (nsfw) "fal's safety filter declined the picture" else "fal returned no image")
        return url to picture["content_type"]?.jsonPrimitive?.contentOrNull
    }

    /** fal error bodies: `{"detail": "..."}` or `{"detail": [{"msg": "...", "loc": [...]}]}`. */
    fun errorMessage(body: String): String? = runCatching {
        json.parseToJsonElement(body).jsonObject["detail"]?.let(::detailMessage)
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun detailMessage(detail: JsonElement): String = when (detail) {
        is JsonPrimitive -> detail.contentOrNull.orEmpty()
        is JsonArray -> detail.joinToString("; ") { item ->
            val obj = item as? JsonObject
            val loc = obj?.get("loc")?.jsonArray?.joinToString(".") { it.jsonPrimitive.content }.orEmpty()
            val msg = obj?.get("msg")?.jsonPrimitive?.contentOrNull ?: item.toString()
            if (loc.isBlank()) msg else "$loc: $msg"
        }
        else -> detail.toString()
    }.take(400)

    /** fal's HTTP failures as the app's errors (so the manga fallback knows a refusal from a key problem). */
    fun errorFor(code: Int, body: String, retryAfter: Long?): AIError {
        val message = errorMessage(body) ?: body.take(300).ifBlank { "HTTP $code" }
        return when {
            code == 401 -> AIError.InvalidKey("fal.ai rejected the key: $message")
            code == 402 || message.contains("balance", true) || message.contains("locked", true) -> AIError.OutOfCredits
            code == 403 -> AIError.HttpFailure(403, "fal: $message")
            code == 429 -> AIError.RateLimited(retryAfter)
            code == 400 || code == 422 -> AIError.BadRequest("fal: $message")
            code in 500..599 -> AIError.ProviderDown
            else -> AIError.HttpFailure(code, "fal: $message")
        }
    }
}
