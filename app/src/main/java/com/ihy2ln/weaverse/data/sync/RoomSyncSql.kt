package com.ihy2ln.weaverse.data.sync

import androidx.sqlite.db.SupportSQLiteDatabase
import com.ihy2ln.weaverse.sync.SyncSql

/**
 * [SyncSql] implementation backed by Room's underlying SQLite database.
 * Raw writes through this wrapper still fire Room's invalidation triggers,
 * so live queries refresh automatically after a merge — no app restart.
 */
class RoomSyncSql(private val db: SupportSQLiteDatabase) : SyncSql {

    override fun exec(sql: String, vararg binds: Any?) {
        if (binds.isEmpty()) {
            db.execSQL(sql)
        } else {
            db.execSQL(sql, binds)
        }
    }

    override fun select(sql: String, vararg binds: Any?): SyncSql.QueryResult {
        db.query(sql, binds).use { cursor ->
            val columns = cursor.columnNames.toList()
            val rows = buildList {
                while (cursor.moveToNext()) {
                    add(columns.indices.map { c ->
                        when (cursor.getType(c)) {
                            android.database.Cursor.FIELD_TYPE_NULL -> null
                            android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(c)
                            android.database.Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(c)
                            android.database.Cursor.FIELD_TYPE_BLOB -> null
                            else -> cursor.getString(c)
                        }
                    })
                }
            }
            return SyncSql.QueryResult(columns, rows)
        }
    }

    override fun columnsOf(table: String): List<String> {
        val schema = table.substringBefore('.')
        val name = table.substringAfter('.')
        db.query("PRAGMA $schema.table_info($name)").use { cursor ->
            val idx = cursor.getColumnIndex("name")
            val out = mutableListOf<String>()
            while (cursor.moveToNext()) out += cursor.getString(idx)
            return out
        }
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
}
