package com.ihy2ln.weaverse.core.media

import java.io.File
import java.net.URI

/**
 * Path heuristics shared by Write persist and Read rendering so a mediaId always
 * resolves to something Coil / ExoPlayer can load, or is skipped if empty.
 */
object MediaPaths {
    fun storedMediaPathOrNull(path: String?): String? {
        val trimmed = path?.trim().orEmpty()
        return trimmed.takeIf { it.isNotEmpty() }
    }

    fun isRemoteOrContentUri(path: String): Boolean {
        val lower = path.trim().lowercase()
        return lower.startsWith("content:") ||
            lower.startsWith("http://") ||
            lower.startsWith("https://") ||
            lower.startsWith("file:")
    }

    fun localFileIfReadable(path: String): File? {
        val stored = storedMediaPathOrNull(path) ?: return null
        val file = if (stored.lowercase().startsWith("file:")) {
            runCatching { File(URI(stored)) }.getOrNull() ?: return null
        } else if (isRemoteOrContentUri(stored)) {
            return null
        } else {
            File(stored)
        }
        return file.takeIf { it.exists() && it.isFile && it.length() > 0L }
    }

    fun mediaLoadTarget(path: String): Any = localFileIfReadable(path) ?: path
}
