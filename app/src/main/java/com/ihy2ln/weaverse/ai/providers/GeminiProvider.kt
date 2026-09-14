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
class GeminiProvider @Inject constructor(
    private val settings: SettingsRepository,
    private val okHttpClient: OkHttpClient,
) : AIProvider {
    override val name: String = "Gemini"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun models(): List<ModelInfo> = OtherProviderSeeds.gemini

    override fun stream(request: AIRequest): Flow<AIChunk> = flow {
        val key = settings.apiKey(SecureKeyStore.GEMINI) ?: throw AIError.NoApiKey()
        val model = request.modelId.removePrefix("gemini/")
        val url = "$BASE/models/$model:streamGenerateContent?alt=sse&key=$key"
        val httpRequest = Request.Builder()
            .url(url)
            .post(buildBody(request).toRequestBody(JSON))
            .build()
        WeaverseAiLog.i("stream Gemini model=$model")
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
                        val obj = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: continue
                        val text = obj["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                            ?.get("content")?.jsonObject
                            ?.get("parts")?.jsonArray
                            ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
                            ?.joinToString("")
                        if (!text.isNullOrEmpty()) emit(AIChunk.Delta(text))
                        val usage = obj["usageMetadata"]?.jsonObject
                        if (usage != null) {
                            val prompt = usage["promptTokenCount"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                            val completion = usage["candidatesTokenCount"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                            emit(AIChunk.Usage(prompt, completion, prompt + completion, null))
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
        val key = settings.apiKey(SecureKeyStore.GEMINI) ?: throw AIError.NoApiKey()
        val model = request.modelId.removePrefix("gemini/")
        val url = "$BASE/models/$model:generateContent?key=$key"
        val httpRequest = Request.Builder()
            .url(url)
            .post(buildBody(request).toRequestBody(JSON))
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
                val text = obj["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("content")?.jsonObject
                    ?.get("parts")?.jsonArray
                    ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
                    ?.joinToString("")
                    .orEmpty()
                val usage = obj["usageMetadata"]?.jsonObject
                val prompt = usage?.get("promptTokenCount")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                val completion = usage?.get("candidatesTokenCount")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                return AIResult(text, name, prompt, completion, prompt + completion, null)
            }
        } catch (e: AIError) {
            throw e
        } catch (e: IOException) {
            throw AIError.NoNetwork(e)
        }
    }

    private fun buildBody(request: AIRequest): String {
        val contents = buildJsonArray {
            request.messages.forEach { (role, content) ->
                add(
                    buildJsonObject {
                        put("role", if (role == "assistant") "model" else "user")
                        put(
                            "parts",
                            buildJsonArray {
                                add(buildJsonObject { put("text", content) })
                            },
                        )
                    },
                )
            }
        }
        return buildJsonObject {
            if (request.systemPrompt.isNotBlank()) {
                put(
                    "systemInstruction",
                    buildJsonObject {
                        put(
                            "parts",
                            buildJsonArray { add(buildJsonObject { put("text", request.systemPrompt) }) },
                        )
                    },
                )
            }
            put("contents", contents)
            request.maxTokens?.let { max ->
                put("generationConfig", buildJsonObject { put("maxOutputTokens", max) })
            }
        }.toString()
    }

    companion object {
        private const val BASE = "https://generativelanguage.googleapis.com/v1beta"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
