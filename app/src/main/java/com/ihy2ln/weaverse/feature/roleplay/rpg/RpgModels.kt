package com.ihy2ln.weaverse.feature.roleplay.rpg

import kotlinx.serialization.Serializable

const val RPG_SCHEMA_VERSION = 1
const val RPG_CUSTOM_ACTION_ID = "write_your_own"
const val RPG_RETRY_MESSAGE =
    "The co-narrator reply was incomplete. Nothing changed. Retry for three structured choices."

@Serializable
enum class RpgRiskTier { Low, Medium, High }

@Serializable
enum class RpgScenePhase {
    Onboarding,
    Exploration,
    Combat,
    Aftermath,
    Recap,
}

@Serializable
enum class RpgCombatResult { Ongoing, Victory, Defeat }

@Serializable
data class RpgSceneState(
    val id: String = "",
    val chapterId: String = FirstLightChapter.ID,
    val title: String = "",
    val introduction: String = "",
    val objective: String = "",
    val phase: RpgScenePhase = RpgScenePhase.Onboarding,
    val proposals: List<RpgActionProposal> = emptyList(),
    val customActionEnabled: Boolean = true,
    val nextGuidance: String = "What can I do next?",
)

@Serializable
data class RpgActionProposal(
    val id: String,
    val title: String,
    val summary: String,
    val intent: String,
    val skill: String,
    val ability: String,
    val checkDc: Int,
    val involvedCharacters: List<String> = emptyList(),
    val riskTier: RpgRiskTier = RpgRiskTier.Medium,
    val rewardCategories: List<String> = emptyList(),
    val isCustom: Boolean = false,
    val customText: String = "",
)

@Serializable
data class RpgActionPreview(
    val proposalId: String,
    val title: String,
    val intent: String,
    val checkLabel: String,
    val ability: String,
    val checkDc: Int,
    val riskTier: RpgRiskTier,
    val likelyConsequences: List<String> = emptyList(),
    val rewardHints: List<String> = emptyList(),
    val involvedCharacters: List<String> = emptyList(),
)

@Serializable
data class RpgActionOutcome(
    val proposalId: String,
    val title: String,
    val narrative: String,
    val roll: Int,
    val dc: Int,
    val success: Boolean,
    val critical: Boolean = false,
    val rewards: List<RpgReward> = emptyList(),
    val relationshipChanges: List<String> = emptyList(),
    val nextObjective: String = "",
)

@Serializable
data class RpgReward(
    val id: String,
    val category: String,
    val label: String,
    val materialId: String = "",
    val materialQty: Int = 0,
    val bondCompanionId: String = "",
    val bondDelta: Int = 0,
    val factionId: String = "",
    val factionDelta: Int = 0,
    val unlockFlag: String = "",
    val recipeId: String = "",
)

@Serializable
data class RpgCombatActor(
    val id: String,
    val name: String,
    val hp: Int,
    val maxHp: Int,
    val ap: Int,
    val maxAp: Int,
    val ep: Int,
    val maxEp: Int,
    val isSummoner: Boolean = false,
)

@Serializable
data class RpgEnemyIntent(
    val title: String,
    val description: String,
    val damage: Int,
    val targetId: String,
)

@Serializable
data class RpgCombatEnemy(
    val id: String,
    val name: String,
    val hp: Int,
    val maxHp: Int,
    val intent: RpgEnemyIntent,
)

@Serializable
data class RpgCombatCard(
    val id: String,
    val title: String,
    val description: String,
    val ownerId: String,
    val apCost: Int,
    val epCost: Int,
    val spCost: Int = 0,
    val damage: Int = 0,
    val block: Int = 0,
    val heal: Int = 0,
    val isUltimate: Boolean = false,
)

@Serializable
data class RpgCombatState(
    val encounterId: String = "",
    val round: Int = 1,
    val party: List<RpgCombatActor> = emptyList(),
    val enemies: List<RpgCombatEnemy> = emptyList(),
    val hand: List<RpgCombatCard> = emptyList(),
    val selectedCardId: String? = null,
    val selectedTargetId: String? = null,
    val summonerSp: Int = 0,
    val maxSummonerSp: Int = 0,
    val pendingBlock: Int = 0,
    val log: List<String> = emptyList(),
    val result: RpgCombatResult = RpgCombatResult.Ongoing,
)

@Serializable
data class RpgCompanionBond(
    val id: String,
    val name: String,
    val level: Int = 1,
    val flags: List<String> = emptyList(),
)

@Serializable
data class RpgRecipe(
    val id: String,
    val name: String,
    val description: String = "",
    val inputs: Map<String, Int> = emptyMap(),
    val output: String = "",
    val unlocked: Boolean = false,
)

@Serializable
data class RpgCraftingLedger(
    val materials: Map<String, Int> = emptyMap(),
    val recipes: List<RpgRecipe> = emptyList(),
    val preparedItems: List<String> = emptyList(),
)

@Serializable
data class RpgFactionStanding(
    val id: String,
    val name: String,
    val reputation: Int = 0,
    val flags: List<String> = emptyList(),
)

@Serializable
data class RpgCampaignProgress(
    val chapterId: String = FirstLightChapter.ID,
    val chapterTitle: String = FirstLightChapter.TITLE,
    val chapterIndex: Int = 1,
    val totalChapters: Int = 1,
    val completedSceneIds: List<String> = emptyList(),
    val nextObjective: String = "",
    val recapNotes: List<String> = emptyList(),
    val onboarded: Boolean = false,
)

@Serializable
data class RpgPartyMember(
    val id: String,
    val name: String,
    val role: String,
    val hp: Int = 10,
    val maxHp: Int = 10,
    val isSummoner: Boolean = false,
)

@Serializable
data class RpgCampaignState(
    val schemaVersion: Int = RPG_SCHEMA_VERSION,
    val scene: RpgSceneState = RpgSceneState(),
    val combat: RpgCombatState? = null,
    val companions: List<RpgCompanionBond> = emptyList(),
    val crafting: RpgCraftingLedger = RpgCraftingLedger(),
    val factions: List<RpgFactionStanding> = emptyList(),
    val progress: RpgCampaignProgress = RpgCampaignProgress(),
    val party: List<RpgPartyMember> = emptyList(),
    val rngSeed: Long = 1L,
    val rngCounter: Int = 0,
    val pendingPreview: RpgActionPreview? = null,
    val confirmedProposalId: String? = null,
    val lastOutcome: RpgActionOutcome? = null,
    val lastError: String? = null,
    val recentConsequences: List<String> = emptyList(),
    val conditions: List<String> = emptyList(),
    val inventory: List<String> = emptyList(),
    val flags: List<String> = emptyList(),
)

data class RpgReduction(
    val state: RpgCampaignState,
    val accepted: Boolean,
    val message: String,
)

fun RpgCampaignState.worldFingerprint(): String =
    listOf(
        party.joinToString { "${it.id}:${it.hp}" },
        companions.joinToString { "${it.id}:${it.level}:${it.flags}" },
        crafting.materials.toSortedMap().entries.joinToString(),
        crafting.preparedItems.joinToString(),
        factions.joinToString { "${it.id}:${it.reputation}:${it.flags}" },
        inventory.joinToString(),
        flags.joinToString(),
        conditions.joinToString(),
    ).joinToString("|")
