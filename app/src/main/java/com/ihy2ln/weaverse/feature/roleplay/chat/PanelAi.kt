package com.ihy2ln.weaverse.feature.roleplay.chat

import android.graphics.Bitmap
import android.util.Base64
import com.ihy2ln.weaverse.ai.AiGenerationService
import com.ihy2ln.weaverse.ai.ImageAttachment
import com.ihy2ln.weaverse.ai.context.AssembledPrompt
import com.ihy2ln.weaverse.core.media.ImageOps
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.math.min

/** One text region the AI found in a picture, with its translation. */
@Serializable
private data class RegionDto(
    val x: Int = 0,
    val y: Int = 0,
    val w: Int = 0,
    val h: Int = 0,
    val original: String = "",
    val text: String = "",
    val translation: String = "",
)

private val regionJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

data class AiPanelDetection(
    val boxes: List<android.graphics.RectF> = emptyList(),
    val error: String? = null,
)

/**
 * Vision-AI helpers for the Storyboard: panel separation and speech-text
 * reading/translation. Both send a downscaled copy of the picture as an
 * image attachment and expect a strict JSON array back.
 */
object PanelAi {

    /** Downscale + encode for the vision request (long edge ≤ 1100 px). */
    fun imageAttachmentFor(path: String): ImageAttachment? {
        val file = File(path)
        if (!file.exists() || file.length() == 0L) return null
        val bitmap: Bitmap = ImageOps.loadBitmap(path, maxDim = 1100) ?: return null
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 86, it) }.toByteArray()
        return ImageAttachment(mimeType = "image/jpeg", base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP))
    }

    private suspend fun ask(ai: AiGenerationService, modelRef: String, instruction: String, path: String): String? {
        val attachment = imageAttachmentFor(path) ?: return null
        val result = ai.complete(
            userMessage = instruction,
            assembled = AssembledPrompt(
                systemBlocks = listOf(
                    "You are a precise comic/manga analysis engine. Always answer with a raw JSON array only — " +
                        "no prose, no markdown fences, no explanation.",
                ),
                messages = emptyList(),
                usedEntries = emptyList(),
                tokenBreakdown = emptyList(),
            ),
            modelRef = modelRef,
            maxTokens = 2048,
            temperature = 0.1,
            imageAttachments = listOf(attachment),
        )
        val text = result.text.trim()
        if (text.isBlank()) return null
        return text
    }

    private fun extractJsonArray(raw: String): String? {
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start < 0 || end <= start) return null
        return raw.substring(start, end + 1)
    }

    /**
     * AI panel separation: returns normalized boxes (0..1) for every panel on
     * the page, or null when the model/answer is unusable (caller falls back
     * to the gutter heuristic).
     */
    suspend fun detectPanels(ai: AiGenerationService, modelRef: String, path: String): List<android.graphics.RectF>? =
        detectPanelsDetailed(ai, modelRef, path)
            .takeIf { it.error == null }
            ?.boxes
            ?.takeIf { it.isNotEmpty() }

    suspend fun detectPanelsDetailed(
        ai: AiGenerationService,
        modelRef: String,
        path: String,
    ): AiPanelDetection {
        val file = File(path)
        if (!file.isFile || file.length() == 0L) {
            return AiPanelDetection(error = "The source image could not be read.")
        }
        val raw = runCatching {
            ask(
            ai = ai,
            modelRef = modelRef,
            path = path,
            instruction = "Detect every comic panel on this page. " +
                "Return ONLY a JSON array of boxes with integer coordinates on a 0-1000 scale relative to the " +
                "image, in reading order, format: [{\"x\":0,\"y\":0,\"w\":100,\"h\":200}]. " +
                "Boxes must tightly bound each panel and not overlap.",
            )
        }.getOrElse { error ->
            return AiPanelDetection(error = error.message ?: "The Vision request failed.")
        } ?: return AiPanelDetection(error = "The Vision model returned no usable response.")
        val array = extractJsonArray(raw)
            ?: return AiPanelDetection(error = "The Vision response did not contain a panel array.")
        val decoded = runCatching {
            regionJson.decodeFromString(ListSerializer(RegionDto.serializer()), array)
        }.getOrElse {
            return AiPanelDetection(error = "The Vision panel array could not be parsed.")
        }
        val boxes = decoded.mapNotNull { dto ->
            val x = dto.x / 1000f
            val y = dto.y / 1000f
            val w = dto.w / 1000f
            val h = dto.h / 1000f
            if (w <= 0f || h <= 0f || x < -0.05f || y < -0.05f || x + w > 1.05f || y + h > 1.05f) {
                null
            } else {
                android.graphics.RectF(
                    x.coerceIn(0f, 1f),
                    y.coerceIn(0f, 1f),
                    (x + w).coerceIn(0f, 1f),
                    (y + h).coerceIn(0f, 1f),
                )
            }
        }
        if (decoded.isNotEmpty() && boxes.isEmpty()) {
            return AiPanelDetection(error = "The Vision model returned no valid panel boxes.")
        }
        return AiPanelDetection(boxes = boxes)
    }

    /**
     * Reads every speech-bubble text region and translates it into
     * [targetLanguage]. Returns regions with normalized boxes plus the
     * original and translated strings.
     */
    suspend fun readText(
        ai: AiGenerationService,
        modelRef: String,
        path: String,
        targetLanguage: String,
    ): List<PanelTextRegion>? {
        val raw = ask(
            ai = ai,
            modelRef = modelRef,
            path = path,
            instruction = "Find every region of this comic page that contains written language (speech bubbles, " +
                "captions, sound effects). For each region return its bounding box on a 0-1000 scale, the exact " +
                "original text, and a natural translation into $targetLanguage. " +
                "Return ONLY a JSON array: [{\"x\":0,\"y\":0,\"w\":100,\"h\":40,\"original\":\"...\",\"translation\":\"...\"}]. " +
                "Use an empty original when a region is decorative. Do not merge separate bubbles.",
        ) ?: return null
        val array = extractJsonArray(raw) ?: return null
        val decoded = runCatching {
            regionJson.decodeFromString(ListSerializer(RegionDto.serializer()), array)
        }.getOrNull() ?: return null
        return decoded.mapNotNull { dto ->
            val original = (dto.original.ifBlank { dto.text }).trim()
            val translation = dto.translation.trim()
            if (original.isBlank() && translation.isBlank()) return@mapNotNull null
            PanelTextRegion(
                x = (dto.x / 1000f).coerceIn(0f, 1f),
                y = (dto.y / 1000f).coerceIn(0f, 1f),
                w = (dto.w / 1000f).coerceIn(0.01f, 1f),
                h = (dto.h / 1000f).coerceIn(0.01f, 1f),
                original = original,
                translation = translation,
            )
        }.takeIf { it.isNotEmpty() }
    }

    /** Smallest edge used for square-ish brush math in the editor. */
    fun minEdge(bitmap: Bitmap): Int = min(bitmap.width, bitmap.height)
}
