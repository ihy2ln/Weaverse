package com.ihy2ln.weaverse.core.text

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val aliasesJson = Json { ignoreUnknownKeys = true }

fun decodeAliases(json: String): List<String> =
    runCatching { aliasesJson.decodeFromString<List<String>>(json) }
        .getOrDefault(emptyList())
        .filter { it.isNotBlank() }

fun encodeAliases(aliases: List<String>): String =
    aliasesJson.encodeToString(aliases.map { it.trim() }.filter { it.isNotBlank() })
