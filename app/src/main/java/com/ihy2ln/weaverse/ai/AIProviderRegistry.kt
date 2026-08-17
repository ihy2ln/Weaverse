package com.ihy2ln.weaverse.ai

import com.ihy2ln.weaverse.ai.openrouter.OpenRouterRepository
import com.ihy2ln.weaverse.ai.providers.OpenRouterProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AIProviderRegistry @Inject constructor(
    private val openRouterRepository: OpenRouterRepository,
) {
    private val openRouter by lazy { OpenRouterProvider(openRouterRepository) }

    fun resolve(modelRef: String): AIProvider {
        WeaverseAiLog.i("resolve modelRef=$modelRef provider=OpenRouter endpoint=${OpenRouterRepository.BASE_URL}")
        return when {
            modelRef.isBlank() -> throw AIError.NoProvider("Model reference is blank")
            modelRef.startsWith("mock/") -> throw AIError.NoProvider(
                "Mock providers removed. Configure OpenRouter and pick a live model.",
            )
            modelRef.startsWith("openrouter/") -> openRouter
            modelRef.contains("/") -> openRouter
            else -> throw AIError.NoProvider("Unknown model reference: $modelRef")
        }
    }

    fun openRouter(): OpenRouterProvider = openRouter
}
