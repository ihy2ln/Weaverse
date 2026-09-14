package com.ihy2ln.weaverse.sync

/**
 * Minimal SQL facade so the shared sync schema + record-merge code can run on
 * both hosts: the desktop (JDBC, via [JdbcSyncSql]) and Android (Room's
 * SupportSQLiteDatabase, wrapped by the app module).
 */
interface SyncSql {
    fun exec(sql: String, vararg binds: Any?)

    /** Runs a SELECT and returns columns + raw rows. Null values come back as null. */
    fun select(sql: String, vararg binds: Any?): QueryResult

    /** Column names of a table (unqualified or prefixed, e.g. "incoming.scenes"). */
    fun columnsOf(table: String): List<String>

    /** Executes [block] inside a transaction, committing on success. */
    fun transaction(block: () -> Unit)

    data class QueryResult(val columns: List<String>, val rows: List<List<Any?>>) {
        fun rowObjects(): List<Map<String, Any?>> = rows.map { row ->
            columns.indices.associate { columns[it] to row[it] }
        }
    }
}

/** JDBC-backed [SyncSql] for the desktop host and JVM tests. */
class JdbcSyncSql(private val conn: java.sql.Connection) : SyncSql {

    override fun exec(sql: String, vararg binds: Any?) {
        if (binds.isEmpty()) {
            conn.createStatement().use { it.execute(sql) }
        } else {
            conn.prepareStatement(sql).use { ps ->
                binds.forEachIndexed { i, v -> ps.setObject(i + 1, v) }
                ps.execute()
            }
        }
    }

    override fun select(sql: String, vararg binds: Any?): SyncSql.QueryResult {
        return if (binds.isEmpty()) {
            conn.createStatement().use { st -> readResult(st.executeQuery(sql)) }
        } else {
            conn.prepareStatement(sql).use { ps ->
                binds.forEachIndexed { i, v -> ps.setObject(i + 1, v) }
                readResult(ps.executeQuery())
            }
        }
    }

    override fun columnsOf(table: String): List<String> {
        val pragma = if ('.' in table) {
            val schema = table.substringBefore('.')
            val name = table.substringAfter('.')
            "PRAGMA $schema.table_info($name)"
        } else {
            "PRAGMA table_info($table)"
        }
        return select(pragma).rows.mapNotNull { it.getOrNull(1) as? String }
    }

    override fun transaction(block: () -> Unit) {
        conn.autoCommit = false
        try {
            block()
            conn.commit()
        } catch (t: Throwable) {
            conn.rollback()
            throw t
        } finally {
            conn.autoCommit = true
        }
    }
}

private fun readResult(rs: java.sql.ResultSet): SyncSql.QueryResult = rs.use {
    val meta = rs.metaData
    val columns = (1..meta.columnCount).map { meta.getColumnLabel(it) }
    val rows = buildList {
        while (rs.next()) {
            add(
                columns.indices.map { c ->
                    val v = rs.getObject(c + 1)
                    when (v) {
                        is java.sql.Blob, is java.sql.Clob -> null
                        else -> v
                    }
                },
            )
        }
    }
    SyncSql.QueryResult(columns, rows)
}
