package com.ihy2ln.weaverse

/** Debug-only access for end-to-end tests using the same database instance as the running UI. */
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface BrowserQaEntryPoint {
    fun database(): com.ihy2ln.weaverse.data.db.WeaverseDatabase
    fun settings(): com.ihy2ln.weaverse.data.settings.SettingsRepository
}
