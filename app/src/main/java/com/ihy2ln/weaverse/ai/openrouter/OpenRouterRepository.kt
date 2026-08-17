package com.ihy2ln.weaverse.ai.openrouter

import com.ihy2ln.weaverse.ai.AIChunk
import com.ihy2ln.weaverse.ai.AIError
import com.ihy2ln.weaverse.ai.AIRequest
import com.ihy2ln.weaverse.ai.AIResult
import com.ihy2ln.weaverse.ai.WeaverseAiLog
import com.ihy2ln.weaverse.ai.ModelInfo
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

@Singleton
class OpenRouterRepository @Inject constructor(
    private val settings: SettingsRepository,
    private val modelCache: OpenRouterModelCache,
    private val okHttpClient: OkHttpClient,
) {
    /** Overridable for unit tests (MockWebServer). */
    @Volatile
    var baseUrl: String = BASE_URL
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val streamingClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .readTimeout(300, TimeUnit.SECONDS)
            .build()
    }

    suspend fun validateKey(apiKey: String): OpenRouterKeyData = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw AIError.InvalidKey("API key is blank")
        val response = executeGet("$baseUrl/key", apiKey)
        val body = response.body?.string().orEmpty()
        logResponse("$baseUrl/key", response.code, body)
        if (!response.isSuccessful) {
            throw OpenRouterErrorMapper.fromHttp(
                response.code,
                body,
                response.header("Retry-After")?.toLongOrNull(),
            )
        }
        val parsed = json.decodeFromString(OpenRouterKeyResponse.serializer(), body)
        OpenRouterErrorMapper.fromEmbeddedError(parsed.error)?.let { throw it }
        parsed.data ?: throw AIError.HttpFailure(response.code, "Missing key data in response")
    }

    suspend fun testStoredKey(): OpenRouterKeyData {
        val key = settings.apiKey(SecureKeyStore.OPENROUTER)
            ?: throw AIError.NoApiKey()
        return validateKey(key)
    }

    suspend fun fetchModels(forceRefresh: Boolean = false): List<ModelInfo> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            val cached = modelCache.getCachedModels()
            if (cached.isNotEmpty()) return@withContext modelCache.toModelInfo(cached)
        }
        val key = requireKey()
        val response = executeGet("$baseUrl/models", key)
        val body = response.body?.string().orEmpty()
        logResponse("$baseUrl/models", response.code, body)
        if (!response.isSuccessful) {
            throw OpenRouterErrorMapper.fromHttp(
                response.code,
                body,
                response.header("Retry-After")?.toLongOrNull(),
            )
        }
        val parsed = json.decodeFromString(OpenRouterModelsResponse.serializer(), body)
        OpenRouterErrorMapper.fromEmbeddedError(parsed.error)?.let { throw it }
        modelCache.save(parsed)
        modelCache.toModelInfo(parsed.data)
    }

    fun streamCompletion(request: AIRequest): Flow<AIChunk> = flow {
        val key = requireKey()
        val modelId = normalizeModelId(request.modelId)
        ensureModelVerified(modelId)

        val messages = buildMessages(request)
        logAssembledPrompt(modelId, messages)

        val bodyJson = json.encodeToString(
            OpenRouterChatRequest(
                model = modelId,
                messages = messages,
                stream = true,
                maxTokens = request.maxTokens,
                temperature = request.temperature,
                topP = request.topP,
            ),
        )

        val httpRequest = authorizedRequest("$baseUrl/chat/completions", key)
            .post(bodyJson.toRequestBody(JSON_MEDIA))
            .build()

        WeaverseAiLog.i("POST ${httpRequest.url} Authorization=Bearer *** Content-Type=application/json HTTP-Referer=$HTTP_REFERER X-Title=$X_TITLE")

        val call = streamingClient.newCall(httpRequest)
        try {
            currentCoroutineContext().ensureActive()
            val response = call.execute()
            try {
                if (!response.isSuccessful) {
                    val errBody = response.body?.string().orEmpty()
                    logResponse("$baseUrl/chat/completions", response.code, errBody)
                    throw OpenRouterErrorMapper.fromHttp(
                        response.code,
                        errBody,
                        response.header("Retry-After")?.toLongOrNull(),
                    )
                }
                val source = response.body?.byteStream()
                    ?: throw AIError.HttpFailure(response.code, "Empty response body")
                BufferedReader(source.reader()).use { reader ->
                    var firstLogged = false
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val line = reader.readLine() ?: break
                        if (!firstLogged && line.isNotBlank()) {
                            WeaverseAiLog.i("SSE first lines: ${line.take(500)}")
                            firstLogged = true
                        }
                        when (val parsed = OpenRouterSseParser.parseLine(line)) {
                            OpenRouterSseParser.ParseResult.Done -> {
                                emit(AIChunk.Done)
                                return@flow
                            }
                            OpenRouterSseParser.ParseResult.Ignore -> Unit
                            is OpenRouterSseParser.ParseResult.Chunks -> {
                                parsed.chunks.forEach { chunk ->
                                    if (chunk is AIChunk.Usage) {
                                        WeaverseAiLog.i(
                                            "usage prompt=${chunk.promptTokens} completion=${chunk.completionTokens} total=${chunk.totalTokens} cost=${chunk.cost}",
                                        )
                                    }
                                    emit(chunk)
                                }
                            }
                        }
                    }
                }
                emit(AIChunk.Done)
            } finally {
                response.close()
            }
        } catch (e: CancellationException) {
            call.cancel()
            throw e
        } catch (e: AIError) {
            throw e
        } catch (e: IOException) {
            call.cancel()
            throw AIError.NoNetwork(e)
        } catch (e: Exception) {
            call.cancel()
            throw OpenRouterErrorMapper.fromThrowable(e)
        }
    }.flowOn(Dispatchers.IO)

    suspend fun complete(request: AIRequest): AIResult = withContext(Dispatchers.IO) {
        val key = requireKey()
        val modelId = normalizeModelId(request.modelId)
        ensureModelVerified(modelId)

        val messages = buildMessages(request)
        logAssembledPrompt(modelId, messages)

        val bodyJson = json.encodeToString(
            OpenRouterChatRequest(
                model = modelId,
                messages = messages,
                stream = false,
                maxTokens = request.maxTokens,
                temperature = request.temperature,
                topP = request.topP,
            ),
        )

        val httpRequest = authorizedRequest("$baseUrl/chat/completions", key)
            .post(bodyJson.toRequestBody(JSON_MEDIA))
            .build()

        WeaverseAiLog.i("POST ${httpRequest.url} Authorization=Bearer *** Content-Type=application/json HTTP-Referer=$HTTP_REFERER X-Title=$X_TITLE")

        try {
            streamingClient.newCall(httpRequest).execute().use { response ->
                val body = response.body?.string().orEmpty()
                logResponse("$baseUrl/chat/completions", response.code, body)
                if (!response.isSuccessful) {
                    throw OpenRouterErrorMapper.fromHttp(
                        response.code,
                        body,
                        response.header("Retry-After")?.toLongOrNull(),
                    )
                }
                val parsed = json.decodeFromString(OpenRouterChatResponse.serializer(), body)
                OpenRouterErrorMapper.fromEmbeddedError(parsed.error)?.let { throw it }
                val text = parsed.choices.firstOrNull()?.message?.textContent().orEmpty()
                AIResult(
                    text = text,
                    providerName = PROVIDER_NAME,
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
        } catch (e: Exception) {
            throw OpenRouterErrorMapper.fromThrowable(e)
        }
    }

    fun storedApiKey(): String? = settings.apiKey(SecureKeyStore.OPENROUTER)

    suspend fun modelSupportsImages(modelRef: String): Boolean {
        val cached = modelCache.getCachedModels()
        if (cached.isEmpty()) {
            runCatching { fetchModels(forceRefresh = false) }
        }
        return modelCache.modelSupportsImages(modelRef, modelCache.getCachedModels())
    }

    /**
     * OpenRouter TTS via POST /api/v1/audio/speech. Writes MP3 bytes to [outputFile].
     */
    suspend fun synthesizeSpeech(
        text: String,
        modelId: String,
        outputFile: File,
        voice: String = "alloy",
    ): File = withContext(Dispatchers.IO) {
        val key = requireKey()
        val model = normalizeModelId(modelId)
        val bodyJson = json.encodeToString(
            OpenRouterSpeechRequest(
                model = model,
                input = text.take(4096),
                voice = voice,
                responseFormat = "mp3",
            ),
        )
        val httpRequest = authorizedRequest("$baseUrl/audio/speech", key)
            .post(bodyJson.toRequestBody(JSON_MEDIA))
            .build()
        WeaverseAiLog.i("POST ${httpRequest.url} TTS model=$model")
        try {
            streamingClient.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string().orEmpty()
                    logResponse("$baseUrl/audio/speech", response.code, errBody)
                    throw OpenRouterErrorMapper.fromHttp(
                        response.code,
                        errBody,
                        response.header("Retry-After")?.toLongOrNull(),
                    )
                }
                val bytes = response.body?.bytes()
                    ?: throw AIError.HttpFailure(response.code, "Empty TTS audio body")
                outputFile.parentFile?.mkdirs()
                outputFile.writeBytes(bytes)
                outputFile
            }
        } catch (e: AIError) {
            throw e
        } catch (e: IOException) {
            throw AIError.NoNetwork(e)
        } catch (e: Exception) {
            throw OpenRouterErrorMapper.fromThrowable(e)
        }
    }

    private fun requireKey(): String =
        storedApiKey() ?: throw AIError.NoApiKey()

    private suspend fun ensureModelVerified(modelId: String) {
        val live = modelCache.getCachedModels()
        if (live.isEmpty()) {
            // Best-effort refresh so we don't block forever offline with empty cache
            runCatching { fetchModels(forceRefresh = true) }
        }
        val after = modelCache.getCachedModels()
        if (after.isNotEmpty() && !modelCache.isKnownModel(modelId, after)) {
            throw AIError.BadRequest(
                "Model '$modelId' is not in the live OpenRouter model list. Refresh models in Settings.",
            )
        }
    }

    private fun buildMessages(request: AIRequest): List<OpenRouterChatMessage> {
        val messages = mutableListOf<OpenRouterChatMessage>()
        if (request.systemPrompt.isNotBlank()) {
            messages += OpenRouterChatMessage("system", JsonPrimitive(request.systemPrompt))
        }
        val lastUserIndex = request.messages.indexOfLast { it.first == "user" }
        request.messages.forEachIndexed { index, (role, content) ->
            val isLastUserWithImages =
                index == lastUserIndex && role == "user" && request.imageAttachments.isNotEmpty()
            if (isLastUserWithImages) {
                messages += OpenRouterChatMessage(
                    role = role,
                    content = buildMultimodalContent(content, request.imageAttachments),
                )
            } else {
                messages += OpenRouterChatMessage(role, JsonPrimitive(content))
            }
        }
        return messages
    }

    private fun buildMultimodalContent(
        text: String,
        images: List<com.ihy2ln.weaverse.ai.ImageAttachment>,
    ): JsonArray = buildJsonArray {
        add(
            buildJsonObject {
                put("type", "text")
                put("text", text)
            },
        )
        images.forEach { image ->
            val mime = image.mimeType.ifBlank { "image/jpeg" }
            add(
                buildJsonObject {
                    put("type", "image_url")
                    put(
                        "image_url",
                        buildJsonObject {
                            put("url", "data:$mime;base64,${image.base64Data}")
                        },
                    )
                },
            )
        }
    }

    private fun normalizeModelId(modelRef: String): String =
        modelRef.removePrefix("openrouter/").ifBlank { WritingModelSeeds.DEFAULT_MODEL_ID }

    private fun authorizedRequest(url: String, apiKey: String): Request.Builder =
        Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("HTTP-Referer", HTTP_REFERER)
            .header("X-Title", X_TITLE)

    private fun executeGet(url: String, apiKey: String): Response {
        val request = authorizedRequest(url, apiKey).get().build()
        WeaverseAiLog.i("GET ${request.url} Authorization=Bearer *** HTTP-Referer=$HTTP_REFERER X-Title=$X_TITLE")
        return try {
            okHttpClient.newCall(request).execute()
        } catch (e: IOException) {
            throw AIError.NoNetwork(e)
        }
    }

    private fun logResponse(url: String, code: Int, body: String) {
        WeaverseAiLog.i("Response $url status=$code body=${body.take(500)}")
    }

    private fun logAssembledPrompt(modelId: String, messages: List<OpenRouterChatMessage>) {
        val preview = messages.joinToString("\n---\n") { "${it.role}: ${it.textContent()}" }.take(2000)
        WeaverseAiLog.i("Assembled prompt model=$modelId provider=$PROVIDER_NAME endpoint=$baseUrl/chat/completions\n$preview")
    }

    companion object {
        const val BASE_URL = "https://openrouter.ai/api/v1"
        const val PROVIDER_NAME = "OpenRouter"
        const val HTTP_REFERER = "https://github.com/ihy2ln/weaverse"
        const val X_TITLE = "Weaverse"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
