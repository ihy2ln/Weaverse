package com.ihy2ln.weaverse.desktop

import java.awt.Desktop
import java.net.URI
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val dataArg = args.toList()
        .firstOrNull { it.startsWith("--data=") }
        ?.removePrefix("--data=")
        ?: System.getenv("WEAVERSE_DATA")
    val noBrowser = args.contains("--no-browser") ||
        System.getenv("WEAVERSE_NO_BROWSER") == "1"

    val dataDir = DesktopPaths.resolveDataDir(dataArg)
    val config = DesktopConfigStore.load(DesktopPaths.configFile(dataDir))
    val server = SyncHttpServer(dataDir, config)
    server.start()

    val lan = SyncHttpServer.localLanAddresses().joinToString(", ").ifBlank { "this-pc-ip" }
        val scheme = if (config.tls) "https" else "http"
        println(
            """
        |Weaverse Desktop v${config.appVersion}
        |Data dir : ${dataDir.absolutePath}
        |
        |Open the web version (sync hub):
        |  $scheme://127.0.0.1:${config.port}/
        |  $scheme://$lan:${config.port}/
        |
        |The single sync password is shown on that web page.
        |Import a Novelcrafter ZIP (novel.md or novel.docx) via Import on the hub, or drop it in:
        |  ${DesktopPaths.importDir(dataDir).absolutePath}
        |In the Android app: Settings → Import / Export, or Open web sync → Push or Pull.
        |
        |Keep this window open. Press Ctrl+C to stop.
        """.trimMargin(),
        )

        if (config.openBrowser && !noBrowser) {
            runCatching {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browse(URI("$scheme://127.0.0.1:${config.port}/"))
                }
            }
        }

    Runtime.getRuntime().addShutdownHook(
        Thread {
            runCatching { server.stop() }
        },
    )

    // Block forever
    try {
        Thread.currentThread().join()
    } catch (_: InterruptedException) {
        server.stop()
        exitProcess(0)
    }
}
