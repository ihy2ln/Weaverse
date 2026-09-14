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
import kotlinx.serialization.builtins.serializer
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

    /**
     * Downscale + encode for a vision/edit request. [maxDim] defaults to 1100 px, enough
     * for reading lettering; a full-page edit (colorization) asks for more (see
     * [RoleplayChatViewModel]'s colorize step) so fine line art survives the round trip.
     */
    fun imageAttachmentFor(path: String, maxDim: Int = 1100): ImageAttachment? {
        val file = File(path)
        if (!file.exists() || file.length() == 0L) return null
        val bitmap: Bitmap = ImageOps.loadBitmap(path, maxDim = maxDim) ?: return null
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }.toByteArray()
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
        return parseTranslatedRegions(raw)
    }

    /** Pure parser kept separate so multilingual translation output is testable. */
    internal fun parseTranslatedRegions(raw: String): List<PanelTextRegion>? {
        val array = extractJsonArray(raw) ?: return null
        val decoded = runCatching {
            regionJson.decodeFromString(ListSerializer(RegionDto.serializer()), array)
        }.getOrNull() ?: return null
        return decoded.mapIndexedNotNull { index, dto ->
            val original = (dto.original.ifBlank { dto.text }).trim()
            val translation = dto.translation.trim()
            if (original.isBlank() && translation.isBlank()) return@mapIndexedNotNull null
            val x = (dto.x / 1000f).coerceIn(0f, 1f)
            val y = (dto.y / 1000f).coerceIn(0f, 1f)
            val right = (x + (dto.w / 1000f).coerceAtLeast(0.01f)).coerceIn(0.01f, 1f)
            val bottom = (y + (dto.h / 1000f).coerceAtLeast(0.01f)).coerceIn(0.01f, 1f)
            PanelTextRegion(
                id = "t$index",
                x = x.coerceAtMost(right - 0.01f),
                y = y.coerceAtMost(bottom - 0.01f),
                w = (right - x).coerceAtLeast(0.01f),
                h = (bottom - y).coerceAtLeast(0.01f),
                original = original,
                translation = translation,
            )
        }.takeIf { it.isNotEmpty() }
    }

    /** Boxes only — used when Detection is selected without OCR or translation. */
    internal fun parseDetectedRegions(raw: String): List<PanelTextRegion>? {
        val array = extractJsonArray(raw) ?: return null
        val decoded = runCatching {
            regionJson.decodeFromString(ListSerializer(RegionDto.serializer()), array)
        }.getOrNull() ?: return null
        return decoded.mapIndexedNotNull { index, dto ->
            val w = (dto.w / 1000f).coerceIn(0.01f, 1f)
            val h = (dto.h / 1000f).coerceIn(0.01f, 1f)
            if (dto.w <= 0 || dto.h <= 0) return@mapIndexedNotNull null
            PanelTextRegion(
                id = "t$index",
                x = (dto.x / 1000f).coerceIn(0f, 1f),
                y = (dto.y / 1000f).coerceIn(0f, 1f),
                w = w,
                h = h,
                original = (dto.original.ifBlank { dto.text }).trim(),
                translation = dto.translation.trim(),
            )
        }.takeIf { it.isNotEmpty() }
    }

    suspend fun detectText(
        ai: AiGenerationService,
        modelRef: String,
        path: String,
    ): List<PanelTextRegion>? {
        val raw = ask(
            ai = ai,
            modelRef = modelRef,
            path = path,
            instruction = "Find every region of this comic page that contains written language " +
                "(speech bubbles, captions, sound effects). Return ONLY a JSON array of boxes on a " +
                "0-1000 scale: [{\"x\":0,\"y\":0,\"w\":100,\"h\":40}]. Do not merge separate bubbles.",
        ) ?: return null
        return parseDetectedRegions(raw)
    }

    suspend fun translateTexts(
        ai: AiGenerationService,
        modelRef: String,
        originals: List<String>,
        targetLanguage: String,
    ): List<String>? {
        if (originals.isEmpty()) return emptyList()
        val numbered = originals.mapIndexed { index, text -> "${index + 1}. $text" }.joinToString("\n")
        val result = ai.complete(
            userMessage = "Translate each numbered line into $targetLanguage. " +
                "Return ONLY a JSON array of strings in the same order, no numbers, no commentary. " +
                "When the target is English, use English letters only: never copy Japanese, Chinese, Korean, " +
                "Arabic, Cyrillic, or any other source-script characters into the answer. Preserve names and " +
                "sound effects as readable English transliteration when needed.\n\n$numbered",
            assembled = AssembledPrompt(
                systemBlocks = listOf(
                    "You are a manga translator. Answer with a raw JSON array of strings only.",
                ),
                messages = emptyList(),
                usedEntries = emptyList(),
                tokenBreakdown = emptyList(),
            ),
            modelRef = modelRef,
            maxTokens = 2048,
            temperature = 0.2,
        )
        val array = extractJsonArray(result.text.trim()) ?: return null
        val decoded = runCatching {
            regionJson.decodeFromString(ListSerializer(String.serializer()), array)
        }.getOrNull() ?: return null
        return decoded.takeIf { it.size == originals.size }
            ?.map { text -> if (targetLanguage.equals("English", ignoreCase = true)) normalizeEnglishText(text) else text.trim() }
    }

    suspend fun proofreadTexts(
        ai: AiGenerationService,
        modelRef: String,
        translations: List<String>,
        language: String,
    ): List<String>? {
        if (translations.isEmpty()) return emptyList()
        val numbered = translations.mapIndexed { index, text -> "${index + 1}. $text" }.joinToString("\n")
        val result = ai.complete(
            userMessage = "Proofread these manga lines in $language. Preserve meaning, names, tone, and sound effects; " +
                "make dialogue natural and concise enough to fit its original bubble. Return ONLY a JSON array of " +
                "strings in the same order. When the language is English, output English letters only and never " +
                "restore source-script characters.\n\n$numbered",
            assembled = AssembledPrompt(
                systemBlocks = listOf(
                    "You are a professional manga translation editor and letterer. Return a raw JSON array only.",
                ),
                messages = emptyList(),
                usedEntries = emptyList(),
                tokenBreakdown = emptyList(),
            ),
            modelRef = modelRef,
            maxTokens = 2048,
            temperature = 0.15,
        )
        val array = extractJsonArray(result.text.trim()) ?: return null
        return runCatching {
            regionJson.decodeFromString(ListSerializer(String.serializer()), array)
        }.getOrNull()?.takeIf { it.size == translations.size }
            ?.map { text -> if (language.equals("English", ignoreCase = true)) normalizeEnglishText(text) else text.trim() }
    }

    /** Removes model wrappers that otherwise become visible as part of the lettering. */
    internal fun normalizeEnglishText(text: String): String = text
        .trim()
        .replace(Regex("^\\s*(?:translation|english|answer)\\s*[:：-]\\s*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("^\\s*\\d+[.)]\\s*"), "")
        .trim()
        .trim('`', '"', '\'')
        .replace(Regex("\\s+"), " ")
        .trim()

    /** Foreign scripts are not safe to paint when the requested output is English. */
    internal fun containsForeignScript(text: String): Boolean = Regex(
        "[\\u3040-\\u30ff\\u3400-\\u4dbf\\u4e00-\\u9fff\\uac00-\\ud7af" +
            "\\u0400-\\u04ff\\u0600-\\u06ff\\u0590-\\u05ff\\u0900-\\u097f\\u0e00-\\u0e7f]",
    ).containsMatchIn(text)

    /** Returns source-region indexes that must not be erased or lettered yet. */
    internal fun invalidEnglishRegionIndexes(regions: List<PanelTextRegion>): List<Int> =
        regions.mapIndexedNotNull { index, region ->
            val translated = normalizeEnglishText(region.translation)
            val copiedSource = region.original.isNotBlank() && translated.isNotBlank() &&
                translated.equals(normalizeEnglishText(region.original), ignoreCase = true)
            if (!region.visible) return@mapIndexedNotNull null
            if (containsForeignScript(translated) || copiedSource ||
                (region.original.isNotBlank() && translated.isBlank())
            ) index else null
        }

    /** Smallest edge used for square-ish brush math in the editor. */
    fun minEdge(bitmap: Bitmap): Int = min(bitmap.width, bitmap.height)
}
