package com.ihy2ln.weaverse.feature.novel.codex

import com.ihy2ln.weaverse.core.media.CodexMediaIds
import com.ihy2ln.weaverse.core.media.MediaRepository
import com.ihy2ln.weaverse.core.roleplay.avatarColorHexFor
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.CodexEntryEntity
import com.ihy2ln.weaverse.data.db.entities.MediaEntity
import com.ihy2ln.weaverse.data.db.entities.RpCharacterEntity
import com.ihy2ln.weaverse.data.db.entities.decodeEquipment
import com.ihy2ln.weaverse.data.db.entities.decodeItems
import com.ihy2ln.weaverse.data.db.entities.encodeItems
import com.ihy2ln.weaverse.feature.roleplay.characters.RpgCharacterSheet
import com.ihy2ln.weaverse.feature.roleplay.characters.decodeRpgSheet
import com.ihy2ln.weaverse.feature.roleplay.characters.encodeRpgSheet
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A codex entry drawn as a plate: framed portrait, name, its kind, and the four
 * stat chips that matter for that kind — a character's CLASS / HP / AC / GEAR,
 * a location's TYPE / SCALE / POP, an object's RARITY / VALUE, and so on. Every
 * codex list uses this; only the chips change with the template.
 */
data class CodexEntryUi(
    val entry: CodexEntryEntity,
    val categoryName: String = "",
    val kind: CodexEntryKind = CodexEntryKind.Other,
    val portraitPath: String = "",
    val avatarColorHex: String = "",
    /** Label → value, in the order the plate prints them. */
    val chips: List<Pair<String, String>> = emptyList(),
    /** The one-line note under the chips: what it has equipped, who rules it, … */
    val trailingLine: String = "",
    /** Linked RpCharacterEntity id, for the kinds that carry gear. */
    val rosterCharacterId: String? = null,
) {
    val id: String get() = entry.id
    val name: String get() = entry.name
    val plainText: String get() = entry.plainText
    val colorHex: String? get() = entry.colorHex
    /** The type line under the name: the template, plus the category when they differ. */
    val typeLine: String
        get() = if (categoryName.isBlank() || categoryName.equals(kind.label, ignoreCase = true)) {
            kind.label
        } else {
            "${kind.label} · $categoryName"
        }
}

private val legacySheetJson = Json { ignoreUnknownKeys = true }

/**
 * Joins a codex entry to the sheet behind it.
 *
 * Characters (and the other kinds that can carry gear) are backed by an
 * [RpCharacterEntity] linked by `defaultCodexId`, so they use the real Roster
 * sheet and Inventory ledger. Locations, objects, lore and everything else keep
 * their template in `codex_entries.sheetJson` — no roster character is invented
 * for a legend or a mountain range.
 */
@Singleton
class CodexRosterLink @Inject constructor(
    private val db: WeaverseDatabase,
    private val mediaRepository: MediaRepository,
) {
    /** Roster characters that back codex entries, keyed by entry id. */
    fun linkedByEntryId(characters: List<RpCharacterEntity>): Map<String, RpCharacterEntity> =
        characters
            .filter { !it.defaultCodexId.isNullOrBlank() && it.defaultCodexId?.startsWith("persona:") != true }
            .associateBy { it.defaultCodexId!! }

    /** Builds the plate for one entry from already-observed roster/media lists. */
    fun decorate(
        entry: CodexEntryEntity,
        categoryName: String,
        linked: Map<String, RpCharacterEntity>,
        mediaById: Map<String, MediaEntity>,
    ): CodexEntryUi {
        val character = linked[entry.id]
        val sheet = decodeCodexSheet(entry.sheetJson)
        val kind = sheet.kindOr(categoryName)
        fun pathOf(mediaId: String?): String = mediaId
            ?.let(mediaById::get)
            ?.let { mediaRepository.resolveFile(it).absolutePath }
            .orEmpty()
        return CodexEntryUi(
            entry = entry,
            categoryName = categoryName,
            kind = kind,
            // Codex media doubles as the portrait until a roster portrait is set.
            portraitPath = pathOf(character?.avatarMediaId)
                .ifBlank { pathOf(CodexMediaIds.parse(entry.imageMediaId).firstOrNull()) },
            avatarColorHex = avatarColorHexFor(entry.name, entry.colorHex ?: character?.colorHex),
            chips = chipsFor(kind, entry, sheet, character),
            trailingLine = trailingLineFor(kind, sheet, character),
            rosterCharacterId = character?.id,
        )
    }

    private fun chipsFor(
        kind: CodexEntryKind,
        entry: CodexEntryEntity,
        sheet: CodexSheetData,
        character: RpCharacterEntity?,
    ): List<Pair<String, String>> = when (kind) {
        CodexEntryKind.Character -> {
            val rpg = characterSheetFor(entry, character)
            val carried = decodeItems(character?.inventoryJson?.takeIf { it.isNotBlank() } ?: entry.inventoryJson)
            listOf(
                "CLASS" to rpg?.let { "${it.characterClass} ${it.level}" }.orEmpty(),
                "HP" to rpg?.let { "${it.currentHp}/${it.maxHp}" }.orEmpty(),
                "AC" to rpg?.armorClass?.toString().orEmpty(),
                "GEAR" to carried.sumOf { it.quantity.coerceAtLeast(1) }.toString(),
            )
        }
        CodexEntryKind.Location -> listOf(
            "TYPE" to sheet.location.locationType,
            "SCALE" to sheet.location.scale,
            "POP" to sheet.location.population.takeIf { it > 0 }?.toString().orEmpty(),
        )
        CodexEntryKind.Item -> listOf(
            "TYPE" to sheet.item.itemType,
            "RARITY" to sheet.item.rarity,
            "VALUE" to sheet.item.value,
            "STATS" to if (sheet.item.statsFilledIn) sheet.item.damage.ifBlank { "yes" } else "—",
        )
        CodexEntryKind.Lore -> listOf(
            "TYPE" to sheet.lore.loreType,
            "ERA" to sheet.lore.era,
        )
        CodexEntryKind.Other -> listOf("TYPE" to sheet.other.subtype)
    }

    private fun trailingLineFor(
        kind: CodexEntryKind,
        sheet: CodexSheetData,
        character: RpCharacterEntity?,
    ): String = when (kind) {
        CodexEntryKind.Character -> character?.equipmentJson
            ?.let { decodeEquipment(it).values.filter(String::isNotBlank) }
            .orEmpty()
            .joinToString(" • ")
        CodexEntryKind.Location -> listOf(sheet.location.region, sheet.location.ruler)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        CodexEntryKind.Item -> listOf(sheet.item.owner, sheet.item.location)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        CodexEntryKind.Lore -> sheet.lore.significance.lineSequence().firstOrNull()?.trim().orEmpty()
        CodexEntryKind.Other -> ""
    }

    /** The RPG sheet behind a character entry: the roster one, else the legacy column. */
    private fun characterSheetFor(entry: CodexEntryEntity, character: RpCharacterEntity?): RpgCharacterSheet? = when {
        character != null -> decodeRpgSheet(character.extensionsJson)
        entry.sheetJson.isNotBlank() && entry.sheetJson != "{}" && "\"kind\"" !in entry.sheetJson -> runCatching {
            legacySheetJson.decodeFromString(RpgCharacterSheet.serializer(), entry.sheetJson)
        }.getOrNull()
        else -> null
    }

    /**
     * The roster character backing this entry, created and seeded on first use.
     * Only the kinds that carry gear get one; returns null for lore and the rest.
     */
    suspend fun ensureCharacterFor(entryId: String): RpCharacterEntity? {
        val existing = db.roleplayDao().getCharacters().firstOrNull { it.defaultCodexId == entryId }
        if (existing != null) return existing
        val entry = db.codexDao().getAllEntries().firstOrNull { it.id == entryId } ?: return null
        val now = System.currentTimeMillis()
        val seededSheet = characterSheetFor(entry, null) ?: RpgCharacterSheet()
        val character = RpCharacterEntity(
            id = "rpc-codex-${UUID.randomUUID()}",
            name = entry.name.ifBlank { "Untitled" },
            avatarMediaId = CodexMediaIds.parse(entry.imageMediaId).firstOrNull(),
            description = entry.plainText,
            tagsJson = "[\"Codex\"]",
            extensionsJson = encodeRpgSheet("{}", seededSheet),
            defaultCodexId = entry.id,
            colorHex = entry.colorHex,
            inventoryJson = encodeItems(decodeItems(entry.inventoryJson)),
            inParty = false,
            createdAt = now,
            updatedAt = now,
        )
        db.roleplayDao().upsertCharacter(character)
        return character
    }
}
