package com.ihy2ln.weaverse.core.mcp

import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.core.manga.MangaDownloadRepository
import com.ihy2ln.weaverse.core.manga.MangaSearchResult
import com.ihy2ln.weaverse.core.media.SceneMediaLibrary
import com.ihy2ln.weaverse.core.media.SceneMediaRequest
import com.ihy2ln.weaverse.core.text.documentFromJson
import com.ihy2ln.weaverse.core.text.plainText
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal MCP (Model Context Protocol) tool server for CLI harnesses such as
 * Cursor (IDE and `agent` CLI), Claude Code, OpenCode, and Codex CLI. Implements
 * the JSON-RPC methods those harnesses speak over streamable HTTP: `initialize`,
 * `tools/list`, `tools/call`, and `ping`. Tools are library views plus confirmed
 * manga download helpers.
 */
@Singleton
class McpTools @Inject constructor(
    private val db: WeaverseDatabase,
    private val sceneMediaLibrary: SceneMediaLibrary,
    private val mangaDownloads: MangaDownloadRepository,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    data class McpCall(val response: JsonObject, val isError: Boolean = false)

    suspend fun handle(request: JsonObject): McpCall {
        val method = (request["method"] as? JsonPrimitive)?.content.orEmpty()
        val id = request["id"]
        val params = request["params"] as? JsonObject ?: JsonObject(emptyMap())
        return when (method) {
            "initialize" -> McpCall(
                result(
                    id,
                    buildJsonObject {
                        put("protocolVersion", "2024-11-05")
                        putJsonObject("capabilities") {
                            putJsonObject("tools") {}
                        }
                        putJsonObject("serverInfo") {
                            put("name", "Weaverse")
                            put("version", com.ihy2ln.weaverse.BuildConfig.VERSION_NAME)
                        }
                    },
                ),
            )
            "notifications/initialized", "initialized" -> McpCall(
                buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("id", id ?: JsonPrimitive(0))
                    put("result", buildJsonObject {})
                },
            )
            "ping" -> McpCall(result(id, buildJsonObject {}))
            "tools/list" -> McpCall(result(id, buildJsonObject { put("tools", toolsArray()) }))
            "tools/call" -> callTool(id, params)
            else -> McpCall(
                error(id, -32601, "Unknown method: $method"),
                isError = true,
            )
        }
    }

    private suspend fun callTool(id: kotlinx.serialization.json.JsonElement?, params: JsonObject): McpCall {
        val name = (params["name"] as? JsonPrimitive)?.content.orEmpty()
        val args = params["arguments"] as? JsonObject ?: JsonObject(emptyMap())
        fun text(value: String, isErr: Boolean = false): McpCall = McpCall(
            result(
                id,
                buildJsonObject {
                    put(
                        "content",
                        buildJsonArray {
                            add(buildJsonObject {
                                put("type", "text")
                                put("text", value)
                            })
                        },
                    )
                    if (isErr) put("isError", true)
                },
            ),
            isError = isErr,
        )
        return try {
            when (name) {
                "list_works" -> {
                    val books = db.bookDao().observeAll().first()
                    text(books.joinToString("\n") {
                        "${it.title} · ${it.workType} · id=${it.id}"
                    }.ifBlank { "No works yet." })
                }
                "list_scenes" -> {
                    val bookId = (args["bookId"] as? JsonPrimitive)?.content.orEmpty()
                    val books = db.bookDao().observeAll().first()
                    val target = books.find { it.id == bookId } ?: books.firstOrNull()
                        ?: return text("No works yet.")
                    val lines = StringBuilder()
                    val acts = db.manuscriptDao().observeActs(target.id).first()
                    acts.forEach { act ->
                        db.manuscriptDao().observeChapters(act.id).first().forEach { chapter ->
                            db.manuscriptDao().observeScenes(chapter.id).first().forEach { scene ->
                                lines.append("${scene.title} · ${scene.wordCount} words · id=${scene.id}\n")
                            }
                        }
                    }
                    text("Scenes in \"${target.title}\":\n${lines.toString().ifBlank { "No scenes yet." }}")
                }
                "read_scene" -> {
                    val sceneId = (args["sceneId"] as? JsonPrimitive)?.content.orEmpty()
                    val scene = db.manuscriptDao().getScene(sceneId)
                        ?: return text("Scene not found: $sceneId")
                    text("\"${scene.title}\" (${scene.wordCount} words)\n\n${scene.plainText}")
                }
                "search_codex" -> {
                    val query = (args["query"] as? JsonPrimitive)?.content.orEmpty()
                    val entries = db.codexDao().getAllEntries()
                        .filter { !it.disabled && (query.isBlank() || it.name.contains(query, true)) }
                    text(entries.joinToString("\n") {
                        "${it.name} · id=${it.id}\n${it.plainText.take(400)}"
                    }.ifBlank { "No codex entries match." })
                }
                "read_codex_entry" -> {
                    val entryId = (args["entryId"] as? JsonPrimitive)?.content.orEmpty()
                    val entry = db.codexDao().getAllEntries().find { it.id == entryId }
                        ?: return text("Codex entry not found: $entryId")
                    text("${entry.name}\n\n${entry.plainText}")
                }
                "list_notes" -> {
                    val notes = db.snippetDao().getByCategory("notes")
                    text(notes.joinToString("\n") {
                        "${it.title} · id=${it.id}"
                    }.ifBlank { "No notes yet." })
                }
                "read_note" -> {
                    val noteId = (args["noteId"] as? JsonPrimitive)?.content.orEmpty()
                    val note = db.snippetDao().getByCategory("notes").find { it.id == noteId }
                        ?: return text("Note not found: $noteId")
                    text("${note.title}\n\n${documentFromJson(note.body).plainText()}")
                }
                "find_scene_media" -> {
                    val scene = (args["scene"] as? JsonPrimitive)?.content.orEmpty().trim()
                    if (scene.isBlank()) return text("A scene description or scene type is required.", isErr = true)
                    val kind = (args["kind"] as? JsonPrimitive)?.content.orEmpty().ifBlank { "any" }
                    val tags = (args["tags"] as? JsonPrimitive)?.content.orEmpty()
                        .split(',')
                        .map(String::trim)
                        .filter(String::isNotBlank)
                    val limit = (args["limit"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 12
                    text(sceneMediaLibrary.promptContext(SceneMediaRequest(scene, kind, tags, limit)))
                }
                "search_manga" -> {
                    val query = (args["query"] as? JsonPrimitive)?.content.orEmpty().trim()
                    if (query.isBlank()) return text("A manga title is required.", isErr = true)
                    val source = (args["sourceId"] as? JsonPrimitive)?.content.orEmpty().ifBlank { "mangadex" }
                    val results = mangaDownloads.search(source, query)
                    text(results.joinToString("\n") {
                        "${it.title} · id=${it.remoteId} · source=${it.sourceId}\n${it.canonicalUrl}"
                    }.ifBlank { "No manga matches." })
                }
                "list_manga_chapters" -> {
                    val mangaId = (args["mangaId"] as? JsonPrimitive)?.content.orEmpty()
                    val title = (args["title"] as? JsonPrimitive)?.content.orEmpty().ifBlank { "Manga" }
                    if (mangaId.isBlank()) return text("mangaId is required.", isErr = true)
                    val source = (args["sourceId"] as? JsonPrimitive)?.content.orEmpty().ifBlank { "mangadex" }
                    val manga = MangaSearchResult(source, mangaId, title)
                    val chapters = mangaDownloads.loadChapters(manga)
                    text(chapters.joinToString("\n") {
                        "${it.title} · remoteId=${it.remoteId} · ${it.language} · ${it.canonicalUrl}"
                    }.ifBlank { "No chapters found." })
                }
                "queue_manga_download" -> {
                    if (!confirmed(args)) return text("This downloads remote pages into the local library. Repeat with confirm=true after the user approves it.", isErr = true)
                    val source = (args["sourceId"] as? JsonPrimitive)?.content.orEmpty().ifBlank { "mangadex" }
                    val remoteId = (args["chapterId"] as? JsonPrimitive)?.content.orEmpty()
                    val mangaId = (args["mangaId"] as? JsonPrimitive)?.content.orEmpty()
                    val title = (args["title"] as? JsonPrimitive)?.content.orEmpty().ifBlank { "Chapter $remoteId" }
                    if (remoteId.isBlank() || mangaId.isBlank()) return text("chapterId and mangaId are required.", isErr = true)
                    val entity = mangaDownloads.addDiscoveredChapter(
                        com.ihy2ln.weaverse.core.manga.MangaChapter(
                            sourceId = source,
                            remoteId = remoteId,
                            mangaId = mangaId,
                            mangaTitle = (args["mangaTitle"] as? JsonPrimitive)?.content.orEmpty().ifBlank { "Manga" },
                            title = title,
                            chapterNumber = (args["chapterNumber"] as? JsonPrimitive)?.content.orEmpty(),
                            canonicalUrl = (args["url"] as? JsonPrimitive)?.content.orEmpty(),
                        ),
                    )
                    mangaDownloads.enqueue(entity)
                    text("Queued ${entity.mangaTitle} · ${entity.title} · chapterId=${entity.id}")
                }
                "download_manga_web_link" -> {
                    if (!confirmed(args)) return text("This downloads remote pages into the local library. Repeat with confirm=true after the user approves it.", isErr = true)
                    val url = (args["url"] as? JsonPrimitive)?.content.orEmpty().trim()
                    if (url.isBlank()) return text("url is required.", isErr = true)
                    val title = (args["title"] as? JsonPrimitive)?.content.orEmpty()
                    val entity = mangaDownloads.enqueueWebLink(url, title)
                    text("Queued ${entity.mangaTitle} · ${entity.title} · chapterId=${entity.id} · ${entity.pageCount} page(s)")
                }
                "manga_download_status" -> {
                    val chapters = mangaDownloads.observeChapters().first()
                    val id = (args["chapterId"] as? JsonPrimitive)?.content.orEmpty()
                    text(chapters.filter { id.isBlank() || it.id == id }.joinToString("\n") {
                        "${it.mangaTitle} · ${it.title} · ${it.status} · ${it.progress}% · id=${it.id}"
                    }.ifBlank { "No local manga downloads." })
                }
                "import_manga_chapter_to_storyboard" -> {
                    if (!confirmed(args)) return text("This creates Storyboard pages. Repeat with confirm=true after the user approves it.", isErr = true)
                    val chapterId = (args["chapterId"] as? JsonPrimitive)?.content.orEmpty()
                    val chatId = (args["chatId"] as? JsonPrimitive)?.content.orEmpty()
                    if (chapterId.isBlank() || chatId.isBlank()) return text("chapterId and chatId are required.", isErr = true)
                    val count = mangaDownloads.importChapterToStoryboard(chatId, chapterId)
                    text("Added $count original page(s) to Storyboard chat $chatId. Pages remain editable and can be translated or colored in the app.")
                }
                else -> McpCall(error(id, -32602, "Unknown tool: $name"), isError = true)
            }
        } catch (err: Throwable) {
            text("Tool failed: ${err.message ?: err.javaClass.simpleName}", isErr = true)
        }
    }

    private fun toolsArray(): JsonArray = JsonArray(
        listOf(
            tool("list_works", "List every novel, campaign and storyboard in the library"),
            tool(
                "list_scenes",
                "List the scenes of a work (manuscript tree flattened)",
                mapOf("bookId" to "Work id from list_works; optional — defaults to the first work"),
            ),
            tool(
                "read_scene",
                "Read a scene's full plain text",
                mapOf("sceneId" to "Scene id from list_scenes"),
            ),
            tool(
                "search_codex",
                "Search the shared codex by name",
                mapOf("query" to "Name fragment; empty lists everything"),
            ),
            tool(
                "read_codex_entry",
                "Read one codex entry's full text",
                mapOf("entryId" to "Entry id from search_codex"),
            ),
            tool("list_notes", "List the writer's notes"),
            tool(
                "read_note",
                "Read one note's full text",
                mapOf("noteId" to "Note id from list_notes"),
            ),
            tool(
                "find_scene_media",
                "Find categorized local pictures or videos that the AI may use for a scene. Returns stable media IDs ranked by scene category and tags.",
                mapOf(
                    "scene" to "Scene type or description, for example dungeon battle, farm, town, house, shrine or merchant",
                    "kind" to "Optional: image, video, or any",
                    "tags" to "Optional comma-separated hints such as boss, cave, safe-camp or night",
                    "limit" to "Optional maximum results from 1 to 50; defaults to 12",
                ),
                required = setOf("scene"),
            ),
            tool("search_manga", "Search an installed manga source catalog", mapOf("query" to "Manga title", "sourceId" to "Optional source id such as mangadex, rawkuma, comix, atsumaru, mangafire, or mangadot; defaults to mangadex"), required = setOf("query")),
            tool("list_manga_chapters", "List chapters for a manga returned by search_manga", mapOf("mangaId" to "Remote manga id", "title" to "Manga title", "sourceId" to "Optional source id; defaults to mangadex"), required = setOf("mangaId")),
            tool("queue_manga_download", "Queue a catalog chapter for local download; requires confirm=true", mapOf("chapterId" to "Remote chapter id", "mangaId" to "Remote manga id", "mangaTitle" to "Manga title", "title" to "Chapter title", "sourceId" to "Optional source id", "url" to "Optional public chapter URL", "confirm" to "Must be true after explicit user approval"), required = setOf("chapterId", "mangaId", "confirm")),
            tool("download_manga_web_link", "Queue a public chapter-reader URL the same way Browse → Download from web link does; requires confirm=true", mapOf("url" to "Public chapter URL", "title" to "Optional title override", "confirm" to "Must be true after explicit user approval"), required = setOf("url", "confirm")),
            tool("manga_download_status", "Read local manga download progress", mapOf("chapterId" to "Optional local chapter id"), required = emptySet()),
            tool("import_manga_chapter_to_storyboard", "Create editable Storyboard pages from a completed local chapter; requires confirm=true", mapOf("chapterId" to "Local chapter id", "chatId" to "Storyboard chat id", "confirm" to "Must be true after explicit user approval"), required = setOf("chapterId", "chatId", "confirm")),
        ),
    )

    private fun confirmed(args: JsonObject): Boolean =
        (args["confirm"] as? JsonPrimitive)?.content?.equals("true", ignoreCase = true) == true

    private fun tool(
        name: String,
        description: String,
        properties: Map<String, String> = emptyMap(),
        required: Set<String> = properties.keys,
    ): JsonObject =
        buildJsonObject {
            put("name", name)
            put("description", description)
            if (properties.isNotEmpty()) {
                putJsonObject("inputSchema") {
                    put("type", "object")
                    put(
                        "required",
                        buildJsonArray { required.forEach { add(JsonPrimitive(it)) } },
                    )
                    putJsonObject("properties") {
                        properties.forEach { (propName, propDescription) ->
                            putJsonObject(propName) {
                                put("type", "string")
                                put("description", propDescription)
                            }
                        }
                    }
                }
            }
        }

    private fun result(id: kotlinx.serialization.json.JsonElement?, payload: JsonObject): JsonObject =
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id ?: JsonPrimitive(0))
            put("result", payload)
        }

    private fun error(id: kotlinx.serialization.json.JsonElement?, code: Int, message: String): JsonObject =
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id ?: JsonPrimitive(0))
            putJsonObject("error") {
                put("code", code)
                put("message", message)
            }
        }
}
