package com.ihy2ln.weaverse

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.ihy2ln.weaverse.core.crash.CrashLog
import com.ihy2ln.weaverse.core.manga.MangaDownloadRepository
import com.ihy2ln.weaverse.core.roleplay.DailyCharacterGenerator
import com.ihy2ln.weaverse.data.backup.AutoBackupScheduler
import com.ihy2ln.weaverse.data.backup.AutoBackupWorker
import com.ihy2ln.weaverse.data.backup.BackupManager
import com.ihy2ln.weaverse.data.export.SampleBookImporter
import com.ihy2ln.weaverse.data.seed.DatabaseSeeder
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import com.ihy2ln.weaverse.data.sync.SyncCoordinator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import eu.kanade.tachiyomi.network.NetworkHelper
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingleton

@HiltAndroidApp
class WeaverseApp : Application(), Configuration.Provider {
    @Inject lateinit var seeder: DatabaseSeeder
    @Inject lateinit var syncCoordinator: SyncCoordinator
    @Inject lateinit var sampleBookImporter: SampleBookImporter
    @Inject lateinit var dailyCharacterGenerator: DailyCharacterGenerator
    @Inject lateinit var crashLog: CrashLog
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var backupManager: BackupManager
    @Inject lateinit var mangaDownloadRepository: MangaDownloadRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker? {
                        if (workerClassName == AutoBackupWorker::class.java.name) {
                            return AutoBackupWorker(appContext, workerParameters, backupManager)
                        }
                        if (workerClassName == com.ihy2ln.weaverse.core.manga.MangaDownloadWorker::class.java.name) {
                            return com.ihy2ln.weaverse.core.manga.MangaDownloadWorker(
                                appContext,
                                workerParameters,
                                mangaDownloadRepository,
                            )
                        }
                        return null
                    }
                },
            )
            .build()

    override fun onCreate() {
        instance = this
        super.onCreate()
        // The Games process only hosts the Godot runtime. Seeding, sync, backups and the
        // extension host belong to the main process; running them twice would put two
        // processes on one database.
        if (isGameProcess()) return
        // Extension source classes use Mihon's small service locator at runtime.
        // Register only the host services that are part of the public source ABI.
        Injekt.importModule(object : InjektModule {
            override fun InjektRegistrar.registerInjectables() {
                addSingleton<Application>(this@WeaverseApp)
                addSingleton<Context>(this@WeaverseApp)
                addSingleton(NetworkHelper(this@WeaverseApp))
            }
        })
        crashLog.install()
        appScope.launch {
            seeder.seedIfEmpty()
            sampleBookImporter.importBundledIsekaiGachaIfMissing()
            syncCoordinator.suggestedWebUrl()
            runCatching { dailyCharacterGenerator.generateIfDue() }
            runCatching { backupManager.maybeAutoBackup() }
            if (settings.preferences.first().autoBackupEnabled) {
                AutoBackupScheduler.ensure(this@WeaverseApp)
            }
        }
    }

    private fun isGameProcess(): Boolean {
        val name = if (android.os.Build.VERSION.SDK_INT >= 28) {
            getProcessName()
        } else {
            (getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager).runningAppProcesses
                ?.firstOrNull { it.pid == android.os.Process.myPid() }?.processName
        }
        return name != null && (name.endsWith(":game") || name.endsWith(":phoenix"))
    }

    companion object {
        lateinit var instance: WeaverseApp
            private set
    }
}
