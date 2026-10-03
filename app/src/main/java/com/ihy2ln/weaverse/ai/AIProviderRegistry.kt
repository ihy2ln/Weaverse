package com.ihy2ln.weaverse.ai

import com.ihy2ln.weaverse.ai.providers.AnthropicProvider
import com.ihy2ln.weaverse.ai.providers.GeminiProvider
import com.ihy2ln.weaverse.ai.providers.OpenAiProvider
import com.ihy2ln.weaverse.ai.providers.OpenRouterProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AIProviderRegistry @Inject constructor(
    private val openRouterProvider: OpenRouterProvider,
    private val openAi: OpenAiProvider,
    private val anthropic: AnthropicProvider,
    private val gemini: GeminiProvider,
    private val pcHarness: com.ihy2ln.weaverse.ai.pc.PcHarnessProvider,
) {
    fun resolve(modelRef: String): AIProvider {
        WeaverseAiLog.i("resolve modelRef=$modelRef")
        return when {
            modelRef.isBlank() -> throw AIError.NoProvider("Model reference is blank")
            modelRef.startsWith("mock/") -> throw AIError.NoProvider(
                "Mock providers removed. Configure a provider and pick a live model.",
            )
            modelRef.startsWith(com.ihy2ln.weaverse.ai.pc.PcHarness.COMFY) -> throw AIError.NoProvider(
                "$modelRef is a ComfyUI picture workflow; pick a text model for writing.",
            )
            com.ihy2ln.weaverse.ai.pc.PcHarness.isPcRef(modelRef) -> pcHarness
            modelRef.startsWith("openai/") -> openAi
            modelRef.startsWith("anthropic/") -> anthropic
            modelRef.startsWith("gemini/") -> gemini
            modelRef.startsWith("openrouter/") -> openRouterProvider
            modelRef.contains("/") -> openRouterProvider
            else -> throw AIError.NoProvider("Unknown model reference: $modelRef")
        }
    }

    fun stripProviderPrefix(modelRef: String): String = when {
        // PC harness refs keep their prefix: it says which CLI runs them.
        com.ihy2ln.weaverse.ai.pc.PcHarness.isPcRef(modelRef) -> modelRef
        modelRef.startsWith("openai/") -> modelRef.removePrefix("openai/")
        modelRef.startsWith("anthropic/") -> modelRef.removePrefix("anthropic/")
        modelRef.startsWith("gemini/") -> modelRef.removePrefix("gemini/")
        else -> modelRef.removePrefix("openrouter/")
    }

    fun openRouter(): OpenRouterProvider = openRouterProvider
}
