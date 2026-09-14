package com.ihy2ln.weaverse.data.export

import com.ihy2ln.weaverse.core.media.MediaRepository
import com.ihy2ln.weaverse.core.roleplay.CharacterCardPng
import com.ihy2ln.weaverse.core.roleplay.CharacterCardSpec
import com.ihy2ln.weaverse.core.text.Document
import com.ihy2ln.weaverse.core.text.toJson
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.CodexCategoryEntity
import com.ihy2ln.weaverse.data.db.entities.CodexEntryEntity
import com.ihy2ln.weaverse.data.db.entities.CodexEntryLoreEntity
import com.ihy2ln.weaverse.data.db.entities.RpCharacterEntity
import com.ihy2ln.weaverse.data.db.entities.RpChatEntity
import com.ihy2ln.weaverse.data.db.entities.RpMessageEntity
import com.ihy2ln.weaverse.data.db.entities.RpPersonaEntity
import com.ihy2ln.weaverse.data.repo.CodexScopes
import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class SillyTavernImportResult(
    val characters: Int = 0,
    val chats: Int = 0,
    val messages: Int = 0,
    val worldEntries: Int = 0,
    val personas: Int = 0,
) {
    fun message(): String =
        "Imported SillyTavern data — $characters characters, $chats chats " +
            "($messages messages), $worldEntries world-info entries, $personas personas"
}

@Singleton
class SillyTavernImporter @Inject constructor(
    private val db: WeaverseDatabase,
    private val mediaRepository: MediaRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun looksLike(bytes: ByteArray, displayName: String): Boolean =
        SillyTavernProbe.looksLike(bytes, displayName)

    suspend fun importBytes(bytes: ByteArray, displayName: String): SillyTavernImportResult =
        withContext(Dispatchers.IO) {
            if (CharacterCardPng.looksLikePng(bytes) || displayName.endsWith(".json")) {
                if (CharacterCardPng.looksLikePng(bytes) || SillyTavernProbe.looksLikeCardJson(bytes)) {
                    importCard(bytes, displayName)
                    return@withContext SillyTavernImportResult(characters = 1)
                }
            }
            if (looksLikeZip(bytes)) return@withContext importZip(bytes)
            val text = bytes.toString(Charsets.UTF_8)
            when {
                text.contains("\"is_user\"") || text.contains("\"character_name\"") ->
                    importChatJsonl(text, characterHint = displayName.substringBeforeLast('.'))
                text.contains("\"entries\"") ->
                    SillyTavernImportResult(worldEntries = importWorldInfo(text))
                else -> error("Not a SillyTavern export (need characters/, chats/, worlds/, or a PNG card)")
            }
        }

    private suspend fun importZip(bytes: ByteArray): SillyTavernImportResult {
        val files = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    files[entry.name.replace('\\', '/')] = zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        var characters = 0
        var chats = 0
        var messages = 0
        var world = 0
        var personas = 0
        val nameToId = mutableMapOf<String, String>()

        files.forEach { (path, data) ->
            val lower = path.lowercase()
            if (lower.contains("/characters/") || lower.startsWith("characters/")) {
                if (CharacterCardPng.looksLikePng(data) || lower.endsWith(".json")) {
                    val id = importCard(data, path)
                    val name = db.roleplayDao().getCharacter(id)?.name.orEmpty()
                    if (name.isNotBlank()) nameToId[name.lowercase()] = id
                    characters++
                }
            }
        }
        files.forEach { (path, data) ->
            val lower = path.lowercase()
            if (lower.contains("/worlds/") || lower.startsWith("worlds/") || lower.contains("worldinfo")) {
                if (lower.endsWith(".json")) {
                    world += importWorldInfo(data.toString(Charsets.UTF_8))
                }
            }
        }
        files.forEach { (path, data) ->
            val lower = path.lowercase()
            if (lower.contains("/chats/") || lower.startsWith("chats/")) {
                val hint = path.substringAfterLast('/').substringBeforeLast('.')
                val folderChar = path.substringAfter("chats/", "").substringBefore('/', "")
                val result = importChatJsonl(
                    data.toString(Charsets.UTF_8),
                    characterHint = folderChar.ifBlank { hint },
                    nameToId = nameToId,
                )
                chats += result.chats
                messages += result.messages
            }
        }
        files.forEach { (path, data) ->
            val lower = path.lowercase()
            if (lower.endsWith("settings.json") || lower.contains("power_user")) {
                personas += importPersonasFromSettings(data.toString(Charsets.UTF_8))
            }
        }
        return SillyTavernImportResult(characters, chats, messages, world, personas)
    }

    private suspend fun importCard(bytes: ByteArray, displayName: String): String {
        val spec = if (CharacterCardPng.looksLikePng(bytes)) {
            CharacterCardPng.parseCardJson(CharacterCardPng.extractCardJson(bytes))
        } else {
            CharacterCardPng.parseCardJson(bytes.toString(Charsets.UTF_8))
        }
        return upsertCharacter(spec, avatarBytes = bytes.takeIf { CharacterCardPng.looksLikePng(it) })
    }

    private suspend fun upsertCharacter(spec: CharacterCardSpec, avatarBytes: ByteArray?): String {
        val id = "char-${UUID.randomUUID()}"
        val now = System.currentTimeMillis()
        val avatarId = avatarBytes?.let { png ->
            mediaRepository.importFromBytes(png, fileName = "$id.png", mimeType = "image/png").id
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
                alternateGreetingsJson = encodeList(spec.alternateGreetings),
                tagsJson = encodeList(spec.tags),
                characterVersion = spec.characterVersion,
                extensionsJson = spec.extensionsJson.ifBlank { "{}" },
                createdAt = now,
            ),
        )
        return id
    }

    private suspend fun importWorldInfo(raw: String): Int {
        val root = json.parseToJsonElement(raw).jsonObject
        val entriesElement = root["entries"] ?: return 0
        val entries = when (entriesElement) {
            is JsonObject -> entriesElement.values.mapNotNull { it as? JsonObject }
            is JsonArray -> entriesElement.mapNotNull { it as? JsonObject }
            else -> emptyList()
        }
        if (entries.isEmpty()) return 0
        val categoryId = "cat-world-info"
        val existing = db.codexDao().getCategories(CodexScopes.ID).firstOrNull { it.id == categoryId }
        if (existing == null) {
            db.codexDao().upsertCategory(
                CodexCategoryEntity(
                    id = categoryId,
                    scopeType = CodexScopes.TYPE,
                    scopeId = CodexScopes.ID,
                    name = "World Info",
                    colorHex = "#6B8E9F",
                    isSystem = true,
                ),
            )
        }
        val now = System.currentTimeMillis()
        entries.forEach { obj ->
            val content = obj["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val comment = obj["comment"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val keys = stringList(obj["key"] ?: obj["keys"])
            val secondary = stringList(obj["keysecondary"] ?: obj["secondary_keys"])
            val name = comment.ifBlank { keys.firstOrNull().orEmpty() }.ifBlank { "World entry" }
            val entryId = "codex-${UUID.randomUUID()}"
            db.codexDao().upsertEntry(
                CodexEntryEntity(
                    id = entryId,
                    categoryId = categoryId,
                    scopeType = CodexScopes.TYPE,
                    scopeId = CodexScopes.ID,
                    name = name,
                    aliasesJson = encodeList(keys.drop(1)),
                    docJson = Document.fromPlainText(content).toJson(),
                    plainText = content,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            db.codexDao().upsertLore(
                CodexEntryLoreEntity(
                    entryId = entryId,
                    keysJson = encodeList(keys),
                    secondaryKeysJson = encodeList(secondary),
                    insertionOrder = obj["insertion_order"]?.jsonPrimitive?.intOrNull
                        ?: obj["order"]?.jsonPrimitive?.intOrNull
                        ?: 100,
                    position = obj["position"]?.jsonPrimitive?.contentOrNull ?: "beforeChar",
                    depth = obj["depth"]?.jsonPrimitive?.intOrNull ?: 0,
                    probability = obj["probability"]?.jsonPrimitive?.intOrNull ?: 100,
                    isConstant = obj["constant"]?.jsonPrimitive?.booleanOrNull ?: false,
                    caseSensitive = obj["case_sensitive"]?.jsonPrimitive?.booleanOrNull ?: false,
                    matchWholeWords = obj["match_whole_words"]?.jsonPrimitive?.booleanOrNull ?: true,
                    scanDepth = obj["scan_depth"]?.jsonPrimitive?.intOrNull ?: 2,
                ),
            )
        }
        return entries.size
    }

    private suspend fun importChatJsonl(
        raw: String,
        characterHint: String,
        nameToId: Map<String, String> = emptyMap(),
    ): SillyTavernImportResult {
        val lines = raw.lineSequence().map { it.trim() }.filter { it.startsWith("{") }.toList()
        if (lines.isEmpty()) return SillyTavernImportResult()
        var characterName = characterHint.substringAfterLast('/').ifBlank { "Imported chat" }
        var userName = "You"
        val first = runCatching { json.parseToJsonElement(lines.first()).jsonObject }.getOrNull()
        first?.get("character_name")?.jsonPrimitive?.contentOrNull?.let { characterName = it }
        first?.get("user_name")?.jsonPrimitive?.contentOrNull?.let { userName = it }
        val characterId = nameToId[characterName.lowercase()]
            ?: db.roleplayDao().getCharacters().firstOrNull { it.name.equals(characterName, true) }?.id
            ?: upsertCharacter(CharacterCardSpec(name = characterName), avatarBytes = null)
        val personaId = ensurePersona(userName)
        val now = System.currentTimeMillis()
        val chatId = "rpchat-${UUID.randomUUID()}"
        db.roleplayDao().upsertChat(
            RpChatEntity(
                id = chatId,
                characterId = characterId,
                personaId = personaId,
                title = characterName,
                createdAt = now,
                updatedAt = now,
            ),
        )
        var order = 0
        lines.drop(1).forEach { line ->
            val obj = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull() ?: return@forEach
            val mes = obj["mes"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (mes.isBlank()) return@forEach
            val isUser = obj["is_user"]?.jsonPrimitive?.booleanOrNull == true
            val t = now + order
            db.roleplayDao().upsertMessage(
                RpMessageEntity(
                    id = "rpm-$chatId-$order",
                    chatId = chatId,
                    swipeGroupId = "sw-$chatId-$order",
                    swipeIndex = 0,
                    isActiveSwipe = true,
                    role = if (isUser) "user" else "char",
                    contentJson = Document.fromPlainText(mes).toJson(),
                    createdAt = t,
                    displayMode = "messenger",
                ),
            )
            order++
        }
        return SillyTavernImportResult(chats = 1, messages = order)
    }

    private suspend fun importPersonasFromSettings(raw: String): Int {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return 0
        val power = root["power_user"]?.jsonObject
        val personas = power?.get("personas")?.jsonObject
            ?: root["personas"]?.jsonObject
            ?: return 0
        var count = 0
        personas.forEach { (fileName, value) ->
            val name = value.jsonPrimitive.contentOrNull ?: fileName.substringBeforeLast('.')
            if (name.isBlank()) return@forEach
            ensurePersona(name)
            count++
        }
        return count
    }

    private suspend fun ensurePersona(name: String): String {
        val existing = db.roleplayDao().getPersonas().firstOrNull { it.name.equals(name, true) }
        if (existing != null) return existing.id
        val id = "persona-${UUID.randomUUID()}"
        val isFirst = db.roleplayDao().getPersonas().isEmpty()
        db.roleplayDao().upsertPersona(
            RpPersonaEntity(
                id = id,
                name = name.ifBlank { "You" },
                isDefault = isFirst,
            ),
        )
        return id
    }

    private fun looksLikeZip(bytes: ByteArray): Boolean = SillyTavernProbe.looksLikeZip(bytes)

    private fun stringList(element: kotlinx.serialization.json.JsonElement?): List<String> = when (element) {
        is JsonArray -> element.mapNotNull { it.jsonPrimitive.contentOrNull?.takeIf { s -> s.isNotBlank() } }
        else -> emptyList()
    }

    private fun encodeList(values: List<String>): String =
        values.joinToString(prefix = "[", postfix = "]") { "\"${it.replace("\"", "")}\"" }
}

/** Content probes with no database — used by import routing and tests. */
object SillyTavernProbe {
    fun looksLike(bytes: ByteArray, displayName: String): Boolean {
        val lower = displayName.lowercase()
        if (CharacterCardPng.looksLikePng(bytes)) {
            return runCatching {
                CharacterCardPng.extractCardJson(bytes)
                true
            }.getOrDefault(false)
        }
        if (!looksLikeZip(bytes)) {
            if (looksLikeCardJson(bytes)) return true
            val text = runCatching { bytes.toString(Charsets.UTF_8).trimStart() }.getOrDefault("")
            if (looksLikeChatJsonl(text)) return true
            if (looksLikeWorldInfo(text)) return true
            return false
        }
        return lower.contains("silly") || zipHasStLayout(bytes)
    }

    fun looksLikeCardJson(bytes: ByteArray): Boolean {
        val text = bytes.toString(Charsets.UTF_8)
        return text.contains("chara_card_v2") ||
            text.contains("\"first_mes\"") ||
            text.contains("\"firstMes\"")
    }

    fun looksLikeChatJsonl(text: String): Boolean =
        text.contains("\"is_user\"") &&
            (text.contains("\"mes\"") || text.contains("\"send_date\"") || text.contains("\"character_name\""))

    fun looksLikeWorldInfo(text: String): Boolean =
        text.contains("\"entries\"") &&
            (text.contains("\"keys\"") || text.contains("\"key\"")) &&
            (text.contains("\"constant\"") || text.contains("\"secondary_keys\"") ||
                text.contains("\"characterFilter\"") || text.contains("\"selective\""))

    fun zipHasStLayout(bytes: ByteArray): Boolean {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name.replace('\\', '/').lowercase()
                if (name.contains("chats/") || name.contains("worlds/") || name.contains("worldinfo")) {
                    return true
                }
                if (name.contains("characters/") && (name.endsWith(".png") || name.endsWith(".json"))) {
                    return true
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return false
    }

    fun looksLikeZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()
}
