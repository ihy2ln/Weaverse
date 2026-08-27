package com.ihy2ln.weaverse.data.sync

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ihy2ln.weaverse.sync.SyncSql

/** Room-backed [SyncSql] so record-level merge runs on the open live connection. */
class RoomSyncSql(private val db: SupportSQLiteDatabase) : SyncSql {

    override fun exec(sql: String, vararg binds: Any?) {
        if (binds.isEmpty()) {
            db.execSQL(sql)
        } else {
            db.execSQL(sql, binds)
        }
    }

    override fun select(sql: String, vararg binds: Any?): SyncSql.QueryResult {
        val cursor = if (binds.isEmpty()) db.query(sql) else db.query(sql, binds)
        return cursor.use { readCursor(it) }
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
        db.beginTransaction()
        try {
            block()
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun readCursor(cursor: Cursor): SyncSql.QueryResult {
        val columns = cursor.columnNames.toList()
        val rows = buildList {
            while (cursor.moveToNext()) {
                add(
                    columns.indices.map { i ->
                        when (cursor.getType(i)) {
                            Cursor.FIELD_TYPE_NULL -> null
                            Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                            Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(i)
                            Cursor.FIELD_TYPE_STRING -> cursor.getString(i)
                            Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(i)
                            else -> cursor.getString(i)
                        }
                    },
                )
            }
        }
        return SyncSql.QueryResult(columns, rows)
    }
}
