package com.ihy2ln.weaverse.ai.fal

import com.ihy2ln.weaverse.ai.ModelInfo
import com.ihy2ln.weaverse.ai.providers.OpenAiProvider
import com.ihy2ln.weaverse.data.settings.SecureKeyStore
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/** Text models through fal's OpenAI-compatible router (`fal-llm/<model>`), billed to the fal key. */
@Singleton
class FalChatProvider @Inject constructor(
    settings: SettingsRepository,
    okHttpClient: OkHttpClient,
) : OpenAiProvider(settings, okHttpClient) {
    override val name: String = "fal.ai"
    override val baseUrl: String = "https://fal.run/openrouter/router/openai/v1"
    override val keyId: String = SecureKeyStore.FAL
    override val modelPrefix: String = Fal.CHAT
    override fun authorization(key: String): String = "Key $key"

    override suspend fun models(): List<ModelInfo> = Fal.chatModels
}
