package com.ihy2ln.weaverse.ai.providers

import com.ihy2ln.weaverse.ai.AIChunk
import com.ihy2ln.weaverse.ai.AIMessage
import com.ihy2ln.weaverse.ai.AIMessageRole
import com.ihy2ln.weaverse.ai.AIProvider
import com.ihy2ln.weaverse.ai.AIRequest
import com.ihy2ln.weaverse.ai.AIResult
import com.ihy2ln.weaverse.ai.ModelInfo
import com.ihy2ln.weaverse.data.db.entity.AIProviderType
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Any endpoint speaking the OpenAI Chat Completions wire format — OpenAI
 * itself, OpenRouter, DeepSeek, Together, KoboldCpp, Ollama, LM Studio, or
 * a custom base URL (spec §8.1). [baseUrl] already includes whatever path
 * prefix the target expects up to (not including) `/chat/completions`.
 */
@Singleton
class OpenAICompatibleProvider @Inject constructor(private val client: HttpClient) : AIProvider {
    override val type: AIProviderType = AIProviderType.OpenAICompatible

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun models(baseUrl: String, apiKey: String): Result<List<ModelInfo>> = runCatching {
        val response = client.get("${baseUrl.trimEnd('/')}/models") {
            if (apiKey.isNotBlank()) bearerAuth(apiKey)
        }
        if (!response.status.isSuccess()) error(response.describeFailure("This provider"))
        val body: OpenAiModelsResponse = response.body()
        body.data.map { ModelInfo(id = it.id, displayName = it.id) }
    }

    override fun stream(baseUrl: String, apiKey: String, request: AIRequest): Flow<AIChunk> = flow {
        val requestBody = OpenAiRequestBody(
            model = request.model,
            messages = buildList {
                if (!request.systemPrompt.isNullOrBlank()) add(OpenAiMessage("system", request.systemPrompt))
                addAll(request.messages.map { OpenAiMessage(roleOf(it), it.content) })
            },
            stream = true,
            temperature = request.params.temperature,
            top_p = request.params.topP,
            max_tokens = request.params.maxTokens,
            stop = request.params.stopSequences.ifEmpty { null },
        )

        val builder = StringBuilder()
        var inputTokens: Int? = null
        var outputTokens: Int? = null

        client.preparePost("${baseUrl.trimEnd('/')}/chat/completions") {
            if (apiKey.isNotBlank()) bearerAuth(apiKey)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(OpenAiRequestBody.serializer(), requestBody))
        }.execute { response ->
            if (!response.status.isSuccess()) {
                emit(AIChunk.Error(response.describeFailure("This provider")))
                return@execute
            }
            response.bodyAsChannel().forEachSseDataLine { data ->
                val event = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return@forEachSseDataLine
                val choice = event["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                val text = choice?.get("delta")?.jsonObject?.get("content")?.jsonPrimitive?.content
                if (!text.isNullOrEmpty()) {
                    builder.append(text)
                    emit(AIChunk.Delta(text))
                }
                event["usage"]?.jsonObject?.let { usage ->
                    inputTokens = usage["prompt_tokens"]?.jsonPrimitive?.content?.toIntOrNull()
                    outputTokens = usage["completion_tokens"]?.jsonPrimitive?.content?.toIntOrNull()
                }
            }
            emit(AIChunk.Done(fullText = builder.toString(), inputTokens = inputTokens, outputTokens = outputTokens))
        }
    }.catch { throwable -> emit(AIChunk.Error(throwable.message ?: "Streaming failed")) }

    override suspend fun complete(baseUrl: String, apiKey: String, request: AIRequest): AIResult {
        var fullText = ""
        var inputTokens: Int? = null
        var outputTokens: Int? = null
        stream(baseUrl, apiKey, request).lastOrNull()?.let { last ->
            if (last is AIChunk.Done) {
                fullText = last.fullText
                inputTokens = last.inputTokens
                outputTokens = last.outputTokens
            }
        }
        return AIResult(fullText, inputTokens, outputTokens)
    }

    private fun roleOf(message: AIMessage): String = when (message.role) {
        AIMessageRole.System -> "system"
        AIMessageRole.User -> "user"
        AIMessageRole.Assistant -> "assistant"
    }
}

@Serializable
private data class OpenAiRequestBody(
    val model: String,
    val messages: List<OpenAiMessage>,
    val stream: Boolean = true,
    val temperature: Float? = null,
    val top_p: Float? = null,
    val max_tokens: Int? = null,
    val stop: List<String>? = null,
)

@Serializable
private data class OpenAiMessage(val role: String, val content: String)

@Serializable
private data class OpenAiModelsResponse(val data: List<OpenAiModelInfo> = emptyList())

@Serializable
private data class OpenAiModelInfo(val id: String)
