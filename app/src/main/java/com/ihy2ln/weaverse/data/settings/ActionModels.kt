package com.ihy2ln.weaverse.data.settings

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object ActionModelKeys {
    const val SCENE_BEAT = "scene_beat"
    const val SHORTEN = "shorten"
    const val EXTEND = "extend"
    const val REPLACE = "replace"
    const val SUMMARIZE = "summarize"
    const val REVIEW = "review"
    const val ROLEPLAY_SWIPE = "roleplay_swipe"
    const val WORKSHOP = "workshop"
    const val PROMPT_AI = "prompt_ai"
}

object ActionModels {
    private val json = Json { ignoreUnknownKeys = true }
    private val mapSerializer = MapSerializer(String.serializer(), String.serializer())

    fun decode(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            json.parseToJsonElement(raw).jsonObject.mapValues { it.value.jsonPrimitive.content }
        }.getOrDefault(emptyMap())
    }

    fun encode(map: Map<String, String>): String = json.encodeToString(mapSerializer, map)
}
