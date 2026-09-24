package com.ihy2ln.weaverse.core.roleplay

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * SillyTavern / Chub PNG character card (spec v2): JSON in a `chara` tEXt chunk,
 * base64-encoded. Also reads `ccv3`, `zTXt`, and raw JSON files.
 */
object CharacterCardPng {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun looksLikePng(bytes: ByteArray): Boolean =
        bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte()

    fun extractCardJson(png: ByteArray): String {
        var offset = 8
        while (offset + 12 <= png.size) {
            val length = readInt(png, offset)
            if (length < 0 || offset + 12 + length > png.size) break
            val type = String(png, offset + 4, 4, Charsets.US_ASCII)
            val dataStart = offset + 8
            val dataEnd = dataStart + length
            val chunk = png.copyOfRange(dataStart, dataEnd)
            when (type) {
                "tEXt" -> decodeTextChunk(chunk)?.let { return it }
                "zTXt" -> decodeZtxtChunk(chunk)?.let { return it }
                "iTXt" -> decodeItxtChunk(chunk)?.let { return it }
            }
            offset = dataEnd + 4
        }
        error("No chara tEXt chunk found in PNG")
    }

    fun embedCardJson(png: ByteArray, cardJson: String): ByteArray {
        val encoded = Base64.getEncoder().encodeToString(cardJson.toByteArray(Charsets.UTF_8))
        val chunk = encodeTextChunk("chara", encoded)
        val iend = findIend(png)
        val out = ByteArray(iend + chunk.size + (png.size - iend))
        System.arraycopy(png, 0, out, 0, iend)
        System.arraycopy(chunk, 0, out, iend, chunk.size)
        System.arraycopy(png, iend, out, iend + chunk.size, png.size - iend)
        return out
    }

    fun cardJson(spec: CharacterCardSpec): String {
        val tags = spec.tags
        val greetings = spec.alternateGreetings
        val data = buildJsonObject {
            put("name", JsonPrimitive(spec.name))
            put("description", JsonPrimitive(spec.description))
            put("personality", JsonPrimitive(spec.personality))
            put("scenario", JsonPrimitive(spec.scenario))
            put("first_mes", JsonPrimitive(spec.firstMes))
            put("mes_example", JsonPrimitive(spec.mesExample))
            put("creator_notes", JsonPrimitive(spec.creatorNotes))
            put("system_prompt", JsonPrimitive(spec.systemPrompt))
            put("post_history_instructions", JsonPrimitive(spec.postHistoryInstructions))
            put("character_version", JsonPrimitive(spec.characterVersion.ifBlank { "2.0" }))
            put("creator", JsonPrimitive(spec.creator))
            put(
                "tags",
                buildJsonArray { tags.forEach { add(JsonPrimitive(it)) } },
            )
            put(
                "alternate_greetings",
                buildJsonArray { greetings.forEach { add(JsonPrimitive(it)) } },
            )
            put(
                "extensions",
                runCatching { json.parseToJsonElement(spec.extensionsJson.ifBlank { "{}" }) }
                    .getOrElse { buildJsonObject {} },
            )
        }
        val root = buildJsonObject {
            put("spec", JsonPrimitive("chara_card_v2"))
            put("spec_version", JsonPrimitive("2.0"))
            put("data", data)
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    fun parseCardJson(raw: String): CharacterCardSpec {
        val root = json.parseToJsonElement(raw).jsonObject
        val data = root["data"]?.jsonObject ?: root
        fun field(key: String, alt: String = key): String =
            data[key]?.jsonPrimitive?.contentOrNull
                ?: root[key]?.jsonPrimitive?.contentOrNull
                ?: data[alt]?.jsonPrimitive?.contentOrNull
                ?: ""
        val tags = decodeStringList(data["tags"] ?: root["tags"])
        val greetings = decodeStringList(data["alternate_greetings"] ?: data["alternateGreetings"])
        val extensions = data["extensions"]?.toString() ?: "{}"
        return CharacterCardSpec(
            name = field("name").ifBlank { "Imported Character" },
            description = field("description"),
            personality = field("personality"),
            scenario = field("scenario"),
            firstMes = field("first_mes", "firstMes"),
            mesExample = field("mes_example", "mesExample"),
            systemPrompt = field("system_prompt", "systemPrompt"),
            creatorNotes = field("creator_notes", "creatorNotes"),
            postHistoryInstructions = field("post_history_instructions", "postHistoryInstructions"),
            characterVersion = field("character_version", "characterVersion").ifBlank { "2.0" },
            creator = field("creator"),
            tags = tags,
            alternateGreetings = greetings,
            extensionsJson = extensions,
        )
    }

    fun minimalPng(red: Int = 40, green: Int = 44, blue: Int = 52): ByteArray {
        val ihdr = ByteArray(13)
        writeInt(ihdr, 0, 1)
        writeInt(ihdr, 4, 1)
        ihdr[8] = 8
        ihdr[9] = 2
        val scanline = byteArrayOf(0, red.toByte(), green.toByte(), blue.toByte())
        val idat = deflate(scanline)
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        out.write(chunk("IHDR", ihdr))
        out.write(chunk("IDAT", idat))
        out.write(chunk("IEND", ByteArray(0)))
        return out.toByteArray()
    }

    private fun decodeTextChunk(data: ByteArray): String? {
        val nullIdx = data.indexOfByte(0)
        if (nullIdx <= 0) return null
        val keyword = String(data, 0, nullIdx, Charsets.ISO_8859_1)
        if (keyword != "chara" && keyword != "ccv3") return null
        val text = String(data, nullIdx + 1, data.size - nullIdx - 1, Charsets.ISO_8859_1)
        return decodePayload(text)
    }

    private fun decodeZtxtChunk(data: ByteArray): String? {
        val nullIdx = data.indexOfByte(0)
        if (nullIdx <= 0 || nullIdx + 2 > data.size) return null
        val keyword = String(data, 0, nullIdx, Charsets.ISO_8859_1)
        if (keyword != "chara" && keyword != "ccv3") return null
        val inflated = inflate(data.copyOfRange(nullIdx + 2, data.size))
        return decodePayload(String(inflated, Charsets.ISO_8859_1))
    }

    private fun decodeItxtChunk(data: ByteArray): String? {
        val nullIdx = data.indexOfByte(0)
        if (nullIdx <= 0 || nullIdx + 5 > data.size) return null
        val keyword = String(data, 0, nullIdx, Charsets.ISO_8859_1)
        if (keyword != "chara" && keyword != "ccv3") return null
        val compressed = data[nullIdx + 1].toInt() != 0
        var cursor = nullIdx + 3
        fun skipCString(): Int {
            val end = data.indexOfByte(0, cursor).takeIf { it >= 0 } ?: data.size
            val next = end + 1
            cursor = next
            return end
        }
        skipCString()
        skipCString()
        val rest = data.copyOfRange(cursor.coerceAtMost(data.size), data.size)
        val text = if (compressed) String(inflate(rest), Charsets.UTF_8) else String(rest, Charsets.UTF_8)
        return decodePayload(text)
    }

    private fun decodePayload(text: String): String {
        val trimmed = text.trim()
        return runCatching {
            String(Base64.getDecoder().decode(trimmed), Charsets.UTF_8)
        }.getOrElse { trimmed }
    }

    private fun decodeStringList(element: kotlinx.serialization.json.JsonElement?): List<String> {
        if (element == null) return emptyList()
        return when (element) {
            is JsonArray -> element.mapNotNull { it.jsonPrimitive.contentOrNull?.takeIf { s -> s.isNotBlank() } }
            is JsonPrimitive -> element.contentOrNull
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                .orEmpty()
            else -> emptyList()
        }
    }

    private fun encodeTextChunk(keyword: String, text: String): ByteArray {
        val payload = keyword.toByteArray(Charsets.ISO_8859_1) + 0 + text.toByteArray(Charsets.ISO_8859_1)
        return chunk("tEXt", payload)
    }

    private fun chunk(type: String, data: ByteArray): ByteArray {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        val out = ByteArray(12 + data.size)
        writeInt(out, 0, data.size)
        System.arraycopy(typeBytes, 0, out, 4, 4)
        System.arraycopy(data, 0, out, 8, data.size)
        writeInt(out, 8 + data.size, crc.value.toInt())
        return out
    }

    private fun findIend(png: ByteArray): Int {
        var offset = 8
        while (offset + 12 <= png.size) {
            val length = readInt(png, offset)
            val type = String(png, offset + 4, 4, Charsets.US_ASCII)
            if (type == "IEND") return offset
            offset += 12 + length
        }
        return png.size
    }

    private fun deflate(input: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION)
        deflater.setInput(input)
        deflater.finish()
        val buf = ByteArray(64)
        val out = ByteArrayOutputStream()
        while (!deflater.finished()) {
            val n = deflater.deflate(buf)
            if (n > 0) out.write(buf, 0, n)
        }
        deflater.end()
        return out.toByteArray()
    }

    private fun inflate(input: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(input)
        val buf = ByteArray(256)
        val out = ByteArrayOutputStream()
        while (!inflater.finished()) {
            val n = inflater.inflate(buf)
            if (n == 0 && inflater.needsInput()) break
            if (n > 0) out.write(buf, 0, n)
        }
        inflater.end()
        return out.toByteArray()
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private fun writeInt(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 24).toByte()
        bytes[offset + 1] = (value ushr 16).toByte()
        bytes[offset + 2] = (value ushr 8).toByte()
        bytes[offset + 3] = value.toByte()
    }

    private fun ByteArray.indexOfByte(value: Byte, start: Int = 0): Int {
        for (i in start until size) if (this[i] == value) return i
        return -1
    }
}

data class CharacterCardSpec(
    val name: String,
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val firstMes: String = "",
    val mesExample: String = "",
    val systemPrompt: String = "",
    val creatorNotes: String = "",
    val postHistoryInstructions: String = "",
    val characterVersion: String = "2.0",
    val creator: String = "",
    val tags: List<String> = emptyList(),
    val alternateGreetings: List<String> = emptyList(),
    val extensionsJson: String = "{}",
)
