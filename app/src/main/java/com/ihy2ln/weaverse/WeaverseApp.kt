package com.ihy2ln.weaverse

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.ihy2ln.weaverse.core.crash.CrashLog
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

@HiltAndroidApp
class WeaverseApp : Application(), Configuration.Provider {
    @Inject lateinit var seeder: DatabaseSeeder
    @Inject lateinit var syncCoordinator: SyncCoordinator
    @Inject lateinit var sampleBookImporter: SampleBookImporter
    @Inject lateinit var dailyCharacterGenerator: DailyCharacterGenerator
    @Inject lateinit var crashLog: CrashLog
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var backupManager: BackupManager

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
                        return null
                    }
                },
            )
            .build()

    override fun onCreate() {
        instance = this
        super.onCreate()
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

    companion object {
        lateinit var instance: WeaverseApp
            private set
    }
}
