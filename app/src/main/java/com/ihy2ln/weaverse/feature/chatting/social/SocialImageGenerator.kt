package com.ihy2ln.weaverse.feature.chatting.social

import android.util.Base64
import com.ihy2ln.weaverse.ai.AiGenerationService
import com.ihy2ln.weaverse.ai.ImageAttachment
import com.ihy2ln.weaverse.core.media.MediaRepository
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.MediaEntity
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** One original fictional image at most per refresh; the caller enforces that budget. */
@Singleton
class SocialImageGenerator @Inject constructor(
    private val ai: AiGenerationService,
    private val settings: SettingsRepository,
    private val media: MediaRepository,
    private val db: WeaverseDatabase,
    client: OkHttpClient,
) {
    private val http = client.newBuilder().callTimeout(45, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun generate(prompt: String, referenceMediaId: String? = null): MediaEntity? {
        val endpoint = settings.socialString("comfy_endpoint").first()
        val workflow = settings.socialString("comfy_workflow").first()
        val result = if (endpoint.isNotBlank() && workflow.isNotBlank()) {
            runCatching { comfy(endpoint, workflow, prompt) }.getOrNull()
        } else null
        val generated = result ?: run {
            val model = settings.socialString("image_model").first()
                .ifBlank { settings.preferences.first().mangaImageModelRef }
            if (!model.startsWith("openrouter/")) return null
            val reference = referenceMediaId?.let { id ->
                runCatching {
                    val item = media.getById(id) ?: return@runCatching null
                    val file = media.resolveFile(item)
                    if (!file.isFile || file.length() > 8 * 1024 * 1024 || !item.mimeType.startsWith("image/")) null
                    else ImageAttachment(item.mimeType, Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
                }.getOrNull()
            }
            runCatching { ai.generateImage(prompt, model, listOfNotNull(reference)) }
                .recoverCatching { ai.generateImage(prompt, model) }
                .getOrNull()?.let { (bytes, mime) -> Triple(bytes, mime, "OpenRouter") }
        } ?: return null
        val (bytes, mime, source) = generated
        if (bytes.isEmpty() || bytes.size > 15 * 1024 * 1024 || !mime.startsWith("image/")) return null
        val id = UUID.randomUUID().toString()
        val ext = if (mime.contains("png")) "png" else if (mime.contains("webp")) "webp" else "jpg"
        val imported = media.importFromBytes(bytes, id, "$id.$ext", mime)
        return imported.copy(displayName = "WeaverSocial original image", category = "WeaverSocial",
            tags = "AI generated, fictional adult character", sourceSite = source,
            sourceCredit = "Original AI image").also { db.mediaDao().upsert(it) }
    }

    /** An exported ComfyUI API workflow with __PROMPT__ and optional __SEED__ placeholders. */
    private suspend fun comfy(endpoint: String, workflow: String, prompt: String): Triple<ByteArray, String, String> = withContext(Dispatchers.IO) {
        val base = endpoint.trimEnd('/').toHttpUrlOrNull() ?: error("Invalid ComfyUI URL")
        require(base.scheme == "http" || base.scheme == "https")
        require("__PROMPT__" in workflow) { "ComfyUI workflow needs a __PROMPT__ placeholder" }
        val payload = workflow.replace("__PROMPT__", prompt.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " "))
            .replace("__SEED__", (System.currentTimeMillis() % Int.MAX_VALUE).toString())
        val graph = json.parseToJsonElement(payload) as? JsonObject ?: error("ComfyUI workflow must be an API JSON object")
        val body = JsonObject(mapOf("prompt" to graph)).toString().toRequestBody("application/json".toMediaType())
        val queued = http.newCall(Request.Builder().url(base.newBuilder().addPathSegment("prompt").build())
            .post(body).build()).execute().use { response ->
            require(response.isSuccessful) { "ComfyUI queue HTTP ${response.code}" }
            json.parseToJsonElement(response.body?.string().orEmpty()) as JsonObject
        }
        val id = (queued["prompt_id"] as? JsonPrimitive)?.contentOrNull ?: error("ComfyUI returned no prompt id")
        repeat(45) {
            delay(2_000)
            val history = http.newCall(Request.Builder().url(base.newBuilder().addPathSegment("history").addPathSegment(id).build()).build())
                .execute().use { response ->
                    require(response.isSuccessful) { "ComfyUI history HTTP ${response.code}" }
                    json.parseToJsonElement(response.body?.string().orEmpty()) as? JsonObject
                }
            val job = history?.get(id) as? JsonObject
            val outputs = job?.get("outputs") as? JsonObject
            val image = outputs?.values?.asSequence()?.mapNotNull { it as? JsonObject }
                ?.flatMap { ((it["images"] as? JsonArray)?.asSequence() ?: emptySequence()).mapNotNull { v -> v as? JsonObject } }
                ?.firstOrNull()
            if (image != null) {
                val filename = (image["filename"] as? JsonPrimitive)?.contentOrNull ?: error("ComfyUI image missing filename")
                val subfolder = (image["subfolder"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                val type = (image["type"] as? JsonPrimitive)?.contentOrNull ?: "output"
                val url = base.newBuilder().addPathSegment("view").addQueryParameter("filename", filename)
                    .addQueryParameter("subfolder", subfolder).addQueryParameter("type", type).build()
                return@withContext http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    require(response.isSuccessful) { "ComfyUI image HTTP ${response.code}" }
                    Triple(response.body?.bytes() ?: error("Empty ComfyUI image"),
                        response.header("Content-Type")?.substringBefore(';') ?: "image/png", "ComfyUI")
                }
            }
        }
        error("ComfyUI image generation timed out")
    }

    suspend fun comfyStatus(): String = withContext(Dispatchers.IO) {
        val url = settings.socialString("comfy_endpoint").first().trimEnd('/').toHttpUrlOrNull()
            ?: return@withContext "ComfyUI endpoint not set"
        runCatching {
            http.newCall(Request.Builder().url(url.newBuilder().addPathSegment("system_stats").build()).build())
                .execute().use { if (it.isSuccessful) "ComfyUI connected" else "ComfyUI HTTP ${it.code}" }
        }.getOrElse { "ComfyUI unreachable: ${it.message.orEmpty().take(80)}" }
    }
}
