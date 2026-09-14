package com.ihy2ln.weaverse.ai.providers

import com.ihy2ln.weaverse.ai.AIChunk
import com.ihy2ln.weaverse.ai.AIError
import com.ihy2ln.weaverse.ai.AIProvider
import com.ihy2ln.weaverse.ai.AIRequest
import com.ihy2ln.weaverse.ai.AIResult
import com.ihy2ln.weaverse.ai.ModelInfo
import com.ihy2ln.weaverse.ai.OtherProviderSeeds
import com.ihy2ln.weaverse.ai.WeaverseAiLog
import com.ihy2ln.weaverse.ai.openrouter.OpenRouterErrorMapper
import com.ihy2ln.weaverse.data.settings.SecureKeyStore
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnthropicProvider @Inject constructor(
    private val settings: SettingsRepository,
    private val okHttpClient: OkHttpClient,
) : AIProvider {
    override val name: String = "Anthropic"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun models(): List<ModelInfo> = OtherProviderSeeds.anthropic

    override fun stream(request: AIRequest): Flow<AIChunk> = flow {
        val key = settings.apiKey(SecureKeyStore.ANTHROPIC) ?: throw AIError.NoApiKey()
        val model = request.modelId.removePrefix("anthropic/")
        val body = buildBody(model, request, stream = true)
        val httpRequest = Request.Builder()
            .url(BASE)
            .header("x-api-key", key)
            .header("anthropic-version", VERSION)
            .post(body.toRequestBody(JSON))
            .build()
        WeaverseAiLog.i("stream Anthropic model=$model")
        try {
            okHttpClient.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    throw OpenRouterErrorMapper.fromHttp(
                        response.code,
                        response.body?.string().orEmpty(),
                        response.header("Retry-After")?.toLongOrNull(),
                    )
                }
                BufferedReader(response.body!!.byteStream().reader()).use { reader ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val line = reader.readLine() ?: break
                        val trimmed = line.trim()
                        if (!trimmed.startsWith("data:")) continue
                        val payload = trimmed.removePrefix("data:").trim()
                        if (payload == "[DONE]") {
                            emit(AIChunk.Done)
                            return@flow
                        }
                        val obj = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: continue
                        when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                            "content_block_delta" -> {
                                val text = obj["delta"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                                if (!text.isNullOrEmpty()) emit(AIChunk.Delta(text))
                            }
                            "message_delta" -> {
                                val usage = obj["usage"]?.jsonObject
                                val out = usage?.get("output_tokens")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                                if (out > 0) emit(AIChunk.Usage(0, out, out, null))
                            }
                            "message_stop" -> {
                                emit(AIChunk.Done)
                                return@flow
                            }
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
    }

    override suspend fun complete(request: AIRequest): AIResult {
        val key = settings.apiKey(SecureKeyStore.ANTHROPIC) ?: throw AIError.NoApiKey()
        val model = request.modelId.removePrefix("anthropic/")
        val httpRequest = Request.Builder()
            .url(BASE)
            .header("x-api-key", key)
            .header("anthropic-version", VERSION)
            .post(buildBody(model, request, stream = false).toRequestBody(JSON))
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
                val obj = json.parseToJsonElement(body).jsonObject
                val text = obj["content"]?.jsonArray
                    ?.mapNotNull { (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull }
                    ?.joinToString("")
                    .orEmpty()
                val usage = obj["usage"]?.jsonObject
                val prompt = usage?.get("input_tokens")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                val completion = usage?.get("output_tokens")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                return AIResult(text, name, prompt, completion, prompt + completion, null)
            }
        } catch (e: AIError) {
            throw e
        } catch (e: IOException) {
            throw AIError.NoNetwork(e)
        }
    }

    private fun buildBody(model: String, request: AIRequest, stream: Boolean): String {
        val messages = buildJsonArray {
            request.messages.forEach { (role, content) ->
                add(
                    buildJsonObject {
                        put("role", if (role == "assistant") "assistant" else "user")
                        put("content", content)
                    },
                )
            }
        }
        val root = buildJsonObject {
            put("model", model)
            put("max_tokens", request.maxTokens ?: 1024)
            put("stream", stream)
            request.temperature?.let { put("temperature", it) }
            if (request.systemPrompt.isNotBlank()) put("system", request.systemPrompt)
            put("messages", messages)
        }
        return root.toString()
    }

    companion object {
        private const val BASE = "https://api.anthropic.com/v1/messages"
        private const val VERSION = "2023-06-01"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
