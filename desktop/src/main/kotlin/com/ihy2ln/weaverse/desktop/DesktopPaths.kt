package com.ihy2ln.weaverse.desktop

import java.io.File

object DesktopPaths {
    /** Prefer Weaverse/data next to the JAR (S:\AI\Novel\Weaverse), else ~/.weaverse */
    fun resolveDataDir(explicit: String?): File {
        if (!explicit.isNullOrBlank()) {
            return File(explicit).also { it.mkdirs() }
        }
        val jarDir = runCatching {
            File(
                DesktopPaths::class.java.protectionDomain.codeSource.location.toURI(),
            ).parentFile
        }.getOrNull()
        val besideJar = jarDir?.resolve("data")
        val weaverseSibling = jarDir?.parentFile?.resolve("data")
        val candidates = listOfNotNull(
            besideJar,
            weaverseSibling,
            File("Weaverse/data"),
            File("data"),
            File(System.getProperty("user.home"), ".weaverse"),
        )
        val existing = candidates.firstOrNull { it.exists() }
        val chosen = existing ?: candidates.first()
        chosen.mkdirs()
        File(chosen, "media").mkdirs()
        File(chosen, "incoming").mkdirs()
        return chosen
    }

    fun dbFile(dataDir: File): File = File(dataDir, "weaverse.db")
    fun mediaDir(dataDir: File): File = File(dataDir, "media").also { it.mkdirs() }
    fun latestSyncZip(dataDir: File): File = File(dataDir, "latest-sync.zip")
    fun configFile(dataDir: File): File = File(dataDir, "sync-config.json")
    fun tlsFile(dataDir: File): File = File(dataDir, "tls.p12")
    fun backupsDir(dataDir: File): File = File(dataDir, "backups").also { it.mkdirs() }
    /** Drop Novelcrafter ZIPs here (sibling of data/): Weaverse/import */
    fun importDir(dataDir: File): File = File(dataDir.parentFile ?: dataDir, "import").also { it.mkdirs() }
}
