package com.ihy2ln.weaverse

import android.app.Application
import com.ihy2ln.weaverse.data.export.SampleBookImporter
import com.ihy2ln.weaverse.data.seed.AdamsHavenRpgSeeder
import com.ihy2ln.weaverse.data.seed.DatabaseSeeder
import com.ihy2ln.weaverse.data.sync.SyncCoordinator
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class WeaverseApp : Application() {
    @Inject lateinit var seeder: DatabaseSeeder
    @Inject lateinit var adamsHavenRpgSeeder: AdamsHavenRpgSeeder
    @Inject lateinit var syncCoordinator: SyncCoordinator
    @Inject lateinit var sampleBookImporter: SampleBookImporter

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            seeder.seedIfEmpty()
            runCatching { sampleBookImporter.importBundledIsekaiGachaIfMissing() }
            adamsHavenRpgSeeder.seedIfMissing()
            syncCoordinator.suggestedWebUrl()
        }
    }
}
