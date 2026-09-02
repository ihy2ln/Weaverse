package com.ihy2ln.weaverse.core.roleplay

import android.content.Context
import android.net.Uri
import com.ihy2ln.weaverse.core.media.MediaRepository
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.RpCharacterEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

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
    private val mediaRepository: MediaRepository,
) {
    suspend fun importFromUri(uri: Uri): String = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Could not read file")
        importBytes(bytes)
    }

    suspend fun importBytes(bytes: ByteArray): String {
        val spec = if (CharacterCardPng.looksLikePng(bytes)) {
            CharacterCardPng.parseCardJson(CharacterCardPng.extractCardJson(bytes))
        } else {
            CharacterCardPng.parseCardJson(String(bytes, Charsets.UTF_8))
        }
        val id = "char-${UUID.randomUUID()}"
        val now = System.currentTimeMillis()
        val avatarId = if (CharacterCardPng.looksLikePng(bytes)) {
            mediaRepository.importFromBytes(bytes, fileName = "$id.png", mimeType = "image/png").id
        } else {
            null
        }
        db.roleplayDao().upsertCharacter(
            RpCharacterEntity(
                id = id,
                name = spec.name,
                avatarMediaId = avatarId,
                description = spec.description,
                personality = spec.personality,
                scenario = spec.scenario,
                firstMes = spec.firstMes,
                mesExample = spec.mesExample,
                creatorNotes = spec.creatorNotes,
                systemPrompt = spec.systemPrompt,
                postHistoryInstructions = spec.postHistoryInstructions,
                alternateGreetingsJson = spec.alternateGreetings.joinToString(
                    prefix = "[",
                    postfix = "]",
                ) { "\"${it.replace("\"", "")}\"" }.ifBlank { "[]" },
                tagsJson = spec.tags.joinToString(prefix = "[", postfix = "]") {
                    "\"${it.replace("\"", "")}\""
                }.ifBlank { "[]" },
                characterVersion = spec.characterVersion,
                extensionsJson = spec.extensionsJson.ifBlank { "{}" },
                createdAt = now,
            ),
        )
        return id
    }

    fun parsePng(bytes: ByteArray): ParsedCharacterCard {
        val spec = CharacterCardPng.parseCardJson(CharacterCardPng.extractCardJson(bytes))
        return spec.toParsed()
    }

    fun parseJson(raw: String): ParsedCharacterCard =
        CharacterCardPng.parseCardJson(raw).toParsed()

    private fun CharacterCardSpec.toParsed() = ParsedCharacterCard(
        name = name,
        description = description,
        personality = personality,
        scenario = scenario,
        firstMes = firstMes,
        mesExample = mesExample,
        systemPrompt = systemPrompt,
        creatorNotes = creatorNotes,
        postHistoryInstructions = postHistoryInstructions,
        tagsJson = tags.joinToString(prefix = "[", postfix = "]") { "\"${it.replace("\"", "")}\"" }
            .ifBlank { "[]" },
        characterVersion = characterVersion,
    )
}
