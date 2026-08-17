package com.ihy2ln.weaverse.ai.providers

import com.ihy2ln.weaverse.ai.AIChunk
import com.ihy2ln.weaverse.ai.AIMessageRole
import com.ihy2ln.weaverse.ai.AIProvider
import com.ihy2ln.weaverse.ai.AIRequest
import com.ihy2ln.weaverse.ai.AIResult
import com.ihy2ln.weaverse.ai.ModelInfo
import com.ihy2ln.weaverse.data.db.entity.AIProviderType
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnthropicProvider @Inject constructor(private val client: HttpClient) : AIProvider {
    override val type: AIProviderType = AIProviderType.Anthropic

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun models(baseUrl: String, apiKey: String): Result<List<ModelInfo>> = runCatching {
        val response = client.get("${baseUrl.trimEnd('/')}/v1/models") {
            header("x-api-key", apiKey)
            header("anthropic-version", API_VERSION)
        }
        if (!response.status.isSuccess()) error(response.describeFailure("Anthropic"))
        val body: AnthropicModelsResponse = response.body()
        body.data.map { ModelInfo(id = it.id, displayName = it.display_name ?: it.id) }
    }

    override fun stream(baseUrl: String, apiKey: String, request: AIRequest): Flow<AIChunk> = flow {
        val requestBody = AnthropicRequestBody(
            model = request.model,
            max_tokens = request.params.maxTokens,
            system = request.systemPrompt,
            messages = request.messages
                .filter { it.role != AIMessageRole.System }
                .map { AnthropicMessage(role = if (it.role == AIMessageRole.User) "user" else "assistant", content = it.content) },
            stream = true,
            temperature = request.params.temperature,
            top_p = request.params.topP,
            top_k = request.params.topK,
            stop_sequences = request.params.stopSequences.ifEmpty { null },
        )

        val builder = StringBuilder()
        var inputTokens: Int? = null
        var outputTokens: Int? = null

        client.preparePost("${baseUrl.trimEnd('/')}/v1/messages") {
            header("x-api-key", apiKey)
            header("anthropic-version", API_VERSION)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(AnthropicRequestBody.serializer(), requestBody))
        }.execute { response ->
            if (!response.status.isSuccess()) {
                emit(AIChunk.Error(response.describeFailure("Anthropic")))
                return@execute
            }
            response.bodyAsChannel().forEachSseDataLine { data ->
                val event = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return@forEachSseDataLine
                when (event["type"]?.jsonPrimitive?.content) {
                    "content_block_delta" -> {
                        val text = event["delta"]?.jsonObject?.get("text")?.jsonPrimitive?.content
                        if (!text.isNullOrEmpty()) {
                            builder.append(text)
                            emit(AIChunk.Delta(text))
                        }
                    }
                    "message_start" -> {
                        inputTokens = event["message"]?.jsonObject?.get("usage")?.jsonObject
                            ?.get("input_tokens")?.jsonPrimitive?.content?.toIntOrNull()
                    }
                    "message_delta" -> {
                        outputTokens = event["usage"]?.jsonObject?.get("output_tokens")?.jsonPrimitive?.content?.toIntOrNull()
                    }
                }
            }
            emit(AIChunk.Done(fullText = builder.toString(), inputTokens = inputTokens, outputTokens = outputTokens))
        }
    }.catch { throwable -> emit(AIChunk.Error(throwable.message ?: "Anthropic streaming failed")) }

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

    private companion object {
        const val API_VERSION = "2023-06-01"
    }
}

@Serializable
private data class AnthropicRequestBody(
    val model: String,
    val max_tokens: Int,
    val system: String? = null,
    val messages: List<AnthropicMessage>,
    val stream: Boolean = true,
    val temperature: Float? = null,
    val top_p: Float? = null,
    val top_k: Int? = null,
    val stop_sequences: List<String>? = null,
)

@Serializable
private data class AnthropicMessage(val role: String, val content: String)

@Serializable
private data class AnthropicModelsResponse(val data: List<AnthropicModelInfo> = emptyList())

@Serializable
private data class AnthropicModelInfo(val id: String, val display_name: String? = null)
