package com.ihy2ln.weaverse.ai.openrouter

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenRouterModelsTest {
    @Test
    fun reasoningCanBeKeptSmallAndExcludedFromVisibleReply() {
        val payload = Json { encodeDefaults = true }.encodeToString(
            OpenRouterChatRequest(
                model = "deepseek/test",
                messages = listOf(OpenRouterChatMessage("user", JsonPrimitive("Act"))),
                reasoning = OpenRouterReasoning(effort = "minimal", exclude = true),
            ),
        )

        assertTrue(payload.contains("\"reasoning\""))
        assertTrue(payload.contains("\"effort\":\"minimal\""))
        assertTrue(payload.contains("\"exclude\":true"))
    }

    /**
     * A pure text-to-image model's modality string is "text->image" — the word "image"
     * appears only because that is its OUTPUT. A naive substring check on the whole string
     * used to read that as "accepts an image", so a txt2img-only model (Flux, DALL-E, SDXL…)
     * would pass as an image-EDIT model, get picked for a colorize/retype request, and
     * silently ignore the uploaded picture while generating something unrelated.
     */
    @Test
    fun textToImageOnlyModelDoesNotClaimToAcceptAnImage() {
        val txt2img = OpenRouterModelDto(
            id = "black-forest-labs/flux-schnell",
            architecture = OpenRouterArchitecture(modality = "text->image"),
        )

        assertFalse(txt2img.supportsImageInput())
        assertTrue(txt2img.generatesImages())
    }

    @Test
    fun trueImageEditModelAcceptsAndProducesAnImage() {
        val imageEdit = OpenRouterModelDto(
            id = "google/gemini-2.5-flash-image",
            architecture = OpenRouterArchitecture(
                modality = "text+image->text+image",
                inputModalities = listOf("text", "image"),
                outputModalities = listOf("text", "image"),
            ),
        )

        assertTrue(imageEdit.supportsImageInput())
        assertTrue(imageEdit.generatesImages())
    }

    @Test
    fun visionOnlyModelAcceptsAnImageButDoesNotGenerateOne() {
        val visionOnly = OpenRouterModelDto(
            id = "openai/gpt-5.6-luna",
            architecture = OpenRouterArchitecture(
                modality = "text+image->text",
                inputModalities = listOf("text", "image"),
                outputModalities = listOf("text"),
            ),
        )

        assertTrue(visionOnly.supportsImageInput())
        assertFalse(visionOnly.generatesImages())
    }

    @Test
    fun textPlusImageOutputCountsAsImageGeneration() {
        val both = OpenRouterModelDto(
            id = "google/gemini-2.5-flash-image",
            architecture = OpenRouterArchitecture(modality = "text+image->text+image"),
        )
        assertTrue(both.supportsImageInput())
        assertTrue(both.generatesImages())
        assertTrue(both.isTextGeneration())
    }

    @Test
    fun lunaWithoutArchitectureStillCountsAsVisionFromId() {
        val luna = OpenRouterModelDto(id = "openai/gpt-5.6-luna", name = "GPT-5.6 Luna")
        assertTrue(luna.supportsImageInput())
        assertTrue(luna.isTextGeneration())
        assertFalse(luna.generatesImages())
    }

    @Test
    fun catalogPutsLunaInWritingAndVision() {
        val luna = OpenRouterModelDto(
            id = "openai/gpt-5.6-luna",
            name = "OpenAI: GPT-5.6 Luna",
            architecture = OpenRouterArchitecture(
                modality = "text+image+file->text",
                inputModalities = listOf("file", "image", "text"),
                outputModalities = listOf("text"),
            ),
        )
        val flux = OpenRouterModelDto(
            id = "black-forest-labs/flux-schnell",
            name = "Flux Schnell",
            architecture = OpenRouterArchitecture(modality = "text->image"),
        )
        val geminiImage = OpenRouterModelDto(
            id = "google/gemini-2.5-flash-image",
            name = "Gemini Flash Image",
            architecture = OpenRouterArchitecture(
                modality = "text+image->text+image",
                inputModalities = listOf("text", "image"),
                outputModalities = listOf("text", "image"),
            ),
        )
        val catalog = listOf(luna, flux, geminiImage)
        val writing = OpenRouterModelCatalog.writingModels(catalog).map { it.id }
        val vision = OpenRouterModelCatalog.visionModels(catalog).map { it.id }
        val image = OpenRouterModelCatalog.imageModels(catalog).map { it.id }

        assertTrue(writing.contains("openai/gpt-5.6-luna"))
        assertTrue(vision.contains("openai/gpt-5.6-luna"))
        assertFalse(image.contains("openai/gpt-5.6-luna"))

        assertFalse(writing.contains("black-forest-labs/flux-schnell"))
        assertFalse(vision.contains("black-forest-labs/flux-schnell"))
        assertTrue(image.contains("black-forest-labs/flux-schnell"))

        assertTrue(writing.contains("google/gemini-2.5-flash-image"))
        assertTrue(vision.contains("google/gemini-2.5-flash-image"))
        assertTrue(image.contains("google/gemini-2.5-flash-image"))
    }

    @Test
    fun parseModelsBodyKeepsGoodRowsWhenOneIsMalformed() {
        val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
        val body = """
            {"data":[
              {"id":"openai/gpt-5.6-luna","name":"Luna","architecture":{"modality":"text+image+file->text","input_modalities":["text","image","file"],"output_modalities":["text"]}},
              {"name":"missing-id"},
              {"id":"black-forest-labs/flux-schnell","architecture":{"modality":"text->image","output_modalities":["image"]}}
            ]}
        """.trimIndent()
        val parsed = parseOpenRouterModelsBody(json, body)
        assertTrue(parsed.data.any { it.id == "openai/gpt-5.6-luna" && it.supportsImageInput() })
        assertTrue(parsed.data.any { it.id == "black-forest-labs/flux-schnell" && it.generatesImages() })
        assertEquals(2, parsed.data.size)
    }
}
