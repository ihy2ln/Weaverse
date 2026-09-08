package com.ihy2ln.weaverse.feature.roleplay.campaign

import com.ihy2ln.weaverse.feature.roleplay.combat.RpgCombatOutcome
import com.ihy2ln.weaverse.feature.roleplay.combat.RpgCombatRuleset
import kotlinx.serialization.Serializable

@Serializable
data class RpgSceneNode(
    val id: String,
    val title: String,
    val summary: String,
    val chapter: Int = 1,
    val prerequisites: List<String> = emptyList(),
    val encounterId: String? = null,
    val sceneArtAssetId: String = "",
)

@Serializable
data class RpgMapState(
    val nodes: List<RpgSceneNode> = defaultRpgSceneNodes(),
    val currentNodeId: String = "chapter-1-arrival",
    val completedNodeIds: Set<String> = emptySet(),
    val discoveredNodeIds: Set<String> = setOf("chapter-1-arrival"),
)

@Serializable
data class RpgExplorationState(
    val active: Boolean = false,
    val location: String = "",
    val returnNodeId: String = "",
    val summary: String = "",
)

@Serializable
data class RpgPartyState(
    val memberIds: List<String> = emptyList(),
    val hpByMember: Map<String, Int> = emptyMap(),
    val conditionsByMember: Map<String, Set<String>> = emptyMap(),
    val equipmentByMember: Map<String, List<String>> = emptyMap(),
)

@Serializable
data class RpgCompanionState(
    val companionId: String,
    val relationship: Int = 0,
    val stance: String = "Neutral",
    val available: Boolean = true,
    val consequenceLog: List<String> = emptyList(),
)

@Serializable
data class RpgProgressionState(
    val milestonePoints: Int = 0,
    val completedMilestones: Set<String> = emptySet(),
    val rewards: List<String> = emptyList(),
)

@Serializable
data class RpgCampaignState(
    val schemaVersion: Int = CURRENT_RPG_SCHEMA,
    val campaignId: String,
    val modeId: String = RpgCombatRuleset.DndD20.id,
    val ruleSystemId: String = "dnd-5e",
    val chapter: Int = 1,
    val map: RpgMapState = RpgMapState(),
    val exploration: RpgExplorationState = RpgExplorationState(),
    val party: RpgPartyState = RpgPartyState(),
    val companions: List<RpgCompanionState> = emptyList(),
    val progression: RpgProgressionState = RpgProgressionState(),
    val activeCombatJson: String? = null,
    val lastOutcome: RpgCombatOutcome? = null,
    val chapterRecap: String = "",
)

const val CURRENT_RPG_SCHEMA = 1

fun defaultRpgSceneNodes(): List<RpgSceneNode> = listOf(
    RpgSceneNode("chapter-1-arrival", "The First Sign", "A strange summons pulls the party into the opening mystery.", sceneArtAssetId = "scene_forest_waystone"),
    RpgSceneNode("chapter-1-crossroads", "The Crossroads", "Three leads compete for the party's attention.", prerequisites = listOf("chapter-1-arrival"), encounterId = "encounter-crossroads", sceneArtAssetId = "scene_crossroads"),
    RpgSceneNode("chapter-1-vault", "The Sealed Vault", "The first chapter's danger waits behind an ancient seal.", prerequisites = listOf("chapter-1-crossroads"), encounterId = "encounter-vault", sceneArtAssetId = "scene_vault"),
)

fun createRpgCampaign(campaignId: String, modeId: String = RpgCombatRuleset.DndD20.id, ruleSystemId: String = "dnd-5e"): RpgCampaignState =
    RpgCampaignState(campaignId = campaignId, modeId = RpgCombatRuleset.fromId(modeId).id, ruleSystemId = ruleSystemId)

fun availableRpgSceneNodes(state: RpgCampaignState): List<RpgSceneNode> = state.map.nodes.filter { node ->
    node.id !in state.map.completedNodeIds && node.prerequisites.all { it in state.map.completedNodeIds }
}

fun enterFreeformExploration(state: RpgCampaignState, location: String): RpgCampaignState =
    state.copy(exploration = RpgExplorationState(true, location, state.map.currentNodeId))

fun returnToChapterNode(state: RpgCampaignState): RpgCampaignState =
    state.copy(exploration = RpgExplorationState(), map = state.map.copy(currentNodeId = state.exploration.returnNodeId.ifBlank { state.map.currentNodeId }))

fun applyCompanionConsequence(state: RpgCampaignState, companionId: String, delta: Int, consequence: String): RpgCampaignState =
    state.copy(companions = state.companions.map { companion ->
        if (companion.companionId != companionId) companion else companion.copy(
            relationship = (companion.relationship + delta).coerceIn(-100, 100),
            stance = when { companion.relationship + delta >= 25 -> "Supportive"; companion.relationship + delta <= -25 -> "Wary"; else -> "Neutral" },
            consequenceLog = (companion.consequenceLog + consequence).takeLast(20),
        )
    })

fun applyCombatOutcome(state: RpgCampaignState, outcome: RpgCombatOutcome): RpgCampaignState {
    val node = state.map.nodes.firstOrNull { it.encounterId == outcome.encounterId }
    val completed = if (outcome.result == RpgCombatOutcome.Result.Victory && node != null) state.map.completedNodeIds + node.id else state.map.completedNodeIds
    val discovered = if (node != null) state.map.discoveredNodeIds + node.id else state.map.discoveredNodeIds
    return state.copy(
        map = state.map.copy(completedNodeIds = completed, discoveredNodeIds = discovered, currentNodeId = node?.id ?: state.map.currentNodeId),
        progression = state.progression.copy(milestonePoints = state.progression.milestonePoints + if (outcome.result == RpgCombatOutcome.Result.Victory) 1 else 0, rewards = state.progression.rewards + outcome.rewards),
        lastOutcome = outcome,
        chapterRecap = outcome.recap,
        activeCombatJson = null,
    )
}

fun buildChapterRecap(state: RpgCampaignState): String = state.lastOutcome?.recap ?: "Chapter ${state.chapter} is underway at ${state.map.nodes.firstOrNull { it.id == state.map.currentNodeId }?.title ?: "an unknown location"}."
