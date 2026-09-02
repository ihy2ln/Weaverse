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
) {
    fun resolve(modelRef: String): AIProvider {
        WeaverseAiLog.i("resolve modelRef=$modelRef")
        return when {
            modelRef.isBlank() -> throw AIError.NoProvider("Model reference is blank")
            modelRef.startsWith("mock/") -> throw AIError.NoProvider(
                "Mock providers removed. Configure a provider and pick a live model.",
            )
            modelRef.startsWith("openai/") -> openAi
            modelRef.startsWith("anthropic/") -> anthropic
            modelRef.startsWith("gemini/") -> gemini
            modelRef.startsWith("openrouter/") -> openRouterProvider
            modelRef.contains("/") -> openRouterProvider
            else -> throw AIError.NoProvider("Unknown model reference: $modelRef")
        }
    }

    fun stripProviderPrefix(modelRef: String): String = when {
        modelRef.startsWith("openai/") -> modelRef.removePrefix("openai/")
        modelRef.startsWith("anthropic/") -> modelRef.removePrefix("anthropic/")
        modelRef.startsWith("gemini/") -> modelRef.removePrefix("gemini/")
        else -> modelRef.removePrefix("openrouter/")
    }

    fun openRouter(): OpenRouterProvider = openRouterProvider
}
