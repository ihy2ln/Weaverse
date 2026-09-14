package com.ihy2ln.weaverse.desktop

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File

/** Read-only MCP surface for the Windows/web hub (Cursor, Claude Code, OpenCode, Codex CLI). */
class DesktopMcpTools(
    private val dataDir: File,
    private val version: String,
) {
    fun handle(request: JsonObject): JsonObject {
        val method = (request["method"] as? JsonPrimitive)?.content.orEmpty()
        val id = request["id"] ?: JsonNull
        val params = request["params"] as? JsonObject ?: JsonObject(emptyMap())
        return when (method) {
            "initialize" -> result(
                id,
                buildJsonObject {
                    put("protocolVersion", "2024-11-05")
                    putJsonObject("capabilities") { putJsonObject("tools") {} }
                    putJsonObject("serverInfo") {
                        put("name", "Weaverse Desktop")
                        put("version", version)
                    }
                },
            )
            "notifications/initialized", "initialized", "ping" -> result(id, buildJsonObject {})
            "tools/list" -> result(id, buildJsonObject { put("tools", toolsArray()) })
            "tools/call" -> callTool(id, params)
            else -> error(id, -32601, "Unknown method: $method")
        }
    }

    private fun callTool(id: JsonElement, params: JsonObject): JsonObject {
        val name = (params["name"] as? JsonPrimitive)?.content.orEmpty()
        val args = params["arguments"] as? JsonObject ?: JsonObject(emptyMap())
        fun text(value: String, isError: Boolean = false): JsonObject = result(
            id,
            buildJsonObject {
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", value)
                    })
                })
                if (isError) put("isError", true)
            },
        )

        return runCatching {
            val snapshot = LibraryReader.workspace(DesktopPaths.dbFile(dataDir))
            when (name) {
                "list_works" -> text(snapshot.books.joinToString("\n") {
                    "${it.title} · id=${it.id}"
                }.ifBlank { "No works yet." })
                "list_scenes" -> {
                    val bookId = (args["bookId"] as? JsonPrimitive)?.content.orEmpty()
                    val scenes = snapshot.scenes.filter { bookId.isBlank() || it.bookId == bookId }
                    text(scenes.joinToString("\n") {
                        "${it.title} · ${it.wordCount} words · id=${it.id}"
                    }.ifBlank { "No scenes yet." })
                }
                "read_scene" -> {
                    val sceneId = (args["sceneId"] as? JsonPrimitive)?.content.orEmpty()
                    val scene = LibraryReader.scene(DesktopPaths.dbFile(dataDir), sceneId)
                    if (scene == null) text("Scene not found: $sceneId", isError = true)
                    else text("\"${scene.title}\" (${scene.wordCount} words)\n\n${scene.body}")
                }
                "search_codex" -> {
                    val query = (args["query"] as? JsonPrimitive)?.content.orEmpty()
                    text(snapshot.codex.filter {
                        query.isBlank() || it.name.contains(query, ignoreCase = true)
                    }.joinToString("\n") {
                        "${it.name} · id=${it.id}\n${it.bodyPreview}"
                    }.ifBlank { "No codex entries match." })
                }
                "read_codex_entry" -> {
                    val entryId = (args["entryId"] as? JsonPrimitive)?.content.orEmpty()
                    val entry = snapshot.codex.firstOrNull { it.id == entryId }
                    if (entry == null) text("Codex entry not found: $entryId", isError = true)
                    else text("${entry.name}\n\n${entry.bodyPreview}")
                }
                "list_notes" -> text(snapshot.notes.joinToString("\n") {
                    "${it.title} · id=${it.id}"
                }.ifBlank { "No notes yet." })
                "read_note" -> {
                    val noteId = (args["noteId"] as? JsonPrimitive)?.content.orEmpty()
                    val note = LibraryReader.note(DesktopPaths.dbFile(dataDir), noteId)
                    if (note == null) text("Note not found: $noteId", isError = true)
                    else text("${note.title}\n\n${note.body}")
                }
                "find_scene_media" -> {
                    val query = (args["scene"] as? JsonPrimitive)?.content.orEmpty().trim()
                    if (query.isBlank()) text("A scene description is required.", isError = true)
                    else text(snapshot.media.joinToString("\n") {
                        "${it.id} · ${it.caption.ifBlank { it.section }} · ${it.relativePath}"
                    }.ifBlank { "No saved media yet." })
                }
                else -> error(id, -32602, "Unknown tool: $name")
            }
        }.getOrElse { error(id, -32000, "Tool failed: ${it.message ?: it.javaClass.simpleName}") }
    }

    private fun toolsArray(): JsonArray = JsonArray(
        listOf(
            tool("list_works", "List every work in the Weaverse library"),
            tool("list_scenes", "List scenes in the library", mapOf("bookId" to "Optional work id"), emptySet()),
            tool("read_scene", "Read one scene", mapOf("sceneId" to "Scene id")),
            tool("search_codex", "Search codex entries", mapOf("query" to "Name fragment; empty lists everything")),
            tool("read_codex_entry", "Read one codex entry", mapOf("entryId" to "Codex entry id")),
            tool("list_notes", "List notes"),
            tool("read_note", "Read one note", mapOf("noteId" to "Note id")),
            tool("find_scene_media", "List saved media relevant to a scene", mapOf("scene" to "Scene description")),
        ),
    )

    private fun tool(
        name: String,
        description: String,
        properties: Map<String, String> = emptyMap(),
        required: Set<String> = properties.keys,
    ): JsonObject = buildJsonObject {
        put("name", name)
        put("description", description)
        putJsonObject("inputSchema") {
            put("type", "object")
            put("required", buildJsonArray { required.forEach { add(JsonPrimitive(it)) } })
            putJsonObject("properties") {
                properties.forEach { (property, help) ->
                    putJsonObject(property) {
                        put("type", "string")
                        put("description", help)
                    }
                }
            }
        }
    }

    private fun result(id: JsonElement, payload: JsonObject): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        put("result", payload)
    }

    private fun error(id: JsonElement, code: Int, message: String): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        putJsonObject("error") {
            put("code", code)
            put("message", message)
        }
    }
}
