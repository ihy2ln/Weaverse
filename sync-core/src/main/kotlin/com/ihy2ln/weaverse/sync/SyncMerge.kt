package com.ihy2ln.weaverse.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Record-level merge for Weaverse sync. The caller ATTACHes the peer database
 * (by default under the alias `incoming`) and then calls [mergeInto] on the
 * local database's [SyncSql]. Every synced table merges row-by-row:
 *
 *  - a remote row wins when its `updatedAt` is newer than the local one
 *    (or the row doesn't exist locally yet),
 *  - a local row re-created after a remote delete survives (its `updatedAt`
 *    beats the tombstone),
 *  - remote tombstones delete local rows that haven't been touched since,
 *  - before any locally-modified row is overwritten, the losing version is
 *    copied into `sync_conflicts` — nothing is silently dropped.
 *
 * Writes go through the host's own SQLite connection so Room's invalidation
 * tracker fires and the UI refreshes without an app restart.
 *
 * Uses UPDATE + INSERT (not INSERT OR REPLACE) so bury-triggers don't
 * tombstone rows we just applied. That also stays compatible with the
 * SQLite 3.18 shipped on Android API 26.
 */
object SyncMerge {

    private val json = Json { ignoreUnknownKeys = true }

    data class Report(
        val appliedRows: Int = 0,
        val deletedRows: Int = 0,
        val conflicts: Int = 0,
        val skippedTables: List<String> = emptyList(),
    ) {
        val summary: String
            get() = "$appliedRows applied, $deletedRows removed, $conflicts conflicts" +
                if (skippedTables.isEmpty()) "" else ", skipped: ${skippedTables.joinToString()}"
    }

    fun mergeInto(local: SyncSql, incoming: String = "incoming"): Report {
        SyncSchema.ensure(local)
        SyncSchema.ensure(local, prefix = "$incoming.")

        var applied = 0
        var deleted = 0
        var conflicts = 0
        val skipped = mutableListOf<String>()

        local.transaction {
            for (t in SyncSchema.TABLES) {
                val localCols = local.columnsOf(t.name)
                if (localCols.isEmpty()) continue
                val remoteCols = local.columnsOf("$incoming.${t.name}")
                if (remoteCols.isEmpty()) {
                    skipped += t.name
                    continue
                }
                val shared = localCols.intersect(remoteCols.toSet()).toList()
                if ("updatedAt" !in shared || t.keys.any { it !in shared }) {
                    skipped += t.name
                    continue
                }
                conflicts += captureConflicts(local, t, incoming, shared)
                applied += applyRemoteRows(local, t, incoming, shared)
                deleted += applyTombstones(local, t, incoming)
            }
            importTombstones(local, incoming)
            gc(local)
        }

        return Report(applied, deleted, conflicts, skipped)
    }

    /**
     * Captures the local copy of every row the peer is about to overwrite
     * where the local row was also modified and the content actually differs.
     * Must run **before** [applyRemoteRows].
     */
    private fun captureConflicts(local: SyncSql, t: SyncSchema.SyncTable, inc: String, cols: List<String>): Int {
        val join = t.keys.joinToString(" AND ") { "l.$it = i.$it" }
        val selectList = cols.joinToString(", ") { c -> "i.$c AS i_$c, l.$c AS l_$c" }
        val rows = local.select(
            "SELECT $selectList FROM $inc.${t.name} i JOIN ${t.name} l ON $join " +
                "WHERE i.updatedAt > l.updatedAt AND l.updatedAt > 0",
        )
        if (rows.rows.isEmpty()) return 0

        var captured = 0
        val now = System.currentTimeMillis()
        for (row in rows.rowObjects()) {
            val differs = cols.any { c ->
                c != "updatedAt" && c !in t.keys && row["i_$c"] != row["l_$c"]
            }
            if (!differs) continue
            val lost = JsonObject(
                buildMap {
                    cols.forEach { c -> put(c, toJson(row["l_$c"])) }
                },
            )
            val rowKey = t.keys.joinToString(":") { row["l_$it"].toString() }
            local.exec(
                "INSERT INTO sync_conflicts(tableName, rowKey, winner, lostJson, localUpdatedAt, remoteUpdatedAt, at) " +
                    "VALUES(?, ?, 'remote', ?, ?, ?, ?)",
                t.name,
                rowKey,
                lost.toString(),
                (row["l_updatedAt"] as? Number)?.toLong() ?: 0L,
                (row["i_updatedAt"] as? Number)?.toLong() ?: 0L,
                now,
            )
            captured++
        }
        return captured
    }

    /** Rows the peer has that are new or newer than local. */
    private fun applyRemoteRows(local: SyncSql, t: SyncSchema.SyncTable, inc: String, cols: List<String>): Int {
        val join = t.keys.joinToString(" AND ") { "l.$it = i.$it" }
        val result = local.select(
            "SELECT COUNT(*) FROM $inc.${t.name} i LEFT JOIN ${t.name} l ON $join " +
                "WHERE l.${t.keys.first()} IS NULL OR i.updatedAt > l.updatedAt",
        )
        val count = (result.rows.firstOrNull()?.firstOrNull() as? Number)?.toInt() ?: 0
        if (count == 0) return 0

        val nonKeys = cols.filter { it !in t.keys }
        if (nonKeys.isNotEmpty()) {
            val assignments = nonKeys.joinToString(", ") { c ->
                val match = t.keys.joinToString(" AND ") { "i.$it = ${t.name}.$it" }
                "$c = (SELECT i.$c FROM $inc.${t.name} i WHERE $match)"
            }
            val existsNewer =
                "EXISTS (SELECT 1 FROM $inc.${t.name} i WHERE " +
                    t.keys.joinToString(" AND ") { "i.$it = ${t.name}.$it" } +
                    " AND i.updatedAt > ${t.name}.updatedAt)"
            local.exec("UPDATE ${t.name} SET $assignments WHERE $existsNewer")
        }

        val colList = cols.joinToString(", ")
        val selectList = cols.joinToString(", ") { "i.$it" }
        local.exec(
            "INSERT INTO ${t.name}($colList) SELECT $selectList FROM $inc.${t.name} i " +
                "LEFT JOIN ${t.name} l ON $join WHERE l.${t.keys.first()} IS NULL",
        )
        return count
    }

    /** Deletes local rows the peer deleted, unless they were touched since. */
    private fun applyTombstones(local: SyncSql, t: SyncSchema.SyncTable, inc: String): Int {
        val keyExpr = t.keys.joinToString(" || ':' || ") { it }
        val predicate =
            "tb.tableName = '${t.name}' AND tb.deletedAt > " +
                "(SELECT COALESCE(l.updatedAt, 0) FROM ${t.name} l WHERE l.${t.keys.joinToString(" || ':' || ")} = tb.rowKey)"
        val countResult = local.select(
            "SELECT COUNT(*) FROM $inc.sync_tombstones tb WHERE $predicate",
        )
        val count = (countResult.rows.firstOrNull()?.firstOrNull() as? Number)?.toInt() ?: 0
        if (count > 0) {
            local.exec(
                "DELETE FROM ${t.name} WHERE $keyExpr IN " +
                    "(SELECT tb.rowKey FROM $inc.sync_tombstones tb WHERE $predicate)",
            )
        }
        return count
    }

    /** Keeps local tombstones the newest for each key. */
    private fun importTombstones(local: SyncSql, inc: String) {
        val rows = local.select(
            "SELECT tb.tableName, tb.rowKey, tb.deletedAt FROM $inc.sync_tombstones tb",
        )
        for (row in rows.rows) {
            local.exec(
                "INSERT OR REPLACE INTO sync_tombstones(tableName, rowKey, deletedAt) SELECT ?, ?, ? " +
                    "WHERE NOT EXISTS(SELECT 1 FROM sync_tombstones l WHERE l.tableName = ? AND l.rowKey = ? AND l.deletedAt > ?)",
                row[0], row[1], row[2], row[0], row[1], row[2],
            )
        }
    }

    private fun gc(local: SyncSql) {
        local.exec("DELETE FROM sync_conflicts WHERE id NOT IN (SELECT id FROM sync_conflicts ORDER BY id DESC LIMIT 200)")
        local.exec(
            "DELETE FROM sync_tombstones WHERE deletedAt < ?",
            System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000,
        )
    }

    /** Reads stored conflicts (newest first) for a Settings-style viewer. */
    fun conflicts(local: SyncSql, limit: Int = 50): List<ConflictEntry> =
        local.select(
            "SELECT id, tableName, rowKey, winner, lostJson, localUpdatedAt, remoteUpdatedAt, at " +
                "FROM sync_conflicts ORDER BY id DESC LIMIT $limit",
        ).rows.map { row ->
            ConflictEntry(
                id = (row[0] as? Number)?.toLong() ?: 0L,
                tableName = row[1]?.toString().orEmpty(),
                rowKey = row[2]?.toString().orEmpty(),
                winner = row[3]?.toString().orEmpty(),
                lostJson = row[4]?.toString().orEmpty(),
                localUpdatedAt = (row[5] as? Number)?.toLong() ?: 0L,
                remoteUpdatedAt = (row[6] as? Number)?.toLong() ?: 0L,
                at = (row[7] as? Number)?.toLong() ?: 0L,
            )
        }

    /** Restores the losing local version, overwriting the remote-winning row. */
    fun restoreLost(local: SyncSql, entry: ConflictEntry) {
        val table = SyncSchema.TABLES.find { it.name == entry.tableName } ?: return
        val obj = runCatching { json.parseToJsonElement(entry.lostJson).jsonObject }.getOrNull() ?: return
        val cols = local.columnsOf(table.name).filter { it in obj }
        if (cols.isEmpty() || table.keys.any { it !in cols }) return
        val placeholders = cols.joinToString(",") { "?" }
        val values = cols.map { col -> jsonToSql(obj[col]) }.toTypedArray()
        // INSERT OR REPLACE would bury the row; strip the tombstone afterwards.
        local.exec(
            "INSERT OR REPLACE INTO ${table.name}(${cols.joinToString(",")}) VALUES($placeholders)",
            *values,
        )
        local.exec(
            "DELETE FROM sync_tombstones WHERE tableName = ? AND rowKey = ?",
            table.name,
            entry.rowKey,
        )
        dismissConflict(local, entry)
    }

    fun dismissConflict(local: SyncSql, entry: ConflictEntry) {
        if (entry.id > 0) {
            local.exec("DELETE FROM sync_conflicts WHERE id = ?", entry.id)
        } else {
            local.exec(
                "DELETE FROM sync_conflicts WHERE tableName = ? AND rowKey = ? AND at = ?",
                entry.tableName,
                entry.rowKey,
                entry.at,
            )
        }
    }

    data class ConflictEntry(
        val id: Long = 0L,
        val tableName: String,
        val rowKey: String,
        val winner: String,
        val lostJson: String,
        val localUpdatedAt: Long,
        val remoteUpdatedAt: Long,
        val at: Long,
    )
}

private fun toJson(value: Any?): kotlinx.serialization.json.JsonElement = when (value) {
    null -> JsonNull
    is Number -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    else -> JsonPrimitive(value.toString())
}

private fun jsonToSql(element: kotlinx.serialization.json.JsonElement?): Any? {
    if (element == null || element is JsonNull) return null
    val prim = element.jsonPrimitive
    prim.contentOrNull ?: return null
    if (prim.isString) return prim.content
    return prim.content.toLongOrNull()
        ?: prim.content.toDoubleOrNull()
        ?: prim.content
}
