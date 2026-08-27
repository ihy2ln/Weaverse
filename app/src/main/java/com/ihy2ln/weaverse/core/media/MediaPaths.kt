package com.ihy2ln.weaverse.core.media

import java.io.File

/** True when [path] is a content/file/http URI rather than a filesystem path. */
fun isRemoteOrContentUri(path: String): Boolean {
    val lower = path.trim().lowercase()
    return lower.startsWith("content:") ||
        lower.startsWith("file:") ||
        lower.startsWith("http:") ||
        lower.startsWith("https:")
}

fun storedMediaPathOrNull(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() }

fun localFileIfReadable(path: String): File? {
    val file = File(path)
    return file.takeIf { it.isFile && it.length() > 0L }
}

/**
 * Pick a Coil/ExoPlayer load target for a persisted media path.
 * Returns a readable [File], a URI string, or null when nothing can be shown.
 */
fun mediaLoadTarget(path: String?): Any? {
    val raw = storedMediaPathOrNull(path) ?: return null
    if (isRemoteOrContentUri(raw)) return raw
    return localFileIfReadable(raw) ?: localFileIfReadable(File(raw).absolutePath)
}
