package com.ihy2ln.weaverse.data.db

import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * Copies the database aside before Room upgrades it, so a failed or destructive
 * migration never costs the user their library. Runs before the database is opened.
 */
object PreMigrationBackup {
    private const val KEEP = 3

    /** Returns the backup file, or null when no upgrade is pending or nothing was copied. */
    fun backupIfUpgrading(dbFile: File, backupDir: File, targetVersion: Int): File? =
        runCatching {
            if (!dbFile.exists()) return null
            val onDisk = readUserVersion(dbFile) ?: return null
            if (onDisk <= 0 || onDisk >= targetVersion) return null
            backupDir.mkdirs()
            val stamp = System.currentTimeMillis()
            val dest = File(backupDir, "pre-migration-v$onDisk-to-v$targetVersion-$stamp.db")
            dbFile.copyTo(dest, overwrite = true)
            listOf("-wal", "-shm").forEach { suffix ->
                val extra = File(dbFile.path + suffix)
                if (extra.exists()) extra.copyTo(File(dest.path + suffix), overwrite = true)
            }
            prune(backupDir)
            dest
        }.getOrNull()

    private fun readUserVersion(dbFile: File): Int? = runCatching {
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
    }.getOrNull()

    private fun prune(backupDir: File) {
        backupDir.listFiles { f -> f.name.startsWith("pre-migration-") && f.name.endsWith(".db") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(KEEP)
            ?.forEach { old ->
                old.delete()
                File(old.path + "-wal").delete()
                File(old.path + "-shm").delete()
            }
    }
}
