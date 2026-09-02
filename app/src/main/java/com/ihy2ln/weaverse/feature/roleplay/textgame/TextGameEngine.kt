package com.ihy2ln.weaverse.feature.roleplay.textgame

import kotlin.math.roundToInt

class TextGameEngine(private val definition: TextGameDefinition) {
    fun initialState(
        difficulty: TextGameDifficulty = TextGameDifficulty.Standard,
        rngSeed: Long = TextGamePersistentState().rngSeed,
    ): TextGameState = TextGameState(
        persistent = TextGamePersistentState(difficulty = difficulty, rngSeed = rngSeed),
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
        TextGameAction.LeaveDungeon -> leaveDungeon(state)
        TextGameAction.CastUltimate -> castUltimate(state)
        TextGameAction.DismissStoryProposal -> accepted(
            state.copy(run = state.run.copy(pendingStoryProposal = null)),
            "I dismiss the unconfirmed story proposal.",
        )
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
            run = state.run.copy(nodeId = choice.destinationNodeId, playerHealth = applied.second),
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
        val bonus = if (targetId != null && targetId == state.run.markedTargetId) state.run.markedBonus else 0
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
                applied.first.copy(rngSeed = applied.first.rngSeed + 1),
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
        val next = state.copy(
            persistent = resolveActiveMission(applied.first, TextGameMissionStatus.Completed),
            run = state.run.copy(
                dungeonFight = false,
                enemies = emptyList(),
                hand = emptyList(),
                playedCards = emptyList(),
                selectedCardId = null,
                selectedTargetId = null,
                lastBattleGains = gainsOf(effects),
            ),
        )
        return accepted(
            next,
            "$sourceText The room is cleared — I pocket $bonusCoins coins and return to the map.",
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
            persistent = state.persistent.copy(collection = addUnique(state.persistent.collection, cardId)),
            run = state.run.copy(nodeId = destination, rewardOptions = emptyList()),
        )
        return accepted(enterNode(next), "I add ${card.title} to my permanent collection.")
    }

    /** Commits one validated board offer and advances into its authored dungeon route. */
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
        val repeatDestination = board.missionRepeatDestinationNodeId
            ?.takeIf { board.missionRepeatRequiredFlag?.let { flag -> flag in applied.first.flags } == true }
        val destination = repeatDestination ?: board.missionDestinationNodeId
            ?: return rejected(state, "The mission board has no dungeon route.")
        val next = state.copy(
            persistent = applied.first.copy(
                missionId = offered.id,
                missionTitle = offered.title,
                missionLog = log,
            ),
            run = state.run.copy(
                nodeId = destination,
                missionOffer = emptyList(),
                missionBoardIntro = "",
                playerHealth = applied.second,
            ),
        )
        return accepted(
            enterNode(next),
            "Mission accepted — ${offered.title}. ${offered.description}".trim(),
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
        val moved = DungeonRules.stepTo(dungeon, x, y)
            ?: return rejected(state, "No door leads there from where I stand.")
        val room = moved.currentRoom() ?: return rejected(state, "The room is missing.")
        val kind = DungeonKind.fromIndex(room.kind)
        return when {
            kind.isFightKind && !room.cleared -> startDungeonFight(state.copy(persistent = state.persistent.copy(dungeon = moved)), kind)
            kind == DungeonKind.Treasure -> {
                val applied = applyEffects(state.persistent.copy(dungeon = moved), state.run.playerHealth, listOf(TextGameEffect(coinsDelta = 3)))
                val gained = applied.first.coins - state.persistent.coins
                accepted(
                    state.copy(
                        persistent = applied.first,
                        run = state.run.copy(playerHealth = applied.second),
                    ),
                    "A hidden cache — I pocket $gained coins.",
                )
            }
            kind == DungeonKind.Rest -> {
                val applied = applyEffects(state.persistent.copy(dungeon = moved), state.run.playerHealth, listOf(TextGameEffect(healthDelta = 99)))
                accepted(
                    state.copy(
                        persistent = applied.first,
                        run = state.run.copy(playerHealth = applied.second),
                    ),
                    "A safe camp. I rest until my strength returns.",
                )
            }
            kind == DungeonKind.Merchant -> {
                val applied = applyEffects(state.persistent.copy(dungeon = moved), state.run.playerHealth, listOf(TextGameEffect(materialsDelta = 1)))
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
        val entered = enterNode(state.copy(run = state.run.copy(nodeId = node.id, dungeonFight = true)))
        return accepted(entered, "${kind.label}: ${node.title}. The room seals behind me.")
    }

    private fun leaveDungeon(state: TextGameState): TextGameResolution {
        val dungeon = state.persistent.dungeon ?: return rejected(state, "I am not inside the dungeon.")
        if (!DungeonRules.canRetreat(dungeon)) {
            return rejected(state, "I cannot retreat from here — only camps and the entrance let you walk out.")
        }
        return accepted(
            state.copy(persistent = state.persistent.copy(dungeon = DungeonRules.endDelve(dungeon))),
            "I climb back to the Haven road. The dungeon keeps everything I learned.",
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
            val repeatDestination = node.missionRepeatDestinationNodeId
                ?.takeIf { node.missionRepeatRequiredFlag?.let { flag -> flag in state.persistent.flags } == true }
            val destination = repeatDestination ?: node.missionDestinationNodeId ?: return state
            return enterNode(state.copy(run = state.run.copy(nodeId = destination)))
        }
        val encounter = node.encounterId?.let(definition::encounter) ?: return state
        val healthMultiplier = when (state.persistent.difficulty) {
            TextGameDifficulty.Story -> 0.85
            TextGameDifficulty.Standard -> 1.0
            TextGameDifficulty.Veteran -> 1.2
            TextGameDifficulty.Nightmare -> 1.4
        }
        val openingGuard = state.persistent.preparedGuard
        // Roguelite variance: each entry into an encounter rolls enemy health
        // off this run's seed, so no two runs (or visits) fight identical foes.
        var varianceSeed = state.persistent.rngSeed * 31L + (state.run.nodeId.hashCode().toLong() and 0xFFFFL)
        return state.copy(
            persistent = state.persistent.copy(preparedGuard = 0, rngSeed = state.persistent.rngSeed + 3),
            run = state.run.copy(
            playerHealth = state.persistent.maxHealth,
            guard = openingGuard,
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
            persistent = persistent.copy(
                coins = (persistent.coins + effect.coinsDelta).coerceAtLeast(0),
                seeds = (persistent.seeds + effect.seedsDelta).coerceAtLeast(0),
                harvest = (persistent.harvest + effect.harvestDelta).coerceAtLeast(0),
                materials = (persistent.materials + effect.materialsDelta).coerceAtLeast(0),
                dishes = (persistent.dishes + effect.dishesDelta).coerceAtLeast(0),
                summonerSp = (persistent.summonerSp + effect.summonerSpDelta).coerceAtLeast(0),
                cropGrowth = (persistent.cropGrowth + effect.cropGrowthDelta).coerceAtLeast(0),
                preparedGuard = (persistent.preparedGuard + effect.preparedGuardDelta).coerceAtLeast(0),
                farmLevel = (persistent.farmLevel + effect.farmLevelDelta).coerceAtLeast(1),
                townLevel = (persistent.townLevel + effect.townLevelDelta).coerceAtLeast(1),
                homeLevel = (persistent.homeLevel + effect.houseLevelDelta + effect.homeLevelDelta).coerceAtLeast(1),
                battlesWon = (persistent.battlesWon + effect.battlesWonDelta).coerceAtLeast(0),
                maxHealth = (persistent.maxHealth + effect.maxHealthDelta).coerceAtLeast(1),
                ultimate = (persistent.ultimate + effect.ultimateDelta).coerceIn(0, ULT_MAX),
                flags = effect.setFlag?.let { addUnique(persistent.flags, it) } ?: persistent.flags,
                companionId = effect.companionId ?: persistent.companionId,
                collection = effect.addCardId?.let { addUnique(persistent.collection, it) } ?: persistent.collection,
            )
            health = (health + effect.healthDelta + effect.maxHealthDelta).coerceIn(0, persistent.maxHealth)
        }
        return persistent to health
    }

    private fun guardText(absorbed: Int): String = if (absorbed > 0) "; Home preparation blocks $absorbed" else ""

    private fun accepted(state: TextGameState, log: String) =
        TextGameResolution(state.copy(run = state.run.copy(lastLog = log)), true, log)

    private fun rejected(state: TextGameState, log: String) =
        TextGameResolution(state.copy(run = state.run.copy(lastLog = log)), false, log)

    private fun addUnique(values: List<String>, value: String): List<String> =
        if (value in values) values else values + value
}
