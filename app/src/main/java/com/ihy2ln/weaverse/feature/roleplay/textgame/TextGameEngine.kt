package com.ihy2ln.weaverse.feature.roleplay.textgame

import kotlin.math.roundToInt
import kotlin.random.Random

class TextGameEngine(private val definition: TextGameDefinition) {
    fun initialState(
        difficulty: TextGameDifficulty = TextGameDifficulty.Standard,
        rngSeed: Long = TextGamePersistentState().rngSeed,
    ): TextGameState = TextGameState(
        persistent = TextGamePersistentState(
            difficulty = difficulty,
            rngSeed = rngSeed,
            havenBoard = HavenBoardRules.initial(),
        ),
        run = TextGameRunState(
            nodeId = definition.startNodeId,
            playerHealth = TextGamePersistentState().maxHealth,
        ),
    )

    fun reduce(state: TextGameState, action: TextGameAction): TextGameResolution = when (action) {
        TextGameAction.Reset -> accepted(
            initialState(state.persistent.difficulty, state.persistent.rngSeed + 7_919L),
            "I begin a new run as the Summoner from a different Haven opening.",
        )
        is TextGameAction.Choose -> choose(state, action.choiceId)
        is TextGameAction.SelectTarget -> selectTarget(state, action.enemyId)
        is TextGameAction.SelectCard -> selectCard(state, action.cardId)
        TextGameAction.PlaySelectedCard -> state.run.selectedCardId?.let { playCard(state, it) }
            ?: rejected(state, "I need to select a card first.")
        is TextGameAction.PlayCard -> playCard(state, action.cardId)
        TextGameAction.EndTurn -> endTurn(state)
        is TextGameAction.ClaimReward -> claimReward(state, action.cardId)
        TextGameAction.RunGachaTutorial -> runGachaTutorial(state)
        is TextGameAction.QueueStoryProposal -> queueStoryProposal(state, action.proposal)
        is TextGameAction.ConfirmStoryOption -> confirmStoryOption(state, action.optionId)
        is TextGameAction.BeginMission -> beginMission(state, action.mission)
        TextGameAction.EnterDungeon -> enterDungeon(state)
        is TextGameAction.DungeonStep -> dungeonStep(state, action.x, action.y)
        TextGameAction.DescendDungeon -> descendDungeon(state)
        TextGameAction.LeaveDungeon -> leaveDungeon(state)
        TextGameAction.CastUltimate -> castUltimate(state)
        TextGameAction.DismissStoryProposal -> accepted(
            state.copy(run = state.run.copy(pendingStoryProposal = null)),
            "I dismiss the unconfirmed story proposal.",
        )
        is TextGameAction.FarmTill -> farmTill(state, action.plotId)
        is TextGameAction.FarmPlant -> farmPlant(state, action.plotId, action.score01, action.cropId)
        is TextGameAction.FarmWater -> farmWater(state, action.plotId)
        is TextGameAction.FarmHarvest -> farmHarvest(state, action.plotId, action.score01)
        is TextGameAction.FarmPackDish -> farmPackDish(state, action.dish)
        is TextGameAction.TownCollect -> townCollect(state, action.buildingId)
        is TextGameAction.TownMove -> townMove(state, action.buildingId, action.plotId)
        is TextGameAction.PlaceHavenCard -> placeHavenCard(state, action.cardId, action.board, action.x, action.y)
        is TextGameAction.StackHavenUpgrade -> stackHavenUpgrade(state, action.buildingCardId, action.upgradeCardId)
        is TextGameAction.MoveHavenCard -> moveHavenCard(state, action.cardId, action.x, action.y)
        is TextGameAction.EnterHavenRoom -> enterHavenRoom(state, action.buildingCardId)
    }

    fun isChoiceEnabled(state: TextGameState, choice: TextGameChoice): Boolean {
        val condition = choice.condition
        return (condition.requiredFlag == null || condition.requiredFlag in state.persistent.flags) &&
            (condition.forbiddenFlag == null || condition.forbiddenFlag !in state.persistent.flags) &&
            condition.requiredFlags.all { it in state.persistent.flags } &&
            condition.forbiddenFlags.none { it in state.persistent.flags } &&
            state.persistent.coins >= condition.minimumCoins &&
            state.persistent.seeds >= condition.minimumSeeds &&
            state.persistent.harvest >= condition.minimumHarvest &&
            state.persistent.cropGrowth >= condition.minimumCropGrowth &&
            state.persistent.materials >= condition.minimumMaterials &&
            state.persistent.dishes >= condition.minimumDishes
    }

    fun canPlay(state: TextGameState, card: TextGameCard): Boolean {
        if (!canSelectCard(state, card)) return false
        if (card.damage > 0 || card.markBonus > 0) {
            val targetId = state.run.selectedTargetId ?: return false
            if (state.run.enemies.none { it.id == targetId && it.health > 0 }) return false
        }
        return true
    }

    fun canSelectCard(state: TextGameState, card: TextGameCard): Boolean {
        if (definition.node(state.run.nodeId)?.type != TextGameNodeType.Battle) return false
        if (card.id !in state.run.hand || card.id in state.run.playedCards) return false
        val actor = state.run.resources.firstOrNull { it.actorId == card.ownerId } ?: return false
        if (actor.ap < card.apCost || actor.ep < card.epCost || state.persistent.summonerSp < card.spCost) return false
        return true
    }

    private fun choose(state: TextGameState, choiceId: String): TextGameResolution {
        val node = definition.node(state.run.nodeId) ?: return rejected(state, "The current scene is missing.")
        val choice = node.choices.firstOrNull { it.id == choiceId }
            ?: return rejected(state, "That choice is not available here.")
        if (!isChoiceEnabled(state, choice)) return rejected(state, "You do not meet that choice's requirements.")

        val applied = applyEffects(state.persistent, state.run.playerHealth, choice.effects)
        val moved = state.copy(
            persistent = applied.first,
            run = state.run.copy(
                nodeId = choice.destinationNodeId,
                playerHealth = applied.second,
                havenRoomArtPath = null,
            ),
        )
        return accepted(enterNode(moved), choice.label)
    }

    private fun selectTarget(state: TextGameState, enemyId: String): TextGameResolution {
        val enemy = state.run.enemies.firstOrNull { it.id == enemyId && it.health > 0 }
            ?: return rejected(state, "That target is no longer available.")
        val name = currentEncounter(state)?.enemies?.firstOrNull { it.id == enemyId }?.name ?: enemyId
        return accepted(state.copy(run = state.run.copy(selectedTargetId = enemyId)), "$name targeted.")
    }

    private fun selectCard(state: TextGameState, cardId: String): TextGameResolution {
        val card = definition.card(cardId) ?: return rejected(state, "Unknown card.")
        if (!canSelectCard(state, card)) {
            return rejected(state, "${card.title} is spent or its owner lacks the required resources.")
        }
        return accepted(
            state.copy(run = state.run.copy(selectedCardId = card.id)),
            "${card.title} selected. I choose a legal target, then commit the card.",
        )
    }

    private fun playCard(state: TextGameState, cardId: String): TextGameResolution {
        val card = definition.card(cardId) ?: return rejected(state, "Unknown card.")
        if (!canPlay(state, card)) return rejected(state, "${card.title} cannot be played with the current target or resources.")
        val actor = state.run.resources.first { it.actorId == card.ownerId }
        val resources = state.run.resources.map {
            when {
                it.actorId == actor.actorId -> it.copy(ap = it.ap - card.apCost, ep = it.ep - card.epCost)
                card.transferEp > 0 && it.actorId == card.transferTargetId ->
                    it.copy(ep = (it.ep + card.transferEp).coerceAtMost(it.maxEp))
                else -> it
            }
        }
        val targetId = state.run.selectedTargetId
        val markBonus = if (targetId != null && targetId == state.run.markedTargetId) state.run.markedBonus else 0
        val farmBonus = if (card.damage > 0 && state.run.farmAttackBonus > 0) state.run.farmAttackBonus else 0
        val bonus = markBonus + farmBonus
        val enemies = state.run.enemies.map {
            if (it.id == targetId && card.damage > 0) it.copy(health = (it.health - card.damage - bonus).coerceAtLeast(0)) else it
        }
        val marked = when {
            card.markBonus > 0 -> targetId
            card.damage > 0 && targetId == state.run.markedTargetId -> null
            else -> state.run.markedTargetId
        }
        var next = state.copy(
            persistent = state.persistent.copy(
                summonerSp = state.persistent.summonerSp - card.spCost,
                ultimate = (state.persistent.ultimate + ULT_PER_CARD).coerceAtMost(ULT_MAX),
            ),
            run = state.run.copy(
                resources = resources,
                enemies = enemies,
                playedCards = state.run.playedCards + card.id,
                selectedCardId = null,
                markedTargetId = marked,
                markedBonus = when {
                    card.markBonus > 0 -> card.markBonus
                    card.damage > 0 && targetId == state.run.markedTargetId -> 0
                    else -> state.run.markedBonus
                },
            ),
        )
        val damageText = if (card.damage > 0) " for ${card.damage + bonus} damage" else ""
        if (enemies.isNotEmpty() && enemies.all { it.health <= 0 }) {
            if (state.run.dungeonFight) {
                return resolveDungeonVictory(next, "${card.title}$damageText.")
            }
            return resolveAuthoredVictory(next, "${card.title}$damageText.")
        }
        return accepted(next, "${card.title}$damageText.")
    }

    /** The authored-battle victory: reward scene, flags, mission completion. */
    private fun resolveAuthoredVictory(state: TextGameState, sourceText: String): TextGameResolution {
        val battleNode = definition.node(state.run.nodeId)
            ?: return rejected(state, "The battle scene is missing.")
        val rewardNode = battleNode.victoryNodeId ?: return rejected(state, "The battle has no victory scene.")
        val rewards = seededRewards(state.persistent.rngSeed + state.run.turn, state.persistent.collection)
        val victoryFlag = battleNode.victoryFlag
        val difficultyCoinBonus = when (state.persistent.difficulty) {
            TextGameDifficulty.Story, TextGameDifficulty.Standard -> 0
            TextGameDifficulty.Veteran -> 1
            TextGameDifficulty.Nightmare -> 2
        }
        val victoryEffects = battleNode.victoryEffects + TextGameEffect(
            coinsDelta = battleNode.victoryCoins + difficultyCoinBonus,
            summonerSpDelta = battleNode.victorySp,
            battlesWonDelta = 1,
            ultimateDelta = ULT_PER_VICTORY,
            setFlag = victoryFlag,
        )
        val applied = applyEffects(state.persistent, state.run.playerHealth, victoryEffects)
        val next = state.copy(
            persistent = resolveActiveMission(
                applied.first.copy(
                    rngSeed = applied.first.rngSeed + 1,
                    havenBoard = HavenBoardRules.grantReward(applied.first.havenBoard),
                    defeatedMonsters = applied.first.defeatedMonsters + defeatedMonsterIds(state),
                ),
                TextGameMissionStatus.Completed,
            ),
            run = state.run.copy(
                nodeId = rewardNode,
                rewardOptions = rewards,
                lastBattleGains = gainsOf(victoryEffects),
            ),
        )
        return accepted(
            next,
            "$sourceText I win the encounter and recover " +
                "${battleNode.victoryCoins + difficultyCoinBonus} coins and ${battleNode.victorySp} SP.",
        )
    }

    /** Sums a victory effect package into the spoils card shown after the fight. */
    private fun gainsOf(effects: List<TextGameEffect>): TextGameBattleGains {
        var gains = TextGameBattleGains()
        effects.forEach { effect ->
            gains = gains.copy(
                coins = gains.coins + effect.coinsDelta,
                sp = gains.sp + effect.summonerSpDelta,
                materials = gains.materials + effect.materialsDelta,
                seeds = gains.seeds + effect.seedsDelta,
                cropGrowth = gains.cropGrowth + effect.cropGrowthDelta,
                ultimate = gains.ultimate + effect.ultimateDelta,
            )
        }
        return gains
    }

    /** Dungeon fights route back to the map with depth-scaled spoils. */
    private fun resolveDungeonVictory(state: TextGameState, sourceText: String): TextGameResolution {
        val dungeon = state.persistent.dungeon
            ?: return rejected(state, "The dungeon is missing.")
        val cleared = DungeonRules.clearCurrent(dungeon)
        val bonusCoins = (2 * cleared.rewardMultiplier()).roundToInt()
        val effects = listOf(
            TextGameEffect(coinsDelta = bonusCoins, battlesWonDelta = 1, ultimateDelta = ULT_PER_VICTORY),
        )
        val applied = applyEffects(state.persistent.copy(dungeon = cleared), state.run.playerHealth, effects)
        val room = cleared.currentRoom()
        val bossCleared = room != null && DungeonKind.fromIndex(room.kind) == DungeonKind.Boss
        val withBestiary = applied.first.copy(
            defeatedMonsters = applied.first.defeatedMonsters + defeatedMonsterIds(state),
        )
        val persistent = if (bossCleared) {
            resolveActiveMission(withBestiary, TextGameMissionStatus.Completed)
        } else {
            withBestiary
        }
        val next = state.copy(
            persistent = persistent,
            run = state.run.copy(
                dungeonFight = false,
                dungeonRoomKind = null,
                enemies = emptyList(),
                hand = emptyList(),
                playedCards = emptyList(),
                selectedCardId = null,
                selectedTargetId = null,
                lastBattleGains = gainsOf(effects),
            ),
        )
        val missionBit = if (bossCleared && state.persistent.missionId != null) {
            " Contract complete — the floor boss is down."
        } else {
            ""
        }
        return accepted(
            next,
            "$sourceText The room is cleared — I pocket $bonusCoins coins and return to the map.$missionBit",
        )
    }

    private fun endTurn(state: TextGameState): TextGameResolution {
        if (definition.node(state.run.nodeId)?.type != TextGameNodeType.Battle) {
            return rejected(state, "There is no battle turn to end.")
        }
        val encounter = currentEncounter(state) ?: return rejected(state, "Encounter data is missing.")
        val difficultyDamage = when (state.persistent.difficulty) {
            TextGameDifficulty.Story -> -1
            TextGameDifficulty.Standard -> 0
            TextGameDifficulty.Veteran -> 1
            TextGameDifficulty.Nightmare -> 2
        }
        val damage = encounter.enemies.sumOf { enemy ->
            if (state.run.enemies.firstOrNull { it.id == enemy.id }?.health?.let { it > 0 } == true) {
                (enemy.intentDamage + difficultyDamage).coerceAtLeast(1)
            } else 0
        }
        val absorbed = minOf(state.run.guard, damage)
        val remainingDamage = damage - absorbed
        val guard = state.run.guard - absorbed
        val health = (state.run.playerHealth - remainingDamage).coerceAtLeast(0)
        if (health == 0) {
            // A dungeon defeat ends the delve — the dungeon keeps what it taught.
            val dungeonAfter = if (state.run.dungeonFight) {
                state.persistent.dungeon?.let(DungeonRules::endDelve)
            } else {
                state.persistent.dungeon
            }
            return accepted(
                state.copy(
                    persistent = resolveActiveMission(
                        state.persistent.copy(dungeon = dungeonAfter),
                        TextGameMissionStatus.Failed,
                    ),
                    run = state.run.copy(
                        nodeId = definition.node(state.run.nodeId)?.defeatNodeId ?: "defeat",
                        playerHealth = 0,
                        guard = guard,
                        dungeonFight = false,
                        lastBattleGains = TextGameBattleGains(),
                    ),
                ),
                "Enemy intents deal $damage damage${guardText(absorbed)}. I fall and the case pulls me home.",
            )
        }
        val refreshed = state.run.resources.map { it.copy(ap = it.maxAp, ep = (it.ep + 2).coerceAtMost(it.maxEp)) }
        val buffRounds = (state.run.farmBuffRounds - 1).coerceAtLeast(0)
        return accepted(
            state.copy(
                persistent = state.persistent.copy(
                    ultimate = (state.persistent.ultimate + ULT_PER_TURN).coerceAtMost(ULT_MAX),
                ),
                run = state.run.copy(
                    playerHealth = health,
                    guard = guard,
                    resources = refreshed,
                    playedCards = emptyList(),
                    selectedCardId = null,
                    turn = state.run.turn + 1,
                    farmBuffRounds = buffRounds,
                    farmAttackBonus = if (buffRounds > 0) state.run.farmAttackBonus else 0,
                ),
            ),
            "Enemy intents deal $damage damage${guardText(absorbed)}. AP refreshes and each ally recovers 2 EP.",
        )
    }

    private fun claimReward(state: TextGameState, cardId: String): TextGameResolution {
        if (definition.node(state.run.nodeId)?.type != TextGameNodeType.Reward || cardId !in state.run.rewardOptions) {
            return rejected(state, "That reward is not available.")
        }
        val card = definition.collectible(cardId) ?: return rejected(state, "Reward card data is missing.")
        val destination = definition.node(state.run.nodeId)?.rewardDestinationNodeId
            ?: return rejected(state, "The reward scene has no destination.")
        val next = state.copy(
            persistent = state.persistent.copy(
                collection = addUnique(state.persistent.collection, cardId),
                havenBoard = HavenBoardRules.grantReward(state.persistent.havenBoard),
            ),
            run = state.run.copy(nodeId = destination, rewardOptions = emptyList()),
        )
        return accepted(enterNode(next), "I add ${card.title} to my permanent collection.")
    }

    /**
     * Commits one validated board offer and descends onto the grid dungeon map
     * (Godot AdamsHavenCardGame `scenes/Dungeon.tscn` flow) — authored road
     * nodes are no longer the Accept-mission route.
     */
    private fun beginMission(state: TextGameState, mission: TextGameMission): TextGameResolution {
        val board = definition.node(state.run.nodeId)
        if (board?.type != TextGameNodeType.MissionBoard) {
            return rejected(state, "I can only accept a mission at the mission board.")
        }
        if (state.persistent.missionId != null) {
            return rejected(state, "I already have an active mission.")
        }
        val offered = state.run.missionOffer.firstOrNull { it.id == mission.id }
            ?: return rejected(state, "That mission is not on the current board.")
        val applied = applyEffects(state.persistent, state.run.playerHealth, offered.effects)
        val log = applied.first.missionLog.map { entry ->
            if (entry.mission.id == offered.id) entry.copy(status = TextGameMissionStatus.Active) else entry
        }
        val withMission = applied.first.copy(
            missionId = offered.id,
            missionTitle = offered.title,
            missionLog = log,
        )
        val dungeon = withMission.dungeon ?: DungeonGenerator.generate(withMission.rngSeed * 31L + 7L)
        val started = DungeonRules.startDelve(dungeon, 0)
            ?: return rejected(state, "That floor is still sealed — beat the boss above it first.")
        val next = state.copy(
            persistent = withMission.copy(dungeon = started),
            run = state.run.copy(
                missionOffer = emptyList(),
                missionBoardIntro = "",
                playerHealth = applied.second,
                dungeonFight = false,
            ),
        )
        return accepted(
            next,
            "Mission accepted — ${offered.title}. I descend into the dungeon. " +
                "${DungeonRules.exits(started).size} doorways lead out of the entrance. " +
                offered.description,
        )
    }

    // ------------------------------------------------------------- the dungeon
    // Ported from the Godot AdamsHavenCardGame `core/dungeon.gd`: a persistent
    // grid dungeon with fog of war — rooms cleared stay cleared, a floor is
    // beaten across several trips.

    private val ULT_PER_CARD = 8
    private val ULT_PER_TURN = 5
    private val ULT_PER_VICTORY = 30
    private val ULT_MAX = 100
    private val ULT_DAMAGE = 14

    /** Generates (once) the persistent dungeon and starts a delve on floor one. */
    private fun enterDungeon(state: TextGameState): TextGameResolution {
        val dungeon = state.persistent.dungeon ?: DungeonGenerator.generate(state.persistent.rngSeed * 31L + 7L)
        val started = DungeonRules.startDelve(dungeon, 0)
            ?: return rejected(state, "That floor is still sealed — beat the boss above it first.")
        return accepted(
            state.copy(persistent = state.persistent.copy(dungeon = started)),
            "I descend into the dungeon. ${DungeonRules.exits(started).size} doorways lead out of the entrance.",
        )
    }

    private fun dungeonStep(state: TextGameState, x: Int, y: Int): TextGameResolution {
        val dungeon = state.persistent.dungeon
            ?: return rejected(state, "I am not inside the dungeon.")
        if (DungeonRules.canStepTo(dungeon, x, y)) {
            return resolveEnteredRoom(state, dungeon, x, y)
        }
        // Tap a known cleared room further off: walk the door-path when every
        // cell on it is already cleared (Godot scenes/Dungeon.gd).
        val path = DungeonRules.route(dungeon, dungeon.atX, dungeon.atY, x, y)
        if (path.isEmpty()) return rejected(state, "No door leads there from where I stand.")
        val floor = dungeon.currentFloor() ?: return rejected(state, "The floor is missing.")
        if (path.any { cell -> floor.room(cell.first, cell.second)?.cleared != true }) {
            return rejected(state, "I can only walk a cleared path — fight or explore the rooms between.")
        }
        var walked = dungeon
        for (cell in path) {
            walked = DungeonRules.stepTo(walked, cell.first, cell.second) ?: break
        }
        val room = walked.currentRoom()
        val kind = room?.let { DungeonKind.fromIndex(it.kind) }
        return accepted(
            state.copy(persistent = state.persistent.copy(dungeon = walked)),
            "I cross the cleared halls to the ${kind?.label ?: "room"}.",
        )
    }

    private fun resolveEnteredRoom(
        state: TextGameState,
        dungeon: DungeonState,
        x: Int,
        y: Int,
    ): TextGameResolution {
        val moved = DungeonRules.stepTo(dungeon, x, y)
            ?: return rejected(state, "No door leads there from where I stand.")
        val room = moved.currentRoom() ?: return rejected(state, "The room is missing.")
        val kind = DungeonKind.fromIndex(room.kind)
        return when {
            kind.isFightKind && !room.cleared -> startDungeonFight(state.copy(persistent = state.persistent.copy(dungeon = moved)), kind)
            kind == DungeonKind.Treasure && !room.cleared -> {
                val looted = DungeonRules.clearCurrent(moved)
                val applied = applyEffects(state.persistent.copy(dungeon = looted), state.run.playerHealth, listOf(TextGameEffect(coinsDelta = 3)))
                val gained = applied.first.coins - state.persistent.coins
                accepted(
                    state.copy(
                        persistent = applied.first,
                        run = state.run.copy(playerHealth = applied.second),
                    ),
                    "A hidden cache — I pocket $gained coins.",
                )
            }
            kind == DungeonKind.Rest && !room.cleared -> {
                val rested = DungeonRules.clearCurrent(moved)
                val applied = applyEffects(state.persistent.copy(dungeon = rested), state.run.playerHealth, listOf(TextGameEffect(healthDelta = 99)))
                accepted(
                    state.copy(
                        persistent = applied.first,
                        run = state.run.copy(playerHealth = applied.second),
                    ),
                    "A safe camp. I rest until my strength returns.",
                )
            }
            kind == DungeonKind.Merchant && !room.cleared -> {
                val traded = DungeonRules.clearCurrent(moved)
                val applied = applyEffects(state.persistent.copy(dungeon = traded), state.run.playerHealth, listOf(TextGameEffect(materialsDelta = 1)))
                accepted(
                    state.copy(
                        persistent = applied.first,
                        run = state.run.copy(playerHealth = applied.second),
                    ),
                    "A wandering trader sells me salvaged materials cheap.",
                )
            }
            kind == DungeonKind.Stairs -> {
                val descended = DungeonRules.descend(moved)
                    ?: return accepted(
                        state.copy(persistent = state.persistent.copy(dungeon = moved)),
                        "The stairs are sealed — the floor's boss still stands.",
                    )
                accepted(state.copy(persistent = state.persistent.copy(dungeon = descended)), "I take the stairs down to ${descended.floorName()}.")
            }
            else -> accepted(state.copy(persistent = state.persistent.copy(dungeon = moved)), "${kind.label} room entered.")
        }
    }

    /** Stepping onto an uncleared fight room launches the encounter. */
    private fun startDungeonFight(state: TextGameState, kind: DungeonKind): TextGameResolution {
        val dungeon = state.persistent.dungeon ?: return rejected(state, "The dungeon is missing.")
        val floor = dungeon.currentFloor() ?: return rejected(state, "The floor is missing.")
        val battleNodes = definition.nodes.filter { it.type == TextGameNodeType.Battle && it.encounterId != null }
        if (battleNodes.isEmpty()) return rejected(state, "No battle routes are defined for this dungeon.")
        val node = battleNodes[((floor.index + floor.fightsCleared()).mod(battleNodes.size))]
        val entered = enterNode(
            state.copy(
                run = state.run.copy(
                    nodeId = node.id,
                    dungeonFight = true,
                    dungeonRoomKind = kind.ordinal,
                ),
            ),
        )
        return accepted(entered, "${kind.label}: ${node.title}. The room seals behind me.")
    }

    private fun defeatedMonsterIds(state: TextGameState): Set<String> {
        val kind = state.run.dungeonRoomKind?.let(DungeonKind::fromIndex)
        val tier = gkomTierFor(kind)
        return state.run.enemies.asSequence()
            .filter { it.health <= 0 }
            .mapNotNull { pickGkomVariant(it.id, state.persistent.rngSeed, tier)?.id }
            .toSet()
    }

    private fun leaveDungeon(state: TextGameState): TextGameResolution {
        val dungeon = state.persistent.dungeon ?: return rejected(state, "I am not inside the dungeon.")
        if (!DungeonRules.canRetreat(dungeon)) {
            return rejected(state, "I cannot retreat from here — only camps and the entrance let you walk out.")
        }
        val floor = dungeon.currentFloor()
        val bossBeaten = floor?.beaten() == true
        val persistentBase = state.persistent.copy(dungeon = DungeonRules.endDelve(dungeon))
        val persistent = if (bossBeaten && state.persistent.missionId != null) {
            resolveActiveMission(persistentBase, TextGameMissionStatus.Completed)
        } else {
            persistentBase
        }
        val doneBit = if (bossBeaten && state.persistent.missionId != null) {
            " The contract is fulfilled."
        } else {
            ""
        }
        return accepted(
            state.copy(persistent = persistent, run = state.run.copy(dungeonFight = false)),
            "I climb back to the Haven road. The dungeon keeps everything I learned.$doneBit",
        )
    }

    private fun descendDungeon(state: TextGameState): TextGameResolution {
        val dungeon = state.persistent.dungeon
            ?: return rejected(state, "I am not inside the dungeon.")
        val descended = DungeonRules.descend(dungeon)
            ?: return rejected(state, "The stairs are sealed — the floor's boss still stands.")
        return accepted(
            state.copy(persistent = state.persistent.copy(dungeon = descended)),
            "I take the stairs down to ${descended.floorName()}.",
        )
    }

    /** The summoner's ultimate: a full gauge releases a heavy strike. */
    private fun castUltimate(state: TextGameState): TextGameResolution {
        if (definition.node(state.run.nodeId)?.type != TextGameNodeType.Battle) {
            return rejected(state, "The ultimate needs a battle.")
        }
        if (state.persistent.ultimate < ULT_MAX) {
            return rejected(state, "The ultimate gauge is only ${state.persistent.ultimate}% charged.")
        }
        val targetId = state.run.selectedTargetId
            ?: return rejected(state, "I need a target for the ultimate.")
        val bonus = if (targetId == state.run.markedTargetId) state.run.markedBonus else 0
        val damage = ULT_DAMAGE + bonus
        val enemies = state.run.enemies.map {
            if (it.id == targetId && it.health > 0) it.copy(health = (it.health - damage).coerceAtLeast(0)) else it
        }
        val next = state.copy(
            persistent = state.persistent.copy(ultimate = 0),
            run = state.run.copy(enemies = enemies, markedTargetId = null, markedBonus = 0),
        )
        if (enemies.isNotEmpty() && enemies.all { it.health <= 0 }) {
            if (state.run.dungeonFight) {
                return resolveDungeonVictory(next, "The ultimate erupts for $damage damage.")
            }
            return resolveAuthoredVictory(next, "The ultimate erupts for $damage damage.")
        }
        return accepted(next, "The ultimate erupts for $damage damage.")
    }

    private fun resolveActiveMission(
        persistent: TextGamePersistentState,
        status: TextGameMissionStatus,
    ): TextGamePersistentState {
        val activeId = persistent.missionId ?: return persistent
        return persistent.copy(
            missionId = null,
            missionTitle = "",
            missionLog = persistent.missionLog.map { entry ->
                if (entry.mission.id == activeId) {
                    entry.copy(status = status, resolvedAfterBattle = persistent.battlesWon)
                } else entry
            },
        )
    }

    private fun runGachaTutorial(state: TextGameState): TextGameResolution {        if (definition.node(state.run.nodeId)?.type != TextGameNodeType.Gacha) {
            return rejected(state, "The summoning tutorial is not available here.")
        }
        if (state.persistent.gachaTutorialComplete) {
            return rejected(state, "I have already completed the summoning tutorial.")
        }
        val pool = definition.gachaPoolIds.mapNotNull { id -> definition.roster.firstOrNull { it.id == id } }
        if (pool.size < 2) return rejected(state, "The local summoning pool is incomplete.")
        val firstIndex = ((state.persistent.rngSeed ushr 1) % pool.size).toInt()
        val first = pool[firstIndex]
        val remaining = pool.filterNot { it.id == first.id }
        val secondIndex = (((state.persistent.rngSeed + 1L) ushr 1) % remaining.size).toInt()
        val second = remaining[secondIndex]
        val summoned = listOf(first, second)
        val persistent = state.persistent.copy(
            rosterIds = (state.persistent.rosterIds + summoned.map { it.id }).distinct(),
            activePartyIds = (state.persistent.activePartyIds + summoned.map { it.id }).distinct().take(4),
            activeSlotCount = 4,
            gachaTutorialComplete = true,
            gachaDrawCount = state.persistent.gachaDrawCount + 2,
            recentGachaIds = summoned.map { it.id },
            rngSeed = state.persistent.rngSeed + 2,
            flags = addUnique(state.persistent.flags, "gacha_tutorial_complete"),
        )
        return accepted(
            state.copy(persistent = persistent),
            "I complete the local summon and recruit ${first.name} and ${second.name}. Four active slots are now open.",
        )
    }

    private fun queueStoryProposal(state: TextGameState, proposal: TextGameStoryProposal): TextGameResolution {
        if (proposal.prose.isBlank() || proposal.options.size !in 1..4) {
            return rejected(state, "The story proposal needs prose and one to four choices.")
        }
        return accepted(
            state.copy(run = state.run.copy(pendingStoryProposal = proposal)),
            "A story proposal is waiting for my confirmation.",
        )
    }

    private fun confirmStoryOption(state: TextGameState, optionId: String): TextGameResolution {
        val proposal = state.run.pendingStoryProposal
            ?: return rejected(state, "There is no story proposal waiting for confirmation.")
        val option = proposal.options.firstOrNull { it.id == optionId }
            ?: return rejected(state, "That story option is not available.")
        val choiceId = option.validatedChoiceId
        if (choiceId == null) {
            return accepted(
                state.copy(run = state.run.copy(pendingStoryProposal = null)),
                "I accept the story direction. No gameplay state changes.",
            )
        }
        val choice = definition.node(state.run.nodeId)?.choices?.firstOrNull { it.id == choiceId }
            ?: return rejected(state, "That proposed gameplay action is not valid here.")
        if (!isChoiceEnabled(state, choice)) return rejected(state, "That proposed gameplay action is not affordable or unlocked.")
        return choose(state.copy(run = state.run.copy(pendingStoryProposal = null)), choiceId)
            .let { result -> result.copy(log = "${result.log} Confirmed story option: ${option.label}.") }
    }

    private fun enterNode(state: TextGameState): TextGameState {
        val node = definition.node(state.run.nodeId) ?: return state
        if (node.type == TextGameNodeType.MissionBoard && state.persistent.missionId != null) {
            // Active contract: stay on the board node and put the player on the
            // grid map rather than bouncing into the old authored road scenes.
            if (state.persistent.dungeon?.inDelve() == true) return state
            val dungeon = state.persistent.dungeon
                ?: DungeonGenerator.generate(state.persistent.rngSeed * 31L + 7L)
            val started = DungeonRules.startDelve(dungeon, 0) ?: return state
            return state.copy(persistent = state.persistent.copy(dungeon = started))
        }
        val encounter = node.encounterId?.let(definition::encounter) ?: return state
        val healthMultiplier = when (state.persistent.difficulty) {
            TextGameDifficulty.Story -> 0.85
            TextGameDifficulty.Standard -> 1.0
            TextGameDifficulty.Veteran -> 1.2
            TextGameDifficulty.Nightmare -> 1.4
        }
        val openingGuard = state.persistent.preparedGuard
        val dish = state.persistent.farm.packedDish?.let { FarmRules.DISHES[it] }
        val dishGuard = (dish?.openingGuard ?: 0) + (dish?.openingHeal ?: 0)
        val dishAttack = dish?.attackBonus ?: 0
        val dishRounds = dish?.duration ?: 0
        // Roguelite variance: each entry into an encounter rolls enemy health
        // off this run's seed, so no two runs (or visits) fight identical foes.
        var varianceSeed = state.persistent.rngSeed * 31L + (state.run.nodeId.hashCode().toLong() and 0xFFFFL)
        val farmAfterDish = state.persistent.farm.copy(packedDish = null)
        return state.copy(
            persistent = state.persistent.copy(
                preparedGuard = 0,
                farm = farmAfterDish,
                rngSeed = state.persistent.rngSeed + 3,
            ),
            run = state.run.copy(
            playerHealth = state.persistent.maxHealth,
            guard = openingGuard + dishGuard,
            farmAttackBonus = dishAttack,
            farmBuffRounds = if (dishAttack > 0) dishRounds else 0,
            resources = encounter.actorResources,
            enemies = encounter.enemies.map {
                varianceSeed = varianceSeed * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L
                val variance = (((varianceSeed ushr 33) % 5L) - 2L).toInt()
                val scaledHealth = (kotlin.math.ceil(it.maxHealth * healthMultiplier).toInt() + variance)
                    .coerceAtLeast(1)
                TextGameEnemyState(it.id, scaledHealth, scaledHealth)
            },
            hand = encounter.openingHand,
            playedCards = emptyList(),
            selectedCardId = null,
            selectedTargetId = encounter.enemies.firstOrNull()?.id,
            markedTargetId = null,
            markedBonus = 0,
            rewardOptions = emptyList(),
            turn = 1,
        ))
    }

    private fun currentEncounter(state: TextGameState): TextGameEncounter? =
        definition.node(state.run.nodeId)?.encounterId?.let(definition::encounter)

    private fun seededRewards(seed: Long, excluded: List<String>): List<String> {
        val eligible = definition.rewardCardIds.filterNot(excluded::contains)
            .ifEmpty { definition.rewardCardIds }
        if (eligible.size <= 3) return eligible
        var value = seed
        val remaining = eligible.toMutableList()
        return buildList {
            repeat(3) {
                value = value * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L
                val index = ((value ushr 1) % remaining.size).toInt()
                add(remaining.removeAt(index))
            }
        }
    }

    private fun applyEffects(
        startingPersistent: TextGamePersistentState,
        startingHealth: Int,
        effects: List<TextGameEffect>,
    ): Pair<TextGamePersistentState, Int> {
        var persistent = startingPersistent
        var health = startingHealth
        effects.forEach { effect ->
            val nextBattles = (persistent.battlesWon + effect.battlesWonDelta).coerceAtLeast(0)
            val nextFarmLevel = (persistent.farmLevel + effect.farmLevelDelta).coerceAtLeast(1)
            val nextTownLevel = (persistent.townLevel + effect.townLevelDelta).coerceAtLeast(1)
            var farm = persistent.farm
            if (nextBattles > farm.battlesFought) {
                farm = FarmRules.syncBattlesFought(farm, nextBattles)
            }
            val flags = effect.setFlag?.let { addUnique(persistent.flags, it) } ?: persistent.flags
            if (effect.setFlag == "farm_cleared" || effect.farmLevelDelta != 0) {
                val capacity = FarmRules.plotCapacity(nextFarmLevel, cleared = "farm_cleared" in flags || farm.plots.isNotEmpty())
                farm = FarmRules.ensureCapacity(farm, capacity)
            }
            if (effect.setFlag == "farm_cleared" && farm.plots.isNotEmpty()) {
                farm = FarmRules.till(farm, farm.plots.first().id) ?: farm
            }
            if (effect.setFlag == "crop_planted") {
                val tilled = farm.plots.firstOrNull { it.soil == FarmSoil.Tilled }
                if (tilled != null) {
                    val crop = FarmRules.CROPS.first()
                    farm = FarmRules.plant(farm, tilled.id, crop.id, quality = 3) ?: farm
                }
            }
            if (effect.setFlag == "crop_harvested") {
                val ready = farm.plots.firstOrNull { it.soil == FarmSoil.Ready }
                    ?: farm.plots.firstOrNull { it.soil == FarmSoil.Planted }?.let { planted ->
                        // Tutorial harvest can fire via cropGrowth without waiting — force ready.
                        farm = farm.copy(
                            plots = farm.plots.map {
                                if (it.id == planted.id) it.copy(soil = FarmSoil.Ready) else it
                            },
                        )
                        farm.plots.firstOrNull { it.id == planted.id }
                    }
                if (ready != null) {
                    FarmRules.harvest(farm, ready.id)?.let { (cleared, result) ->
                        farm = FarmRules.addDish(cleared, result.dish, maxOf(1, result.amount / 2))
                    }
                }
            }
            persistent = persistent.copy(
                coins = (persistent.coins + effect.coinsDelta).coerceAtLeast(0),
                seeds = (persistent.seeds + effect.seedsDelta).coerceAtLeast(0),
                harvest = (persistent.harvest + effect.harvestDelta).coerceAtLeast(0),
                materials = (persistent.materials + effect.materialsDelta).coerceAtLeast(0),
                dishes = (persistent.dishes + effect.dishesDelta).coerceAtLeast(0),
                summonerSp = (persistent.summonerSp + effect.summonerSpDelta).coerceAtLeast(0),
                cropGrowth = (persistent.cropGrowth + effect.cropGrowthDelta).coerceAtLeast(0),
                preparedGuard = (persistent.preparedGuard + effect.preparedGuardDelta).coerceAtLeast(0),
                farmLevel = nextFarmLevel,
                townLevel = nextTownLevel,
                homeLevel = (persistent.homeLevel + effect.houseLevelDelta + effect.homeLevelDelta).coerceAtLeast(1),
                battlesWon = nextBattles,
                maxHealth = (persistent.maxHealth + effect.maxHealthDelta).coerceAtLeast(1),
                ultimate = (persistent.ultimate + effect.ultimateDelta).coerceIn(0, ULT_MAX),
                flags = flags,
                companionId = effect.companionId ?: persistent.companionId,
                collection = effect.addCardId?.let { addUnique(persistent.collection, it) } ?: persistent.collection,
                farm = farm,
                town = TownRules.sync(persistent.town, flags, nextTownLevel),
                havenBoard = HavenBoardRules.autoPlaceFromFlags(persistent.havenBoard, flags),
            )
            health = (health + effect.healthDelta + effect.maxHealthDelta).coerceIn(0, persistent.maxHealth)
        }
        return persistent to health
    }

    private fun syncedFarm(state: TextGameState): FarmState {
        val cleared = "farm_cleared" in state.persistent.flags || state.persistent.farm.plots.isNotEmpty()
        val capacity = FarmRules.plotCapacity(state.persistent.farmLevel, cleared)
        var farm = FarmRules.ensureCapacity(state.persistent.farm, capacity)
        farm = FarmRules.syncBattlesFought(farm, state.persistent.battlesWon)
        return farm
    }

    private fun farmTill(state: TextGameState, plotId: Int): TextGameResolution {
        if (!isFarmNode(state)) return rejected(state, "I can only till plots at the Farm.")
        var persistent = state.persistent
        if ("farm_cleared" !in persistent.flags && persistent.farm.plots.isEmpty()) {
            persistent = persistent.copy(flags = addUnique(persistent.flags, "farm_cleared"))
        }
        val farm = syncedFarm(state.copy(persistent = persistent))
        val next = FarmRules.till(farm, plotId) ?: return rejected(state, "That plot is not wild ground.")
        return accepted(
            state.copy(
                persistent = persistent.copy(
                    farm = next,
                    flags = addUnique(persistent.flags, "farm_cleared"),
                ),
            ),
            "I till the plot.",
        )
    }

    private fun farmPlant(state: TextGameState, plotId: Int, score01: Float, cropId: String): TextGameResolution {
        if (!isFarmNode(state)) return rejected(state, "I can only plant at the Farm.")
        if (state.persistent.seeds < 1) return rejected(state, "I need a seed to plant.")
        val farm = syncedFarm(state)
        val plot = farm.plots.firstOrNull { it.id == plotId }
            ?: return rejected(state, "That plot is missing.")
        if (plot.soil != FarmSoil.Tilled) return rejected(state, "I need tilled soil before planting.")
        val rng = Random(state.persistent.rngSeed xor (plotId * 31L) xor farm.battlesFought.toLong())
        val roll = FarmRules.roll(score01, base = 2, rng = rng)
        val quality = FarmRules.qualityForTier(roll.tier)
        val crop = if (cropId.isNotBlank()) {
            FarmRules.crop(cropId) ?: return rejected(state, "I do not have that seed.")
        } else {
            FarmRules.pickCrop(rng)
        }
        val planted = FarmRules.plant(farm, plotId, crop.id, quality)
            ?: return rejected(state, "I cannot plant there.")
        return accepted(
            state.copy(
                persistent = state.persistent.copy(
                    farm = planted,
                    seeds = state.persistent.seeds - 1,
                    flags = addUnique(state.persistent.flags, "crop_planted"),
                    rngSeed = state.persistent.rngSeed + 11,
                ),
            ),
            "I plant ${crop.name} at quality $quality.",
        )
    }

    private fun farmWater(state: TextGameState, plotId: Int): TextGameResolution {
        if (!isFarmNode(state)) return rejected(state, "I can only water plots at the Farm.")
        val farm = syncedFarm(state)
        val next = FarmRules.water(farm, plotId) ?: return rejected(state, "That crop is not waiting for water.")
        return accepted(
            state.copy(persistent = state.persistent.copy(farm = next)),
            "I water the plot — one battle sooner.",
        )
    }

    private fun farmHarvest(state: TextGameState, plotId: Int, score01: Float): TextGameResolution {
        if (!isFarmNode(state)) return rejected(state, "I can only harvest at the Farm.")
        val farm = syncedFarm(state)
        val roll = FarmRules.roll(
            score01,
            base = 2,
            rng = Random(state.persistent.rngSeed xor (plotId * 97L)),
        )
        val harvested = FarmRules.harvest(farm, plotId)
            ?: return rejected(state, "Nothing is ready to harvest there.")
        val (cleared, result) = harvested
        val bonus = maxOf(0, roll.tier - 2)
        val total = result.amount + bonus
        val dishesGained = maxOf(1, total / 2)
        val withDish = FarmRules.addDish(cleared, result.dish, dishesGained)
        return accepted(
            state.copy(
                persistent = state.persistent.copy(
                    farm = withDish,
                    harvest = state.persistent.harvest + total,
                    dishes = state.persistent.dishes + dishesGained,
                    flags = addUnique(state.persistent.flags, "crop_harvested"),
                    rngSeed = state.persistent.rngSeed + 17,
                ),
            ),
            "Harvest  ${roll.text}  →  ${roll.tierName}. I pull $total× ${result.cropName} → pantry ${result.dish} x$dishesGained.",
        )
    }

    private fun farmPackDish(state: TextGameState, dish: String): TextGameResolution {
        if (state.run.nodeId !in setOf("farm", "return_farm", "kitchen")) {
            return rejected(state, "I pack dishes from the kitchen or Clearing.")
        }
        val farm = syncedFarm(state)
        val packed = FarmRules.packDish(farm, dish) ?: return rejected(state, "I do not have that dish.")
        val def = FarmRules.DISHES[dish]
        return accepted(
            state.copy(persistent = state.persistent.copy(farm = packed)),
            "${dish} is packed for the next run${def?.let { " — ${it.status}" } ?: ""}.",
        )
    }

    private fun townCollect(state: TextGameState, buildingId: String): TextGameResolution {
        if (!isTownNode(state)) return rejected(state, "I collect from businesses in Town.")
        val def = TownRules.def(buildingId) ?: return rejected(state, "That lot is not on the square.")
        val persistent = state.persistent
        val town = TownRules.sync(persistent.town, persistent.flags, persistent.townLevel)
        val collected = TownRules.collect(
            town,
            def,
            persistent.flags,
            persistent.townLevel,
            persistent.battlesWon,
        ) ?: return rejected(state, "Nothing is ready to collect there.")
        val (nextTown, result) = collected
        return accepted(
            state.copy(
                persistent = persistent.copy(
                    town = nextTown,
                    coins = persistent.coins + result.coins,
                    materials = persistent.materials + result.materials,
                ),
            ),
            if (result.materials > 0) {
                "I collect ${result.coins} coin and ${result.materials} material from ${result.name}."
            } else {
                "I collect ${result.coins} coin from ${result.name}."
            },
        )
    }

    private fun townMove(state: TextGameState, buildingId: String, plotId: String): TextGameResolution {
        if (!isTownNode(state)) return rejected(state, "I rearrange buildings in Town.")
        val persistent = state.persistent
        val def = TownRules.def(buildingId) ?: return rejected(state, "That lot is not on the square.")
        val synced = TownRules.sync(persistent.town, persistent.flags, persistent.townLevel)
        val from = TownRules.plotId(synced, def)
        val next = TownRules.move(synced, buildingId, plotId, persistent.flags, persistent.townLevel)
            ?: return rejected(state, "That plot cannot hold this building.")
        val name = TownRules.displayName(def, persistent.homeLevel)
        val after = TownRules.plotId(next, def)
        return accepted(
            state.copy(persistent = persistent.copy(town = next)),
            if (from == after) "The $name stays on its plot." else "I move the $name onto a new plot.",
        )
    }

    private fun placeHavenCard(state: TextGameState, cardId: String, board: String, x: Float, y: Float): TextGameResolution {
        val kind = HavenBoardKind.fromId(board) ?: return rejected(state, "That board is not part of the Haven.")
        if (HavenBoardRules.boardKind(state.run.nodeId) != kind) {
            return rejected(state, "I can only place cards on the plot I am standing on.")
        }
        val next = HavenBoardRules.place(state.persistent.havenBoard, cardId, kind, x, y)
            ?: return rejected(state, "That card cannot be placed here.")
        val name = HavenBoardRules.def(cardId)?.name ?: cardId
        return accepted(
            state.copy(persistent = state.persistent.copy(havenBoard = next)),
            "I lay the $name card on the plot.",
        )
    }

    private fun stackHavenUpgrade(state: TextGameState, buildingCardId: String, upgradeCardId: String): TextGameResolution {
        if (!isHavenBoardNode(state.run.nodeId)) return rejected(state, "I stack upgrades on the Town or Farm plot.")
        val next = HavenBoardRules.stack(state.persistent.havenBoard, buildingCardId, upgradeCardId)
            ?: return rejected(state, "That upgrade does not fit this building.")
        val name = HavenBoardRules.def(upgradeCardId)?.name ?: upgradeCardId
        return accepted(
            state.copy(persistent = state.persistent.copy(havenBoard = next)),
            "I stack $name onto the building.",
        )
    }

    private fun moveHavenCard(state: TextGameState, cardId: String, x: Float, y: Float): TextGameResolution {
        if (!isHavenBoardNode(state.run.nodeId)) return rejected(state, "I rearrange cards on the Town or Farm plot.")
        val next = HavenBoardRules.move(state.persistent.havenBoard, cardId, x, y)
            ?: return rejected(state, "That card is not on the plot.")
        return accepted(
            state.copy(persistent = state.persistent.copy(havenBoard = next)),
            "I slide the card to a new spot on the plot.",
        )
    }

    private fun enterHavenRoom(state: TextGameState, buildingCardId: String): TextGameResolution {
        if (!isHavenBoardNode(state.run.nodeId)) return rejected(state, "I enter rooms from the Town or Farm plot.")
        val placed = state.persistent.havenBoard.placed.firstOrNull { it.cardId == buildingCardId }
            ?: return rejected(state, "That building is not on the plot.")
        val visit = HavenBoardRules.visitNode(placed) ?: return rejected(state, "That building has no interior yet.")
        val node = definition.node(visit) ?: return rejected(state, "That room is not wired yet.")
        return accepted(
            state.copy(
                run = state.run.copy(
                    nodeId = node.id,
                    havenRoomArtPath = HavenBoardRules.roomArt(placed),
                ),
            ),
            "I step inside ${HavenBoardRules.def(buildingCardId)?.name ?: "the building"}.",
        )
    }

    private fun isFarmNode(state: TextGameState): Boolean =
        state.run.nodeId in setOf("farm", "return_farm", "kitchen", "barn")

    private fun isTownNode(state: TextGameState): Boolean = isTownWorldNode(state.run.nodeId)

    private fun guardText(absorbed: Int): String = if (absorbed > 0) "; Home preparation blocks $absorbed" else ""

    private fun accepted(state: TextGameState, log: String) =
        TextGameResolution(state.copy(run = state.run.copy(lastLog = log)), true, log)

    private fun rejected(state: TextGameState, log: String) =
        TextGameResolution(state.copy(run = state.run.copy(lastLog = log)), false, log)

    private fun addUnique(values: List<String>, value: String): List<String> =
        if (value in values) values else values + value
}
