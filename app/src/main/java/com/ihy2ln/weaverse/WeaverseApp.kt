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
    @Inject lateinit var adamsHavenCodexPack: com.ihy2ln.weaverse.data.seed.AdamsHavenCodexPack
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
            // Each step stands alone: one failing (a bad seed, a broken sample book) must not
            // skip the rest — least of all backup scheduling — or crash the app at launch.
            startupStep("seed") { seeder.seedIfEmpty() }
            startupStep("sample book") { sampleBookImporter.importBundledIsekaiGachaIfMissing() }
            // After the sample book, so its older entries are updated rather than duplicated.
            startupStep("adams haven codex") { adamsHavenCodexPack.applyIfNew() }
            startupStep("sync url") { syncCoordinator.suggestedWebUrl() }
            startupStep("daily character") { dailyCharacterGenerator.generateIfDue() }
            startupStep("auto-backup") { backupManager.maybeAutoBackup() }
            startupStep("backup schedule") {
                if (settings.preferences.first().autoBackupEnabled) {
                    AutoBackupScheduler.ensure(this@WeaverseApp)
                }
            }
        }
    }

    private suspend fun startupStep(name: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Throwable) {
            android.util.Log.e("Weaverse", "Startup step '$name' failed", e)
        }
    }

    companion object {
        lateinit var instance: WeaverseApp
            private set
    }
}
