package com.ihy2ln.weaverse.feature.roleplay.textgame

import kotlinx.serialization.Serializable

@Serializable
data class TextGameDefinition(
    val id: String,
    val schemaVersion: Int = 1,
    val title: String,
    val subtitle: String = "",
    val startNodeId: String,
    val nodes: List<TextGameNode>,
    val cards: List<TextGameCard>,
    val collectibleCards: List<TextGameCollectibleCard> = emptyList(),
    val sceneAssets: List<TextGameSceneAsset> = emptyList(),
    val encounters: List<TextGameEncounter> = emptyList(),
    val rewardCardIds: List<String> = emptyList(),
    val roster: List<TextGameRosterMember> = emptyList(),
    val gachaPoolIds: List<String> = emptyList(),
    val services: List<TextGameServiceDefinition> = emptyList(),
)

@Serializable
enum class TextGameDifficulty {
    Story,
    Standard,
    Veteran,
    Nightmare;

    companion object {
        fun fromId(value: String?): TextGameDifficulty = entries.firstOrNull {
            it.name.equals(value, ignoreCase = true)
        } ?: Standard
    }
}

val TextGameDifficulty.label: String
    get() = when (this) {
        TextGameDifficulty.Story -> "Story"
        TextGameDifficulty.Standard -> "Standard"
        TextGameDifficulty.Veteran -> "Veteran"
        TextGameDifficulty.Nightmare -> "Nightmare"
    }

val TextGameDifficulty.description: String
    get() = when (this) {
        TextGameDifficulty.Story -> "Gentler fights with full story and haven progression."
        TextGameDifficulty.Standard -> "The intended balance across battle, Farm, Town, and Home."
        TextGameDifficulty.Veteran -> "Hardier enemies, sharper intents, and better coin rewards."
        TextGameDifficulty.Nightmare -> "The strongest enemies and highest deterministic rewards."
    }

@Serializable
enum class TextGameNodeType { Narrative, MissionBoard, Battle, Reward, Gacha, Hub, Ending }

@Serializable
data class TextGameHotspot(
    val id: String,
    val label: String,
    val choiceId: String,
    /** Normalized position within the scene picture. */
    val x: Float = .5f,
    val y: Float = .5f,
)

@Serializable
data class TextGameRosterMember(
    val id: String,
    val name: String,
    val role: String,
    val source: String = "Codex",
    /** Character-card front from the shared Adams Haven Pictures catalog. */
    val collectibleCardId: String? = null,
)

@Serializable
data class TextGameSceneAsset(
    val id: String,
    /** Logical lookup key such as crossroads, town, farm, dungeon, or battle. */
    val sceneTypes: List<String>,
    val mediaId: String,
    val artAssetPath: String,
    val width: Int,
    val height: Int,
    val displayName: String,
    val category: String,
    val tags: List<String>,
)

@Serializable
data class TextGameServiceDefinition(
    val id: String,
    val label: String,
    val area: String,
    val unlockLevel: Int,
    val efficiencyPercent: Int,
    val capacity: Int,
    val employeePerk: String = "",
)

@Serializable
data class TextGameNode(
    val id: String,
    val type: TextGameNodeType,
    val title: String,
    val prose: String,
    /** Seed-selected alternatives. The base prose remains a compatibility fallback. */
    val proseVariants: List<String> = emptyList(),
    /** Selects a bundled Pictures-library scene from TextGameDefinition.sceneAssets. */
    val sceneAssetType: String? = null,
    /** Optional ID from WeaverVerse's Pictures library. */
    val sceneMediaId: String? = null,
    /** Optional image bundled with the game, used when no Pictures-library override exists. */
    val bundledSceneAssetPath: String? = null,
    /** Optional stable ID for a muted looping video or FMV in WeaverVerse's media library. */
    val sceneMotionMediaId: String? = null,
    /** Optional APK asset path for a bundled MP4/WebM loop. Still art remains the fallback. */
    val bundledSceneMotionAssetPath: String? = null,
    val choices: List<TextGameChoice> = emptyList(),
    val encounterId: String? = null,
    val victoryNodeId: String? = null,
    val defeatNodeId: String? = null,
    /** Destination entered after accepting a mission from this MissionBoard node. */
    val missionDestinationNodeId: String? = null,
    /** Optional later-game destination used once [missionRepeatRequiredFlag] is set. */
    val missionRepeatDestinationNodeId: String? = null,
    val missionRepeatRequiredFlag: String? = null,
    /** Destination used after a card is selected from this Reward node. */
    val rewardDestinationNodeId: String? = null,
    val victoryCoins: Int = 3,
    val victorySp: Int = 1,
    val victoryFlag: String? = null,
    val victoryEffects: List<TextGameEffect> = emptyList(),
    val hotspots: List<TextGameHotspot> = emptyList(),
)

fun TextGameNode.proseFor(seed: Long): String {
    val options = proseVariants.ifEmpty { listOf(prose) }
    val mixed = (seed xor id.hashCode().toLong()) and Long.MAX_VALUE
    return options[(mixed % options.size).toInt()]
}

@Serializable
data class TextGameChoice(
    val id: String,
    val label: String,
    val destinationNodeId: String,
    val condition: TextGameCondition = TextGameCondition(),
    val effects: List<TextGameEffect> = emptyList(),
)

@Serializable
data class TextGameCondition(
    val requiredFlag: String? = null,
    val forbiddenFlag: String? = null,
    val requiredFlags: List<String> = emptyList(),
    val forbiddenFlags: List<String> = emptyList(),
    val minimumCoins: Int = 0,
    val minimumSeeds: Int = 0,
    val minimumHarvest: Int = 0,
    val minimumCropGrowth: Int = 0,
    val minimumMaterials: Int = 0,
    val minimumDishes: Int = 0,
)

@Serializable
data class TextGameEffect(
    val coinsDelta: Int = 0,
    val seedsDelta: Int = 0,
    val harvestDelta: Int = 0,
    val materialsDelta: Int = 0,
    val dishesDelta: Int = 0,
    val summonerSpDelta: Int = 0,
    val cropGrowthDelta: Int = 0,
    val preparedGuardDelta: Int = 0,
    val farmLevelDelta: Int = 0,
    val townLevelDelta: Int = 0,
    val homeLevelDelta: Int = 0,
    val houseLevelDelta: Int = 0,
    val battlesWonDelta: Int = 0,
    val maxHealthDelta: Int = 0,
    val healthDelta: Int = 0,
    /** Ultimate gauge charge, 0-100. */
    val ultimateDelta: Int = 0,
    val setFlag: String? = null,
    val companionId: String? = null,
    val addCardId: String? = null,
)

@Serializable
data class TextGameCard(
    val id: String,
    val title: String,
    val ownerId: String,
    val ownerName: String,
    val description: String,
    val apCost: Int = 0,
    val epCost: Int = 0,
    val spCost: Int = 0,
    val damage: Int = 0,
    val markBonus: Int = 0,
    val transferEp: Int = 0,
    val transferTargetId: String? = null,
)

@Serializable
data class TextGameCollectibleCard(
    val id: String,
    val title: String,
    val category: String,
    /** Stable ID registered in WeaverVerse's shared Pictures database. */
    val mediaId: String,
    val artAssetPath: String,
)

@Serializable
data class TextGameEncounter(
    val id: String,
    val enemies: List<TextGameEnemy>,
    val openingHand: List<String>,
    val actorResources: List<TextGameActorResource>,
)

@Serializable
data class TextGameEnemy(
    val id: String,
    val name: String,
    val maxHealth: Int,
    val intent: String,
    val intentDamage: Int,
)

@Serializable
data class TextGameActorResource(
    val actorId: String,
    val actorName: String,
    val ap: Int,
    val ep: Int,
    val maxAp: Int = ap,
    val maxEp: Int = ep,
)

/**
 * One roguelite opening offer: a different way to begin the run, generated
 * fresh (by the AI, or by local RNG without a key) for every new run.
 */
@Serializable
data class TextGameMission(
    val id: String,
    val title: String,
    val description: String = "",
    val effects: List<TextGameEffect> = emptyList(),
)

@Serializable
enum class TextGameMissionStatus { Available, Active, Completed, Failed }

/** Persisted mission-board history. Entries remain available in the Mission Log after a run. */
@Serializable
data class TextGameMissionLogEntry(
    val mission: TextGameMission,
    val status: TextGameMissionStatus = TextGameMissionStatus.Available,
    val offeredSeed: Long = 0L,
    val resolvedAfterBattle: Int? = null,
)

@Serializable
data class TextGamePersistentState(
    val flags: List<String> = emptyList(),
    val collection: List<String> = emptyList(),
    val companionId: String? = null,
    val coins: Int = 6,
    val seeds: Int = 1,
    val harvest: Int = 0,
    val materials: Int = 0,
    val dishes: Int = 0,
    val summonerSp: Int = 0,
    val cropGrowth: Int = 0,
    val preparedGuard: Int = 0,
    val farmLevel: Int = 1,
    val townLevel: Int = 1,
    val homeLevel: Int = 1,
    val battlesWon: Int = 0,
    val maxHealth: Int = 18,
    val rngSeed: Long = 13_371L,
    val difficulty: TextGameDifficulty = TextGameDifficulty.Standard,
    val rosterIds: List<String> = listOf("kestrel", "sable"),
    val activePartyIds: List<String> = listOf("kestrel", "sable"),
    val subUnitIds: List<String> = emptyList(),
    val activeSlotCount: Int = 2,
    val subSlotCount: Int = 2,
    val gachaTutorialComplete: Boolean = false,
    val gachaDrawCount: Int = 0,
    val recentGachaIds: List<String> = emptyList(),
    /** The opening mission chosen for this run; null while the roguelite offer is pending. */
    val missionId: String? = null,
    val missionTitle: String = "",
    /** Ultimate gauge, 0-100. Fills from cards, turns, and victories. */
    val ultimate: Int = 0,
    /** The persistent dungeon — floors, rooms, fog. Null until first entered. */
    val dungeon: DungeonState? = null,
    /** Persisted mission-board history shown in the Mission Log after a run. */
    val missionLog: List<TextGameMissionLogEntry> = emptyList(),
)

@Serializable
data class TextGameEnemyState(
    val id: String,
    val health: Int,
    val maxHealth: Int = health,
)

@Serializable
data class TextGameRunState(
    val nodeId: String,
    val playerHealth: Int = 18,
    val guard: Int = 0,
    val resources: List<TextGameActorResource> = emptyList(),
    val enemies: List<TextGameEnemyState> = emptyList(),
    val hand: List<String> = emptyList(),
    val playedCards: List<String> = emptyList(),
    val selectedCardId: String? = null,
    val selectedTargetId: String? = null,
    val markedTargetId: String? = null,
    val markedBonus: Int = 0,
    val rewardOptions: List<String> = emptyList(),
    val turn: Int = 1,
    val lastLog: String = "",
    val pendingStoryProposal: TextGameStoryProposal? = null,
    /** The 1-6 mission-board contracts on offer until one is accepted. */
    val missionOffer: List<TextGameMission> = emptyList(),
    /** AI-authored or seeded offline introduction shown above the mission board. */
    val missionBoardIntro: String = "",
    /** True while the current battle belongs to a dungeon room. */
    val dungeonFight: Boolean = false,
    /** Spoils of the fight that just ended, shown on the reward/ending screen. */
    val lastBattleGains: TextGameBattleGains? = null,
)

@Serializable
data class TextGameBattleGains(
    val coins: Int = 0,
    val sp: Int = 0,
    val materials: Int = 0,
    val seeds: Int = 0,
    val cropGrowth: Int = 0,
    val ultimate: Int = 0,
) {
    fun isEmpty(): Boolean =
        coins == 0 && sp == 0 && materials == 0 && seeds == 0 && cropGrowth == 0 && ultimate == 0
}

@Serializable
data class TextGameStoryProposal(
    val id: String,
    val prose: String,
    val options: List<TextGameStoryOption>,
    val sourcePrompt: String = "",
)

@Serializable
data class TextGameStoryOption(
    val id: String,
    val label: String,
    /** Optional existing choice ID. Only validated choices may change game state. */
    val validatedChoiceId: String? = null,
)

@Serializable
data class TextGameState(
    val persistent: TextGamePersistentState = TextGamePersistentState(),
    val run: TextGameRunState,
)

sealed interface TextGameAction {
    data class Choose(val choiceId: String) : TextGameAction
    data class SelectTarget(val enemyId: String) : TextGameAction
    data class SelectCard(val cardId: String) : TextGameAction
    data object PlaySelectedCard : TextGameAction
    data class PlayCard(val cardId: String) : TextGameAction
    data object EndTurn : TextGameAction
    data class ClaimReward(val cardId: String) : TextGameAction
    data object RunGachaTutorial : TextGameAction
    data class QueueStoryProposal(val proposal: TextGameStoryProposal) : TextGameAction
    data class ConfirmStoryOption(val optionId: String) : TextGameAction
    data object DismissStoryProposal : TextGameAction
    data class BeginMission(val mission: TextGameMission) : TextGameAction
    data object EnterDungeon : TextGameAction
    data class DungeonStep(val x: Int, val y: Int) : TextGameAction
    data object LeaveDungeon : TextGameAction
    data object CastUltimate : TextGameAction
    data object Reset : TextGameAction
}

data class TextGameResolution(
    val state: TextGameState,
    val accepted: Boolean,
    val log: String,
)

fun TextGameDefinition.node(id: String): TextGameNode? = nodes.firstOrNull { it.id == id }
fun TextGameDefinition.card(id: String): TextGameCard? = cards.firstOrNull { it.id == id }
fun TextGameDefinition.collectible(id: String): TextGameCollectibleCard? = collectibleCards.firstOrNull { it.id == id }
fun TextGameDefinition.encounter(id: String): TextGameEncounter? = encounters.firstOrNull { it.id == id }
