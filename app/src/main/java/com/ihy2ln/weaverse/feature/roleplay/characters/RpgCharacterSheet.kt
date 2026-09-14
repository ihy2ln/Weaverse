package com.ihy2ln.weaverse.feature.roleplay.characters

import kotlin.math.max
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
    val subclass: String = "",
    val species: String = "",
    val level: Int = 1,
    val experiencePoints: Int = 0,
    val background: String = "",
    val currentHp: Int = 10,
    val maxHp: Int = 10,
    val temporaryHp: Int = 0,
    val armorClass: Int = 10,
    val proficiencyBonus: Int = 2,
    val speedFeet: Int = 30,
    val initiative: Int = 0,
    val size: String = "Medium",
    val passivePerception: Int = 10,
    val heroicInspiration: Boolean = false,
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
    val weaponsAndDamageCantrips: String = "",
    val combatNotes: String = "",
    val spells: String = "",
    val spellcastingAbility: String = "",
    val spellcastingModifier: Int = 0,
    val spellSaveDc: Int = 10,
    val spellAttackBonus: Int = 0,
    val spellSlots: String = "",
    val preparedSpells: String = "",
    val featuresAndTraits: String = "",
    val classFeatures: String = "",
    val speciesTraits: String = "",
    val feats: String = "",
    val resourcesAndTools: String = "",
    val armorTraining: String = "",
    val weaponProficiencies: String = "",
    val toolProficiencies: String = "",
    val languages: String = "",
    val conditions: String = "",
    val currency: String = "",
    val equipmentNotes: String = "",
    val magicItemAttunement: String = "",
    val appearance: String = "",
    val backstoryAndPersonality: String = "",
    val alignment: String = "",
    /** Physical details: hair, eyes, height, weight, three sizes, skin tone, etc. */
    val bodyDescription: String = "",
    /** Focused Tactical Cards profile. These fields are RPG-only and never use Text Game state. */
    val tacticalRole: String = "Vanguard",
    val tacticalAttack: Int = 3,
    val tacticalDefense: Int = 3,
    val tacticalSupport: Int = 2,
    val tacticalSpeed: Int = 2,
    val tacticalActionPoints: Int = 3,
    val tacticalEnergyPoints: Int = 3,
    val tacticalSignature: String = "Adaptable Strike",
    val tacticalSignatureEffect: String = "1 AP · Deal 5 damage to one enemy.",
)

private data class StartingProfile(
    val role: String,
    val strength: Int,
    val dexterity: Int,
    val constitution: Int,
    val intelligence: Int,
    val wisdom: Int,
    val charisma: Int,
    val hitDie: Int,
    val armorClass: Int,
    val attack: Int,
    val defense: Int,
    val support: Int,
    val speed: Int,
    val energy: Int,
    val signature: String,
    val signatureEffect: String,
)

private fun startingProfile(characterClass: String): StartingProfile {
    val type = characterClass.lowercase()
    return when {
        listOf("wizard", "mage", "sorcerer", "warlock", "witch", "arcanist").any(type::contains) ->
            StartingProfile("Arcanist", 8, 14, 12, 16, 12, 10, 6, 12, 4, 1, 3, 2, 4, "Arcane Burst", "1 AP + 2 EP · Deal 8 damage and Expose one enemy.")
        listOf("rogue", "ranger", "archer", "assassin", "bard", "scout").any(type::contains) ->
            StartingProfile("Skirmisher", 10, 16, 12, 12, 12, 12, 8, 14, 4, 2, 2, 4, 3, "Exploit Opening", "1 AP + 1 EP · Deal 6 damage; +2 against an Exposed enemy.")
        listOf("cleric", "druid", "healer", "priest", "shaman", "oracle").any(type::contains) ->
            StartingProfile("Support", 10, 12, 14, 12, 16, 12, 8, 13, 2, 3, 4, 2, 4, "Restoring Word", "1 AP + 1 EP · Restore 5 HP and Empower one ally.")
        listOf("fighter", "warrior", "paladin", "knight", "guardian", "barbarian").any(type::contains) ->
            StartingProfile("Defender", 16, 12, 14, 10, 10, 10, 10, 16, 4, 4, 1, 2, 2, "Shielding Blow", "1 AP · Deal 5 damage and become Guarded.")
        else -> StartingProfile("Vanguard", 13, 12, 13, 11, 12, 10, 8, 13, 3, 3, 2, 2, 3, "Adaptable Strike", "1 AP · Deal 5 damage to one enemy.")
    }
}

/**
 * Creates a complete, immediately playable sheet for every new roster entry.
 * Known AI/imported values win; missing values receive deterministic class-based defaults.
 */
fun createRpgCharacterSheet(
    name: String = "",
    description: String = "",
    characterClass: String = "Adventurer",
    species: String = "",
    background: String = "",
    level: Int = 1,
    strength: Int? = null,
    dexterity: Int? = null,
    constitution: Int? = null,
    intelligence: Int? = null,
    wisdom: Int? = null,
    charisma: Int? = null,
): RpgCharacterSheet {
    val resolvedClass = characterClass.ifBlank { "Adventurer" }
    val profile = startingProfile(resolvedClass)
    val resolvedLevel = level.coerceIn(1, 20)
    val suppliedScores = listOf(strength, dexterity, constitution, intelligence, wisdom, charisma)
    val suppliedScoresArePlaceholder = suppliedScores.all { it == null || it == 10 }
    val str = if (suppliedScoresArePlaceholder) profile.strength else strength?.coerceIn(1, 30) ?: profile.strength
    val dex = if (suppliedScoresArePlaceholder) profile.dexterity else dexterity?.coerceIn(1, 30) ?: profile.dexterity
    val con = if (suppliedScoresArePlaceholder) profile.constitution else constitution?.coerceIn(1, 30) ?: profile.constitution
    val int = if (suppliedScoresArePlaceholder) profile.intelligence else intelligence?.coerceIn(1, 30) ?: profile.intelligence
    val wis = if (suppliedScoresArePlaceholder) profile.wisdom else wisdom?.coerceIn(1, 30) ?: profile.wisdom
    val cha = if (suppliedScoresArePlaceholder) profile.charisma else charisma?.coerceIn(1, 30) ?: profile.charisma
    val proficiency = 2 + (resolvedLevel - 1) / 4
    val hpPerLevel = max(1, profile.hitDie / 2 + 1 + abilityModifier(con))
    val hp = max(1, profile.hitDie + abilityModifier(con) + ((resolvedLevel - 1) * hpPerLevel))
    val attackAbility = maxOf(str, dex, int, wis, cha)
    val attackBonus = abilityModifier(attackAbility) + proficiency
    fun signedBonus(value: Int): String = if (value >= 0) "+$value" else value.toString()
    val subject = name.ifBlank { "This adventurer" }
    return RpgCharacterSheet(
        characterClass = resolvedClass,
        species = species.ifBlank { "Unspecified ancestry" },
        level = resolvedLevel,
        background = background.ifBlank { "Wanderer" },
        currentHp = hp,
        maxHp = hp,
        armorClass = profile.armorClass,
        proficiencyBonus = proficiency,
        initiative = abilityModifier(dex),
        passivePerception = 10 + abilityModifier(wis),
        strength = str,
        dexterity = dex,
        constitution = con,
        intelligence = int,
        wisdom = wis,
        charisma = cha,
        hitDiceCount = resolvedLevel,
        hitDieType = "d${profile.hitDie}",
        savingThrows = "Primary save ${if (str >= dex) "Strength" else "Dexterity"} ${signedBonus(abilityModifier(max(str, dex)) + proficiency)}; Constitution ${formatModifier(con)}",
        skillsAndProficiencies = "Athletics ${formatModifier(str)}; Perception ${formatModifier(wis)}; Survival ${formatModifier(wis)}",
        attacksAndActions = "Basic attack: ${signedBonus(attackBonus)} to hit; Help; Dash; Defend.",
        weaponsAndDamageCantrips = "Adventuring weapon · 1d8 ${formatModifier(max(str, dex))} damage.",
        combatNotes = "App-managed HP, conditions, targets, and deterministic outcomes.",
        featuresAndTraits = profile.signature,
        classFeatures = profile.signature,
        resourcesAndTools = "Adventuring kit",
        weaponProficiencies = "Simple weapons",
        languages = "Common",
        appearance = description.lineSequence().firstOrNull()?.trim().orEmpty(),
        backstoryAndPersonality = "$subject is ready for the campaign; edit this field as their history develops.",
        tacticalRole = profile.role,
        tacticalAttack = profile.attack,
        tacticalDefense = profile.defense,
        tacticalSupport = profile.support,
        tacticalSpeed = profile.speed,
        tacticalActionPoints = 3,
        tacticalEnergyPoints = profile.energy,
        tacticalSignature = profile.signature,
        tacticalSignatureEffect = profile.signatureEffect,
    )
}

fun abilityModifier(score: Int): Int = Math.floorDiv(score - 10, 2)

fun formatModifier(score: Int): String = abilityModifier(score).let { if (it >= 0) "+$it" else "$it" }

fun RpgCharacterSheet.withCurrentHp(value: Int): RpgCharacterSheet =
    copy(currentHp = value.coerceIn(0, maxHp.coerceAtLeast(0)))

fun decodeRpgSheet(extensionsJson: String): RpgCharacterSheet = runCatching {
    val root = sheetJson.parseToJsonElement(extensionsJson) as? JsonObject
        ?: return@runCatching createRpgCharacterSheet()
    root[SHEET_KEY]?.let { sheetJson.decodeFromJsonElement<RpgCharacterSheet>(it) }
        ?: createRpgCharacterSheet()
}.getOrDefault(createRpgCharacterSheet())

fun hasRpgSheet(extensionsJson: String): Boolean = runCatching {
    val root = sheetJson.parseToJsonElement(extensionsJson) as? JsonObject
    root?.containsKey(SHEET_KEY) == true
}.getOrDefault(false)

/** Adds a populated sheet while preserving every unrelated imported extension field. */
fun ensureRpgSheet(
    extensionsJson: String,
    name: String,
    description: String = "",
    characterClass: String = "Adventurer",
): String = if (hasRpgSheet(extensionsJson)) {
    extensionsJson
} else {
    encodeRpgSheet(
        extensionsJson,
        createRpgCharacterSheet(name = name, description = description, characterClass = characterClass),
    )
}

fun encodeRpgSheet(extensionsJson: String, sheet: RpgCharacterSheet): String {
    val root = runCatching { sheetJson.parseToJsonElement(extensionsJson) as? JsonObject }
        .getOrNull() ?: JsonObject(emptyMap())
    return sheetJson.encodeToString(JsonObject(root + (SHEET_KEY to sheetJson.encodeToJsonElement(sheet))))
}
