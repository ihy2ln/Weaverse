package com.ihy2ln.weaverse.sync

/**
 * Shared, idempotent schema plumbing for record-level sync:
 *  - an `updatedAt` column on every synced table (auto-touched by triggers so
 *    existing write paths don't need to set it themselves),
 *  - a `sync_tombstones` table recording deletes (via triggers),
 *  - a `sync_conflicts` table capturing the losing row when a merge overwrites
 *    locally-modified data, so nothing is silently lost.
 *
 * Runs identically on the Android host (Room connection) and the desktop host
 * (JDBC). [ensure] may be called on any database any number of times; on an
 * attached peer database pass its alias as [prefix] (e.g. "incoming.").
 */
object SyncSchema {

    /** Bumped when the trigger/table set changes so older peers can be detected. */
    const val SYNC_SCHEMA_VERSION = 4

    data class SyncTable(val name: String, val keys: List<String>)

    val TABLES: List<SyncTable> = listOf(
        SyncTable("series", listOf("id")),
        SyncTable("books", listOf("id")),
        SyncTable("acts", listOf("id")),
        SyncTable("chapters", listOf("id")),
        SyncTable("scenes", listOf("id")),
        SyncTable("scene_codex_links", listOf("sceneId", "entryId")),
        SyncTable("codex_categories", listOf("id")),
        SyncTable("codex_entries", listOf("id")),
        SyncTable("codex_entries_lore", listOf("entryId")),
        SyncTable("snippets", listOf("id")),
        SyncTable("chat_threads", listOf("id")),
        SyncTable("chat_messages", listOf("id")),
        SyncTable("rp_characters", listOf("id")),
        SyncTable("rp_personas", listOf("id")),
        SyncTable("rp_chats", listOf("id")),
        SyncTable("rp_messages", listOf("id")),
        SyncTable("media", listOf("id")),
        SyncTable("prompt_folders", listOf("id")),
        SyncTable("prompts", listOf("id")),
        SyncTable("ai_profiles", listOf("id")),
    )

    fun keyExpr(table: SyncTable, prefix: String = ""): String =
        table.keys.joinToString(" || ':' || ") { "$prefix$it" }

    private fun whereByKey(table: SyncTable, a: String, b: String): String =
        table.keys.joinToString(" AND ") { "$a$it = $b$it" }

    fun ensure(sql: SyncSql, prefix: String = "") {
        sql.exec(
            """
            CREATE TABLE IF NOT EXISTS ${prefix}sync_tombstones (
                tableName TEXT NOT NULL,
                rowKey TEXT NOT NULL,
                deletedAt INTEGER NOT NULL,
                PRIMARY KEY(tableName, rowKey)
            )
            """.trimIndent(),
        )
        sql.exec(
            """
            CREATE TABLE IF NOT EXISTS ${prefix}sync_conflicts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                tableName TEXT NOT NULL,
                rowKey TEXT NOT NULL,
                winner TEXT NOT NULL,
                lostJson TEXT NOT NULL,
                localUpdatedAt INTEGER NOT NULL,
                remoteUpdatedAt INTEGER NOT NULL,
                at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        sql.exec(
            "CREATE TABLE IF NOT EXISTS ${prefix}sync_meta (" +
                "key TEXT NOT NULL PRIMARY KEY, value INTEGER NOT NULL)",
        )

        for (t in TABLES) {
            val cols = sql.columnsOf("${prefix}${t.name}")
            if (cols.isEmpty()) continue // table absent on this (older) database
            if ("updatedAt" !in cols) {
                sql.exec(
                    "ALTER TABLE ${prefix}${t.name} ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0",
                )
            }
            // Baseline new columns from createdAt once, so first merge isn't a coin flip.
            if ("createdAt" in cols) {
                sql.exec(
                    "UPDATE ${prefix}${t.name} SET updatedAt = createdAt WHERE updatedAt = 0",
                )
            }
            // SQLite forbids qualified table names inside triggers, so we never
            // install triggers on an attached peer database (prefix != "").
            if (prefix.isEmpty()) {
                createTouchTrigger(sql, t, prefix)
                createBuryTrigger(sql, t, prefix)
            }
        }

        sql.exec(
            "INSERT OR REPLACE INTO ${prefix}sync_meta(key, value) VALUES('schemaVersion', $SYNC_SCHEMA_VERSION)",
        )
    }

    private fun createTouchTrigger(sql: SyncSql, t: SyncTable, prefix: String) {
        // Only stamps when the statement itself didn't change updatedAt — write
        // paths that already maintain it (scenes, books, …) are left alone.
        sql.exec(
            """
            CREATE TRIGGER IF NOT EXISTS ${prefix}sync_touch_${t.name}
            AFTER UPDATE ON ${prefix}${t.name}
            FOR EACH ROW WHEN NEW.updatedAt = OLD.updatedAt
            BEGIN
                UPDATE ${prefix}${t.name}
                SET updatedAt = CAST(strftime('%s','now') AS INTEGER) * 1000
                WHERE ${whereByKey(t, "", "NEW.")};
            END
            """.trimIndent(),
        )
    }

    private fun createBuryTrigger(sql: SyncSql, t: SyncTable, prefix: String) {
        val key = t.keys.joinToString(" || ':' || ") { "OLD.$it" }
        sql.exec(
            """
            CREATE TRIGGER IF NOT EXISTS ${prefix}sync_bury_${t.name}
            AFTER DELETE ON ${prefix}${t.name}
            FOR EACH ROW
            BEGIN
                INSERT OR REPLACE INTO ${prefix}sync_tombstones(tableName, rowKey, deletedAt)
                VALUES('${t.name}', $key, CAST(strftime('%s','now') AS INTEGER) * 1000);
            END
            """.trimIndent(),
        )
    }
}
