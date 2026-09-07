package com.ihy2ln.weaverse.feature.roleplay.rpg

/**
 * Single RPG state-change boundary. AI output may only enter through
 * [proposeActions]; world mutation happens in resolve/combat/reward methods.
 */
class RpgReducer {
    fun proposeActions(
        state: RpgCampaignState,
        parsed: RpgProposalParseResult,
    ): RpgReduction {
        return when (parsed) {
            is RpgProposalParseResult.Failure -> RpgReduction(
                state = state.copy(lastError = parsed.retryMessage),
                accepted = false,
                message = parsed.retryMessage,
            )
            is RpgProposalParseResult.Success -> {
                if (state.scene.phase != RpgScenePhase.Exploration) {
                    rejected(state, "Choices can only be proposed during exploration.")
                } else {
                    val beforeWorld = state.worldFingerprint()
                    val next = state.copy(
                        scene = state.scene.copy(proposals = FirstLightChapter.withCustom(parsed.proposals)),
                        pendingPreview = null,
                        confirmedProposalId = null,
                        lastError = null,
                    )
                    if (next.worldFingerprint() != beforeWorld) {
                        rejected(state, "Proposal attach must not mutate world state.")
                    } else {
                        RpgReduction(next, true, "Three choices plus Write your own action.")
                    }
                }
            }
        }
    }

    fun previewAction(state: RpgCampaignState, proposalId: String): RpgReduction {
        val proposal = proposalOf(state, proposalId)
            ?: return rejected(state, "That action is not available.")
        if (proposal.isCustom && proposal.customText.isBlank()) {
            return rejected(state, "Write your action before previewing it.")
        }
        val beforeWorld = state.worldFingerprint()
        val preview = RpgActionPreview(
            proposalId = proposal.id,
            title = proposal.title,
            intent = proposal.intent,
            checkLabel = "${proposal.skill} (${proposal.ability})",
            ability = proposal.ability,
            checkDc = proposal.checkDc,
            riskTier = proposal.riskTier,
            likelyConsequences = likelyConsequences(proposal),
            rewardHints = proposal.rewardCategories.map { "Possible: $it" },
            involvedCharacters = proposal.involvedCharacters,
        )
        val next = state.copy(
            pendingPreview = preview,
            confirmedProposalId = null,
            lastError = null,
        )
        if (next.worldFingerprint() != beforeWorld) {
            return rejected(state, "Preview must not mutate world state.")
        }
        return RpgReduction(next, true, "Preview ready. Confirm to resolve.")
    }

    fun confirmAction(state: RpgCampaignState, proposalId: String): RpgReduction {
        val preview = state.pendingPreview
            ?: return rejected(state, "Preview the action before confirming.")
        if (preview.proposalId != proposalId) {
            return rejected(state, "Confirm the action you previewed.")
        }
        val beforeWorld = state.worldFingerprint()
        val next = state.copy(confirmedProposalId = proposalId, lastError = null)
        if (next.worldFingerprint() != beforeWorld) {
            return rejected(state, "Confirm must not mutate world state.")
        }
        return RpgReduction(next, true, "Confirmed. Resolve to roll.")
    }

    fun resolveAction(state: RpgCampaignState): RpgReduction {
        val proposalId = state.confirmedProposalId
            ?: return rejected(state, "Confirm an action before resolving.")
        val proposal = proposalOf(state, proposalId)
            ?: return rejected(state, "That action is not available.")
        val (roll, rolled) = RpgDice.rollD20(state)
        val criticalSuccess = roll == 20
        val criticalFail = roll == 1
        val success = criticalSuccess || (!criticalFail && roll >= proposal.checkDc)
        val rewards = FirstLightChapter.explorationRewards(proposal.id, success)
        val narrative = buildString {
            append(if (success) "You succeed" else "The check slips")
            append(" on ${proposal.skill} (rolled $roll vs DC ${proposal.checkDc}). ")
            append(proposal.intent)
            if (!success) append(" The thorns still wake.")
        }
        val outcome = RpgActionOutcome(
            proposalId = proposal.id,
            title = proposal.title,
            narrative = narrative,
            roll = roll,
            dc = proposal.checkDc,
            success = success,
            critical = criticalSuccess || criticalFail,
            rewards = rewards,
            relationshipChanges = rewards.filter { it.bondDelta != 0 }.map { it.label },
            nextObjective = "Survive the Thornbound Wisp.",
        )
        var next = rolled.copy(
            lastOutcome = outcome,
            pendingPreview = null,
            confirmedProposalId = null,
            lastError = null,
            recentConsequences = (listOf(narrative) + rolled.recentConsequences).take(8),
            progress = rolled.progress.copy(
                completedSceneIds = unique(rolled.progress.completedSceneIds + rolled.scene.id),
                nextObjective = outcome.nextObjective,
            ),
        )
        next = applyRewardsInternal(next, rewards.filter { it.category != "combat" }).state
        val combatStart = startCombat(next, FirstLightChapter.ENCOUNTER_WISP)
        return RpgReduction(combatStart.state, combatStart.accepted, outcome.narrative)
    }

    fun writeCustomAction(state: RpgCampaignState, text: String): RpgReduction {
        if (state.scene.phase != RpgScenePhase.Exploration) {
            return rejected(state, "Custom actions are only available during exploration.")
        }
        val trimmed = text.trim()
        if (trimmed.isBlank()) return rejected(state, "Write what you want to do.")
        val custom = customActionProposal(trimmed)
        val others = state.scene.proposals.filterNot { it.isCustom }.take(3)
        val next = state.copy(
            scene = state.scene.copy(proposals = others + custom),
            pendingPreview = null,
            confirmedProposalId = null,
            lastError = null,
        )
        return previewAction(next, RPG_CUSTOM_ACTION_ID)
    }

    fun completeOnboarding(state: RpgCampaignState, summonerName: String): RpgReduction {
        val name = summonerName.trim().ifBlank { "Adam" }
        if (state.scene.phase != RpgScenePhase.Onboarding) {
            return rejected(state, "Onboarding is not active.")
        }
        val party = state.party.map {
            if (it.isSummoner) it.copy(name = name) else it
        }
        val forest = FirstLightChapter.forestScene()
        val next = state.copy(
            party = party,
            scene = forest,
            progress = state.progress.copy(
                onboarded = true,
                completedSceneIds = unique(state.progress.completedSceneIds + FirstLightChapter.SCENE_ONBOARDING),
                nextObjective = forest.objective,
            ),
            lastError = null,
            recentConsequences = listOf("Party ready: $name and Mira.") + state.recentConsequences,
        )
        return RpgReduction(next, true, "First Light begins on the forest verge.")
    }

    fun advanceScene(state: RpgCampaignState, sceneId: String? = null): RpgReduction {
        val destination = sceneId ?: defaultNextScene(state)
            ?: return rejected(state, "There is no next scene yet.")
        val scene = when (destination) {
            FirstLightChapter.SCENE_FOREST -> FirstLightChapter.forestScene()
            FirstLightChapter.SCENE_COMBAT -> FirstLightChapter.combatScene()
            FirstLightChapter.SCENE_AFTERMATH -> FirstLightChapter.aftermathScene()
            FirstLightChapter.SCENE_RECAP -> FirstLightChapter.recapScene(FirstLightChapter.NEXT_OBJECTIVE)
            else -> return rejected(state, "Unknown scene.")
        }
        val next = state.copy(
            scene = scene,
            combat = if (scene.phase == RpgScenePhase.Combat) state.combat else null,
            progress = state.progress.copy(
                completedSceneIds = unique(state.progress.completedSceneIds + state.scene.id),
                nextObjective = scene.objective,
                recapNotes = if (scene.phase == RpgScenePhase.Recap) {
                    FirstLightChapter.recapNotes(state)
                } else state.progress.recapNotes,
            ),
            pendingPreview = null,
            confirmedProposalId = null,
            lastError = null,
        )
        return RpgReduction(next, true, "Scene: ${scene.title}.")
    }

    fun startCombat(state: RpgCampaignState, encounterId: String? = null): RpgReduction {
        val id = encounterId ?: FirstLightChapter.ENCOUNTER_WISP
        if (id != FirstLightChapter.ENCOUNTER_WISP) {
            return rejected(state, "Unknown encounter.")
        }
        val combat = FirstLightChapter.thornEncounter(state.party, state.rngSeed)
        val next = state.copy(
            scene = FirstLightChapter.combatScene(),
            combat = combat,
            pendingPreview = null,
            confirmedProposalId = null,
            lastError = null,
            progress = state.progress.copy(nextObjective = FirstLightChapter.combatScene().objective),
        )
        return RpgReduction(next, true, "Combat begins. The Wisp intends a Lash.")
    }

    fun playCombatCard(state: RpgCampaignState, cardId: String, targetId: String? = null): RpgReduction {
        val combat = state.combat ?: return rejected(state, "No combat is active.")
        if (combat.result != RpgCombatResult.Ongoing) {
            return rejected(state, "This fight is already over.")
        }
        val card = combat.hand.firstOrNull { it.id == cardId }
            ?: return rejected(state, "That card is not in the shared hand.")
        val owner = combat.party.firstOrNull { it.id == card.ownerId }
            ?: return rejected(state, "The card's owner is missing.")
        if (owner.ap < card.apCost) return rejected(state, "${owner.name} has no AP left.")
        if (owner.ep < card.epCost) return rejected(state, "${owner.name} lacks EP for ${card.title}.")
        if (combat.summonerSp < card.spCost) return rejected(state, "Not enough SP for ${card.title}.")
        val target = (targetId ?: combat.selectedTargetId)?.let { id ->
            combat.enemies.firstOrNull { it.id == id && it.hp > 0 }
        }
        if (card.damage > 0 && target == null) {
            return rejected(state, "Choose a living enemy first.")
        }
        val party = combat.party.map { actor ->
            if (actor.id == owner.id) {
                actor.copy(
                    ap = actor.ap - card.apCost,
                    ep = actor.ep - card.epCost,
                    hp = (actor.hp + card.heal).coerceAtMost(actor.maxHp),
                )
            } else actor
        }
        val enemies = combat.enemies.map { enemy ->
            if (target != null && enemy.id == target.id) {
                enemy.copy(hp = (enemy.hp - card.damage).coerceAtLeast(0))
            } else enemy
        }
        val log = combat.log + "${owner.name} plays ${card.title}."
        var nextCombat = combat.copy(
            party = party,
            enemies = enemies,
            selectedCardId = null,
            summonerSp = combat.summonerSp - card.spCost,
            pendingBlock = combat.pendingBlock + card.block,
            log = log.takeLast(12),
        )
        nextCombat = concludeIfNeeded(nextCombat)
        var next = state.copy(combat = nextCombat, lastError = null)
        if (nextCombat.result == RpgCombatResult.Victory) {
            next = applyRewardsInternal(next, FirstLightChapter.victoryRewards()).state
            next = next.copy(
                party = syncPartyHealth(next.party, nextCombat.party),
                recentConsequences = listOf("Victory against the Thornbound Wisp.") + next.recentConsequences,
            )
        } else if (nextCombat.result == RpgCombatResult.Defeat) {
            next = next.copy(
                party = syncPartyHealth(next.party, nextCombat.party),
                recentConsequences = listOf("The party falls. Retry the ambush.") + next.recentConsequences,
            )
        }
        return RpgReduction(next, true, log.last())
    }

    fun endCombatRound(state: RpgCampaignState): RpgReduction {
        val combat = state.combat ?: return rejected(state, "No combat is active.")
        if (combat.result != RpgCombatResult.Ongoing) {
            return rejected(state, "This fight is already over.")
        }
        var nextCombat = resolveEnemyIntents(combat)
        nextCombat = concludeIfNeeded(nextCombat)
        if (nextCombat.result == RpgCombatResult.Ongoing) {
            val (index, rolled) = RpgDice.pick(state, 2)
            val mira = nextCombat.party.firstOrNull { it.id == FirstLightChapter.MIRA_ID }
                ?: nextCombat.party.first()
            val summoner = nextCombat.party.firstOrNull { it.isSummoner } ?: mira
            val target = if (index == 0) mira else summoner
            nextCombat = nextCombat.copy(
                round = nextCombat.round + 1,
                party = nextCombat.party.map { it.copy(ap = it.maxAp) },
                enemies = nextCombat.enemies.map { enemy ->
                    if (enemy.hp <= 0) enemy else enemy.copy(
                        intent = RpgEnemyIntent(
                            title = "Lash",
                            description = "Barbed light snaps at ${target.name}.",
                            damage = 4,
                            targetId = target.id,
                        ),
                    )
                },
                pendingBlock = 0,
                summonerSp = (nextCombat.summonerSp + 1).coerceAtMost(nextCombat.maxSummonerSp),
                log = (nextCombat.log + "Round ${nextCombat.round + 1}. AP restored. Wisp intends a Lash.").takeLast(12),
            )
            val next = rolled.copy(combat = nextCombat, lastError = null)
            return RpgReduction(next, true, "Round ${nextCombat.round} begins.")
        }
        var next = state.copy(combat = nextCombat, lastError = null)
        if (nextCombat.result == RpgCombatResult.Defeat) {
            next = next.copy(
                party = syncPartyHealth(next.party, nextCombat.party),
                recentConsequences = listOf("The party falls. Retry the ambush.") + next.recentConsequences,
            )
        }
        return RpgReduction(next, true, nextCombat.log.lastOrNull() ?: "Round ended.")
    }

    fun applyReward(state: RpgCampaignState, reward: RpgReward): RpgReduction =
        applyRewardsInternal(state, listOf(reward))

    fun craftRecipe(state: RpgCampaignState, recipeId: String): RpgReduction {
        val recipe = state.crafting.recipes.firstOrNull { it.id == recipeId }
            ?: return rejected(state, "Unknown recipe.")
        if (!recipe.unlocked) return rejected(state, "${recipe.name} is still locked.")
        recipe.inputs.forEach { (mat, qty) ->
            if ((state.crafting.materials[mat] ?: 0) < qty) {
                return rejected(state, "Not enough materials for ${recipe.name}.")
            }
        }
        val materials = state.crafting.materials.toMutableMap()
        recipe.inputs.forEach { (mat, qty) ->
            materials[mat] = (materials[mat] ?: 0) - qty
        }
        val next = state.copy(
            crafting = state.crafting.copy(
                materials = materials.filterValues { it > 0 },
                preparedItems = state.crafting.preparedItems + recipe.output,
            ),
            inventory = state.inventory + recipe.output,
            recentConsequences = listOf("Crafted ${recipe.output}.") + state.recentConsequences,
        )
        return RpgReduction(next, true, "Crafted ${recipe.output}.")
    }

    fun collectAftermath(state: RpgCampaignState): RpgReduction {
        if (state.combat?.result != RpgCombatResult.Victory && state.scene.phase != RpgScenePhase.Aftermath) {
            if (state.combat?.result != RpgCombatResult.Victory) {
                return rejected(state, "Win the fight before collecting spoils.")
            }
        }
        return advanceScene(state.copy(combat = state.combat), FirstLightChapter.SCENE_AFTERMATH)
    }

    fun finishChapter(state: RpgCampaignState): RpgReduction {
        val recap = advanceScene(state, FirstLightChapter.SCENE_RECAP)
        val notes = FirstLightChapter.recapNotes(recap.state)
        return RpgReduction(
            recap.state.copy(
                progress = recap.state.progress.copy(
                    recapNotes = notes,
                    nextObjective = FirstLightChapter.NEXT_OBJECTIVE,
                ),
            ),
            recap.accepted,
            "Chapter recap ready.",
        )
    }

    fun retryCombat(state: RpgCampaignState): RpgReduction {
        if (state.combat?.result != RpgCombatResult.Defeat && state.scene.phase != RpgScenePhase.Combat) {
            return rejected(state, "There is no lost fight to retry.")
        }
        val restoredParty = state.party.map { it.copy(hp = it.maxHp) }
        return startCombat(state.copy(party = restoredParty), FirstLightChapter.ENCOUNTER_WISP)
    }

    private fun applyRewardsInternal(state: RpgCampaignState, rewards: List<RpgReward>): RpgReduction {
        var next = state
        val notes = mutableListOf<String>()
        rewards.forEach { reward ->
            if (reward.materialQty > 0 && reward.materialId.isNotBlank()) {
                val materials = next.crafting.materials.toMutableMap()
                materials[reward.materialId] = (materials[reward.materialId] ?: 0) + reward.materialQty
                val recipes = next.crafting.recipes.map { recipe ->
                    if (reward.recipeId.isNotBlank() && recipe.id == reward.recipeId) {
                        recipe.copy(unlocked = true)
                    } else recipe
                }
                next = next.copy(crafting = next.crafting.copy(materials = materials, recipes = recipes))
            } else if (reward.recipeId.isNotBlank()) {
                next = next.copy(
                    crafting = next.crafting.copy(
                        recipes = next.crafting.recipes.map {
                            if (it.id == reward.recipeId) it.copy(unlocked = true) else it
                        },
                    ),
                )
            }
            if (reward.bondDelta != 0 && reward.bondCompanionId.isNotBlank()) {
                next = next.copy(
                    companions = next.companions.map { bond ->
                        if (bond.id == reward.bondCompanionId) {
                            bond.copy(
                                level = (bond.level + reward.bondDelta).coerceAtLeast(1),
                                flags = unique(bond.flags + listOfNotNull(reward.unlockFlag.takeIf { it.isNotBlank() })),
                            )
                        } else bond
                    },
                )
            }
            if (reward.factionDelta != 0 && reward.factionId.isNotBlank()) {
                next = next.copy(
                    factions = next.factions.map { standing ->
                        if (standing.id == reward.factionId) {
                            standing.copy(
                                reputation = standing.reputation + reward.factionDelta,
                                flags = unique(standing.flags + listOfNotNull(reward.unlockFlag.takeIf { it.isNotBlank() })),
                            )
                        } else standing
                    },
                )
            }
            if (reward.unlockFlag.isNotBlank()) {
                next = next.copy(flags = unique(next.flags + reward.unlockFlag))
            }
            if (reward.label.isNotBlank()) notes += reward.label
        }
        next = next.copy(recentConsequences = (notes + next.recentConsequences).take(8))
        return RpgReduction(next, true, notes.joinToString(" ").ifBlank { "Rewards applied." })
    }

    private fun resolveEnemyIntents(combat: RpgCombatState): RpgCombatState {
        var block = combat.pendingBlock
        var party = combat.party
        val log = combat.log.toMutableList()
        combat.enemies.filter { it.hp > 0 }.forEach { enemy ->
            val raw = enemy.intent.damage
            val absorbed = minOf(block, raw)
            block -= absorbed
            val dealt = raw - absorbed
            party = party.map { actor ->
                if (actor.id == enemy.intent.targetId) {
                    actor.copy(hp = (actor.hp - dealt).coerceAtLeast(0))
                } else actor
            }
            log += if (absorbed > 0) {
                "${enemy.name} lashes for $raw; $absorbed blocked."
            } else {
                "${enemy.name} lashes ${enemy.intent.title} for $dealt."
            }
        }
        return combat.copy(party = party, pendingBlock = block, log = log.takeLast(12))
    }

    private fun concludeIfNeeded(combat: RpgCombatState): RpgCombatState = when {
        combat.enemies.isNotEmpty() && combat.enemies.all { it.hp <= 0 } ->
            combat.copy(result = RpgCombatResult.Victory, log = combat.log + "The Wisp unravels.")
        combat.party.isNotEmpty() && combat.party.all { it.hp <= 0 } ->
            combat.copy(result = RpgCombatResult.Defeat, log = combat.log + "The party falls.")
        else -> combat
    }

    private fun proposalOf(state: RpgCampaignState, proposalId: String): RpgActionProposal? =
        state.scene.proposals.firstOrNull { it.id == proposalId }

    private fun likelyConsequences(proposal: RpgActionProposal): List<String> = buildList {
        add("Check: ${proposal.skill} DC ${proposal.checkDc}.")
        add("Risk: ${proposal.riskTier.name}.")
        when (proposal.riskTier) {
            RpgRiskTier.Low -> add("Failure still advances, but the ambush hits harder.")
            RpgRiskTier.Medium -> add("Failure skips a reward and wakes the Wisp.")
            RpgRiskTier.High -> add("Failure may cost HP before the fight.")
        }
        add("A Thornbound Wisp is waiting on this path.")
    }

    private fun defaultNextScene(state: RpgCampaignState): String? = when (state.scene.phase) {
        RpgScenePhase.Onboarding -> FirstLightChapter.SCENE_FOREST
        RpgScenePhase.Exploration -> FirstLightChapter.SCENE_COMBAT
        RpgScenePhase.Combat -> when (state.combat?.result) {
            RpgCombatResult.Victory -> FirstLightChapter.SCENE_AFTERMATH
            else -> null
        }
        RpgScenePhase.Aftermath -> FirstLightChapter.SCENE_RECAP
        RpgScenePhase.Recap -> null
    }

    private fun syncPartyHealth(
        party: List<RpgPartyMember>,
        actors: List<RpgCombatActor>,
    ): List<RpgPartyMember> = party.map { member ->
        actors.firstOrNull { it.id == member.id }?.let { member.copy(hp = it.hp) } ?: member
    }

    private fun unique(values: List<String>): List<String> = values.filter { it.isNotBlank() }.distinct()

    private fun rejected(state: RpgCampaignState, message: String) =
        RpgReduction(state.copy(lastError = message), false, message)
}
