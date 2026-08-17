package com.ihy2ln.weaverse.sync.novelcrafter

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

/**
 * Writes a parsed Novelcrafter export into the desktop/web SQLite library
 * (same table names Android Room uses so the hub and a later Pull can read it).
 */
object NovelcrafterSqliteImporter {
    init {
        Class.forName("org.sqlite.JDBC")
    }

    fun importZip(
        bytes: ByteArray,
        dbFile: File,
        mediaDir: File,
    ): NovelcrafterImportCounts {
        val parsed = NovelcrafterZipParser.parse(bytes)
        return importParsed(parsed, dbFile, mediaDir)
    }

    fun importParsed(
        parsed: NovelcrafterParsedExport,
        dbFile: File,
        mediaDir: File,
    ): NovelcrafterImportCounts {
        dbFile.parentFile?.mkdirs()
        mediaDir.mkdirs()
        val now = System.currentTimeMillis()
        val key = UUID.randomUUID().toString().take(8)
        val seriesId = "nc-series-$key"
        val bookId = "nc-book-$key"

        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
            conn.autoCommit = false
            ensureSchema(conn)
            insertSeriesBook(conn, seriesId, bookId, parsed, now)
            val categoryIds = insertCategories(conn, bookId, key, now)
            val counts = insertManuscript(conn, bookId, key, parsed, now)
            insertCodex(conn, bookId, key, parsed, categoryIds, now)
            insertWorkshop(conn, bookId, key, parsed, now)
            insertSnippets(conn, bookId, key, parsed, now)
            val mediaIds = insertBundledArt(conn, mediaDir, now)
            attachArt(conn, bookId, parsed, mediaIds, now)
            val rp = insertRoleplay(conn, key, parsed, mediaIds, now)
            conn.commit()
            return NovelcrafterImportCounts(
                bookId = bookId,
                bookTitle = parsed.bookTitle,
                actCount = counts.first,
                chapterCount = counts.second,
                sceneCount = counts.third,
                codexCount = parsed.codexEntries.size,
                chatCount = parsed.chats.size,
                snippetCount = parsed.snippets.size,
                rpCharacterCount = rp.first,
                rpChatCount = rp.second,
                mediaCount = mediaIds.size,
            )
        }
    }

    private fun insertSeriesBook(
        conn: Connection,
        seriesId: String,
        bookId: String,
        parsed: NovelcrafterParsedExport,
        now: Long,
    ) {
        conn.prepareStatement(
            """
            INSERT INTO series(id, title, description, premise, rollingSummary, summaryUpdatedAt, createdAt)
            VALUES(?, ?, ?, '', '', NULL, ?)
            """.trimIndent(),
        ).use { ps ->
            ps.setString(1, seriesId)
            ps.setString(2, parsed.bookTitle)
            ps.setString(
                3,
                if (parsed.author.isNotBlank()) {
                    "Imported from Novelcrafter · by ${parsed.author}"
                } else {
                    "Imported from Novelcrafter (${parsed.manuscriptSource})"
                },
            )
            ps.setLong(4, now)
            ps.executeUpdate()
        }
        conn.prepareStatement(
            """
            INSERT INTO books(id, seriesId, title, genre, pov, tense, styleGuide, targetWordCount,
                coverMediaId, createdAt, updatedAt)
            VALUES(?, ?, ?, 'Isekai', '', '', ?, 0, NULL, ?, ?)
            """.trimIndent(),
        ).use { ps ->
            ps.setString(1, bookId)
            ps.setString(2, seriesId)
            ps.setString(3, parsed.bookTitle)
            ps.setString(4, if (parsed.author.isNotBlank()) "Author: ${parsed.author}" else "")
            ps.setLong(5, now)
            ps.setLong(6, now)
            ps.executeUpdate()
        }
    }

    private fun insertCategories(
        conn: Connection,
        bookId: String,
        key: String,
        now: Long,
    ): Map<String, String> {
        val ids = linkedMapOf<String, String>()
        NovelcrafterCategories.folderToCategory.values.distinctBy { it.first }.forEach { (name, colorIndex) ->
            val catId = "nc-cat-$key-$colorIndex"
            ids[name] = catId
            conn.prepareStatement(
                """
                INSERT INTO codex_categories(id, scopeType, scopeId, name, colorHex, icon, glyph,
                    sortOrder, isSystem, isBuiltIn)
                VALUES(?, 'book', ?, ?, ?, '', '', ?, 1, 1)
                """.trimIndent(),
            ).use { ps ->
                ps.setString(1, catId)
                ps.setString(2, bookId)
                ps.setString(3, name)
                ps.setString(4, NovelcrafterCategories.palette[colorIndex % NovelcrafterCategories.palette.size])
                ps.setInt(5, colorIndex)
                ps.executeUpdate()
            }
        }
        return ids
    }

    private fun insertManuscript(
        conn: Connection,
        bookId: String,
        key: String,
        parsed: NovelcrafterParsedExport,
        now: Long,
    ): Triple<Int, Int, Int> {
        val acts = parsed.acts.ifEmpty {
            listOf(NcAct("Act 1", listOf(NcChapter("Chapter 1", listOf(NcScene("Scene 1"))))))
        }
        var actCount = 0
        var chapterCount = 0
        var sceneCount = 0
        acts.forEachIndexed { actIndex, act ->
            val actId = "nc-act-$key-$actIndex"
            conn.prepareStatement("INSERT INTO acts(id, bookId, title, sortOrder) VALUES(?,?,?,?)").use { ps ->
                ps.setString(1, actId)
                ps.setString(2, bookId)
                ps.setString(3, act.title)
                ps.setInt(4, actIndex)
                ps.executeUpdate()
            }
            actCount++
            act.chapters.forEachIndexed { chIndex, chapter ->
                val chapterId = "nc-ch-$key-$actIndex-$chIndex"
                conn.prepareStatement(
                    "INSERT INTO chapters(id, actId, title, sortOrder, summary) VALUES(?,?,?,?,?)",
                ).use { ps ->
                    ps.setString(1, chapterId)
                    ps.setString(2, actId)
                    ps.setString(3, chapter.title)
                    ps.setInt(4, chIndex)
                    ps.setString(5, "")
                    ps.executeUpdate()
                }
                chapterCount++
                chapter.scenes.forEachIndexed { scIndex, scene ->
                    val sceneId = "nc-sc-$key-$actIndex-$chIndex-$scIndex"
                    val prose = scene.prose.ifBlank { scene.summary }
                    val doc = PlainDocumentJson.fromPlainText(prose)
                    val words = prose.split(Regex("\\s+")).count { it.isNotBlank() }
                    conn.prepareStatement(
                        """
                        INSERT INTO scenes(id, chapterId, title, sortOrder, docJson, plainText, summary,
                            beatsJson, wordCount, status, pov, povCharacterId, inWorldDate, labelsJson,
                            colorHex, createdAt, updatedAt)
                        VALUES(?,?,?,?,?,?,?,'[]',?,'draft','',NULL,'','[]',NULL,?,?)
                        """.trimIndent(),
                    ).use { ps ->
                        ps.setString(1, sceneId)
                        ps.setString(2, chapterId)
                        ps.setString(3, scene.title)
                        ps.setInt(4, scIndex)
                        ps.setString(5, doc)
                        ps.setString(6, prose)
                        ps.setString(7, scene.summary)
                        ps.setInt(8, words)
                        ps.setLong(9, now)
                        ps.setLong(10, now)
                        ps.executeUpdate()
                    }
                    sceneCount++
                }
            }
        }
        return Triple(actCount, chapterCount, sceneCount)
    }

    private fun insertCodex(
        conn: Connection,
        bookId: String,
        key: String,
        parsed: NovelcrafterParsedExport,
        categoryIds: Map<String, String>,
        now: Long,
    ) {
        parsed.codexEntries.forEach { entry ->
            val (catName, colorIndex) = NovelcrafterCategories.folderToCategory[entry.categoryFolder.lowercase()]
                ?: ("Notes" to 9)
            val categoryId = categoryIds[catName] ?: categoryIds.values.first()
            val bodyText = buildString {
                append(entry.body.trim())
                if (entry.notes.isNotBlank()) {
                    if (isNotEmpty()) append("\n\n")
                    append("## Notes\n\n")
                    append(entry.notes.trim())
                }
            }.ifBlank { entry.name }
            val colorHex = entry.color?.let { NovelcrafterCategories.namedColorToHex(it) }
                ?: NovelcrafterCategories.palette[colorIndex % NovelcrafterCategories.palette.size]
            val aliasesJson = entry.aliases.joinToString(prefix = "[", postfix = "]") { "\"${it.replace("\"", "'")}\"" }
            conn.prepareStatement(
                """
                INSERT INTO codex_entries(id, categoryId, scopeType, scopeId, name, aliasesJson, docJson,
                    plainText, colorHex, alwaysInclude, disabled, imageMediaId, isAiGenerated, createdAt, updatedAt)
                VALUES(?, ?, 'book', ?, ?, ?, ?, ?, ?, ?, 0, NULL, 0, ?, ?)
                """.trimIndent(),
            ).use { ps ->
                ps.setString(1, "nc-entry-$key-${entry.id}")
                ps.setString(2, categoryId)
                ps.setString(3, bookId)
                ps.setString(4, entry.name)
                ps.setString(5, aliasesJson)
                ps.setString(6, PlainDocumentJson.fromPlainText(bodyText))
                ps.setString(7, bodyText)
                ps.setString(8, colorHex)
                ps.setInt(9, if (entry.alwaysInclude) 1 else 0)
                ps.setLong(10, now)
                ps.setLong(11, now)
                ps.executeUpdate()
            }
        }
    }

    private fun insertWorkshop(
        conn: Connection,
        bookId: String,
        key: String,
        parsed: NovelcrafterParsedExport,
        now: Long,
    ) {
        parsed.chats.forEach { chat ->
            val threadId = "nc-chat-$key-${chat.id}"
            conn.prepareStatement(
                """
                INSERT INTO chat_threads(id, scopeId, name, pinned, promptId, modelRef, sceneId, createdAt, updatedAt)
                VALUES(?, ?, ?, ?, NULL, '', NULL, ?, ?)
                """.trimIndent(),
            ).use { ps ->
                ps.setString(1, threadId)
                ps.setString(2, bookId)
                ps.setString(3, chat.title)
                ps.setInt(4, if (chat.favourite) 1 else 0)
                ps.setLong(5, now)
                ps.setLong(6, now)
                ps.executeUpdate()
            }
            chat.messages.forEachIndexed { index, msg ->
                conn.prepareStatement(
                    """
                    INSERT INTO chat_messages(id, threadId, role, contentJson, contextUsedJson, tokenCount, wordCount, createdAt)
                    VALUES(?, ?, ?, ?, '[]', 0, ?, ?)
                    """.trimIndent(),
                ).use { ps ->
                    val words = msg.content.split(Regex("\\s+")).count { it.isNotBlank() }
                    ps.setString(1, "$threadId-m$index")
                    ps.setString(2, threadId)
                    ps.setString(3, msg.role)
                    ps.setString(4, PlainDocumentJson.fromPlainText(msg.content))
                    ps.setInt(5, words)
                    ps.setLong(6, now + index)
                    ps.executeUpdate()
                }
            }
        }
    }

    private fun insertSnippets(
        conn: Connection,
        bookId: String,
        key: String,
        parsed: NovelcrafterParsedExport,
        now: Long,
    ) {
        parsed.snippets.forEach { snip ->
            conn.prepareStatement(
                """
                INSERT INTO snippets(id, scopeType, scopeId, title, body, category, pinned, createdAt)
                VALUES(?, 'book', ?, ?, ?, 'novelcrafter', 0, ?)
                """.trimIndent(),
            ).use { ps ->
                ps.setString(1, "nc-snip-$key-${snip.id}")
                ps.setString(2, bookId)
                ps.setString(3, snip.title)
                ps.setString(4, snip.body)
                ps.setLong(5, now)
                ps.executeUpdate()
            }
        }
    }

    private fun insertBundledArt(conn: Connection, mediaDir: File, now: Long): Map<String, String> {
        val ids = linkedMapOf<String, String>()
        ImportArt.pieces.forEach { piece ->
            val bytes = ImportArt.loadBytes(piece.fileName) ?: return@forEach
            val ext = piece.fileName.substringAfterLast('.', "jpg")
            val relative = "media/${piece.id}.$ext"
            val file = File(mediaDir, "${piece.id}.$ext")
            file.writeBytes(bytes)
            conn.prepareStatement(
                """
                INSERT OR REPLACE INTO media(id, type, relativePath, mimeType, byteSize, width, height,
                    durationMs, thumbnailPath, checksum, createdAt)
                VALUES(?, 'image', ?, ?, ?, 0, 0, NULL, ?, '', ?)
                """.trimIndent(),
            ).use { ps ->
                ps.setString(1, piece.id)
                ps.setString(2, relative)
                ps.setString(3, ImportArt.mimeFor(piece.fileName))
                ps.setLong(4, bytes.size.toLong())
                ps.setString(5, relative)
                ps.setLong(6, now)
                ps.executeUpdate()
            }
            ids[piece.attach] = piece.id
            ids[piece.id] = piece.id
        }
        return ids
    }

    private fun attachArt(
        conn: Connection,
        bookId: String,
        parsed: NovelcrafterParsedExport,
        mediaIds: Map<String, String>,
        now: Long,
    ) {
        mediaIds["cover"]?.let { coverId ->
            conn.prepareStatement("UPDATE books SET coverMediaId = ?, updatedAt = ? WHERE id = ?").use { ps ->
                ps.setString(1, coverId)
                ps.setLong(2, now)
                ps.setString(3, bookId)
                ps.executeUpdate()
            }
        }
        mediaIds["first-scene"]?.let { mediaId ->
            val piece = ImportArt.pieces.first { it.attach == "first-scene" }
            conn.createStatement().use { st ->
                st.executeQuery(
                    """
                    SELECT s.id, s.plainText FROM scenes s
                    JOIN chapters c ON c.id = s.chapterId
                    JOIN acts a ON a.id = c.actId
                    WHERE a.bookId = '$bookId'
                    ORDER BY a.sortOrder, c.sortOrder, s.sortOrder
                    LIMIT 1
                    """.trimIndent(),
                ).use { rs ->
                    if (rs.next()) {
                        val sceneId = rs.getString("id")
                        val prose = rs.getString("plainText").orEmpty()
                        conn.prepareStatement(
                            "UPDATE scenes SET docJson = ?, updatedAt = ? WHERE id = ?",
                        ).use { ps ->
                            ps.setString(1, PlainDocumentJson.withLeadingImage(mediaId, piece.caption, prose))
                            ps.setLong(2, now)
                            ps.setString(3, sceneId)
                            ps.executeUpdate()
                        }
                    }
                }
            }
        }
        fun attachToCodex(attachKey: String, vararg names: String) {
            val mediaId = mediaIds[attachKey] ?: return
            val entry = NovelcrafterCategories.findEntry(parsed.codexEntries, *names) ?: return
            conn.prepareStatement(
                "UPDATE codex_entries SET imageMediaId = ? WHERE name = ? AND scopeId = ?",
            ).use { ps ->
                ps.setString(1, mediaId)
                ps.setString(2, entry.name)
                ps.setString(3, bookId)
                ps.executeUpdate()
            }
        }
        attachToCodex("location", "Adams Haven", "Elysium Vale")
        attachToCodex("object", "Celestium", "Life Technology")
    }

    private fun insertRoleplay(
        conn: Connection,
        key: String,
        parsed: NovelcrafterParsedExport,
        mediaIds: Map<String, String>,
        now: Long,
    ): Pair<Int, Int> {
        val personaId = "nc-persona-$key"
        conn.prepareStatement(
            "INSERT INTO rp_personas(id, name, avatarMediaId, description, isDefault) VALUES(?,?,NULL,?,1)",
        ).use { ps ->
            ps.setString(1, personaId)
            ps.setString(2, "JD")
            ps.setString(3, "Writer persona for Isekai Gacha — John / JD.")
            ps.executeUpdate()
        }
        val characters = parsed.codexEntries.filter { it.categoryFolder.equals("characters", ignoreCase = true) }
        characters.forEach { entry ->
            val first = entry.body.lineSequence().firstOrNull { it.isNotBlank() }?.take(240).orEmpty()
            conn.prepareStatement(
                """
                INSERT INTO rp_characters(id, name, avatarMediaId, description, personality, scenario,
                    firstMes, mesExample, creatorNotes, systemPrompt, postHistoryInstructions,
                    alternateGreetingsJson, tagsJson, characterVersion, extensionsJson, defaultCodexId,
                    colorHex, createdAt)
                VALUES(?, ?, NULL, ?, '', ?, ?, '', ?, ?, '', '[]', '["isekai-gacha"]', '2.0', '{}', NULL, ?, ?)
                """.trimIndent(),
            ).use { ps ->
                ps.setString(1, "nc-rp-$key-${entry.id}")
                ps.setString(2, entry.name)
                ps.setString(3, entry.body.take(2000))
                ps.setString(4, "Adams Haven / Elysium Vale")
                ps.setString(5, first.ifBlank { "You meet ${entry.name}." })
                ps.setString(6, "Imported from Novelcrafter characters/")
                ps.setString(7, "You are ${entry.name}. Stay in character.")
                ps.setString(8, entry.color?.let { NovelcrafterCategories.namedColorToHex(it) })
                ps.setLong(9, now)
                ps.executeUpdate()
            }
        }

        fun charId(vararg names: String): String? {
            val entry = NovelcrafterCategories.findEntry(characters, *names) ?: return null
            return "nc-rp-$key-${entry.id}"
        }

        val iisId = charId("Isekai Incubus System", "IIS")
        val amaraId = charId("Amara")
        val elowenId = charId("Elowen")
        val templeBg = mediaIds["rp-background"]
        val farmBg = mediaIds["location"]
        val mangaArt = mediaIds["manga-panel"]

        data class Seed(
            val id: String,
            val title: String,
            val mode: String,
            val characterId: String?,
            val backgroundId: String?,
            val role: String,
            val text: String,
            val mediaId: String? = null,
            val caption: String = "",
        )

        val seeds = listOf(
            Seed(
                id = "nc-rpchat-$key-messenger",
                title = "IIS — wristband",
                mode = "messenger",
                characterId = iisId,
                backgroundId = null,
                role = "char",
                text = "You have successfully transferred to Adams Haven. Congratulations. I am your Isekai Incubus System. Tutorial begins now.",
            ),
            Seed(
                id = "nc-rpchat-$key-dm",
                title = "Adams Haven — forest path",
                mode = "dungeonMaster",
                characterId = elowenId ?: iisId,
                backgroundId = farmBg ?: templeBg,
                role = "char",
                text = "Sun cuts through the canopy. A dirt path crosses a clear stream on stepping stones. Somewhere ahead, stone ruins wait.",
            ),
            Seed(
                id = "nc-rpchat-$key-manga",
                title = "Pulled through the void",
                mode = "roleplay",
                characterId = amaraId ?: iisId,
                backgroundId = templeBg,
                role = "char",
                text = "White. Then the dark between worlds. Someone — or something — is pulling you through.",
                mediaId = mangaArt,
                caption = "Pulled through the void",
            ),
        )
        seeds.forEach { seed ->
            conn.prepareStatement(
                """
                INSERT INTO rp_chats(id, characterId, groupId, personaId, title, backgroundMediaId,
                    authorsNote, authorsNoteDepth, presetId, promptTemplateId, branchOfChatId,
                    displayMode, narrationColorHex, speechColorHex, oocColorHex, createdAt, updatedAt)
                VALUES(?, ?, NULL, ?, ?, ?, '', 4, NULL, NULL, NULL, ?, NULL, NULL, NULL, ?, ?)
                """.trimIndent(),
            ).use { ps ->
                ps.setString(1, seed.id)
                ps.setString(2, seed.characterId)
                ps.setString(3, personaId)
                ps.setString(4, seed.title)
                ps.setString(5, seed.backgroundId)
                ps.setString(6, seed.mode)
                ps.setLong(7, now)
                ps.setLong(8, now)
                ps.executeUpdate()
            }
            val content = if (seed.mediaId != null) {
                PlainDocumentJson.mediaOnly(seed.mediaId, seed.caption, seed.text)
            } else {
                PlainDocumentJson.fromPlainText(seed.text)
            }
            conn.prepareStatement(
                """
                INSERT INTO rp_messages(id, chatId, swipeGroupId, swipeIndex, isActiveSwipe, role,
                    speakerCharacterId, contentJson, tokenCount, isEdited, createdAt, displayMode)
                VALUES(?, ?, ?, 0, 1, ?, ?, ?, 0, 0, ?, ?)
                """.trimIndent(),
            ).use { ps ->
                ps.setString(1, "${seed.id}-m0")
                ps.setString(2, seed.id)
                ps.setString(3, "${seed.id}-swipe")
                ps.setString(4, seed.role)
                ps.setString(5, seed.characterId)
                ps.setString(6, content)
                ps.setLong(7, now)
                ps.setString(8, seed.mode)
                ps.executeUpdate()
            }
        }
        return characters.size to seeds.size
    }

    private fun ensureSchema(conn: Connection) {
        conn.createStatement().use { st ->
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS series (
                    id TEXT NOT NULL PRIMARY KEY,
                    title TEXT NOT NULL,
                    description TEXT NOT NULL DEFAULT '',
                    premise TEXT NOT NULL DEFAULT '',
                    rollingSummary TEXT NOT NULL DEFAULT '',
                    summaryUpdatedAt INTEGER,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS books (
                    id TEXT NOT NULL PRIMARY KEY,
                    seriesId TEXT,
                    title TEXT NOT NULL,
                    genre TEXT NOT NULL DEFAULT '',
                    pov TEXT NOT NULL DEFAULT '',
                    tense TEXT NOT NULL DEFAULT '',
                    styleGuide TEXT NOT NULL DEFAULT '',
                    targetWordCount INTEGER NOT NULL DEFAULT 0,
                    coverMediaId TEXT,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS acts (
                    id TEXT NOT NULL PRIMARY KEY,
                    bookId TEXT NOT NULL,
                    title TEXT NOT NULL,
                    sortOrder INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS chapters (
                    id TEXT NOT NULL PRIMARY KEY,
                    actId TEXT NOT NULL,
                    title TEXT NOT NULL,
                    sortOrder INTEGER NOT NULL,
                    summary TEXT NOT NULL DEFAULT ''
                )
                """.trimIndent(),
            )
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS scenes (
                    id TEXT NOT NULL PRIMARY KEY,
                    chapterId TEXT NOT NULL,
                    title TEXT NOT NULL,
                    sortOrder INTEGER NOT NULL,
                    docJson TEXT NOT NULL,
                    plainText TEXT NOT NULL,
                    summary TEXT NOT NULL DEFAULT '',
                    beatsJson TEXT NOT NULL DEFAULT '[]',
                    wordCount INTEGER NOT NULL DEFAULT 0,
                    status TEXT NOT NULL DEFAULT 'draft',
                    pov TEXT NOT NULL DEFAULT '',
                    povCharacterId TEXT,
                    inWorldDate TEXT NOT NULL DEFAULT '',
                    labelsJson TEXT NOT NULL DEFAULT '[]',
                    colorHex TEXT,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS codex_categories (
                    id TEXT NOT NULL PRIMARY KEY,
                    scopeType TEXT NOT NULL,
                    scopeId TEXT NOT NULL,
                    name TEXT NOT NULL,
                    colorHex TEXT NOT NULL,
                    icon TEXT NOT NULL DEFAULT '',
                    glyph TEXT NOT NULL DEFAULT '',
                    sortOrder INTEGER NOT NULL DEFAULT 0,
                    isSystem INTEGER NOT NULL DEFAULT 0,
                    isBuiltIn INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS codex_entries (
                    id TEXT NOT NULL PRIMARY KEY,
                    categoryId TEXT NOT NULL,
                    scopeType TEXT NOT NULL,
                    scopeId TEXT NOT NULL,
                    name TEXT NOT NULL,
                    aliasesJson TEXT NOT NULL DEFAULT '[]',
                    docJson TEXT NOT NULL,
                    plainText TEXT NOT NULL,
                    colorHex TEXT,
                    alwaysInclude INTEGER NOT NULL DEFAULT 0,
                    disabled INTEGER NOT NULL DEFAULT 0,
                    imageMediaId TEXT,
                    isAiGenerated INTEGER NOT NULL DEFAULT 0,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS snippets (
                    id TEXT NOT NULL PRIMARY KEY,
                    scopeType TEXT NOT NULL,
                    scopeId TEXT NOT NULL,
                    title TEXT NOT NULL,
                    body TEXT NOT NULL,
                    category TEXT NOT NULL DEFAULT '',
                    pinned INTEGER NOT NULL DEFAULT 0,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS chat_threads (
                    id TEXT NOT NULL PRIMARY KEY,
                    scopeId TEXT NOT NULL,
                    name TEXT NOT NULL,
                    pinned INTEGER NOT NULL DEFAULT 0,
                    promptId TEXT,
                    modelRef TEXT NOT NULL DEFAULT '',
                    sceneId TEXT,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS chat_messages (
                    id TEXT NOT NULL PRIMARY KEY,
                    threadId TEXT NOT NULL,
                    role TEXT NOT NULL,
                    contentJson TEXT NOT NULL,
                    contextUsedJson TEXT NOT NULL DEFAULT '[]',
                    tokenCount INTEGER NOT NULL DEFAULT 0,
                    wordCount INTEGER NOT NULL DEFAULT 0,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS rp_characters (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    avatarMediaId TEXT,
                    description TEXT NOT NULL DEFAULT '',
                    personality TEXT NOT NULL DEFAULT '',
                    scenario TEXT NOT NULL DEFAULT '',
                    firstMes TEXT NOT NULL DEFAULT '',
                    mesExample TEXT NOT NULL DEFAULT '',
                    creatorNotes TEXT NOT NULL DEFAULT '',
                    systemPrompt TEXT NOT NULL DEFAULT '',
                    postHistoryInstructions TEXT NOT NULL DEFAULT '',
                    alternateGreetingsJson TEXT NOT NULL DEFAULT '[]',
                    tagsJson TEXT NOT NULL DEFAULT '[]',
                    characterVersion TEXT NOT NULL DEFAULT '2.0',
                    extensionsJson TEXT NOT NULL DEFAULT '{}',
                    defaultCodexId TEXT,
                    colorHex TEXT,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS rp_personas (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    avatarMediaId TEXT,
                    description TEXT NOT NULL DEFAULT '',
                    isDefault INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS rp_chats (
                    id TEXT NOT NULL PRIMARY KEY,
                    characterId TEXT,
                    groupId TEXT,
                    personaId TEXT NOT NULL,
                    title TEXT NOT NULL,
                    backgroundMediaId TEXT,
                    authorsNote TEXT NOT NULL DEFAULT '',
                    authorsNoteDepth INTEGER NOT NULL DEFAULT 4,
                    presetId TEXT,
                    promptTemplateId TEXT,
                    branchOfChatId TEXT,
                    displayMode TEXT NOT NULL DEFAULT 'messenger',
                    narrationColorHex TEXT,
                    speechColorHex TEXT,
                    oocColorHex TEXT,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS rp_messages (
                    id TEXT NOT NULL PRIMARY KEY,
                    chatId TEXT NOT NULL,
                    swipeGroupId TEXT NOT NULL,
                    swipeIndex INTEGER NOT NULL,
                    isActiveSwipe INTEGER NOT NULL,
                    role TEXT NOT NULL,
                    speakerCharacterId TEXT,
                    contentJson TEXT NOT NULL,
                    tokenCount INTEGER NOT NULL DEFAULT 0,
                    isEdited INTEGER NOT NULL DEFAULT 0,
                    createdAt INTEGER NOT NULL,
                    displayMode TEXT NOT NULL DEFAULT 'messenger'
                )
                """.trimIndent(),
            )
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS media (
                    id TEXT NOT NULL PRIMARY KEY,
                    type TEXT NOT NULL,
                    relativePath TEXT NOT NULL,
                    mimeType TEXT NOT NULL,
                    byteSize INTEGER NOT NULL,
                    width INTEGER NOT NULL DEFAULT 0,
                    height INTEGER NOT NULL DEFAULT 0,
                    durationMs INTEGER,
                    thumbnailPath TEXT,
                    checksum TEXT NOT NULL DEFAULT '',
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }
    }
}
