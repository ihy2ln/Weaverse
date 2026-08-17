package com.ihy2ln.weaverse.feature.roleplay.characters

import com.ihy2ln.weaverse.core.media.PngChunkIO
import com.ihy2ln.weaverse.data.db.entity.RpCharacterEntity
import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** SillyTavern/Chub-compatible V2 character card (spec §11's PNG round-trip target format). */
@Serializable
data class CharaCardV2(
    val spec: String = "chara_card_v2",
    val spec_version: String = "2.0",
    val data: CharaCardData,
)

@Serializable
data class CharaCardData(
    val name: String,
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val first_mes: String = "",
    val mes_example: String = "",
    val creator_notes: String = "",
    val system_prompt: String = "",
    val post_history_instructions: String = "",
    val alternate_greetings: List<String> = emptyList(),
    val character_version: String = "1.0",
    val tags: List<String> = emptyList(),
    /** Passed through verbatim — see [RpCharacterEntity.extensionsJson]'s own doc comment. */
    val extensions: JsonElement = JsonObject(emptyMap()),
)

fun RpCharacterEntity.toCardData(): CharaCardData = CharaCardData(
    name = name,
    description = description,
    personality = personality,
    scenario = scenario,
    first_mes = firstMes,
    mes_example = mesExample,
    creator_notes = creatorNotes,
    system_prompt = systemPrompt,
    post_history_instructions = postHistoryInstructions,
    alternate_greetings = alternateGreetings,
    character_version = characterVersion,
    tags = tags,
    extensions = runCatching { CharacterCardCodec.json.parseToJsonElement(extensionsJson) }.getOrDefault(JsonObject(emptyMap())),
)

fun CharaCardData.toEntity(existingId: String? = null, avatarMediaId: String? = null): RpCharacterEntity {
    val entity = RpCharacterEntity(
        name = name,
        avatarMediaId = avatarMediaId,
        description = description,
        personality = personality,
        scenario = scenario,
        firstMes = first_mes,
        mesExample = mes_example,
        creatorNotes = creator_notes,
        systemPrompt = system_prompt,
        postHistoryInstructions = post_history_instructions,
        alternateGreetings = alternate_greetings,
        characterVersion = character_version,
        tags = tags,
        extensionsJson = CharacterCardCodec.json.encodeToString(extensions),
    )
    return if (existingId != null) entity.copy(id = existingId) else entity
}

/**
 * Embeds/extracts a [CharaCardV2] as a `tEXt` chunk (keyword `chara`, base64-encoded UTF-8 JSON)
 * inside a PNG byte stream — the same convention SillyTavern/Chub character-card PNGs use, so
 * cards exported here should open in those tools and vice versa (spec §13's PNG round-trip
 * acceptance criterion). [encode] takes an already-valid PNG (e.g. from `Bitmap.compress`) and
 * only adds/replaces the one ancillary chunk; it never touches pixel data.
 */
object CharacterCardCodec {
    private const val CHUNK_TYPE = "tEXt"
    private const val KEYWORD = "chara"
    internal val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(character: RpCharacterEntity, basePng: ByteArray): ByteArray {
        val cardJson = json.encodeToString(CharaCardV2(data = character.toCardData()))
        val base64 = Base64.getEncoder().encodeToString(cardJson.toByteArray(Charsets.UTF_8))
        val chunkData = keywordBytes(KEYWORD) + byteArrayOf(0) + base64.toByteArray(Charsets.ISO_8859_1)

        val chunks = PngChunkIO.readChunks(basePng).toMutableList()
        chunks.removeAll { it.type == CHUNK_TYPE && keywordOf(it.data) == KEYWORD }
        val insertAt = chunks.indexOfFirst { it.type == "IHDR" }.let { if (it == -1) 0 else it + 1 }
        chunks.add(insertAt, PngChunkIO.Chunk(CHUNK_TYPE, chunkData))
        return PngChunkIO.writeChunks(chunks)
    }

    fun decode(png: ByteArray, existingId: String? = null, avatarMediaId: String? = null): RpCharacterEntity? {
        val chunk = PngChunkIO.readChunks(png).firstOrNull { it.type == CHUNK_TYPE && keywordOf(it.data) == KEYWORD } ?: return null
        val base64 = textOf(chunk.data)
        val cardJson = String(Base64.getDecoder().decode(base64), Charsets.UTF_8)
        return json.decodeFromString<CharaCardV2>(cardJson).data.toEntity(existingId, avatarMediaId)
    }

    private fun keywordBytes(keyword: String) = keyword.toByteArray(Charsets.ISO_8859_1)

    private fun keywordOf(data: ByteArray): String {
        val nullIndex = data.indexOf(0.toByte())
        if (nullIndex < 0) return ""
        return String(data, 0, nullIndex, Charsets.ISO_8859_1)
    }

    private fun textOf(data: ByteArray): String {
        val nullIndex = data.indexOf(0.toByte())
        return String(data, nullIndex + 1, data.size - nullIndex - 1, Charsets.ISO_8859_1)
    }
}
