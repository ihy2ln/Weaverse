package com.ihy2ln.weaverse.core.roleplay

import android.content.Context
import android.net.Uri
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.RpCharacterEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class ParsedCharacterCard(
    val name: String,
    val description: String,
    val personality: String,
    val scenario: String,
    val firstMes: String,
    val mesExample: String,
    val systemPrompt: String,
    val creatorNotes: String,
    val postHistoryInstructions: String,
    val tagsJson: String,
    val characterVersion: String,
)

@Singleton
class CharacterCardImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: WeaverseDatabase,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun importFromUri(uri: Uri): String = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Could not read file")
        val parsed = when {
            bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> parsePng(bytes)
            else -> parseJson(String(bytes, Charsets.UTF_8))
        }
        val id = "char-${UUID.randomUUID()}"
        val now = System.currentTimeMillis()
        db.roleplayDao().upsertCharacter(
            RpCharacterEntity(
                id = id,
                name = parsed.name,
                description = parsed.description,
                personality = parsed.personality,
                scenario = parsed.scenario,
                firstMes = parsed.firstMes,
                mesExample = parsed.mesExample,
                creatorNotes = parsed.creatorNotes,
                systemPrompt = parsed.systemPrompt,
                postHistoryInstructions = parsed.postHistoryInstructions,
                alternateGreetingsJson = "[]",
                tagsJson = parsed.tagsJson,
                characterVersion = parsed.characterVersion,
                createdAt = now,
            ),
        )
        id
    }

    fun parsePng(bytes: ByteArray): ParsedCharacterCard {
        var offset = 8
        while (offset + 12 <= bytes.size) {
            val length = readInt(bytes, offset)
            val type = String(bytes, offset + 4, 4)
            val dataStart = offset + 8
            val dataEnd = dataStart + length
            if (type == "tEXt" && dataEnd <= bytes.size) {
                val chunkData = bytes.copyOfRange(dataStart, dataEnd)
                val nullIdx = chunkData.indexOf(0)
                if (nullIdx > 0) {
                    val keyword = String(chunkData, 0, nullIdx)
                    val text = String(chunkData, nullIdx + 1, chunkData.size - nullIdx - 1)
                    if (keyword == "chara" || keyword == "ccv3") {
                        val decoded = String(Base64.getDecoder().decode(text.trim()))
                        return parseJson(decoded)
                    }
                }
            }
            offset = dataEnd + 4
        }
        error("No chara tEXt chunk found in PNG")
    }

    fun parseJson(raw: String): ParsedCharacterCard {
        val root = json.parseToJsonElement(raw).jsonObject
        val data = root["data"]?.jsonObject ?: root
        fun field(key: String, alt: String = key): String =
            data[key]?.jsonPrimitive?.contentOrNull
                ?: root[key]?.jsonPrimitive?.contentOrNull
                ?: ""
        val tags = data["tags"]?.toString() ?: "[]"
        return ParsedCharacterCard(
            name = field("name").ifBlank { "Imported Character" },
            description = field("description"),
            personality = field("personality"),
            scenario = field("scenario"),
            firstMes = field("first_mes", "firstMes"),
            mesExample = field("mes_example", "mesExample"),
            systemPrompt = field("system_prompt", "systemPrompt"),
            creatorNotes = field("creator_notes", "creatorNotes"),
            postHistoryInstructions = field("post_history_instructions", "postHistoryInstructions"),
            tagsJson = tags,
            characterVersion = field("character_version", "characterVersion").ifBlank { "2.0" },
        )
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
}
