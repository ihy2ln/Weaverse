package com.ihy2ln.weaverse.ai.providers

import com.ihy2ln.weaverse.ai.AIChunk
import com.ihy2ln.weaverse.ai.AIError
import com.ihy2ln.weaverse.ai.AIProvider
import com.ihy2ln.weaverse.ai.AIRequest
import com.ihy2ln.weaverse.ai.AIResult
import com.ihy2ln.weaverse.ai.ModelInfo
import com.ihy2ln.weaverse.ai.OtherProviderSeeds
import com.ihy2ln.weaverse.ai.WeaverseAiLog
import com.ihy2ln.weaverse.ai.openrouter.OpenRouterChatRequest
import com.ihy2ln.weaverse.ai.openrouter.OpenRouterChatResponse
import com.ihy2ln.weaverse.ai.openrouter.OpenRouterErrorMapper
import com.ihy2ln.weaverse.ai.openrouter.OpenRouterSseParser
import com.ihy2ln.weaverse.ai.openrouter.textContent
import com.ihy2ln.weaverse.data.settings.SecureKeyStore
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** OpenAI's chat completions; open so other OpenAI-compatible endpoints (fal's router) reuse it. */
@Singleton
open class OpenAiProvider @Inject constructor(
    private val settings: SettingsRepository,
    private val okHttpClient: OkHttpClient,
) : AIProvider {
    override val name: String = "OpenAI"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    protected open val baseUrl: String = BASE
    protected open val keyId: String = SecureKeyStore.OPENAI
    protected open val modelPrefix: String = "openai/"
    protected open fun authorization(key: String): String = "Bearer $key"

    override suspend fun models(): List<ModelInfo> = OtherProviderSeeds.openai

    override fun stream(request: AIRequest): Flow<AIChunk> = flow {
        val key = settings.apiKey(keyId) ?: throw AIError.NoApiKey()
        val model = request.modelId.removePrefix(modelPrefix)
        val bodyJson = json.encodeToString(chatRequest(model, request, stream = true))
        val httpRequest = Request.Builder()
            .url("$baseUrl/chat/completions")
            .header("Authorization", authorization(key))
            .post(bodyJson.toRequestBody(JSON))
            .build()
        WeaverseAiLog.i("stream $name model=$model")
        try {
            okHttpClient.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string().orEmpty()
                    throw OpenRouterErrorMapper.fromHttp(
                        response.code,
                        err,
                        response.header("Retry-After")?.toLongOrNull(),
                    )
                }
                BufferedReader(response.body!!.byteStream().reader()).use { reader ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val line = reader.readLine() ?: break
                        when (val parsed = OpenRouterSseParser.parseLine(line)) {
                            OpenRouterSseParser.ParseResult.Done -> {
                                emit(AIChunk.Done)
                                return@flow
                            }
                            is OpenRouterSseParser.ParseResult.Chunks -> parsed.chunks.forEach { emit(it) }
                            OpenRouterSseParser.ParseResult.Ignore -> Unit
                        }
                    }
                }
                emit(AIChunk.Done)
            }
        } catch (e: AIError) {
            throw e
        } catch (e: IOException) {
            throw AIError.NoNetwork(e)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun complete(request: AIRequest): AIResult = withContext(Dispatchers.IO) { completeBlocking(request) }

    private suspend fun completeBlocking(request: AIRequest): AIResult {
        val key = settings.apiKey(keyId) ?: throw AIError.NoApiKey()
        val model = request.modelId.removePrefix(modelPrefix)
        val bodyJson = json.encodeToString(chatRequest(model, request, stream = false))
        val httpRequest = Request.Builder()
            .url("$baseUrl/chat/completions")
            .header("Authorization", authorization(key))
            .post(bodyJson.toRequestBody(JSON))
            .build()
        try {
            okHttpClient.newCall(httpRequest).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw OpenRouterErrorMapper.fromHttp(
                        response.code,
                        body,
                        response.header("Retry-After")?.toLongOrNull(),
                    )
                }
                val parsed = json.decodeFromString(OpenRouterChatResponse.serializer(), body)
                return AIResult(
                    text = parsed.choices.firstOrNull()?.message?.textContent().orEmpty(),
                    providerName = name,
                    promptTokens = parsed.usage?.promptTokens ?: 0,
                    completionTokens = parsed.usage?.completionTokens ?: 0,
                    totalTokens = parsed.usage?.totalTokens ?: 0,
                    cost = parsed.usage?.cost,
                )
            }
        } catch (e: AIError) {
            throw e
        } catch (e: IOException) {
            throw AIError.NoNetwork(e)
        }
    }

    private fun chatRequest(model: String, request: AIRequest, stream: Boolean): OpenRouterChatRequest {
        val messages = buildList {
            if (request.systemPrompt.isNotBlank()) {
                add(com.ihy2ln.weaverse.ai.openrouter.OpenRouterChatMessage("system", JsonPrimitive(request.systemPrompt)))
            }
            request.messages.forEach { (role, content) ->
                add(com.ihy2ln.weaverse.ai.openrouter.OpenRouterChatMessage(role, JsonPrimitive(content)))
            }
        }
        return OpenRouterChatRequest(
            model = model,
            messages = messages,
            stream = stream,
            maxTokens = request.maxTokens,
            temperature = request.temperature,
            topP = request.topP,
        )
    }

    companion object {
        private const val BASE = "https://api.openai.com/v1"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
