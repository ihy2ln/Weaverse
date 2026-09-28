package com.ihy2ln.weaverse

import android.app.Application
import com.ihy2ln.weaverse.data.export.SampleBookImporter
import com.ihy2ln.weaverse.data.repo.CodexRepository
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
    @Inject lateinit var syncCoordinator: SyncCoordinator
    @Inject lateinit var sampleBookImporter: SampleBookImporter
    @Inject lateinit var codexRepository: CodexRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            seeder.seedIfEmpty()
            sampleBookImporter.importBundledIsekaiGachaIfMissing()
            // Fold any per-book Codex rows (seed data, older versions) into the shared library
            // up front, so AI context and search see them before the Codex tab is ever opened.
            runCatching { codexRepository.ensureGlobalAndMigrate() }
            syncCoordinator.suggestedWebUrl()
        }
    }
}
