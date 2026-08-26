package com.ihy2ln.weaverse.feature.roleplay.characters

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

private const val SHEET_KEY = "weaverseRpgSheet"
private val sheetJson = Json { ignoreUnknownKeys = true }

@Serializable
data class RpgCharacterSheet(
    val characterClass: String = "Adventurer",
    val level: Int = 1,
    val background: String = "",
    val currentHp: Int = 10,
    val maxHp: Int = 10,
    val temporaryHp: Int = 0,
    val armorClass: Int = 10,
    val proficiencyBonus: Int = 2,
    val speedFeet: Int = 30,
    val initiative: Int = 0,
    val strength: Int = 10,
    val dexterity: Int = 10,
    val constitution: Int = 10,
    val intelligence: Int = 10,
    val wisdom: Int = 10,
    val charisma: Int = 10,
    val hitDiceCount: Int = 1,
    val hitDieType: String = "d8",
    val deathSaveSuccesses: Int = 0,
    val deathSaveFailures: Int = 0,
    val savingThrows: String = "",
    val skillsAndProficiencies: String = "",
    val attacksAndActions: String = "",
    val combatNotes: String = "",
    val spells: String = "",
    val featuresAndTraits: String = "",
    val resourcesAndTools: String = "",
    val languages: String = "",
    val conditions: String = "",
    val currency: String = "",
)

fun abilityModifier(score: Int): Int = Math.floorDiv(score - 10, 2)

fun formatModifier(score: Int): String = abilityModifier(score).let { if (it >= 0) "+$it" else "$it" }

fun RpgCharacterSheet.withCurrentHp(value: Int): RpgCharacterSheet =
    copy(currentHp = value.coerceIn(0, maxHp.coerceAtLeast(0)))

fun decodeRpgSheet(extensionsJson: String): RpgCharacterSheet = runCatching {
    val root = sheetJson.parseToJsonElement(extensionsJson) as? JsonObject ?: return@runCatching RpgCharacterSheet()
    root[SHEET_KEY]?.let { sheetJson.decodeFromJsonElement<RpgCharacterSheet>(it) } ?: RpgCharacterSheet()
}.getOrDefault(RpgCharacterSheet())

fun encodeRpgSheet(extensionsJson: String, sheet: RpgCharacterSheet): String {
    val root = runCatching { sheetJson.parseToJsonElement(extensionsJson) as? JsonObject }
        .getOrNull() ?: JsonObject(emptyMap())
    return sheetJson.encodeToString(JsonObject(root + (SHEET_KEY to sheetJson.encodeToJsonElement(sheet))))
}
