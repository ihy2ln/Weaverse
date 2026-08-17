package com.ihy2ln.weaverse.sync.novelcrafter

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Detects and parses Novelcrafter "full export" ZIPs.
 *
 * Observed layouts:
 * - `novel.md` **or** `novel.docx` — manuscript
 * - `characters|locations|lore|objects|other/<slug-id>/metadata.json` + `entry.md` [+ `notes.md`]
 * - `chats/<date> <title> - <id>.md` — workshop chats (`## User` / `## AI`)
 * - `snippets/<date> <id>.md`
 * - `codex.html` — ignored
 */
object NovelcrafterZipParser {
    private val json = Json { ignoreUnknownKeys = true }
    private val codexFolders = setOf("characters", "locations", "lore", "objects", "other")

    fun looksLikeNovelcrafterZip(entryNames: Collection<String>): Boolean {
        val normalized = entryNames.map { it.replace('\\', '/').trimStart('/') }
        val hasManuscript = normalized.any { isManuscriptName(it) }
        val hasCodexFolder = normalized.any { path ->
            val top = path.substringBefore('/')
            top.lowercase() in codexFolders && path.contains('/')
        }
        val hasWeaverseProject = normalized.any {
            it.equals("project.json", ignoreCase = true) || it.endsWith("/project.json")
        }
        return hasManuscript && hasCodexFolder && !hasWeaverseProject
    }

    fun looksLikeNovelcrafterZipBytes(bytes: ByteArray): Boolean {
        val names = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) names += entry.name
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return looksLikeNovelcrafterZip(names)
    }

    fun parse(bytes: ByteArray): NovelcrafterParsedExport {
        val files = linkedMapOf<String, String>()
        var novelDocx: ByteArray? = null
        val allNames = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val name = entry.name.replace('\\', '/').trimStart('/')
                    allNames += name
                    val lower = name.lowercase()
                    when {
                        lower.endsWith(".md") || lower.endsWith(".json") -> {
                            files[name] = zip.readBytes().toString(Charsets.UTF_8)
                        }
                        isManuscriptName(name) && lower.endsWith(".docx") -> {
                            novelDocx = zip.readBytes()
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        require(looksLikeNovelcrafterZip(allNames)) {
            "ZIP is not a Novelcrafter full export (expected novel.md or novel.docx + codex folders)"
        }
        val novelMd = files.entries.firstOrNull { it.key.substringAfterLast('/').equals("novel.md", ignoreCase = true) }?.value
        val manuscriptSource: String
        val manuscriptText = when {
            !novelMd.isNullOrBlank() -> {
                manuscriptSource = "novel.md"
                novelMd
            }
            novelDocx != null -> {
                manuscriptSource = "novel.docx"
                WordHeadingHeuristics.apply(DocxPlainText.extract(novelDocx))
            }
            else -> {
                manuscriptSource = "empty"
                ""
            }
        }
        val (title, author, acts) = parseNovelMd(manuscriptText)
        return NovelcrafterParsedExport(
            bookTitle = title.ifBlank { "Imported Novelcrafter Book" },
            author = author,
            acts = acts,
            codexEntries = parseCodexEntries(files),
            chats = parseChats(files),
            snippets = parseSnippets(files),
            manuscriptSource = manuscriptSource,
        )
    }

    fun parseNovelMd(text: String): Triple<String, String, List<NcAct>> {
        val lines = text.replace("\r\n", "\n").lines()
        var bookTitle = "Imported Novel"
        var author = ""
        val acts = mutableListOf<MutableAct>()
        var currentAct: MutableAct? = null
        var currentChapter: MutableChapter? = null
        var sceneTitle: String? = null
        val buffer = mutableListOf<String>()

        fun ensureAct(title: String): MutableAct {
            val existing = acts.lastOrNull()
            if (existing != null && existing.title == title) return existing
            return MutableAct(title).also {
                acts += it
                currentAct = it
            }
        }

        fun ensureChapter(title: String): MutableChapter {
            val act = currentAct ?: ensureAct("Act 1")
            val existing = act.chapters.lastOrNull()
            if (existing != null && existing.title == title) return existing
            return MutableChapter(title).also {
                act.chapters += it
                currentChapter = it
            }
        }

        fun flushScene() {
            val chapter = currentChapter ?: return
            val raw = buffer.joinToString("\n").trim()
            buffer.clear()
            if (raw.isBlank() && sceneTitle == null) return
            val (summary, prose) = splitSummaryProse(raw)
            val title = sceneTitle?.trim().orEmpty().ifBlank {
                "Scene ${chapter.scenes.size + 1}"
            }
            chapter.scenes += NcScene(title = title, summary = summary, prose = prose)
            sceneTitle = null
        }

        for (line in lines) {
            when {
                line.startsWith("# ") && !line.startsWith("##") -> {
                    flushScene()
                    bookTitle = line.removePrefix("# ").trim()
                }
                line.startsWith("by ", ignoreCase = true) && currentAct == null && currentChapter == null -> {
                    author = line.removePrefix("by ").removePrefix("By ").trim()
                }
                line.startsWith("## ") -> {
                    flushScene()
                    currentChapter = null
                    ensureAct(line.removePrefix("## ").trim())
                }
                line.startsWith("### ") -> {
                    flushScene()
                    ensureChapter(line.removePrefix("### ").trim())
                }
                line.startsWith("#### ") -> {
                    sceneTitle = line.removePrefix("#### ").trim()
                }
                line.trim() == "* * *" || line.trim() == "***" -> {
                    flushScene()
                }
                else -> buffer += line
            }
        }
        flushScene()

        val cleaned = acts.mapNotNull { act ->
            val chapters = act.chapters.mapNotNull { ch ->
                if (ch.scenes.isEmpty()) null else NcChapter(ch.title, ch.scenes.toList())
            }
            if (chapters.isEmpty()) null else NcAct(act.title, chapters)
        }
        return Triple(bookTitle, author, cleaned)
    }

    private fun isManuscriptName(path: String): Boolean {
        val name = path.replace('\\', '/').substringAfterLast('/')
        return name.equals("novel.md", ignoreCase = true) || name.equals("novel.docx", ignoreCase = true)
    }

    private fun splitSummaryProse(raw: String): Pair<String, String> {
        val normalized = raw.trim()
        if (normalized.isBlank()) return "" to ""
        val parts = normalized.split(Regex("(?m)^---\\s*$"), limit = 2)
        return if (parts.size == 2) {
            parts[0].trim() to parts[1].trim()
        } else {
            "" to normalized
        }
    }

    private fun parseCodexEntries(files: Map<String, String>): List<NcCodexEntry> {
        val byDir = files.keys
            .map { it.replace('\\', '/') }
            .filter { path ->
                val top = path.substringBefore('/').lowercase()
                top in codexFolders && path.count { it == '/' } >= 2
            }
            .groupBy { it.substringBeforeLast('/') }

        return byDir.mapNotNull { (dir, paths) ->
            val folder = dir.substringBefore('/').lowercase()
            val metaPath = paths.firstOrNull { it.endsWith("metadata.json", ignoreCase = true) }
            val entryPath = paths.firstOrNull { it.endsWith("entry.md", ignoreCase = true) }
            val notesPath = paths.firstOrNull { it.endsWith("notes.md", ignoreCase = true) }
            val metaText = metaPath?.let { files[it] }.orEmpty()
            val entryText = entryPath?.let { files[it] }.orEmpty()
            val notesText = notesPath?.let { files[it] }.orEmpty()
            if (metaText.isBlank() && entryText.isBlank()) return@mapNotNull null

            var id = dir.substringAfterLast('/')
            var name = id.substringBeforeLast('-').replace('-', ' ').trim().ifBlank { id }
            var aliases = emptyList<String>()
            var color: String? = null
            var alwaysInclude = false
            if (metaText.isNotBlank()) {
                runCatching {
                    val root = json.parseToJsonElement(metaText).jsonObject
                    id = root["id"]?.jsonPrimitive?.contentOrNull ?: id
                    val attrs = root["attributes"]?.jsonObject
                    name = attrs?.get("name")?.jsonPrimitive?.contentOrNull ?: name
                    color = attrs?.get("color")?.jsonPrimitive?.contentOrNull
                    alwaysInclude = attrs?.get("alwaysIncludeInContext")?.jsonPrimitive?.booleanOrNull ?: false
                    aliases = attrs?.get("aliases")?.jsonArray
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                        ?.filter { it.isNotBlank() }
                        .orEmpty()
                }
            }
            val body = stripFrontMatter(entryText).ifBlank { stripFrontMatter(notesText) }
            val notes = if (entryText.isNotBlank()) stripFrontMatter(notesText) else ""
            NcCodexEntry(
                id = id,
                categoryFolder = folder,
                name = name,
                aliases = aliases,
                color = color,
                alwaysInclude = alwaysInclude,
                body = body,
                notes = notes,
            )
        }.sortedBy { it.name.lowercase() }
    }

    private fun parseChats(files: Map<String, String>): List<NcChat> {
        return files.filter { (path, _) ->
            path.replace('\\', '/').startsWith("chats/", ignoreCase = true) &&
                path.endsWith(".md", ignoreCase = true)
        }.map { (path, text) ->
            val fileName = path.substringAfterLast('/')
            val idFromName = Regex("""([A-Za-z0-9]{6,})\.md$""").find(fileName)?.groupValues?.get(1)
                ?: fileName.removeSuffix(".md")
            val (fm, body) = splitFrontMatter(text)
            val title = fm["title"]?.ifBlank { null } ?: fileName.removeSuffix(".md")
            val favourite = fm["favourite"]?.equals("true", ignoreCase = true) == true
            NcChat(
                id = idFromName,
                title = title,
                favourite = favourite,
                messages = parseChatMessages(body),
            )
        }.sortedBy { it.title.lowercase() }
    }

    private fun parseChatMessages(body: String): List<NcChatMessage> {
        val messages = mutableListOf<NcChatMessage>()
        var role: String? = null
        val buf = StringBuilder()
        fun flush() {
            val r = role ?: return
            val content = buf.toString().trim()
            buf.clear()
            if (content.isBlank()) return
            messages += NcChatMessage(
                role = if (r.equals("AI", ignoreCase = true) || r.equals("assistant", ignoreCase = true)) {
                    "assistant"
                } else {
                    "user"
                },
                content = content,
            )
        }
        for (line in body.replace("\r\n", "\n").lines()) {
            val heading = Regex("^##\\s+(User|AI|Assistant)\\s*$", RegexOption.IGNORE_CASE).matchEntire(line)
            if (heading != null) {
                flush()
                role = heading.groupValues[1]
            } else if (role != null) {
                if (buf.isNotEmpty()) buf.append('\n')
                buf.append(line)
            }
        }
        flush()
        return messages
    }

    private fun parseSnippets(files: Map<String, String>): List<NcSnippet> {
        return files.filter { (path, _) ->
            path.replace('\\', '/').startsWith("snippets/", ignoreCase = true) &&
                path.endsWith(".md", ignoreCase = true)
        }.map { (path, text) ->
            val fileName = path.substringAfterLast('/')
            val idFromName = Regex("""([A-Za-z0-9]{6,})\.md$""").find(fileName)?.groupValues?.get(1)
                ?: fileName.removeSuffix(".md")
            val (fm, body) = splitFrontMatter(text)
            val title = fm["title"]?.ifBlank { null }
                ?: body.lineSequence().firstOrNull { it.isNotBlank() }?.take(64)
                ?: fileName.removeSuffix(".md")
            NcSnippet(id = idFromName, title = title, body = body.trim())
        }
    }

    private fun stripFrontMatter(text: String): String = splitFrontMatter(text).second.trim()

    private fun splitFrontMatter(text: String): Pair<Map<String, String>, String> {
        val normalized = text.replace("\r\n", "\n")
        if (!normalized.startsWith("---\n")) return emptyMap<String, String>() to normalized
        val end = normalized.indexOf("\n---\n", startIndex = 4)
        if (end < 0) return emptyMap<String, String>() to normalized
        val yaml = normalized.substring(4, end)
        val body = normalized.substring(end + 5)
        val map = mutableMapOf<String, String>()
        for (line in yaml.lines()) {
            val idx = line.indexOf(':')
            if (idx <= 0) continue
            val key = line.substring(0, idx).trim()
            var value = line.substring(idx + 1).trim()
            if (value.startsWith('"') && value.endsWith('"') && value.length >= 2) {
                value = value.substring(1, value.length - 1)
            }
            map[key] = value
        }
        return map to body
    }

    private class MutableAct(val title: String) {
        val chapters = mutableListOf<MutableChapter>()
    }

    private class MutableChapter(val title: String) {
        val scenes = mutableListOf<NcScene>()
    }
}
