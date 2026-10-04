package com.ihy2ln.weaverse.data.seed

import android.content.Context
import com.ihy2ln.weaverse.core.media.CodexMediaIds
import com.ihy2ln.weaverse.core.media.MediaRepository
import com.ihy2ln.weaverse.core.text.Document
import com.ihy2ln.weaverse.core.text.decodeAliases
import com.ihy2ln.weaverse.core.text.plainText
import com.ihy2ln.weaverse.core.text.toJson
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.CodexCategoryEntity
import com.ihy2ln.weaverse.data.db.entities.CodexEntryEntity
import com.ihy2ln.weaverse.data.db.entities.RpCharacterEntity
import com.ihy2ln.weaverse.data.repo.CodexScopes
import com.ihy2ln.weaverse.feature.novel.codex.CodexCustomField
import com.ihy2ln.weaverse.feature.novel.codex.CodexEntryKind
import com.ihy2ln.weaverse.feature.novel.codex.CodexSheetData
import com.ihy2ln.weaverse.feature.novel.codex.LocationSheet
import com.ihy2ln.weaverse.feature.novel.codex.OtherSheet
import com.ihy2ln.weaverse.feature.novel.codex.encodeCodexSheet
import com.ihy2ln.weaverse.feature.roleplay.characters.RpgCharacterSheet
import com.ihy2ln.weaverse.feature.roleplay.characters.decodeRpgSheet
import com.ihy2ln.weaverse.feature.roleplay.characters.encodeRpgSheet
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class CodexPack(
    val format: String = "",
    val id: String = "",
    val version: String = "",
    val source: String = "",
    val entries: List<CodexPackEntry> = emptyList(),
)

@Serializable
data class CodexPackEntry(
    val id: String,
    val category: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val text: String = "",
    val images: List<String> = emptyList(),
    val character: CodexPackCharacter? = null,
    val location: LocationSheet? = null,
    val other: CodexPackOther? = null,
)

/** The roster-sheet fields a game character fills in; blank ones leave the sheet as it is. */
@Serializable
data class CodexPackCharacter(
    val characterClass: String = "",
    val subclass: String = "",
    val species: String = "",
    val size: String = "",
    val appearance: String = "",
    val bodyDescription: String = "",
    val backstoryAndPersonality: String = "",
    val featuresAndTraits: String = "",
    val attacksAndActions: String = "",
    val weaponsAndDamageCantrips: String = "",
    val resourcesAndTools: String = "",
    val maxHp: Int = 0,
)

@Serializable
data class CodexPackOther(
    val subtype: String = "",
    val summary: String = "",
    val description: String = "",
    val details: String = "",
    val customFields: List<CodexCustomField> = emptyList(),
)

/** Pictures the app installed itself (card art, codex packs), as opposed to ones the writer added. */
fun isBundledPicture(mediaId: String): Boolean =
    mediaId.startsWith("adams-haven-") || mediaId.startsWith("codex-ah-") || mediaId.startsWith("ah-card-")

/** Marks where the game's text ends and an entry's earlier text, kept for reference, begins. */
const val EARLIER_NOTES_MARKER = "── Earlier notes ──"

/**
 * The entry's text after an update: the game's current text, then whatever the entry said before
 * (minus any game text an earlier pack wrote above the marker), so nothing the writer had is lost.
 */
fun mergedPackText(gameText: String, existing: String): String {
    val earlier = if (EARLIER_NOTES_MARKER in existing) existing.substringAfter(EARLIER_NOTES_MARKER).trim() else existing.trim()
    return if (earlier.isBlank() || earlier == gameText.trim()) gameText.trim()
    else gameText.trim() + "\n\n" + EARLIER_NOTES_MARKER + "\n" + earlier
}

/**
 * Whether an existing Codex name is the same person/place as [packName]: the same name, one of its
 * aliases, or every word of the game name present in it ("Daisy Wildheart" in "Daisy (Bonfire)
 * Wildheart"). Single-word game names must match exactly, so "Kaela" never grabs "Jasmine Kaela".
 */
fun isSameCodexName(packName: String, existingName: String, existingAliases: List<String> = emptyList()): Boolean {
    fun words(s: String) = s.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
    val pack = words(packName)
    if (pack.isEmpty()) return false
    val names = listOf(existingName) + existingAliases
    if (names.any { words(it) == pack }) return true
    return pack.size >= 2 && words(existingName).containsAll(pack)
}

/**
 * Brings the Codex up to date with the Adams Haven games: the party fighters, the Tower roster,
 * the bestiary and the expedition regions, from a pack built by tools/build_adams_haven_codex.py.
 * Runs once per pack version. Entries it made before are updated in place; an older entry for the
 * same character (e.g. from the novel export) takes the game's text, stats and pictures, with its
 * earlier text kept below [EARLIER_NOTES_MARKER] and its earlier pictures after the new ones.
 */
@Singleton
class AdamsHavenCodexPack @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: WeaverseDatabase,
    private val mediaRepository: MediaRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val stateDir = File(context.filesDir, "codex-packs")
    private val versionFile = File(stateDir, "adams-haven.version")
    private val linksFile = File(stateDir, "adams-haven.links.json")
    private val linksSerializer = MapSerializer(String.serializer(), String.serializer())
    private val sameNamedCategories = mutableMapOf<String, Set<String>>()

    suspend fun applyIfNew(): Int = withContext(Dispatchers.IO) {
        val pack = runCatching {
            json.decodeFromString(CodexPack.serializer(), context.assets.open(PACK_PATH).use { it.readBytes().decodeToString() })
        }.getOrNull() ?: return@withContext 0
        if (pack.entries.isEmpty() || runCatching { versionFile.readText().trim() }.getOrNull() == pack.version) return@withContext 0
        val links = runCatching { json.decodeFromString(linksSerializer, linksFile.readText()) }.getOrDefault(emptyMap()).toMutableMap()
        val categories = mutableMapOf<String, String>()
        // Read once: one pass over the Codex and the roster, not one per entry.
        val entries = db.codexDao().getAllEntries().associateBy { it.id }.toMutableMap()
        val characters = db.roleplayDao().getCharacters().filter { it.defaultCodexId != null }
            .associateBy { it.defaultCodexId!! }.toMutableMap()
        var changed = 0
        pack.entries.forEach { item ->
            runCatching {
                apply(item, categoryId(item.category, categories), links, entries, characters)
                changed++
            }.onFailure { android.util.Log.w("Weaverse", "Codex pack entry ${item.id} failed", it) }
        }
        stateDir.mkdirs()
        linksFile.writeText(json.encodeToString(linksSerializer, links))
        versionFile.writeText(pack.version)
        changed
    }

    private suspend fun apply(
        item: CodexPackEntry,
        categoryId: String,
        links: MutableMap<String, String>,
        entries: MutableMap<String, CodexEntryEntity>,
        characters: MutableMap<String, RpCharacterEntity>,
    ) {
        val dao = db.codexDao()
        val now = System.currentTimeMillis()
        val all = entries.values
        // Any category with the same name (the demo seed and the novel import each made their own).
        val sameNamed = sameNamedCategories.getOrPut(item.category) {
            dao.getAllCategories().filter { it.name.equals(item.category, ignoreCase = true) }.map { it.id }.toSet() + categoryId
        }
        val existing = links[item.id]?.let { id -> all.firstOrNull { it.id == id } }
            ?: all.firstOrNull { it.categoryId in sameNamed && isSameCodexName(item.name, it.name, decodeAliases(it.aliasesJson)) }
        val images = item.images.mapNotNull { path ->
            val file = path.substringAfterLast('/')
            runCatching {
                mediaRepository.registerBundledImage(
                    assetPath = path,
                    id = "codex-ah-${file.substringBeforeLast('.')}",
                    relativePath = "codex/adams_haven/$file",
                    displayName = item.name,
                    category = "Adams Haven / Codex / ${item.category}",
                ).id
            }.getOrNull()
        }
        val text = mergedPackText(item.text, existing?.plainText.orEmpty())
        val doc = Document.fromPlainText(text)
        val oldAliases = existing?.let { decodeAliases(it.aliasesJson) }.orEmpty()
        val aliases = (item.aliases + oldAliases + listOfNotNull(existing?.name?.takeIf { it != item.name }))
            .map(String::trim).filter { it.isNotBlank() && it != item.name }.distinct()
        val kind = when {
            item.character != null -> CodexEntryKind.Character
            item.location != null -> CodexEntryKind.Location
            else -> CodexEntryKind.Other
        }
        val sheetJson = when (kind) {
            CodexEntryKind.Location -> encodeCodexSheet(CodexSheetData(kind = kind.name, location = item.location!!))
            CodexEntryKind.Other -> encodeCodexSheet(
                CodexSheetData(
                    kind = kind.name,
                    other = item.other?.let { OtherSheet(it.subtype, it.summary, it.description, it.details, customFields = it.customFields) }
                        ?: OtherSheet(description = item.text),
                ),
            )
            else -> existing?.sheetJson ?: "{}"
        }
        val entry = (existing ?: CodexEntryEntity(
            id = "ah-${item.id}",
            categoryId = categoryId,
            scopeType = CodexScopes.TYPE,
            scopeId = CodexScopes.ID,
            name = item.name,
            docJson = "",
            plainText = "",
            createdAt = now,
            updatedAt = now,
        )).copy(
            name = item.name,
            aliasesJson = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(String.serializer()), aliases),
            docJson = doc.toJson(),
            plainText = doc.plainText(),
            imageMediaId = CodexMediaIds.encode(images + keptPictures(existing?.imageMediaId, replaceBundled = images.isNotEmpty())),
            sheetJson = sheetJson,
            updatedAt = now,
        )
        dao.upsertEntry(entry)
        entries[entry.id] = entry
        links[item.id] = entry.id
        item.character?.let { characters[entry.id] = updateRosterCharacter(entry, it, images.firstOrNull(), now, characters[entry.id]) }
    }

    /**
     * The entry's earlier pictures that stay: ones the writer added themselves always do; pictures
     * that came with the app (older card art, earlier packs) give way to the game's new ones.
     */
    private fun keptPictures(raw: String?, replaceBundled: Boolean): List<String> =
        CodexMediaIds.parse(raw).filterNot { replaceBundled && isBundledPicture(it) }

    /** A character entry's prose lives on its roster sheet; bring that up to date too. */
    private suspend fun updateRosterCharacter(
        entry: CodexEntryEntity,
        game: CodexPackCharacter,
        portrait: String?,
        now: Long,
        current: RpCharacterEntity?,
    ): RpCharacterEntity {
        val dao = db.roleplayDao()
        val sheet = (current?.let { decodeRpgSheet(it.extensionsJson) } ?: RpgCharacterSheet()).let { s ->
            fun pick(new: String, old: String) = new.ifBlank { old }
            s.copy(
                characterClass = pick(game.characterClass, s.characterClass),
                subclass = pick(game.subclass, s.subclass),
                species = pick(game.species, s.species),
                size = pick(game.size, s.size),
                appearance = pick(game.appearance, s.appearance),
                bodyDescription = pick(game.bodyDescription, s.bodyDescription),
                backstoryAndPersonality = pick(game.backstoryAndPersonality, s.backstoryAndPersonality),
                featuresAndTraits = pick(game.featuresAndTraits, s.featuresAndTraits),
                attacksAndActions = pick(game.attacksAndActions, s.attacksAndActions),
                weaponsAndDamageCantrips = pick(game.weaponsAndDamageCantrips, s.weaponsAndDamageCantrips),
                resourcesAndTools = pick(game.resourcesAndTools, s.resourcesAndTools),
                maxHp = if (game.maxHp > 0) game.maxHp else s.maxHp,
                currentHp = if (game.maxHp > 0) game.maxHp else s.currentHp,
            )
        }
        val character = (current ?: RpCharacterEntity(
            id = "rpc-codex-${entry.id}",
            name = entry.name,
            tagsJson = "[\"Codex\"]",
            defaultCodexId = entry.id,
            colorHex = entry.colorHex,
            createdAt = now,
        )).copy(
            name = entry.name,
            description = entry.plainText,
            avatarMediaId = portrait ?: current?.avatarMediaId,
            extensionsJson = encodeRpgSheet(current?.extensionsJson ?: "{}", sheet),
            updatedAt = now,
        )
        dao.upsertCharacter(character)
        return character
    }

    /** The global category named [name], made if missing (Monsters is new). */
    private suspend fun categoryId(name: String, cache: MutableMap<String, String>): String = cache[name] ?: run {
        val dao = db.codexDao()
        dao.getAllCategories().firstOrNull { it.scopeId == CodexScopes.ID && it.name.equals(name, ignoreCase = true) }?.id
            ?: dao.getAllCategories().firstOrNull { it.name.equals(name, ignoreCase = true) }?.id
            ?: CodexCategoryEntity(
                id = "ah-cat-${name.lowercase()}",
                scopeType = CodexScopes.TYPE,
                scopeId = CodexScopes.ID,
                name = name,
                colorHex = if (name == "Monsters") "#B4463C" else "#6E7FD8",
                sortOrder = dao.getAllCategories().size,
                updatedAt = System.currentTimeMillis(),
            ).also { dao.upsertCategory(it) }.id
    }.also { cache[name] = it }

    companion object {
        const val PACK_PATH = "codex/adams_haven/pack.json"
    }
}
