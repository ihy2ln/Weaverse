package com.ihy2ln.weaverse.ai.pc

import com.ihy2ln.weaverse.ai.AIChunk
import com.ihy2ln.weaverse.ai.AIError
import com.ihy2ln.weaverse.ai.AIProvider
import com.ihy2ln.weaverse.ai.AIRequest
import com.ihy2ln.weaverse.ai.AIResult
import com.ihy2ln.weaverse.ai.ImageAttachment
import com.ihy2ln.weaverse.ai.ModelInfo
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import com.ihy2ln.weaverse.data.sync.SyncTlsPinning
import com.ihy2ln.weaverse.sync.normalizeSyncBaseUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The AI tools on the user's PC, reached through the Weaverse desktop companion: Claude Code
 * and Codex (ChatGPT) run headless on the user's own subscriptions, and ComfyUI edits pictures
 * on the PC's GPU. Model refs: `claudecode/<model>`, `codex/<model>`, `comfy/<workflow>`.
 */
object PcHarness {
    const val CLAUDE = "claudecode/"
    const val CODEX = "codex/"
    const val COMFY = "comfy/"
    const val TAG = "PC harness"

    fun isPcRef(ref: String): Boolean = ref.startsWith(CLAUDE) || ref.startsWith(CODEX) || ref.startsWith(COMFY)

    private fun textModel(ref: String, name: String) = ModelInfo(
        id = ref,
        displayName = name,
        supportsImages = true,
        tags = listOf("Text output", "Vision", TAG),
    )

    private fun imageModel(workflow: String, name: String) = ModelInfo(
        id = COMFY + workflow,
        displayName = name,
        supportsImages = true,
        generatesImages = true,
        tags = listOf("Image generation", "Image API", "Reference editing", TAG),
    )

    /** What is listed before the PC has been checked; replaced by what the PC reports. */
    val defaultTextModels = listOf(
        textModel(CLAUDE + "sonnet", "Claude Code · Sonnet (your Claude plan, on PC)"),
        textModel(CLAUDE + "opus", "Claude Code · Opus (your Claude plan, on PC)"),
        textModel(CLAUDE + "haiku", "Claude Code · Haiku (your Claude plan, on PC)"),
        textModel(CODEX + "default", "ChatGPT · Codex (your ChatGPT plan, on PC)"),
    )
    val defaultImageModels = listOf(imageModel("qwen-image-edit", "ComfyUI · Qwen Image Edit 2.1 (your PC's GPU)"))

    fun modelsFrom(caps: PcCapabilities): Pair<List<ModelInfo>, List<ModelInfo>> {
        val text = caps.harnesses.filter { it.available && it.id != "comfyui" }.flatMap { harness ->
            val (prefix, label, plan) = if (harness.id == "claude") Triple(CLAUDE, "Claude Code", "Claude") else Triple(CODEX, "ChatGPT · Codex", "ChatGPT")
            harness.models.map { model ->
                textModel(prefix + model, "$label · ${model.replaceFirstChar(Char::titlecase)} (your $plan plan, on PC)")
            }
        }
        val images = caps.harnesses.filter { it.available && it.id == "comfyui" }.flatMap { harness ->
            harness.models.map { workflow ->
                imageModel(workflow, if (workflow == "qwen-image-edit") defaultImageModels.first().displayName else "ComfyUI · $workflow (your PC)")
            }
        }
        return text to images
    }
}

@Serializable
data class PcHarnessStatus(val id: String, val available: Boolean, val detail: String, val models: List<String> = emptyList())

@Serializable
data class PcCapabilities(val harnesses: List<PcHarnessStatus> = emptyList())

@Serializable
private data class CompleteBody(val harness: String, val model: String, val system: String, val prompt: String, val images: List<String>)

@Serializable
private data class ImageBody(val workflow: String, val prompt: String, val negative: String, val image: String)

@Serializable
private data class JobId(val id: String = "", val error: String = "")

@Serializable
private data class JobStatus(
    val id: String = "",
    val state: String = "",
    val text: String = "",
    val mimeType: String = "",
    val error: String = "",
    val elapsedSeconds: Long = 0,
)

@Singleton
class PcBridgeClient @Inject constructor(
    private val settings: SettingsRepository,
    baseClient: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val base = baseClient.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private data class Target(val url: String, val password: String, val tls: Boolean, val pin: String)

    @Volatile private var target = Target("", "", false, "")

    private val _capabilities = MutableStateFlow<PcCapabilities?>(null)
    /** Last answer from the PC; null until checked. */
    val capabilities: StateFlow<PcCapabilities?> = _capabilities.asStateFlow()
    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    init {
        scope.launch {
            settings.preferences.collect { prefs ->
                target = Target(prefs.syncWebUrl.trim(), prefs.syncPassword, prefs.syncTlsEnabled, prefs.syncCertSha256)
            }
        }
    }

    /** The PC is set up (Settings → Sync through the web version: PC address and sync password). */
    fun isConfigured(): Boolean = target.url.isNotBlank() && target.password.isNotBlank()

    suspend fun refreshCapabilities(): PcCapabilities = withContext(Dispatchers.IO) {
        try {
            val caps = json.decodeFromString(PcCapabilities.serializer(), call(Request.Builder().url(url("/api/ai/capabilities")).get()))
            _capabilities.value = caps
            _status.value = caps.harnesses.joinToString("\n") { (if (it.available) "✓ " else "✗ ") + it.detail }
            caps
        } catch (failure: Exception) {
            _status.value = failure.message ?: "Could not reach the PC."
            throw failure
        }
    }

    suspend fun complete(harness: String, model: String, system: String, prompt: String, images: List<ImageAttachment>): String =
        withContext(Dispatchers.IO) {
            val body = json.encodeToString(CompleteBody.serializer(), CompleteBody(harness, model, system, prompt, images.map { it.base64Data }))
            val id = submit("/api/ai/complete", body)
            awaitJob(id, timeoutMinutes = 12).text
        }

    suspend fun editImage(workflow: String, prompt: String, image: ImageAttachment): Pair<ByteArray, String> =
        withContext(Dispatchers.IO) {
            val body = json.encodeToString(
                ImageBody.serializer(),
                ImageBody(workflow, prompt, "grayscale, monochrome, changed linework, extra text, blurry, low quality", image.base64Data),
            )
            val id = submit("/api/ai/image", body)
            val done = awaitJob(id, timeoutMinutes = 25)
            val bytes = base.newCall(authorized(Request.Builder().url(url("/api/ai/jobs/$id/image")).get())).execute().use { response ->
                if (!response.isSuccessful) throw AIError.HttpFailure(response.code, "The PC finished but the picture could not be fetched")
                response.body?.bytes() ?: throw AIError.EmbeddedError("The PC returned an empty picture")
            }
            bytes to done.mimeType.ifBlank { "image/png" }
        }

    private fun submit(path: String, body: String): String {
        val response = call(Request.Builder().url(url(path)).post(body.toRequestBody(JSON)))
        return json.decodeFromString(JobId.serializer(), response).id.ifBlank { throw AIError.EmbeddedError("The PC did not accept the job") }
    }

    private suspend fun awaitJob(id: String, timeoutMinutes: Long): JobStatus {
        val deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(timeoutMinutes)
        var misses = 0
        while (System.currentTimeMillis() < deadline) {
            delay(2000)
            val status = try {
                json.decodeFromString(JobStatus.serializer(), call(Request.Builder().url(url("/api/ai/jobs/$id")).get()))
            } catch (network: AIError.NoNetwork) {
                // Wi-Fi hiccups are fine: the PC keeps working and the job waits to be collected.
                if (++misses > 15) throw network
                continue
            }
            misses = 0
            when (status.state) {
                "done" -> return status
                "error" -> throw AIError.EmbeddedError(status.error.ifBlank { "The PC reported an error" })
            }
        }
        throw AIError.EmbeddedError("The PC did not finish within $timeoutMinutes minutes")
    }

    private fun call(builder: Request.Builder): String {
        val client = clientFor(target)
        try {
            client.newCall(authorized(builder)).execute().use { response ->
                val text = response.body?.string().orEmpty()
                return when (response.code) {
                    in 200..299 -> text
                    401 -> throw AIError.InvalidKey("The PC rejected the sync password (Settings → Sync through the web version).")
                    404 -> throw AIError.HttpFailure(404, "The PC's Weaverse companion is too old for PC harnesses — update it.")
                    else -> throw AIError.HttpFailure(response.code, text.take(300).ifBlank { "HTTP ${response.code}" })
                }
            }
        } catch (io: IOException) {
            throw AIError.NoNetwork(io)
        }
    }

    private fun authorized(builder: Request.Builder): Request =
        builder.header("Authorization", "Bearer ${target.password}").build()

    private fun url(path: String): String {
        if (!isConfigured()) {
            throw AIError.NoProvider("Set your PC's address and sync password in Settings → Sync through the web version, and start Weaverse Desktop on the PC.")
        }
        return normalizeSyncBaseUrl(target.url).trimEnd('/') + path
    }

    private fun clientFor(t: Target): OkHttpClient =
        if (t.tls || t.url.startsWith("https", ignoreCase = true)) {
            SyncTlsPinning.apply(base.newBuilder(), t.pin.takeIf { it.isNotBlank() }).build()
        } else {
            base
        }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}

/** Claude Code and Codex as ordinary text/vision providers; answers arrive whole, not streamed. */
@Singleton
class PcHarnessProvider @Inject constructor(
    private val bridge: PcBridgeClient,
) : AIProvider {
    override val name: String = "PC harness"

    override suspend fun models(): List<ModelInfo> =
        bridge.capabilities.value?.let { PcHarness.modelsFrom(it).first } ?: PcHarness.defaultTextModels

    override fun stream(request: AIRequest): Flow<AIChunk> = flow {
        val result = complete(request)
        emit(AIChunk.Delta(result.text))
        emit(AIChunk.Done)
    }

    override suspend fun complete(request: AIRequest): AIResult {
        val (harness, model) = when {
            request.modelId.startsWith(PcHarness.CLAUDE) -> "claude" to request.modelId.removePrefix(PcHarness.CLAUDE)
            request.modelId.startsWith(PcHarness.CODEX) -> "codex" to request.modelId.removePrefix(PcHarness.CODEX)
            else -> throw AIError.NoProvider("${request.modelId} is a picture model, not a text model.")
        }
        // The CLIs take one prompt; earlier turns are laid out as a transcript.
        val prompt = if (request.messages.size <= 1) {
            request.messages.lastOrNull()?.second.orEmpty()
        } else {
            request.messages.joinToString("\n\n") { (role, text) -> "${role.uppercase()}:\n$text" } +
                "\n\nReply as the ASSISTANT to the last USER message."
        }
        val text = bridge.complete(harness, model, request.systemPrompt, prompt, request.imageAttachments)
        return AIResult(text = text, providerName = name)
    }
}
