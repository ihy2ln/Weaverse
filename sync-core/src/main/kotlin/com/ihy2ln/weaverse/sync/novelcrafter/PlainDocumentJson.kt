package com.ihy2ln.weaverse.sync.novelcrafter

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Minimal Document JSON matching Android `Document.toJson()` (classDiscriminator = type). */
object PlainDocumentJson {
    private const val PARAGRAPH = "com.ihy2ln.weaverse.core.text.Paragraph"
    private const val MEDIA = "com.ihy2ln.weaverse.core.text.MediaBlock"

    fun fromPlainText(text: String, blockId: String = "p-1"): String {
        if (text.isBlank()) return """{"blocks":[]}"""
        return buildJsonObject {
            put("blocks", buildJsonArray { add(paragraph(blockId, text)) })
        }.toString()
    }

    fun withLeadingImage(
        mediaId: String,
        caption: String,
        prose: String,
    ): String = buildJsonObject {
        put(
            "blocks",
            buildJsonArray {
                add(mediaBlock("m-art", mediaId, caption))
                if (prose.isNotBlank()) add(paragraph("p-1", prose))
            },
        )
    }.toString()

    fun mediaOnly(mediaId: String, caption: String, extraText: String = ""): String = buildJsonObject {
        put(
            "blocks",
            buildJsonArray {
                add(mediaBlock("m-art", mediaId, caption))
                if (extraText.isNotBlank()) add(paragraph("p-1", extraText))
            },
        )
    }.toString()

    private fun paragraph(id: String, text: String): JsonObject = buildJsonObject {
        put("type", PARAGRAPH)
        put("id", id)
        put("spans", JsonArray(listOf(buildJsonObject { put("text", JsonPrimitive(text)) })))
        put("align", "Start")
        put("indentLevel", 0)
    }

    private fun mediaBlock(id: String, mediaId: String, caption: String): JsonObject = buildJsonObject {
        put("type", MEDIA)
        put("id", id)
        put("mediaId", mediaId)
        put("kind", "Image")
        put("widthPercent", 100.0)
        put("align", "Center")
        put(
            "caption",
            if (caption.isBlank()) {
                JsonArray(emptyList())
            } else {
                JsonArray(listOf(buildJsonObject { put("text", JsonPrimitive(caption)) }))
            },
        )
        put("autoplay", false)
        put("loop", false)
        put("muted", true)
        put("gridCol", -1)
        put("gridRow", -1)
        put("gridColSpan", 2)
        put("gridRowSpan", 2)
        put("collapsed", false)
    }
}
