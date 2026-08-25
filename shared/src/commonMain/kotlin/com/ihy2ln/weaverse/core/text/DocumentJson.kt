package com.ihy2ln.weaverse.core.text

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val docJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "type"
    serializersModule = documentSerializersModule
}

fun Document.toJson(): String = docJson.encodeToString(this)

fun documentFromJson(json: String?): Document {
    if (json.isNullOrBlank()) return Document.empty()
    return runCatching { docJson.decodeFromString<Document>(json) }.getOrDefault(Document.empty())
}
