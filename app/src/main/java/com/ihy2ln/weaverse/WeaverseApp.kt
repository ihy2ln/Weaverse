package com.ihy2ln.weaverse

import android.app.Application
import com.ihy2ln.weaverse.ai.WeaverseAiLog
import com.ihy2ln.weaverse.data.export.SampleBookImporter
import com.ihy2ln.weaverse.data.seed.DatabaseSeeder
import com.ihy2ln.weaverse.data.sync.SyncCoordinator
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class WeaverseApp : Application() {
    @Inject lateinit var seeder: DatabaseSeeder
    @Inject lateinit var syncCoordinator: SyncCoordinator
    @Inject lateinit var sampleBookImporter: SampleBookImporter

    // Startup work must never take the process down: a failed seed or a half-written bundled
    // sample is recoverable, a crash on launch is not.
    private val startupErrors = CoroutineExceptionHandler { _, error ->
        WeaverseAiLog.e("startup work failed", error)
    }
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + startupErrors)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            runCatching { seeder.seedIfEmpty() }
                .onFailure { WeaverseAiLog.e("seedIfEmpty failed", it) }
            runCatching { sampleBookImporter.importBundledIsekaiGachaIfMissing() }
                .onFailure { WeaverseAiLog.e("bundled sample import failed", it) }
            runCatching { syncCoordinator.suggestedWebUrl() }
                .onFailure { WeaverseAiLog.e("sync warm-up failed", it) }
        }
    }
}
