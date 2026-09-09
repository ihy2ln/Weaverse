package com.ihy2ln.weaverse.feature.roleplay.campaign

import com.ihy2ln.weaverse.feature.roleplay.combat.RpgCombatOutcome
import com.ihy2ln.weaverse.feature.roleplay.combat.RpgCombatRuleset
import kotlinx.serialization.Serializable

@Serializable
enum class RpgStartupStep { Cyoa, GeneratingChapterPlan, ChapterPlan, Verification, GeneratingScene, Started }

@Serializable
enum class RpgGenerationStatus { Idle, Generating, Failed, Complete }

@Serializable
data class RpgCampaignSetupSnapshot(
    val title: String = "Untitled Campaign",
    val setting: String = "Open fantasy setting",
    val modeId: String = RpgCombatRuleset.DndD20.id,
    val ruleSystem: String = "D&D d20",
    val houseRules: String = "",
    val characters: String = "",
    val pointOfView: String = "Third-person multiple",
    val tense: String = "Past tense",
    val playerRole: String = "Adventurer",
)

@Serializable
data class RpgPlanAnswer(
    val questionId: String,
    val value: String = "",
    val presetId: String? = null,
    val skipped: Boolean = false,
)

@Serializable
data class RpgAdventurePlan(val answers: List<RpgPlanAnswer> = emptyList())

@Serializable
data class RpgChapterBeat(
    val id: String,
    val title: String,
    val summary: String = "",
    val completed: Boolean = false,
)

@Serializable
data class RpgChapterOutline(
    val workingTitle: String = "Chapter One",
    val premise: String = "",
    val primaryObjective: String = "",
    val antagonist: String = "",
    val importantLocations: String = "",
    val beats: List<RpgChapterBeat> = emptyList(),
    val optionalBeat: String = "",
    val majorChallenge: String = "",
    val climax: String = "",
    val possibleOutcomes: String = "",
)

@Serializable
data class RpgOpeningSceneGuideline(
    val title: String = "Scene One",
    val locationAndAtmosphere: String = "",
    val startingCast: String = "",
    val immediateObjective: String = "",
    val conflictAndStakes: String = "",
    val complication: String = "",
    val firstDecisionHook: String = "",
    val sceneArtTags: String = "",
)

@Serializable
data class RpgChapterPlanPayload(
    val outline: RpgChapterOutline = RpgChapterOutline(),
    val openingScene: RpgOpeningSceneGuideline = RpgOpeningSceneGuideline(),
)

@Serializable
data class RpgSceneDraft(
    val prose: String = "",
    val choices: List<String> = emptyList(),
    val sceneArtTags: String = "",
)

@Serializable
data class RpgStartupState(
    val step: RpgStartupStep = RpgStartupStep.Cyoa,
    val setup: RpgCampaignSetupSnapshot = RpgCampaignSetupSnapshot(),
    val plan: RpgAdventurePlan = RpgAdventurePlan(),
    val chapterOutline: RpgChapterOutline = RpgChapterOutline(),
    val openingScene: RpgOpeningSceneGuideline = RpgOpeningSceneGuideline(),
    val sceneDraft: RpgSceneDraft? = null,
    val generationStatus: RpgGenerationStatus = RpgGenerationStatus.Idle,
    val generationProgress: Int = 0,
    val generationError: String = "",
    val generationRequestId: String = "",
    val cyoaSuggestions: Map<String, List<String>> = emptyMap(),
    val cyoaSuggestionStatus: RpgGenerationStatus = RpgGenerationStatus.Idle,
    val cyoaSuggestionProgress: Int = 0,
    val cyoaSuggestionError: String = "",
)

@Serializable
data class RpgSceneNode(
    val id: String,
    val title: String,
    val summary: String,
    val chapter: Int = 1,
    val prerequisites: List<String> = emptyList(),
    val encounterId: String? = null,
    val sceneArtAssetId: String = "",
    val objective: String = "Reach the next story beat.",
    val branchIds: List<String> = emptyList(),
    val location: String = "",
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
    val discoveredSceneIds: Set<String> = emptySet(),
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
    val startup: RpgStartupState = RpgStartupState(),
)

const val CURRENT_RPG_SCHEMA = 2

fun defaultRpgSceneNodes(): List<RpgSceneNode> = listOf(
    RpgSceneNode("chapter-1-arrival", "The First Sign", "A strange summons pulls the party into the opening mystery.", objective = "Inspect the waystone and identify who sent the summons.", branchIds = listOf("chapter-1-crossroads", "chapter-1-wild-trail"), location = "Whispering Forest", sceneArtAssetId = "scene_forest_waystone"),
    RpgSceneNode("chapter-1-crossroads", "The Crossroads", "Three leads compete for the party's attention.", objective = "Choose which lead to follow before nightfall.", prerequisites = listOf("chapter-1-arrival"), encounterId = "encounter-crossroads", location = "Old Crossroads", sceneArtAssetId = "scene_crossroads"),
    RpgSceneNode("chapter-1-wild-trail", "The Wild Trail", "A risky shortcut reveals a different side of the mystery.", objective = "Follow the tracks without alerting the hidden watchers.", prerequisites = listOf("chapter-1-arrival"), location = "Whispering Forest", sceneArtAssetId = "scene_forest_trail"),
    RpgSceneNode("chapter-1-vault", "The Sealed Vault", "The first chapter's danger waits behind an ancient seal.", objective = "Break the seal and survive what answers from within.", prerequisites = listOf("chapter-1-crossroads"), encounterId = "encounter-vault", location = "Sunken Vault", sceneArtAssetId = "scene_vault"),
)

fun createRpgCampaign(campaignId: String, modeId: String = RpgCombatRuleset.DndD20.id, ruleSystemId: String = "dnd-5e"): RpgCampaignState =
    RpgCampaignState(campaignId = campaignId, modeId = RpgCombatRuleset.fromId(modeId).id, ruleSystemId = ruleSystemId)

fun availableRpgSceneNodes(state: RpgCampaignState): List<RpgSceneNode> = state.map.nodes.filter { node ->
    node.id !in state.map.completedNodeIds && node.prerequisites.all { it in state.map.completedNodeIds }
}

fun enterFreeformExploration(state: RpgCampaignState, location: String): RpgCampaignState =
    state.copy(exploration = RpgExplorationState(true, location, state.map.currentNodeId, "Explore $location and uncover a lead."))

fun discoverExplorationScene(state: RpgCampaignState, sceneId: String): RpgCampaignState =
    if (!state.exploration.active) state else state.copy(
        exploration = state.exploration.copy(discoveredSceneIds = state.exploration.discoveredSceneIds + sceneId),
    )

fun enterRpgSceneNode(state: RpgCampaignState, nodeId: String): RpgCampaignState {
    val node = state.map.nodes.firstOrNull { it.id == nodeId } ?: return state
    if (node !in availableRpgSceneNodes(state)) return state
    return state.copy(map = state.map.copy(currentNodeId = node.id, discoveredNodeIds = state.map.discoveredNodeIds + node.id))
}

fun completeRpgSceneNode(state: RpgCampaignState, nodeId: String, consequence: String = ""): RpgCampaignState {
    val node = state.map.nodes.firstOrNull { it.id == nodeId } ?: return state
    val completed = state.map.completedNodeIds + node.id
    val discovered = state.map.discoveredNodeIds + node.branchIds
    val recap = buildString {
        append("${node.title} completed. ")
        if (consequence.isNotBlank()) append(consequence)
    }.trim()
    return state.copy(
        map = state.map.copy(currentNodeId = node.id, completedNodeIds = completed, discoveredNodeIds = discovered),
        progression = state.progression.copy(milestonePoints = state.progression.milestonePoints + 1),
        chapterRecap = recap,
    )
}

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
