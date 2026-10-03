package com.ihy2ln.weaverse.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Serializable
data class AiHarnessStatus(
    val id: String,
    val available: Boolean,
    val detail: String,
    val models: List<String> = emptyList(),
)

@Serializable
data class AiBridgeCapabilities(val harnesses: List<AiHarnessStatus>)

/** Text (+ optional images) for Claude Code or Codex. Images are base64 PNG/JPEG/WebP. */
@Serializable
data class AiCompleteRequest(
    val harness: String,
    val model: String = "",
    val system: String = "",
    val prompt: String,
    val images: List<String> = emptyList(),
)

/** One reference image through a ComfyUI workflow. */
@Serializable
data class AiImageRequest(
    val workflow: String = AiBridge.DEFAULT_WORKFLOW,
    val prompt: String,
    val negative: String = "",
    val image: String,
    val steps: Int = 0,
    val seed: Long = -1,
)

@Serializable
data class AiJobStatus(
    val id: String,
    val state: String,
    val text: String = "",
    val mimeType: String = "",
    val error: String = "",
    val elapsedSeconds: Long = 0,
)

/**
 * Runs the PC's own AI tools for the phone: Claude Code and Codex (ChatGPT) in headless mode,
 * and ComfyUI workflows. Nothing here is a raw shell: each harness runs with fixed flags in an
 * empty scratch folder, may only read the images it was given, and ComfyUI stays on this PC.
 * Work runs as jobs the phone polls, so a 4-minute ComfyUI page never holds an HTTP request open.
 */
class AiBridge(
    private val dataDir: File,
    private val config: DesktopConfig,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val comfyLock = Mutex()
    private val cliSlots = Semaphore(2)

    private class Job(val started: Long = System.currentTimeMillis()) {
        @Volatile var state = "running"
        @Volatile var text = ""
        @Volatile var bytes: ByteArray? = null
        @Volatile var mimeType = ""
        @Volatile var error = ""
        @Volatile var finished = 0L
    }

    fun capabilities(): AiBridgeCapabilities = AiBridgeCapabilities(
        listOf(
            cliStatus(CLAUDE, config.claudePath, listOf("sonnet", "opus", "haiku")),
            cliStatus(CODEX, config.codexPath, listOf("default")),
            comfyStatus(),
        ),
    )

    fun submitComplete(request: AiCompleteRequest): String = submit { job ->
        cliSlots.withPermit {
            job.text = when (request.harness) {
                CLAUDE -> runClaude(request)
                CODEX -> runCodex(request)
                else -> error("Unknown harness: ${request.harness}")
            }
        }
    }

    fun submitImage(request: AiImageRequest): String = submit { job ->
        comfyLock.withLock {
            val (bytes, mime) = runComfy(request)
            job.bytes = bytes
            job.mimeType = mime
        }
    }

    fun status(id: String): AiJobStatus? {
        prune()
        val job = jobs[id] ?: return null
        return AiJobStatus(
            id = id,
            state = job.state,
            text = job.text,
            mimeType = job.mimeType,
            error = job.error,
            elapsedSeconds = (System.currentTimeMillis() - job.started) / 1000,
        )
    }

    /** The finished image; the job is forgotten once it has been collected. */
    fun takeImage(id: String): Pair<ByteArray, String>? {
        val job = jobs[id] ?: return null
        val bytes = job.bytes ?: return null
        jobs.remove(id)
        return bytes to job.mimeType
    }

    private fun submit(work: suspend (Job) -> Unit): String {
        prune()
        val id = UUID.randomUUID().toString()
        val job = Job()
        jobs[id] = job
        scope.launch {
            try {
                work(job)
                job.state = "done"
            } catch (failure: Throwable) {
                job.error = failure.message?.take(2000) ?: failure.javaClass.simpleName
                job.state = "error"
            } finally {
                job.finished = System.currentTimeMillis()
            }
        }
        return id
    }

    private fun prune() {
        val cutoff = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(30)
        jobs.entries.removeIf { it.value.finished in 1 until cutoff }
    }

    // ---------------------------------------------------------------- CLI harnesses

    private fun cliStatus(id: String, configured: String, models: List<String>): AiHarnessStatus {
        val exe = findExecutable(id, configured)
            ?: return AiHarnessStatus(id, false, "${label(id)} is not installed on this PC (or not on PATH).")
        val version = runCatching { runProcess(command(exe, listOf("--version")), "", workDir(), 30).trim().lines().first() }
            .getOrElse { return AiHarnessStatus(id, false, "${label(id)} found at $exe but did not start: ${it.message}") }
        return AiHarnessStatus(id, true, "${label(id)} $version", models)
    }

    private fun runClaude(request: AiCompleteRequest): String {
        val exe = findExecutable(CLAUDE, config.claudePath) ?: error("Claude Code is not installed on the PC.")
        val dir = workDir()
        try {
            val images = writeImages(dir, request.images)
            val args = mutableListOf(
                "-p", "--output-format", "json",
                "--allowedTools", "Read",
                "--disallowedTools", "Bash,Edit,Write,MultiEdit,WebFetch,WebSearch,Task,NotebookEdit",
                "--max-turns", if (images.isEmpty()) "1" else (images.size + 2).toString(),
            )
            safeModel(request.model)?.let { args += listOf("--model", it) }
            val out = runProcess(command(exe, args), composePrompt(request, images, "use the Read tool"), dir, CLI_TIMEOUT_S)
            val result = runCatching { json.parseToJsonElement(out.trim().lines().last { it.isNotBlank() }).jsonObject }
                .getOrElse { error("Claude Code returned something unreadable: ${out.take(300)}") }
            val text = result["result"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (result["is_error"]?.jsonPrimitive?.booleanOrNull == true) {
                error(
                    if (text.contains("authenticate", true) || text.contains("login", true)) {
                        "Claude Code on the PC is signed out. Open a terminal on the PC, run `claude`, then /login. ($text)"
                    } else {
                        "Claude Code: $text"
                    },
                )
            }
            return text
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun runCodex(request: AiCompleteRequest): String {
        val exe = findExecutable(CODEX, config.codexPath)
            ?: error("The ChatGPT Codex CLI is not installed on the PC. Install it with `npm install -g @openai/codex`, then run `codex login`.")
        val dir = workDir()
        try {
            val images = writeImages(dir, request.images)
            val output = File(dir, "answer.txt")
            val args = mutableListOf(
                "exec", "--skip-git-repo-check", "--sandbox", "read-only", "--color", "never",
                "--output-last-message", output.absolutePath,
            )
            safeModel(request.model)?.takeIf { it != "default" }?.let { args += listOf("--model", it) }
            images.forEach { args += listOf("--image", it.absolutePath) }
            args += "-"
            val log = runProcess(command(exe, args), composePrompt(request, emptyList(), ""), dir, CLI_TIMEOUT_S)
            val text = output.takeIf(File::isFile)?.readText()?.trim().orEmpty()
            if (text.isBlank()) {
                error(
                    if (log.contains("login", true) || log.contains("auth", true)) {
                        "Codex on the PC is signed out. Run `codex login` on the PC. (${log.takeLast(300)})"
                    } else {
                        "Codex returned no answer: ${log.takeLast(400)}"
                    },
                )
            }
            return text
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun composePrompt(request: AiCompleteRequest, images: List<File>, howToRead: String): String = buildString {
        if (request.system.isNotBlank()) {
            append("<instructions>\n").append(request.system.trim()).append("\n</instructions>\n\n")
        }
        if (images.isNotEmpty()) {
            append("Attached images ($howToRead): ")
            append(images.joinToString(", ") { "./" + it.name }).append("\n\n")
        }
        append(request.prompt.trim())
        append("\n\nAnswer directly with the requested output only; do not edit files or run commands.")
    }

    private fun writeImages(dir: File, images: List<String>): List<File> = images.mapIndexed { index, b64 ->
        val bytes = Base64.getDecoder().decode(b64.substringAfter("base64,"))
        File(dir, "image_${index + 1}.${imageExtension(bytes)}").apply { writeBytes(bytes) }
    }

    private fun safeModel(model: String): String? =
        model.trim().takeIf { it.isNotBlank() && it.matches(Regex("[A-Za-z0-9._:\\-]{1,80}")) }

    private fun workDir(): File = File(dataDir, "ai-scratch/${UUID.randomUUID()}").apply { mkdirs() }

    private fun findExecutable(id: String, configured: String): File? {
        configured.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf(File::isFile)?.let { return it }
        val names = if (isWindows) listOf("$id.exe", "$id.cmd", "$id.bat") else listOf(id)
        val path = System.getenv("PATH").orEmpty().split(File.pathSeparatorChar).toMutableList()
        if (isWindows) {
            System.getenv("APPDATA")?.let { path += "$it\\npm" }
            System.getenv("USERPROFILE")?.let { path += listOf("$it\\.local\\bin", "$it\\AppData\\Roaming\\npm") }
        }
        return path.asSequence().filter { it.isNotBlank() }
            .flatMap { dir -> names.asSequence().map { File(dir, it) } }
            .firstOrNull(File::isFile)
    }

    /** .cmd/.bat shims (npm installs) need cmd.exe; prompts always go through stdin, never arguments. */
    private fun command(exe: File, args: List<String>): List<String> =
        if (isWindows && exe.extension.lowercase() in setOf("cmd", "bat")) listOf("cmd.exe", "/c", exe.absolutePath) + args
        else listOf(exe.absolutePath) + args

    private fun runProcess(command: List<String>, stdin: String, dir: File, timeoutSeconds: Long): String {
        val process = ProcessBuilder(command).directory(dir).redirectErrorStream(true).start()
        val output = StringBuilder()
        val reader = Thread {
            process.inputStream.bufferedReader().use { r -> r.lineSequence().forEach { output.appendLine(it) } }
        }.apply { isDaemon = true; start() }
        process.outputStream.bufferedWriter().use { it.write(stdin) }
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.descendants().forEach { it.destroyForcibly() }
            process.destroyForcibly()
            error("Timed out after ${timeoutSeconds}s")
        }
        reader.join(5000)
        return output.toString()
    }

    // ---------------------------------------------------------------- ComfyUI

    private fun comfyStatus(): AiHarnessStatus {
        val stats = runCatching { httpGet("/system_stats").decodeToString() }.getOrElse {
            return AiHarnessStatus(COMFY, false, "ComfyUI is not answering at ${config.comfyUrl} — start it on the PC.")
        }
        val version = runCatching {
            json.parseToJsonElement(stats).jsonObject["system"]?.jsonObject?.get("comfyui_version")?.jsonPrimitive?.contentOrNull
        }.getOrNull().orEmpty()
        return AiHarnessStatus(COMFY, true, "ComfyUI $version at ${config.comfyUrl}".trim(), workflowNames())
    }

    /** The built-in Qwen Image Edit 2.1 workflow plus any API-format JSON dropped into data/comfy-workflows. */
    private fun workflowNames(): List<String> =
        listOf(DEFAULT_WORKFLOW) + customWorkflowDir().listFiles { f -> f.extension == "json" }.orEmpty()
            .map { it.nameWithoutExtension }.sorted()

    private fun customWorkflowDir(): File = File(dataDir, "comfy-workflows").apply { mkdirs() }

    private fun workflowTemplate(name: String): String {
        if (name == DEFAULT_WORKFLOW) {
            return AiBridge::class.java.getResourceAsStream("/comfy/$DEFAULT_WORKFLOW.json")!!.readBytes().decodeToString()
        }
        val file = File(customWorkflowDir(), "$name.json")
        require(file.isFile && file.parentFile == customWorkflowDir()) { "No ComfyUI workflow named $name" }
        return file.readText()
    }

    private fun runComfy(request: AiImageRequest): Pair<ByteArray, String> {
        val image = Base64.getDecoder().decode(request.image.substringAfter("base64,"))
        val uploaded = upload(image, "weaverse_${UUID.randomUUID()}.${imageExtension(image)}")
        val seed = if (request.seed >= 0) request.seed else (Math.random() * 1e15).toLong()
        val steps = request.steps.takeIf { it in 1..100 } ?: config.comfySteps
        val graph = fill(
            json.parseToJsonElement(workflowTemplate(request.workflow)),
            mapOf(
                "__IMAGE__" to JsonPrimitive(uploaded),
                "__PROMPT__" to JsonPrimitive(request.prompt),
                "__NEGATIVE__" to JsonPrimitive(request.negative),
                "__SEED__" to JsonPrimitive(seed),
                "__STEPS__" to JsonPrimitive(steps),
            ),
        )
        val body = JsonObject(mapOf("prompt" to graph, "client_id" to JsonPrimitive("weaverse"))).toString()
        val queued = runCatching { httpPost("/prompt", body.toByteArray(), "application/json") }
            .getOrElse { error("ComfyUI refused the workflow: ${it.message}") }
        val promptId = json.parseToJsonElement(queued.decodeToString()).jsonObject["prompt_id"]!!.jsonPrimitive.content
        val deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(20)
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(1500)
            val history = json.parseToJsonElement(httpGet("/history/$promptId").decodeToString()).jsonObject
            val entry = history[promptId]?.jsonObject ?: continue
            val status = entry["status"]?.jsonObject
            if (status?.get("status_str")?.jsonPrimitive?.contentOrNull == "error") {
                error("ComfyUI failed: " + status.toString().take(600))
            }
            val outputs = entry["outputs"]?.jsonObject ?: continue
            val picture = outputs.values.asSequence()
                .mapNotNull { it.jsonObject["images"]?.jsonArray?.firstOrNull()?.jsonObject }
                .firstOrNull() ?: continue
            fun field(key: String) = URLEncoder.encode(picture[key]?.jsonPrimitive?.contentOrNull.orEmpty(), Charsets.UTF_8)
            val bytes = httpGet("/view?filename=${field("filename")}&subfolder=${field("subfolder")}&type=${field("type")}")
            return bytes to "image/${imageExtension(bytes).replace("jpg", "jpeg")}"
        }
        error("ComfyUI did not finish within 20 minutes")
    }

    /** Swaps "__NAME__" string values anywhere in the graph for their values (numbers stay numbers). */
    private fun fill(element: JsonElement, values: Map<String, JsonElement>): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.mapValues { fill(it.value, values) })
        is JsonArray -> JsonArray(element.map { fill(it, values) })
        is JsonPrimitive -> if (element.isString) values[element.content] ?: element else element
    }

    private fun upload(bytes: ByteArray, name: String): String {
        val boundary = "----weaverse" + UUID.randomUUID().toString().replace("-", "")
        val body = buildList<ByteArray> {
            add("--$boundary\r\nContent-Disposition: form-data; name=\"image\"; filename=\"$name\"\r\nContent-Type: application/octet-stream\r\n\r\n".toByteArray())
            add(bytes)
            add("\r\n--$boundary\r\nContent-Disposition: form-data; name=\"overwrite\"\r\n\r\ntrue\r\n--$boundary--\r\n".toByteArray())
        }.reduce { a, b -> a + b }
        val response = httpPost("/upload/image", body, "multipart/form-data; boundary=$boundary")
        return json.parseToJsonElement(response.decodeToString()).jsonObject["name"]!!.jsonPrimitive.content
    }

    private fun httpGet(path: String): ByteArray = http(path, "GET", null, null)
    private fun httpPost(path: String, body: ByteArray, type: String): ByteArray = http(path, "POST", body, type)

    private fun http(path: String, method: String, body: ByteArray?, type: String?): ByteArray {
        val connection = URI(config.comfyUrl.trimEnd('/') + path).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 5000
        connection.readTimeout = 120_000
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", type)
            connection.outputStream.use { it.write(body) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
        if (code !in 200..299) error("HTTP $code ${bytes.decodeToString().take(800)}")
        return bytes
    }

    private fun imageExtension(bytes: ByteArray): String = when {
        bytes.size > 3 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() -> "png"
        bytes.size > 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "jpg"
        bytes.size > 11 && String(bytes, 8, 4) == "WEBP" -> "webp"
        else -> "png"
    }

    private fun label(id: String) = when (id) {
        CLAUDE -> "Claude Code"
        CODEX -> "Codex (ChatGPT)"
        else -> "ComfyUI"
    }

    companion object {
        const val CLAUDE = "claude"
        const val CODEX = "codex"
        const val COMFY = "comfyui"
        const val DEFAULT_WORKFLOW = "qwen-image-edit"
        private const val CLI_TIMEOUT_S = 600L
        private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
    }
}
