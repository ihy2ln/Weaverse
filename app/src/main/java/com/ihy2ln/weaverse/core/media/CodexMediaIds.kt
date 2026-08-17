package com.ihy2ln.weaverse.core.media

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

/** Encode/decode multi media ids stored in [com.ihy2ln.weaverse.data.db.entities.CodexEntryEntity.imageMediaId]. */
object CodexMediaIds {
    fun parse(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val trimmed = raw.trim()
        if (trimmed.startsWith("[")) {
            return runCatching { json.decodeFromString<List<String>>(trimmed) }
                .getOrDefault(emptyList())
                .filter { it.isNotBlank() }
        }
        return listOf(trimmed)
    }

    fun encode(ids: List<String>): String? {
        val clean = ids.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        return when {
            clean.isEmpty() -> null
            clean.size == 1 -> clean.first()
            else -> json.encodeToString(clean)
        }
    }
}
