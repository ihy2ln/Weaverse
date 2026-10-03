package com.ihy2ln.weaverse.ai.pc

import com.ihy2ln.weaverse.feature.prompt.PromptModelSelection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PcHarnessTest {
    @Test
    fun pcRefsAreKeptWhileOpenRouterIdsGetTheirPrefix() {
        assertEquals("claudecode/sonnet", PromptModelSelection.modelRef("claudecode/sonnet"))
        assertEquals("comfy/qwen-image-edit", PromptModelSelection.modelRef("comfy/qwen-image-edit"))
        assertEquals("openrouter/openai/gpt-5", PromptModelSelection.modelRef("openai/gpt-5"))
        assertTrue(PcHarness.isPcRef("codex/default"))
        assertFalse(PcHarness.isPcRef("openrouter/anthropic/claude-sonnet-4"))
    }

    @Test
    fun onlyAvailableHarnessesBecomeModels() {
        val caps = PcCapabilities(
            listOf(
                PcHarnessStatus("claude", true, "Claude Code 2.1", listOf("sonnet", "haiku")),
                PcHarnessStatus("codex", false, "not installed"),
                PcHarnessStatus("comfyui", true, "ComfyUI 0.37", listOf("qwen-image-edit", "my-flow")),
            ),
        )
        val (text, images) = PcHarness.modelsFrom(caps)
        assertEquals(listOf("claudecode/sonnet", "claudecode/haiku"), text.map { it.id })
        assertTrue(text.all { it.supportsImages && "Text output" in it.tags })
        assertEquals(listOf("comfy/qwen-image-edit", "comfy/my-flow"), images.map { it.id })
        assertTrue(images.all { it.generatesImages })
    }

    @Test
    fun ollamaModelsAreLocalTextModelsAndOnlyVisionOnesReadPictures() {
        val caps = PcCapabilities(
            listOf(
                PcHarnessStatus(
                    "ollama", true, "Ollama 0.35.1 · 2 local models",
                    models = listOf("huihui_ai/qwen3-vl-abliterated", "artifish/llama3.2-uncensored"),
                    vision = listOf("huihui_ai/qwen3-vl-abliterated"),
                ),
            ),
        )
        val (text, images) = PcHarness.modelsFrom(caps)
        assertEquals(listOf("ollama/huihui_ai/qwen3-vl-abliterated", "ollama/artifish/llama3.2-uncensored"), text.map { it.id })
        assertEquals(listOf(true, false), text.map { it.supportsImages })
        assertTrue(text.all { "Local" in it.tags })
        assertTrue(images.isEmpty())
        assertTrue(PcHarness.isPcRef("ollama/qwen3.6"))
        assertEquals("ollama/qwen3.6", PromptModelSelection.modelRef("ollama/qwen3.6"))
        assertTrue(PcHarness.supportsImages("ollama/huihui_ai/qwen3-vl-abliterated", caps))
        assertFalse(PcHarness.supportsImages("ollama/artifish/llama3.2-uncensored", caps))
        assertFalse(PcHarness.supportsImages("ollama/qwen3.6", null))
    }
}
