package com.ihy2ln.weaverse.ai.fal

import com.ihy2ln.weaverse.ai.AIError
import com.ihy2ln.weaverse.ai.ImageAttachment
import com.ihy2ln.weaverse.ai.ModelInfo
import com.ihy2ln.weaverse.ai.WeaverseAiLog
import com.ihy2ln.weaverse.data.settings.SecureKeyStore
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * fal.ai's picture models through its queue API: submit, poll the status, fetch the result.
 * Pictures go up inline as data URIs, so no upload step or storage account is involved.
 */
@Singleton
class FalClient @Inject constructor(
    private val settings: SettingsRepository,
    baseClient: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = baseClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()
    private val schemas = ConcurrentHashMap<String, Fal.InputSchema>()

    private val _imageModels = MutableStateFlow(Fal.seedImageModels)
    /** fal's live picture catalog once loaded; the curated seeds until then. */
    val imageModels: StateFlow<List<ModelInfo>> = _imageModels.asStateFlow()

    init {
        if (hasKey()) scope.launch { runCatching { refreshCatalog() } }
    }

    fun hasKey(): Boolean = !settings.apiKey(SecureKeyStore.FAL).isNullOrBlank()

    private fun key(): String = settings.apiKey(SecureKeyStore.FAL) ?: throw AIError.NoApiKey()

    /**
     * Loads fal's image-editing and text-to-image catalogs with the stored key, which also
     * checks the key (fal answers 401 to a wrong one). Curated editors stay first.
     */
    suspend fun refreshCatalog(): List<ModelInfo> = withContext(Dispatchers.IO) {
        val key = key()
        val loaded = buildList {
            for (category in listOf("image-to-image", "text-to-image")) {
                var cursor: String? = null
                var pages = 0
                do {
                    val url = CATALOG.toHttpUrl().newBuilder()
                        .addQueryParameter("category", category)
                        .addQueryParameter("status", "active")
                        .addQueryParameter("limit", "100")
                        .apply { cursor?.let { addQueryParameter("cursor", it) } }
                        .build()
                    val (models, next) = Fal.parseCatalog(execute(Request.Builder().url(url).get(), key))
                    addAll(models)
                    cursor = next
                } while (cursor != null && ++pages < 4)
            }
        }
        val seeds = Fal.seedImageModels.map { it.id }.toSet()
        val merged = (Fal.seedImageModels.filter { seed -> loaded.any { it.id == seed.id } } +
            loaded.filter { it.id !in seeds }.sortedWith(compareBy({ !it.supportsImages }, { it.displayName.lowercase() })))
            .distinctBy { it.id }
        _imageModels.value = merged.ifEmpty { Fal.seedImageModels }
        merged
    }

    /**
     * The account's credit in USD. fal only shows it to admin keys, so an ordinary key gives null
     * (the key still works; a picture then fails with a clear "no credit" message when empty).
     */
    suspend fun balance(): Double? = withContext(Dispatchers.IO) {
        runCatching { Fal.parseBalance(execute(Request.Builder().url(BILLING).get(), key())) }.getOrNull()
    }

    /** `ratio:W:H` tags for the manga editor's padding, from the endpoint's schema. */
    suspend fun ratioTags(endpoint: String): List<String> =
        runCatching { Fal.ratioTags(schema(endpoint)) }.getOrDefault(emptyList())

    /**
     * One picture from [endpoint] (e.g. `fal-ai/flux-pro/kontext`). Editing endpoints get the
     * attached pictures; text-to-image endpoints only the prompt.
     */
    suspend fun generateImage(
        endpoint: String,
        prompt: String,
        images: List<ImageAttachment>,
        aspectRatio: String?,
    ): Pair<ByteArray, String> = withContext(Dispatchers.IO) {
        val key = key()
        val schema = schema(endpoint)
        if (images.isEmpty() && schema.takesImage) throw AIError.BadRequest("$endpoint edits a picture; attach one.")
        val uris = if (schema.takesImage) images.map { "data:${it.mimeType};base64,${it.base64Data}" } else emptyList()
        val body = Fal.input(schema, prompt, uris, aspectRatio).toString()
        WeaverseAiLog.i("fal submit endpoint=$endpoint images=${uris.size}")
        val submitted = json.parseToJsonElement(
            execute(Request.Builder().url("$QUEUE/$endpoint").post(body.toRequestBody(JSON)), key),
        ).jsonObject
        val statusUrl = submitted["status_url"]?.jsonPrimitive?.contentOrNull
            ?: throw AIError.EmbeddedError("fal did not queue the request")
        val responseUrl = submitted["response_url"]?.jsonPrimitive?.contentOrNull
            ?: throw AIError.EmbeddedError("fal did not queue the request")
        val deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(15)
        while (true) {
            if (System.currentTimeMillis() > deadline) throw AIError.EmbeddedError("fal did not finish within 15 minutes")
            delay(1500)
            val status = json.parseToJsonElement(execute(Request.Builder().url(statusUrl).get(), key)).jsonObject
            if (status["status"]?.jsonPrimitive?.contentOrNull == "COMPLETED") {
                status["error"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let {
                    throw AIError.EmbeddedError("fal: $it")
                }
                break
            }
        }
        val (url, type) = Fal.resultImage(executeLenient(Request.Builder().url(responseUrl).get(), key))
        download(url, type)
    }

    private suspend fun schema(endpoint: String): Fal.InputSchema = schemas[endpoint] ?: withContext(Dispatchers.IO) {
        val url = SCHEMA.toHttpUrl().newBuilder().addQueryParameter("endpoint_id", endpoint).build()
        Fal.parseSchema(execute(Request.Builder().url(url).get(), key = null)).also { schemas[endpoint] = it }
    }

    private fun download(url: String, type: String?): Pair<ByteArray, String> {
        if (url.startsWith("data:")) {
            val mime = url.substringAfter("data:").substringBefore(';').ifBlank { type ?: "image/png" }
            return Base64.getDecoder().decode(url.substringAfter("base64,")) to mime
        }
        try {
            http.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (!response.isSuccessful) throw AIError.HttpFailure(response.code, "fal finished but the picture could not be downloaded")
                val bytes = response.body?.bytes() ?: throw AIError.EmbeddedError("fal returned an empty picture")
                val mime = type ?: response.header("Content-Type")?.substringBefore(';') ?: "image/png"
                return bytes to mime
            }
        } catch (io: IOException) {
            throw AIError.NoNetwork(io)
        }
    }

    /** Body of a 2xx answer, or fal's error as an [AIError]. */
    private fun execute(builder: Request.Builder, key: String?): String {
        val request = builder.apply { key?.let { header("Authorization", "Key $it") } }.build()
        try {
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw Fal.errorFor(response.code, text, response.header("Retry-After")?.toLongOrNull())
                }
                return text
            }
        } catch (io: IOException) {
            throw AIError.NoNetwork(io)
        }
    }

    /** The result endpoint answers a failed generation with 4xx/5xx and a `detail`; keep that text. */
    private fun executeLenient(builder: Request.Builder, key: String): String {
        try {
            http.newCall(builder.header("Authorization", "Key $key").build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful && Fal.errorMessage(text) == null) {
                    throw Fal.errorFor(response.code, text, null)
                }
                return text
            }
        } catch (io: IOException) {
            throw AIError.NoNetwork(io)
        }
    }

    private companion object {
        const val QUEUE = "https://queue.fal.run"
        const val CATALOG = "https://api.fal.ai/v1/models"
        const val SCHEMA = "https://fal.ai/api/openapi/queue/openapi.json"
        const val BILLING = "https://api.fal.ai/v1/account/billing?expand=credits"
        val JSON = "application/json".toMediaType()
    }
}
