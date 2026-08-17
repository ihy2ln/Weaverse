package com.ihy2ln.weaverse.sync

fun normalizeSyncBaseUrl(raw: String, defaultPort: Int = DEFAULT_SYNC_PORT): String {
    var host = raw.trim().trimEnd('/')
    if (host.isBlank()) return host
    if (!host.startsWith("http://") && !host.startsWith("https://")) {
        host = "http://$host"
    }
    val withoutScheme = host.removePrefix("http://").removePrefix("https://")
    if (!withoutScheme.contains(':')) {
        host = "$host:$defaultPort"
    }
    return host
}
