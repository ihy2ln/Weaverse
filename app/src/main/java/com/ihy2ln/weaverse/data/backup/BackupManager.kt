package com.ihy2ln.weaverse.data.backup

import android.content.Context
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: WeaverseDatabase,
    private val settings: SettingsRepository,
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val backupDir get() = File(context.filesDir, "backups").also { it.mkdirs() }
    private val shareDir: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "backups").also { it.mkdirs() }

    /** Immediate DB+media snapshot used before every sync merge. Independent of the daily toggle. */
    suspend fun snapshotBeforeMerge(reason: String = "sync-merge"): File? = withContext(Dispatchers.IO) {
        runCatching {
            checkpointWal()
            val timestamp = System.currentTimeMillis()
            val dbFile = context.getDatabasePath("weaverse.db")
            val sources = BackupSources(
                dbFile = dbFile,
                walFile = File(dbFile.path + "-wal"),
                shmFile = File(dbFile.path + "-shm"),
                mediaDir = File(context.filesDir, "media"),
            )
            val dir = File(backupDir, "sync-snapshots").also { it.mkdirs() }
            val zip = File(dir, "pre-merge-$reason-$timestamp.zip")
            val manifest = json.encodeToString(
                BackupManifest(exportedAt = timestamp, version = 2, platforms = listOf("mobile")),
            )
            BackupArchives.packMobile(zip, sources, manifest)
            pruneSnapshots(dir, keep = 7)
            pruneBackupZips(keep = 7)
            zip
        }.getOrNull()
    }

    suspend fun maybeAutoBackup() = withContext(Dispatchers.IO) {
        val prefs = settings.preferences.first()
        if (!prefs.autoBackupEnabled) return@withContext
        val now = System.currentTimeMillis()
        val dayMs = 20L * 60 * 60 * 1000
        if (prefs.lastAutoBackupAt > 0L && now - prefs.lastAutoBackupAt < dayMs) return@withContext
        exportAutoBackup()
    }

    suspend fun exportAutoBackup() = withContext(Dispatchers.IO) {
        val prefs = settings.preferences.first()
        if (!prefs.autoBackupEnabled) return@withContext
        exportBackup()
        settings.setLastAutoBackupAt(System.currentTimeMillis())
    }

    /**
     * Keeps the newest [keep] backups. One backup is up to four files (mobile and PC zips,
     * each in the private and shareable folders), grouped by the timestamp in their names.
     */
    fun pruneBackupZips(keep: Int = 7) {
        val byExport = (backupDir.listFiles()?.toList().orEmpty() + shareDir.listFiles()?.toList().orEmpty())
            .filter { it.isFile }
            .mapNotNull { file -> BackupArchives.backupTimestamp(file.name)?.let { it to file } }
            .groupBy({ it.first }, { it.second })
        byExport.keys.sortedDescending().drop(keep)
            .forEach { stamp -> byExport.getValue(stamp).forEach { runCatching { it.delete() } } }
    }

    private fun pruneSnapshots(dir: File, keep: Int) {
        dir.listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(keep)
            ?.forEach { runCatching { it.delete() } }
    }

    suspend fun exportBackup(): BackupExportResult = withContext(Dispatchers.IO) {
        checkpointWal()
        val timestamp = System.currentTimeMillis()
        val dbFile = context.getDatabasePath("weaverse.db")
        val sources = BackupSources(
            dbFile = dbFile,
            walFile = File(dbFile.path + "-wal"),
            shmFile = File(dbFile.path + "-shm"),
            mediaDir = File(context.filesDir, "media"),
            datastoreDir = File(context.filesDir, "datastore"),
            mangaDir = File(context.filesDir, "manga"),
        )
        val manifest = json.encodeToString(
            BackupManifest(exportedAt = timestamp, version = 2, platforms = listOf("mobile", "pc")),
        )
        val mobileZip = File(backupDir, BackupArchives.mobileZipName(timestamp))
        val pcZip = File(backupDir, BackupArchives.pcZipName(timestamp))
        BackupArchives.packMobile(mobileZip, sources, manifest)
        BackupArchives.packPc(pcZip, sources, manifest)
        copyBeside(mobileZip, File(shareDir, mobileZip.name))
        copyBeside(pcZip, File(shareDir, pcZip.name))
        pruneBackupZips(keep = 7)
        BackupExportResult(
            mobileZip = File(shareDir, mobileZip.name).takeIf { it.exists() } ?: mobileZip,
            pcZip = File(shareDir, pcZip.name).takeIf { it.exists() } ?: pcZip,
        )
    }

    suspend fun restoreLatestBackup(): Unit = withContext(Dispatchers.IO) {
        val zips = backupDir.listFiles()?.filter { it.extension == "zip" }.orEmpty() +
            shareDir.listFiles()?.filter { it.extension == "zip" }.orEmpty()
        val latest = zips
            .sortedWith(
                compareByDescending<File> { it.name.contains("-mobile-") }
                    .thenByDescending { it.lastModified() },
            )
            .firstOrNull()
            ?: error("No backup found in ${backupDir.absolutePath}")
        restoreFrom(latest)
    }

    /**
     * Replaces the library with [zipFile]. Everything is unpacked into a staging folder and
     * checked first; the current library is then saved as a "pre-restore" snapshot, the
     * database is closed, and the staged files are moved in. The app must restart afterwards
     * (see [com.ihy2ln.weaverse.core.AppRestarter]); the closed database can't be used again.
     */
    suspend fun restoreFrom(zipFile: File) = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "restore-staging").also { it.deleteRecursively(); it.mkdirs() }
        try {
            val stagedDb = File(staging, "db").also { it.mkdirs() }
            val stagedMedia = File(staging, "media")
            val stagedManga = File(staging, "manga")
            val stagedSettings = File(staging, "datastore")
            ZipInputStream(FileInputStream(zipFile)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    val out: File? = when {
                        BackupArchives.isMobileDbEntry(name) -> File(stagedDb, name)
                        BackupArchives.isPcDbEntry(name) -> File(stagedDb, BackupArchives.MOBILE_DB)
                        BackupArchives.mediaRelativePath(name) != null ->
                            BackupArchives.childInside(stagedMedia, BackupArchives.mediaRelativePath(name)!!)
                        BackupArchives.mangaRelativePath(name) != null ->
                            BackupArchives.childInside(stagedManga, BackupArchives.mangaRelativePath(name)!!)
                        BackupArchives.settingsRelativePath(name) != null ->
                            BackupArchives.childInside(stagedSettings, BackupArchives.settingsRelativePath(name)!!)
                        else -> null
                    }
                    if (out != null && !entry.isDirectory) {
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { zip.copyTo(it) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            val restoredDb = File(stagedDb, BackupArchives.MOBILE_DB)
            require(restoredDb.isFile && restoredDb.length() > 0) { "That zip has no Weaverse library in it" }
            val backupVersion = schemaVersionOf(restoredDb)
            val appVersion = db.openHelper.readableDatabase.version
            require(backupVersion <= appVersion) {
                "That backup is from a newer Weaverse (library version $backupVersion, this app reads up to $appVersion). Update the app first."
            }
            // Keep the current library so a wrong restore can be undone.
            snapshotBeforeMerge("pre-restore")
            db.close()
            val liveDb = context.getDatabasePath("weaverse.db")
            listOf("", "-wal", "-shm", "-journal").forEach { File(liveDb.path + it).delete() }
            liveDb.parentFile?.mkdirs()
            listOf("", "-wal", "-shm").forEach { suffix ->
                File(stagedDb, BackupArchives.MOBILE_DB + suffix).takeIf { it.isFile }
                    ?.copyTo(File(liveDb.path + suffix), overwrite = true)
            }
            stagedMedia.takeIf { it.isDirectory }?.copyRecursively(File(context.filesDir, "media"), overwrite = true)
            stagedManga.takeIf { it.isDirectory }?.copyRecursively(File(context.filesDir, "manga"), overwrite = true)
            stagedSettings.takeIf { it.isDirectory }?.copyRecursively(File(context.filesDir, "datastore"), overwrite = true)
        } finally {
            staging.deleteRecursively()
        }
    }

    /** True once a restore has closed the database; only an app restart recovers. */
    fun libraryClosed(): Boolean = !db.isOpen

    /** Room keeps its schema version in SQLite's user_version, which also sits at byte 60 of the header. */
    private fun schemaVersionOf(dbFile: File): Int = runCatching {
        android.database.sqlite.SQLiteDatabase.openDatabase(
            dbFile.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY,
        ).use { it.version }
    }.getOrElse {
        java.io.RandomAccessFile(dbFile, "r").use { file ->
            require(file.length() >= 100) { "That zip's library file is damaged" }
            file.seek(60)
            file.readInt()
        }
    }

    private fun checkpointWal() {
        runCatching {
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        }
    }

    private fun copyBeside(source: File, dest: File) {
        if (source.canonicalPath == dest.canonicalPath) return
        dest.parentFile?.mkdirs()
        source.copyTo(dest, overwrite = true)
    }
}

@kotlinx.serialization.Serializable
data class BackupManifest(
    val exportedAt: Long,
    val version: Int,
    val platforms: List<String> = listOf("mobile"),
)
