package com.ihy2ln.weaverse.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema migrations for [WeaverseDatabase]. Every `version` bump needs a matching
 * entry here — there is deliberately no destructive fallback for current schemas,
 * because falling back wipes the user's entire library on upgrade.
 * Exported schemas live in `app/schemas/` for reference when writing the next one.
 */
object WeaverseMigrations {
    /** v3 → v4: per-entry mention tracking flags on codex entries. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE codex_entries ADD COLUMN trackMentions INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE codex_entries ADD COLUMN caseSensitiveMatching INTEGER NOT NULL DEFAULT 0")
        }
    }

    /** v4 → v5: active-prompt-per-type flag. */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE prompts ADD COLUMN isDefault INTEGER NOT NULL DEFAULT 0")
        }
    }

    val ALL = arrayOf(MIGRATION_3_4, MIGRATION_4_5)

    /** Pre-transplant schemas (the old from-scratch rewrite) have no upgrade path. */
    val DESTRUCTIVE_FROM = intArrayOf(1, 2)
}
