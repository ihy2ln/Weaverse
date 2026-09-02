package com.ihy2ln.weaverse.desktop

import com.ihy2ln.weaverse.sync.ImportZipResult
import com.ihy2ln.weaverse.sync.JdbcSyncSql
import com.ihy2ln.weaverse.sync.LibrarySummary
import com.ihy2ln.weaverse.sync.NoteDetail
import com.ihy2ln.weaverse.sync.SceneDetail
import com.ihy2ln.weaverse.sync.SyncAuth
import com.ihy2ln.weaverse.sync.WorkspaceSnapshot
import com.ihy2ln.weaverse.sync.SyncMerge
import com.ihy2ln.weaverse.sync.SyncPackage
import com.ihy2ln.weaverse.sync.SyncPairRequest
import com.ihy2ln.weaverse.sync.SyncPairResponse
import com.ihy2ln.weaverse.sync.SyncPushResult
import com.ihy2ln.weaverse.sync.SyncSchema
import com.ihy2ln.weaverse.sync.SyncStatusResponse
import com.ihy2ln.weaverse.sync.SyncTls
import com.ihy2ln.weaverse.sync.novelcrafter.NovelcrafterSqliteImporter
import com.ihy2ln.weaverse.sync.novelcrafter.NovelcrafterZipParser
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
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
import kotlinx.serialization.json.Json
import java.io.File
import java.net.InetAddress
import java.net.NetworkInterface
import java.sql.DriverManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

class SyncHttpServer(
    private val dataDir: File,
    private val config: DesktopConfig,
) {
    private val sessions = ConcurrentHashMap.newKeySet<String>()
    private val lastSyncAt = AtomicReference<Long?>(null)
    private var engine: EmbeddedServer<*, *>? = null
    private var certSha256: String = ""

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun start(): EmbeddedServer<*, *> {
        maybeAutoImport()
        val held = if (config.tls) SyncTls.loadOrCreate(DesktopPaths.tlsFile(dataDir)) else null
        certSha256 = held?.let { SyncTls.fingerprint(it) }.orEmpty()
        val server = if (held != null) {
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
                        port = config.port
                    }
                },
            ) {
                configure()
            }
        } else {
            embeddedServer(CIO, host = "0.0.0.0", port = config.port) {
                configure()
            }
        }
        engine = server
        server.start(wait = false)
        return server
    }

    fun stop() {
        engine?.stop(1000, 2000)
        engine = null
    }

    private fun Application.configure() {
        install(ContentNegotiation) { json(json) }
        routing {
            get("/") {
                call.respondText(webIndexHtml(), ContentType.Text.Html)
            }
            get("/app.js") {
                call.respondText(webAppJs(), ContentType.Text.JavaScript)
            }
            get("/app.css") {
                call.respondText(webAppCss(), ContentType.Text.CSS)
            }
            get("/api/status") {
                val summary = LibraryReader.summarize(DesktopPaths.dbFile(dataDir))
                val lan = localLanAddresses().joinToString(", ")
                call.respond(
                    SyncStatusResponse(
                        deviceId = config.deviceId,
                        deviceName = config.deviceName,
                        appVersion = config.appVersion,
                        hostMode = "desktop",
                        port = config.port,
                        pairPin = config.pairPin,
                        lastSyncAt = lastSyncAt.get(),
                        hasLibrary = DesktopPaths.dbFile(dataDir).exists() ||
                            DesktopPaths.latestSyncZip(dataDir).exists(),
                        bookCount = summary.books.size,
                        noteCount = summary.notes.size,
                        webUrl = "${if (config.tls) "https" else "http"}://127.0.0.1:${config.port}/",
                        lanHint = lan.ifBlank { "connect on this Wi‑Fi using this PC's IP" },
                        tls = config.tls,
                        certSha256 = certSha256,
                    ),
                )
            }
            post("/api/pair") {
                val body = call.receive<SyncPairRequest>()
                if (!SyncAuth.constantTimeEquals(body.pin, config.pairPin)) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        SyncPairResponse(ok = false, message = "Invalid password"),
                    )
                    return@post
                }
                val token = SyncAuth.newSessionToken()
                sessions.add(token)
                call.respond(
                    SyncPairResponse(
                        ok = true,
                        token = token,
                        message = "Paired",
                        certSha256 = certSha256,
                        tls = config.tls,
                    ),
                )
            }
            get("/api/library") {
                if (!authorized(call.request.headers["X-Weaverse-Token"])) {
                    call.respond(HttpStatusCode.Unauthorized, LibrarySummary())
                    return@get
                }
                call.respond(LibraryReader.summarize(DesktopPaths.dbFile(dataDir)))
            }
            get("/api/workspace") {
                if (!authorized(call.request.headers["X-Weaverse-Token"])) {
                    call.respond(HttpStatusCode.Unauthorized, WorkspaceSnapshot())
                    return@get
                }
                call.respond(LibraryReader.workspace(DesktopPaths.dbFile(dataDir)))
            }
            get("/api/scenes/{id}") {
                if (!authorized(call.request.headers["X-Weaverse-Token"])) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("ok" to false))
                    return@get
                }
                val scene = LibraryReader.scene(DesktopPaths.dbFile(dataDir), call.parameters["id"].orEmpty())
                if (scene == null) call.respond(HttpStatusCode.NotFound, mapOf("ok" to false))
                else call.respond(scene)
            }
            put("/api/scenes/{id}") {
                if (!authorized(call.request.headers["X-Weaverse-Token"])) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("ok" to false))
                    return@put
                }
                val id = call.parameters["id"].orEmpty()
                val detail = call.receive<SceneDetail>()
                LibraryReader.upsertScene(
                    DesktopPaths.dbFile(dataDir),
                    id.ifBlank { detail.id },
                    detail.title,
                    detail.summary,
                    detail.body,
                )
                rebuildLatestZip()
                lastSyncAt.set(System.currentTimeMillis())
                call.respond(mapOf("ok" to true))
            }
            get("/api/threads/{id}/messages") {
                if (!authorized(call.request.headers["X-Weaverse-Token"])) {
                    call.respond(HttpStatusCode.Unauthorized, emptyList<Any>())
                    return@get
                }
                call.respond(LibraryReader.threadMessages(DesktopPaths.dbFile(dataDir), call.parameters["id"].orEmpty()))
            }
            get("/api/rp/{id}/messages") {
                if (!authorized(call.request.headers["X-Weaverse-Token"])) {
                    call.respond(HttpStatusCode.Unauthorized, emptyList<Any>())
                    return@get
                }
                call.respond(LibraryReader.rpMessages(DesktopPaths.dbFile(dataDir), call.parameters["id"].orEmpty()))
            }
            get("/api/notes/{id}") {
                if (!authorized(call.request.headers["X-Weaverse-Token"])) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("ok" to false))
                    return@get
                }
                val id = call.parameters["id"].orEmpty()
                val note = LibraryReader.note(DesktopPaths.dbFile(dataDir), id)
                if (note == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("ok" to false))
                } else {
                    call.respond(note)
                }
            }
            put("/api/notes/{id}") {
                if (!authorized(call.request.headers["X-Weaverse-Token"])) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("ok" to false))
                    return@put
                }
                val id = call.parameters["id"].orEmpty()
                val detail = call.receive<NoteDetail>()
                LibraryReader.upsertNote(
                    DesktopPaths.dbFile(dataDir),
                    id.ifBlank { detail.id },
                    detail.title,
                    detail.body,
                )
                rebuildLatestZip()
                lastSyncAt.set(System.currentTimeMillis())
                call.respond(mapOf("ok" to true))
            }
            get("/api/sync/pull") {
                if (!authorized(call.request.headers["X-Weaverse-Token"])) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("ok" to false))
                    return@get
                }
                ensureLatestZip()
                val zip = DesktopPaths.latestSyncZip(dataDir)
                if (!zip.exists()) {
                    call.respond(HttpStatusCode.NotFound, mapOf("ok" to false, "message" to "No library yet"))
                    return@get
                }
                call.respondFile(zip)
            }
            get("/api/media/{id}") {
                val id = call.parameters["id"].orEmpty()
                val file = LibraryReader.resolveMediaFile(dataDir, id)
                if (file == null || !file.exists()) {
                    call.respond(HttpStatusCode.NotFound, mapOf("ok" to false))
                } else {
                    call.respondFile(file)
                }
            }
            post("/api/import") {
                if (!authorized(call.request.headers["X-Weaverse-Token"])) {
                    call.respond(HttpStatusCode.Unauthorized, ImportZipResult(false, "Unauthorized"))
                    return@post
                }
                val incoming = File(dataDir, "incoming/import-${System.currentTimeMillis()}.zip")
                incoming.parentFile?.mkdirs()
                call.receiveChannel().copyTo(incoming.outputStream())
                if (!incoming.exists() || incoming.length() < 32) {
                    call.respond(ImportZipResult(false, "Empty ZIP"))
                    return@post
                }
                val bytes = incoming.readBytes()
                if (!NovelcrafterZipParser.looksLikeNovelcrafterZipBytes(bytes)) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ImportZipResult(
                            false,
                            "ZIP not recognized. Use a Novelcrafter full export (novel.md or novel.docx + characters/…).",
                        ),
                    )
                    return@post
                }
                val result = NovelcrafterSqliteImporter.importZip(
                    bytes,
                    DesktopPaths.dbFile(dataDir),
                    DesktopPaths.mediaDir(dataDir),
                )
                rebuildLatestZip()
                lastSyncAt.set(System.currentTimeMillis())
                call.respond(
                    ImportZipResult(
                        ok = true,
                        message = "Imported “${result.bookTitle}” — ${result.sceneCount} scenes, " +
                            "${result.codexCount} codex, ${result.rpChatCount} RP chats, " +
                            "${result.mediaCount} pictures",
                        bookId = result.bookId,
                        bookTitle = result.bookTitle,
                        sceneCount = result.sceneCount,
                        codexCount = result.codexCount,
                        rpChatCount = result.rpChatCount,
                        mediaCount = result.mediaCount,
                    ),
                )
            }
            post("/api/sync/push") {
                if (!authorized(call.request.headers["X-Weaverse-Token"])) {
                    call.respond(HttpStatusCode.Unauthorized, SyncPushResult(false, "Unauthorized"))
                    return@post
                }
                val incoming = File(dataDir, "incoming/push-${System.currentTimeMillis()}.zip")
                incoming.parentFile?.mkdirs()
                call.receiveChannel().copyTo(incoming.outputStream())
                if (!incoming.exists() || incoming.length() < 32) {
                    call.respond(SyncPushResult(false, "Empty package"))
                    return@post
                }
                val report = mergeIncoming(incoming)
                incoming.copyTo(DesktopPaths.latestSyncZip(dataDir), overwrite = true)
                lastSyncAt.set(System.currentTimeMillis())
                call.respond(
                    SyncPushResult(
                        true,
                        "Merged on desktop host — ${report.summary}",
                        appliedRows = report.appliedRows,
                        deletedRows = report.deletedRows,
                        conflicts = report.conflicts,
                    ),
                )
            }
        }
    }

    private fun mergeIncoming(zip: File): SyncMerge.Report {
        val dbFile = DesktopPaths.dbFile(dataDir)
        snapshotDb(dbFile)
        if (!dbFile.exists() || dbFile.length() == 0L) {
            SyncPackage.restoreInto(zip, dbFile, DesktopPaths.mediaDir(dataDir))
            return SyncMerge.Report()
        }
        Class.forName("org.sqlite.JDBC")
        return DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
            val sql = JdbcSyncSql(conn)
            SyncSchema.ensure(sql)
            SyncPackage.mergeFromZip(zip, sql, DesktopPaths.mediaDir(dataDir))
        }
    }

    private fun snapshotDb(dbFile: File) {
        if (!dbFile.exists()) return
        val dir = DesktopPaths.backupsDir(dataDir)
        val snap = File(dir, "pre-merge-${System.currentTimeMillis()}.db")
        runCatching { dbFile.copyTo(snap, overwrite = true) }
        dir.listFiles()
            ?.filter { it.name.startsWith("pre-merge-") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(7)
            ?.forEach { runCatching { it.delete() } }
    }

    private fun maybeAutoImport() {
        val db = DesktopPaths.dbFile(dataDir)
        if (LibraryReader.summarize(db).books.isNotEmpty()) return
        val zips = DesktopPaths.importDir(dataDir).listFiles()
            ?.filter { it.isFile && it.extension.equals("zip", ignoreCase = true) }
            .orEmpty()
            .sortedBy { it.name.lowercase() }
        val zip = zips.firstOrNull() ?: return
        runCatching {
            NovelcrafterSqliteImporter.importZip(
                zip.readBytes(),
                db,
                DesktopPaths.mediaDir(dataDir),
            )
            rebuildLatestZip()
        }
    }

    private fun authorized(token: String?): Boolean {
        if (token.isNullOrBlank()) return false
        if (sessions.contains(token)) return true
        return SyncAuth.constantTimeEquals(token, config.pairPin)
    }

    private fun ensureLatestZip() {
        val zip = DesktopPaths.latestSyncZip(dataDir)
        val db = DesktopPaths.dbFile(dataDir)
        if (!zip.exists() && db.exists()) {
            rebuildLatestZip()
        }
    }

    private fun rebuildLatestZip() {
        val summary = LibraryReader.summarize(DesktopPaths.dbFile(dataDir))
        SyncPackage.writePackage(
            dbFile = DesktopPaths.dbFile(dataDir),
            mediaDir = DesktopPaths.mediaDir(dataDir),
            outZip = DesktopPaths.latestSyncZip(dataDir),
            deviceId = config.deviceId,
            deviceName = config.deviceName,
            appVersion = config.appVersion,
            bookCount = summary.books.size,
            noteCount = summary.notes.size,
        )
    }

    companion object {
        fun localLanAddresses(): List<String> = buildList {
            runCatching {
                NetworkInterface.getNetworkInterfaces().toList().forEach { nic ->
                    if (!nic.isUp || nic.isLoopback) return@forEach
                    nic.inetAddresses.toList().forEach { addr ->
                        if (!addr.isLoopbackAddress && addr.hostAddress?.contains(':') != true) {
                            add(addr.hostAddress)
                        }
                    }
                }
            }
            if (isEmpty()) {
                runCatching { add(InetAddress.getLocalHost().hostAddress) }
            }
        }
    }
}
