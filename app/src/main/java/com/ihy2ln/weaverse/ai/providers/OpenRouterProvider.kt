package com.ihy2ln.weaverse.ai.providers

import android.util.Log
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OpenRouter as a first-class provider (Revision 02 §5), not just an
 * [OpenAICompatibleProvider] pointed at OpenRouter's base URL — that would
 * work for streaming (OpenRouter speaks the same chat-completions wire
 * format), but misses everything spec explicitly wants beyond it:
 * `HTTP-Referer`/`X-Title` headers, a `/models` response rich enough for
 * pricing/context-length, and a `/auth/key` credits read. Some request/
 * response shape duplication with [OpenAICompatibleProvider] as a result —
 * accepted rather than refactoring [AIProvider]'s shared interface to carry
 * OpenRouter-only concerns (extra headers, credits) that every other
 * provider would have to ignore.
 */
@Singleton
class OpenRouterProvider @Inject constructor(private val client: HttpClient) : AIProvider {
    override val type: AIProviderType = AIProviderType.OpenRouter

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun models(baseUrl: String, apiKey: String): Result<List<ModelInfo>> = runCatching {
        val response = client.get("${baseUrl.trimEnd('/')}/models") {
            if (apiKey.isNotBlank()) bearerAuth(apiKey)
        }
        if (!response.status.isSuccess()) error(response.describeFailure("OpenRouter"))
        val body: OpenRouterModelsResponse = response.body()
        body.data.map { model ->
            ModelInfo(
                id = model.id,
                displayName = model.name ?: model.id,
                contextLength = model.context_length,
                pricePerMillionInputTokens = model.pricing?.prompt?.toDoubleOrNull()?.times(1_000_000),
                pricePerMillionOutputTokens = model.pricing?.completion?.toDoubleOrNull()?.times(1_000_000),
                modality = model.architecture?.modality,
            )
        }
    }

    /** Real key validation (spec: "call GET /api/v1/key with that key") — distinct from
     * [models], which merely proves the key works as a side effect. This is what the Settings
     * screen now runs before ever persisting a key: 2xx and the key is stored with the real
     * label/usage/limit/rate-limit/free-tier data below shown in the UI; any other response and
     * the key is never written to [com.ihy2ln.weaverse.data.settings.SecretsStore]. */
    suspend fun validateKey(baseUrl: String, apiKey: String): Result<OpenRouterKeyInfo> = runCatching {
        // OpenRouter's real key-info endpoint is `/auth/key`, not the bare `/key` some docs
        // shorthand it as — the wrong path 404s, which would look like a broken key.
        val response = client.get("${baseUrl.trimEnd('/')}/auth/key") { bearerAuth(apiKey) }
        if (!response.status.isSuccess()) error(response.describeFailure("OpenRouter"))
        val body: OpenRouterAuthKeyResponse = response.body()
        val data = body.data ?: error("OpenRouter accepted the key but returned no key data")
        OpenRouterKeyInfo(
            label = data.label,
            usage = data.usage,
            limit = data.limit,
            limitRemaining = data.limit_remaining ?: data.limit?.let { it - (data.usage ?: 0.0) },
            isFreeTier = data.is_free_tier,
            rateLimitRequests = data.rate_limit?.requests,
            rateLimitInterval = data.rate_limit?.interval,
        )
    }

    override fun stream(baseUrl: String, apiKey: String, request: AIRequest): Flow<AIChunk> = flow {
        val requestBody = OpenRouterRequestBody(
            model = request.model,
            messages = buildList {
                if (!request.systemPrompt.isNullOrBlank()) add(OpenRouterMessage("system", request.systemPrompt))
                addAll(request.messages.map { OpenRouterMessage(roleOf(it), it.content) })
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
        val url = "${baseUrl.trimEnd('/')}/chat/completions"
        val rawPreview = StringBuilder()

        // Redacted proof-of-work log (spec: "Logcat output showing the outgoing request URL,
        // headers with the key redacted, and the raw first 500 chars of the response").
        Log.d(
            LOG_TAG,
            "-> POST $url headers={Authorization=Bearer ${redactKey(apiKey)}, HTTP-Referer=$APP_REFERER, " +
                "X-Title=$APP_TITLE, Content-Type=application/json} model=${request.model}",
        )

        client.preparePost(url) {
            if (apiKey.isNotBlank()) bearerAuth(apiKey)
            header("HTTP-Referer", APP_REFERER)
            header("X-Title", APP_TITLE)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(OpenRouterRequestBody.serializer(), requestBody))
        }.execute { response ->
            Log.d(LOG_TAG, "<- HTTP ${response.status}")
            if (!response.status.isSuccess()) {
                val failure = response.describeFailure("OpenRouter")
                Log.d(LOG_TAG, "<- body preview: ${failure.take(500)}")
                emit(AIChunk.Error(failure))
                return@execute
            }
            var sawError = false
            response.bodyAsChannel().forEachSseDataLine { data ->
                if (rawPreview.length < 500) rawPreview.append(data).append('\n')
                val event = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return@forEachSseDataLine
                // OpenRouter can return HTTP 200 with an "error" object embedded in an SSE
                // event instead of a normal choices/delta payload — a 2xx status alone doesn't
                // mean the generation is succeeding.
                event["error"]?.jsonObject?.let { errorObj ->
                    sawError = true
                    val message = errorObj["message"]?.jsonPrimitive?.content ?: "OpenRouter reported an error"
                    val code = errorObj["code"]?.jsonPrimitive?.content
                    emit(AIChunk.Error(if (code != null) "$message (code $code)" else message))
                    return@forEachSseDataLine
                }
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
            Log.d(LOG_TAG, "<- body preview: ${rawPreview.toString().take(500)}")
            if (!sawError) {
                emit(AIChunk.Done(fullText = builder.toString(), inputTokens = inputTokens, outputTokens = outputTokens))
            }
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

    /** Never logs the real key — only its length and last 4 characters, enough to tell two
     * saved keys apart in Logcat without the log itself becoming a credential leak. */
    private fun redactKey(apiKey: String): String =
        if (apiKey.length <= 4) "*".repeat(apiKey.length) else "***${apiKey.takeLast(4)}"

    companion object {
        const val DEFAULT_BASE_URL = "https://openrouter.ai/api/v1"
        private const val APP_REFERER = "https://github.com/ihy2ln/weaverse"
        private const val APP_TITLE = "Weaverse"
        private const val LOG_TAG = "WeaverseAI"
    }
}

@Serializable
private data class OpenRouterRequestBody(
    val model: String,
    val messages: List<OpenRouterMessage>,
    val stream: Boolean = true,
    val temperature: Float? = null,
    val top_p: Float? = null,
    val max_tokens: Int? = null,
    val stop: List<String>? = null,
)

@Serializable
private data class OpenRouterMessage(val role: String, val content: String)

@Serializable
private data class OpenRouterModelsResponse(val data: List<OpenRouterModel> = emptyList())

@Serializable
private data class OpenRouterModel(
    val id: String,
    val name: String? = null,
    val context_length: Int? = null,
    val pricing: OpenRouterPricing? = null,
    val architecture: OpenRouterArchitecture? = null,
)

/** OpenRouter reports price as a decimal-string USD-per-token (e.g. `"0.000003"`), not per
 * million — [OpenRouterProvider.models] does the `* 1_000_000` conversion spec's display wants. */
@Serializable
private data class OpenRouterPricing(val prompt: String? = null, val completion: String? = null)

@Serializable
private data class OpenRouterArchitecture(val modality: String? = null)

@Serializable
private data class OpenRouterAuthKeyResponse(val data: OpenRouterAuthKeyData? = null)

@Serializable
private data class OpenRouterAuthKeyData(
    val label: String? = null,
    val usage: Double? = null,
    val limit: Double? = null,
    val limit_remaining: Double? = null,
    val is_free_tier: Boolean? = null,
    val rate_limit: OpenRouterRateLimit? = null,
)

@Serializable
private data class OpenRouterRateLimit(val requests: Int? = null, val interval: String? = null)

/** Real data from `GET /auth/key` (spec: "display the REAL data returned: label, usage,
 * credit limit, rate limit, is_free_tier") — the public type [ConnectionProfilesViewModel] and
 * its screen consume, since the private wire-format classes above can't cross the package. */
data class OpenRouterKeyInfo(
    val label: String?,
    val usage: Double?,
    val limit: Double?,
    val limitRemaining: Double?,
    val isFreeTier: Boolean?,
    val rateLimitRequests: Int?,
    val rateLimitInterval: String?,
)
