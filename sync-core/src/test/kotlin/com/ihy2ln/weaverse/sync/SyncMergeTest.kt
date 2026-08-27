package com.ihy2ln.weaverse.sync

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Record-level merge tests against real SQLite databases. Each test builds a
 * local and an incoming database with identical table shapes, ATTACHes the
 * incoming one, and asserts the merge outcome.
 */
class SyncMergeTest {

    private lateinit var localConn: Connection
    private lateinit var incomingConn: Connection
    private lateinit var localFile: File
    private lateinit var incomingFile: File
    private lateinit var local: JdbcSyncSql

    @BeforeEach
    fun setUp() {
        localFile = File.createTempFile("weaverse-local", ".db")
        incomingFile = File.createTempFile("weaverse-incoming", ".db")
        localConn = DriverManager.getConnection("jdbc:sqlite:${localFile.absolutePath}")
        incomingConn = DriverManager.getConnection("jdbc:sqlite:${incomingFile.absolutePath}")
        for (conn in listOf(localConn, incomingConn)) {
            conn.createStatement().use { st ->
                st.execute("CREATE TABLE books (id TEXT PRIMARY KEY, title TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                st.execute("CREATE TABLE scenes (id TEXT PRIMARY KEY, chapterId TEXT NOT NULL, title TEXT NOT NULL, docJson TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL DEFAULT 0)")
                st.execute("CREATE TABLE scene_codex_links (sceneId TEXT NOT NULL, entryId TEXT NOT NULL, PRIMARY KEY(sceneId, entryId))")
            }
        }
        SyncSchema.ensure(JdbcSyncSql(incomingConn))
        SyncSchema.ensure(JdbcSyncSql(localConn))
        val incomingPath = incomingFile.absolutePath.replace('\\', '/')
        localConn.createStatement().use { it.execute("ATTACH DATABASE '$incomingPath' AS incoming") }
        local = JdbcSyncSql(localConn)
    }

    @AfterEach
    fun tearDown() {
        runCatching { localConn.createStatement().use { it.execute("DETACH DATABASE incoming") } }
        localConn.close()
        incomingConn.close()
        localFile.delete()
        incomingFile.delete()
    }

    private fun exec(conn: Connection, sql: String, vararg binds: Any?) {
        if (binds.isEmpty()) {
            conn.createStatement().use { it.execute(sql) }
        } else {
            conn.prepareStatement(sql).use { ps ->
                binds.forEachIndexed { i, v -> ps.setObject(i + 1, v) }
                ps.execute()
            }
        }
    }

    private fun insertBook(conn: Connection, id: String, title: String, createdAt: Long = 1000L) =
        exec(conn, "INSERT INTO books(id, title, createdAt) VALUES(?, ?, ?)", id, title, createdAt)

    private fun bookTitle(id: String): String? =
        localConn.createStatement().use { st ->
            st.executeQuery("SELECT title FROM books WHERE id = '$id'").use { rs ->
                if (rs.next()) rs.getString(1) else null
            }
        }

    @Test
    fun `remote newer row overwrites local`() {
        insertBook(localConn, "b1", "Old title", 1000L)
        insertBook(incomingConn, "b1", "New title", 1000L)
        // Remote row was edited later.
        exec(incomingConn, "UPDATE books SET updatedAt = 5000 WHERE id = 'b1'")
        exec(localConn, "UPDATE books SET updatedAt = 2000 WHERE id = 'b1'")

        val report = SyncMerge.mergeInto(local)

        assertEquals("New title", bookTitle("b1"))
        assertEquals(1, report.appliedRows)
    }

    @Test
    fun `local newer row is kept`() {
        insertBook(localConn, "b1", "Local edit", 1000L)
        insertBook(incomingConn, "b1", "Remote edit", 1000L)
        exec(localConn, "UPDATE books SET updatedAt = 9000 WHERE id = 'b1'")
        exec(incomingConn, "UPDATE books SET updatedAt = 5000 WHERE id = 'b1'")

        SyncMerge.mergeInto(local)

        assertEquals("Local edit", bookTitle("b1"))
    }

    @Test
    fun `remote-only rows are added`() {
        insertBook(incomingConn, "b2", "Brand new", 1000L)

        val report = SyncMerge.mergeInto(local)

        assertEquals("Brand new", bookTitle("b2"))
        assertEquals(1, report.appliedRows)
    }

    @Test
    fun `remote delete propagates through tombstone`() {
        insertBook(localConn, "b1", "Doomed", 1000L)
        insertBook(incomingConn, "b1", "Doomed", 1000L)
        // Both sides start synced.
        exec(localConn, "UPDATE books SET updatedAt = 1000 WHERE id = 'b1'")
        exec(incomingConn, "UPDATE books SET updatedAt = 1000 WHERE id = 'b1'")
        // Remote deletes it (trigger writes a tombstone with a later timestamp).
        Thread.sleep(10)
        exec(incomingConn, "DELETE FROM books WHERE id = 'b1'")

        val report = SyncMerge.mergeInto(local)

        assertEquals(null, bookTitle("b1"))
        assertEquals(1, report.deletedRows)
    }

    @Test
    fun `locally re-created row survives remote delete`() {
        insertBook(localConn, "b1", "Original", 1000L)
        insertBook(incomingConn, "b1", "Original", 1000L)
        exec(localConn, "UPDATE books SET updatedAt = 1000 WHERE id = 'b1'")
        exec(incomingConn, "UPDATE books SET updatedAt = 1000 WHERE id = 'b1'")
        Thread.sleep(10)
        exec(incomingConn, "DELETE FROM books WHERE id = 'b1'")
        // Local keeps editing after the remote delete.
        Thread.sleep(10)
        exec(localConn, "UPDATE books SET title = 'Still alive', updatedAt = ? WHERE id = 'b1'", System.currentTimeMillis())

        SyncMerge.mergeInto(local)

        assertEquals("Still alive", bookTitle("b1"))
    }

    @Test
    fun `update without updatedAt is auto-touched by trigger`() {
        insertBook(localConn, "b1", "Title", 1000L)
        exec(localConn, "UPDATE books SET updatedAt = 1000 WHERE id = 'b1'")
        exec(localConn, "UPDATE books SET title = 'Touched' WHERE id = 'b1'") // no updatedAt set

        val touched = localConn.createStatement().use { st ->
            st.executeQuery("SELECT updatedAt FROM books WHERE id = 'b1'").use { rs ->
                rs.next(); rs.getLong(1)
            }
        }
        assertTrue(touched > 1000, "trigger should stamp updatedAt, got $touched")
    }

    @Test
    fun `overwrite of modified local row is captured as conflict`() {
        insertBook(localConn, "b1", "Local version", 1000L)
        insertBook(incomingConn, "b1", "Remote version", 1000L)
        exec(localConn, "UPDATE books SET updatedAt = 6000 WHERE id = 'b1'")
        exec(incomingConn, "UPDATE books SET updatedAt = 7000 WHERE id = 'b1'")

        val report = SyncMerge.mergeInto(local)

        assertEquals(1, report.conflicts)
        val conflicts = SyncMerge.conflicts(local)
        assertEquals(1, conflicts.size)
        assertEquals("books", conflicts.first().tableName)
        assertTrue(conflicts.first().lostJson.contains("Local version"))
        assertEquals("Remote version", bookTitle("b1"))
    }

    @Test
    fun `composite key table merges and tombstones`() {
        exec(localConn, "INSERT INTO scene_codex_links(sceneId, entryId) VALUES('s1', 'e1')")
        exec(localConn, "INSERT INTO scene_codex_links(sceneId, entryId) VALUES('s2', 'e2')")
        exec(incomingConn, "INSERT INTO scene_codex_links(sceneId, entryId) VALUES('s1', 'e1')")
        exec(incomingConn, "INSERT INTO scene_codex_links(sceneId, entryId) VALUES('s3', 'e3')")
        exec(incomingConn, "UPDATE scene_codex_links SET updatedAt = 1000")
        exec(localConn, "UPDATE scene_codex_links SET updatedAt = 1000")
        Thread.sleep(10)
        exec(incomingConn, "DELETE FROM scene_codex_links WHERE sceneId = 's1'")

        val report = SyncMerge.mergeInto(local)

        fun exists(vararg pair: String): Boolean = localConn.createStatement().use { st ->
            st.executeQuery("SELECT 1 FROM scene_codex_links WHERE sceneId = '${pair[0]}' AND entryId = '${pair[1]}'").use { it.next() }
        }
        assertEquals(false, exists("s1", "e1")) // deleted remotely
        assertEquals(true, exists("s2", "e2")) // kept locally
        assertEquals(true, exists("s3", "e3")) // added from remote
        assertEquals(1, report.appliedRows)
        assertEquals(1, report.deletedRows)
    }

    @Test
    fun `scenes table with existing updatedAt participates`() {
        exec(localConn, "INSERT INTO scenes(id, chapterId, title, docJson, createdAt, updatedAt) VALUES('sc1', 'c1', 'Local', '{}', 1000, 1000)")
        exec(incomingConn, "INSERT INTO scenes(id, chapterId, title, docJson, createdAt, updatedAt) VALUES('sc1', 'c1', 'Remote', '{}', 1000, 9000)")

        val report = SyncMerge.mergeInto(local)

        val title = localConn.createStatement().use { st ->
            st.executeQuery("SELECT title FROM scenes WHERE id = 'sc1'").use { rs -> rs.next(); rs.getString(1) }
        }
        assertEquals("Remote", title)
        assertTrue("scenes" !in report.skippedTables)
    }

    @Test
    fun `merge is repeatable and converges`() {
        insertBook(localConn, "b1", "L", 1000L)
        insertBook(incomingConn, "b1", "R", 1000L)
        exec(localConn, "UPDATE books SET updatedAt = 5000 WHERE id = 'b1'")
        exec(incomingConn, "UPDATE books SET updatedAt = 6000 WHERE id = 'b1'")
        SyncMerge.mergeInto(local)
        val second = SyncMerge.mergeInto(local)

        assertEquals("R", bookTitle("b1"))
        // Second run applies nothing new (timestamps equal now), except conflict
        // capture may re-run only when content differs — after first merge it matches.
        assertEquals(0, second.conflicts)
    }

    @Test
    fun `restore lost conflict puts local version back`() {
        insertBook(localConn, "b1", "Local version", 1000L)
        insertBook(incomingConn, "b1", "Remote version", 1000L)
        exec(localConn, "UPDATE books SET updatedAt = 6000 WHERE id = 'b1'")
        exec(incomingConn, "UPDATE books SET updatedAt = 7000 WHERE id = 'b1'")

        SyncMerge.mergeInto(local)
        val conflict = SyncMerge.conflicts(local).first()
        SyncMerge.restoreLost(local, conflict)

        assertEquals("Local version", bookTitle("b1"))
        assertTrue(SyncMerge.conflicts(local).isEmpty())
        val tombstones = localConn.createStatement().use { st ->
            st.executeQuery("SELECT COUNT(*) FROM sync_tombstones").use { it.next(); it.getInt(1) }
        }
        assertEquals(0, tombstones)
    }
}
