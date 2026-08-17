package com.ihy2ln.weaverse

import android.app.Application
import com.ihy2ln.weaverse.ai.prompt.PromptLibrarySeeder
import com.ihy2ln.weaverse.data.db.seed.DemoDataSeeder
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class WeaverseApp : Application() {
    @Inject lateinit var demoDataSeeder: DemoDataSeeder
    @Inject lateinit var promptLibrarySeeder: PromptLibrarySeeder

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch { demoDataSeeder.seedIfNeeded() }
        appScope.launch { promptLibrarySeeder.seedIfNeeded() }
    }
}
