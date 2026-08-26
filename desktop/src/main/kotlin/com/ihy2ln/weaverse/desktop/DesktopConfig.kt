package com.ihy2ln.weaverse.desktop

import com.ihy2ln.weaverse.sync.DEFAULT_SYNC_PORT
import com.ihy2ln.weaverse.sync.SyncAuth
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class DesktopConfig(
    val deviceId: String = SyncAuth.newDeviceId(),
    val deviceName: String = "Weaverse Desktop",
    val port: Int = DEFAULT_SYNC_PORT,
    val pairPin: String = SyncAuth.newPairPin(),
    val appVersion: String = "1.2.0-beta",
    val openBrowser: Boolean = true,
    val allowRemote: Boolean = true,
)

object DesktopConfigStore {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(file: File): DesktopConfig {
        if (!file.exists()) {
            val created = DesktopConfig()
            save(file, created)
            return created
        }
        return runCatching {
            json.decodeFromString(DesktopConfig.serializer(), file.readText())
        }.getOrElse {
            val created = DesktopConfig()
            save(file, created)
            created
        }
    }

    fun save(file: File, config: DesktopConfig) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(config))
    }
}
