package com.ihy2ln.weaverse.core.roleplay

import android.content.Context
import com.ihy2ln.weaverse.core.media.MediaRepository
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.RpCharacterEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

@Singleton
class CharacterCardExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: WeaverseDatabase,
    private val mediaRepository: MediaRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val exportDir: File get() = File(context.filesDir, "exports/cards").also { it.mkdirs() }

    suspend fun exportPng(characterId: String): File = withContext(Dispatchers.IO) {
        val character = db.roleplayDao().getCharacter(characterId) ?: error("Character not found")
        val spec = character.toSpec()
        val cardJson = CharacterCardPng.cardJson(spec)
        val basePng = avatarPngBytes(character) ?: CharacterCardPng.minimalPng()
        val embedded = CharacterCardPng.embedCardJson(basePng, cardJson)
        val safe = character.name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(40).ifBlank { "character" }
        val file = File(exportDir, "$safe.png")
        file.writeBytes(embedded)
        file
    }

    suspend fun exportJson(characterId: String): File = withContext(Dispatchers.IO) {
        val character = db.roleplayDao().getCharacter(characterId) ?: error("Character not found")
        val file = File(
            exportDir,
            "${character.name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(40).ifBlank { "character" }}.json",
        )
        file.writeText(CharacterCardPng.cardJson(character.toSpec()))
        file
    }

    private suspend fun avatarPngBytes(character: RpCharacterEntity): ByteArray? {
        val mediaId = character.avatarMediaId ?: return null
        val media = mediaRepository.getById(mediaId) ?: return null
        val file = mediaRepository.resolveFile(media)
        if (!file.isFile) return null
        val bytes = file.readBytes()
        return if (CharacterCardPng.looksLikePng(bytes)) bytes else null
    }

    private fun RpCharacterEntity.toSpec(): CharacterCardSpec {
        val tags = runCatching {
            json.parseToJsonElement(tagsJson).jsonArray.map { it.jsonPrimitive.content }
        }.getOrDefault(emptyList())
        val greetings = runCatching {
            json.parseToJsonElement(alternateGreetingsJson).jsonArray.map { it.jsonPrimitive.content }
        }.getOrDefault(emptyList())
        return CharacterCardSpec(
            name = name,
            description = description,
            personality = personality,
            scenario = scenario,
            firstMes = firstMes,
            mesExample = mesExample,
            systemPrompt = systemPrompt,
            creatorNotes = creatorNotes,
            postHistoryInstructions = postHistoryInstructions,
            characterVersion = characterVersion,
            tags = tags,
            alternateGreetings = greetings,
            extensionsJson = extensionsJson,
        )
    }
}
