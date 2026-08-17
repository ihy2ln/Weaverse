package com.ihy2ln.weaverse.ai.providers

import com.ihy2ln.weaverse.ai.AIChunk
import com.ihy2ln.weaverse.ai.AIProvider
import com.ihy2ln.weaverse.ai.AIRequest
import com.ihy2ln.weaverse.ai.AIResult
import com.ihy2ln.weaverse.ai.ModelInfo
import com.ihy2ln.weaverse.ai.openrouter.OpenRouterRepository
import kotlinx.coroutines.flow.Flow

class OpenRouterProvider(
    private val repository: OpenRouterRepository,
) : AIProvider {
    override val name: String = OpenRouterRepository.PROVIDER_NAME

    override suspend fun models(): List<ModelInfo> = repository.fetchModels(forceRefresh = false)

    override fun stream(request: AIRequest): Flow<AIChunk> = repository.streamCompletion(request)

    override suspend fun complete(request: AIRequest): AIResult = repository.complete(request)
}
