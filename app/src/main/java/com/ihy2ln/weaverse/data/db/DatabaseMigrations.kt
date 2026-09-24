package com.ihy2ln.weaverse.data.db

import androidx.room.migration.Migration

/**
 * Every schema step from [FIRST_MIGRATABLE_VERSION] up to [WeaverseDatabase.VERSION].
 *
 * To change the schema: bump [WeaverseDatabase.VERSION], add a `Migration(n, n + 1)` here that
 * transforms the old tables in place, and commit the new `app/schemas/<n+1>.json`. Never rely on
 * destructive fallback for a released version — it wipes the user's library without a word.
 *
 * Example:
 * ```
 * val MIGRATION_5_6 = object : Migration(5, 6) {
 *     override fun migrate(db: SupportSQLiteDatabase) {
 *         db.execSQL("ALTER TABLE books ADD COLUMN subtitle TEXT NOT NULL DEFAULT ''")
 *     }
 * }
 * ```
 */
object DatabaseMigrations {
    /**
     * Versions before this shipped with `exportSchema = false` and no migrations, so their
     * exact table layout is not recorded anywhere. They are the only ones still allowed to
     * fall back to a rebuild, and [PreMigrationBackup] copies the file aside first.
     */
    const val FIRST_MIGRATABLE_VERSION = 5

    val ALL: Array<Migration> = arrayOf(
        // MIGRATION_5_6, ...
    )

    /** Legacy versions that may still be rebuilt from scratch (after a backup). */
    val LEGACY_VERSIONS: IntArray = (1 until FIRST_MIGRATABLE_VERSION).toList().toIntArray()

    /**
     * The `from -> from + 1` steps with no migration. Empty means every supported upgrade
     * path is covered.
     */
    fun missingSteps(
        steps: List<Pair<Int, Int>>,
        from: Int = FIRST_MIGRATABLE_VERSION,
        to: Int = WeaverseDatabase.VERSION,
    ): List<Pair<Int, Int>> {
        val covered = steps.toSet()
        return (from until to).map { it to it + 1 }.filterNot { it in covered }
    }
}
