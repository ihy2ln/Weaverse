package com.ihy2ln.weaverse.desktop

import com.ihy2ln.weaverse.sync.BookSummary
import com.ihy2ln.weaverse.sync.ChatLine
import com.ihy2ln.weaverse.sync.CodexEntrySummary
import com.ihy2ln.weaverse.sync.LibrarySummary
import com.ihy2ln.weaverse.sync.MediaSummary
import com.ihy2ln.weaverse.sync.NoteDetail
import com.ihy2ln.weaverse.sync.NoteSummary
import com.ihy2ln.weaverse.sync.RpChatSummary
import com.ihy2ln.weaverse.sync.SceneDetail
import com.ihy2ln.weaverse.sync.SceneSummary
import com.ihy2ln.weaverse.sync.ThreadSummary
import com.ihy2ln.weaverse.sync.WorkspaceSnapshot
import com.ihy2ln.weaverse.sync.novelcrafter.ImportArt
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

object LibraryReader {
    init {
        Class.forName("org.sqlite.JDBC")
    }

    private fun connect(dbFile: File): Connection =
        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")

    private fun tableExists(conn: Connection, name: String): Boolean =
        conn.createStatement().use { st ->
            st.executeQuery(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name='$name'",
            ).use { it.next() }
        }

    fun summarize(dbFile: File): LibrarySummary {
        val snap = workspace(dbFile)
        return LibrarySummary(books = snap.books, notes = snap.notes)
    }

    fun workspace(dbFile: File): WorkspaceSnapshot {
        if (!dbFile.exists()) return WorkspaceSnapshot()
        return runCatching {
            connect(dbFile).use { conn ->
                WorkspaceSnapshot(
                    books = books(conn),
                    scenes = scenes(conn),
                    codex = codex(conn),
                    notes = notes(conn),
                    threads = threads(conn),
                    rpChats = rpChats(conn),
                    media = media(conn),
                )
            }
        }.getOrElse { WorkspaceSnapshot() }
    }

    fun note(dbFile: File, id: String): NoteDetail? {
        if (!dbFile.exists()) return null
        return runCatching {
            connect(dbFile).use { conn ->
                if (!tableExists(conn, "snippets")) return null
                conn.prepareStatement(
                    "SELECT id, title, body FROM snippets WHERE id = ? AND category = 'notes'",
                ).use { ps ->
                    ps.setString(1, id)
                    ps.executeQuery().use { rs ->
                        if (!rs.next()) return null
                        NoteDetail(
                            id = rs.getString("id"),
                            title = rs.getString("title") ?: "Untitled",
                            body = rs.getString("body").orEmpty(),
                        )
                    }
                }
            }
        }.getOrNull()
    }

    fun upsertNote(dbFile: File, id: String, title: String, body: String) {
        dbFile.parentFile?.mkdirs()
        connect(dbFile).use { conn ->
            conn.createStatement().use { st ->
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
            }
            val now = System.currentTimeMillis()
            conn.prepareStatement(
                """
                INSERT INTO snippets(id, scopeType, scopeId, title, body, category, pinned, createdAt)
                VALUES(?, 'app', 'global', ?, ?, 'notes', 0, ?)
                ON CONFLICT(id) DO UPDATE SET title=excluded.title, body=excluded.body
                """.trimIndent(),
            ).use { ps ->
                ps.setString(1, id)
                ps.setString(2, title)
                ps.setString(3, body)
                ps.setLong(4, now)
                ps.executeUpdate()
            }
        }
    }

    fun scene(dbFile: File, id: String): SceneDetail? {
        if (!dbFile.exists()) return null
        return runCatching {
            connect(dbFile).use { conn ->
                if (!tableExists(conn, "scenes")) return null
                conn.prepareStatement(
                    "SELECT id, title, summary, plainText, wordCount, status FROM scenes WHERE id = ?",
                ).use { ps ->
                    ps.setString(1, id)
                    ps.executeQuery().use { rs ->
                        if (!rs.next()) return null
                        SceneDetail(
                            id = rs.getString("id"),
                            title = rs.getString("title") ?: "Untitled",
                            summary = rs.getString("summary").orEmpty(),
                            body = rs.getString("plainText").orEmpty(),
                            wordCount = rs.getInt("wordCount"),
                            status = rs.getString("status") ?: "draft",
                        )
                    }
                }
            }
        }.getOrNull()
    }

    fun upsertScene(dbFile: File, id: String, title: String, summary: String, body: String) {
        if (!dbFile.exists()) return
        connect(dbFile).use { conn ->
            if (!tableExists(conn, "scenes")) return
            val words = body.split(Regex("\\s+")).count { it.isNotBlank() }
            conn.prepareStatement(
                """
                UPDATE scenes SET title = ?, summary = ?, plainText = ?, wordCount = ?, updatedAt = ?
                WHERE id = ?
                """.trimIndent(),
            ).use { ps ->
                ps.setString(1, title)
                ps.setString(2, summary)
                ps.setString(3, body)
                ps.setInt(4, words)
                ps.setLong(5, System.currentTimeMillis())
                ps.setString(6, id)
                ps.executeUpdate()
            }
        }
    }

    fun threadMessages(dbFile: File, threadId: String): List<ChatLine> {
        if (!dbFile.exists()) return emptyList()
        return runCatching {
            connect(dbFile).use { conn ->
                if (!tableExists(conn, "chat_messages")) return emptyList()
                conn.prepareStatement(
                    "SELECT id, role, contentJson, createdAt FROM chat_messages WHERE threadId = ? ORDER BY createdAt",
                ).use { ps ->
                    ps.setString(1, threadId)
                    ps.executeQuery().use { rs ->
                        buildList {
                            while (rs.next()) {
                                add(
                                    ChatLine(
                                        id = rs.getString("id"),
                                        role = rs.getString("role") ?: "user",
                                        text = rs.getString("contentJson").orEmpty(),
                                        createdAt = rs.getLong("createdAt"),
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun rpMessages(dbFile: File, chatId: String): List<ChatLine> {
        if (!dbFile.exists()) return emptyList()
        return runCatching {
            connect(dbFile).use { conn ->
                if (!tableExists(conn, "rp_messages")) return emptyList()
                conn.prepareStatement(
                    """
                    SELECT id, role, contentJson, createdAt FROM rp_messages
                    WHERE chatId = ? AND isActiveSwipe = 1
                    ORDER BY createdAt
                    """.trimIndent(),
                ).use { ps ->
                    ps.setString(1, chatId)
                    ps.executeQuery().use { rs ->
                        buildList {
                            while (rs.next()) {
                                add(
                                    ChatLine(
                                        id = rs.getString("id"),
                                        role = rs.getString("role") ?: "user",
                                        text = rs.getString("contentJson").orEmpty(),
                                        createdAt = rs.getLong("createdAt"),
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun books(conn: Connection): List<BookSummary> {
        if (!tableExists(conn, "books")) return emptyList()
        return conn.createStatement().use { st ->
            st.executeQuery("SELECT id, title, updatedAt FROM books ORDER BY updatedAt DESC").use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            BookSummary(
                                id = rs.getString("id"),
                                title = rs.getString("title") ?: "Untitled",
                                updatedAt = rs.getLong("updatedAt"),
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun notes(conn: Connection): List<NoteSummary> {
        if (!tableExists(conn, "snippets")) return emptyList()
        return conn.createStatement().use { st ->
            st.executeQuery(
                "SELECT id, title, body, createdAt FROM snippets WHERE category = 'notes' ORDER BY createdAt DESC",
            ).use { rs ->
                buildList {
                    while (rs.next()) {
                        val body = rs.getString("body").orEmpty()
                        add(
                            NoteSummary(
                                id = rs.getString("id"),
                                title = rs.getString("title") ?: "Untitled",
                                bodyPreview = body.take(160),
                                updatedAt = rs.getLong("createdAt"),
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun scenes(conn: Connection): List<SceneSummary> {
        if (!tableExists(conn, "scenes")) return emptyList()
        val sql = if (tableExists(conn, "acts") && tableExists(conn, "chapters")) {
            """
            SELECT s.id, s.title, s.summary, s.wordCount, s.status, s.updatedAt,
                   a.title AS actTitle, c.title AS chapterTitle, a.bookId AS bookId
            FROM scenes s
            JOIN chapters c ON c.id = s.chapterId
            JOIN acts a ON a.id = c.actId
            ORDER BY a.sortOrder, c.sortOrder, s.sortOrder
            """.trimIndent()
        } else {
            "SELECT id, title, summary, wordCount, status, updatedAt FROM scenes ORDER BY sortOrder"
        }
        return conn.createStatement().use { st ->
            st.executeQuery(sql).use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            SceneSummary(
                                id = rs.getString("id"),
                                bookId = runCatching { rs.getString("bookId") }.getOrNull().orEmpty(),
                                actTitle = runCatching { rs.getString("actTitle") }.getOrNull().orEmpty(),
                                chapterTitle = runCatching { rs.getString("chapterTitle") }.getOrNull().orEmpty(),
                                title = rs.getString("title") ?: "Untitled",
                                summary = rs.getString("summary").orEmpty(),
                                wordCount = rs.getInt("wordCount"),
                                status = rs.getString("status") ?: "draft",
                                updatedAt = rs.getLong("updatedAt"),
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun codex(conn: Connection): List<CodexEntrySummary> {
        if (!tableExists(conn, "codex_entries")) return emptyList()
        val sql = if (tableExists(conn, "codex_categories")) {
            """
            SELECT e.id, e.name, e.plainText, c.name AS category
            FROM codex_entries e
            LEFT JOIN codex_categories c ON c.id = e.categoryId
            WHERE e.disabled = 0
            ORDER BY c.sortOrder, e.name
            """.trimIndent()
        } else {
            "SELECT id, name, plainText FROM codex_entries ORDER BY name"
        }
        return conn.createStatement().use { st ->
            st.executeQuery(sql).use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            CodexEntrySummary(
                                id = rs.getString("id"),
                                name = rs.getString("name") ?: "Untitled",
                                category = runCatching { rs.getString("category") }.getOrNull().orEmpty(),
                                bodyPreview = rs.getString("plainText").orEmpty().take(160),
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun threads(conn: Connection): List<ThreadSummary> {
        if (!tableExists(conn, "chat_threads")) return emptyList()
        return conn.createStatement().use { st ->
            st.executeQuery(
                "SELECT id, name, updatedAt FROM chat_threads ORDER BY pinned DESC, updatedAt DESC",
            ).use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            ThreadSummary(
                                id = rs.getString("id"),
                                name = rs.getString("name") ?: "Chat",
                                updatedAt = rs.getLong("updatedAt"),
                            ),
                        )
                    }
                }
            }
        }
    }

    fun resolveMediaFile(dataDir: File, id: String): File? {
        val dbFile = DesktopPaths.dbFile(dataDir)
        if (!dbFile.exists()) return fallbackArtFile(dataDir, id)
        return runCatching {
            connect(dbFile).use { conn ->
                if (!tableExists(conn, "media")) return@use fallbackArtFile(dataDir, id)
                conn.prepareStatement("SELECT relativePath FROM media WHERE id = ?").use { ps ->
                    ps.setString(1, id)
                    ps.executeQuery().use { rs ->
                        if (!rs.next()) return@use fallbackArtFile(dataDir, id)
                        val relative = rs.getString("relativePath").orEmpty()
                        val candidates = listOf(
                            File(dataDir, relative),
                            File(DesktopPaths.mediaDir(dataDir), relative.substringAfterLast('/')),
                            File(DesktopPaths.mediaDir(dataDir), relative.removePrefix("media/")),
                        )
                        candidates.firstOrNull { it.exists() } ?: fallbackArtFile(dataDir, id)
                    }
                }
            }
        }.getOrNull()
    }

    private fun fallbackArtFile(dataDir: File, id: String): File? {
        val piece = ImportArt.pieces.firstOrNull { it.id == id } ?: return null
        val loose = File(DesktopPaths.mediaDir(dataDir), piece.fileName)
        return loose.takeIf { it.exists() }
    }

    private fun media(conn: Connection): List<MediaSummary> {
        if (!tableExists(conn, "media")) return emptyList()
        return conn.createStatement().use { st ->
            st.executeQuery("SELECT id, relativePath FROM media ORDER BY createdAt").use { rs ->
                buildList {
                    while (rs.next()) {
                        val id = rs.getString("id") ?: continue
                        val piece = ImportArt.pieces.firstOrNull { it.id == id }
                        add(
                            MediaSummary(
                                id = id,
                                caption = piece?.caption.orEmpty(),
                                section = piece?.section.orEmpty(),
                                relativePath = rs.getString("relativePath").orEmpty(),
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun rpChats(conn: Connection): List<RpChatSummary> {
        if (!tableExists(conn, "rp_chats")) return emptyList()
        return conn.createStatement().use { st ->
            st.executeQuery(
                "SELECT id, title, displayMode, updatedAt FROM rp_chats ORDER BY updatedAt DESC",
            ).use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            RpChatSummary(
                                id = rs.getString("id"),
                                title = rs.getString("title") ?: "Chat",
                                displayMode = rs.getString("displayMode") ?: "messenger",
                                updatedAt = rs.getLong("updatedAt"),
                            ),
                        )
                    }
                }
            }
        }
    }
}
