package com.ihy2ln.weaverse.ai

import android.util.Log
import com.ihy2ln.weaverse.ai.providers.AnthropicProvider
import com.ihy2ln.weaverse.ai.providers.GeminiProvider
import com.ihy2ln.weaverse.ai.providers.OpenAICompatibleProvider
import com.ihy2ln.weaverse.ai.providers.OpenRouterProvider
import com.ihy2ln.weaverse.data.db.entity.AIProviderType
import com.ihy2ln.weaverse.data.db.entity.ConnectionProfileEntity
import com.ihy2ln.weaverse.data.settings.SecretsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes a request to the right [AIProvider] for a connection profile. There is no simulated
 * fallback: a profile with no stored key produces a real [AIChunk.Error] (never a generated-
 * looking response), and every other path always makes a real HTTP call and reports its real
 * outcome — no success is ever reported without a real 2xx from the provider.
 */
@Singleton
class AIService @Inject constructor(
    private val anthropicProvider: AnthropicProvider,
    private val openAICompatibleProvider: OpenAICompatibleProvider,
    private val geminiProvider: GeminiProvider,
    private val openRouterProvider: OpenRouterProvider,
    private val secretsStore: SecretsStore,
) {
    fun stream(profile: ConnectionProfileEntity, request: AIRequest): Flow<AIChunk> {
        val apiKey = secretsStore.getApiKey(profile.id)
        if (apiKey.isNullOrBlank()) {
            return flow { emit(AIChunk.Error(NO_KEY_MESSAGE.format(profile.label))) }
        }
        val provider = providerFor(profile.providerType)
        logResolvedRoute(profile, request, provider)
        return provider.stream(profile.baseUrl, apiKey, request)
    }

    suspend fun complete(profile: ConnectionProfileEntity, request: AIRequest): AIResult {
        val apiKey = secretsStore.getApiKey(profile.id)
            ?: throw IllegalStateException(NO_KEY_MESSAGE.format(profile.label))
        val provider = providerFor(profile.providerType)
        logResolvedRoute(profile, request, provider)
        return provider.complete(profile.baseUrl, apiKey, request)
    }

    suspend fun testConnection(profile: ConnectionProfileEntity): Result<List<ModelInfo>> {
        val apiKey = secretsStore.getApiKey(profile.id)
        if (apiKey.isNullOrBlank()) return Result.failure(IllegalStateException("No API key configured for this profile"))
        return providerFor(profile.providerType).models(profile.baseUrl, apiKey)
    }

    /** Validates a candidate key against the real provider *before* it's ever written to
     * [SecretsStore] — takes [apiKey] directly rather than reading it back out of the store,
     * since at save time it isn't stored yet. A saved-key success message must never be shown
     * without this succeeding first (see [com.ihy2ln.weaverse.feature.novel.aiproviders.ConnectionProfilesViewModel]). */
    suspend fun validateKey(profile: ConnectionProfileEntity, apiKey: String): Result<List<ModelInfo>> =
        providerFor(profile.providerType).models(profile.baseUrl, apiKey)

    private fun providerFor(type: AIProviderType): AIProvider = when (type) {
        AIProviderType.Anthropic -> anthropicProvider
        AIProviderType.OpenAICompatible -> openAICompatibleProvider
        AIProviderType.Gemini -> geminiProvider
        AIProviderType.OpenRouter -> openRouterProvider
    }

    /** Revision 02 §5: "the app must route it through the OpenRouter profile — log the resolved
     * provider + model + endpoint on each send." Applies to every provider, not just OpenRouter,
     * since a resolved-route log is equally useful for verifying any profile picked the right
     * connection (and this is the one chokepoint every send already passes through). */
    private fun logResolvedRoute(profile: ConnectionProfileEntity, request: AIRequest, provider: AIProvider) {
        Log.d(
            "AIService",
            "Routing to ${provider.type} (profile '${profile.label}') — model=${request.model}, endpoint=${profile.baseUrl}",
        )
    }

    private companion object {
        const val NO_KEY_MESSAGE = "No API key configured for connection profile \"%s\" — add one in Settings -> AI Providers before generating."
    }
}
