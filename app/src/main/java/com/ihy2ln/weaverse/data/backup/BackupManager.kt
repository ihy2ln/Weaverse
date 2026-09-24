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

    fun pruneBackupZips(keep: Int = 7) {
        val all = (backupDir.listFiles()?.toList().orEmpty() + shareDir.listFiles()?.toList().orEmpty())
            .filter { it.isFile && it.extension.equals("zip", ignoreCase = true) }
            .sortedByDescending { it.lastModified() }
        all.drop(keep).forEach { runCatching { it.delete() } }
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

    suspend fun restoreFrom(zipFile: File) = withContext(Dispatchers.IO) {
        ZipInputStream(FileInputStream(zipFile)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    BackupArchives.isMobileDbEntry(name) -> {
                        val suffix = name.removePrefix("weaverse.db")
                        val out = File(context.getDatabasePath("weaverse.db").path + suffix)
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { zip.copyTo(it) }
                    }
                    BackupArchives.isPcDbEntry(name) -> {
                        val out = context.getDatabasePath("weaverse.db")
                        FileOutputStream(out).use { zip.copyTo(it) }
                    }
                    BackupArchives.mediaRelativePath(name) != null -> {
                        val relative = BackupArchives.mediaRelativePath(name)!!
                        val outFile = File(File(context.filesDir, "media"), relative)
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { zip.copyTo(it) }
                    }
                    BackupArchives.settingsRelativePath(name) != null -> {
                        val relative = BackupArchives.settingsRelativePath(name)!!
                        val outFile = File(File(context.filesDir, "datastore"), relative)
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { zip.copyTo(it) }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
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
