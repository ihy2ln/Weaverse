package com.ihy2ln.weaverse.ai.openrouter

import com.ihy2ln.weaverse.ai.AIError
import com.ihy2ln.weaverse.ai.ModelInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WritingModelSeedsTest {
    @Test
    fun resolvesExactIdsFromLiveList() {
        val live = listOf(
            ModelInfo("deepseek/deepseek-v4-flash", "DeepSeek V4 Flash"),
            ModelInfo("google/gemma-4-31b-it-20260402", "Gemma 4"),
        )
        val resolved = WritingModelSeeds.resolveWritingModels(live)
        assertTrue(resolved.any { it.id == "deepseek/deepseek-v4-flash" && it.available })
        assertTrue(resolved.any { it.id == "google/gemma-4-31b-it-20260402" && it.available })
    }

    @Test
    fun greysOutMissingExactIds() {
        val resolved = WritingModelSeeds.resolveWritingModels(emptyList())
        val missing = resolved.first { it.id == "deepseek/deepseek-v4-flash" }
        assertFalse(missing.available)
    }

    @Test
    fun resolvesPrefixHintWhenPresent() {
        val live = listOf(ModelInfo("openai/gpt-5.6-luna", "GPT 5.6 Luna"))
        val resolved = WritingModelSeeds.resolveWritingModels(live)
        assertTrue(resolved.any { it.id == "openai/gpt-5.6-luna" && it.displayName == "OpenAI GPT-5.6 Luna" })
    }

    @Test
    fun resolveAllWritingModelsIncludesEveryLiveTextModel() {
        val live = (1..40).map { index ->
            ModelInfo("vendor/writer-$index", "Writer $index")
        } + ModelInfo("openai/tts-only", "TTS", isTts = true)
        val resolved = WritingModelSeeds.resolveAllWritingModels(live)
        assertEquals(40, resolved.count { it.id.startsWith("vendor/writer-") && it.available })
        assertFalse(resolved.any { it.isTts })
        assertTrue(resolved.size >= 40)
    }

    @Test
    fun noApiKeyHasHelpfulMessage() {
        val err = AIError.NoApiKey()
        assertEquals("Configure OpenRouter API key in Settings → AI Connections", err.message)
    }
}
