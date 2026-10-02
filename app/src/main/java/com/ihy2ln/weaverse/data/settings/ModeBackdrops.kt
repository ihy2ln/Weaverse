package com.ihy2ln.weaverse.data.settings

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * The backgrounds each mode's pages sit on, keyed by mode ("home", "novel", "rpg", "games",
 * "browser", "manga", "notes"). Each entry is "art:<key>" (Weaverse key art), "video:<key>"
 * (a built-in focus video), "media:<id>" (a picture or video the user added) or "wallpaper"
 * (the appearance wallpaper). An empty list means the mode's own key art.
 */
object ModeBackdrops {
    const val ART = "art:"
    const val MEDIA = "media:"
    const val VIDEO = "video:"
    const val WALLPAPER = "wallpaper"

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), ListSerializer(String.serializer()))

    fun decode(text: String?): Map<String, List<String>> =
        if (text.isNullOrBlank()) emptyMap() else runCatching { json.decodeFromString(serializer, text) }.getOrDefault(emptyMap())

    fun encode(map: Map<String, List<String>>): String =
        json.encodeToString(serializer, map.mapValues { (_, v) -> v.distinct() }.filterValues { it.isNotEmpty() })
}
