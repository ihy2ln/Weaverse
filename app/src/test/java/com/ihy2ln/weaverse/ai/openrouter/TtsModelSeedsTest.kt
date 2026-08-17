package com.ihy2ln.weaverse.ai.openrouter

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TtsModelSeedsTest {
    @Test
    fun tagsSpeechOutputModelsAsTts() {
        val dto = OpenRouterModelDto(
            id = "openai/gpt-4o-mini-tts",
            name = "GPT-4o Mini TTS",
            architecture = OpenRouterArchitecture(
                modality = "text->speech",
                inputModalities = listOf("text"),
                outputModalities = listOf("speech"),
            ),
        )
        assertTrue(dto.isSpeechOutput())
    }

    @Test
    fun resolveTtsSeedsGreysMissing() {
        val resolved = TtsModelSeeds.resolveTtsModels(emptyList())
        assertTrue(resolved.any { it.id.contains("tts") && !it.available })
        assertTrue(resolved.all { it.isTts || it.tags.contains("TTS") })
    }

    @Test
    fun supportsImageInputFromArchitecture() {
        val dto = OpenRouterModelDto(
            id = "google/gemini-flash",
            architecture = OpenRouterArchitecture(
                inputModalities = listOf("text", "image"),
                outputModalities = listOf("text"),
            ),
        )
        assertTrue(dto.supportsImageInput())
        assertFalse(
            OpenRouterModelDto(id = "deepseek/deepseek-v4-flash").supportsImageInput(),
        )
    }

    @Test
    fun isTextGenerationKeepsChatModelsAndDropsTtsOnly() {
        val chat = OpenRouterModelDto(
            id = "deepseek/deepseek-v4-flash",
            architecture = OpenRouterArchitecture(
                modality = "text->text",
                inputModalities = listOf("text"),
                outputModalities = listOf("text"),
            ),
        )
        val tts = OpenRouterModelDto(
            id = "openai/gpt-4o-mini-tts",
            architecture = OpenRouterArchitecture(
                modality = "text->speech",
                inputModalities = listOf("text"),
                outputModalities = listOf("speech"),
            ),
        )
        assertTrue(chat.isTextGeneration())
        assertFalse(tts.isTextGeneration())
        assertTrue(OpenRouterModelDto(id = "unknown/no-architecture").isTextGeneration())
    }
}
