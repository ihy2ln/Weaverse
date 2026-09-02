package com.ihy2ln.weaverse.data.sync

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.ihy2ln.weaverse.BuildConfig
import com.ihy2ln.weaverse.data.backup.BackupManager
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.export.novelcrafter.NovelcrafterImporter
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import com.ihy2ln.weaverse.data.db.entities.SnippetEntity
import com.ihy2ln.weaverse.sync.BookSummary
import com.ihy2ln.weaverse.sync.DEFAULT_SYNC_PORT
import com.ihy2ln.weaverse.sync.ImportZipResult
import com.ihy2ln.weaverse.sync.LibrarySummary
import com.ihy2ln.weaverse.sync.NoteDetail
import com.ihy2ln.weaverse.sync.NoteSummary
import com.ihy2ln.weaverse.sync.SyncAuth
import com.ihy2ln.weaverse.sync.SyncMerge
import com.ihy2ln.weaverse.sync.novelcrafter.ImportArt
import com.ihy2ln.weaverse.sync.novelcrafter.NovelcrafterZipParser
import com.ihy2ln.weaverse.sync.SyncPackage
import com.ihy2ln.weaverse.sync.SyncPairRequest
import com.ihy2ln.weaverse.sync.SyncPairResponse
import com.ihy2ln.weaverse.sync.SyncPushResult
import com.ihy2ln.weaverse.sync.SyncStatusResponse
import com.ihy2ln.weaverse.sync.SyncTls
import com.ihy2ln.weaverse.sync.normalizeSyncBaseUrl
import com.ihy2ln.weaverse.sync.web.webAppCss
import com.ihy2ln.weaverse.sync.web.webAppJs
import com.ihy2ln.weaverse.sync.web.webIndexHtml
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.utils.io.jvm.javaio.copyTo
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

data class SyncUiSnapshot(
    val hosting: Boolean = false,
    val port: Int = DEFAULT_SYNC_PORT,
    val pairPin: String = "",
    val deviceName: String = "Weaverse Android",
    val lanAddress: String = "",
    val statusText: String = "",
    val lastError: String = "",
    val peerHost: String = "",
    val peerPin: String = "",
    val autoSync: Boolean = true,
    val tlsEnabled: Boolean = false,
    val certSha256: String = "",
    val conflicts: List<SyncMerge.ConflictEntry> = emptyList(),
)

@Singleton
class SyncCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: WeaverseDatabase,
    private val settings: SettingsRepository,
    private val novelcrafterImporter: NovelcrafterImporter,
    private val backupManager: BackupManager,
    private val mcpTools: com.ihy2ln.weaverse.core.mcp.McpTools,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val deviceId = SyncAuth.newDeviceId()
    private val pairPin = SyncAuth.newPairPin()
    private val sessions = ConcurrentHashMap.newKeySet<String>()
    private val foregrounded = AtomicBoolean(true)

    private val _state = MutableStateFlow(
        SyncUiSnapshot(
            pairPin = pairPin,
            deviceName = "Weaverse · ${Build.MODEL}",
            lanAddress = ipv4Address(),
        ),
    )
    val state: StateFlow<SyncUiSnapshot> = _state.asStateFlow()

    private var server: EmbeddedServer<*, *>? = null
    private var client = buildClient(null)
    private var lastClientPin: String = ""
    private var hostCertSha256: String = ""
    private var hostTls: Boolean = false

    init {
        scope.launch(Dispatchers.Main) {
            ProcessLifecycleOwner.get().lifecycle.addObserver(
                object : DefaultLifecycleObserver {
                    override fun onStart(owner: LifecycleOwner) {
                        foregrounded.set(true)
                    }

                    override fun onStop(owner: LifecycleOwner) {
                        foregrounded.set(false)
                    }
                },
            )
        }
        scope.launch {
            settings.preferences.collect { prefs ->
                _state.update {
                    it.copy(
                        peerHost = prefs.syncWebUrl.ifBlank { it.peerHost },
                        peerPin = prefs.syncPassword.ifBlank { it.peerPin },
                        autoSync = prefs.autoSync,
                        tlsEnabled = prefs.syncTlsEnabled,
                        certSha256 = prefs.syncCertSha256.ifBlank { it.certSha256 },
                    )
                }
                val pin = prefs.syncCertSha256
                if (pin != lastClientPin) {
                    lastClientPin = pin
                    rebuildClient(pin)
                }
            }
        }
        scope.launch {
            while (isActive) {
                delay(FOREGROUND_POLL_MS)
                if (!foregrounded.get()) continue
                val snap = _state.value
                if (snap.autoSync && snap.peerHost.isNotBlank() && snap.peerPin.isNotBlank()) {
                    runCatching { quietSync() }
                }
            }
        }
        scope.launch { refreshConflicts() }
    }

    private val syncDir get() = File(context.filesDir, "sync").also { it.mkdirs() }

    suspend fun startHost(port: Int = DEFAULT_SYNC_PORT) = withContext(Dispatchers.IO) {
        if (server != null) return@withContext
        val tlsEnabled = settings.preferences.first().syncTlsEnabled
        hostTls = tlsEnabled
        val held = if (tlsEnabled) {
            SyncTls.loadOrCreate(File(syncDir, "tls.p12"))
        } else {
            null
        }
        hostCertSha256 = held?.let { SyncTls.fingerprint(it) }.orEmpty()
        val engine = if (held != null) {
            val ks = SyncTls.toKeyStore(held)
            embeddedServer(
                factory = CIO,
                configure = {
                    sslConnector(
                        keyStore = ks,
                        keyAlias = SyncTls.KEY_ALIAS,
                        keyStorePassword = { SyncTls.STORE_PASSWORD.toCharArray() },
                        privateKeyPassword = { SyncTls.STORE_PASSWORD.toCharArray() },
                    ) {
                        host = "0.0.0.0"
                        this.port = port
                    }
                },
            ) {
                configureHost(port)
            }
        } else {
            embeddedServer(CIO, host = "0.0.0.0", port = port) {
                configureHost(port)
            }
        }
        engine.start(wait = false)
        server = engine
        val url = localWebUrl(port)
        _state.update {
            it.copy(
                hosting = true,
                port = port,
                lanAddress = ipv4Address(),
                peerHost = it.peerHost.ifBlank { url },
                tlsEnabled = tlsEnabled,
                certSha256 = hostCertSha256.ifBlank { it.certSha256 },
                statusText = "Web hub running. Open the web link to see the password.",
                lastError = "",
            )
        }
    }

    private fun Application.configureHost(port: Int) {
        install(ServerContentNegotiation) { json(json) }
        routing {
            get("/") { call.respondText(webIndexHtml(), ContentType.Text.Html) }
            get("/app.js") { call.respondText(webAppJs(), ContentType.Text.JavaScript) }
            get("/app.css") { call.respondText(webAppCss(), ContentType.Text.CSS) }
            get("/api/status") {
                call.respond(
                    SyncStatusResponse(
                        deviceId = deviceId,
                        deviceName = _state.value.deviceName,
                        appVersion = BuildConfig.VERSION_NAME,
                        hostMode = "android",
                        port = port,
                        pairPin = pairPin,
                        hasLibrary = true,
                        webUrl = localWebUrl(port),
                        lanHint = ipv4Address(),
                        tls = hostTls,
                        certSha256 = hostCertSha256,
                    ),
                )
            }
            post("/api/pair") {
                val body = call.receive<SyncPairRequest>()
                if (!SyncAuth.constantTimeEquals(body.pin, pairPin)) {
                    call.respond(SyncPairResponse(false, message = "Invalid password"))
                    return@post
                }
                val token = SyncAuth.newSessionToken()
                sessions.add(token)
                call.respond(
                    SyncPairResponse(
                        true,
                        token = token,
                        message = "Paired",
                        certSha256 = hostCertSha256,
                        tls = hostTls,
                    ),
                )
            }
            get("/api/library") {
                if (!authorized(call.request.headers["X-Weaverse-Token"])) {
                    call.respond(LibrarySummary())
                    return@get
                }
                call.respond(librarySummary())
            }
            // MCP (Model Context Protocol) endpoint for CLI harnesses such as
            // Claude Code / OpenCode / Codex CLI. Auth: the sync password as a
            // Bearer token (same secret the web hub pairs with).
            post("/mcp") {
                val pin = pairPin
                val bearer = call.request.headers["Authorization"]
                    ?.removePrefix("Bearer ")?.trim().orEmpty()
                val altPin = call.request.headers["X-MCP-Pin"].orEmpty()
                if (pin.isBlank() || (bearer != pin && altPin != pin)) {
                    call.respond(
                        buildJsonObject {
                            put("jsonrpc", "2.0")
                            put("id", kotlinx.serialization.json.JsonNull)
                            putJsonObject("error") {
                                put("code", -32001)
                                put("message", "Unauthorized — use the sync password as a Bearer token.")
                            }
                        },
                    )
                    return@post
                }
                val rpcRequest = runCatching { call.receive<kotlinx.serialization.json.JsonObject>() }.getOrNull()
                if (rpcRequest == null) {
                    call.respond(
                        buildJsonObject {
                            put("jsonrpc", "2.0")
                            put("id", kotlinx.serialization.json.JsonNull)
                            putJsonObject("error") { put("code", -32700); put("message", "Invalid JSON") }
                        },
                    )
                    return@post
                }
                val response = mcpTools.handle(rpcRequest)
                call.respond(response.response)
            }
            get("/mcp") {
                call.respondText(
                    "Weaverse MCP server. POST JSON-RPC 2.0 here; tools: list_works, list_scenes, " +
                        "read_scene, search_codex, read_codex_entry, list_notes, read_note. " +
                        "Auth: Authorization: Bearer <sync password>.",
                    ContentType.Text.Plain,
                )
            }
            get("/api/notes/{id}") {
                if (!authorized(call.request.headers["X-Weaverse-Token"])) {
                    call.respond(mapOf("ok" to false))
                    return@get
                }
                val id = call.parameters["id"].orEmpty()
                val note = db.snippetDao().getById(id)
                if (note == null || note.category != "notes") {
                    call.respond(mapOf("ok" to false))
                } else {
                    call.respond(NoteDetail(note.id, note.title, note.body))
                }
            }
            put("/api/notes/{id}") {
                if (!authorized(call.request.headers["X-Weaverse-Token"])) {
                    call.respond(mapOf("ok" to false))
                    return@put
                }
                val id = call.parameters["id"].orEmpty()
                val detail = call.receive<NoteDetail>()
                val noteId = id.ifBlank { detail.id }
                val existing = db.snippetDao().getById(noteId)
                db.snippetDao().upsert(
                    SnippetEntity(
                        id = noteId,
                        scopeType = existing?.scopeType ?: "app",
                        scopeId = existing?.scopeId ?: "global",
                        title = detail.title,
                        body = detail.body,
                        category = "notes",
                        pinned = existing?.pinned ?: false,
                        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    ),
                )
                call.respond(mapOf("ok" to true))
            }
            get("/api/media/{id}") {
                val id = call.parameters["id"].orEmpty()
                val piece = ImportArt.pieces.firstOrNull { it.id == id }
                val file = File(context.filesDir, "media/$id.jpg").takeIf { it.exists() }
                    ?: File(context.filesDir, "media/$id.png").takeIf { it.exists() }
                    ?: piece?.let { File(context.filesDir, "media/${it.id}.${it.fileName.substringAfterLast('.')}") }
                if (file != null && file.exists()) {
                    call.respondFile(file)
                } else {
                    call.respond(mapOf("ok" to false))
                }
            }
            post("/api/import") {
                if (!authorized(call.request.headers["X-Weaverse-Token"])) {
                    call.respond(ImportZipResult(false, "Unauthorized"))
                    return@post
                }
                val incoming = File(syncDir, "import-${System.currentTimeMillis()}.zip")
                call.receiveChannel().copyTo(incoming.outputStream())
                val bytes = incoming.readBytes()
                if (!NovelcrafterZipParser.looksLikeNovelcrafterZipBytes(bytes)) {
                    call.respond(
                        ImportZipResult(
                            false,
                            "ZIP not recognized. Use a Novelcrafter full export (novel.md or novel.docx + characters/…).",
                        ),
                    )
                    return@post
                }
                val parsed = NovelcrafterZipParser.parse(bytes)
                val result = novelcrafterImporter.import(parsed)
                settings.setSelectedBookId(result.bookId)
                call.respond(
                    ImportZipResult(
                        ok = true,
                        message = "Imported “${result.bookTitle}”",
                        bookId = result.bookId,
                        bookTitle = result.bookTitle,
                        sceneCount = result.sceneCount,
                        codexCount = result.codexCount,
                        rpChatCount = result.rpChatCount,
                        mediaCount = result.mediaCount,
                    ),
                )
            }
            get("/api/sync/pull") {
                val token = call.request.headers["X-Weaverse-Token"]
                if (!authorized(token)) {
                    call.respond(mapOf("ok" to false, "message" to "Unauthorized"))
                    return@get
                }
                val zip = buildLocalPackage()
                call.respondFile(zip)
            }
            post("/api/sync/push") {
                val token = call.request.headers["X-Weaverse-Token"]
                if (!authorized(token)) {
                    call.respond(SyncPushResult(false, "Unauthorized"))
                    return@post
                }
                val incoming = File(syncDir, "incoming-${System.currentTimeMillis()}.zip")
                call.receiveChannel().copyTo(incoming.outputStream())
                val report = mergePackage(incoming)
                call.respond(
                    SyncPushResult(
                        true,
                        "Merged on Android host — ${report.summary}",
                        appliedRows = report.appliedRows,
                        deletedRows = report.deletedRows,
                        conflicts = report.conflicts,
                    ),
                )
            }
        }
    }

    fun suggestedWebUrl(): String {
        val peer = normalizeSyncBaseUrl(_state.value.peerHost)
        if (peer.isNotBlank()) return peer
        if (_state.value.hosting) return localWebUrl(_state.value.port)
        return "${if (_state.value.tlsEnabled) "https" else "http"}://127.0.0.1:$DEFAULT_SYNC_PORT"
    }

    suspend fun stopHost() = withContext(Dispatchers.IO) {
        runCatching { server?.stop(500, 1000) }
        server = null
        hostTls = false
        hostCertSha256 = ""
        _state.update { it.copy(hosting = false, statusText = "Host stopped") }
    }

    fun setPeer(host: String, pin: String) {
        _state.update { it.copy(peerHost = host.trim(), peerPin = pin.trim()) }
        scope.launch { settings.setSyncPeer(host.trim(), pin.trim()) }
    }

    fun setAutoSync(enabled: Boolean) {
        _state.update { it.copy(autoSync = enabled) }
        scope.launch { settings.setAutoSync(enabled) }
    }

    fun setTlsEnabled(enabled: Boolean) {
        _state.update { it.copy(tlsEnabled = enabled) }
        scope.launch {
            settings.setSyncTlsEnabled(enabled)
            if (_state.value.hosting) {
                stopHost()
                startHost()
            }
        }
    }

    fun keepMine(entry: SyncMerge.ConflictEntry) {
        scope.launch {
            runCatching { SyncMerge.restoreLost(liveSql(), entry) }
            refreshConflicts()
        }
    }

    fun keepTheirs(entry: SyncMerge.ConflictEntry) {
        scope.launch {
            runCatching { SyncMerge.dismissConflict(liveSql(), entry) }
            refreshConflicts()
        }
    }

    private suspend fun quietSync() {
        val host = normalizeHost(_state.value.peerHost)
        val pin = _state.value.peerPin
        if (host.isBlank() || pin.isBlank()) return
        runCatching { pair(host, pin) }
        val remote = runCatching {
            client.get("$host/api/status").body<SyncStatusResponse>().lastSyncAt ?: 0L
        }.getOrDefault(0L)
        val localDb = context.getDatabasePath("weaverse.db")
        val localMtime = if (localDb.exists()) localDb.lastModified() else 0L
        val last = settings.preferences.first().lastSyncAt
        if (remote > last + 2000 && remote >= localMtime) {
            pullFromPeer()
        } else if (localMtime > last + 2000) {
            pushToPeer()
            settings.setLastSyncAt(System.currentTimeMillis())
        }
    }

    suspend fun pushToPeer() = withContext(Dispatchers.IO) {
        val host = normalizeHost(_state.value.peerHost)
        val pin = _state.value.peerPin
        if (host.isBlank() || pin.isBlank()) {
            _state.update { it.copy(lastError = "Open the web hub, then enter its password") }
            return@withContext
        }
        runCatching {
            val token = pair(host, pin)
            val zip = buildLocalPackage()
            val result = client.post("$host/api/sync/push") {
                header("X-Weaverse-Token", token)
                contentType(ContentType.Application.OctetStream)
                setBody(zip.readBytes())
            }.body<SyncPushResult>()
            if (result.ok) settings.setLastSyncAt(System.currentTimeMillis())
            _state.update {
                it.copy(
                    statusText = if (result.ok) {
                        "Pushed to the web hub" +
                            if (result.appliedRows > 0 || result.conflicts > 0) {
                                " — ${result.appliedRows} applied, ${result.conflicts} conflicts"
                            } else {
                                ""
                            }
                    } else {
                        result.message
                    },
                    lastError = if (result.ok) "" else result.message,
                )
            }
            refreshConflicts()
        }.onFailure { err ->
            _state.update { it.copy(lastError = err.message ?: "Push failed") }
        }
    }

    suspend fun pullFromPeer() = withContext(Dispatchers.IO) {
        val host = normalizeHost(_state.value.peerHost)
        val pin = _state.value.peerPin
        if (host.isBlank() || pin.isBlank()) {
            _state.update { it.copy(lastError = "Open the web hub, then enter its password") }
            return@withContext
        }
        runCatching {
            val token = pair(host, pin)
            val response = client.get("$host/api/sync/pull") {
                header("X-Weaverse-Token", token)
            }
            if (response.status.value >= 400) error("Pull failed (${response.status})")
            val incoming = File(syncDir, "pulled-${System.currentTimeMillis()}.zip")
            response.bodyAsChannel().toInputStream().use { input ->
                incoming.outputStream().use { input.copyTo(it) }
            }
            val report = mergePackage(incoming)
            settings.setLastSyncAt(System.currentTimeMillis())
            _state.update {
                it.copy(
                    statusText = "Merged from the web hub — ${report.summary}",
                    lastError = "",
                )
            }
        }.onFailure { err ->
            _state.update { it.copy(lastError = err.message ?: "Pull failed") }
        }
    }

    private suspend fun pair(host: String, pin: String): String {
        val res = client.post("$host/api/pair") {
            contentType(ContentType.Application.Json)
            setBody(SyncPairRequest(pin))
        }.body<SyncPairResponse>()
        val token = res.token
        if (!res.ok || token.isNullOrBlank()) error(res.message.ifBlank { "Pair failed" })
        if (res.certSha256.isNotBlank()) {
            settings.setSyncCertSha256(res.certSha256)
            rebuildClient(res.certSha256)
            _state.update { it.copy(certSha256 = res.certSha256) }
        }
        return token
    }

    private fun authorized(token: String?): Boolean {
        if (token.isNullOrBlank()) return false
        if (sessions.contains(token)) return true
        return SyncAuth.constantTimeEquals(token, pairPin)
    }

    private fun buildLocalPackage(): File {
        runCatching { db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close() }
        val zip = File(syncDir, "local-package.zip")
        SyncPackage.writePackage(
            dbFile = context.getDatabasePath("weaverse.db"),
            mediaDir = File(context.filesDir, "media"),
            outZip = zip,
            deviceId = deviceId,
            deviceName = _state.value.deviceName,
            appVersion = BuildConfig.VERSION_NAME,
        )
        return zip
    }

    private suspend fun mergePackage(zip: File): SyncMerge.Report {
        backupManager.snapshotBeforeMerge("sync")
        runCatching { db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close() }
        val report = SyncPackage.mergeFromZip(
            zipFile = zip,
            live = liveSql(),
            mediaDir = File(context.filesDir, "media"),
        )
        refreshConflicts()
        return report
    }

    private fun liveSql(): RoomSyncSql = RoomSyncSql(db.openHelper.writableDatabase)

    private fun refreshConflicts() {
        val list = runCatching { SyncMerge.conflicts(liveSql()) }.getOrDefault(emptyList())
        _state.update { it.copy(conflicts = list) }
    }

    private fun rebuildClient(pin: String?) {
        runCatching { client.close() }
        client = buildClient(pin?.takeIf { it.isNotBlank() })
    }

    private fun buildClient(pin: String?): HttpClient = HttpClient(OkHttp) {
        expectSuccess = false
        engine {
            config {
                SyncTlsPinning.apply(this, pin)
            }
        }
        install(ContentNegotiation) { json(json) }
    }

    private suspend fun librarySummary(): LibrarySummary {
        val books = db.bookDao().getAll().map { BookSummary(it.id, it.title, it.updatedAt) }
        val notes = db.snippetDao().getByCategory("notes").map {
            NoteSummary(it.id, it.title, it.body.take(160), it.createdAt)
        }
        return LibrarySummary(books, notes)
    }

    private fun localWebUrl(port: Int): String {
        val ip = ipv4Address().ifBlank { "127.0.0.1" }
        val scheme = if (hostTls) "https" else "http"
        return "$scheme://$ip:$port"
    }

    private fun normalizeHost(raw: String): String = normalizeSyncBaseUrl(raw)

    private fun ipv4Address(): String {
        return runCatching {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val ip = wm.connectionInfo.ipAddress
            if (ip == 0) return@runCatching ""
            String.format(
                "%d.%d.%d.%d",
                ip and 0xff,
                ip shr 8 and 0xff,
                ip shr 16 and 0xff,
                ip shr 24 and 0xff,
            )
        }.getOrDefault("")
    }

    companion object {
        private const val FOREGROUND_POLL_MS = 8_000L
    }
}
