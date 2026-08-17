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

/** Google Gemini's `generateContent`/`streamGenerateContent` REST API (spec §8.1). */
@Singleton
class GeminiProvider @Inject constructor(private val client: HttpClient) : AIProvider {
    override val type: AIProviderType = AIProviderType.Gemini

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun models(baseUrl: String, apiKey: String): Result<List<ModelInfo>> = runCatching {
        val response = client.get("${baseUrl.trimEnd('/')}/v1beta/models?key=$apiKey")
        if (!response.status.isSuccess()) error(response.describeFailure("Gemini"))
        val body: GeminiModelsResponse = response.body()
        body.models.map { ModelInfo(id = it.name.removePrefix("models/"), displayName = it.displayName ?: it.name) }
    }

    override fun stream(baseUrl: String, apiKey: String, request: AIRequest): Flow<AIChunk> = flow {
        val requestBody = GeminiRequestBody(
            contents = request.messages
                .filter { it.role != AIMessageRole.System }
                .map { GeminiContent(role = if (it.role == AIMessageRole.User) "user" else "model", parts = listOf(GeminiPart(it.content))) },
            systemInstruction = request.systemPrompt?.takeIf { it.isNotBlank() }
                ?.let { GeminiContent(role = "system", parts = listOf(GeminiPart(it))) },
            generationConfig = GeminiGenerationConfig(
                temperature = request.params.temperature,
                topP = request.params.topP,
                topK = request.params.topK,
                maxOutputTokens = request.params.maxTokens,
                stopSequences = request.params.stopSequences.ifEmpty { null },
            ),
        )

        val builder = StringBuilder()
        var inputTokens: Int? = null
        var outputTokens: Int? = null
        val url = "${baseUrl.trimEnd('/')}/v1beta/models/${request.model}:streamGenerateContent?alt=sse&key=$apiKey"

        client.preparePost(url) {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(GeminiRequestBody.serializer(), requestBody))
        }.execute { response ->
            if (!response.status.isSuccess()) {
                emit(AIChunk.Error(response.describeFailure("Gemini")))
                return@execute
            }
            response.bodyAsChannel().forEachSseDataLine { data ->
                val event = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return@forEachSseDataLine
                val candidate = event["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                val text = candidate?.get("content")?.jsonObject?.get("parts")?.jsonArray
                    ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
                if (!text.isNullOrEmpty()) {
                    builder.append(text)
                    emit(AIChunk.Delta(text))
                }
                event["usageMetadata"]?.jsonObject?.let { usage ->
                    inputTokens = usage["promptTokenCount"]?.jsonPrimitive?.content?.toIntOrNull()
                    outputTokens = usage["candidatesTokenCount"]?.jsonPrimitive?.content?.toIntOrNull()
                }
            }
            emit(AIChunk.Done(fullText = builder.toString(), inputTokens = inputTokens, outputTokens = outputTokens))
        }
    }.catch { throwable -> emit(AIChunk.Error(throwable.message ?: "Gemini streaming failed")) }

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
}

@Serializable
private data class GeminiRequestBody(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiGenerationConfig? = null,
)

@Serializable
private data class GeminiContent(val role: String, val parts: List<GeminiPart>)

@Serializable
private data class GeminiPart(val text: String)

@Serializable
private data class GeminiGenerationConfig(
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val maxOutputTokens: Int? = null,
    val stopSequences: List<String>? = null,
)

@Serializable
private data class GeminiModelsResponse(val models: List<GeminiModelInfo> = emptyList())

@Serializable
private data class GeminiModelInfo(val name: String, val displayName: String? = null)
